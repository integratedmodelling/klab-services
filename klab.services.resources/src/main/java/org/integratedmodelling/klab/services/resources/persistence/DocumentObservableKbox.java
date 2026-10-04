package org.integratedmodelling.klab.services.resources.persistence;

import java.util.*;
import org.integratedmodelling.klab.api.knowledge.Concept;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.integratedmodelling.klab.api.services.ResourcesService;
import org.integratedmodelling.klab.api.services.runtime.Channel;

/** Independent document-backed semantic dictionary. No H2 database is constructed. */
public class DocumentObservableKbox implements AutoCloseable {
  protected final ResourcesService resourceService;
  protected final KboxDocumentStore documents;
  public DocumentObservableKbox(ResourcesService service, KboxDocumentStore documents) {
    this.resourceService = Objects.requireNonNull(service);
    this.documents = Objects.requireNonNull(documents);
  }
  protected Reasoner reasoner() { return resourceService.serviceScope().getService(Reasoner.class); }
  public long getConceptId(Concept concept) {
    var document = documents.get("concept:" + concept.getUrn());
    return document == null ? -1 : ((Number) document.get("id")).longValue();
  }
  public long requireConceptId(Concept concept, Channel monitor) {
    String key = "concept:" + concept.getUrn();
    var document = documents.get(key);
    var core = reasoner().coreObservable(concept);
    String coreUrn = core == null ? "" : core.getUrn();
    if (document == null) document = documents.insertIfAbsent(key,
        Map.of("kind", "concept", "definition", concept.getUrn(), "core", coreUrn, "id", documents.nextId()));
    if (core != null && !coreUrn.equals(document.get("core"))) {
      document = new HashMap<>(document);
      document.put("core", coreUrn);
      documents.put(key, document);
    }
    return ((Number) document.get("id")).longValue();
  }

  /** Rebuild core-head indexes after a worldview change before serving discovery requests. */
  public void refreshSemanticIndex() {
    for (var document : documents.find("kind", List.of("concept"))) {
      var concept = reasoner().resolveConcept((String) document.get("definition"));
      var core = concept == null ? null : reasoner().coreObservable(concept);
      var updated = new HashMap<>(document);
      updated.put("core", core == null ? "" : core.getUrn());
      documents.put("concept:" + document.get("definition"), updated);
    }
  }

  public String getTypeDefinition(long id) {
    return documents.find("id", List.of(id)).stream()
        .filter(d -> "concept".equals(d.get("kind")))
        .map(d -> (String) d.get("definition")).findFirst().orElse(null);
  }
  public Observable getType(long id) {
    var definition = getTypeDefinition(id);
    return definition == null ? null : reasoner().resolveObservable(definition);
  }
  public List<String> getKnownDefinitions() {
    return documents.find("kind", List.of("concept")).stream()
        .map(d -> (String) d.get("definition")).sorted().toList();
  }
  public Set<Long> getCompatibleTypeIds(Observable observable, Concept context) {
    var reasoner = reasoner();
    var core = reasoner.coreObservable(observable);
    Set<Long> result = new HashSet<>();
    if (core == null) return result;
    Set<String> heads = new HashSet<>();
    for (var parent : reasoner.resolving(core)) {
      heads.add(parent.getUrn());
      String replacement = parent.getUrn();
      if (observable.getSpecializedComponents() != null) {
        for (var pair : observable.getSpecializedComponents()) {
          String urn = pair.getSecond().getUrn();
          replacement = replacement.replace(pair.getFirst().getUrn(), urn.contains(" ") ? "(" + urn + ")" : urn);
        }
        var resolved = reasoner.resolveConcept(replacement);
        if (resolved != null) heads.add(resolved.getUrn());
      }
    }
    // The empty head contains unresolved definitions, which must be retried. Resolved heads are
    // indexed so candidate lookup does not load the entire semantic dictionary per request.
    Set<String> indexedHeads = new HashSet<>(heads);
    indexedHeads.add("");
    for (var document : documents.find("core", indexedHeads)) {
      var candidate = reasoner.resolveConcept((String) document.get("definition"));
      if (candidate == null) continue;
      var candidateCore = reasoner.coreObservable(candidate);
      if (candidateCore != null && "".equals(document.get("core"))) requireConceptId(candidate, resourceService.serviceScope());
      if (candidateCore != null && heads.contains(candidateCore.getUrn())
          && reasoner.semanticDistance(candidate, observable, context) >= 0)
        result.add(((Number) document.get("id")).longValue());
    }
    return result;
  }
  @Override public void close() { documents.close(); }
}
