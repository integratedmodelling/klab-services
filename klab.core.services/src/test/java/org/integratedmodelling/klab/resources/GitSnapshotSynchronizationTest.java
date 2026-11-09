package org.integratedmodelling.klab.resources;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.nio.file.Files;
import java.nio.file.Path;
import org.eclipse.jgit.api.Git;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.services.base.BaseService;
import org.integratedmodelling.klab.utilities.Utils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GitSnapshotSynchronizationTest {
  @TempDir Path root;

  private Git origin() throws Exception {
    var git = Git.init().setDirectory(root.resolve("origin").toFile()).call();
    identify(git);
    commit(git, "file", "initial\n");
    return git;
  }

  private void identify(Git git) throws Exception {
    var config = git.getRepository().getConfig();
    config.setString("user", null, "name", "Test");
    config.setString("user", null, "email", "test@example.org");
    config.setBoolean("core", null, "autocrlf", false);
    config.save();
  }

  private void commit(Git git, String path, String text) throws Exception {
    Files.writeString(git.getRepository().getWorkTree().toPath().resolve(path), text);
    git.add().addFilepattern(path).call();
    git.commit().setMessage(path).call();
  }

  private Git clone(Git origin) throws Exception {
    var local = Git.cloneRepository().setURI(origin.getRepository().getWorkTree().toURI().toString())
        .setDirectory(root.resolve("local").toFile()).setNoCheckout(true).call();
    identify(local);
    local.reset().setMode(org.eclipse.jgit.api.ResetCommand.ResetType.HARD).setRef("HEAD").call();
    return local;
  }

  private Utils.Git.Modifications synchronize(Git local) {
    var service = mock(BaseService.class);
    var scope = mock(Scope.class);
    when(service.credentialVault()).thenReturn(
        mock(org.integratedmodelling.common.authentication.CredentialVault.class));
    return Utils.Git.withCredentials(service, scope, null,
        () -> Utils.Git.fetchAndMerge(local.getRepository().getWorkTree(), scope));
  }

  @Test void fetchesAndFastForwards() throws Exception {
    try (var origin = origin(); var local = clone(origin)) {
      commit(origin, "file", "remote\n");
      var result = synchronize(local);
      assertFalse(Utils.Notifications.hasErrors(result.getNotifications()),
          result.getNotifications().stream().map(n -> n.getMessage()).toList().toString());
      assertEquals(origin.getRepository().resolve("HEAD"), local.getRepository().resolve("HEAD"));
      assertEquals("remote\n", Files.readString(root.resolve("local/file")));
    }
  }

  @Test void mergesConflictFreeDivergence() throws Exception {
    try (var origin = origin(); var local = clone(origin)) {
      commit(local, "local-only", "local\n");
      commit(origin, "remote-only", "remote\n");
      var result = synchronize(local);
      assertFalse(Utils.Notifications.hasErrors(result.getNotifications()),
          result.getNotifications().stream().map(n -> n.getMessage()).toList().toString());
      assertTrue(Files.exists(root.resolve("local/local-only")));
      assertTrue(Files.exists(root.resolve("local/remote-only")));
      try (var walk = new org.eclipse.jgit.revwalk.RevWalk(local.getRepository())) {
        assertEquals(2, walk.parseCommit(local.getRepository().resolve("HEAD")).getParentCount());
      }
    }
  }

  @Test void conflictingRemoteChangesLeaveHeadIndexAndWorkingTreeIntact() throws Exception {
    try (var origin = origin(); var local = clone(origin)) {
      commit(local, "file", "local\n");
      commit(origin, "file", "remote\n");
      var head = local.getRepository().resolve("HEAD");
      byte[] index = Files.readAllBytes(root.resolve("local/.git/index"));
      assertTrue(Utils.Notifications.hasErrors(synchronize(local).getNotifications()));
      assertEquals(head, local.getRepository().resolve("HEAD"));
      assertArrayEquals(index, Files.readAllBytes(root.resolve("local/.git/index")));
      assertEquals("local\n", Files.readString(root.resolve("local/file")));
      assertTrue(local.status().call().isClean());
    }
  }

  @Test void remoteChangesWithDirtyWorkingTreeAreReportedAndPreserved() throws Exception {
    try (var origin = origin(); var local = clone(origin)) {
      Files.writeString(root.resolve("local/file"), "pending\n");
      commit(origin, "file", "remote\n");
      var head = local.getRepository().resolve("HEAD");
      assertTrue(Utils.Notifications.hasErrors(synchronize(local).getNotifications()));
      assertEquals(head, local.getRepository().resolve("HEAD"));
      assertEquals("pending\n", Files.readString(root.resolve("local/file")));
    }
  }
}
