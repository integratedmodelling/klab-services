package org.integratedmodelling.klab.api.documentation;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Dependency-free process diagram. All transport types are concrete, mutable Java beans. Serialize
 * {@link #getRoot()} directly for ELK JSON input. See docs/FLOWCHARTS.md. Metadata must contain
 * only JSON values (string-keyed maps, lists, primitives and null).
 */
public class FlowChart implements Serializable {
  private Node root = new Node();
  private Map<String, Object> metadata = new LinkedHashMap<>();

  public FlowChart() {}

  public Node getRoot() {
    return root;
  }

  public void setRoot(Node root) {
    this.root = root;
  }

  public Map<String, Object> getMetadata() {
    return metadata;
  }

  public void setMetadata(Map<String, Object> metadata) {
    this.metadata = metadata;
  }

  /** A stateless conversion contract; adapters are never part of the transport payload. */
  @FunctionalInterface
  public interface Adapter<T> {
    FlowChart adapt(T source);
  }

  public static <T> FlowChart adapt(T source, Adapter<? super T> adapter) {
    return Objects.requireNonNull(adapter).adapt(Objects.requireNonNull(source));
  }

  public static Builder builder(String rootId) {
    return new Builder(rootId);
  }

  public enum Role {
    INPUT,
    OUTPUT
  }

  /** Plain transport bean for diagram element data. */
  public static class Element implements Serializable {
    private String id = null;
    private Map<String, Object> metadata = new LinkedHashMap<>();
    private Map<String, String> layoutOptions = new LinkedHashMap<>();

    public Element() {}

    public String getId() {
      return id;
    }

    public void setId(String id) {
      this.id = id;
    }

    public Map<String, Object> getMetadata() {
      return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
      this.metadata = metadata;
    }

    public Map<String, String> getLayoutOptions() {
      return layoutOptions;
    }

    public void setLayoutOptions(Map<String, String> layoutOptions) {
      this.layoutOptions = layoutOptions;
    }
  }

  /** Plain transport bean for diagram shape data. */
  public static class Shape extends Element {
    private double x = 0;
    private double y = 0;
    private double width = 0;
    private double height = 0;
    private List<Label> labels = new ArrayList<>();

    public Shape() {}

    public double getX() {
      return x;
    }

    public void setX(double x) {
      this.x = x;
    }

    public double getY() {
      return y;
    }

    public void setY(double y) {
      this.y = y;
    }

    public double getWidth() {
      return width;
    }

    public void setWidth(double width) {
      this.width = width;
    }

    public double getHeight() {
      return height;
    }

    public void setHeight(double height) {
      this.height = height;
    }

    public List<Label> getLabels() {
      return labels;
    }

    public void setLabels(List<Label> labels) {
      this.labels = labels;
    }
  }

  /** An ELK node, including the root; ports may occur at every level. */
  public static class Node extends Shape {
    private List<Node> children = new ArrayList<>();
    private List<Port> ports = new ArrayList<>();
    private List<Link> edges = new ArrayList<>();

    public Node() {}

    public List<Node> getChildren() {
      return children;
    }

    public void setChildren(List<Node> children) {
      this.children = children;
    }

    public List<Port> getPorts() {
      return ports;
    }

    public void setPorts(List<Port> ports) {
      this.ports = ports;
    }

    public List<Link> getEdges() {
      return edges;
    }

    public void setEdges(List<Link> edges) {
      this.edges = edges;
    }
  }

  /** A node-owned endpoint. Role is relative to its owning node. */
  public static class Port extends Shape {
    private Role role = null;

    public Port() {}

    public Role getRole() {
      return role;
    }

    public void setRole(Role role) {
      this.role = role;
    }
  }

  /** Plain transport bean for diagram label data. */
  public static class Label extends Shape {
    private String text = null;

    public Label() {}

    public String getText() {
      return text;
    }

    public void setText(String text) {
      this.text = text;
    }
  }

  /** An ELK extended edge; endpoints reference node or port IDs. */
  public static class Link extends Element {
    private String container;

    /** ELK coordinate container, when different from the edge's structural owner. */
    public String getContainer() {
      return container;
    }

    public void setContainer(String container) {
      this.container = container;
    }

    private List<String> sources = new ArrayList<>();
    private List<String> targets = new ArrayList<>();
    private List<Label> labels = new ArrayList<>();
    private List<Section> sections = new ArrayList<>();
    private List<Point> junctionPoints = new ArrayList<>();

    public Link() {}

    public List<String> getSources() {
      return sources;
    }

    public void setSources(List<String> sources) {
      this.sources = sources;
    }

    public List<String> getTargets() {
      return targets;
    }

    public void setTargets(List<String> targets) {
      this.targets = targets;
    }

    public List<Label> getLabels() {
      return labels;
    }

    public void setLabels(List<Label> labels) {
      this.labels = labels;
    }

    public List<Section> getSections() {
      return sections;
    }

    public void setSections(List<Section> sections) {
      this.sections = sections;
    }

    public List<Point> getJunctionPoints() {
      return junctionPoints;
    }

    public void setJunctionPoints(List<Point> junctionPoints) {
      this.junctionPoints = junctionPoints;
    }
  }

  /** Optional ELK routing geometry, with coordinates relative to the containing node. */
  public static class Section extends Element {
    private Point startPoint = null;
    private Point endPoint = null;
    private List<Point> bendPoints = new ArrayList<>();
    private String incomingShape = null;
    private String outgoingShape = null;
    private List<String> incomingSections = new ArrayList<>();
    private List<String> outgoingSections = new ArrayList<>();

    public Section() {}

    public Point getStartPoint() {
      return startPoint;
    }

    public void setStartPoint(Point startPoint) {
      this.startPoint = startPoint;
    }

    public Point getEndPoint() {
      return endPoint;
    }

    public void setEndPoint(Point endPoint) {
      this.endPoint = endPoint;
    }

    public List<Point> getBendPoints() {
      return bendPoints;
    }

    public void setBendPoints(List<Point> bendPoints) {
      this.bendPoints = bendPoints;
    }

    public String getIncomingShape() {
      return incomingShape;
    }

    public void setIncomingShape(String incomingShape) {
      this.incomingShape = incomingShape;
    }

    public String getOutgoingShape() {
      return outgoingShape;
    }

    public void setOutgoingShape(String outgoingShape) {
      this.outgoingShape = outgoingShape;
    }

    public List<String> getIncomingSections() {
      return incomingSections;
    }

    public void setIncomingSections(List<String> incomingSections) {
      this.incomingSections = incomingSections;
    }

    public List<String> getOutgoingSections() {
      return outgoingSections;
    }

    public void setOutgoingSections(List<String> outgoingSections) {
      this.outgoingSections = outgoingSections;
    }
  }

  /** Plain transport bean for diagram point data. */
  public static class Point implements Serializable {
    private double x = 0;
    private double y = 0;

    public Point() {}

    public double getX() {
      return x;
    }

    public void setX(double x) {
      this.x = x;
    }

    public double getY() {
      return y;
    }

    public void setY(double y) {
      this.y = y;
    }
  }

  /** Fluent construction of a node and its contained elements. IDs are graph-wide. */
  public static class NodeBuilder {
    protected final Node node;

    private NodeBuilder(Node node) {
      this.node = node;
    }

    public NodeBuilder label(String text) {
      node.getLabels().add(labelOf(text));
      return this;
    }

    public NodeBuilder size(double width, double height) {
      node.setWidth(width);
      node.setHeight(height);
      return this;
    }

    public NodeBuilder metadata(String key, Object value) {
      node.getMetadata().put(key, value);
      return this;
    }

    public NodeBuilder layout(String key, String value) {
      node.getLayoutOptions().put(key, value);
      return this;
    }

    public NodeBuilder node(String id, Consumer<NodeBuilder> configure) {
      Node child = new Node();
      child.setId(id);
      configure.accept(new NodeBuilder(child));
      node.getChildren().add(child);
      return this;
    }

    public NodeBuilder port(String id, Role role) {
      return port(id, role, p -> {});
    }

    public NodeBuilder port(String id, Role role, Consumer<Port> configure) {
      Port port = new Port();
      port.setId(id);
      port.setRole(role);
      configure.accept(port);
      node.getPorts().add(port);
      return this;
    }

    public NodeBuilder link(String id, String source, String target) {
      return link(id, source, target, e -> {});
    }

    public NodeBuilder link(String id, String source, String target, Consumer<Link> configure) {
      Link link = new Link();
      link.setId(id);
      link.getSources().add(source);
      link.getTargets().add(target);
      configure.accept(link);
      node.getEdges().add(link);
      return this;
    }
  }

  /** Builder owns a mutable result; build validates and returns it without copying. */
  public static class Builder {
    private final FlowChart chart = new FlowChart();

    private Builder(String id) {
      chart.root.setId(id);
    }

    public Builder root(Consumer<NodeBuilder> configure) {
      configure.accept(new NodeBuilder(chart.root));
      return this;
    }

    public Builder metadata(String key, Object value) {
      chart.metadata.put(key, value);
      return this;
    }

    public FlowChart build() {
      chart.validate();
      return chart;
    }
  }

  public static Label labelOf(String text) {
    Label label = new Label();
    label.setText(text);
    return label;
  }

  /**
   * Check topology after construction or deserialization. Throws IllegalArgumentException for
   * duplicate/missing IDs, containment cycles, missing endpoints or missing port roles. Cycles in
   * process links and disconnected nodes are valid. Port roles describe interfaces; they do not
   * constrain edge direction (a container input can feed an internal child).
   */
  public void validate() {
    Set<String> ids = new HashSet<>();
    Set<String> endpoints = new HashSet<>();
    List<Link> links = new ArrayList<>();
    collect(
        Objects.requireNonNull(root, "root"),
        ids,
        endpoints,
        links,
        Collections.newSetFromMap(new IdentityHashMap<>()));
    for (Link link : links) {
      if (link.getSources().isEmpty() || link.getTargets().isEmpty())
        throw new IllegalArgumentException("Empty endpoints: " + link.getId());
      for (String endpoint : link.getSources()) checkEndpoint(endpoint, endpoints);
      for (String endpoint : link.getTargets()) checkEndpoint(endpoint, endpoints);
    }
  }

  private static void checkEndpoint(String id, Set<String> endpoints) {
    if (!endpoints.contains(id)) throw new IllegalArgumentException("Unknown endpoint: " + id);
  }

  private static void addId(Element element, Set<String> ids) {
    if (element == null
        || element.getId() == null
        || element.getId().isBlank()
        || !ids.add(element.getId()))
      throw new IllegalArgumentException("Missing or duplicate element ID");
  }

  private static void collect(
      Node node, Set<String> ids, Set<String> endpoints, List<Link> links, Set<Node> visited) {
    if (!visited.add(node)) throw new IllegalArgumentException("Repeated node containment");
    addId(node, ids);
    endpoints.add(node.getId());
    for (Port port : node.getPorts()) {
      addId(port, ids);
      endpoints.add(port.getId());
      if (port.getRole() == null)
        throw new IllegalArgumentException("Missing port role: " + port.getId());
    }
    for (Link link : node.getEdges()) {
      addId(link, ids);
      links.add(link);
    }
    for (Node child : node.getChildren()) collect(child, ids, endpoints, links, visited);
  }
}
