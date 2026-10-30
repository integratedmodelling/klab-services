package org.integratedmodelling.klab.resources;

import java.io.*;
import java.nio.file.*;
import java.util.Comparator;
import java.util.zip.*;

/** Portable project snapshots. The single top-level directory preserves the project identity. */
public final class ProjectArchiveIO {
  private ProjectArchiveIO() {}

  public static InputStream exportProject(Path root, String project) throws IOException {
    checkName(project);
    var archive = Files.createTempFile("klab-project-export-", ".zip");
    try {
      try (var zip = new ZipOutputStream(Files.newOutputStream(archive)); var paths = Files.walk(root)) {
        for (var path : paths.filter(p -> Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS)).toList()) {
          var relative = root.relativize(path);
          if (java.util.stream.StreamSupport.stream(relative.spliterator(), false)
              .anyMatch(segment -> segment.toString().equalsIgnoreCase(".git"))) continue;
          zip.putNextEntry(new ZipEntry(project + "/" + relative.toString().replace('\\', '/')));
          Files.copy(path, zip);
          zip.closeEntry();
        }
      }
      return new FilterInputStream(Files.newInputStream(archive)) {
        @Override public void close() throws IOException {
          try { super.close(); } finally { Files.deleteIfExists(archive); }
        }
      };
    } catch (Throwable error) {
      Files.deleteIfExists(archive);
      throw error;
    }
  }

  /** Extract without overwriting a project or trusting archive paths and Git configuration. */
  public static String projectName(Path archive) throws IOException {
    try (var zip = new ZipFile(archive.toFile())) {
      var entries = zip.entries();
      if (!entries.hasMoreElements()) throw new IOException("Empty project archive");
      var name = entries.nextElement().getName().split("/")[0];
      checkName(name);
      return name;
    }
  }

  /** Extract without overwriting a project or trusting archive paths and Git configuration. */
  public static Path importProject(Path archive, Path workspace) throws IOException {
    Files.createDirectories(workspace);
    var staging = Files.createTempDirectory(workspace, ".project-import-");
    String project = null;
    long bytes = 0;
    int entries = 0;
    try {
      try (var zip = new ZipInputStream(Files.newInputStream(archive))) {
        ZipEntry entry;
        while ((entry = zip.getNextEntry()) != null) {
          if (++entries > 100000) throw new IOException("Too many project archive entries");
          var name = entry.getName();
          if (name.contains("\\") || name.startsWith("/") || name.contains(":"))
            throw new IOException("Invalid project archive path");
          var segments = name.split("/");
          for (var segment : segments)
            if (segment.equals("..") || segment.equals(".") || segment.equalsIgnoreCase(".git"))
              throw new IOException("Unsafe project archive path");
          checkName(segments[0]);
          if (project == null) project = segments[0];
          if (!project.equals(segments[0])) throw new IOException("Archive must contain one project directory");
          var target = staging.resolve(name).normalize();
          if (!target.startsWith(staging)) throw new IOException("Archive path escapes project");
          if (entry.isDirectory()) Files.createDirectories(target);
          else {
            Files.createDirectories(target.getParent());
            try (var output = Files.newOutputStream(target, StandardOpenOption.CREATE_NEW)) {
              byte[] buffer = new byte[8192];
              int count;
              while ((count = zip.read(buffer)) != -1) {
                bytes += count;
                if (bytes > 2L * 1024 * 1024 * 1024) throw new IOException("Project archive exceeds 2 GiB");
                output.write(buffer, 0, count);
              }
            }
          }
        }
      }
      if (project == null || !Files.isRegularFile(staging.resolve(project).resolve("META-INF/manifest.json")))
        throw new IOException("Archive has no project manifest");
      var destination = workspace.resolve(project);
      if (Files.exists(destination)) throw new FileAlreadyExistsException(destination.toString());
      return Files.move(staging.resolve(project), destination);
    } finally {
      try (var paths = Files.walk(staging)) {
        for (var path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
      }
    }
  }

  private static void checkName(String name) throws IOException {
    if (name == null || !name.matches("[a-zA-Z0-9][a-zA-Z0-9._-]*") || name.endsWith("."))
      throw new IOException("Invalid project directory name");
  }
}
