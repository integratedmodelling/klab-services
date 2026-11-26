package org.integratedmodelling.klab.services.reasoner.internal;

import java.util.*;
import org.integratedmodelling.klab.api.knowledge.Worldview;
import org.integratedmodelling.klab.api.knowledge.impl.WorldviewImpl;
import org.integratedmodelling.klab.api.lang.kim.*;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.api.services.ResourcesService;
import org.integratedmodelling.klab.api.utils.Utils;

/** A project is the unit of precedence. Never revive deleted files from its lower priority copy. */
public final class WorldviewSources {
  private WorldviewSources() {}

  public static Worldview discover(Scope scope) {
    return discover(scope, false);
  }

  /** Resolve the owner with the same caller visibility and project precedence as full discovery. */
  public static ResourcesService providerForProject(UserScope scope, String project) {
    var providers = scope.getServices(ResourcesService.class);
    if (providers.size() <= 1) return scope.getService(ResourcesService.class);
    var selected = discover(scope, true);
    if (selected == null) return null;
    var owners = selected.getMetadata().get(Worldview.PROJECT_SOURCES);
    var id = owners instanceof Map<?, ?> map ? map.get(project) : null;
    if (id == null && selected instanceof WorldviewImpl impl) id = impl.getServiceId();
    for (var provider : providers) if (id != null && id.equals(provider.serviceId())) return provider;
    return null;
  }

  private static Worldview discover(Scope scope, boolean routingOnly) {
    var providers = scope.getServices(ResourcesService.class).stream()
        .sorted(Comparator.comparing((ResourcesService r) -> !Utils.URLs.isLocalHost(r.getUrl()))
            .thenComparing(r -> Objects.toString(r.serviceId(), ""))
            .thenComparing(r -> r.getUrl().toExternalForm()))
        .toList();
    var views = new ArrayList<Worldview>();
    var seen = new HashSet<String>();
    for (var provider : providers) {
      var status = provider.status();
      if (status == null || !status.isAvailable() || !provider.capabilities(scope).isWorldviewProvider()) continue;
      var identity = Objects.toString(provider.serviceId(), provider.getUrl().toExternalForm());
      if (seen.add(identity)) {
        var user = scope instanceof UserScope owner ? owner : null;
        views.addAll(routingOnly ? provider.listWorldviewHeaders(user) : provider.list(Worldview.class, user));
      }
    }
    return compose(views);
  }

  /** Inputs are in provider precedence order; each describes a complete provider snapshot. */
  static Worldview compose(List<Worldview> views) {
    if (views.isEmpty()) return null;
    var primary = views.getFirst();
    var compatible = views.stream().filter(v -> Objects.equals(primary.getUrn(), v.getUrn())).toList();
    if (compatible.size() == 1 || primary.isEmpty()) return primary;
    var owners = new HashMap<String, Worldview>();
    var ontologies = new LinkedHashMap<String, KimOntology>();
    var strategies = new LinkedHashMap<String, KimObservationStrategyDocument>();
    var bindings = new ArrayList<Worldview.AuthorityBinding>();
    var diagnostics = new ArrayList<org.integratedmodelling.klab.api.services.runtime.Notification>();
    for (var view : compatible) {
      var selected = new HashSet<String>();
      var manifest = view.getMetadata().get(Worldview.PROJECT_SOURCES);
      if (manifest instanceof Map<?, ?> projects) {
        for (Object key : projects.keySet()) {
          if (!(key instanceof String project) || project.isBlank())
            throw new IllegalArgumentException("Invalid worldview project name");
          owners.putIfAbsent(project, view);
          if (owners.get(project) == view) selected.add(project);
        }
      }
      collect(view, view.getOntologies(), owners, ontologies, selected);
      collect(view, view.getObservationStrategies(), owners, strategies, selected);
      if (selected.isEmpty()) continue;
      if (view.isEmpty()) throw new IllegalArgumentException("Incomplete Resources worldview " + view.getUrn());
      for (var binding : view.getAuthorityBindings()) {
        var ontology = ontologies.get(binding.sourceOntology());
        if (ontology != null && selected.contains(ontology.getProjectName())) bindings.add(binding);
      }
      for (var notification : view.getNotifications()) {
        var context = notification.getLexicalContext();
        if (context == null || context.getProjectUrn() == null || selected.contains(context.getProjectUrn()))
          diagnostics.add(notification);
      }
    }
    if (owners.isEmpty()) return primary;
    var result = new WorldviewImpl();
    result.setUrn(primary.getUrn());
    result.setWorldviewId(primary.getWorldviewId());
    result.getMetadata().putAll(primary.getMetadata());
    var projectSources = new TreeMap<String, String>();
    for (var entry : owners.entrySet())
      if (entry.getValue() instanceof WorldviewImpl impl && impl.getServiceId() != null)
        projectSources.put(entry.getKey(), impl.getServiceId());
    result.getMetadata().put(Worldview.PROJECT_SOURCES, projectSources);
    var revisions = new TreeMap<String, Object>();
    for (var entry : owners.entrySet()) {
      var revision = entry.getValue().getMetadata().get(
          org.integratedmodelling.klab.components.AuthorityComponentResolver.REVISION_METADATA);
      if (revision != null) revisions.put(entry.getKey(), revision);
    }
    result.getMetadata().put(org.integratedmodelling.klab.components.AuthorityComponentResolver.REVISION_METADATA,
        org.integratedmodelling.klab.api.services.reasoner.objects.SemanticValidationRequest.sourceHash(
            org.integratedmodelling.common.utils.Utils.Json.asString(revisions)));
    result.setAnnotations(new ArrayList<>(primary.getAnnotations()));
    result.setOntologies(order(ontologies, primary.getUrn()));
    result.setObservationStrategies(new ArrayList<>(strategies.values()));
    result.setAuthorityBindings(bindings);
    result.setNotifications(diagnostics);
    return result;
  }

  private static <T extends KlabDocument<?>> void collect(Worldview view, Collection<T> documents,
      Map<String, Worldview> owners, Map<String, T> result, Set<String> selected) {
    for (var document : documents) {
      String project = document.getProjectName();
      if (project == null || project.isBlank())
        throw new IllegalArgumentException("Missing project for " + document.getUrn());
      owners.putIfAbsent(project, view);
      if (owners.get(project) != view) continue;
      selected.add(project);
      if (result.putIfAbsent(document.getUrn(), document) != null)
        throw new IllegalArgumentException("Duplicate document in selected projects: " + document.getUrn());
    }
  }

  private static List<KimOntology> order(LinkedHashMap<String, KimOntology> documents, String root) {
    var ordered = new ArrayList<KimOntology>();
    var done = new HashSet<String>();
    var visiting = new HashSet<String>();
    visit(root, documents, ordered, done, visiting);
    for (String urn : documents.keySet()) visit(urn, documents, ordered, done, visiting);
    if (!ordered.getFirst().getUrn().equals(root))
      throw new IllegalArgumentException("Worldview root imports another worldview namespace: " + root);
    return ordered;
  }

  private static void visit(String urn, Map<String, KimOntology> documents, List<KimOntology> ordered,
      Set<String> done, Set<String> visiting) {
    if (done.contains(urn)) return;
    var document = documents.get(urn);
    if (document == null) throw new IllegalArgumentException("Missing worldview ontology: " + urn);
    if (!visiting.add(urn)) throw new IllegalArgumentException("Cyclic worldview import: " + urn);
    for (String dependency : document.getImportedOntologies()) visit(dependency, documents, ordered, done, visiting);
    visiting.remove(urn);
    done.add(urn);
    ordered.add(document);
  }
}
