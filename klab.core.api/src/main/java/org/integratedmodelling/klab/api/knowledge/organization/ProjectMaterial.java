package org.integratedmodelling.klab.api.knowledge.organization;

import java.util.Collection;
import java.util.List;
import org.integratedmodelling.klab.api.data.Metadata;
import org.integratedmodelling.klab.api.knowledge.KlabAsset;
import org.integratedmodelling.klab.api.lang.Annotation;

/** Arbitrary project content, addressed as project/canonical/relative/path.ext. */
public class ProjectMaterial implements KlabAsset {
  private String projectName;
  private String path;
  private byte[] content;
  private String serviceId;
  private Metadata metadata = Metadata.create();

  public ProjectMaterial() {}
  public ProjectMaterial(String projectName, String path, byte[] content) {
    this.projectName = projectName; this.path = canonicalPath(path); this.content = content;
  }
  public String getProjectName() { return projectName; }
  public void setProjectName(String value) { projectName = value; }
  public String getPath() { return path; }
  public void setPath(String value) { path = canonicalPath(value); }
  public byte[] getContent() { return content; }
  public void setContent(byte[] value) { content = value; }
  public String getUrn() { return projectName + "/" + path; }
  public String getServiceId() { return serviceId; }
  public void setServiceId(String value) { serviceId = value; }
  public Metadata getMetadata() { return metadata; }
  public void setMetadata(Metadata value) { metadata = value; }
  public Collection<Annotation> getAnnotations() { return List.of(); }

  public static ProjectMaterial coordinates(String urn) {
    int slash = urn == null ? -1 : urn.indexOf('/');
    if (slash < 1) throw new IllegalArgumentException("Material URN must be project/relative/path.ext");
    return new ProjectMaterial(urn.substring(0, slash), urn.substring(slash + 1), null);
  }

  /** Reject aliases and platform-dependent paths rather than silently normalizing them. */
  public static String canonicalPath(String path) {
    if (path == null || path.isBlank() || path.startsWith("/") || path.contains("\\")
        || path.contains(":")) throw new IllegalArgumentException("A canonical relative path is required");
    for (String part : path.split("/", -1)) {
      String base = part.split("\\.", 2)[0].toUpperCase(java.util.Locale.ROOT);
      if (part.isBlank() || part.equals(".") || part.equals("..") || part.endsWith(".") || part.endsWith(" ")
          || part.chars().anyMatch(c -> c < 32 || "<>\"|?*".indexOf(c) >= 0)
          || base.matches("CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9]") || part.toLowerCase(java.util.Locale.ROOT).startsWith(".git"))
        throw new IllegalArgumentException("Invalid project-relative path: " + path);
    }
    // Protect canonical language locations, project configuration and repository control files.
    String lower = path.toLowerCase(java.util.Locale.ROOT);
    if (lower.startsWith("meta-inf/") || lower.equals("meta-inf") || lower.startsWith("resources/")
        || lower.equals("resources") || lower.equals(".gitignore") || lower.equals(".gitattributes")
        || lower.equals(".gitmodules") || ProjectStorage.getDocumentData(lower) != null)
      throw new IllegalArgumentException("Additional material cannot occupy a canonical project document path");
    return path;
  }
}
