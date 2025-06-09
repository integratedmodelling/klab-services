//package org.integratedmodelling.klab.api.data;
//
//import java.util.ArrayList;
//import java.util.LinkedHashMap;
//import java.util.List;
//import java.util.Map;
//
///**
// * Java beans that serialize to a JSON graph usable for visualization and transport, compliant with
// * the barely interesting JSON graph schema from
// * https://github.com/jsongraph/json-graph-specification.
// *
// * <p>This is the transport format used for the various endpoints that produce graphs to face the
// * user, such as the {@link org.integratedmodelling.klab.api.digitaltwin.DigitalTwin}'s {@link
// * KnowledgeGraph} or any visualization of the {@link
// * org.integratedmodelling.klab.api.knowledge.Worldview}. {@link
// * org.integratedmodelling.klab.api.provenance.Provenance} may also be representable as a graph,
// * while the {@link org.integratedmodelling.klab.api.services.runtime.Dataflow} requires more
// * sophisticated visualization.
// *
// * <p>Apart from the graph type, everything about the objects represented by the nodes and edges is
// * defined in the user-defined metadata. According to graph type, these may be matched to an
// * externally provided model, while visualization can simply be created using the bean and
// * conventions based on types.
// *
// * <p>If we want to put up a good face and pretend this is a "standard", using media type <code>
// * application/vnd.jgf+json</code> will make those who spent time on this smile.
// */
//public record RuntimeAssetGraph(
////    boolean directed,
////    String type,
////    String label,
//    Map<String, String> metadata,
//    Map<String, Node> nodes,
//    List<Edge> edges) {
//
//  public RuntimeAssetGraph {
//    metadata = new LinkedHashMap<>();
//    nodes = new LinkedHashMap<>();
//    edges = new ArrayList<>();
//  }
//
//  public record Node(String label, Map<String, String> metadata, RuntimeAsset asset) {
//    public Node {
//      metadata = new LinkedHashMap<>();
//    }
//
//    public Node(RuntimeAsset asset) {
//      this(asset.getId() + "", new LinkedHashMap<>(), asset);
//    }
//  }
//
//  public record Edge(
//      String source,
//      String target,
//      String relationship,
//      boolean directed,
//      String label,
//      Map<String, String> metadata) {
//    public Edge {
//      metadata = new LinkedHashMap<>();
//    }
//  }
//}
