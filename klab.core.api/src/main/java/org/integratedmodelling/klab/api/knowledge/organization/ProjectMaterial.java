package org.integratedmodelling.klab.api.knowledge.organization;

import java.util.Collection;
import java.util.List;
import org.integratedmodelling.klab.api.data.Metadata;
import org.integratedmodelling.klab.api.knowledge.KlabAsset;
import org.integratedmodelling.klab.api.lang.Annotation;

/**
 * Arbitrary project content, addressed as project/canonical/relative/path.ext.
 * Directory URNs end in '/', with project/ denoting the read-only material listing root.
 * Retrieve directories through the generic Resources retrieve endpoint to list immediate entries
 * without their contents. Submit a directory with ADD to create it; DELETE removes empty directories.
 */
public class ProjectMaterial implements KlabAsset {
  private String projectName;
  private String path;
  private byte[] content;
  private String serviceId;
  private boolean directory;
  private long size;
  private List<ProjectMaterial> entries = new java.util.ArrayList<>();
  private Metadata metadata = Metadata.create();

  public ProjectMaterial() {}
  public ProjectMaterial(String projectName, String path, byte[] content) {
    this.projectName = projectName; setPath(path); this.content = content;
  }
  public String getProjectName() { return projectName; }
  public void setProjectName(String value) { projectName = value; }
  public String getPath() { return path; }
  public void setPath(String value) { path = "".equals(value) ? "" : canonicalPath(value); }
  public boolean isDirectory() { return directory; }
  public void setDirectory(boolean value) { directory = value; }
  public long getSize() { return size; }
  public void setSize(long value) { size = value; }
  public List<ProjectMaterial> getEntries() { return entries; }
  public void setEntries(List<ProjectMaterial> value) { entries = value == null ? new java.util.ArrayList<>() : value; }
  public byte[] getContent() { return content; }
  public void setContent(byte[] value) { content = value; }
  public String getUrn() { return projectName + "/" + path + (directory && !path.isEmpty() ? "/" : ""); }
  public String getServiceId() { return serviceId; }
  public void setServiceId(String value) { serviceId = value; }
  public Metadata getMetadata() { return metadata; }
  public void setMetadata(Metadata value) { metadata = value; }
  public Collection<Annotation> getAnnotations() { return List.of(); }

  public static ProjectMaterial coordinates(String urn) {
    int slash = urn == null ? -1 : urn.indexOf('/');
    if (slash < 1) throw new IllegalArgumentException("Material URN must be project/relative/path.ext");
    boolean directory = urn.endsWith("/");
    String path = urn.substring(slash + 1);
    if (path.equals("/")) throw new IllegalArgumentException("A canonical material URN is required");
    if (directory && !path.isEmpty()) path = path.substring(0, path.length() - 1);
    var material = new ProjectMaterial(urn.substring(0, slash), path, null);
    material.setDirectory(directory);
    return material;
  }

  /** Reject aliases and platform-dependent paths rather than silently normalizing them. */
  public static String canonicalPath(String path) {
    if (path == null || path.isBlank() || path.startsWith("/") || path.contains("\\")
        || path.contains(":")) throw new IllegalArgumentException("A canonical relative path is required");
    for (String part : path.split("/", -1)) {
      if (part.isEmpty() || !((part.charAt(0) >= 'a' && part.charAt(0) <= 'z')
          || (part.charAt(0) >= 'A' && part.charAt(0) <= 'Z')
          || (part.charAt(0) >= '0' && part.charAt(0) <= '9')))
        throw new IllegalArgumentException("Material file and folder names must start with an ASCII letter or number: " + path);
      String base = part.split("\\.", 2)[0].toUpperCase(java.util.Locale.ROOT);
      if (part.isBlank() || part.equals(".") || part.equals("..") || part.endsWith(".") || part.endsWith(" ")
          || part.chars().anyMatch(c -> c < 32 || "<>\"|?*".indexOf(c) >= 0)
          || base.matches("CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9]") || part.toLowerCase(java.util.Locale.ROOT).startsWith(".git"))
        throw new IllegalArgumentException("Invalid project-relative path: " + path);
    }
    // Protect canonical language locations, project configuration and repository control files.
    String lower = path.toLowerCase(java.util.Locale.ROOT);
    String first = lower.split("/", 2)[0];
    if (java.util.Set.of("meta-inf", "resources", "src", "apps", "behaviors", "scripts",
            "testcases", "strategies", "docs", "components").contains(first)
        || lower.equals(".gitignore") || lower.equals(".gitattributes")
        || lower.equals(".gitmodules") || ProjectStorage.getDocumentData(lower) != null)
      throw new IllegalArgumentException("Additional material cannot occupy a canonical project document path");
    return path;
  }
}
