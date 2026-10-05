package org.integratedmodelling.klab.resources;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import org.eclipse.jgit.api.Git;
import org.integratedmodelling.klab.utilities.Utils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GitProjectWorkflowsTest {
  @TempDir Path root;

  private Git repository() throws Exception {
    var git = Git.init().setDirectory(root.toFile()).call();
    var config = git.getRepository().getConfig();
    config.setBoolean("core", null, "autocrlf", false);
    config.setString("user", null, "name", "Test");
    config.setString("user", null, "email", "test@example.org");
    config.save();
    Files.writeString(root.resolve("file"), "initial\n");
    git.add().addFilepattern(".").call();
    git.commit().setMessage("Initial").call();
    return git;
  }

  @Test
  void commitsCreatesSwitchesAndMergesBranches() throws Exception {
    try (var git = repository()) {
      String original = git.getRepository().getBranch();
      Files.writeString(root.resolve("file"), "saved before switching\n");
      var switched = Utils.Git.commitAndSwitch(root.toFile(), "feature/shared");
      assertFalse(Utils.Notifications.hasErrors(switched.getNotifications()));
      assertEquals("feature/shared", git.getRepository().getBranch());
      assertTrue(git.status().call().isClean());
      Files.writeString(root.resolve("new"), "new document");
      assertFalse(Utils.Notifications.hasErrors(
          Utils.Git.commitAndSwitch(root.toFile(), original).getNotifications()));
      assertFalse(Files.exists(root.resolve("new")));
      var merged = Utils.Git.mergeChangesFrom(root.toFile(), "feature/shared");
      assertFalse(Utils.Notifications.hasErrors(merged.getNotifications()));
      assertTrue(Files.exists(root.resolve("new")));
      assertTrue(merged.getAddedPaths().contains("new"));
    }
  }

  @Test
  void invalidBranchDoesNotCommitPendingChanges() throws Exception {
    try (var git = repository()) {
      var head = git.getRepository().resolve("HEAD");
      Files.writeString(root.resolve("file"), "pending");
      var result = Utils.Git.commitAndSwitch(root.toFile(), "invalid name");
      assertTrue(Utils.Notifications.hasErrors(result.getNotifications()));
      assertEquals(head, git.getRepository().resolve("HEAD"));
      assertEquals("pending", Files.readString(root.resolve("file")));
    }
  }

  @Test
  void conflictingMergeRestoresOriginalBranchContents() throws Exception {
    try (var git = repository()) {
      String original = git.getRepository().getBranch();
      git.checkout().setCreateBranch(true).setName("other").call();
      Files.writeString(root.resolve("file"), "other\n");
      git.add().addFilepattern(".").call();
      git.commit().setMessage("Other").call();
      git.checkout().setName(original).call();
      Files.writeString(root.resolve("file"), "local\n");
      git.add().addFilepattern(".").call();
      git.commit().setMessage("Local").call();
      var head = git.getRepository().resolve("HEAD");
      var result = Utils.Git.mergeChangesFrom(root.toFile(), "other");
      assertTrue(Utils.Notifications.hasErrors(result.getNotifications()));
      assertEquals(head, git.getRepository().resolve("HEAD"));
      assertEquals("local\n", Files.readString(root.resolve("file")));
      assertTrue(git.status().call().isClean());
    }
  }

  @Test
  void remoteBranchIsCheckedOutWithTrackingEvenWhenATagHasTheSameName() throws Exception {
    try (var git = repository()) {
      var head = git.getRepository().resolve("HEAD");
      var config = git.getRepository().getConfig();
      config.setString("remote", "origin", "url", root.toUri().toString());
      config.setString("remote", "origin", "fetch", "+refs/heads/*:refs/remotes/origin/*");
      config.save();
      var remote = git.getRepository().updateRef("refs/remotes/origin/team/work");
      remote.setNewObjectId(head);
      remote.update();
      git.tag().setName("team/work").call();
      var result = Utils.Git.commitAndSwitch(root.toFile(), "team/work");
      assertFalse(Utils.Notifications.hasErrors(result.getNotifications()));
      assertEquals("team/work", git.getRepository().getBranch());
      assertNotNull(git.getRepository().exactRef("refs/heads/team/work"));
      assertEquals("origin", git.getRepository().getConfig().getString("branch", "team/work", "remote"));
    }
  }
}
