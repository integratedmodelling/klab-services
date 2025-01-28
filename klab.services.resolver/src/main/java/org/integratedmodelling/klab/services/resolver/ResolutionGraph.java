package org.integratedmodelling.klab.services.resolver;

import org.integratedmodelling.klab.api.collections.Pair;
import org.integratedmodelling.klab.api.exceptions.KlabIllegalStateException;
import org.integratedmodelling.klab.api.knowledge.Model;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.Resolvable;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.observation.scale.Scale;
import org.integratedmodelling.klab.api.lang.Contextualizable;
import org.integratedmodelling.klab.api.lang.LogicalConnector;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.resolver.Coverage;
import org.integratedmodelling.klab.api.services.resources.ResourceSet;
import org.jgrapht.Graph;
import org.jgrapht.Graphs;
import org.jgrapht.graph.AbstractBaseGraph;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.DefaultEdge;
import org.w3.xlink.XlinkFactory;

import java.util.*;

/**
 * Next-gen Resolution graph, to substitute Resolution/ResolutionImpl.
 *
 * <p>The nodes can be:
 *
 * <ol>
 *   <li>Observations
 *   <li>Models
 *   <li>Observables to defer resolution to
 * </ol>
 *
 * <p>These are inserted in the graph before resolution. If they are unresolved (i.e. are models or
 * are unresolved observations) they remain there as cached unresolved. The root nodes can only be
 * observations of substantials.
 *
 * <p>The edges report the resolution coverage of the source resolvable. When the target is an
 * observable, the coverage is unknown until there has been a trip back to the runtime.
 *
 * <p>The graph also contains a cache of resolved resolvables with their native coverage, indexed by
 * observable, so that the resolver can quickly assess if a previously used resolvable can be used
 * for other resolutions before searching for models.
 */
public class ResolutionGraph {

  private Resolvable target;
  private Coverage targetCoverage;
  private ContextScope rootScope;
  private DefaultDirectedGraph<Resolvable, ResolutionGraph.ResolutionEdge> graph =
      new DefaultDirectedGraph<>(ResolutionEdge.class);
  private ResolutionGraph parent;

  // these are only used in the root graph. They collect the merged dependencies of all
  // strategies and models, added only after the runtime has successfully resolved them.
  private ResourceSet dependencies = new ResourceSet();

  /**
   * A catalog per observable of all resolving sources seen, used by merging their native coverage
   * with any resolving candidate before new strategies are attempted. Includes resolved
   * observations, resolved models and (eventually) resolved external dataflows.
   */
  private Map<Observable, Set<Resolvable>> resolutionCatalog = new HashMap<>();

  private boolean empty;

  private ResolutionGraph(ContextScope rootScope) {
    this.rootScope = rootScope;
  }

  public Graph<Resolvable, ResolutionEdge> graph() {
    return this.graph;
  }

  public double getResolvedCoverage() {
    return targetCoverage == null ? 0 : targetCoverage.getCoverage();
  }

  private ResolutionGraph(Resolvable target, Scale scaleToCover, ResolutionGraph parent) {

    if (parent.empty) {
      throw new KlabIllegalStateException("cannot use an empty resolution graph");
    }

    this.parent = parent;

    /**
     * Models are resolved from full down, intersecting the coverage of the dependencies. Everything
     * else is resolved from zero up, uniting the coverages.
     */
    this.target = target;
    this.targetCoverage = Coverage.create(scaleToCover, target instanceof Model ? 1.0 : 0.0);
    var tc = getCoverage(target);
    if (tc != null) {
      this.targetCoverage = targetCoverage.merge(tc, LogicalConnector.INTERSECTION);
    }

    this.rootScope = parent.rootScope;
    this.resolutionCatalog.putAll(parent.resolutionCatalog);
  }

  private Scale getCoverage(Resolvable target) {
    return switch (target) {
      case Model model -> Scale.create(model.getCoverage());
      case Observation observation -> Scale.create(observation.getGeometry());
      default -> null;
    };
  }

  public ResourceSet getDependencies() {
    return rootGraph().dependencies;
  }

  private ResolutionGraph rootGraph() {
    var ret = this;
    while (ret.parent != null) {
      ret = ret.parent;
    }
    return ret;
  }

  /**
   * Merge the coverage and return the result without setting the coverage in the graph, just
   * invoked for testing stop conditions.
   *
   * @param resolution
   * @return
   */
  public Coverage checkCoverage(ResolutionGraph resolution) {

    if (resolution.isEmpty()) {
      return Coverage.empty();
    }
    return this.targetCoverage.merge(
        resolution.targetCoverage,
        target instanceof Model ? LogicalConnector.INTERSECTION : LogicalConnector.UNION);
  }

  public boolean merge(ResolutionGraph childGraph) {
    return merge(childGraph, null);
  }

  /**
   * Accept the resolution contained in the passed graph for its target, adding all sub-resolvables
   * collected along the way, then add our resolvable to the graph and updating the target coverage
   * according to the kind of merge (uniting for alternative observables, intersecting for model
   * dependencies) and the catalog. Return true if our own target coverage is made complete by the
   * merge.
   */
  public boolean merge(ResolutionGraph childGraph, String localName) {

    Graphs.addAllVertices(this.graph, childGraph.graph.vertexSet());
    Graphs.addAllEdges(this.graph, childGraph.graph, childGraph.graph.edgeSet());

    /*
    Our resolvable is resolved by the child's
     */
    this.graph.addVertex(this.target);
    this.graph.addVertex(childGraph.target);
    this.graph.addEdge(
        this.target, childGraph.target, new ResolutionEdge(childGraph.targetCoverage, localName));

    /*
    ...by the amount determined in its coverage, "painting" the incoming extents onto ours.
     */
    this.targetCoverage =
        this.targetCoverage.merge(
            childGraph.targetCoverage,
            this.target instanceof Model ? LogicalConnector.INTERSECTION : LogicalConnector.UNION);

    /*
    TODO UPDATE THE CATALOG WITH THE NATIVE COVERAGE OF THE TARGET
     */

    /*
    if our coverage is satisfactory, signal that the merge has done all we need
     */
    return targetCoverage.isComplete();
  }

  /**
   * Accept the resolution contained in this graph's objects, adding our resolvable to the graph and
   * updating the catalog. Called directly on a parent graph when an existing resolvable is enough
   * to resolve the target.
   */
  public void accept(Resolvable resolvable, Coverage finalCoverage) {

    // resolvable is in the graph already

    System.out.println(
        "ACCEPTING EXISTING RESOLVABLE INTO SAME GRAPH FOR THIS RESOLVABLE - JUST CREATE"
            + " THE LINK FROM THE TARGET TO THE RESOLVABLE AND SET THE COVERAGE");
  }

  public static ResolutionGraph create(ContextScope rootScope) {
    return new ResolutionGraph(rootScope);
  }

  public static ResolutionGraph empty() {
    var ret = new ResolutionGraph(null);
    ret.empty = true;
    return ret;
  }

  public boolean isEmpty() {
    return empty;
  }

  /**
   * Spawn a new resolution graph to resolve the passed observation in the passed scale, as a child
   * of the previous.
   *
   * @param target
   * @param scaleToCover
   * @return
   */
  public ResolutionGraph createChild(Resolvable target, Scale scaleToCover) {
    return new ResolutionGraph(target, scaleToCover, this);
  }

  /**
   * Return any known resolvable (already present in the graph) that can resolve the passed
   * observable, paired with the result of intersecting its native coverage with the passed scale.
   *
   * @param observable
   * @return
   */
  public List<Pair<Resolvable, Coverage>> getResolving(Observable observable, Scale scale) {
    return List.of();
  }

  public Observation getContextObservation() {
    ResolutionGraph target = this;
    while (target != null && !(target.target instanceof Observation)) {
      target = target.parent;
    }
    return target == null ? null : (Observation) target.target;
  }

  public void setDependencies(ResourceSet dependencies) {
    rootGraph().dependencies = dependencies;
  }

  public List<Resolvable> rootNodes() {
    List<Resolvable> ret = new ArrayList<>();
    for (Resolvable l : graph().vertexSet()) {
      if (graph.incomingEdgesOf(l).isEmpty()) {
        ret.add(l);
      }
    }
    return ret;
  }

  /**
   * The RESOLVED_BY edge, only including the resolution coverage for now. Each resolvable may have
   * >1 resolving nodes, successively covering the extents up to "sufficient" coverage.
   *
   * <p>TODO must add the local "name" for the resolving object if one is needed
   */
  public static class ResolutionEdge extends DefaultEdge {

    public Coverage coverage;
    public String localName;

    public ResolutionEdge() {}

    public ResolutionEdge(Coverage coverage, String localName) {
      this.coverage = coverage;
    }
  }
}
