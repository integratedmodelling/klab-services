package org.integratedmodelling.klab.api.services.resources;

import java.util.List;
import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.services.resolver.ResolutionConstraint;

/** Catalog selection, not a Runtime request: no graph, transaction or semantic-cycle identity. */
public record ModelCandidateRequest(int version, List<String> namespaces, List<String> projects,
    List<String> scenarios) {
  public ModelCandidateRequest {
    if (version != 1) throw new IllegalArgumentException("Unsupported model candidate protocol");
    namespaces = namespaces == null ? List.of() : List.copyOf(namespaces);
    projects = projects == null ? List.of() : List.copyOf(projects);
    scenarios = scenarios == null ? List.of() : List.copyOf(scenarios);
  }

  public static ModelCandidateRequest from(Parameters<String> query) {
    var constraints = query == null ? List.<ResolutionConstraint>of()
        : query.getList("resolutionConstraints", ResolutionConstraint.class);
    return new ModelCandidateRequest(1,
        values(constraints, ResolutionConstraint.Type.ResolutionNamespace),
        values(constraints, ResolutionConstraint.Type.ResolutionProject),
        values(constraints, ResolutionConstraint.Type.Scenarios));
  }

  private static List<String> values(List<ResolutionConstraint> constraints, ResolutionConstraint.Type type) {
    return constraints == null ? List.of() : constraints.stream().filter(c -> c.getType() == type)
        .flatMap(c -> c.payload(String.class).stream()).distinct().sorted().toList();
  }

  public boolean visible(String namespace, String project,
      org.integratedmodelling.klab.api.lang.kim.KlabStatement.Scope visibility, boolean scenario) {
    boolean sameNamespace = namespace != null && namespaces.contains(namespace);
    boolean sameProject = project != null && projects.contains(project);
    if (visibility == org.integratedmodelling.klab.api.lang.kim.KlabStatement.Scope.PRIVATE)
      return sameNamespace;
    if (visibility == org.integratedmodelling.klab.api.lang.kim.KlabStatement.Scope.PROJECT_PRIVATE && !sameProject)
      return false;
    return sameNamespace || namespace != null && scenarios.contains(namespace) || !scenario;
  }
}
