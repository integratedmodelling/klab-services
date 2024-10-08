package org.integratedmodelling.klab.utilities;

import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.*;
import org.eclipse.jgit.api.errors.CheckoutConflictException;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.RemoteConfig;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.integratedmodelling.common.authentication.Authentication;
import org.integratedmodelling.common.logging.Logging;
import org.integratedmodelling.klab.api.authentication.ExternalAuthenticationCredentials;
import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.exceptions.KlabIOException;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.lang.Annotation;
import org.integratedmodelling.klab.api.lang.ServiceCall;
import org.integratedmodelling.klab.api.lang.Statement;
import org.integratedmodelling.klab.api.lang.kim.KimConcept;
import org.integratedmodelling.klab.api.lang.kim.KimObservable;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.services.runtime.Notification;
import org.integratedmodelling.klab.api.view.UI;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;

public class Utils extends org.integratedmodelling.common.utils.Utils {

//    public static class Graphs {
//
//        public enum Layout {
//            HIERARCHICAL, RADIALTREE, SIMPLE, SPRING
//        }
//
//        public static <E> void show(org.integratedmodelling.contrib.jgrapht.Graph<?, E> graph, String title,
//                                    Class<? extends E> edgeClass) {
//            show(adaptContribGraph(graph, edgeClass), title, Layout.SPRING);
//        }
//
//        public static <E> void show(org.integratedmodelling.contrib.jgrapht.Graph<?, E> graph, String title, Layout layout,
//                                    Class<? extends E> edgeClass) {
//            show(adaptContribGraph(graph, edgeClass), title, layout);
//        }
//
//        public static void show(Graph<?, ?> graph, String title) {
//            show(graph, title, Layout.SPRING);
//        }
//
//        public static void show(Graph<?, ?> graph, String title, Layout layout) {
//
//            SwingUtilities.invokeLater(new Runnable() {
//
//                @Override
//                public void run() {
//                    @SuppressWarnings("unchecked")
//                    GraphPanel panel = new GraphPanel(title, (Graph<Object, Object>) graph, layout);
//                    panel.showGraph();
//                }
//
//            });
//        }
//
//        @SuppressWarnings("unchecked")
//        private static <E> Graph<?, ?> adaptContribGraph(org.integratedmodelling.contrib.jgrapht.Graph<?, E> graph,
//                                                         Class<? extends E> edgeClass) {
//
//            DefaultDirectedGraph<Object, E> ret = new DefaultDirectedGraph<Object, E>(edgeClass);
//            for (Object o : graph.vertexSet()) {
//                ret.addVertex(o);
//            }
//            for (Object e : graph.edgeSet()) {
//                ret.addEdge(graph.getEdgeSource((E) e), graph.getEdgeTarget((E) e), (E) e);
//            }
//            return ret;
//        }
//
//        /**
//         * Show the dependency graph in the loader.
//         */
//        public static void showDependencies() {
//            show(((KimLoader) Resources.INSTANCE.getLoader()).getDependencyGraph(), "Dependencies", DefaultEdge.class);
//        }
//
//        /**
//         * Return whether precursor has a directed edge to dependent in graph.
//         *
//         * @param <V>
//         * @param <E>
//         * @param dependent
//         * @param precursor
//         * @param graph
//         * @return true if dependency exists
//         */
//        public static <V, E> boolean dependsOn(V dependent, V precursor, Graph<V, E> graph) {
//
//            for (E o : graph.incomingEdgesOf(dependent)) {
//                if (graph.getEdgeSource(o).equals(precursor)) {
//                    return true;
//                }
//            }
//            return false;
//        }
//
//        /**
//         * Shallow copy of graph into another.
//         *
//         * @param <V>
//         * @param <E>
//         * @param graph
//         * @param newGraph
//         * @return same graph passed as receiver
//         */
//        public static <V, E> Graph<V, E> copy(Graph<V, E> graph, Graph<V, E> newGraph) {
//            for (V vertex : graph.vertexSet()) {
//                newGraph.addVertex(vertex);
//            }
//            for (E edge : graph.edgeSet()) {
//                newGraph.addEdge(graph.getEdgeSource(edge), graph.getEdgeTarget(edge), edge);
//            }
//            return newGraph;
//        }
//
//    }
//
//    class GraphPanel extends JFrame {
//
//        /**
//         *
//         */
//        private static final long serialVersionUID = -2707712944901661771L;
//
//        public GraphPanel(String title, Graph<Object, Object> sourceGraph, Layout layout) {
//
//            super(title);
//
//            mxGraph graph = new mxGraph();
//            Object parent = graph.getDefaultParent();
//            graph.getModel().beginUpdate();
//
//            try {
//
//                Map<Object, Object> vertices = new HashMap<>();
//                for (Object v : sourceGraph.vertexSet()) {
//                    vertices.put(v, graph.insertVertex(parent, null, v.toString(), 20, 20, v.toString().length() * 6, 30));
//                }
//                for (Object v : sourceGraph.edgeSet()) {
//                    graph.insertEdge(parent, null, v.toString(), vertices.get(sourceGraph.getEdgeSource(v)),
//                            vertices.get(sourceGraph.getEdgeTarget(v)));
//                }
//
//            } finally {
//                graph.getModel().endUpdate();
//            }
//
//            switch (layout) {
//                case HIERARCHICAL:
//                    break;
//                case RADIALTREE:
//                    break;
//                case SIMPLE:
//                    break;
//                case SPRING:
//                    new mxHierarchicalLayout(graph).execute(graph.getDefaultParent());
//                    break;
//                default:
//                    break;
//
//            }
//
//            mxGraphComponent graphComponent = new mxGraphComponent(graph);
//            getContentPane().add(graphComponent);
//        }
//
//        public void showGraph() {
//            setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
//            setSize(400, 320);
//            setVisible(true);
//        }
//
//    }

    public static class Annotations {

        public static boolean hasAnnotation(Observable observable, String s) {
            for (Annotation annotation : observable.getAnnotations()) {
                if (annotation.getName().equals(s)) {
                    return true;
                }
            }
            return false;
        }

        public static Annotation getAnnotation(Observable observable, String s) {
            for (Annotation annotation : observable.getAnnotations()) {
                if (annotation.getName().equals(s)) {
                    return annotation;
                }
            }
            return null;
        }

        public static boolean hasAnnotation(Statement object, String s) {
            for (Annotation annotation : object.getAnnotations()) {
                if (annotation.getName().equals(s)) {
                    return true;
                }
            }
            return false;
        }

        public static Annotation getAnnotation(Statement object, String s) {
            for (Annotation annotation : object.getAnnotations()) {
                if (annotation.getName().equals(s)) {
                    return annotation;
                }
            }
            return null;
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
         * Simple methods that are messy to keep writing explicitly
         *
         * @param annotations
         * @param id
         * @return
         */
        public static Annotation getAnnotation(List<Annotation> annotations, String id) {
            for (Annotation annotation : annotations) {
                if (id.equals(annotation.getName())) {
                    return annotation;
                }
            }
            return null;
        }

        public static Parameters<String> collectVariables(List<Annotation> annotations) {
            Parameters<String> ret = Parameters.create();
            for (Annotation annotation : annotations) {
                if ("var".equals(annotation.getName())) {
                    for (String key : annotation.getNamedKeys()) {
                        ret.put(key, annotation.get(key));
                    }
                }
            }
            return ret;
        }

        /**
         * Collect the annotations from an k.IM object and its semantic lineage, ensuring that downstream
         * annotations of the same name override those upstream. Any string parameter filters the annotations
         * collected.
         *
         * @param objects
         * @return all annotations from upstream
         */
        public static Collection<Annotation> collectAnnotations(Object... objects) {

            Map<String, Annotation> ret = new HashMap<>();
            for (Object object : objects) {
                if (object instanceof KlabAsset) {
                    collectAnnotations((Knowledge) object, ret);
                } else if (object instanceof Statement) {
                    collectAnnotations((Statement) object, ret);
                } else if (object instanceof Artifact) {
                    for (Annotation annotation : ((Artifact) object).getAnnotations()) {
                        if (!ret.containsKey(annotation.getName())) {
                            ret.put(annotation.getName(), annotation);
                        }
                    }
                }
            }
            return ret.values();
        }

        /**
         * Collect the annotations from anything semantic lineage, ensuring that downstream annotations of the
         * same name override those upstream.
         *
         * @param object
         * @return all annotations from upstream
         */
        public static Collection<Annotation> collectAnnotations(KlabAsset object) {
            Map<String, Annotation> ret = new HashMap<>();
            collectAnnotations(object, ret);
            return ret.values();
        }

        private static void collectAnnotations(KlabAsset object, Map<String, Annotation> collection) {

            //            for (Annotation annotation : object.getAnnotations()) {
            //                if (!collection.containsKey(annotation.getName())) {
            //                    Annotation a = new AnnotationImpl(annotation);
            //                    collection.put(a.getName(), a);
            //                }
            //            }

            if (object instanceof KimObservable) {

                // /*
                // * collect from roles, traits and main in this order
                // */
                // // for (IConcept role : Roles.INSTANCE.getRoles(((IObservable)
                // // object).getType())) {
                // // collectAnnotations(role, collection);
                // // }
                // for (IConcept trait : Traits.INSTANCE.getTraits(((IObservable)
                // object).getType())) {
                // // FIXME REMOVE ugly hack: landcover is a type, but it's used as an attribute in
                // // various places so the change
                // // is deep. This makes landcover colormaps end up in places they shouldn't be.
                // // TODO check - may not be relevant anymore now that landcover is correctly a
                // type of and not a trait.
                // if (!trait.getNamespace().equals("landcover")) {
                // collectAnnotations(trait, collection);
                // }
                // }
                //
                // collectAnnotations(((IObservable) object).getType(), collection);
            } else if (object instanceof KimConcept) {
                // IKimObject mobject = Resources.INSTANCE.getModelObject(object.toString());
                // if (mobject != null) {
                // collectAnnotations(mobject, collection);
                // }
                // if (((IConcept) object).is(Type.CLASS)) {
                // // collect annotations from what is classified
                // IConcept classified = Observables.INSTANCE.getDescribedType((IConcept) object);
                // if (classified != null) {
                // collectAnnotations(classified, collection);
                // }
                // }
                // for (IConcept parent : ((IConcept) object).getParents()) {
                // if (!CoreOntology.CORE_ONTOLOGY_NAME.equals(parent.getNamespace())) {
                // collectAnnotations(parent, collection);
                // }
                // }
            } else if (object instanceof Concept) {
                // TODO
            } else if (object instanceof Observable) {
                // TODO
            } else if (object instanceof Model) {
                collectAnnotations(((Model) object).getObservables().get(0), collection);
                //            } else if (object instanceof Instance) {
                //                collectAnnotations(((Instance) object).getObservable(), collection);
            }
            //
            // if (getParent(object) != null) {
            // collectAnnotations(object.getParent(), collection);
            // }

        }

        // private void collectAnnotations(Knowledge object, Map<String, IAnnotation> collection) {
        //
        // for (Annotation annotation : object.getAnnotations()) {
        // if (!collection.containsKey(annotation.getName())) {
        // collection.put(annotation.getName(), annotation);
        // }
        // }
        //
        // }
        //
        // private void collectAnnotations(ISemantic object, Map<String, IAnnotation> collection) {
        //
        // if (object instanceof IObservable) {
        //
        // for (IAnnotation annotation : ((IObservable)object).getAnnotations()) {
        // if (!collection.containsKey(annotation.getName())) {
        // collection.put(annotation.getName(), annotation);
        // }
        // }
        //
        // /*
        // * collect from roles, traits and main in this order
        // */
        // // for (IConcept role : Roles.INSTANCE.getRoles(((IObservable)
        // // object).getType())) {
        // // collectAnnotations(role, collection);
        // // }
        // for (IConcept trait : Traits.INSTANCE.getTraits(((IObservable) object).getType())) {
        // // FIXME REMOVE ugly hack: landcover is a type, but it's used as an attribute in
        // // various places so the change
        // // is deep. This makes landcover colormaps end up in places they shouldn't be.
        // // TODO check - may not be relevant anymore now that landcover is correctly a type of and
        // not a trait.
        // if (!trait.getNamespace().equals("landcover")) {
        // collectAnnotations(trait, collection);
        // }
        // }
        //
        // collectAnnotations(((IObservable) object).getType(), collection);
        //
        // } else if (object instanceof IConcept) {
        // IKimObject mobject = Resources.INSTANCE.getModelObject(object.toString());
        // if (mobject != null) {
        // collectAnnotations(mobject, collection);
        // }
        // if (((IConcept) object).is(Type.CLASS)) {
        // // collect annotations from what is classified
        // IConcept classified = Observables.INSTANCE.getDescribedType((IConcept) object);
        // if (classified != null) {
        // collectAnnotations(classified, collection);
        // }
        // }
        // for (IConcept parent : ((IConcept) object).getParents()) {
        // if (!CoreOntology.CORE_ONTOLOGY_NAME.equals(parent.getNamespace())) {
        // collectAnnotations(parent, collection);
        // }
        // }
        // }
        // }

    }


    public static class Classpath {

        /**
         * Extract the OWL assets in the classpath (under /knowledge/**) to the specified filesystem
         * directory.
         *
         * @param destinationDirectory
         * @throws IOException
         */
        public static void extractKnowledgeFromClasspath(File destinationDirectory) {
            try {
                PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
                Resource[] resources = resolver.getResources("/knowledge/**");
                for (Resource resource : resources) {

                    String path = null;
                    if (resource instanceof FileSystemResource) {
                        path = ((FileSystemResource) resource).getPath();
                    } else if (resource instanceof ClassPathResource) {
                        path = ((ClassPathResource) resource).getPath();
                    }
                    if (path == null) {
                        throw new KlabIOException("internal: cannot establish path for resource " + resource);
                    }

                    if (!path.endsWith("owl")) {
                        continue;
                    }

                    String filePath = path.substring(path.indexOf("knowledge/") + "knowledge/".length());

                    int pind = filePath.lastIndexOf('/');
                    if (pind >= 0) {
                        String fileDir = filePath.substring(0, pind);
                        File destDir = new File(destinationDirectory + File.separator + fileDir);
                        destDir.mkdirs();
                    }
                    File dest = new File(destinationDirectory + File.separator + filePath);
                    InputStream is = resource.getInputStream();
                    FileUtils.copyInputStreamToFile(is, dest);
                    is.close();
                }
            } catch (IOException ex) {
                throw new KlabIOException(ex);
            }
        }

        /**
         * Only works for a flat hierarchy!
         *
         * @param resourcePattern
         * @param destinationDirectory
         */
        public static void extractResourcesFromClasspath(String resourcePattern, File destinationDirectory) {

            try {
                PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
                Resource[] resources = resolver.getResources(resourcePattern);
                for (Resource resource : resources) {

                    String path = null;
                    if (resource instanceof FileSystemResource) {
                        path = ((FileSystemResource) resource).getPath();
                    } else if (resource instanceof ClassPathResource) {
                        path = ((ClassPathResource) resource).getPath();
                    }
                    if (path == null) {
                        throw new KlabIOException("internal: cannot establish path for resource " + resource);
                    }
                    String fileName = org.integratedmodelling.klab.api.utils.Utils.Files.getFileName(path);
                    File dest = new File(destinationDirectory + File.separator + fileName);
                    InputStream is = resource.getInputStream();
                    FileUtils.copyInputStreamToFile(is, dest);
                    is.close();
                }
            } catch (IOException ex) {
                throw new KlabIOException(ex);
            }
        }
    }

    public static class Files extends org.integratedmodelling.klab.api.utils.Utils.Files {

        static public final Set<String> JAVA_ARCHIVE_EXTENSIONS = Set.of("zip", "jar");

        public static void deleteDirectory(File pdir) {
            try {
                FileUtils.deleteDirectory(pdir);
            } catch (IOException e) {
                throw new KlabIOException(e);
            }
        }

        public static void touch(File file) {
            try {
                FileUtils.touch(file);
            } catch (IOException e) {
                throw new KlabIOException(e);
            }
        }

        public static boolean deleteQuietly(File pdir) {
            return FileUtils.deleteQuietly(pdir);
        }

        public static void copyDirectory(File directory, File backupDir) {
            try {
                FileUtils.copyDirectory(directory, backupDir);
            } catch (IOException e) {
                throw new KlabIOException(e);
            }
        }

        //        public static void writeStringToFile(String string, File file) {
        //            try {
        //                FileUtils.write(file, string, StandardCharsets.UTF_8);
        //            } catch (IOException e) {
        //                throw new KlabIOException(e);
        //            }
        //        }

    }

    public static class Git {

        public static final String MAIN_BRANCH = "master";

        /**
         * Compound repository operations (as implemented in Utils.Git in the common package) return one of
         * these, which contains notifications (they should be checked for errors before anything else is
         * done) and the relative paths that were affected. When changes affect a
         * {@link org.integratedmodelling.klab.api.knowledge.organization.Workspace}, they can be converted
         * into {@link org.integratedmodelling.klab.api.services.resources.ResourceSet} by a resources server
         * that knows mutual dependencies.
         *
         * FIXME these are often wrong. Must return:
         *    for pull: all changes w.r.t. head before pull (I think it does id)
         *    for commit: only those changes that come from the fetch before commit
         *    reset: what was reset in head + whatever comes from the pull after
         *
         *    All the changed paths should be reported in an INFO notification
         *
         *
         */
        public static class Modifications {

            private String repositoryName;

            private List<String> addedPaths = new ArrayList<>();
            private List<String> removedPaths = new ArrayList<>();
            private List<String> modifiedPaths = new ArrayList<>();
            private List<Notification> notifications = new ArrayList<>();

            public List<String> getAddedPaths() {
                return addedPaths;
            }

            public void setAddedPaths(List<String> addedPaths) {
                this.addedPaths = addedPaths;
            }

            public List<String> getRemovedPaths() {
                return removedPaths;
            }

            public void setRemovedPaths(List<String> removedPaths) {
                this.removedPaths = removedPaths;
            }

            public List<String> getModifiedPaths() {
                return modifiedPaths;
            }

            public void setModifiedPaths(List<String> modifiedPaths) {
                this.modifiedPaths = modifiedPaths;
            }

            public List<Notification> getNotifications() {
                return notifications;
            }

            public void setNotifications(List<Notification> notifications) {
                this.notifications = notifications;
            }

            public String getRepositoryName() {
                return repositoryName;
            }

            public void setRepositoryName(String repositoryName) {
                this.repositoryName = repositoryName;
            }
        }

        /**
         * Perform a fetch, if no issues do a merge, then commit any changes and push to origin. Use any
         * credentials installed for the origin repository.
         *
         * @param localRepository
         * @return Modifications record. Empty notifications means all OK. May have no errors but warnings, no
         * info. Use {@link Notifications#hasErrors(Collection)} on the notifications element to check.
         */
        public static Modifications fetchCommitAndPush(File localRepository, String commitMessage,
                                                       Scope scope) {

            Modifications ret = new Modifications();

            ret.setRepositoryName(Files.getFileBaseName(localRepository));

            try (var repo = new FileRepository(new File(localRepository + File.separator + ".git"))) {

                try (var git = new org.eclipse.jgit.api.Git(repo)) {

                    ObjectId oldHead = repo.resolve("HEAD^{tree}");

                    PullCommand pullCmd = git.pull();
                    pullCmd.setCredentialsProvider(getCredentialsProvider(git, scope));
                    PullResult result = pullCmd.call();
                    if (result != null && result.isSuccessful()) {
                        var messages = result.getFetchResult().getMessages();
                        if (messages != null && !messages.isEmpty()) {
                            ret.getNotifications().add(Notification.create(messages,
                                    Notification.Level.Info));
                        }
                        if (result.getMergeResult().getConflicts() != null && !result.getMergeResult().getConflicts().isEmpty()) {
                            ret.getNotifications().add(Notification.error("Conflicts during merge of "
                                            + Strings.join(result.getMergeResult().getConflicts().keySet(),
                                            ", "),
                                    UI.Interactivity.DISPLAY));
                        } else {

                            // commit locally
                            try {

                                var commit = git.commit().setMessage(commitMessage);
                                commit.setAll(true);
                                commit.setCredentialsProvider(getCredentialsProvider(git, scope));
                                var commitResult = commit.call();
                                PushCommand pushCommand = git.push();
                                pushCommand.setRemote("origin");
                                pushCommand.setCredentialsProvider(getCredentialsProvider(git, scope));
                                pushCommand.call();
                            } catch (GitAPIException ex) {
                                ret.getNotifications().add(Notification.error(ex, UI.Interactivity.DISPLAY));
                            }
                        }
                    }
                    compileDiff(repo, git, oldHead, ret);
                }
            } catch (Exception e) {
                ret.getNotifications().add(Notification.error(e, UI.Interactivity.DISPLAY));
            }
            return ret;

        }

        private static CredentialsProvider getCredentialsProvider(org.eclipse.jgit.api.Git git,
                                                                  Scope scope) {

            CredentialsProvider ret = null;
            ExternalAuthenticationCredentials credentials = null;
            try {
                for (RemoteConfig remoteConfig : git.remoteList().call()) {
                    if ("origin".equals(remoteConfig.getName()) && !remoteConfig.getURIs().isEmpty()) {
                        for (var uri : remoteConfig.getURIs()) {
                            credentials =
                                    Authentication.INSTANCE.getCredentials(uri.toString(), scope);
                            if (credentials != null) {
                                break;
                            }
                        }
                    }
                }
            } catch (GitAPIException e) {
                throw new RuntimeException(e);
            }

            return getCredentialsProvider(credentials);
        }

        public static CredentialsProvider getCredentialsProvider(ExternalAuthenticationCredentials credentials) {

            CredentialsProvider ret = null;
            if (credentials != null) {
                ret = switch (credentials.getScheme()) {
                    case ExternalAuthenticationCredentials.BASIC ->
                            new UsernamePasswordCredentialsProvider(credentials.getCredentials().get(0),
                                    credentials.getCredentials().get(1));
                    case ExternalAuthenticationCredentials.KEY -> null;
                    default -> null;
                };

                // check if we need to add a transport mechanism instead
                if (ret == null && ExternalAuthenticationCredentials.SSH.equals(credentials.getScheme())) {

                    //                    SshSessionFactory sshSessionFactory = new
                    //                    JschConfigSessionFactory() {
                    //                        @Override
                    //                        protected void configure(Host host, Session session) {
                    //                            // do nothing
                    //                        }
                    //
                    //                        @Override
                    //                        protected JSch createDefaultJSch(FS fs) throws JSchException {
                    //                            JSch defaultJSch = super.createDefaultJSch(fs);
                    //                            defaultJSch.addIdentity("c:/path/to/my/private_key");
                    //
                    //                            // if key is protected with passphrase
                    //                            // defaultJSch.addIdentity("c:/path/to/my/private_key",
                    //                            "my_passphrase");
                    //
                    //                            return defaultJSch;
                    //                        }
                    //                    };
                    //
                    //                    command.setTransportConfigCallback(transport -> {
                    //                        SshTransport sshTransport = (SshTransport) transport;
                    //                        sshTransport.setSshSessionFactory(sshSessionFactory);
                    //                    });
                }
            }
            return ret;
        }

        /**
         * Perform a safe pull operations from origin, using any installed credentials.
         *
         * @param localRepository
         * @return Modifications record. Empty notifications means all OK. May have no errors but warnings, no
         * info. Use {@link Notifications#hasErrors(Collection)} on the notifications element to check.
         */
        public static Modifications fetchAndMerge(File localRepository, Scope scope) {

            Modifications ret =
                    new Modifications();

            ret.setRepositoryName(Files.getFileBaseName(localRepository));

            try (var repo = new FileRepository(new File(localRepository + File.separator + ".git"))) {
                try (var git = new org.eclipse.jgit.api.Git(repo)) {

                    ObjectId oldHead = repo.resolve("HEAD^{tree}");

                    PullCommand pullCmd = git.pull();
                    pullCmd.setCredentialsProvider(getCredentialsProvider(git, scope));
                    PullResult result = pullCmd.call();
                    if (result != null && result.isSuccessful()) {
                        var messages = result.getFetchResult().getMessages();
                        if (messages != null && !messages.isEmpty()) {
                            ret.getNotifications().add(Notification.create(messages,
                                    Notification.Level.Info));
                        }
                        if (result.getMergeResult().getConflicts() != null && !result.getMergeResult().getConflicts().isEmpty()) {
                            ret.getNotifications().add(Notification.error("Conflicts during merge of "
                                            + Strings.join(result.getMergeResult().getConflicts().keySet(),
                                            ", "),
                                    UI.Interactivity.DISPLAY));
                        } else {
                            compileDiff(repo, git, oldHead, ret);
                        }
                    } else {
                        ret.getNotifications().add(Notification.error("Pull from default remote of " +
                                        "repository " + repo.getIdentifier() + " unsuccessful",
                                UI.Interactivity.DISPLAY));
                    }

                    /*
                    report changes
                     */
                }
            } catch (CheckoutConflictException c) {

                StringBuilder message = new StringBuilder("Conflicts exist between the local version " +
                        "and the on in the published repository.\nPlease resolve the conflicts using Git in the " +
                        "repository located at\n" + localRepository.getAbsolutePath() + "\n\nThe conflicting paths are:");

                for (var conflict : c.getConflictingPaths()) {
                    message.append("\n   ").append(conflict);
                }

                ret.getNotifications().add(Notification.error(message.toString(), UI.Interactivity.DISPLAY));

            } catch (Throwable e) {
                ret.getNotifications().add(Notification.create(e));
            }


            return ret;

        }

        private static void compileDiff(Repository repository, org.eclipse.jgit.api.Git git,
                                        ObjectId oldHead,
                                        Modifications ret) {

            try {
                var head = repository.resolve("HEAD^{tree}");
                ObjectReader reader = repository.newObjectReader();
                CanonicalTreeParser oldTreeIter = new CanonicalTreeParser();
                oldTreeIter.reset(reader, oldHead);
                CanonicalTreeParser newTreeIter = new CanonicalTreeParser();
                newTreeIter.reset(reader, head);
                for (var diff :
                        git.diff().setNewTree(newTreeIter).setOldTree(oldTreeIter).call()) {
                    switch (diff.getChangeType()) {
                        case ADD -> {
                            ret.getAddedPaths().add(diff.getNewPath());
                        }
                        case MODIFY -> {
                            ret.getModifiedPaths().add(diff.getOldPath());
                        }
                        case DELETE -> {
                            ret.getRemovedPaths().add(diff.getOldPath());
                        }
                        case COPY -> {
                            ret.getAddedPaths().add(diff.getNewPath());
                        }

                    }
                }
            } catch (Exception e) {
                ret.getNotifications().add(Notification.create(e));
            }
        }

        public static Modifications mergeChangesFrom(File localRepository, String branch) {
            // TODO ziobue
            return null;
        }

        /**
         * Commit any current changes before switching to the passed branch (either remote or local). If the
         * branch is new, create it based on current and instrument it for push/pull to/from origin.
         *
         * @param localRepository
         * @param branch
         * @return Modifications record. Empty notifications means all OK. May have no errors but warnings, no
         * info. Use {@link Notifications#hasErrors(Collection)} on the notifications element to check.
         */
        public static Modifications commitAndSwitch(File localRepository, String branch) {

            Modifications ret =
                    new Modifications();

            ret.setRepositoryName(Files.getFileBaseName(localRepository));

            try (var repo = new FileRepository(new File(localRepository + File.separator + ".git"))) {
                try (var git = new org.eclipse.jgit.api.Git(repo)) {
                    ObjectId oldHead = repo.resolve("HEAD^{tree}");

                    // TODO

                    compileDiff(repo, git, oldHead, ret);
                }
            } catch (IOException e) {
                ret.getNotifications().add(Notification.create(e));
            }

            return ret;

        }

        /**
         * Perform a hard reset, bringing the current repository to the last commit.
         *
         * @param localRepository
         * @return Modifications record. Empty notifications means all OK. May have no errors but warnings, no
         * info. Use {@link Notifications#hasErrors(Collection)} on the notifications element to check.
         */
        public static Modifications hardReset(File localRepository) {

            Modifications ret =
                    new Modifications();

            ret.setRepositoryName(Files.getFileBaseName(localRepository));

            try (var repo = new FileRepository(new File(localRepository + File.separator + ".git"))) {
                try (var git = new org.eclipse.jgit.api.Git(repo)) {
                    ObjectId oldHead = repo.resolve("HEAD^{tree}");
                    var result = git.reset().setMode(ResetCommand.ResetType.HARD).call();
                    compileDiff(repo, git, oldHead, ret);
                }
            } catch (Exception e) {
                ret.getNotifications().add(Notification.create(e));
            }

            return ret;
        }


        /**
         * Clone repository.
         * <p>
         * TODO use authentication
         *
         * @param gitUrl           the git url
         * @param directory        the directory
         * @param removeIfExisting the remove if existing
         * @return the string
         */
        public static String clone(String gitUrl, File directory, boolean removeIfExisting, Scope scope) {

            String dirname = URLs.getURLBaseName(gitUrl);

            File pdir = new File(directory + File.separator + dirname);
            if (pdir.exists()) {
                if (removeIfExisting) {
                    try {
                        Files.deleteDirectory(pdir);
                    } catch (Throwable e) {
                        throw new KlabIOException(e);
                    }
                } else {
                    throw new KlabIOException("git clone: directory " + pdir + " already exists");
                }
            }

            String[] pdefs = gitUrl.split("#");
            String branch;
            if (pdefs.length < 2) {
                branch = MAIN_BRANCH;
            } else {
                branch = branchExists(pdefs[0], pdefs[1]) ? pdefs[1] : MAIN_BRANCH;
            }
            String url = pdefs[0];

            Logging.INSTANCE.info("cloning Git repository " + url + " branch " + branch + " ...");

            CredentialsProvider credentialsProvider =
                    getCredentialsProvider(Authentication.INSTANCE.getCredentials(url, scope));

            try (org.eclipse.jgit.api.Git result =
                         org.eclipse.jgit.api.Git.cloneRepository().setURI(url)
                                                 .setCredentialsProvider(credentialsProvider)
                                                 .setBranch(branch).setDirectory(pdir).call()) {

                Logging.INSTANCE.info("cloned Git repository: " + result.getRepository());

                if (!branch.equals(MAIN_BRANCH)) {
                    result.checkout().setName(branch).setUpstreamMode(CreateBranchCommand.SetupUpstreamMode.TRACK).setStartPoint("origin/" + branch).call();
                    Logging.INSTANCE.info("switched repository: " + result.getRepository() + " to branch " + branch);
                }

            } catch (Throwable e) {
                throw new KlabIOException(e);
            }

            return dirname;
        }

        //        /**
        //         * Pull local repository in passed directory.
        //         * <p>
        //         * TODO use authentication
        //         *
        //         * @param localRepository main directory (containing .git/)
        //         */
        //        public static void pull(File localRepository) {
        //
        //            try (Repository localRepo = new FileRepository(localRepository + File.separator + "
        //            .git")) {
        //                try (org.eclipse.jgit.api.Git git = new org.eclipse.jgit.api.Git(localRepo)) {
        //
        //                    Logging.INSTANCE.info("fetch/merge changes in repository: " + git
        //                    .getRepository());
        //
        //                    PullCommand pullCmd = git.pull();
        //                    PullResult result = pullCmd.call();
        //                    // return result != null && result.getFetchResult() != null &&
        //                    // result.getFetchResult().
        //
        //                } catch (Throwable e) {
        //                    throw new KlabIOException("error pulling repository " + localRepository + ":
        //                    " + e.getLocalizedMessage());
        //                }
        //            } catch (IOException e) {
        //                throw new KlabIOException(e);
        //            }
        //        }

        /**
         * If a Git repository with the repository name corresponding to the URL exists in gitDirectory, pull
         * it from origin; otherwise clone it from the passed Git URL.
         * <p>
         * TODO: Assumes branch is already set correctly if repo is pulled. Should check branch and
         * checkout if necessary.
         * <p>
         * TODO use authentication
         *
         * @param gitUrl       the git url
         * @param gitDirectory the git directory
         * @return the string
         */
        public static Modifications requireUpdatedRepository(String gitUrl, File gitDirectory, Scope scope) {

            Modifications ret = null;
            String repositoryName = URLs.getURLBaseName(gitUrl);
            File repoDir = new File(gitDirectory + File.separator + repositoryName);
            File gitDir = new File(repoDir + File.separator + ".git");

            if (gitDir.exists() && gitDir.isDirectory() && gitDir.canRead() && repoDir.exists()) {

                ret = fetchAndMerge(repoDir, scope);
                /*
                 * TODO check branch and switch/pull if necessary
                 */
            } else {
                if (gitDir.exists()) {
                    Files.deleteQuietly(gitDir);
                }
                clone(gitUrl, gitDirectory, true, scope);
            }

            return ret;
        }

        /**
         * Checks if is remote git URL.
         *
         * @param string the string
         * @return a boolean.
         */
        public static boolean isRemoteGitURL(String string) {
            return string.startsWith("http:") || string.startsWith("git:") || string.startsWith("https:") || string.startsWith("git@");
        }

        /**
         * Check if remote branch exists
         *
         * @param gitUrl the remote repository
         * @param branch the branch (without refs/heads/)
         * @return true if branch exists
         */
        public static boolean branchExists(String gitUrl, String branch) {
            final LsRemoteCommand lsCmd = new LsRemoteCommand(null);
            lsCmd.setRemote(gitUrl);
            try {
                return lsCmd.call().stream().filter(ref -> ref.getName().equals("refs/heads/" + branch)).count() == 1;
            } catch (GitAPIException e) {
                e.printStackTrace();
                return false;
            }
        }
    }

}
