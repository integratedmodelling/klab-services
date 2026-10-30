package org.integratedmodelling.common.authentication;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.nio.file.*;
import java.util.*;
import org.integratedmodelling.klab.api.authentication.*;
import org.integratedmodelling.klab.api.identities.UserIdentity;
import org.integratedmodelling.klab.api.scope.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CredentialVaultTest {
  @TempDir Path root;
  private final Scope admin = mock(Scope.class);
  private final UserScope alice = user("alice");
  private final UserScope bob = user("bob");
  private static UserScope user(String name) {
    var scope = mock(UserScope.class);
    var identity = mock(UserIdentity.class);
    when(scope.getUser()).thenReturn(identity);
    when(identity.getUsername()).thenReturn(name);
    when(identity.isAuthenticated()).thenReturn(true);
    when(identity.getGroups()).thenReturn(List.of());
    return scope;
  }
  private CredentialVault vault() { return new CredentialVault(root, root.resolve("key"), scope -> scope == admin); }
  private ExternalAuthenticationCredentials credential(String name) {
    var c = new ExternalAuthenticationCredentials();
    c.setName(name); c.setScheme("basic");
    c.setParameters(new HashMap<>(Map.of("username", "git", "password", "very-private-token")));
    c.setPrivileges(ResourcePrivileges.create("alice"));
    return c;
  }

  @Test void encryptsPersistsRedactsAndPreservesSecretsOnEdit() throws Exception {
    var vault = vault();
    var info = vault.save("https://github.com/org", credential("Repository access"), admin);
    assertEquals("Repository access", info.description());
    assertEquals(Map.of("username", "git"), info.parameters());
    assertFalse(info.toString().contains("very-private-token"));
    assertFalse(new String(Files.readAllBytes(root.resolve("credentials.vault")), java.nio.charset.StandardCharsets.ISO_8859_1).contains("very-private-token"));
    var update = credential("Renamed"); update.setId(info.key()); update.setParameters(Map.of("username", "git2"));
    vault.save("https://github.com/org", update, admin);
    var resolved = vault().resolve(info.key(), "https://github.com/org/repo.git", alice);
    assertEquals("very-private-token", resolved.getParameters().get("password"));
    assertEquals(List.of("git2", "very-private-token"), resolved.getCredentials());
    assertTrue(vault.delete(info.key(), admin));
    assertFalse(vault.delete(info.key(), admin));
    assertThrows(SecurityException.class, () -> vault().resolve(info.key(), "https://github.com/org/repo.git", alice));
  }

  @Test void rejectsUnauthorizedAdministrationAndUsageIncludingNullScopes() {
    var vault = vault();
    assertThrows(SecurityException.class, () -> vault.save("https://github.com", credential("test"), bob));
    assertThrows(SecurityException.class, () -> vault.list(null));
    assertThrows(SecurityException.class, () -> vault.schemas(bob));
    var info = vault.save("https://github.com/org", credential("test"), admin);
    assertThrows(SecurityException.class, () -> vault.resolve(info.key(), "https://github.com/org/repo", bob));
    assertThrows(SecurityException.class, () -> vault.resolve(info.key(), "https://github.com/org/repo", null));
    assertThrows(SecurityException.class, () -> vault.delete(info.key(), bob));
    when(alice.getUser().isAnonymous()).thenReturn(true);
    assertThrows(SecurityException.class, () -> vault.resolve(info.key(), "https://github.com/org/repo", alice));
  }

  @Test void bindsOriginPortAndPathWithoutPrefixOrUserInfoConfusion() {
    var vault = vault();
    String id = vault.save("https://github.com/org", credential("test"), admin).key();
    for (String endpoint : List.of("https://github.com.evil/org/repo", "http://github.com/org/repo",
        "https://github.com:444/org/repo", "https://github.com/organization/repo", "https://evil/github.com/org"))
      assertThrows(SecurityException.class, () -> vault.resolve(id, endpoint, alice), endpoint);
    assertNotNull(vault.resolve(id, "https://GITHUB.COM:443/org/repo", alice));
    for (String endpoint : List.of("https://user:secret@github.com/org", "https://github.com/org?token=secret", "github.com", "https://github.com/org%2frepo"))
      assertThrows(IllegalArgumentException.class, () -> vault.save(endpoint, credential("unsafe"), admin));
  }

  @Test void multipleSetsNeedAnExplicitIdAndIdsAreNotUpsertKeys() {
    var vault = vault();
    var first = vault.save("https://github.com", credential("first"), admin);
    var second = vault.save("https://github.com", credential("second"), admin);
    assertNotEquals(first.key(), second.key());
    assertThrows(IllegalArgumentException.class, () -> vault.resolve(null, "https://github.com/repo", alice));
    assertNotNull(vault.resolve(second.key(), "https://github.com/repo", alice));
    var unknown = credential("unknown"); unknown.setId("not-stored");
    assertThrows(IllegalArgumentException.class, () -> vault.save("https://github.com", unknown, admin));
    assertThrows(IllegalArgumentException.class, () -> vault.save("https://github.com", credential("FIRST"), admin));
  }

  @Test void corruptVaultOrLostKeyNeverResetsOrOverwritesStoredCredentials() throws Exception {
    var vault = vault(); vault.save("https://github.com", credential("test"), admin);
    var file = root.resolve("credentials.vault");
    byte[] encrypted = Files.readAllBytes(file); encrypted[encrypted.length - 1] ^= 1; Files.write(file, encrypted);
    assertThrows(IllegalStateException.class, () -> vault.list(admin));
    assertThrows(IllegalStateException.class, () -> vault.save("https://gitlab.com", credential("new"), admin));
    assertArrayEquals(encrypted, Files.readAllBytes(file));
    Files.delete(root.resolve("key"));
    assertThrows(IllegalStateException.class, () -> vault.list(admin));
    assertFalse(Files.exists(root.resolve("key")));
  }

  @Test void supportsConfiguredSchemasAndNeverExposesCustomSecrets() throws Exception {
    Files.writeString(root.resolve("credential-schemas.json"), """
      [{"id":"registry","label":"Registry key","mechanism":"bearer","fields":[
        {"name":"apiKey","label":"API key","secret":true,"required":true}]}]
      """);
    var vault = vault(); var c = credential("Registry"); c.setScheme("registry"); c.setParameters(Map.of("apiKey", "registry-secret"));
    var info = vault.save("https://registry.example", c, admin);
    assertEquals(Map.of(), info.parameters());
    assertEquals(List.of("apiKey"), info.configuredFields());
    assertEquals("bearer", vault.resolve(info.key(), "https://registry.example", alice).getScheme());
    var other = new CredentialVault(root.resolve("other"), root.resolve("other.key"), scope -> scope == admin);
    assertTrue(other.list(admin).isEmpty());
  }
  @Test void concurrentInstancesDoNotLoseCredentialSets() {
    var first = vault(); var second = vault();
    var a = java.util.concurrent.CompletableFuture.runAsync(() -> first.save("https://one.example", credential("one"), admin));
    var b = java.util.concurrent.CompletableFuture.runAsync(() -> second.save("https://two.example", credential("two"), admin));
    java.util.concurrent.CompletableFuture.allOf(a, b).join();
    assertEquals(2, vault().list(admin).size());
  }

  @Test void exclusionsOverridePublicAndForeignServiceScopesAreNotTrusted() {
    var vault = vault(); var c = credential("Shared");
    c.getPrivileges().setPublic(true); c.getPrivileges().getExcludedUsers().add("bob");
    String id = vault.save("https://github.com", c, admin).key();
    assertNotNull(vault.resolve(id, "https://github.com/repo", alice));
    assertThrows(SecurityException.class, () -> vault.resolve(id, "https://github.com/repo", bob));
    assertThrows(SecurityException.class, () -> vault.resolve(id, "https://github.com/repo", mock(ServiceScope.class)));
  }

  @Test void validatesRequiredFieldsAndCreatesPrivateKeyAndVaultFiles() throws Exception {
    var vault = vault(); var c = credential("Validated");
    c.setParameters(Map.of("username", "git"));
    assertThrows(IllegalArgumentException.class, () -> vault.save("https://github.com", c, admin));
    assertFalse(Files.exists(root.resolve("key")));
    var id = vault.save("https://github.com", credential("Valid"), admin).key();
    var update = credential("Valid"); update.setId(id); update.setParameters(Map.of("password", ""));
    assertThrows(IllegalArgumentException.class, () -> vault.save("https://github.com", update, admin));
    for (String name : List.of("key", "credentials.vault", "credentials.lock")) {
      Path path = root.resolve(name);
      if (Files.getFileStore(path).supportsFileAttributeView("posix"))
        assertEquals(java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"), Files.getPosixFilePermissions(path));
      else {
        var acl = Files.getFileAttributeView(path, java.nio.file.attribute.AclFileAttributeView.class).getAcl();
        assertEquals(1, acl.size()); assertEquals(Files.getOwner(root), acl.getFirst().principal());
      }
    }
  }

}
