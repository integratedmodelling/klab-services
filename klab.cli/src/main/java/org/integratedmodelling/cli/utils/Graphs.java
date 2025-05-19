package org.integratedmodelling.cli.utils;

import com.mxgraph.layout.hierarchical.mxHierarchicalLayout;
import com.mxgraph.swing.mxGraphComponent;
import com.mxgraph.view.mxGraph;
import org.integratedmodelling.common.utils.Utils;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.traverse.TopologicalOrderIterator;

import javax.swing.*;
import java.io.Serial;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Graphs {

    public enum Layout {
        HIERARCHICAL, RADIALTREE, SIMPLE, SPRING
    }

    public static void show(Graph<?, ?> graph, String title) {
        show(graph, title, Layout.SPRING);
    }


    /**
     * Dump the graph on the console using ASCII only, ignoring cyclic relationships
     *
     * @param graph
     */
    public static <V, E> String dump(Graph<V, E> graph) {

        /*
        Find out which nodes are "root"
         */
        List<V> roots= new ArrayList<>();
        for (V vertex : graph.vertexSet()) {
            if (graph.incomingEdgesOf(vertex).isEmpty()) {
                roots.add(vertex);
            }
        }

        StringBuffer buffer = new StringBuffer(1024);

        for (V root : roots) {
            if (!buffer.isEmpty()) {
                buffer.append("\n");
            }
            dump(root, graph, buffer, 0);
        }

        return buffer.toString();

    }

    private static <V, E> void dump(V root, Graph<V,E> graph, StringBuffer buffer, int offset) {

        var spacer = Utils.Strings.spaces(offset);
        buffer.append(spacer).append(root.toString()).append("\n");
        for (E edge : graph.outgoingEdgesOf(root)) {
            dump(graph.getEdgeTarget(edge), graph, buffer, offset + 3);
        }
    }


    public static void show(Graph<?, ?> graph, String title, Layout layout) {

        SwingUtilities.invokeLater(new Runnable() {

            @Override
            public void run() {
                @SuppressWarnings("unchecked")
                GraphPanel panel = new GraphPanel(title, (Graph<Object, Object>) graph, layout);
                panel.showGraph();
            }

        });
    }

    @SuppressWarnings("unchecked")
    private static <E> Graph<?, ?> adaptContribGraph(Graph<?, E> graph,
                                                     Class<? extends E> edgeClass) {

        DefaultDirectedGraph<Object, E> ret = new DefaultDirectedGraph<Object, E>(edgeClass);
        for (Object o : graph.vertexSet()) {
            ret.addVertex(o);
        }
        for (Object e : graph.edgeSet()) {
            ret.addEdge(graph.getEdgeSource((E) e), graph.getEdgeTarget((E) e), (E) e);
        }
        return ret;
    }

//        /**
//         * Show the dependency graph in the loader.
//         */
//        public static void showDependencies() {
//            show(((KimLoader) Resources.INSTANCE.getLoader()).getDependencyGraph(), "Dependencies", DefaultEdge.class);
//        }

    /**
     * Return whether precursor has a directed edge to dependent in graph.
     *
     * @param <V>
     * @param <E>
     * @param dependent
     * @param precursor
     * @param graph
     * @return true if dependency exists
     */
    public static <V, E> boolean dependsOn(V dependent, V precursor, Graph<V, E> graph) {

        for (E o : graph.incomingEdgesOf(dependent)) {
            if (graph.getEdgeSource(o).equals(precursor)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Shallow copy of graph into another.
     *
     * @param <V>
     * @param <E>
     * @param graph
     * @param newGraph
     * @return same graph passed as receiver
     */
    public static <V, E> Graph<V, E> copy(Graph<V, E> graph, Graph<V, E> newGraph) {
        for (V vertex : graph.vertexSet()) {
            newGraph.addVertex(vertex);
        }
        for (E edge : graph.edgeSet()) {
            newGraph.addEdge(graph.getEdgeSource(edge), graph.getEdgeTarget(edge), edge);
        }
        return newGraph;
    }

}

class GraphPanel extends JFrame {

    /**
     *
     */
    @Serial
    private static final long serialVersionUID = -2707712944901661771L;

    public GraphPanel(String title, Graph<Object, Object> sourceGraph, Graphs.Layout layout) {

        super(title);

        mxGraph graph = new mxGraph();
        Object parent = graph.getDefaultParent();
        graph.getModel().beginUpdate();

        try {

            Map<Object, Object> vertices = new HashMap<>();
            for (Object v : sourceGraph.vertexSet()) {
                vertices.put(v, graph.insertVertex(parent, null, v.toString(), 20, 20, v.toString().length() * 6, 30));
            }
            for (Object v : sourceGraph.edgeSet()) {
                graph.insertEdge(parent, null, v.toString(), vertices.get(sourceGraph.getEdgeSource(v)),
                        vertices.get(sourceGraph.getEdgeTarget(v)));
            }

        } finally {
            graph.getModel().endUpdate();
        }

        switch (layout) {
            case HIERARCHICAL:
                break;
            case RADIALTREE:
                break;
            case SIMPLE:
                break;
            case SPRING:
                new mxHierarchicalLayout(graph).execute(graph.getDefaultParent());
                break;
            default:
                break;

        }

        mxGraphComponent graphComponent = new mxGraphComponent(graph);
        getContentPane().add(graphComponent);
    }

    public void showGraph() {
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setSize(400, 320);
        setVisible(true);
    }
}