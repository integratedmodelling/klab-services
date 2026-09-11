package org.integratedmodelling.klab.services.resolver;

import java.util.*;
import org.integratedmodelling.klab.api.documentation.FlowChart;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;

/** Detached diagnostic snapshot of the accepted resolution graph, not a runtime/replay graph. */
public final class ResolutionFlowChartAdapter implements FlowChart.Adapter<ResolutionGraph> {
  @Override
  public FlowChart adapt(ResolutionGraph source) {
    Objects.requireNonNull(source, "resolution graph");
    var graph = source.graph();
    var nodes = new ArrayList<>(graph.vertexSet());
    nodes.sort(Comparator.comparing(ResolutionFlowChartAdapter::kind)
        .thenComparing(n -> Objects.toString(n.getUrn(), "")));
    Map<Resolvable, String> ids = new HashMap<>();
    for (int i = 0; i < nodes.size(); i++) ids.put(nodes.get(i), "node-" + i);
    var edges = new ArrayList<>(graph.edgeSet());
    edges.sort(Comparator.comparing((ResolutionGraph.ResolutionEdge e) -> ids.get(graph.getEdgeSource(e)))
        .thenComparing(e -> ids.get(graph.getEdgeTarget(e)))
        .thenComparing(e -> Objects.toString(e.localName, ""))
        .thenComparingLong(e -> e.observationId));
    return FlowChart.builder("resolution")
        .metadata("schema", "klab.resolution-graph.v1")
        .metadata("scope", "accepted-resolution")
        .metadata("resolvedCoverage", source.getResolvedCoverage())
        .metadata("edgeDirection", "resolved-by")
        .root(root -> {
          root.label("Resolution graph").layout("elk.algorithm", "layered").layout("elk.direction", "RIGHT");
          for (var node : nodes) {
            root.node(ids.get(node), n -> {
              var label = node instanceof Observation observation && observation.getObservable() != null
                  ? observation.getObservable().getUrn() + " [" + observation.getId() + "]"
                  : Objects.toString(node.getUrn(), "unnamed");
              n.label(kind(node) + ": " + label)
                  .metadata("kind", kind(node)).metadata("urn", node.getUrn());
              if (node instanceof Observation o) {
                n.metadata("observationId", o.getId()).metadata("transientId", o.getTransientId());
                if (o.getGeometry() != null) n.metadata("geometry", o.getGeometry().encode());
                if (o.getObservable() != null) n.metadata("observable", o.getObservable().getUrn());
              } else if (node instanceof OperationTarget operation) {
                n.metadata("contextualization", operation.observable().getContextualization().name());
                if (operation.context() != null) n.metadata("contextObservationId", operation.context().getId());
                n.metadata("optionalModelDependency", operation.modelDependency() != null && operation.modelDependency().isOptional());
              } else if (node instanceof ObservationStrategy strategy) {
                n.metadata("rank", strategy.getRank()).metadata("namespace", strategy.getNamespace());
              } else if (node instanceof Model model) {
                n.metadata("namespace", model.getNamespace()).metadata("project", model.getProjectName());
              }
            });
          }
          int index = 0;
          for (var edge : edges) {
            root.link("edge-" + index++, ids.get(graph.getEdgeSource(edge)), ids.get(graph.getEdgeTarget(edge)), link -> {
              if (edge.localName != null) {
                link.getLabels().add(FlowChart.labelOf(edge.localName));
                link.getMetadata().put("binding", edge.localName);
              }
              if (edge.coverage != null) {
                link.getMetadata().put("coverage", edge.coverage.getCoverage());
                link.getMetadata().put("geometry", edge.coverage.encode());
              }
              var reference = source.getResolved(edge.observationId);
              if (reference != null) {
                link.getMetadata().put("referenceObservationId", reference.getId());
                link.getMetadata().put("referenceTransientId", reference.getTransientId());
                link.getMetadata().put("referenceObservable", reference.getObservable().getUrn());
              }
            });
          }
        }).build();
  }

  private static String kind(Resolvable node) {
    if (node instanceof OperationTarget) return "operation";
    if (node instanceof Observation) return "observation";
    if (node instanceof ObservationStrategy) return "strategy";
    if (node instanceof Model) return "model";
    if (node instanceof Observable) return "reference";
    return "resolvable";
  }
}
