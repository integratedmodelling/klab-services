package org.integratedmodelling.klab.api.services.resources;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Objects;
import org.integratedmodelling.klab.api.knowledge.KlabAsset;
import org.integratedmodelling.klab.api.lang.kim.KimOntology;
import org.integratedmodelling.klab.api.lang.kim.KlabDocument;

/** Optional authoring identity; it never replaces a document's semantic URN. */
public final class AuthoringSource {
  public static final String ID = "klab.authoring.source";
  public static final String VERSION = "klab.authoring.sourceVersion";
  private static final String PREFIX = "urn:klab:authoring-source:v1:";
  private AuthoringSource() {}
  public record Selector(String service, String project, String id, String version, String urn) {}
  public static String id(KlabAsset asset) { return value(asset.getMetadata(), ID); }
  public static String id(ResourceSet.Resource change) { return value(change.getMetadata(), ID); }
  private static String value(java.util.Map<String,Object> metadata, String key) {
    return metadata != null && metadata.get(key) instanceof String value && !value.isBlank() ? value : null;
  }
  public static String key(KlabDocument<?> document) {
    return (id(document) == null ? "" : document.getServiceId() + ":") + document.getProjectName() + ":" + (document instanceof KimOntology ? "ontology:" : "namespace:")
        + (id(document) == null ? document.getUrn() : "source:" + id(document));
  }
  public static String responseIdentity(KlabDocument<?> document) {
    return key(document)+"@"+value(document.getMetadata(),VERSION);
  }
  public static String pathElement(KlabAsset asset) {
    return asset instanceof KlabDocument<?> && id(asset) != null
        ? KlabAsset.classify(asset) + "@" + asset.getServiceId() + ":" + id(asset) : asset.getUrn();
  }
  public static boolean same(KlabAsset left, KlabAsset right) {
    if (left instanceof KlabDocument<?> a && right instanceof KlabDocument<?> b
        && (id(a) != null || id(b) != null))
      return Objects.equals(a.getServiceId(), b.getServiceId()) && Objects.equals(a.getProjectName(), b.getProjectName())
          && KlabAsset.classify(a) == KlabAsset.classify(b)
          && id(a) != null && Objects.equals(id(a),id(b));
    return Objects.equals(left.getUrn(),right.getUrn());
  }
  public static boolean matches(ResourceSet.Resource change, KlabAsset asset) {
    if (!(asset instanceof KlabDocument<?> || asset instanceof org.integratedmodelling.klab.api.knowledge.organization.Project
        || asset instanceof org.integratedmodelling.klab.api.knowledge.organization.Workspace)) return false;
    if (KlabAsset.classify(asset) != change.getKnowledgeClass()) return false;
    if (asset instanceof KlabDocument<?> document && change.getProjectUrn() != null
        && !Objects.equals(document.getProjectName(),change.getProjectUrn())) return false;
    return id(change) == null ? Objects.equals(change.getResourceUrn(),asset.getUrn())
        : Objects.equals(change.getServiceId(),asset.getServiceId()) && Objects.equals(id(change),id(asset));
  }
  public static String selector(KlabDocument<?> document) {
    if (document.getMetadata().containsKey(ID) && id(document) == null)
      throw new IllegalArgumentException("Invalid authoring source identity");
    return id(document) == null ? document.getUrn()
        : selector(document.getServiceId(),document.getProjectName(),id(document),value(document.getMetadata(),VERSION),document.getUrn());
  }
  public static String selector(ResourceSet.Resource change) {
    return id(change) == null ? change.getResourceUrn()
        : selector(change.getServiceId(),change.getProjectUrn(),id(change),value(change.getMetadata(),VERSION),change.getResourceUrn());
  }
  private static String selector(String service,String project,String id,String version,String urn) {
    return PREFIX + encode(Objects.requireNonNull(service)) + ":" + encode(Objects.requireNonNull(project)) + ":" + encode(id) + ":" + encode(version == null ? "" : version) + ":" + encode(urn);
  }
  private static String encode(String value) { return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8)); }
  public static Selector decode(String value) {
    if (value == null || !value.startsWith(PREFIX)) return null;
    if (value.length() > 16384) throw new IllegalArgumentException("Authoring source selector is too large");
    var parts=value.substring(PREFIX.length()).split(":",-1);
    if(parts.length!=5)throw new IllegalArgumentException("Invalid authoring source selector");
    var selector=new Selector(decodePart(parts[0]),decodePart(parts[1]),decodePart(parts[2]),decodePart(parts[3]),decodePart(parts[4]));
    if(selector.service().isBlank() || selector.project().isBlank() || selector.id().isBlank() || selector.urn().isBlank())throw new IllegalArgumentException("Invalid authoring source selector");
    return selector;
  }
  private static String decodePart(String value) { return new String(Base64.getUrlDecoder().decode(value),StandardCharsets.UTF_8); }
  public static String modelUri(KlabDocument<?> document) {
    return "inmemory:///klab/"+(id(document)==null ? "document/" : "source/")
        +encode(key(document))+"."+document.getLanguage().fileExtension();
  }
  /** Diagnostic-only updates must replace a source snapshot even at unchanged bytes/mtime. */
  public static String diagnosticVersion(KlabDocument<?> document) {
    var value = new StringBuilder();
    for (var notification : document.getNotifications()) {
      value.append(notification.getLevel()).append(':').append(notification.getMessage()).append('\n');
      var context = notification.getLexicalContext();
      if (context != null) value.append(context.getProjectUrn()).append(':').append(context.getDocumentUrn())
          .append(':').append(context.getOffsetInDocument()).append(':').append(context.getLength()).append('\n');
    }
    return org.integratedmodelling.klab.api.services.reasoner.objects.SemanticValidationRequest.sourceHash(value.toString());
  }
  public static String label(KlabDocument<?> document) {
    return document.getUrn()+(id(document)==null ? "" : " ["+id(document)+"]");
  }
  /** Copy only server-issued authoring metadata into a new save payload. */
  public static void copy(KlabDocument<?> from, KlabDocument<?> to) {
    if (id(from) != null && to instanceof org.integratedmodelling.klab.api.lang.kim.impl.KlabDocumentImpl<?> bean)
      bean.setServiceId(from.getServiceId());
    for(var name:java.util.List.of(ID,VERSION)) {
      var value=from.getMetadata().get(name);if(value!=null)to.getMetadata().put(name,value);
    }
  }
}
