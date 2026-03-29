package org.integratedmodelling.common.data;

import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.DefaultEdge;

import java.io.Serial;
import java.util.Collection;

/**
 * Simple wrapper around a JGraphT {@link org.jgrapht.Graph} that exposes a simpler tree API. Not as
 * much to simplify as to recognize as a result of functions for display.
 *
 * @param <T>
 */
public class Tree<T> extends DefaultDirectedGraph<T, DefaultEdge> {

  @Serial
  private static final long serialVersionUID = 1L;

  public Tree() {
    super(DefaultEdge.class);
  }

  public T root() {
    return vertexSet().stream().filter(v -> inDegreeOf(v) == 0).findFirst().orElse(null);
  }

  public Collection<T> roots() {
    return vertexSet().stream().filter(v -> inDegreeOf(v) == 0).toList();
  }

  public Collection<T> leaves() {
    return vertexSet().stream().filter(v -> outDegreeOf(v) == 0).toList();
  }

  public Collection<T> children(T parent) {
    return outgoingEdgesOf(parent).stream().map(this::getEdgeTarget).toList();
  }

  public Collection<T> parents(T child) {
    return incomingEdgesOf(child).stream().map(this::getEdgeSource).toList();
  }

  public T parent(T child) {
    return incomingEdgesOf(child).stream().map(this::getEdgeSource).findFirst().orElse(null);
  }
}
