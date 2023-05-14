package org.integratedmodelling.klab.services.resolver.resolution;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.integratedmodelling.klab.api.authentication.scope.ContextScope;
import org.integratedmodelling.klab.api.knowledge.Knowledge;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.services.resolver.Coverage;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.DefaultEdge;

public class Resolution {

    /*
     * all the resolved nodes by observable
     */
    private Map<Observable, Set<Node>> nodes = new HashMap<>();
    /*
     * the root node of the resolution, corresponding to the original resolvable
     */
    private Node root;
    private ContextScope scope;
    private Graph<Node, ResolutionEdge> resolutionGraph = new DefaultDirectedGraph<>(ResolutionEdge.class);
    /*
     * The total coverage after resolution.
     */
    private Coverage coverage;

    public class Node {

        /*
         * each node starts with a copy the resolution so far and adds its own; these are merged
         * into the graph on acceptance. All resolvables are promoted to observables before they are
         * resolved.
         */
        private Map<Observable, Set<Node>> nodes = new HashMap<>();

        public Node(Knowledge resolvable) {
            
            /*
             * if Instance: start with full coverage
             */
            
        }

        public Node getNode(Knowledge observable) {
            return null;
        }

        public Coverage merge() {
            /*
             * make the link between this node and its parent, adding the coverage to what is known
             * so far
             */
            /*
             * update the overall resolution catalog
             */
            return null;
        }

    }

    /*
     * Promote the passed knowledge to a suitable observable, possibly (creating and) caching a
     * pre-resolved model.
     */
    private Observable promoteResolvable(Knowledge resolvable) {
        return null;
    }

    public class ResolutionEdge extends DefaultEdge {

        private static final long serialVersionUID = 3592176546593487293L;

        /*
         * Coverage of this specific resolution. Never null: if coverage is total, this points to
         * the original scope coverage.
         */
        Coverage coverage;

    }

    public Resolution(Knowledge resolvable, ContextScope scope) {
        this.scope = scope;
        this.root = new Node(resolvable);
    }

    public Node root() {
        return this.root;
    }

    public Coverage getCoverage() {
        return coverage;
    }

}
