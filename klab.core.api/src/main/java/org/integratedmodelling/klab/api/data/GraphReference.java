package org.integratedmodelling.klab.api.data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Java beans that serialize to a JSON graph usable for visualization and transport, compliant with
 * the barely interesting JSON graph schema from
 * https://github.com/jsongraph/json-graph-specification.
 *
 * <p>This is the transport format used for the various endpoints that produce graphs to face the
 * user, such as the {@link org.integratedmodelling.klab.api.digitaltwin.DigitalTwin}'s {@link
 * KnowledgeGraph} or any visualization of the {@link
 * org.integratedmodelling.klab.api.knowledge.Worldview}. {@link
 * org.integratedmodelling.klab.api.provenance.Provenance} may also be representable as a graph,
 * while the {@link org.integratedmodelling.klab.api.services.runtime.Dataflow} requires more
 * sophisticated visualization.
 *
 * <p>Apart from the graph type, everything about the objects represented by the nodes and edges is
 * defined in the user-defined metadata. According to graph type, these may be matched to an
 * externally provided model, while visualization can simply be created using the bean and
 * conventions based on types.
 *
 * <p>If we want to put up a good face and pretend this is a standard, using media type <code>
 * application/vnd.jgf+json</code> will make those who spent time on this smile.
 */
public class GraphReference {

  private boolean directed;
  private String type;
  private String label;
  private Map<String, String> metadata = new LinkedHashMap<>();
  private Map<String, Node> nodes = new LinkedHashMap<>();
  private List<Edge> edges = new ArrayList<>();

  public static class Node {
    private String label;
    private Map<String, String> metadata = new LinkedHashMap<>();

    public String getLabel() {
      return label;
    }

    public void setLabel(String label) {
      this.label = label;
    }

    public Map<String, String> getMetadata() {
      return metadata;
    }

    public void setMetadata(Map<String, String> metadata) {
      this.metadata = metadata;
    }
  }

  public static class Edge {
    private String source;
    private String target;
    private String relationship;
    private boolean directed;
    private String label;
    private Map<String, String> metadata = new LinkedHashMap<>();

    public String getSource() {
      return source;
    }

    public void setSource(String source) {
      this.source = source;
    }

    public String getTarget() {
      return target;
    }

    public void setTarget(String target) {
      this.target = target;
    }

    public String getRelationship() {
      return relationship;
    }

    public void setRelationship(String relationship) {
      this.relationship = relationship;
    }

    public boolean isDirected() {
      return directed;
    }

    public void setDirected(boolean directed) {
      this.directed = directed;
    }

    public String getLabel() {
      return label;
    }

    public void setLabel(String label) {
      this.label = label;
    }

    public Map<String, String> getMetadata() {
      return metadata;
    }

    public void setMetadata(Map<String, String> metadata) {
      this.metadata = metadata;
    }
  }

  public boolean isDirected() {
    return directed;
  }

  public void setDirected(boolean directed) {
    this.directed = directed;
  }

  public String getType() {
    return type;
  }

  public void setType(String type) {
    this.type = type;
  }

  public String getLabel() {
    return label;
  }

  public void setLabel(String label) {
    this.label = label;
  }

  public Map<String, String> getMetadata() {
    return metadata;
  }

  public void setMetadata(Map<String, String> metadata) {
    this.metadata = metadata;
  }

  public Map<String, Node> getNodes() {
    return nodes;
  }

  public void setNodes(Map<String, Node> nodes) {
    this.nodes = nodes;
  }

  public List<Edge> getEdges() {
    return edges;
  }

  public void setEdges(List<Edge> edges) {
    this.edges = edges;
  }
}
