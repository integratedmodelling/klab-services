package org.integratedmodelling.klab.utilities;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;
import org.integratedmodelling.klab.api.authentication.ExternalAuthenticationCredentials;

/** Consult the installed Git credential helpers without opening an interactive prompt. */
final class NativeGitCredentials {
  private NativeGitCredentials() {}

  static ExternalAuthenticationCredentials resolve(String endpoint, File directory) {
    java.net.URI uri;
    try { uri = java.net.URI.create(endpoint); }
    catch (IllegalArgumentException invalid) { return null; }
    if (!("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
        || uri.getUserInfo() != null || endpoint.contains("\n") || endpoint.contains("\r")) return null;
    Process process = null;
    try {
      var command = new ProcessBuilder("git", "credential", "fill");
      if (directory != null && directory.isDirectory()) command.directory(directory);
      command.redirectError(ProcessBuilder.Redirect.DISCARD);
      command.environment().put("GIT_TERMINAL_PROMPT", "0");
      command.environment().put("GCM_INTERACTIVE", "never");
      command.environment().put("GIT_ASKPASS", "");
      command.environment().put("SSH_ASKPASS", "");
      process = command.start();
      try (var input = process.getOutputStream()) {
        input.write(("url=" + endpoint + "\n\n").getBytes(StandardCharsets.UTF_8));
      }
      if (!process.waitFor(10, TimeUnit.SECONDS) || process.exitValue() != 0) return null;
      var response = new String(process.getInputStream().readNBytes(65536), StandardCharsets.UTF_8);
      var fields = new HashMap<String, String>();
      for (var line : response.split("\r?\n")) {
        int separator = line.indexOf('=');
        if (separator > 0) fields.put(line.substring(0, separator), line.substring(separator + 1));
      }
      if (!fields.containsKey("username") || !fields.containsKey("password")) return null;
      var credentials = new ExternalAuthenticationCredentials();
      credentials.setScheme("basic");
      // Bind the helper response to the exact repository, including redirects and push URLs.
      credentials.setEndpoint(endpoint);
      credentials.setParameters(java.util.Map.of("username", fields.get("username"), "password", fields.get("password")));
      return credentials;
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      return null;
    } catch (java.io.IOException unavailable) {
      return null;
    } finally {
      if (process != null && process.isAlive()) process.destroyForcibly();
    }
  }
}
