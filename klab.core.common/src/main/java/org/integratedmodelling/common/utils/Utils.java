package org.integratedmodelling.common.utils;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.uuid.Generators;
import com.google.common.collect.Sets;
import com.google.common.net.HttpHeaders;
import com.google.common.net.MediaType;
import com.jcraft.jsch.JSch;
import com.mxgraph.layout.hierarchical.mxHierarchicalLayout;
import com.mxgraph.swing.mxGraphComponent;
import com.mxgraph.view.mxGraph;
import gg.jte.ContentType;
import gg.jte.TemplateEngine;
import gg.jte.output.StringOutput;
import gg.jte.resolve.DirectoryCodeResolver;
import java.io.*;
import java.lang.reflect.Array;
import java.net.*;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.swing.*;
import org.apache.avro.io.DecoderFactory;
import org.apache.avro.io.EncoderFactory;
import org.apache.avro.specific.SpecificDatumReader;
import org.apache.avro.specific.SpecificDatumWriter;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.mime.HttpMultipartMode;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.entity.mime.content.FileBody;
import org.apache.http.entity.mime.content.StringBody;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.tika.mime.MimeTypes;
import org.integratedmodelling.common.data.BaseDataImpl;
import org.integratedmodelling.common.data.jackson.JacksonConfiguration;
import org.integratedmodelling.common.logging.Logging;
import org.integratedmodelling.common.services.client.resolver.DataflowEncoder;
import org.integratedmodelling.klab.api.ServicesAPI;
import org.integratedmodelling.klab.api.collections.Pair;
import org.integratedmodelling.klab.api.data.mediation.impl.NumericRangeImpl;
import org.integratedmodelling.klab.api.exceptions.*;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.identities.Identity;
import org.integratedmodelling.klab.api.identities.ServiceIdentity;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.observation.scale.time.TimePeriod;
import org.integratedmodelling.klab.api.knowledge.organization.Project;
import org.integratedmodelling.klab.api.lang.Annotation;
import org.integratedmodelling.klab.api.lang.AnnotationImpl;
import org.integratedmodelling.klab.api.lang.ServiceCall;
import org.integratedmodelling.klab.api.lang.kactors.KActorsBehavior;
import org.integratedmodelling.klab.api.lang.kim.KimModel;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.scope.SessionScope;
import org.integratedmodelling.klab.api.services.KlabService;
import org.integratedmodelling.klab.api.services.ResourcesService;
import org.integratedmodelling.klab.api.services.resources.ResourceTransport;
import org.integratedmodelling.klab.api.services.runtime.Dataflow;
import org.integratedmodelling.klab.api.services.runtime.Notification;
import org.integratedmodelling.klab.api.services.runtime.extension.AdapterDescriptor;
import org.integratedmodelling.klab.api.services.runtime.impl.MessageImpl;
import org.integratedmodelling.klab.api.services.runtime.objects.JobStatus;
import org.integratedmodelling.klab.common.data.DataRequest;
import org.integratedmodelling.klab.common.data.Instance;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.springframework.http.HttpStatus;
import org.springframework.web.util.UriUtils;

public class Utils extends org.integratedmodelling.klab.api.utils.Utils {

  public static class Exceptions extends org.integratedmodelling.klab.api.utils.Utils.Exceptions {

    public static void suppressAndLog(Runnable task) {
      try {
        task.run();
      } catch (Throwable t) {
        Logging.INSTANCE.error(t);
      }
    }
  }

  public static class Dataflows {

    /**
     * Return the encoded form of a passed dataflow in the passed scope.
     *
     * @param dataflow
     * @param scope
     * @return
     */
    public static String encode(Dataflow dataflow, ContextScope scope) {

      var ret = new StringWriter();
      var output = new PrintWriter(ret);
      new DataflowEncoder(dataflow, scope).encode(output);
      return ret.toString();
    }
  }

  public static class Annotations {

    public static boolean hasAnnotation(Object object, String s) {
      for (Annotation annotation : getAnnotations(object, false)) {
        if (annotation.getName().equals(s)) {
          return true;
        }
      }
      return false;
    }

    public static boolean hasOrInheritsAnnotation(Object object, String s) {
      for (Annotation annotation : getAnnotations(object, true)) {
        if (annotation.getName().equals(s)) {
          return true;
        }
      }
      return false;
    }

    public static Collection<Annotation> getAnnotations(Object object, boolean addInherited) {
      return switch (object) {
        case KlabAsset asset -> {
          var ret = asset.getAnnotations();
          if (ret == null) {
            ret = new ArrayList<>();
          }
          if (addInherited) {
            Object parent =
                switch (object) {
                  case Observable observable -> null;
                  // TODO inherit from concept definition; traits may redefine observable's
                  // if main observable, inherit from model
                  // if in namespace, inherit from namespace
                  default -> null;
                };
            if (parent != null) {
              ret = addNotPresent(getAnnotations(parent, true), ret);
            }
          }
          yield ret;
        }
        //        case ServiceInfo info -> info.getAnnotations();
        default -> new ArrayList<>();
      };
    }

    private static Collection<Annotation> addNotPresent(
        Collection<Annotation> annotations, Collection<Annotation> current) {
      if (annotations.isEmpty()) {
        return current;
      }
      var ret = new ArrayList<>(current);
      var existing = current.stream().map(Annotation::getName).collect(Collectors.toSet());
      for (var annotation : annotations) {
        if (!existing.contains(annotation.getName())) {
          ret.add(annotation);
        }
      }
      return ret;
    }

    /**
     * Shorthand to check whether the default parameter (list or individual value) of an annotation
     * contains the passed string.
     *
     * @param string
     * @return
     */
    public static boolean defaultsContain(Annotation annotation, String string) {
      if (annotation.get(ServiceCall.DEFAULT_PARAMETER_NAME) instanceof List) {
        return ((List<?>) annotation.get(ServiceCall.DEFAULT_PARAMETER_NAME)).contains(string);
      } else if (annotation.get(ServiceCall.DEFAULT_PARAMETER_NAME) != null) {
        return annotation.get(ServiceCall.DEFAULT_PARAMETER_NAME).equals(string);
      }
      return false;
    }

    /**
     * Simple methods that are messy to keep writing explicitly * @param id
     *
     * @return
     */
    public static Annotation getAnnotation(Object object, String id) {
      for (Annotation annotation : getAnnotations(object, true)) {
        if (id.equals(annotation.getName())) {
          return annotation;
        }
      }
      return null;
    }

    /**
     * Find the passed annotation in the passed objects, using the order as priority
     *
     * @param annotationName
     * @param objects
     * @return
     */
    public static Annotation findAnnotation(String annotationName, Object... objects) {
      return null;
    }

    /**
     * Find the passed annotations in the passed objects, using the order as priority
     *
     * @param annotationNames
     * @param objects
     * @return
     */
    public static Collection<Annotation> findAnnotations(
        Set<String> annotationNames, Object... objects) {
      return collectAnnotations(objects).stream()
          .filter(a -> annotationNames.contains(a.getName()))
          .toList();
    }

    /**
     * Collect the annotations from an k.IM object and its semantic lineage, ensuring that
     * downstream annotations of the same name override those upstream. Any string parameter filters
     * the annotations collected.
     *
     * @param objects
     * @return all annotations from upstream
     */
    public static Collection<Annotation> collectAnnotations(Object... objects) {

      Map<String, Annotation> ret = new LinkedHashMap<>();
      for (Object object : objects) {
        processAnnotations(object, ret);
      }
      return ret.values();
    }

    private static void processAnnotations(Object object, Map<String, Annotation> collection) {
      for (var annotation : getAnnotations(object, true)) {
        if (!collection.containsKey(annotation.getName())) {
          collection.put(annotation.getName(), annotation);
        }
      }
    }

    /**
     * Explore a list of objects carrying annotations in the passed order and look for a specific
     * annotation, building a new one that carries all the parameters in the annotations encountered
     * with the ones filled in first overriding the subsequent in case of repetition.
     *
     * @param name
     * @param annotationCarriers null-proof list of objects that may carry the requested annotation
     * @return a new annotation with the passed name, empty if there were no annotations in the
     *     passed objects.
     */
    public static Annotation mergeAnnotations(String name, Object... annotationCarriers) {

      var ret = new AnnotationImpl();
      ret.setName(name);
      if (annotationCarriers != null) {
        for (var object : annotationCarriers) {
          if (object != null) {
            var a = getAnnotation(object, name);
            if (a != null) {
              for (var key : a.keySet()) {
                if (!ret.containsKey(key)) {
                  ret.put(key, a.get(key));
                }
              }
            }
          }
        }
      }
      return ret;
    }
  }

  public static class Graphs {

    public static <T> List<List<T>> findPathsToCommonAncestor(
        List<T> graphNodes, Function<T, T> parentFinder) {
      // Validate inputs
      if (graphNodes == null || graphNodes.isEmpty()) {
        return java.util.Collections.emptyList();
      }
      if (parentFinder == null) {
        throw new IllegalArgumentException("parentFinder must not be null");
      }

      // Find lowest common ancestor (LCA) among all nodes using only parentFinder
      T commonAncestor = graphNodes.get(0);
      // For nodes beyond the first, refine the common ancestor
      for (int i = 1; i < graphNodes.size(); i++) {
        T node = graphNodes.get(i);
        // Build ancestor set (including the node itself) for current node
        Set<T> ancestors = new HashSet<>();
        T cur = node;
        while (cur != null && !ancestors.contains(cur)) {
          ancestors.add(cur);
          cur = parentFinder.apply(cur);
        }
        // Move up from current commonAncestor until it is in ancestors
        T walker = commonAncestor;
        // Protect against cycles by tracking visited during this ascent
        Set<T> visited = new HashSet<>();
        while (walker != null && !ancestors.contains(walker)) {
          if (!visited.add(walker)) {
            // cycle detected; should not happen if a common ancestor exists
            throw new IllegalStateException("Cycle detected while searching for common ancestor");
          }
          walker = parentFinder.apply(walker);
        }
        if (walker == null) {
          throw new IllegalStateException("No common ancestor found (unexpected)");
        }
        commonAncestor = walker;
      }

      // Build per-node paths from each node up to (but excluding) the common ancestor
      List<List<T>> paths = new ArrayList<>(graphNodes.size());
      for (T start : graphNodes) {
        List<T> path = new ArrayList<>();
        T cur = start;
        Set<T> visited = new HashSet<>();
        while (cur != null && !Objects.equals(cur, commonAncestor)) {
          if (!visited.add(cur)) {
            throw new IllegalStateException(
                "Cycle detected while building path to common ancestor");
          }
          path.add(cur);
          cur = parentFinder.apply(cur);
        }
        if (cur == null && !Objects.equals(start, commonAncestor)) {
          throw new IllegalStateException("Broken hierarchy: reached null before common ancestor");
        }
        paths.add(path);
      }

      return paths;
    }

    public static class CommonAncestry<T> {

      private final T commonAncestor;
      private final List<List<T>> paths;
      private final int maxDistance;

      public CommonAncestry(T commonAncestor, List<List<T>> paths, int maxDistance) {
        this.commonAncestor = commonAncestor;
        this.paths = paths;
        this.maxDistance = maxDistance;
      }

      public T getCommonAncestor() {
        return commonAncestor;
      }

      public List<List<T>> getPaths() {
        return paths;
      }

      public int getMaxDistance() {
        return maxDistance;
      }

      @Override
      public String toString() {
        return "CommonAncestry{"
            + "commonAncestor="
            + commonAncestor
            + ", maxDistance="
            + maxDistance
            + ", paths="
            + paths
            + '}';
      }
    }

    public static <T> CommonAncestry<T> findCommonAncestry(
        List<T> graphNodes, Function<T, T> parentFinder) {
      // Validate inputs
      if (graphNodes == null || graphNodes.isEmpty()) {
        return new CommonAncestry<>(null, java.util.Collections.emptyList(), 0);
      }
      if (parentFinder == null) {
        throw new IllegalArgumentException("parentFinder must not be null");
      }

      // Find lowest common ancestor (LCA) among all nodes using only parentFinder
      T commonAncestor = graphNodes.get(0);
      // For nodes beyond the first, refine the common ancestor
      for (int i = 1; i < graphNodes.size(); i++) {
        T node = graphNodes.get(i);
        // Build ancestor set (including the node itself) for current node
        Set<T> ancestors = new HashSet<>();
        T cur = node;
        while (cur != null && !ancestors.contains(cur)) {
          ancestors.add(cur);
          cur = parentFinder.apply(cur);
        }
        // Move up from current commonAncestor until it is in ancestors
        T walker = commonAncestor;
        // Protect against cycles by tracking visited during this ascent
        Set<T> visited = new HashSet<>();
        while (walker != null && !ancestors.contains(walker)) {
          if (!visited.add(walker)) {
            // cycle detected; should not happen if a common ancestor exists
            throw new IllegalStateException("Cycle detected while searching for common ancestor");
          }
          walker = parentFinder.apply(walker);
        }
        if (walker == null) {
          throw new IllegalStateException("No common ancestor found (unexpected)");
        }
        commonAncestor = walker;
      }

      // Build per-node paths from each node up to (but excluding) the common ancestor
      List<List<T>> paths = new ArrayList<>(graphNodes.size());
      int maxDistance = 0;
      for (T start : graphNodes) {
        List<T> path = new ArrayList<>();
        T cur = start;
        Set<T> visited = new HashSet<>();
        while (cur != null && !Objects.equals(cur, commonAncestor)) {
          if (!visited.add(cur)) {
            throw new IllegalStateException(
                "Cycle detected while building path to common ancestor");
          }
          path.add(cur);
          cur = parentFinder.apply(cur);
        }
        if (cur == null && !Objects.equals(start, commonAncestor)) {
          throw new IllegalStateException("Broken hierarchy: reached null before common ancestor");
        }
        paths.add(path);
        if (path.size() > maxDistance) {
          maxDistance = path.size();
        }
      }

      return new CommonAncestry<>(commonAncestor, paths, maxDistance);
    }

    public enum Layout {
      HIERARCHICAL,
      RADIALTREE,
      SIMPLE,
      SPRING
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
      List<V> roots = new ArrayList<>();
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

    private static <V, E> void dump(V root, Graph<V, E> graph, StringBuffer buffer, int offset) {

      var spacer = Utils.Strings.spaces(offset);
      buffer.append(spacer).append(root.toString()).append("\n");
      for (E edge : graph.outgoingEdgesOf(root)) {
        dump(graph.getEdgeTarget(edge), graph, buffer, offset + 3);
      }
    }

    public static void show(Graph<?, ?> graph, String title, Layout layout) {

      SwingUtilities.invokeLater(
          new Runnable() {

            @Override
            public void run() {
              @SuppressWarnings("unchecked")
              GraphPanel panel = new GraphPanel(title, (Graph<Object, Object>) graph, layout);
              panel.showGraph();
            }
          });
    }

    @SuppressWarnings("unchecked")
    private static <E> Graph<?, ?> adaptContribGraph(
        Graph<?, E> graph, Class<? extends E> edgeClass) {

      DefaultDirectedGraph<Object, E> ret = new DefaultDirectedGraph<Object, E>(edgeClass);
      for (Object o : graph.vertexSet()) {
        ret.addVertex(o);
      }
      for (Object e : graph.edgeSet()) {
        ret.addEdge(graph.getEdgeSource((E) e), graph.getEdgeTarget((E) e), (E) e);
      }
      return ret;
    }

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

    static class GraphPanel extends JFrame {

      /** */
      @Serial private static final long serialVersionUID = -2707712944901661771L;

      public GraphPanel(String title, Graph<Object, Object> sourceGraph, Graphs.Layout layout) {

        super(title);

        mxGraph graph = new mxGraph();
        Object parent = graph.getDefaultParent();
        graph.getModel().beginUpdate();

        try {

          Map<Object, Object> vertices = new HashMap<>();
          for (Object v : sourceGraph.vertexSet()) {
            vertices.put(
                v,
                graph.insertVertex(
                    parent, null, v.toString(), 20, 20, v.toString().length() * 6, 30));
          }
          for (Object v : sourceGraph.edgeSet()) {
            graph.insertEdge(
                parent,
                null,
                v.toString(),
                vertices.get(sourceGraph.getEdgeSource(v)),
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
  }

  public static class SSH {

    public static Collection<String> readHostFile() {

      var ret = new TreeSet<String>();
      JSch jsch = new JSch();

      String HOME = String.valueOf(System.getProperty("user.home"));
      String knownHostsFileName = java.nio.file.Paths.get(HOME, ".ssh", "known_hosts").toString();

      if (new File(knownHostsFileName).exists()) {
        try {
          jsch.setKnownHosts(knownHostsFileName);
          for (var hostKey : jsch.getHostKeyRepository().getHostKey()) {
            java.util.Collections.addAll(ret, hostKey.getHost().split(","));
          }
        } catch (Throwable e) {
          Logging.INSTANCE.error(e);
        }
      }

      return ret;
    }
  }

  public static class Time {

    public static String formatDuration(TimePeriod duration) {
      return formatDuration(
          Duration.of(
              duration.getEnd().getMilliseconds() - duration.getStart().getMilliseconds(),
              ChronoUnit.MILLIS));
    }

    public static String formatDuration(long start, long end) {
      return formatDuration(Duration.of(end - start, ChronoUnit.MILLIS));
    }

    public static String formatDuration(java.time.Duration duration) {
      if (duration == null) {
        return "0s";
      }

      // Duration does not support YEARS directly, so we calculate it from days
      long days = duration.toDays();
      long years = days / 365; // Approximation standard for durations
      days = days % 365;

      // Java 9+ methods to extract parts
      long hours = duration.toHoursPart();
      long minutes = duration.toMinutesPart();
      long seconds = duration.toSecondsPart();
      long milliseconds = duration.toMillisPart();

      StringBuilder sb = new StringBuilder();
      if (years > 0) sb.append(sb.length() == 0 ? "" : ", ").append(years).append("y");
      if (days > 0) sb.append(sb.length() == 0 ? "" : ", ").append(days).append("d");
      if (hours > 0) sb.append(sb.length() == 0 ? "" : ", ").append(hours).append("h");
      if (minutes > 0) sb.append(sb.length() == 0 ? "" : ", ").append(minutes).append("m");
      if (seconds > 0 || sb.length() == 0)
        sb.append(sb.length() == 0 ? "" : ", ").append(seconds).append("s");
      if (milliseconds > 0)
        sb.append(sb.length() == 0 ? "" : ", ").append(milliseconds).append("ms");

      return sb.toString().trim();
    }
  }

  /** Methods to facilitate concurrent access to multiple services. */
  public static class Services {

    /**
     * Resolve a resource URN in all connected services, choose the most appropriate one if multiple
     * services provide it, retrieve it and return it. Local services always have priority.
     *
     * @param scope
     * @param urn
     * @return
     */
    public static Pair<Resource, ResourcesService> resolveResource(Scope scope, String urn) {
      return null;
    }

    public static Pair<AdapterDescriptor, ResourcesService> resolveAdapter(
        Scope scope, String urn) {
      return null;
    }

    /**
     * If the current service does not have it already, resolve a model URN in all connected
     * services, choose the most appropriate one if multiple services provide it, retrieve it and
     * return it. The entire project is always ingested in case the model is not available locally.
     * Local services always have priority.
     *
     * @param scope
     * @param urn
     * @return
     */
    public static Pair<KimModel, ResourcesService> resolveModel(
        Scope scope, String urn, KlabService currentService) {
      return null;
    }

    /**
     * If the current service does not have it already, resolve a behavior URN in all connected
     * services, choose the most appropriate one if multiple services provide it, retrieve it and
     * return it. The entire project is always ingested in case the model is not available locally.
     * Local services always have priority.
     *
     * @param scope
     * @param urn
     * @return
     */
    public static Pair<KActorsBehavior, ResourcesService> resolveBehavior(Scope scope, String urn) {
      return null;
    }

    public static List<Pair<KimModel, ResourcesService>> resolveModels(
        ContextScope scope, Observable observable) {
      return null;
    }

    /**
     * If the current service does not have it already, resolve a project URN in all connected
     * services, choose the most appropriate one if multiple services provide it, retrieve it and
     * return it. Local services always have priority.
     *
     * @param scope
     * @param urn
     * @return
     */
    public static Pair<Project, ResourcesService> resolveProject(
        Scope scope, String urn, KlabService currentService) {
      return null;
    }

    /**
     * Resolve the worldview available to the current scope. Local services have priority. Parts of
     * the worldview provided by different services and accessible to the scope may be merged in the
     * final result.
     *
     * @param scope
     * @return
     */
    public static Worldview resolveWorldview(Scope scope) {
      return null;
    }

    public static Pair<ResourceTransport.Schema, ResourcesService> resolveExportSchema(
        Scope scope, String mediaType, Geometry geometry) {
      return null;
    }

    public static Pair<ResourceTransport.Schema, ResourcesService> resolveImportSchema(
        Scope scope, String urn, Geometry geometry) {
      return null;
    }
  }

  public static class Files extends org.integratedmodelling.klab.api.utils.Utils.Files {

    public static File copyInputStreamToTempFile(InputStream inputStream, String extension) {

      if (inputStream == null) {
        return null;
      }

      try {
        File tempFile =
            File.createTempFile("klab", extension.startsWith(".") ? extension : ("." + extension));
        try (FileOutputStream out = new FileOutputStream(tempFile)) {
          byte[] buffer = new byte[1024];
          int len;
          while ((len = inputStream.read(buffer)) != -1) {
            out.write(buffer, 0, len);
          }
        }
        return tempFile;
      } catch (IOException e) {
        throw new KlabIOException(e);
      }
    }
  }

  public static class Http {

    /**
     * A Future for an object being computed at service side and complying with the job management
     * system in all k.LAB services. Despite all attempts there's no way to avoid polling.
     *
     * <p>The polling is more frequent at the beginning, then becomes more sparse to avoid too many
     * service calls when the remote job is long-running. If there is no response from server. two
     * more attempts will be made at the set schedule 3 times before reporting failure.
     *
     * @param <T>
     */
    public static class PollingFuture<T> extends CompletableFuture<T> {

      private final Client client;
      private final ScheduledExecutorService scheduler =
          Executors.newSingleThreadScheduledExecutor();
      private final Class<T> resultClass;
      private int noResponseCount = 0;
      private final long id;
      private int[] stages;
      private int[] durations;
      private int stageCounter = 0;
      private int currentStage = 0;
      private long start = System.currentTimeMillis();

      /**
       * Delay for the next poll cycle in milliseconds
       *
       * @return
       */
      private int nextDelay() {
        if (stages[currentStage] < 0) {
          return durations[durations.length - 1];
        }
        if (stageCounter == stages[currentStage]) {
          currentStage++;
          stageCounter = 0;
        }
        stageCounter++;
        return durations[currentStage];
      }

      public static void main(String[] dio) {
        var pop =
            new PollingFuture<Object>(null, Object.class, 0, 100, 500, 7, 1000, 5, 1800, -1, 3000);
        for (int i = 0; i < 100; i++) {
          System.out.println(pop.nextDelay());
        }
      }

      /**
       * Create a PollingFuture for the specified client and result class. The client must have
       * started a k.LAB job and have all the configuration to connect to the session containing the
       * job manager.
       *
       * @param client
       * @param resultClass
       * @param id
       * @param waitStages pairs of (nTimes delay) for each phase desired. The last one should
       *     always have -1 as nTimes, which establishes the constant delay after the throttling
       *     phase is finished.
       */
      public PollingFuture(Client client, Class<T> resultClass, long id, int... waitStages) {
        // start polling
        this.id = id;
        this.client = client;
        this.resultClass = resultClass;
        if (waitStages != null && waitStages.length > 1) {
          int stage = 0;
          stages = new int[waitStages.length / 2];
          durations = new int[waitStages.length / 2];
          for (int i = 0; i < waitStages.length; i++) {
            stages[stage] = waitStages[i];
            durations[stage] = waitStages[++i];
            stage++;
          }
        }
        scheduler.schedule(this::poll, 300, TimeUnit.MILLISECONDS);
      }

      @Override
      public boolean cancel(boolean b) {
        return super.cancel(client.get(ServicesAPI.JOBS.CANCEL, Boolean.class, "id", id));
      }

      public void poll() {
        // if not done, reschedule, else complete. If exception (remote or local), complete
        // exceptionally.
        var status = client.get(ServicesAPI.JOBS.STATUS, JobStatus.class, "id", id);
        Logging.INSTANCE.info("Polling for job status: " + status);
        if (status == null) {
          if (noResponseCount == 3) {
            completeExceptionally(
                new KlabServiceAccessException("Service unresponsive after 3 attempts"));
          } else {
            // give it a bit more time
            scheduler.schedule(this::poll, nextDelay() * 2L, TimeUnit.MILLISECONDS);
            noResponseCount++;
          }
        } else if (status.getStatus() == Scope.Status.FINISHED) {
          try {
            var isData =
                org.integratedmodelling.klab.api.data.Data.class.isAssignableFrom(resultClass);
            var result =
                isData
                    ? client.getData(ServicesAPI.JOBS.RETRIEVE_DATA, resultClass, "id", id)
                    : client.get(ServicesAPI.JOBS.RETRIEVE, resultClass, "id", id);
            if (result != null) {
              complete(result);
            } else {
              completeExceptionally(
                  new KlabServiceAccessException(
                      status.getStackTrace() == null ? "Null result" : status.getStackTrace()));
            }
          } catch (Throwable t) {
            completeExceptionally(t);
          }
        } else if (status.getStatus() == Scope.Status.ABORTED) {
          completeExceptionally(
              new KlabServiceAccessException(
                  status.getStackTrace() == null ? "Server error" : status.getStackTrace()));
        } else if (status.getStatus() == Scope.Status.INTERRUPTED) {
          cancel(true);
        } else {
          // schedule the next step
          scheduler.schedule(this::poll, nextDelay(), TimeUnit.MILLISECONDS);
        }
      }
    }

    /**
     * HTTP client instrumented for k.LAB. Thread safe <em>except</em> when the response headers are
     * accessed (they are filled in after each call and reset before the next).
     */
    public static class Client implements AutoCloseable {

      private HttpClient client;
      private URI uri;
      private String authorization;
      private Scope scope; // may be null
      private final Map<String, String> headers = new HashMap<>();
      private final Map<String, List<String>> responseHeaders = new HashMap<>();
      private String forcedAcceptHeader = null;
      private String forcedContentHeader = null;
      private int timeoutSeconds = 10;
      private static final String AUTHENTICATION_HEADER = "Authentication";

      public void setAuthorization(String token) {
        this.authorization = token;
      }

      public void setHeader(String header, String value) {
        this.headers.put(header, value);
      }

      public CompletableFuture<org.integratedmodelling.klab.api.data.Data> postData(
          DataRequest dataRequest) {

        var apiCall = substituteTemplateParameters(ServicesAPI.RESOURCES.CONTEXTUALIZE, Map.of());
        responseHeaders.clear();

        try {

          var requestBuilder =
              HttpRequest.newBuilder()
                  .version(HttpClient.Version.HTTP_1_1)
                  .timeout(Duration.ofMinutes(10))
                  .uri(URI.create(uri + apiCall))
                  .header(
                      HttpHeaders.CONTENT_TYPE,
                      org.springframework.http.MediaType.APPLICATION_OCTET_STREAM_VALUE)
              /*                  .header(
              HttpHeaders.ACCEPT,
              org.springframework.http.MediaType.APPLICATION_OCTET_STREAM_VALUE)*/ ;

          if (authorization != null) {
            requestBuilder = requestBuilder.header(HttpHeaders.AUTHORIZATION, authorization);
          }

          for (String header : headers.keySet()) {
            requestBuilder = requestBuilder.header(header, headers.get(header));
          }

          ByteArrayOutputStream dataStream = new ByteArrayOutputStream();
          var encoder = EncoderFactory.get().binaryEncoder(dataStream, null);
          var writer = new SpecificDatumWriter<>(DataRequest.class);
          writer.write(dataRequest, encoder);
          encoder.flush();

          var request =
              requestBuilder
                  .POST(HttpRequest.BodyPublishers.ofByteArray(dataStream.toByteArray()))
                  .build();

          var response = client.send(request, HttpResponse.BodyHandlers.ofString());

          if (response != null && HttpStatus.valueOf(response.statusCode()).is2xxSuccessful()) {
            parseHeaders(response);
            var id = Long.parseLong(response.body());
            return new PollingFuture<>(
                this,
                org.integratedmodelling.klab.api.data.Data.class,
                id,
                100,
                500,
                7,
                1000,
                5,
                1800,
                -1,
                3000);
          } else {
            System.out.println(
                "============ POST " + request.uri() + " EXCEPTION REPORT ==============");
            //            var log = parseResponse(response.body(), Map.class);
            //            MapUtils.debugPrint(System.out, "Server error", log);
            System.out.println(
                "Server responded " + response.statusCode() + ": " + response.body());
            System.out.println("============ END OF REPORT  ==============");
            return CompletableFuture.failedFuture(
                new KlabServiceAccessException(
                    response.statusCode()
                        + " error in contextualization: response is "
                        + response.body()));
          }

        } catch (Throwable e) {
          if (scope != null) {
            System.out.println("============ POST " + apiCall + " EXCEPTION REPORT ==============");
            System.out.println(
                "Server error: response is not parseable as error: "
                    + apiCall
                    + ": "
                    + e.getMessage());
            System.out.println("============ END OF REPORT  ==============");
            scope.error(e);
            return CompletableFuture.failedFuture(new KlabServiceAccessException(e));
          }
        }

        return null;
      }

      static class Options {
        public boolean silent = false;
        // TODO
      }

      private Client() {}

      private Client(Client other) {
        this.client = other.client;
        this.uri = other.uri;
        this.scope = other.scope;
        this.headers.putAll(other.headers);
        this.authorization = other.authorization;
        this.forcedAcceptHeader = other.forcedAcceptHeader;
        this.forcedContentHeader = other.forcedContentHeader;
      }

      /**
       * Localize the scope for communication when the scope itself is not available but its ID is.
       *
       * @param scopeId
       * @return
       */
      public Client withScope(String scopeId) {
        var ret = new Client(this);
        ret.headers.put(ServicesAPI.SCOPE_HEADER, scopeId);
        return ret;
      }

      public Client withIdentity(Identity identity) {
        var ret = new Client(this);
        if (identity instanceof ServiceIdentity serviceIdentity) {
          ret.authorization = serviceIdentity.getToken();
        } else {
          ret.authorization = identity.getId();
        }
        return ret;
      }

      /**
       * Localize the scope for communication when the scope itself is not available but its ID is.
       *
       * @param authentication
       * @return
       */
      public Client withAuthentication(String authentication) {
        var ret = new Client(this);
        ret.headers.put(AUTHENTICATION_HEADER, authentication);
        return ret;
      }

      /**
       * Modify the default timeout.
       *
       * @param timeoutSeconds the new timeout in seconds
       * @return
       */
      public Client withTimeout(int timeoutSeconds) {
        var ret = new Client(this);
        ret.timeoutSeconds = timeoutSeconds;
        return ret;
      }

      /**
       * Quick call to add a header with no checks or options. Returns the SAME client after
       * modifying it. Does nothing if value is null.
       *
       * @param header
       * @param value
       * @return
       */
      public Client withHeader(String header, String value) {
        if (value != null) {
          headers.put(header, value);
        }
        return this;
      }

      /**
       * Localize the scope to another. Headers passed with the request will reflect the scope
       * nesting.
       *
       * @param scope
       * @return
       */
      public Client withScope(Scope scope) {
        if (scope == null) {
          return this;
        }
        var ret = new Client(this);
        ret.scope = scope;
        if (scope instanceof ContextScope contextScope) {
          ret.headers.put(ServicesAPI.SCOPE_HEADER, ContextScope.getScopeId(contextScope));
          ret.headers.put(ServicesAPI.SERVICE_ID_HEADER, contextScope.getHostServiceId());
        } else if (scope instanceof SessionScope sessionScope) {
          ret.headers.put(ServicesAPI.SCOPE_HEADER, sessionScope.getId());
          ret.headers.put(ServicesAPI.SERVICE_ID_HEADER, sessionScope.getHostServiceId());
        }
        return ret;
      }

      public Client accepting(List<String> mediaTypes) {
        var ret = new Client(this);
        ret.forcedAcceptHeader = Strings.join(mediaTypes, ", ");
        return ret;
      }

      public Client providing(List<String> mediaTypes) {
        var ret = new Client(this);
        ret.forcedContentHeader = Strings.join(mediaTypes, ", ");
        return ret;
      }

      /**
       * Return the first (assumed only) header from the response to the previous call, or null
       *
       * @param header
       * @return
       */
      public String getResponseHeader(String header) {
        var list = responseHeaders.get(header);
        if (list != null && list.size() > 0) {
          return list.get(0);
        }
        return null;
      }

      /**
       * Return all values for the passed response header, or the empty list.
       *
       * @param header
       * @return
       */
      public List<String> getResponseHeaders(String header) {
        var list = responseHeaders.get(header);
        if (list != null) {
          return list;
        }
        return List.of();
      }

      /**
       * Download something into a temporary file. If there is a "format" parameter or the media
       * type has been forced in the client using {@link #accepting(List)}, it must be a valid media
       * type which will also determine the extension of the file.
       *
       * @param apiRequest
       * @param parameters
       * @return
       */
      public File download(String apiRequest, Object... parameters) {

        try {
          var options = new Options();
          var params = makeKeyMap(options, parameters);
          var apiCall = substituteTemplateParameters(apiRequest, params);

          String mediaType =
              forcedAcceptHeader == null
                  ? (params.containsKey("format")
                      ? params.get("format").toString()
                      : MediaType.OCTET_STREAM.toString())
                  : forcedAcceptHeader;

          String fileExtension = MimeTypes.getDefaultMimeTypes().forName(mediaType).getExtension();
          if (fileExtension == null || fileExtension.isEmpty()) {
            fileExtension = "bin";
          }

          var ret = File.createTempFile("klab", "." + fileExtension);
          var request = new HttpGet(URI.create(uri + apiCall + encodeParameters(params)));
          request.setHeader(HttpHeaders.ACCEPT, mediaType);
          if (authorization != null) {
            request.setHeader(HttpHeaders.AUTHORIZATION, authorization);
          }
          for (var header : headers.keySet()) {
            request.setHeader(header, headers.get(header));
          }
          try (var client = HttpClientBuilder.create().build()) {
            var response = client.execute(request);
            HttpEntity entity = response.getEntity();
            if (entity != null) {
              try (var output = new FileOutputStream(ret)) {
                entity.writeTo(output);
              } catch (IOException exception) {
                scope.error(exception);
                return null;
              }
            }
            return ret;
          }
        } catch (Exception e) {
          throw new RuntimeException(e);
        }
      }

      /**
       * Send over a file with optional parameters
       *
       * @param apiRequest
       * @param resultClass
       * @param <T>
       * @return
       */
      public <T> T upload(
          String apiRequest, File upload, Class<T> resultClass, Object... parameters) {

        var options = new Options();
        var params = makeKeyMap(options, parameters);
        var apiCall = substituteTemplateParameters(apiRequest, params);

        responseHeaders.clear();

        try {
          final MultipartEntityBuilder builder = MultipartEntityBuilder.create();
          builder.setMode(HttpMultipartMode.RFC6532);
          builder.addPart("file", new FileBody(upload));
          builder.addPart("fileName", new StringBody(Utils.Files.getFileName(upload)));
          final HttpEntity entity = builder.build();
          HttpPost post = new HttpPost(URI.create(uri + apiCall + encodeParameters(params)));
          post.setHeader(HttpHeaders.ACCEPT, getAcceptedMediaType(resultClass));

          if (authorization != null) {
            post.setHeader(HttpHeaders.AUTHORIZATION, authorization);
          }

          for (String header : headers.keySet()) {
            post.setHeader(header, headers.get(header));
          }

          post.setEntity(entity);
          try (CloseableHttpClient client = HttpClientBuilder.create().build()) {

            var response = client.execute(post);

            final int statusCode = response.getStatusLine().getStatusCode();

            if (HttpStatus.valueOf(statusCode).is2xxSuccessful()) {
              return parseResponse(
                  IOUtils.toString(response.getEntity().getContent(), StandardCharsets.UTF_8),
                  resultClass);
            } else {
              var log =
                  parseResponse(
                      IOUtils.toString(response.getEntity().getContent(), StandardCharsets.UTF_8),
                      Map.class);
              // TODO do something with the error response (which should be better and
              //  contain a stack trace)
            }
          }
        } catch (Throwable e) {
          if (scope != null) {
            scope.error(e, options.silent ? Notification.Mode.Silent : Notification.Mode.Normal);
          } else {
            //                        e.printStackTrace();
          }
        }
        return null;
      }

      /**
       * Send over a file with optional parameters, expecting a task ID and returning a completable
       * future that will finish when the remote job caused by the upload is done.
       *
       * @param apiRequest
       * @param resultClass
       * @param <T>
       * @return
       */
      public <T> CompletableFuture<T> uploadAsync(
          String apiRequest, File upload, Class<T> resultClass, Object... parameters) {

        var options = new Options();
        var params = makeKeyMap(options, parameters);
        var apiCall = substituteTemplateParameters(apiRequest, params);

        responseHeaders.clear();

        try {
          final MultipartEntityBuilder builder = MultipartEntityBuilder.create();
          builder.setMode(HttpMultipartMode.RFC6532);
          builder.addPart("file", new FileBody(upload));
          builder.addPart("fileName", new StringBody(Utils.Files.getFileName(upload)));
          final HttpEntity entity = builder.build();
          HttpPost post = new HttpPost(URI.create(uri + apiCall + encodeParameters(params)));
          post.setHeader(HttpHeaders.ACCEPT, getAcceptedMediaType(resultClass));

          if (authorization != null) {
            post.setHeader(HttpHeaders.AUTHORIZATION, authorization);
          }

          for (String header : headers.keySet()) {
            post.setHeader(header, headers.get(header));
          }

          post.setEntity(entity);
          try (CloseableHttpClient client = HttpClientBuilder.create().build()) {

            var response = client.execute(post);
            final int statusCode = response.getStatusLine().getStatusCode();

            var body = IOUtils.toString(response.getEntity().getContent(), StandardCharsets.UTF_8);
            if (HttpStatus.valueOf(statusCode).is2xxSuccessful()) {
              var id = Long.parseLong(body);
              return new PollingFuture<>(
                  this, resultClass, id, 100, 500, 7, 1000, 5, 1800, -1, 3000);
            } else {
              var log = parseResponse(body, Map.class);
              Logging.INSTANCE.error(
                  "============ POST " + post.getURI() + " EXCEPTION REPORT ==============");
              Logging.INSTANCE.error(Maps.debugPrint(log));
              Logging.INSTANCE.error("============ END OF REPORT  ==============");
              return CompletableFuture.failedFuture(new KlabServiceAccessException(body));
            }
          }
        } catch (Throwable e) {
          if (scope != null) {
            scope.error(e, options.silent ? Notification.Mode.Silent : Notification.Mode.Normal);
          }
          return CompletableFuture.failedFuture(e);
        }
      }

      /**
       * GET helper that sets all headers and automatically handles JSON marshalling.
       *
       * @param apiRequest
       * @param resultClass
       * @param <T>
       * @return
       */
      public <T> T post(
          String apiRequest, Object payload, Class<T> resultClass, Object... parameters) {

        var options = new Options();
        var params = makeKeyMap(options, parameters);
        var apiCall = substituteTemplateParameters(apiRequest, params);

        responseHeaders.clear();

        try {
          var payloadText = payload instanceof String ? (String) payload : Json.asString(payload);

          var uriBuilder = new URIBuilder(uri + apiCall);
          for (String key : params.keySet()) {
            if (params.get(key) != null) {
              uriBuilder = uriBuilder.addParameter(key, params.get(key).toString());
            }
          }

          var requestBuilder =
              HttpRequest.newBuilder()
                  .version(HttpClient.Version.HTTP_1_1)
                  .timeout(Duration.ofSeconds(timeoutSeconds))
                  .uri(uriBuilder.build());
          if (forcedAcceptHeader != null) {
            requestBuilder = requestBuilder.header(HttpHeaders.ACCEPT, forcedAcceptHeader);
          } else {
            requestBuilder =
                requestBuilder.header(HttpHeaders.ACCEPT, getAcceptedMediaType(resultClass));
          }

          if (forcedContentHeader != null) {
            requestBuilder = requestBuilder.header(HttpHeaders.CONTENT_TYPE, forcedContentHeader);
          } else {
            requestBuilder =
                requestBuilder.header(
                    HttpHeaders.CONTENT_TYPE,
                    payload instanceof String
                        ? MediaType.PLAIN_TEXT_UTF_8.toString()
                        : MediaType.JSON_UTF_8.toString());
          }

          if (authorization != null) {
            requestBuilder = requestBuilder.header(HttpHeaders.AUTHORIZATION, authorization);
          }

          for (String header : headers.keySet()) {
            requestBuilder = requestBuilder.header(header, headers.get(header));
          }

          var request =
              requestBuilder.POST(HttpRequest.BodyPublishers.ofString(payloadText)).build();

          var response = client.send(request, HttpResponse.BodyHandlers.ofString());

          if (response != null && HttpStatus.valueOf(response.statusCode()).is2xxSuccessful()) {
            parseHeaders(response);
            return parseResponse(response.body(), resultClass);
          } else {
            Logging.INSTANCE.error(
                "========== POST " + apiCall + " return " + response.statusCode());
            var log = parseResponse(response.body(), Map.class);
            Logging.INSTANCE.error(
                "============ POST " + request.uri() + " EXCEPTION REPORT ==============");
            Logging.INSTANCE.error(Maps.debugPrint(log));
            Logging.INSTANCE.error("============ END OF REPORT  ==============");
            // TODO do something with the error response (which should be better and
            //  contain a stack trace)
          }

        } catch (Throwable e) {
          if (scope != null) {
            scope.error(e, options.silent ? Notification.Mode.Silent : Notification.Mode.Normal);
          } else {
            //                        e.printStackTrace();
          }
        }

        return null;
      }

      /**
       * GET helper that sets all headers and automatically handles JSON marshalling.
       *
       * @param apiRequest
       * @param resultClass
       * @param <T>
       * @return
       */
      public <T> CompletableFuture<T> postAsync(
          String apiRequest, Object payload, Class<T> resultClass, Object... parameters) {

        var options = new Options();
        var params = makeKeyMap(options, parameters);
        var apiCall = substituteTemplateParameters(apiRequest, params);

        responseHeaders.clear();

        try {
          var payloadText = payload instanceof String ? (String) payload : Json.asString(payload);

          var uriBuilder = new URIBuilder(uri + apiCall);
          for (String key : params.keySet()) {
            if (params.get(key) != null) {
              uriBuilder = uriBuilder.addParameter(key, params.get(key).toString());
            }
          }

          var requestBuilder =
              HttpRequest.newBuilder()
                  .version(HttpClient.Version.HTTP_1_1)
                  .timeout(Duration.ofSeconds(timeoutSeconds))
                  .uri(uriBuilder.build());
          if (forcedAcceptHeader != null) {
            requestBuilder = requestBuilder.header(HttpHeaders.ACCEPT, forcedAcceptHeader);
          } else {
            requestBuilder =
                requestBuilder.header(HttpHeaders.ACCEPT, getAcceptedMediaType(resultClass));
          }

          if (forcedContentHeader != null) {
            requestBuilder = requestBuilder.header(HttpHeaders.CONTENT_TYPE, forcedContentHeader);
          } else {
            requestBuilder =
                requestBuilder.header(
                    HttpHeaders.CONTENT_TYPE,
                    payload instanceof String
                        ? MediaType.PLAIN_TEXT_UTF_8.toString()
                        : MediaType.JSON_UTF_8.toString());
          }

          if (authorization != null) {
            requestBuilder = requestBuilder.header(HttpHeaders.AUTHORIZATION, authorization);
          }

          for (String header : headers.keySet()) {
            requestBuilder = requestBuilder.header(header, headers.get(header));
          }

          var request =
              requestBuilder.POST(HttpRequest.BodyPublishers.ofString(payloadText)).build();

          var response = client.send(request, HttpResponse.BodyHandlers.ofString());

          if (HttpStatus.valueOf(response.statusCode()).is2xxSuccessful()) {
            var id = Long.parseLong(response.body());
            return new PollingFuture<>(this, resultClass, id, 100, 500, 7, 1000, 5, 1800, -1, 3000);
          } else {
            var log = parseResponse(response.body(), Map.class);
            Logging.INSTANCE.error(
                "============ POST " + request.uri() + " EXCEPTION REPORT ==============");
            Logging.INSTANCE.error(Maps.debugPrint(log));
            Logging.INSTANCE.error("============ END OF REPORT  ==============");
            return CompletableFuture.failedFuture(new KlabServiceAccessException(response.body()));
          }

        } catch (Throwable e) {
          if (scope != null) {
            scope.error(e, options.silent ? Notification.Mode.Silent : Notification.Mode.Normal);
          }
          return CompletableFuture.failedFuture(e);
        }
      }

      public <T> List<T> postCollection(
          String apiRequest, Object payload, Class<T> resultClass, Object... parameters) {

        var options = new Options();
        var params = makeKeyMap(options, parameters);
        var apiCall = substituteTemplateParameters(apiRequest, params);
        responseHeaders.clear();

        try {
          var payloadText = payload instanceof String ? (String) payload : Json.asString(payload);
          var requestBuilder =
              HttpRequest.newBuilder()
                  .version(HttpClient.Version.HTTP_1_1)
                  .timeout(Duration.ofSeconds(timeoutSeconds))
                  .uri(URI.create(uri + apiCall + encodeParameters(params)));
          if (forcedAcceptHeader != null) {
            requestBuilder = requestBuilder.header(HttpHeaders.ACCEPT, forcedAcceptHeader);
          } else {
            requestBuilder =
                requestBuilder.header(HttpHeaders.ACCEPT, getAcceptedMediaType(resultClass));
          }

          if (forcedContentHeader != null) {
            requestBuilder = requestBuilder.header(HttpHeaders.CONTENT_TYPE, forcedContentHeader);
          } else {
            requestBuilder =
                requestBuilder.header(
                    HttpHeaders.CONTENT_TYPE,
                    payload instanceof String
                        ? MediaType.PLAIN_TEXT_UTF_8.toString()
                        : MediaType.JSON_UTF_8.toString());
          }

          if (authorization != null) {
            requestBuilder = requestBuilder.header(HttpHeaders.AUTHORIZATION, authorization);
          }

          for (String header : headers.keySet()) {
            requestBuilder = requestBuilder.header(header, headers.get(header));
          }

          var request =
              requestBuilder.POST(HttpRequest.BodyPublishers.ofString(payloadText)).build();

          var response = client.send(request, HttpResponse.BodyHandlers.ofString());

          if (response != null && HttpStatus.valueOf(response.statusCode()).is2xxSuccessful()) {
            parseHeaders(response);
            return parseResponseList(response.body(), resultClass);
          }

        } catch (Throwable e) {
          if (scope != null) {
            scope.error(e, options.silent ? Notification.Mode.Silent : Notification.Mode.Normal);
          } else {
            //                        e.printStackTrace();
          }
        }

        return java.util.Collections.emptyList();
      }

      private String getAcceptedMediaType(Class<?> resultClass) {
        if (String.class == resultClass) {
          return MediaType.PLAIN_TEXT_UTF_8.toString();
        }
        // TODO more
        return MediaType.JSON_UTF_8.toString();
      }

      /**
       * Substitutes any {xxx} template variable in request and remove the keys from the parameter
       * map.
       *
       * @param request
       * @param parameters
       * @return
       */
      private String substituteTemplateParameters(String request, Map<String, Object> parameters) {
        var ret = request;
        var toRemove = new HashSet<String>();
        for (String key : parameters.keySet()) {
          var subst = "{" + key + "}";
          if (request.contains(subst)) {
            ret =
                ret.replace(
                    subst,
                    UriUtils.encodeQueryParam(
                        parameters.get(key).toString(), StandardCharsets.UTF_8));
            toRemove.add(key);
          }
        }
        for (var k : toRemove) parameters.remove(k);
        return ret;
      }

      private String encodeParameters(Map<String, Object> parameters) {
        StringBuilder ret = new StringBuilder();
        for (var k : parameters.keySet()) {
          if (parameters.get(k) == null) {
            continue;
          }
          ret.append((ret.isEmpty()) ? "?" : "&")
              .append(k)
              .append("=")
              .append(
                  UriUtils.encodeQueryParam(parameters.get(k).toString(), StandardCharsets.UTF_8));
        }
        return ret.toString();
      }

      /**
       * Make a map of the parameter list filtering out any options for the handling of errors and
       * notifications.
       *
       * @param options
       * @param parameters
       * @return
       */
      private static Map<String, Object> makeKeyMap(Options options, Object[] parameters) {
        var ret = new LinkedHashMap<String, Object>();
        if (parameters != null) {
          for (int i = 0; i < parameters.length; i++) {
            if (parameters[i] instanceof Notification.Mode mode) {
              options.silent = mode == Notification.Mode.Silent;
              continue;
            }
            if (i == parameters.length - 1) {
              throw new KlabIllegalArgumentException(
                  "Utils.Maps.makeKeyMap: unmatched " + "keys " + "in " + "argument list");
            }
            ret.put(parameters[i].toString(), parameters[++i]);
          }
        }
        return ret;
      }

      /**
       * Use a simple socket to check if the service is alive
       *
       * @return
       */
      public boolean isAlive() {
        var host = this.uri.getHost();
        var port = this.uri.getPort() == -1 ? 80 : this.uri.getPort();
        try (var socket = new Socket(host, port)) {
          return true;
        } catch (Exception e) {
          return false;
        }
      }

      /**
       * GET helper that sets all headers and automatically handles JSON marshalling.
       *
       * @param apiRequest the request starting with "/" appended to the main service URL. Add any ?
       *     parameters here.
       * @param resultClass
       * @param parameters paired key, value sequence for URL <em>path</em> template options.
       *     Explicit ?... URL parameters should be added to the URL directly.
       * @param <T>
       * @return
       */
      public <T> T get(String apiRequest, Class<T> resultClass, Object... parameters) {

        var options = new Options();
        var params = makeKeyMap(options, parameters);
        var apiCall = substituteTemplateParameters(apiRequest, params);
        responseHeaders.clear();

        try {
          var requestBuilder = HttpRequest.newBuilder().GET();
          if (authorization != null) {
            requestBuilder = requestBuilder.header(HttpHeaders.AUTHORIZATION, authorization);
          }
          for (String header : headers.keySet()) {
            requestBuilder = requestBuilder.header(header, headers.get(header));
          }

          if (forcedAcceptHeader != null) {
            requestBuilder = requestBuilder.header(HttpHeaders.ACCEPT, forcedAcceptHeader);
          }

          if (Void.class != resultClass) {
            var response =
                client.send(
                    requestBuilder
                        .uri(URI.create(uri + apiCall + encodeParameters(params)))
                        .timeout(Duration.ofSeconds(timeoutSeconds))
                        .build(),
                    HttpResponse.BodyHandlers.ofString());

            if (response != null && HttpStatus.valueOf(response.statusCode()).is2xxSuccessful()) {
              parseHeaders(response);
              return parseResponse(response.body(), resultClass);
            }
          } else {
            client.send(
                requestBuilder.uri(URI.create(uri + apiCall + encodeParameters(params))).build(),
                HttpResponse.BodyHandlers.discarding());
          }

        } catch (Throwable e) {
          if (scope != null) {
            scope.error(e, options.silent ? Notification.Mode.Silent : Notification.Mode.Normal);
          } else {
            //                        e.printStackTrace();
          }
        }
        return null;
      }

      /**
       * GET helper that sets all headers and automatically handles JSON marshalling.
       *
       * @param apiRequest the request starting with "/" appended to the main service URL. Add any ?
       *     parameters here.
       * @param resultClass
       * @param parameters paired key, value sequence for URL <em>path</em> template options.
       *     Explicit ?... URL parameters should be added to the URL directly.
       * @param <T>
       * @return
       */
      public <T> T getData(String apiRequest, Class<T> resultClass, Object... parameters) {

        var options = new Options();
        var params = makeKeyMap(options, parameters);
        var apiCall = substituteTemplateParameters(apiRequest, params);
        responseHeaders.clear();

        try {
          var requestBuilder = HttpRequest.newBuilder().GET();
          if (authorization != null) {
            requestBuilder = requestBuilder.header(HttpHeaders.AUTHORIZATION, authorization);
          }
          for (String header : headers.keySet()) {
            requestBuilder = requestBuilder.header(header, headers.get(header));
          }

          if (forcedAcceptHeader != null) {
            requestBuilder = requestBuilder.header(HttpHeaders.ACCEPT, forcedAcceptHeader);
          }

          if (Void.class != resultClass) {
            var response =
                client.send(
                    requestBuilder
                        .uri(URI.create(uri + apiCall + encodeParameters(params)))
                        .timeout(Duration.ofSeconds(timeoutSeconds))
                        .build(),
                    HttpResponse.BodyHandlers.ofInputStream());

            if (response != null && HttpStatus.valueOf(response.statusCode()).is2xxSuccessful()) {
              parseHeaders(response);
              var decoder = DecoderFactory.get().binaryDecoder(response.body(), null);
              var reader = new SpecificDatumReader<>(Instance.class);
              // TODO FIXME decode the notifications from the Instance
              return (T) BaseDataImpl.create(reader.read(null, decoder), List.of());
            }
          } else {
            client.send(
                requestBuilder.uri(URI.create(uri + apiCall + encodeParameters(params))).build(),
                HttpResponse.BodyHandlers.discarding());
          }

        } catch (Throwable e) {
          if (scope != null) {
            scope.error(e, options.silent ? Notification.Mode.Silent : Notification.Mode.Normal);
          } else {
            //                        e.printStackTrace();
          }
        }
        return null;
      }

      public <T> List<T> getCollection(
          String apiRequest, Class<T> resultClass, Object... parameters) {

        var options = new Options();
        var params = makeKeyMap(options, parameters);
        var apiCall = substituteTemplateParameters(apiRequest, params);
        responseHeaders.clear();

        try {
          var requestBuilder = HttpRequest.newBuilder().GET();
          if (authorization != null) {
            requestBuilder = requestBuilder.header(HttpHeaders.AUTHORIZATION, authorization);
          }
          for (String header : headers.keySet()) {
            requestBuilder = requestBuilder.header(header, headers.get(header));
          }

          if (forcedAcceptHeader != null) {
            requestBuilder = requestBuilder.header(HttpHeaders.ACCEPT, forcedAcceptHeader);
          }

          var response =
              client.send(
                  requestBuilder
                      .uri(URI.create(uri + apiCall + encodeParameters(params)))
                      .timeout(Duration.ofSeconds(timeoutSeconds))
                      .build(),
                  HttpResponse.BodyHandlers.ofString());

          if (response != null && HttpStatus.valueOf(response.statusCode()).is2xxSuccessful()) {
            parseHeaders(response);
            return parseResponseList(response.body(), resultClass);
          }

        } catch (Throwable e) {
          if (scope != null) {
            scope.error(e, options.silent ? Notification.Mode.Silent : Notification.Mode.Normal);
          } else {
            //                        e.printStackTrace();
          }
        }

        return java.util.Collections.emptyList();
      }

      /**
       * PUT helper that sets all headers and automatically handles JSON marshalling.
       *
       * @param apiRequest
       * @param parameters paired key, value sequence for URL options
       * @return
       */
      public boolean put(String apiRequest, Object... parameters) {

        var options = new Options();
        var params = makeKeyMap(options, parameters);
        var apiCall = substituteTemplateParameters(apiRequest, params);
        responseHeaders.clear();

        try {
          var requestBuilder = HttpRequest.newBuilder().PUT(HttpRequest.BodyPublishers.noBody());
          if (authorization != null) {
            requestBuilder = requestBuilder.header(HttpHeaders.AUTHORIZATION, authorization);
          }
          for (String header : headers.keySet()) {
            requestBuilder = requestBuilder.header(header, headers.get(header));
          }

          if (forcedAcceptHeader != null) {
            requestBuilder = requestBuilder.header(HttpHeaders.ACCEPT, forcedAcceptHeader);
          }

          var response =
              client.send(
                  requestBuilder.uri(URI.create(uri + apiCall + encodeParameters(params))).build(),
                  HttpResponse.BodyHandlers.discarding());

          if (response != null && HttpStatus.valueOf(response.statusCode()).is2xxSuccessful()) {
            parseHeaders(response);
            return true;
          }

        } catch (Throwable e) {
          if (scope != null) {
            scope.error(e, options.silent ? Notification.Mode.Silent : Notification.Mode.Normal);
          } else {
            //                        e.printStackTrace();
          }
        }

        return false;
      }

      public void parseHeaders(HttpResponse<?> response) {
        responseHeaders.putAll(response.headers().map());
      }

      private <T> List<T> parseResponseList(String body, Class<T> resultClass) {
        if (body.startsWith("[")) {

          List<T> ret = new ArrayList<>();
          List<?> list = Json.parseObject(body, List.class);
          for (var object : list) {
            if (object == null) {
              ret.add(null);
            } else if (resultClass.isAssignableFrom(object.getClass())) {
              ret.add((T) object);
            } else if (object instanceof Collection) {
              // TODO
              throw new KlabUnimplementedException(
                  "json retrieval of collections within " + "collections");
            } else if (object instanceof Map map) {
              ret.add(Json.convertMap(map, resultClass));
            }
          }
          return ret;

        } else {
          // try parsing a single object and returning as a list
          T object = parseResponse(body, resultClass);
          if (object != null) {
            return List.of(object);
          }
        }

        return java.util.Collections.emptyList();
      }

      private <T> T parseResponse(String body, Class<T> resultClass) {
        if (body == null) {
          return null;
        }
        if (resultClass == String.class) {
          return (T) body;
        } else if (resultClass == Boolean.class) {
          return (T) Boolean.valueOf(body);
        } else if (resultClass == Integer.class) {
          return (T) Integer.valueOf(body);
        } else if (resultClass == Long.class) {
          return (T) Long.valueOf(body);
        } else if (resultClass == Double.class) {
          return (T) Double.valueOf(body);
        } else if (resultClass == Float.class) {
          return (T) Float.valueOf(body);
        } else if (resultClass.isEnum()) {
          try {
            var method = resultClass.getMethod("valueOf", String.class);
            return (T) method.invoke(null, body);
          } catch (Throwable e) {
            throw new KlabIllegalArgumentException(e);
          }
        }
        //        try {
        return Json.parseObject(body, resultClass);
        //        } catch (IOException e) {
        //            throw new KlabIOException(e);
        //        }
      }

      @Override
      public void close() throws Exception {
        if (client != null) {
          client.close();
        }
      }
    }

    /**
     * Get a configured client for a specific URL; if the URL is recognized as being handled by a
     * specific authentication scheme, use the configured credentials for it and automatically build
     * the authentication strategy into the returned client. This should be used for services
     * outside the k.LAB network that do not require an authenticated scope. Use within a try {}
     * pattern to ensure that the connection is closed appropriately.
     *
     * @param serviceUrl
     * @return
     */
    public static Client getClient(URL serviceUrl, Scope scope) {
      return getClient(serviceUrl.toString(), scope);
    }

    /**
     * Get a configured client for a specific URL; if the URL is recognized as being handled by a
     * specific authentication scheme, use the configured credentials for it and automatically build
     * the authentication strategy into the returned client. This should be used for services
     * outside the k.LAB network that do not require an authenticated scope. Use within a try {}
     * pattern to ensure that the connection is closed appropriately.
     *
     * @param serviceUrl
     * @return
     */
    public static Client getClient(String serviceUrl, Scope scope) {
      // TODO use configuration for timeouts and other options
      var client =
          HttpClient.newBuilder()
              .version(HttpClient.Version.HTTP_1_1)
              .connectTimeout(Duration.ofSeconds(10))
              .build();
      var ret = new Client();
      ret.client = client;
      ret.uri = URI.create(serviceUrl);
      ret.scope = scope;
      return ret;
    }

    /**
     * Get an authenticated client for a k.LAB service in a given scope. If needed, notify the scope
     * to the service passing the hub token and obtain authorization. Clients for local services
     * should always be authorized. Use within a try {} pattern to ensure that the connection is
     * closed appropriately.
     *
     * @param service
     * @return a client authenticated for the service in the passed scope
     * @throws org.integratedmodelling.klab.api.exceptions.KlabAuthorizationException if not
     *     authorized to access
     */
    public static Client getServiceClient(String authorization, KlabService service) {

      var client =
          HttpClient.newBuilder()
              .version(HttpClient.Version.HTTP_1_1)
              .connectTimeout(Duration.ofSeconds(10))
              .build();
      var ret = new Client();
      ret.client = client;
      try {
        ret.uri = service.getUrl().toURI();
        ret.authorization = authorization;
        ret.scope = service.serviceScope();
      } catch (URISyntaxException e) {
        throw new KlabInternalErrorException(e);
      }
      return ret;
    }

    /**
     * Get a non-authenticated client for a URL, storing only the service ID and meant to be
     * specialized with authentication in a child client. This can only be used to connect to public
     * endpoints, such as status.
     */
    public static Client getServiceClient(URL url) {

      var client =
          HttpClient.newBuilder()
              .version(HttpClient.Version.HTTP_1_1)
              .connectTimeout(Duration.ofSeconds(10))
              .build();
      var ret = new Http.Client();
      ret.client = client;
      try {
        ret.uri = url.toURI();
      } catch (URISyntaxException e) {
        throw new KlabInternalErrorException(e);
      }
      return ret;
    }
  }

  public static class Json {

    static ObjectMapper defaultMapper;

    static {
      defaultMapper =
          new ObjectMapper()
              .enable(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
              .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
              .enable(SerializationFeature.WRITE_NULL_MAP_VALUES)
              .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
      defaultMapper.getSerializerProvider().setNullKeySerializer(new NullKeySerializer());
      JacksonConfiguration.configureObjectMapperForKlabTypes(defaultMapper);
      MessageImpl.setPayloadMapTranslator((map, clazz) -> defaultMapper.convertValue(map, clazz));
    }

    public static ObjectMapper newObjectMapper() {
      var ret =
          new ObjectMapper()
              .enable(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
              .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
              .enable(SerializationFeature.WRITE_NULL_MAP_VALUES)
              .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

      ret.getSerializerProvider().setNullKeySerializer(new NullKeySerializer());
      JacksonConfiguration.configureObjectMapperForKlabTypes(ret);
      return ret;
    }

    static class NullKeySerializer extends StdSerializer<Object> {

      @Serial private static final long serialVersionUID = 7120301608140961908L;

      public NullKeySerializer() {
        this(null);
      }

      public NullKeySerializer(Class<Object> t) {
        super(t);
      }

      @Override
      public void serialize(Object nullKey, JsonGenerator jsonGenerator, SerializerProvider unused)
          throws IOException {
        jsonGenerator.writeFieldName("");
      }
    }

    /**
     * Default conversion for a map object.
     *
     * @param node the node
     * @return the map
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> asMap(JsonNode node) {
      return defaultMapper.convertValue(node, Map.class);
    }

    /**
     * Default conversion, use within custom deserializers to "normally" deserialize an object.
     *
     * @param <T> the generic type
     * @param node the node
     * @param cls the cls
     * @return the t
     */
    public static <T> T as(JsonNode node, Class<T> cls) {
      return defaultMapper.convertValue(node, cls);
    }

    /**
     * Convert node to list of type T.
     *
     * @param <T> the generic type
     * @param node the node
     * @param cls the cls
     * @return the list
     */
    public static <T> List<T> asList(JsonNode node, Class<T> cls) {
      return defaultMapper.convertValue(node, new TypeReference<List<T>>() {});
    }

    public static <T> List<T> asList(JsonNode node, Class<T> cls, ObjectMapper mapper) {
      return mapper.convertValue(node, new TypeReference<List<T>>() {});
    }

    @SuppressWarnings("unchecked")
    public static <T> T parseObject(String text, Class<T> cls) {
      try {
        return (T) defaultMapper.readerFor(cls).readValue(text);
      } catch (IOException e) {
        throw new IllegalArgumentException(e);
      }
    }

    /**
     * Convert node to list of type T.
     *
     * @param <T> the generic type
     * @param node the node
     * @param cls the cls
     * @return the sets the
     */
    public static <T> Set<T> asSet(JsonNode node, Class<T> cls) {
      return defaultMapper.convertValue(node, new TypeReference<Set<T>>() {});
    }

    @SuppressWarnings("unchecked")
    public static <T> T cloneObject(T object) {
      return (T) parseObject(printAsJson(object), object.getClass());
    }

    /**
     * Load an object from an input stream.
     *
     * @param url the input stream
     * @param cls the class
     * @return the object
     * @throws KlabIOException
     */
    public static <T> T load(InputStream url, Class<T> cls) throws KlabIOException {
      try {
        return defaultMapper.readValue(url, cls);
      } catch (Exception e) {
        throw new KlabIOException(e);
      }
    }

    /**
     * Load an object from a file.
     *
     * @param file
     * @param cls
     * @return the object
     * @throws KlabIOException
     */
    public static <T> T load(File file, Class<T> cls) throws KlabIOException {
      try {
        return defaultMapper.readValue(file, cls);
      } catch (Exception e) {
        throw new KlabIOException(e);
      }
    }

    /**
     * Load an object from a URL.
     *
     * @param url
     * @param cls
     * @return the object
     * @throws KlabIOException
     */
    public static <T> T load(URL url, Class<T> cls) throws KlabIOException {
      try {
        return defaultMapper.readValue(url, cls);
      } catch (Exception e) {
        throw new KlabIOException(e);
      }
    }

    /**
     * Serialize an object to a file.
     *
     * @param object
     * @param outFile
     * @throws KlabIOException
     */
    public static void save(Object object, File outFile) throws KlabIOException {
      try {
        defaultMapper.writeValue(outFile, object);
      } catch (Exception e) {
        throw new KlabIOException(e);
      }
    }

    public static String asString(Object object) {
      try {
        return defaultMapper.writeValueAsString(object);
      } catch (JsonProcessingException e) {
        throw new IllegalArgumentException("serialization failed: " + e.getMessage());
      }
    }

    /**
     * Serialize the passed object as JSON and pretty-print the resulting code.
     *
     * @param object the object
     * @return the string
     */
    public static String printAsJson(Object object) {

      ObjectMapper om = new ObjectMapper();
      om.enable(SerializationFeature.INDENT_OUTPUT); // pretty print
      om.enable(SerializationFeature.WRITE_NULL_MAP_VALUES); // pretty print
      om.enable(SerializationFeature.WRITE_SINGLE_ELEM_ARRAYS_UNWRAPPED); // pretty print
      om.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);

      try {
        return om.writeValueAsString(object);
      } catch (JsonProcessingException e) {
        throw new IllegalArgumentException("serialization failed: " + e.getMessage());
      }
    }

    /**
     * Serialize the passed object as JSON and pretty-print the resulting code.
     *
     * @param object the object
     * @param file
     * @return the string
     */
    public static void printAsJson(Object object, File file) {

      ObjectMapper om = new ObjectMapper();
      om.enable(SerializationFeature.INDENT_OUTPUT); // pretty print
      om.enable(SerializationFeature.WRITE_NULL_MAP_VALUES); // pretty print
      om.enable(SerializationFeature.WRITE_SINGLE_ELEM_ARRAYS_UNWRAPPED); // pretty print
      om.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);

      try {
        om.writeValue(file, object);
      } catch (Exception e) {
        throw new IllegalArgumentException("serialization failed: " + e.getMessage());
      }
    }

    /**
     * Convert a map resulting from parsing generic JSON (or any other source) to the passed type.
     *
     * @param payload
     * @param cls
     * @return the converted object
     */
    public static <T> T convertMap(Map<?, ?> payload, Class<T> cls) {
      return defaultMapper.convertValue(payload, cls);
    }
  }

  public static class Collections extends org.apache.commons.collections.CollectionUtils {

    /**
     * TODO enable injection of sender and standard arguments such as scope, controller and service
     *
     * @param required
     * @param payload
     * @return
     */
    public static Object[] matchArguments(Class<?>[] required, Object[] payload) {

      if (required == null) {
        required = new Class<?>[] {};
      }

      // Must be at least same parameter number w.r.t. supplied
      if (required.length > 0 && (payload == null || payload.length < required.length)) {
        return null;
      }

      if (required.length == 0) {
        // do not return null
        return new Object[0];
      }

      // 1+ parameters, same number, check for exact match
      if (classesMatch(required, payload)) {
        return payload;
      }

      // no exact match, same number, reorder if possible
      ArrayList<Object> reordered = new ArrayList<>();
      for (var cls : required) {
        boolean found = false;
        for (var arg : payload) {
          if (arg == null || cls.isAssignableFrom(arg.getClass())) {
            reordered.add(arg);
            found = true;
            break;
          }
        }
        if (!found) {
          // TODO check for standard injected arguments.
        }
      }

      return reordered.size() == required.length ? reordered.toArray() : null;
    }

    private static boolean classesMatch(Class<?>[] parameterTypes, Object[] payload) {
      for (int i = 0; i < parameterTypes.length; i++) {
        if (payload[i] != null && !parameterTypes[i].isAssignableFrom(payload[i].getClass())) {
          return false;
        }
      }
      return true;
    }

    public static <T1, T2> List<T1> sortMatching(
        List<T1> toSort, List<T2> toMatch, Comparator<T2> comparator) {
      MatchedSorter<T1, T2> sorter = new MatchedSorter<>(toSort, toMatch, comparator);
      return sorter.getSortedValues();
    }

    public static <T1, T2> List<Pair<T1, T2>> sortMatchingPairs(
        List<T1> toSort, List<T2> toMatch, Comparator<T2> comparator) {
      MatchedSorter<T1, T2> sorter = new MatchedSorter<>(toSort, toMatch, comparator);
      List<Pair<T1, T2>> ret = new ArrayList<>();
      for (int i = 0; i < sorter.getSortedCriteria().size(); i++) {
        ret.add(Pair.of(sorter.getSortedValues().get(i), sorter.getSortedCriteria().get(i)));
      }
      return ret;
    }

    @SafeVarargs
    public static <T> List<T> join(Collection<T>... resources) {
      List<T> ret = new ArrayList<>();
      for (Collection<T> list : resources) {
        ret.addAll(list);
      }
      return ret;
    }

    @SafeVarargs
    public static <T> List<T> join(Class<T> returnClass, Collection<? extends T>... resources) {
      List<T> ret = new ArrayList<>();
      for (var list : resources) {
        for (var object : list) {
          ret.add((T) object);
        }
      }
      return ret;
    }

    @SafeVarargs
    public static <T> List<T> join(Iterable<T>... resources) {
      List<T> ret = new ArrayList<>();
      for (Iterable<T> list : resources) {
        for (T o : list) {
          ret.add(o);
        }
      }
      return ret;
    }

    /**
     * Pack the arguments into a collection; if any argument is a collection, add its elements but
     * do not unpack below the first level.
     *
     * @param objects
     * @return
     */
    @SuppressWarnings("unchecked")
    public static <T> Collection<T> shallowCollection(Object... objects) {
      List<T> ret = new ArrayList<>();
      for (Object o : objects) {
        if (o instanceof Collection) {
          ret.addAll((Collection<T>) o);
        } else {
          ret.add((T) o);
        }
      }
      return ret;
    }

    /**
     * Flatten the arguments into a single collection; if any argument is a collection or an array,
     * unpack its elements recursively so that no collections remain.
     *
     * @param objects
     * @return
     */
    @SuppressWarnings("unchecked")
    public static <T> Collection<T> flatCollection(T... objects) {
      List<T> ret = new ArrayList<>();
      addToCollection(ret, objects);
      return ret;
    }

    public static <T> T chooseOne(Collection<T> collection) {
      var idx = ThreadLocalRandom.current().nextInt(0, collection.size());
      return collection.stream().skip(idx).findFirst().orElse(null);
    }

    public static <T> List<T> flatList(List<List<T>> lists) {
      List<T> ret = new ArrayList<>();
      for (List<T> list : lists) {
        ret.addAll(list);
      }
      return ret;
    }

    @SuppressWarnings("unchecked")
    private static <T> void addToCollection(List<T> ret, T... objects) {
      for (T o : objects) {
        if (o instanceof Collection) {
          for (T oo : ((Collection<T>) o)) {
            addToCollection(ret, oo);
          }
        } else if (o != null && o.getClass().isArray()) {
          for (int i = 0; i < Array.getLength(o); i++) {
            addToCollection(ret, (T) Array.get(o, i));
          }
        } else {
          ret.add(o);
        }
      }
    }

    @SuppressWarnings("unchecked")
    public static <T> Set<T> asSet(Collection<T> items) {
      if (items instanceof Set) {
        return (Set<T>) items;
      }

      if (items instanceof Enum) {
        return EnumSet.copyOf((Collection) items);
      }

      return new HashSet<>(items);
    }

    /**
     * Ensure that the passed collection is an ordered list, and if not make a list out of it. Used
     * mostly to promote ordered values() from a linked hash map and similar situations.
     *
     * @param values
     * @param <T>
     * @return an ordered list with the same values as the incoming collection, in order of
     *     iteration
     */
    public static <T> List<T> promoteToList(Collection<T> values) {
      if (values instanceof List) {
        return (List<T>) values;
      }
      return new ArrayList<>(values);
    }
  }

  public static class Strings extends org.integratedmodelling.klab.api.utils.Utils.Strings {}

  public static class YAML {

    static ObjectMapper defaultMapper;

    static {
      defaultMapper = new ObjectMapper(new YAMLFactory());
    }

    /**
     * Load an object from an input stream.
     *
     * @param url the input stream
     * @param cls the class
     * @return the object
     * @throws KlabIOException
     */
    public static <T> T load(InputStream url, Class<T> cls) throws KlabIOException {
      try {
        return defaultMapper.readValue(url, cls);
      } catch (Exception e) {
        throw new KlabIOException(e);
      }
    }

    /**
     * Load an object from a file.
     *
     * @param file
     * @param cls
     * @return the object
     * @throws KlabIOException
     */
    public static <T> T load(File file, Class<T> cls) throws KlabIOException {
      try {
        return defaultMapper.readValue(file, cls);
      } catch (Exception e) {
        throw new KlabIOException(e);
      }
    }

    /**
     * Load an object from a URL.
     *
     * @param url
     * @param cls
     * @return the object
     * @throws KlabIOException
     */
    public static <T> T load(URL url, Class<T> cls) throws KlabIOException {
      try {
        return defaultMapper.readValue(url, cls);
      } catch (Exception e) {
        throw new KlabIOException(e);
      }
    }

    /**
     * Serialize an object to a file.
     *
     * @param object
     * @param outFile
     * @throws KlabIOException
     */
    public static void save(Object object, File outFile) throws KlabIOException {
      try {
        defaultMapper.writeValue(outFile, object);
      } catch (Exception e) {
        throw new KlabIOException(e);
      }
    }

    public static String asString(Object object) {
      try {
        return defaultMapper.writeValueAsString(object);
      } catch (JsonProcessingException e) {
        throw new IllegalArgumentException("serialization failed: " + e.getMessage());
      }
    }
  }

  public static class Markdown {}

  public static class Names extends org.integratedmodelling.klab.api.utils.Utils.Names {

    public static String fastName() {
      return Generators.timeBasedEpochGenerator().generate().toString();
    }
  }

  public static class Maps {

    /**
     * Return the object at the given path as the passed class. Each slash-separated path component,
     * except the last, must point to another map.
     *
     * @param <T>
     * @param path
     * @param cls
     * @return
     */
    public static <T> T get(Map<?, ?> map, String path, Class<T> cls) {
      String[] paths = path.split("/");
      Map<?, ?> o = map;
      for (int i = 0; i < paths.length - 1; i++) {
        Object to = o.get(paths[i]);
        if (!(to instanceof Map)) {
          return null;
        }
        o = (Map<?, ?>) to;
      }
      return o == null ? null : Data.asType(o.get(paths[paths.length - 1]), cls);
    }

    /**
     * Pretty-print a map using the Apache functions.
     *
     * @param map
     * @return
     */
    public static String debugPrint(Map<?, ?> map) {
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      PrintStream ps = new PrintStream(baos);
      MapUtils.debugPrint(ps, "", map);
      ps.flush();
      return baos.toString();
    }

    /**
     * @param originalMap
     * @param translationTable
     * @param <K>
     * @param <V>
     * @return
     */
    public static <K, V> Map<K, V> translateKeys(
        Map<K, V> originalMap, Map<K, K> translationTable) {
      Map<K, V> ret = new HashMap<>();
      for (K key : originalMap.keySet()) {
        ret.put(
            translationTable.containsKey(key) ? translationTable.get(key) : key,
            originalMap.get(key));
      }
      return ret;
    }

    //    /**
    //     * Create a key->value map by pairing keys with the object that follows them.
    //     *
    //     * @param parameters
    //     * @return a key-value map
    //     * @throws KlabIllegalArgumentException if keys aren't matched
    //     */
    //    public static Map<String, Object> makeKeyMap(Object[] parameters) {
    //      var ret = new LinkedHashMap<String, Object>();
    //      if (parameters != null) {
    //        for (int i = 0; i < parameters.length; i++) {
    //          if (i == parameters.length - 1) {
    //            throw new KlabIllegalArgumentException(
    //                "Utils.Maps.makeKeyMap: unmatched keys " + "in " + "argument list");
    //          }
    //          ret.put(parameters[i].toString(), parameters[++i]);
    //        }
    //      }
    //      return ret;
    //    }

    /**
     * Create a key->value map by pairing keys with the object that follows them.
     *
     * @param parameters
     * @return a key-value map
     * @throws KlabIllegalArgumentException if keys aren't matched
     */
    public static Map<String, String> makeStringMap(Object[] parameters) {
      var ret = new LinkedHashMap<String, String>();
      if (parameters != null) {
        for (int i = 0; i < parameters.length; i++) {
          if (i == parameters.length - 1) {
            throw new KlabIllegalArgumentException(
                "Utils.Maps.makeKeyMap: unmatched keys " + "in " + "argument list");
          }
          ret.put(parameters[i].toString(), parameters[++i].toString());
        }
      }
      return ret;
    }

    public static <K, V> Map<K, V> removeNullValues(Map<K, V> map) {
      Set<K> toRemove = new HashSet<>();
      for (K key : map.keySet()) {
        if (map.get(key) == null) {
          toRemove.add(key);
        }
      }
      for (var k : toRemove) {
        map.remove(k);
      }
      return map;
    }

    /**
     * Throw an exception if the map does not contain all the passed keys.
     *
     * @param map
     * @param keys
     * @throws KlabIllegalStateException if not compliant
     */
    public static void ensureContains(Map map, Object... keys) {
      if (map == null) {
        throw new KlabIllegalArgumentException("Utils.Maps.ensureContains: map cannot be null");
      }
      for (Object key : keys) {
        if (!map.containsKey(key)) {
          throw new KlabIllegalStateException(
              "Utils.Maps.ensureContains: missing key " + key + " in " + "map");
        }
      }
    }
  }

  public static class Templates extends org.integratedmodelling.klab.api.utils.Utils.Templates {

    static Map<String, TemplateEngine> plainEngines = new ConcurrentHashMap<>();
    static Map<String, TemplateEngine> htmlEngines = new ConcurrentHashMap<>();
    static File templatesDir = Files.createNewTemporaryDirectory();
    static File htmlTemplatesDir = Files.createNewTemporaryDirectory();

    /**
     * Render a JTE template from a URL with a plain text engine. The engine will precompile all
     * templates in the same classpath and persist across calls for efficiency.
     *
     * @param data
     * @return
     */
    public static String renderJTEPlain(URL jteTemplate, Map<String, Object> data) {
      var output = new StringOutput();
      var path = URLs.getURLBaseName(jteTemplate);
      var file = URLs.copy(jteTemplate, new File(templatesDir + "/" + path));
      plainEngines
          .computeIfAbsent(
              path,
              pth ->
                  TemplateEngine.create(
                      new DirectoryCodeResolver(templatesDir.toPath()), ContentType.Plain))
          .render(path, data, output);
      return output.toString();
    }

    public static String renderJTEHtml(URL jteTemplate, Map<String, Object> data) {
      var output = new StringOutput();
      var path = URLs.getURLBaseName(jteTemplate);
      var file = URLs.copy(jteTemplate, new File(htmlTemplatesDir + "/" + path));
      htmlEngines
          .computeIfAbsent(
              path,
              pth ->
                  TemplateEngine.create(
                      new DirectoryCodeResolver(htmlTemplatesDir.toPath()), ContentType.Html))
          .render(path, data, output);
      return output.toString();
    }

    /**
     * Return all the substituted templates after substituting the passed variables. The
     * substitution for each variable can be null, a single POD, a {@link NumericRangeImpl} or a
     * collection of objects.
     *
     * @param template
     * @param vars
     * @return
     */
    public static List<String> expandMatches(String template, Map<String, Object> vars) {

      /*
       * extract the variables
       */
      List<String> vs = getTemplateVariables(template);

      if (vs.isEmpty()) {
        return java.util.Collections.singletonList(template);
      }

      /*
       * set the vars not in the map to null and substitute any single value in it with
       * singleton lists
       */
      List<String> variables = new ArrayList<>();
      List<Set<Object>> sets = new ArrayList<>();

      for (String var : vs) {
        if (vars.containsKey(var)) {
          variables.add(var);
          sets.add(expandSet(vars.get(var)));
        }
      }

      if (variables.isEmpty()) {
        return java.util.Collections.singletonList(template);
      }

      List<String> ret = new ArrayList<>();

      /*
       * take the cartesian product of each variable that is represented in the vars and
       * substitute one by one
       */
      for (List<Object> permutation : Sets.cartesianProduct(sets)) {
        String tret = template;
        int i = 0;
        for (String var : variables) {
          Object o = permutation.get(i++);
          tret = tret.replaceAll("\\{" + var + "\\}", o.toString());
        }
        ret.add(tret);
      }

      return ret;
    }

    /**
     * Like {@link #expandMatches(String, Map)} but also returns the specific matches for each
     * expanded template as a map.
     *
     * @param template
     * @param vars
     * @return
     */
    public static List<Pair<String, Map<String, Object>>> getExpansion(
        String template, Map<String, Object> vars) {

      /*
       * extract the variables
       */
      List<String> vs = getTemplateVariables(template);

      if (vs.size() == 0) {
        return java.util.Collections.singletonList(Pair.of(template, new HashMap<>()));
      }

      /*
       * set the vars not in the map to null and substitute any single value in it with
       * singleton lists
       */
      List<String> variables = new ArrayList<>();
      List<Set<Object>> sets = new ArrayList<>();

      for (String var : vs) {
        if (vars.containsKey(var)) {
          variables.add(var);
          sets.add(expandSet(vars.get(var)));
        }
      }

      if (variables.isEmpty()) {
        return java.util.Collections.singletonList(Pair.of(template, new HashMap<>()));
      }

      List<Pair<String, Map<String, Object>>> ret = new ArrayList<>();

      /*
       * take the cartesian product of each variable that is represented in the vars and
       * substitute one by one
       */
      for (List<Object> permutation : Sets.cartesianProduct(sets)) {
        Map<String, Object> map = new HashMap<>();
        String tret = template;
        int i = 0;
        for (String var : variables) {
          Object o = permutation.get(i++);
          tret = tret.replaceAll("\\{" + var + "\\}", o.toString());
          map.put(var, o);
        }
        ret.add(Pair.of(tret, map));
      }

      return ret;
    }

    private static Set<Object> expandSet(Object object) {

      Set<Object> ret = new LinkedHashSet<>();
      if (object == null) {
        ret.add("");
      } else if (object instanceof NumericRangeImpl) {
        int bottom = (int) ((NumericRangeImpl) object).getLowerBound();
        int upper = (int) ((NumericRangeImpl) object).getUpperBound();
        for (int n = bottom; n <= upper; n++) {
          ret.add(n);
        }
      } else if (object instanceof Collection) {
        for (Object o : ((Collection<?>) object)) {
          ret.add(o);
        }
      } else {

        String tt = object.toString();
        if (tt.contains(",")) {
          String[] ttt = tt.split(",");
          for (String tttt : ttt) {
            ret.add(tttt.trim());
          }
        } else {
          ret.add(tt);
        }
      }
      return ret;
    }

    public static void main(String[] argv) {

      String url =
          "https://disc2.gesdisc.eosdis.nasa.gov:443/opendap/TRMM_L3/TRMM_3B42_Daily"
              + ".7/{year}/{month}/3B42_Daily.{year}{month}{day}.7.nc4";

      Map<String, Object> vars = new HashMap<>();

      vars.put("year", NumericRangeImpl.create(1998, 2010));
      vars.put("month", NumericRangeImpl.create(2, 3));
      vars.put("day", "monday,tuesday,happy_days");

      for (String uuh : expandMatches(url, vars)) {
        System.out.println(uuh);
      }
    }
  }

  public static class Wildcards {

    /**
     * Check for matching of simple wildcard patterns using * and ? as per conventions.
     *
     * @param string the string
     * @param pattern the pattern
     * @return a boolean.
     */
    public static boolean matches(String string, String pattern) {
      return new WildcardMatcher().match(string, pattern);
    }
  }

  /**
   * jja Popular nouns modified by the given adjective, per Google Books Ngrams gradual → increase
   * jjb Popular adjectives used to modify the given noun, per Google Books Ngrams beach → sandy syn
   * Synonyms (words contained within the same WordNet synset) ocean → sea trg "Triggers" (words
   * that are statistically associated with the query word in the same piece of text.) cow → milking
   * ant Antonyms (per WordNet) late → early spc "Kind of" (direct hypernyms, per WordNet) gondola →
   * boat gen "More general than" (direct hyponyms, per WordNet) boat → gondola com "Comprises"
   * (direct holonyms, per WordNet) car → accelerator par "Part of" (direct meronyms, per WordNet)
   * trunk → tree bga Frequent followers (w′ such that P(w′|w) ≥ 0.001, per Google Books Ngrams)
   * wreak → havoc bgb Frequent predecessors (w′ such that P(w|w′) ≥ 0.001, per Google Books Ngrams)
   * havoc → wreak hom Homophones (sound-alike words) course → coarse cns Consonant match sample →
   * simple
   */
  public static class Words {

    public enum Relation {
      NounsModifiedByAdjective("jja"),
      AdjectivesUsedToModifyNoun("jjb"),
      Synonyms("syn"),
      Antonyms("ant"),
      Triggers("trg"),
      KindOf("spc"),
      PartOf("par"),
      MoreGeneralThan("gen"),
      Comprises("com"),
      FrequentFollowers("bga"),
      FrequentPredecessors("bgb"),
      Homophones("hom"),
      ConsonantMatch("cns");

      public String code;

      Relation(String code) {
        this.code = code;
      }
    }

    /**
     * Returns a list of similar words to the word/phrase supplied.
     *
     * @param word A word of phrase.
     * @return A list of similar words.
     */
    public static List<String> findSimilar(String word) {
      String s = word.replaceAll(" ", "+");
      return getJSON("http://api.datamuse.com/words?rd=" + s);
    }

    /**
     * Returns a list of similar words to the word/phrase supplied beginning with the specified
     * letter(s).
     *
     * @param word A word or phrase.
     * @param startLetter The letter(s) the similar words should begin with.
     * @return A list of similar words.
     */
    public static List<String> findSimilarStartsWith(String word, String startLetter) {
      String s = word.replaceAll(" ", "+");
      return getJSON("http://api.datamuse.com/words?rd=" + s + "&sp=" + startLetter + "*");
    }

    /**
     * Returns a list of similar words to the word/phrase supplied ending with the specified
     * letter(s).
     *
     * @param word A word or phrase.
     * @param endLetter The letter(s) the similar words should end with.
     * @return A list of similar words.
     */
    public static List<String> findSimilarEndsWith(String word, String endLetter) {
      String s = word.replaceAll(" ", "+");
      return getJSON("http://api.datamuse.com/words?rd=" + s + "&sp=*" + endLetter);
    }

    public static List<String> findRelated(String word, Relation relation) {
      return getJSON("http://api.datamuse.com/words?rel_" + relation.code + "=" + word);
    }

    /**
     * Returns a list of words beginning and ending with the specified letters and with the
     * specified number of letters in between.
     *
     * @param startLetter The letter(s) the similar words should start with.
     * @param endLetter The letter(s) the similar words should end with.
     * @param numberMissing The number of letters between the start and end letters
     * @return A list of matching words.
     */
    public static List<String> wordsStartingWithEndingWith(
        String startLetter, String endLetter, int numberMissing) {
      StringBuilder sb = new StringBuilder();
      for (int i = 0; i < numberMissing; i++) {
        sb.append("?");
      }
      return getJSON("http://api.datamuse.com/words?sp=" + startLetter + sb + endLetter);
    }

    /**
     * Returns a list of words beginning and ending with the specified letters and with an
     * unspecified number of letters in between.
     *
     * @param startLetter The letter(s) the similar words should start with.
     * @param endLetter The letter(s) the similar words should end with.
     * @return A list of matching words.
     */
    public static List<String> wordsStartingWithEndingWith(String startLetter, String endLetter) {
      return getJSON("http://api.datamuse.com/words?sp=" + startLetter + "*" + endLetter);
    }

    /**
     * Find words which sound the same as the specified word/phrase when spoken.
     *
     * @param word A word or phrase.
     * @return A list of words/phrases which sound similiar when spoken.
     */
    public static List<String> soundsSimilar(String word) {
      String s = word.replaceAll(" ", "+");
      return getJSON("http://api.datamuse.com/words?sl=" + s);
    }

    /**
     * Find words which are spelt the same as the specified word/phrase.
     *
     * @param word A word or phrase.
     * @return A list of words/phrases which are spelt similar.
     */
    public static List<String> speltSimilar(String word) {
      String s = word.replaceAll(" ", "+");
      return getJSON("http://api.datamuse.com/words?sp=" + s);
    }

    /**
     * Returns suggestions for what the user may be typing based on what they have typed so far.
     * Useful for autocomplete on forms.
     *
     * @param word The current word or phrase.
     * @return Suggestions of what the user may be typing.
     */
    public static List<String> prefixHintSuggestions(String word) {
      String s = word.replaceAll(" ", "+");
      return getJSON("http://api.datamuse.com/sug?s=" + s);
    }

    public static void main(String[] dio) {
      for (int i = 0; i < 30; i++) {
        System.out.println(
            Strings.capitalize(
                makeUpName("elephant", "intelligence", "planet", "sausage", "cucumber")));
      }
    }

    public static String makeUpName(String... names) {
      var list = Collections.flatCollection(names);
      var orig = Collections.chooseOne(list);
      var related = findRelated(orig, Relation.KindOf);
      if (related == null) {
        // API didn't work. Just put some gibberish after a chosen name.
        return orig + " " + Collections.chooseOne(list);
      }
      if (related.isEmpty()) {
        related = findRelated(orig, Relation.MoreGeneralThan);
      }
      var main = related.isEmpty() ? orig : Collections.chooseOne(related);
      var adje = findRelated(main, Relation.AdjectivesUsedToModifyNoun);
      return ((adje == null || adje.isEmpty()) ? "" : (Collections.chooseOne(adje) + " ")) + main;
    }

    /**
     * Query a URL for their source code.
     *
     * @param url The page's URL.
     * @return The source code.
     */
    private static List<String> getJSON(String url) {
      URL datamuse;
      URLConnection dc;
      StringBuilder s = null;
      try {
        datamuse = new URL(url);
        dc = datamuse.openConnection();
        BufferedReader in = new BufferedReader(new InputStreamReader(dc.getInputStream(), "UTF-8"));
        String inputLine;
        s = new StringBuilder();
        while ((inputLine = in.readLine()) != null) s.append(inputLine);
        in.close();
      } catch (Throwable e) {
        s = null;
      }
      if (s == null || s.isEmpty()) return null;
      List<String> ret = new ArrayList<>();
      for (var o : Json.parseObject(s.toString(), List.class)) {
        if (o instanceof Map<?, ?> map && ((Map<?, ?>) o).containsKey("word")) {
          ret.add(map.get("word").toString());
        }
      }
      return ret;
    }
  }

  public static class Network {

    /**
     * Checks if is server alive.
     *
     * @param host the host
     * @return a boolean.
     */
    public static boolean isAlive(String host) {

      try {
        if (InetAddress.getByName(host).isReachable(200)) {
          return true;
        }
      } catch (Exception ignored) {
      }
      return false;
    }

    public static boolean isAlive(URL host) {
      return isAlive(host.toString());
    }

    /**
     * Port available.
     *
     * @param port the port
     * @return a boolean.
     */
    public static boolean portAvailable(int port) {

      ServerSocket ss = null;
      DatagramSocket ds = null;
      try {
        ss = new ServerSocket(port);
        ss.setReuseAddress(true);
        ds = new DatagramSocket(port);
        ds.setReuseAddress(true);
        return true;
      } catch (Exception e) {
        e.getMessage();
      } finally {
        if (ds != null) {
          ds.close();
        }

        if (ss != null) {
          try {
            ss.close();
          } catch (IOException e) {
            /* should not be thrown */
          }
        }
      }

      return false;
    }

    /**
     * Call with "-" as a parameter to get the typical MAC address string. Otherwise use another
     * string to get a unique machine identifier that can be customized.
     *
     * @param sep the sep
     * @return MAC address
     */
    public static String getMACAddress(String sep) {

      InetAddress ip;
      String ret = null;
      try {

        ip = InetAddress.getLocalHost();
        NetworkInterface network = NetworkInterface.getByInetAddress(ip);
        byte[] mac = network.getHardwareAddress();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < mac.length; i++) {
          sb.append(String.format("%02X%s", mac[i], (i < mac.length - 1) ? sep : ""));
        }
        ret = sb.toString();

      } catch (Exception e) {
        throw new KlabIOException(e);
      }

      return ret;
    }
  }

  /**
   * Sorts an array according to the sort order of a matched other using a given comparator. Makes
   * up for the lovely matched sort available in C# and missing in Java collections.
   *
   * @param <T1> the generic type
   * @param <T2> the generic type
   * @author Ferd
   * @version $Id: $Id
   */
  private static class MatchedSorter<T1, T2> {

    List<T1> _a;
    List<T2> _criteria;
    Comparator<T2> _comparator;

    /**
     * Instantiates a new matched sorter.
     *
     * @param a the a
     * @param criteria the criteria
     * @param comparator the comparator
     */
    public MatchedSorter(List<T1> a, List<T2> criteria, Comparator<T2> comparator) {
      _a = a;
      _criteria = criteria;
      _comparator = comparator;
      if (a.size() > 0) {
        quicksort(0, a.size() - 1);
      }
    }

    /**
     * Gets the sorted values.
     *
     * @return the sorted values
     */
    public List<T1> getSortedValues() {
      return _a;
    }

    /**
     * Gets the sorted criteria.
     *
     * @return the sorted criteria
     */
    public List<T2> getSortedCriteria() {
      return _criteria;
    }

    private void swap(int lft, int rt) {
      T1 temp;
      temp = _a.get(lft);
      _a.set(lft, _a.get(rt));
      _a.set(rt, temp);

      T2 otemp = _criteria.get(lft);
      _criteria.set(lft, _criteria.get(rt));
      _criteria.set(rt, otemp);
    }

    private void quicksort(int low, int high) {

      int i = low, j = high;

      // Get the pivot element from the middle of the list
      T2 pivot = _criteria.get(low + (high - low) / 2);

      // Divide into two lists
      while (i <= j) {
        // If the current value from the left list is smaller then the pivot
        // element then get the next element from the left list
        while (_comparator.compare(_criteria.get(i), pivot) < 0) {
          i++;
        }
        // If the current value from the right list is larger then the pivot
        // element then get the next element from the right list
        while (_comparator.compare(_criteria.get(j), pivot) > 0) {
          j--;
        }

        // If we have found a values in the left list which is larger then
        // the pivot element and if we have found a value in the right list
        // which is smaller then the pivot element then we exchange the
        // values.
        // As we are done we can increase i and j
        if (i <= j) {
          swap(i, j);
          i++;
          j--;
        }
      }

      // Recursion
      if (low < j) quicksort(low, j);
      if (i < high) quicksort(i, high);
    }
  }

  /**
   * This class is a utility for finding the String based upon the wild card pattern. For example if
   * the actual String "John" and your wild card pattern is "J*", it will return true.
   *
   * @author Debadatta Mishra(PIKU)
   */
  private static class WildcardMatcher {
    /** String variable for wild card pattern */
    private String wildCardPatternString;

    /** Variable for the length of the wild card pattern */
    private int wildCardPatternLength;

    /** Boolean variable to for checking wild cards, It is false by default. */
    private boolean ignoreWildCards;

    /** Boolean variable to know whether the pattern has leading * or not. */
    private boolean hasLeadingStar;

    /** Boolean variable to know whether the pattern has * at the end. */
    private boolean hasTrailingStar;

    /** A String array to contain chars */
    private String charSegments[];

    /** Variable to maintain the boundary of the String. */
    private int charBound;

    /** Default constructor. */
    public WildcardMatcher() {
      ignoreWildCards = false;
    }

    /**
     * This is the public method which will be called to match a String with the wild card pattern.
     *
     * @param actualString of type String indicating the String to be matched
     * @param wildCardString of type String indicating the wild card String
     * @return true if matches
     */
    public boolean match(String actualString, String wildCardString) {
      wildCardPatternString = wildCardString;
      wildCardPatternLength = wildCardString.length();
      setWildCards();
      return doesMatch(actualString, 0, actualString.length());
    }

    /**
     * This method is used to set the wild cards. The pattern for the wild card may be *, ? or a
     * combination of *,? and alphanumeric character.
     */
    private void setWildCards() {
      if (wildCardPatternString.startsWith("*")) {
        hasLeadingStar = true;
      }
      if (wildCardPatternString.endsWith("*") && wildCardPatternLength > 1) {
        hasTrailingStar = true;
      }
      Vector temp = new Vector();
      int pos = 0;
      StringBuffer buf = new StringBuffer();
      while (pos < wildCardPatternLength) {
        char c = wildCardPatternString.charAt(pos++);
        switch (c) {
          case 42: // It refers to *
            if (buf.length() > 0) {
              temp.addElement(buf.toString());
              charBound += buf.length();
              buf.setLength(0);
            }
            break;
          case 63: // It refers to ?
            buf.append('\0');
            break;

          default:
            buf.append(c);
            break;
        }
      }
      if (buf.length() > 0) {
        temp.addElement(buf.toString());
        charBound += buf.length();
      }
      charSegments = new String[temp.size()];
      temp.copyInto(charSegments);
    }

    /**
     * This is the actual method which makes comparison with the wild card pattern.
     *
     * @param text of type String indicating the actual String
     * @param startPoint of type int indicating the start index of the String
     * @param endPoint of type int indicating the end index of the String
     * @return true if matches.
     */
    private boolean doesMatch(String text, int startPoint, int endPoint) {
      int textLength = text.length();

      if (startPoint > endPoint) {
        return false;
      }
      if (ignoreWildCards) {
        return endPoint - startPoint == wildCardPatternLength
            && wildCardPatternString.regionMatches(
                false, 0, text, startPoint, wildCardPatternLength);
      }
      int charCount = charSegments.length;
      if (charCount == 0 && (hasLeadingStar || hasTrailingStar)) {
        return true;
      }
      if (startPoint == endPoint) {
        return wildCardPatternLength == 0;
      }
      if (wildCardPatternLength == 0) {
        return startPoint == endPoint;
      }
      if (startPoint < 0) {
        startPoint = 0;
      }
      if (endPoint > textLength) {
        endPoint = textLength;
      }
      int currPosition = startPoint;
      int bound = endPoint - charBound;
      if (bound < 0) {
        return false;
      }
      int i = 0;
      String currString = charSegments[i];
      int currStringLength = currString.length();
      if (!hasLeadingStar) {
        if (!isExpressionMatching(text, startPoint, currString, 0, currStringLength)) {
          return false;
        }
        i++;
        currPosition += currStringLength;
      }
      if (charSegments.length == 1 && !hasLeadingStar && !hasTrailingStar) {
        return currPosition == endPoint;
      }
      for (; i < charCount; i++) {
        currString = charSegments[i];
        int k = currString.indexOf('\0');
        int currentMatch;
        currentMatch = getTextPosition(text, currPosition, endPoint, currString);
        if (k < 0) {
          if (currentMatch < 0) {
            return false;
          }
        }
        currPosition = currentMatch + currString.length();
      }
      if (!hasTrailingStar && currPosition != endPoint) {
        int clen = currString.length();
        return isExpressionMatching(text, endPoint - clen, currString, 0, clen);
      }
      return i == charCount;
    }

    /**
     * This method finds the position of the String based upon the wild card pattern. It also
     * considers some special case like *.* and ???.? and their combination.
     *
     * @param textString of type String indicating the String
     * @param start of type int indicating the start index of the String
     * @param end of type int indicating the end index of the String
     * @param posString of type indicating the position after wild card
     * @return the position of the String
     */
    private int getTextPosition(String textString, int start, int end, String posString) {
      /*
       * String after *
       */
      int plen = posString.length();
      int max = end - plen;
      int position = -1;
      int i = textString.indexOf(posString, start);
      /*
       * The following conditions are met for the special case where user give *.*
       */
      if (posString.equals(".")) {
        position = 1;
      }
      if (i == -1 || i > max) {
        position = -1;
      } else {
        position = i;
      }
      return position;
    }

    /**
     * This method is used to match the wild card with the String based upon the start and end
     * index.
     *
     * @param textString of type String indicating the String
     * @param stringStartIndex of type int indicating the start index of the String.
     * @param patternString of type String indicating the pattern
     * @param patternStartIndex of type int indicating the start index
     * @param length of type int indicating the length of pattern
     * @return true if matches otherwise false
     */
    private boolean isExpressionMatching(
        String textString,
        int stringStartIndex,
        String patternString,
        int patternStartIndex,
        int length) {
      while (length-- > 0) {
        char textChar = textString.charAt(stringStartIndex++);
        char patternChar = patternString.charAt(patternStartIndex++);
        if ((ignoreWildCards || patternChar != 0)
            && patternChar != textChar
            && (textChar != patternChar && textChar != patternChar)) {
          return false;
        }
      }
      return true;
    }
  }

  /**
   * A catalog (simply a map String -> T) read from a JSON file and capable of resynchronizing
   * intelligently on request.
   *
   * <p>Any put/remove operations won't sync the contents to the backing file unless
   * setSynchronization(true) is called first. Otherwise synchronize the file manually by calling
   * write() when necessary.
   *
   * @param <T> the type of the resource in the catalog
   * @author ferdinando.villa
   * @version $Id: $Id
   */
  public static class FileCatalog<T> extends LinkedHashMap<String, T> {

    Class<? extends T> cls;
    File file;
    long timestamp;
    boolean error;
    boolean autosync = false;

    /**
     * Creates the.
     *
     * @param <T> the main type for the collection
     * @param url the URL containing the JSON data catalog
     * @param interfaceClass the type of the interface returned (or the implementation type itself)
     * @param implementationClass the class implementing the interface
     * @return a new URL-based catalog
     */
    public static <T> FileCatalog<T> create(
        URL url, Class<T> interfaceClass, Class<? extends T> implementationClass) {
      return new FileCatalog<T>(url, interfaceClass, implementationClass);
    }

    /**
     * Creates a new catalog from a URL.
     *
     * @param <T> the main type for the collection
     * @param url the URL containing the JSON data catalog
     * @param implementationClass the type of the object returned
     * @return a new URL-based catalog
     */
    public static <T> FileCatalog<T> create(URL url, Class<T> implementationClass) {
      return new FileCatalog<T>(url, implementationClass, implementationClass);
    }

    /**
     * Creates the.
     *
     * @param <T> the interface type
     * @param file the containing the JSON data catalog
     * @param interfaceClass the type of the interface returned (or the implementation type itself)
     * @param implementationClass the class implementing the interface
     * @return a new file-based catalog
     */
    public static <T> FileCatalog<T> create(
        File file, Class<T> interfaceClass, Class<? extends T> implementationClass) {
      return new FileCatalog<T>(file, interfaceClass, implementationClass);
    }

    public FileCatalog<T> setSynchronization(boolean b) {
      this.autosync = b;
      return this;
    }

    private FileCatalog(URL url, Class<T> type, Class<? extends T> cls) {
      this.cls = cls;
      try (InputStream input = url.openStream()) {
        this.error = !synchronize(input);
      } catch (Throwable e) {
        throw new RuntimeException(e);
      }
    }

    /**
     * Instantiates a new file catalog.
     *
     * @param file the file
     * @param type the type
     * @param cls the cls
     */
    public FileCatalog(File file, Class<? extends T> type, Class<? extends T> cls) {

      if (!file.exists()) {
        try {
          file.createNewFile();
        } catch (IOException e) {
          throw new KlabIOException(e);
        }
      }

      this.file = file;
      this.cls = cls;
      try (InputStream input = new FileInputStream(file)) {
        this.error = !synchronize(input);
      } catch (Throwable e) {
        throw new RuntimeException(e);
      }
    }

    @Override
    public T put(String key, T value) {
      T ret = super.put(key, value);
      if (autosync) {
        write();
      }
      return ret;
    }

    @Override
    public void putAll(Map<? extends String, ? extends T> m) {
      super.putAll(m);
      if (autosync) {
        write();
      }
    }

    @Override
    public T putIfAbsent(String key, T value) {
      T ret = super.putIfAbsent(key, value);
      if (autosync && ret == null) {
        write();
      }
      return ret;
    }

    @Override
    public boolean remove(Object key, Object value) {
      boolean ret = super.remove(key, value);
      if (autosync && ret) {
        write();
      }
      return ret;
    }

    @Override
    public T remove(Object key) {
      T ret = super.remove(key);
      if (autosync && ret != null) {
        write();
      }
      return ret;
    }

    @Override
    public boolean replace(String key, T oldValue, T newValue) {
      boolean ret = super.replace(key, oldValue, newValue);
      if (autosync && ret) {
        write();
      }
      return ret;
    }

    @Override
    public T replace(String key, T value) {
      T ret = super.replace(key, value);
      if (autosync && ret != null) {
        write();
      }
      return ret;
    }

    /**
     * Checks for errors.
     *
     * @return a boolean.
     */
    public boolean hasErrors() {
      return this.error;
    }

    /**
     * Synchronize, reading the file if necessary.
     *
     * @param stream the stream
     * @return true if no errors. Non-existing file is not an error.
     * @throws java.lang.ClassCastException if the data read are not of the type configured
     */
    public boolean synchronize(InputStream stream) {

      boolean ret = true;

      if (this.file == null || (this.file.exists() && this.timestamp < this.file.lastModified())) {

        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        objectMapper
            .enable(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
            .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
        objectMapper.registerModule(new JavaTimeModule());

        try {
          JavaType type =
              objectMapper.getTypeFactory().constructMapLikeType(Map.class, String.class, cls);
          Map<Object, T> data = objectMapper.reader(type).readValue(stream);
          clear();
          for (Object key : data.keySet()) {
            put(key.toString(), (T) data.get(key));
          }
          this.timestamp =
              this.file == null ? System.currentTimeMillis() : this.file.lastModified();
        } catch (IOException e) {
          ret = false;
        }
      }

      return ret;
    }

    /**
     * Write the map to the backing file. Call after making changes to the underlying map.
     *
     * @throws IllegalStateException if the catalog was read from a URL.
     */
    public synchronized void write() {
      if (this.file != null && this.file.exists()) {

        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.enable(SerializationFeature.INDENT_OUTPUT); // pretty print
        objectMapper.enable(SerializationFeature.WRITE_NULL_MAP_VALUES); // pretty print
        objectMapper.enable(SerializationFeature.WRITE_SINGLE_ELEM_ARRAYS_UNWRAPPED); // pretty
        // print
        objectMapper
            .enable(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
            .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        objectMapper.registerModule(new JavaTimeModule());

        Map<String, T> data = new HashMap<>();
        data.putAll(this);
        try {
          objectMapper.writer().writeValue(this.file, data);
        } catch (IOException e) {
          throw new KlabIOException(e);
        }
        this.timestamp = this.file.lastModified();
      }
    }

    public File getFile() {
      return file;
    }
  }

  //  /**
  //   * Add an option to avoid problem if Lists are serialized as empty arryas
  //   * @param body the body
  //   * @param resultClass the resutl class
  //   * @return
  //   * @param <T> Parsed object
  //   * @throws IOException
  //   */
  //  public static <T> T parseWithJackson(String body, Class<T> resultClass) throws IOException {
  //    ObjectMapper mapper = new ObjectMapper();
  //    mapper.configure(DeserializationFeature.ACCEPT_EMPTY_ARRAY_AS_NULL_OBJECT, true);
  //    return mapper.readValue(body, resultClass);
  //  }
}
