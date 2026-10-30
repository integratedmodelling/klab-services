package org.integratedmodelling.common.authentication;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.security.SecureRandom;
import java.util.*;
import java.util.function.Predicate;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.integratedmodelling.klab.api.authentication.*;
import org.integratedmodelling.klab.api.scope.*;

/** Encrypted, service-owned credential storage. Never exposed through a secret-reading HTTP API. */
public final class CredentialVault {
  private static final byte[] MAGIC = { 'K', 'L', 'C', 1 };
  private static final ObjectMapper JSON = new ObjectMapper();
  private static final Map<Path, java.util.concurrent.locks.ReentrantLock> LOCKS = new java.util.concurrent.ConcurrentHashMap<>();
  private final Path file;
  private final Path keyFile;
  private final Predicate<Scope> administrator;
  private final Map<String, CredentialSchema> schemas = new LinkedHashMap<>();
  private final SecureRandom random = new SecureRandom();

  public record Entry(String endpoint, ExternalAuthenticationCredentials credentials) {}

  public CredentialVault(Path directory, Path keyFile, Predicate<Scope> administrator) {
    this.file = directory.resolve("credentials.vault");
    this.keyFile = keyFile;
    this.administrator = administrator;
    builtin("basic", "Username and password", "basic", "username", "password");
    builtin("token", "Git access token", "basic", "username", "password");
    builtin("key", "API key / bearer token", "bearer", "key");
    builtin("oidc", "OAuth / OpenID Connect client", "oauth2", "grant_type", "client_id", "client_secret", "token_endpoint");
    builtin("s3", "S3 access keys", "s3", "accessKey", "secretKey", "sessionToken");
    builtin("ssh", "SSH private key", "ssh", "username", "privateKey", "passphrase");
    Path configuration = directory.resolve("credential-schemas.json");
    if (Files.exists(configuration)) {
      try {
        for (CredentialSchema schema : JSON.readValue(Files.readAllBytes(configuration),
            new TypeReference<List<CredentialSchema>>() {})) {
          validateSchema(schema);
          schemas.put(schema.id(), schema);
        }
      } catch (Exception e) { throw new IllegalStateException("Invalid credential schema configuration"); }
    }
  }

  private void builtin(String id, String label, String mechanism, String... fields) {
    var definitions = new ArrayList<CredentialSchema.Field>();
    for (String field : fields) definitions.add(new CredentialSchema.Field(field, field,
        !Set.of("username", "client_id", "grant_type", "token_endpoint", "accessKey").contains(field),
        !Set.of("passphrase", "sessionToken").contains(field)));
    schemas.put(id, new CredentialSchema(id, label, mechanism, definitions));
  }

  private static void validateSchema(CredentialSchema schema) {
    if (schema == null || schema.id() == null || !schema.id().matches("[a-zA-Z0-9._-]{1,80}")
        || schema.label() == null || schema.mechanism() == null || schema.fields() == null
        || schema.fields().isEmpty()) throw new IllegalArgumentException("Invalid credential schema");
    var names = new HashSet<String>();
    for (var field : schema.fields()) {
      if (field == null || field.name() == null || !field.name().matches("[a-zA-Z0-9._-]{1,80}")
          || !names.add(field.name())) throw new IllegalArgumentException("Invalid credential field");
    }
  }

  private void requireAdmin(Scope scope) {
    if (scope == null || !administrator.test(scope)) throw new SecurityException("Credential administration requires an administrator");
  }

  public List<CredentialSchema> schemas(Scope scope) {
    requireAdmin(scope);
    return List.copyOf(schemas.values());
  }

  public synchronized List<ExternalAuthenticationCredentials.CredentialInfo> list(Scope scope) {
    requireAdmin(scope);
    return read().values().stream().map(this::info).toList();
  }

  private ExternalAuthenticationCredentials.CredentialInfo info(Entry entry) {
    var c = entry.credentials();
    var visible = new LinkedHashMap<String, String>();
    var schema = schemas.get(c.getScheme());
    if (schema != null) for (var field : schema.fields()) {
      if (!field.secret() && c.getParameters().containsKey(field.name())) visible.put(field.name(), c.getParameters().get(field.name()));
    }
    return new ExternalAuthenticationCredentials.CredentialInfo(entry.endpoint(), c.description(), c.getScheme(),
        c.getId(), visible, List.copyOf(c.getParameters().keySet()), c.getPrivileges());
  }

  /** Missing fields preserve their previous value on update; an explicit empty value clears optional fields. */
  public synchronized ExternalAuthenticationCredentials.CredentialInfo save(
      String endpoint, ExternalAuthenticationCredentials input, Scope scope) {
    requireAdmin(scope);
    try (var lease = lock()) {
      if (input == null) throw new IllegalArgumentException("Missing credential set");
      endpoint = normalize(endpoint);
      var schema = schemas.get(input.getScheme());
      if (schema == null) throw new IllegalArgumentException("Unknown credential schema");
      var entries = read();
      String id = input.getId();
      boolean creating = id == null || id.isBlank();
      if (creating) id = UUID.randomUUID().toString();
      else if (!entries.containsKey(id)) throw new IllegalArgumentException("Unknown credential set ID");
      var old = entries.get(id);
      if (old != null && !old.credentials().getScheme().equals(input.getScheme()))
        throw new IllegalArgumentException("Create a new set to change its schema");
      if (input.getName() == null || input.getName().isBlank() || input.getName().length() > 200)
        throw new IllegalArgumentException("A credential set name is required (maximum 200 characters)");
      for (var entry : entries.values()) if (!entry.credentials().getId().equals(id)
          && entry.credentials().getName().equalsIgnoreCase(input.getName().trim()))
        throw new IllegalArgumentException("Credential set name already exists");
      var values = new LinkedHashMap<String, String>();
      if (old != null) values.putAll(old.credentials().getParameters());
      if (input.getParameters() != null) values.putAll(input.getParameters());
      // Compatibility with existing callers. Positional values never appear in returned metadata.
      if (input.getCredentials() != null && !input.getCredentials().isEmpty()) {
        if (input.getCredentials().size() != schema.fields().size()) throw new IllegalArgumentException("Incorrect credential field count");
        for (int i = 0; i < schema.fields().size(); i++) values.put(schema.fields().get(i).name(), input.getCredentials().get(i));
      }
      var allowed = new HashSet<String>();
      for (var field : schema.fields()) {
        allowed.add(field.name());
        String value = values.get(field.name());
        if (field.required() && (value == null || value.isBlank())) throw new IllegalArgumentException("Missing required credential field: " + field.name());
      }
      if (!allowed.containsAll(values.keySet())) throw new IllegalArgumentException("Unknown credential field");
      values.values().removeIf(v -> v == null || v.isEmpty());
      var stored = new ExternalAuthenticationCredentials();
      stored.setId(id);
      stored.setName(input.getName().trim());
      stored.setScheme(schema.id());
      stored.setParameters(values);
      var privileges = input.getPrivileges() != null ? input.getPrivileges()
          : old == null ? ResourcePrivileges.empty() : old.credentials().getPrivileges();
      if (privileges.getAllowedUsers() == null || privileges.getAllowedGroups() == null
          || privileges.getExcludedUsers() == null || privileges.getExcludedGroups() == null
          || privileges.getAllowedServices() == null)
        throw new IllegalArgumentException("Credential usage grants cannot contain null lists");
      stored.setPrivileges(privileges);
      var entry = new Entry(endpoint, stored);
      entries.put(id, entry);
      write(entries);
      return info(entry);
    }
  }

  public synchronized boolean delete(String id, Scope scope) {
    requireAdmin(scope);
    try (var lease = lock()) {
      var entries = read();
      if (entries.remove(id) == null) return false;
      write(entries);
      return true;
    }
  }

  /** Resolve by ID and verify the actual destination before releasing material to a local component. */
  public synchronized ExternalAuthenticationCredentials resolve(String id, String endpoint, Scope scope) {
    if (scope == null) throw new SecurityException("An authenticated scope is required");
    String destination = normalize(endpoint);
    var entries = read();
    Entry entry;
    if (id != null && !id.isBlank()) {
      entry = entries.get(id);
      if (entry == null || !matches(entry.endpoint(), destination) || !authorized(entry, scope))
        throw new SecurityException("Credential set is unavailable for this operation");
    } else {
      var candidates = entries.values().stream().filter(e -> matches(e.endpoint(), destination) && authorized(e, scope)).toList();
      if (candidates.isEmpty()) return null;
      if (candidates.size() != 1) throw new IllegalArgumentException("Select a credential set ID: multiple sets match this endpoint");
      entry = candidates.getFirst();
    }
    // Deserialize a fresh object: consumers cannot mutate persisted state. Map custom schemas to mechanisms.
    var result = entry.credentials();
    var schema = schemas.get(result.getScheme());
    if (schema == null) throw new IllegalStateException("Credential schema is no longer configured");
    result.setScheme(schema.mechanism());
    result.setEndpoint(entry.endpoint());
    var positional = new ArrayList<String>();
    for (var field : schema.fields()) positional.add(result.getParameters().getOrDefault(field.name(), ""));
    result.setCredentials(positional);
    return result;
  }

  private boolean authorized(Entry entry, Scope scope) {
    if (administrator.test(scope)) return true;
    var privileges = entry.credentials().getPrivileges();
    return privileges != null && scope instanceof UserScope user && user.getUser() != null
        && user.getUser().isAuthenticated() && !user.getUser().isAnonymous()
        && privileges.checkAuthorization(user.getUser().getUsername(), user.getUser().getGroups());
  }

  static String normalize(String endpoint) {
    try {
      URI uri = URI.create(endpoint).normalize();
      if (uri.getHost() == null || uri.getScheme() == null || uri.getUserInfo() != null
          || uri.getQuery() != null || uri.getFragment() != null || uri.getRawPath().contains("%"))
        throw new IllegalArgumentException();
      String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
      if (!Set.of("https", "http", "ssh", "s3").contains(scheme)) throw new IllegalArgumentException();
      int port = uri.getPort();
      if ((scheme.equals("https") && port == 443) || (scheme.equals("http") && port == 80)
          || (scheme.equals("ssh") && port == 22)) port = -1;
      String path = uri.getPath().replaceAll("/+$", "");
      return new URI(scheme, null, uri.getHost().toLowerCase(Locale.ROOT), port, path, null, null).toString();
    } catch (Exception e) { throw new IllegalArgumentException("An absolute endpoint URL without user information, query or fragment is required"); }
  }

  /** Transport adapters must recheck redirected destinations and push URLs before supplying secrets. */
  public static boolean matchesEndpoint(String bound, String destination) {
    try { return bound != null && matches(normalize(bound), normalize(destination)); }
    catch (IllegalArgumentException e) { return false; }
  }

  private static boolean matches(String bound, String destination) {
    return destination.equals(bound) || destination.startsWith(bound + "/");
  }

  private Map<String, Entry> read() {
    if (!Files.exists(file)) return new LinkedHashMap<>();
    try {
      byte[] data = Files.readAllBytes(file);
      if (data.length < 32 || !Arrays.equals(MAGIC, Arrays.copyOf(data, 4))) throw new IllegalStateException();
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      byte[] material = key(false);
      try { cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(material, "AES"), new GCMParameterSpec(128, Arrays.copyOfRange(data, 4, 16))); }
      finally { Arrays.fill(material, (byte) 0); }
      cipher.updateAAD(MAGIC);
      byte[] plaintext = cipher.doFinal(data, 16, data.length - 16);
      try { return JSON.readValue(plaintext, new TypeReference<LinkedHashMap<String, Entry>>() {}); }
      finally { Arrays.fill(plaintext, (byte) 0); }
    } catch (Exception e) { throw new IllegalStateException("Credential vault cannot be decrypted; verify its key and integrity"); }
  }

  private void write(Map<String, Entry> entries) {
    Path temporary = null;
    try {
      Files.createDirectories(file.getParent());
      byte[] key = key(!Files.exists(file));
      byte[] nonce = new byte[12];
      random.nextBytes(nonce);
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, nonce));
      Arrays.fill(key, (byte) 0);
      cipher.updateAAD(MAGIC);
      byte[] plaintext = JSON.writeValueAsBytes(entries);
      byte[] encrypted;
      try { encrypted = cipher.doFinal(plaintext); }
      finally { Arrays.fill(plaintext, (byte) 0); }
      temporary = Files.createTempFile(file.getParent(), "credentials-", ".tmp", privateAttributes(file.getParent()));
      try (var output = java.nio.channels.FileChannel.open(temporary, StandardOpenOption.WRITE)) {
        var buffer = ByteBuffer.allocate(16 + encrypted.length).put(MAGIC).put(nonce).put(encrypted);
        buffer.flip();
        while (buffer.hasRemaining()) output.write(buffer);
        output.force(true);
      }
      Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    } catch (Exception e) { throw new IllegalStateException("Credential vault could not be saved securely"); }
    finally { if (temporary != null) try { Files.deleteIfExists(temporary); } catch (Exception ignored) {} }
  }

  private final class Lease implements AutoCloseable {
    private final java.util.concurrent.locks.ReentrantLock mutex;
    private final java.nio.channels.FileChannel channel;
    private final java.nio.channels.FileLock fileLock;
    Lease(java.util.concurrent.locks.ReentrantLock mutex, java.nio.channels.FileChannel channel,
        java.nio.channels.FileLock fileLock) { this.mutex = mutex; this.channel = channel; this.fileLock = fileLock; }
    @Override public void close() {
      try { fileLock.release(); } catch (java.io.IOException ignored) {}
      try { channel.close(); } catch (java.io.IOException ignored) {}
      mutex.unlock();
    }
  }

  private Lease lock() {
    var mutex = LOCKS.computeIfAbsent(file.toAbsolutePath().normalize(), ignored -> new java.util.concurrent.locks.ReentrantLock());
    mutex.lock();
    java.nio.channels.FileChannel channel = null;
    try {
      Files.createDirectories(file.getParent());
      channel = java.nio.channels.FileChannel.open(file.resolveSibling("credentials.lock"),
          Set.of(StandardOpenOption.CREATE, StandardOpenOption.WRITE), privateAttributes(file.getParent()));
      return new Lease(mutex, channel, channel.lock());
    } catch (Exception e) {
      if (channel != null) try { channel.close(); } catch (java.io.IOException ignored) {}
      mutex.unlock();
      throw new IllegalStateException("Credential vault could not be locked securely");
    }
  }

  private byte[] key(boolean create) throws Exception {
    if (!Files.exists(keyFile)) {
      if (!create) throw new IllegalStateException("Missing vault key");
      Files.createDirectories(keyFile.toAbsolutePath().getParent());
      byte[] key = new byte[32];
      random.nextBytes(key);
      try {
        Files.createFile(keyFile, privateAttributes(keyFile.toAbsolutePath().getParent()));
        Files.write(keyFile, key);
      } catch (FileAlreadyExistsException ignored) { /* another vault sharing the configured key */ }
      finally { Arrays.fill(key, (byte) 0); }
    }
    byte[] key = Files.readAllBytes(keyFile);
    if (key.length != 32) throw new IllegalStateException("Invalid vault key");
    return key;
  }

  private static FileAttribute<?>[] privateAttributes(Path parent) throws Exception {
    if (Files.getFileStore(parent).supportsFileAttributeView("posix"))
      return new FileAttribute<?>[] { PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")) };
    if (Files.getFileStore(parent).supportsFileAttributeView("acl")) {
      var owner = Files.getOwner(parent);
      var acl = List.of(AclEntry.newBuilder().setType(AclEntryType.ALLOW).setPrincipal(owner)
          .setPermissions(EnumSet.allOf(AclEntryPermission.class)).build());
      return new FileAttribute<?>[] { new FileAttribute<List<AclEntry>>() {
        public String name() { return "acl:acl"; }
        public List<AclEntry> value() { return acl; }
      }};
    }
    throw new IllegalStateException("Owner-only file permissions are required");
  }
}
