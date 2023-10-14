/*
 * This file is part of k.LAB.
 *
 * k.LAB is free software: you can redistribute it and/or modify it under the terms of the Affero
 * GNU General Public License as published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * A copy of the GNU Affero General Public License is distributed in the root directory of the k.LAB
 * distribution (LICENSE.txt). If this cannot be found see <http://www.gnu.org/licenses/>.
 *
 * Copyright (C) 2007-2018 integratedmodelling.org and any authors mentioned in author tags. All
 * rights reserved.
 */
package org.integratedmodelling.klab.configuration;

import io.github.classgraph.ClassGraph;
import io.github.classgraph.ClassInfo;
import io.github.classgraph.ScanResult;
import javassist.Modifier;
import org.integratedmodelling.klab.api.Klab;
import org.integratedmodelling.klab.api.collections.Literal;
import org.integratedmodelling.klab.api.collections.Pair;
import org.integratedmodelling.klab.api.exceptions.KException;
import org.integratedmodelling.klab.api.exceptions.KIOException;
import org.integratedmodelling.klab.api.exceptions.KInternalErrorException;
import org.integratedmodelling.klab.api.exceptions.KServiceAccessException;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.geometry.impl.GeometryImpl;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.KlabAsset.KnowledgeClass;
import org.integratedmodelling.klab.api.knowledge.Observable.Builder;
import org.integratedmodelling.klab.api.knowledge.observation.scale.Extent;
import org.integratedmodelling.klab.api.knowledge.observation.scale.Scale;
import org.integratedmodelling.klab.api.knowledge.observation.scale.space.Projection;
import org.integratedmodelling.klab.api.knowledge.observation.scale.space.Shape;
import org.integratedmodelling.klab.api.lang.Prototype;
import org.integratedmodelling.klab.api.lang.Prototype.FunctionType;
import org.integratedmodelling.klab.api.lang.impl.PrototypeImpl;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.services.Authority;
import org.integratedmodelling.klab.api.services.*;
import org.integratedmodelling.klab.api.services.resolver.Coverage;
import org.integratedmodelling.klab.api.services.runtime.Channel;
import org.integratedmodelling.klab.api.services.runtime.extension.KlabAnnotation;
import org.integratedmodelling.klab.api.services.runtime.extension.KlabFunction;
import org.integratedmodelling.klab.api.services.runtime.extension.KlabFunction.Argument;
import org.integratedmodelling.klab.api.services.runtime.extension.Library;
import org.integratedmodelling.klab.api.services.runtime.extension.Verb;
import org.integratedmodelling.klab.api.utils.Utils;
import org.integratedmodelling.klab.api.utils.Utils.OS;
import org.integratedmodelling.klab.components.LocalComponentRepository;
import org.integratedmodelling.klab.data.mediation.CurrencyServiceImpl;
import org.integratedmodelling.klab.data.mediation.UnitServiceImpl;
import org.integratedmodelling.klab.knowledge.ModelBuilderImpl;
import org.integratedmodelling.klab.knowledge.ObservableImpl;
import org.integratedmodelling.klab.logging.Logging;
import org.integratedmodelling.klab.runtime.language.LanguageService;
import org.integratedmodelling.klab.runtime.scale.CoverageImpl;
import org.integratedmodelling.klab.runtime.scale.ScaleImpl;
import org.integratedmodelling.klab.runtime.scale.space.ProjectionImpl;
import org.integratedmodelling.klab.runtime.scale.space.ShapeImpl;
import org.pf4j.DefaultPluginManager;
import org.pf4j.PluginManager;
import org.pf4j.update.PluginInfo;
import org.pf4j.update.UpdateManager;

import java.io.*;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Level;

/**
 * TODO use a declarative approach for all properties, so that there is one place for all default
 * settings and it's possible to override any of them through global JVM settings.
 *
 * @author Ferd
 * @version $Id: $Id
 */
public enum Configuration {

    INSTANCE;

    PluginManager componentManager = new DefaultPluginManager();
    UpdateManager componentUpdateManager;
    private Map<Class<?>, Map<Set<Object>, Service>> services = new HashMap<>();
    private Map<String, Authority> authorities = new HashMap<>();

    /**
     * Standard library loader. Must be registered explicitly when calling {@link #scanPackage(String, Map)}.
     * Not registering this along with the {@link Library} annotation can lead to interesting behaviors.
     */
    public BiConsumer<Annotation, Class<?>> LIBRARY_LOADER = (annotation, cls) -> {
        var languageService = getService(Language.class);
        for (Prototype prototype : loadLibrary((Library) annotation, cls)) {
            ((LanguageService) languageService).declare(prototype);
        }
    };

    static {

        /*
         * "injector" for the crucial k.LAB constructors
         */
        Klab.INSTANCE.setConfiguration(new Klab.Configuration() {

            private Projection defaultProjection = new ProjectionImpl(ProjectionImpl.DEFAULT_PROJECTION_CODE);
            private Projection latlonProjection = new ProjectionImpl(ProjectionImpl.DEFAULT_PROJECTION_CODE);

            @Override
            public Scale promoteGeometryToScale(Geometry geometry) {
                return new ScaleImpl(geometry);
            }

            @Override
            public Observable promoteConceptToObservable(Concept concept) {
                var ret = new ObservableImpl();
                ret.setSemantics(concept);
                ret.setUrn(concept.getUrn());
                ret.setDescriptionType(DescriptionType.forSemantics(concept.getType(),
                        concept.is(SemanticType.COUNTABLE)));
                return ret;
            }

            @Override
            public Builder getObservableBuilder(Concept observable, Scope scope) {
                return new ObservableBuildStrategy(observable, scope);
            }

            @Override
            public Projection getLatLonSpatialProjection() {
                return latlonProjection;
            }

            @Override
            public Projection getDefaultSpatialProjection() {
                return defaultProjection;
            }

            @Override
            public Shape createShapeFromTextSpecification(String shapeText, Projection projection) {
                return ShapeImpl.create(projection.getCode() + " " + shapeText);
            }

            @Override
            public Scale createScaleFromExtents(Collection<Extent<?>> extents) {
                return new ScaleImpl(Utils.Collections.asList(extents));
            }

            @Override
            public Builder getObservableBuilder(Observable observable, Scope scope) {
                return new ObservableBuildStrategy(observable, scope);
            }

            @Override
            public Projection getSpatialProjection(String string) {
                return new ProjectionImpl(string);
            }

            @Override
            public Coverage promoteScaleToCoverage(Scale geometry, double coverage) {
                return new CoverageImpl(geometry, coverage);
            }

            @Override
            public Model.Builder getModelBuilder(Observable observable) {
                return new ModelBuilderImpl(observable);
            }

            @Override
            public Model.Builder getModelBuilder(Artifact.Type nonSemanticType) {
                return new ModelBuilderImpl(nonSemanticType);
            }

            @Override
            public Model.Builder getModelBuilder(Resource resource) {
                return new ModelBuilderImpl(resource);
            }

            @Override
            public Model.Builder getModelBuilder(Literal value) {
                return new ModelBuilderImpl(value);
            }

            @Override
            public Model.Builder getModelLearner(String outputResourceUrn) {
                return new ModelBuilderImpl(outputResourceUrn);
            }

            @Override
            public Extent<?> createExtentCopy(Extent<?> extent) {
                return (Extent<?>) ((GeometryImpl.DimensionImpl) extent).copy();
            }
        });

    }

    /**
     * The package containing all REST resource beans.
     */
    static final public String REST_RESOURCES_PACKAGE_ID = "org.integratedmodelling.klab.rest";

    public static final int DEFAULT_ENGINE_PORT = 8283;
    public static final int DEFAULT_HUB_PORT = 8284;
    public static final int DEFAULT_NODE_PORT = 8287;
    public static final int DEFAULT_LEVER_PORT = 8761;
    public static final int DEFAULT_SEMANTIC_SERVER_PORT = 8301;
    public static final String DEFAULT_PRODUCTS_BRANCH = "master";

    public static final String KLAB_LOG_FILE = "klab.log.file";
    public static final String KLAB_OFFLINE = "klab.offline";
    public static final String KLAB_EXPORT_PATH = "klab.export.path";
    public static final String KLAB_DEBUG_RESOLUTION_RANKS = "klab.debugging.resolution.ranks";
    public static final String KLAB_DEBUG_RESOLUTION_GRAPH = "klab.debugging.resolution.graph";
    public static final String KLAB_DEBUG_RESOLUTION_DFLOW = "klab.debugging.resolution.dflow";
    public static final String KLAB_USE_IN_MEMORY_DATABASE = "klab.database.inmemory";
    public static final String KLAB_PARALLELIZE_CONTEXTUALIZATION = "klab.computation.parallel";
    public static final String KLAB_USE_IN_MEMORY_STORAGE = "klab.storage.inmemory";
    public static final String CERTFILE_PROPERTY = "klab.certificate";
    public static final String KLAB_CONNECTION_TIMEOUT = "klab.connection.timeout";
    public static final String KLAB_PROJECT_BLACKLIST_PROPERTY = "klab.project.blacklist";
    public static final String KLAB_STATS_SERVER_URL_PROPERTY = "stats.server.url";
    public static final String KLAB_LENIENT_GRID_INTERSECTION = "klab.grid.intersection.lenient";
    public static final String LOCAL_STATS_ACTIVE_PROPERTY = "org.integratedmodelling.stats.active";
    public static final String LOCAL_STATS_PRIVATE_PROPERTY = "org.integratedmodelling.stats.private";

    /**
     * If false, coverage of merged spatial layers is interpreted strictly, i.e. if a covered portion with
     * higher priority has nodata and a filler with lower priority has data, the nodata from the covered
     * portions substitute the filler's data.
     */
    public static final String KLAB_FILL_COVERED_NODATA = "klab.space.fillcoverednodata";

    /**
     * Minutes after which a session times out. Default 60.
     */
    public static final String KLAB_SESSION_TIMEOUT_MINUTES = "klab.session.timeout";

    /**
     * Absolute path of work directory. Overrides the default which is ${user.home}/THINKLAB_WORK_DIRECTORY
     */
    public static final String KLAB_DATA_DIRECTORY = "klab.data.directory";

    // configurable temp dir for (potentially very large) storage during simulation.
    public static final String KLAB_TEMPORARY_DATA_DIRECTORY = "klab.temporary.data.directory";

    public static final String KLAB_DISABLE_CONSOLE_ECHO = "klab.disable.console.echo";

    public static final String KLAB_ACCEPTED_WAIT_TIME_SECONDS = "klab.accepted.wait.time";

    /**
     * Name of work directory relative to ${user.home}. Ignored if THINKLAB_DATA_DIRECTORY_PROPERTY is
     * specified.
     */
    public static final String KLAB_WORK_DIRECTORY = "klab.work.directory";

    public static final String KLAB_ENGINE_CERTIFICATE = "klab.engine.certificate";

    /**
     * The Constant KLAB_ENGINE_USE_DEVELOPER_NETWORK.
     */
    public static final String KLAB_ENGINE_USE_DEVELOPER_NETWORK = "klab.engine.useDeveloperNetwork";

    /**
     * Class to choose to create storage - used only to disambiguate if > 1 storage providers are available.
     */
    public static final String STORAGE_PROVIDER_COMPONENT = "klab.storage.provider.class";

    /**
     * Class to choose to create dataflow runtimes - used only to disambiguate if > 1 runtime providers are
     * available.
     */
    public static final String RUNTIME_PROVIDER_COMPONENT = "klab.runtime.provider.class";

    /**
     * If defined, the engine will print times for each actuator run
     */
    public static final String KLAB_SHOWTIMES_PROPERTY = "klab.showtimes";

    /**
     * If defined and set to <code>true</code>, then the region context will be extended assure square grid
     * cells.
     */
    public static final String KLAB_GRID_CONSTRAINT = "klab.grid.forceSquarecells";

    /**
     * If defined and set to <code>true</code>, then intermediate data processed by the models are to be
     * dumped to disk.
     */
    public static final String KLAB_MODEL_DUMP_INTERMEDIATE = "klab.model.dumpIntermediateData";

    /**
     * URL of local node (must match certfile) when running in develop config. Pass to hub as -D to override
     * the default (which won't work on Win), normally with a 127.0.0.1-based URL.
     */
    public static final String KLAB_DEV_NODE_URL = "klab.dev.node.url";

    /**
     * Branch to use for groups observables
     */
    public static final String KLAB_PRODUCTS_BRANCH = "klab.products.branch";

    private OS os;
    private Properties properties;
    private File dataPath;
    private Level loggingLevel = Level.SEVERE;
    private Level notificationLevel = Level.INFO;

    /**
     * The klab relative work path.
     */
    public String KLAB_RELATIVE_WORK_PATH = ".klab";

    private Configuration() {

        if (System.getProperty(KLAB_DATA_DIRECTORY) != null) {
            this.dataPath = new File(System.getProperty(KLAB_DATA_DIRECTORY));
        } else {
            String home = System.getProperty("user.home");
            if (System.getProperty(KLAB_WORK_DIRECTORY) != null) {
                KLAB_RELATIVE_WORK_PATH = System.getProperty(KLAB_WORK_DIRECTORY);
            }
            this.dataPath = new File(home + File.separator + KLAB_RELATIVE_WORK_PATH);

            /*
             * make sure it's available for substitution in property files etc.
             */
            System.setProperty(KLAB_DATA_DIRECTORY, this.dataPath.toString());
        }

        this.dataPath.mkdirs();

        // KLAB.info("k.LAB data directory set to " + dataPath);

        this.properties = new Properties();
        File pFile = new File(dataPath + File.separator + "klab.properties");
        if (!pFile.exists()) {
            try {
                pFile.createNewFile();
            } catch (IOException e) {
                throw new KIOException("cannot write to configuration directory");
            }
        }
        try (InputStream input = new FileInputStream(pFile)) {
            this.properties.load(input);
        } catch (Exception e) {
            throw new KIOException("cannot read configuration properties");
        }

        registerService(new UnitServiceImpl(), UnitService.class);
        registerService(new CurrencyServiceImpl(), CurrencyService.class);
        registerService(new LanguageService(), Language.class);

    }

    public List<Prototype> loadLibrary(Library annotation, Class<?> cls) {

        List<Prototype> ret = new ArrayList<>();
        String namespacePrefix = Library.CORE_LIBRARY.equals(annotation.name()) ? "" :
                                 (annotation.name() + ".");

        for (Class<?> clss : cls.getClasses()) {
            if (clss.isAnnotationPresent(KlabFunction.class)) {
                ret.add(createContextualizerPrototype(namespacePrefix,
                        clss.getAnnotation(KlabFunction.class), clss,
                        null));
            } else if (clss.isAnnotationPresent(Verb.class)) {
                ret.add(createVerbPrototype(namespacePrefix, clss.getAnnotation(Verb.class), clss, null));
            } else if (clss.isAnnotationPresent(KlabAnnotation.class)) {
                ret.add(createAnnotationPrototype(namespacePrefix, clss.getAnnotation(KlabAnnotation.class)
                        , clss,
                        null));
            }
        }

        // annotated methods
        for (Method method : cls.getDeclaredMethods()) {
            if (Modifier.isPublic(method.getModifiers()) && method.isAnnotationPresent(KlabFunction.class)) {
                ret.add(createContextualizerPrototype(namespacePrefix,
                        method.getAnnotation(KlabFunction.class), cls,
                        method));
            } else if (method.isAnnotationPresent(KlabAnnotation.class)) {
                ret.add(createAnnotationPrototype(namespacePrefix, cls.getAnnotation(KlabAnnotation.class),
                        cls,
                        method));
            } else if (method.isAnnotationPresent(Verb.class)) {
                ret.add(createVerbPrototype(namespacePrefix, cls.getAnnotation(Verb.class), cls, method));
            }
        }
        return ret;
    }

    public Prototype createVerbPrototype(String namespacePrefix, Verb annotation, Class<?> clss,
                                         Method method) {
        // TODO
        return null;
    }

    public Prototype createContextualizerPrototype(String namespacePrefix, KlabFunction annotation,
                                                   Class<?> clss,
                                                   Method method) {

        var ret = new PrototypeImpl();

        ret.setName(namespacePrefix + annotation.name());
        ret.setDescription(annotation.description());
        ret.setFilter(annotation.filter());
        ret.setImplementation(clss);
        ret.setExecutorMethod(method == null ? null : method.getName());
        ret.setGeometry(annotation.geometry().isEmpty() ? null : Geometry.create(annotation.geometry()));
        ret.setLabel(annotation.dataflowLabel());
        ret.setReentrant(annotation.reentrant());
        ret.setFunctionType(FunctionType.FUNCTION);

        for (Artifact.Type a : annotation.type()) {
            ret.getType().add(a);
        }

        for (Argument argument : annotation.parameters()) {
            var arg = createArgument(argument);
            ret.getArguments().put(arg.getName(), arg);
        }
        for (Argument argument : annotation.exports()) {
            var arg = createArgument(argument);
            ret.getImports().add(arg);
        }
        for (Argument argument : annotation.imports()) {
            var arg = createArgument(argument);
            ret.getExports().add(arg);
        }

        return ret;
    }

    public Prototype createAnnotationPrototype(String namespacePrefix, KlabAnnotation annotation,
                                               Class<?> clss,
                                               Method method) {

        var ret = new PrototypeImpl();

        ret.setName(namespacePrefix + annotation.name());
        ret.setDescription(annotation.description());
        ret.setImplementation(clss);
        ret.setExecutorMethod(method == null ? null : method.getName());
        ret.setFunctionType(FunctionType.ANNOTATION);
        for (KnowledgeClass kcl : annotation.targets()) {
            ret.getTargets().add(kcl);
        }

        for (Argument argument : annotation.parameters()) {
            var arg = createArgument(argument);
            ret.getArguments().put(arg.getName(), arg);
        }

        return ret;
    }

    private PrototypeImpl.ArgumentImpl createArgument(Argument argument) {
        var arg = new PrototypeImpl.ArgumentImpl();
        arg.setName(argument.name());
        arg.setDescription(argument.description());
        arg.setOptional(argument.optional());
        arg.setConst(argument.constant());
        arg.setArtifact(argument.artifact());
        for (Artifact.Type a : argument.type()) {
            arg.getType().add(a);
        }
        return arg;
    }

    /**
     * Check for updates and load all registered components; return the set of packages to scan from them.
     * <p>
     * TODO!
     *
     * @return
     */
    public Set<String> updateAndLoadComponents(String serviceName) {

        Set<String> ret = new LinkedHashSet<>();
        if (componentUpdateManager == null) {
            componentUpdateManager = new UpdateManager(componentManager, new ArrayList<>());
            componentUpdateManager.addRepository(new LocalComponentRepository(serviceName));
        }

        if (componentUpdateManager.hasUpdates()) {
            List<PluginInfo> updates = componentUpdateManager.getUpdates();
            Logging.INSTANCE.debug("Found {} updates", updates.size());
            for (PluginInfo plugin : updates) {
                Logging.INSTANCE.debug("Found update for plugin '{}'", plugin.id);
                PluginInfo.PluginRelease lastRelease = componentUpdateManager.getLastPluginRelease(plugin.id);
                String lastVersion = lastRelease.version;
                String installedVersion = componentManager.getPlugin(plugin.id).getDescriptor().getVersion();
                Logging.INSTANCE.debug("Update plugin '{}' from version {} to version {}", plugin.id,
                        installedVersion,
                        lastVersion);
                boolean updated = componentUpdateManager.updatePlugin(plugin.id, lastVersion);
                if (updated) {
                    Logging.INSTANCE.debug("Updated plugin '{}'", plugin.id);
                } else {
                    Logging.INSTANCE.error("Cannot update plugin '{}'", plugin.id);
                }
            }
        } else {
            Logging.INSTANCE.debug("No updates found");
        }

        // check for available (new) plugins
        if (componentUpdateManager.hasAvailablePlugins()) {
            List<PluginInfo> availablePlugins = componentUpdateManager.getAvailablePlugins();
            Logging.INSTANCE.debug("Found {} available plugins", availablePlugins.size());
            for (PluginInfo plugin : availablePlugins) {
                Logging.INSTANCE.debug("Found available plugin '{}'", plugin.id);
                PluginInfo.PluginRelease lastRelease = componentUpdateManager.getLastPluginRelease(plugin.id);
                String lastVersion = lastRelease.version;
                Logging.INSTANCE.debug("Install plugin '{}' with version {}", plugin.id, lastVersion);
                boolean installed = componentUpdateManager.installPlugin(plugin.id, lastVersion);
                if (installed) {
                    Logging.INSTANCE.debug("Installed plugin '{}'", plugin.id);
                } else {
                    Logging.INSTANCE.error("Cannot install plugin '{}'", plugin.id);
                }
            }
        } else {
            Logging.INSTANCE.debug("No available plugins found");
        }

        componentManager.loadPlugins();

        return ret;

    }

    /**
     * Single scanning loop for all registered annotations in a package. Done on the main codebase and in each
     * component based on the declared packages.
     * <p>
     * TODO use plug-in manifest to declare packages to scan.
     * Moved to ClassGraph to allow development with Java 21 before Spring Boot 3.2.0 is released
     *
     * @param packageId
     * @return all annotations found with the corresponding class
     */
    public List<Pair<Annotation, Class<?>>> scanPackage(String packageId,
                                                        Map<Class<? extends Annotation>,
                                                                BiConsumer<Annotation,
                                                                Class<?>>> annotationHandlers) {

        List<Pair<Annotation, Class<?>>> ret = new ArrayList<>();
        try (ScanResult scanResult =
                     new ClassGraph()
                             .enableAnnotationInfo()
                             .acceptPackages(packageId)
                             .scan()) {
            for (Class<? extends Annotation> ah : annotationHandlers.keySet()) {
                for (ClassInfo routeClassInfo : scanResult.getClassesWithAnnotation(ah)) {
                    try {
                        Class<?> cls = Class.forName(routeClassInfo.getName());
                        Annotation annotation = cls.getAnnotation(ah);
                        if (annotation != null) {
                            annotationHandlers.get(ah).accept(annotation, cls);
                            ret.add(Pair.of(annotation, cls));
                        }
                    } catch (ClassNotFoundException e) {
                        Logging.INSTANCE.error(e);
                        continue;
                    }
                }
            }
        }

// Spring-based, uses ASM which is not yet compatible with Java 21
//        ClassPathScanningCandidateComponentProvider provider = new
//        ClassPathScanningCandidateComponentProvider(false);
//        for (Class<? extends Annotation> ah : annotationHandlers.keySet()) {
//            provider.addIncludeFilter(new AnnotationTypeFilter(ah));
//        }
//
//        Set<BeanDefinition> beans = provider.findCandidateComponents(packageId);
//        for (BeanDefinition bd : beans) {
//            for (Class<? extends Annotation> ah : annotationHandlers.keySet()) {
//                try {
//                    Class<?> cls = Class.forName(bd.getBeanClassName());
//                    Annotation annotation = cls.getAnnotation(ah);
//                    if (annotation != null) {
//                        annotationHandlers.get(ah).accept(annotation, cls);
//                        ret.add(Pair.of(annotation, cls));
//                    }
//                } catch (ClassNotFoundException e) {
//                    Logging.INSTANCE.error(e);
//                    continue;
//                }
//            }
//        }

        return ret;
    }

    public Properties getProperties() {
        return this.properties;
    }

    public String getProperty(String property, String defaultValue) {
        String ret = System.getProperty(property);
        if (ret == null) {
            ret = getProperties().getProperty(property);
        }
        return ret == null ? defaultValue : ret;
    }

    /**
     * Non-API Save the properties after making changes from outside configuration. Should be used only
     * internally, or removed in favor of a painful setting API.
     */
    public void save() {

        File td = new File(dataPath + File.separator + "klab.properties");

        // String[] doNotPersist = new String[] { Project.ORIGINATING_NODE_PROPERTY };

        Properties p = new Properties();
        p.putAll(getProperties());

        // for (String dn : doNotPersist) {
        // p.remove(dn);
        // }

        try {
            p.store(new FileOutputStream(td), null);
        } catch (Exception e) {
            throw new KIOException(e);
        }

    }

    /**
     * Use reasoner.
     *
     * @return a boolean.
     */
    public boolean useReasoner() {
        return true;
    }

    /**
     * Applies the standard k.LAB property pattern "klab.{service}.{property}" and retrieves the correspondent
     * property.
     *
     * @param service
     * @param property
     * @return
     */
    public String getServiceProperty(String service, String property) {
        return getProperty("klab." + service + "." + property, null);
    }

    /**
     * Applies the standard k.LAB property pattern "klab.{service}.{property}" and retrieves the correspondent
     * property.
     *
     * @param service
     * @param property
     * @param defaultValue
     * @return
     */
    public String getServiceProperty(String service, String property, String defaultValue) {
        return getProperty("klab." + service + "." + property, defaultValue);
    }

    public OS getOS() {

        if (this.os == null) {

            String osd = System.getProperty("os.name").toLowerCase();

            // TODO ALL these checks need careful checking
            if (osd.contains("windows")) {
                os = OS.WIN;
            } else if (osd.contains("mac")) {
                os = OS.MACOS;
            } else if (osd.contains("linux") || osd.contains("unix")) {
                os = OS.UNIX;
            }
        }

        return this.os;
    }

    public File getDataPath(String subspace) {

        String dpath = dataPath.toString();
        File ret = dataPath;

        String[] paths = subspace.split("/");
        for (String path : paths) {
            ret = new File(dpath + File.separator + path);
            ret.mkdirs();
            dpath += File.separator + path;
        }
        return ret;
    }

    public File getDefaultExportDirectory() {
        File ret = new File(getProperties().getProperty(KLAB_EXPORT_PATH, dataPath + File.separator +
                "export"));
        ret.mkdirs();
        return ret;
    }

    public boolean isOffline() {
        return getProperties().getProperty(KLAB_OFFLINE, "false").equals("true");
    }

    public boolean isDebugResolutionRanks() {
        return getProperties().getProperty(KLAB_DEBUG_RESOLUTION_RANKS, "false").equals("true");
    }

    public File getDataPath() {
        return dataPath;
    }

    public int getDataflowThreadCount() {
        // TODO Auto-generated method stub
        return 10;
    }

    public int getTaskThreadCount() {
        // TODO Auto-generated method stub
        return 10;
    }

    public int getScriptThreadCount() {
        // TODO Auto-generated method stub
        return 3;
    }

    public int getResourceThreadCount() {
        // TODO Auto-generated method stub
        return 3;
    }

    public boolean isRemoteResolutionEnabled() {
        // TODO tie to option + live setting
        return true;
    }

    public boolean allowAnonymousUsage() {
        return true;
    }

    public Level getLoggingLevel() {
        return loggingLevel;
    }

    public Level getNotificationLevel() {
        return notificationLevel;
    }

    public double getAcceptedSubsettingError() {
        // TODO Auto-generated method stub
        return 0.15;
    }

    public boolean resolveAllInstances() {
        // TODO tie to engine configuration property
        return false;
    }

    public int getMaxLiveObservationContextsPerSession() {
        // TODO tie to engine configuration property + live setting
        return 10;
    }

    public boolean useInMemoryDatabase() {
        return getProperties().getProperty(KLAB_USE_IN_MEMORY_DATABASE, "true").equals("true");
    }

    public long getResourceRecheckIntervalMs() {
        // TODO tie to engine configuration property. This is 10 minutes
        return 10 * 60 * 1000;
    }

    public boolean parallelizeContextualization() {
        return System.getProperty("parallel") != null
                || properties.getProperty(KLAB_PARALLELIZE_CONTEXTUALIZATION, "false").equals("true");
    }

    public boolean useInMemoryStorage() {
        return System.getProperty("mmap") == null && properties.getProperty(KLAB_USE_IN_MEMORY_STORAGE,
                "true").equals("true");
    }

    public File getExportFile(String export) {
        if (Utils.Files.isRelativePath(export)) {
            return new File(getDefaultExportDirectory() + File.separator + export);
        }
        return new File(export);
    }

    public boolean forceResourcesOnline() {
        return System.getProperty("forceResourcesOnline") != null;
    }

    public File getTemporaryDataDirectory() {
        return new File(getProperties().getProperty(KLAB_TEMPORARY_DATA_DIRECTORY, System.getProperty("java" +
                ".io" +
                ".tmpdir")));
    }

    /**
     * Return a new directory in the temporary area. The directory is automatically removed when the VM shuts
     * down.
     *
     * @param directoryPrefix
     * @return
     */
    public File getScratchDataDirectory(String directoryPrefix) {
        File ret =
                new File(getTemporaryDataDirectory() + File.separator + directoryPrefix + Utils.Names.shortUUID());
        ret.mkdirs();
        ret.deleteOnExit();
        return ret;
    }

    public boolean synchronousDataflow() {
        return System.getProperty("synchronous") != null;
    }

    public boolean isEchoEnabled() {
        return !"true".equals(getProperty(Configuration.KLAB_DISABLE_CONSOLE_ECHO, "false"));
    }

    /**
     * Find a file correspondent to the passed argument. If the string encodes a full path, just return the
     * correspondent file; otherwise explore any configured filepath. In all case only return non-null if the
     * file exists and is readable.
     *
     * @param argument
     * @return
     */
    public File findFile(String argument) {
        File ret = new File(argument);
        if (ret.exists() && ret.isFile()) {
            return ret;
        }
        /*
         * TODO the rest
         */
        return null;
    }

    public int getMaxWaitTime() {
        return Integer.parseInt(getProperty(Configuration.KLAB_ACCEPTED_WAIT_TIME_SECONDS, "10"));
    }

    public String getProductsBranch() {
        return getProperty(Configuration.KLAB_PRODUCTS_BRANCH, Configuration.DEFAULT_PRODUCTS_BRANCH);
    }

    public URL getLocalComponentRepositoryURL(String servicePath) {
        try {
            return getDataPath(servicePath == null ? "components" : (servicePath + "/" + "components")).toURI().toURL();
        } catch (MalformedURLException e) {
            throw new KInternalErrorException(e);
        }
    }

    /**
     * Obtain the best service available for the class and parameters. If multiple are available, choose the
     * one with the lightest load or access cost, according to implementation. If not found, throw a
     * {@link KServiceAccessException}.
     *
     * @param <T>
     * @param serviceClass
     * @param parameters   any POD or Comparable.
     * @return
     * @throws KServiceAccessException if the requested service is not available
     */
    public <T extends Service> T getService(Class<T> serviceClass, Object... parameters) throws KServiceAccessException {
        Collection<T> results = getServices(serviceClass, parameters);
        if (results.size() > 0) {
            return results.iterator().next();
        }
        throw new KServiceAccessException("no service of class " + serviceClass.getCanonicalName() + " was " +
                "registered");
    }

    /**
     * Return all registered and available services of the passed class, potentially filtering through the
     * passed parameters. If none available, return an empty collection. If multiple services are available,
     * they should be sorted with the "best" service on top.
     *
     * @param <T>
     * @param serviceClass
     * @param parameters   any POD or Comparable
     * @return
     */
    @SuppressWarnings("unchecked")
    public <T extends Service> Collection<T> getServices(Class<T> serviceClass, Object... parameters) {

        Set<T> ret = new HashSet<>();
        Set<Object> key = new HashSet<>();
        if (parameters != null) {
            for (Object p : parameters) {
                key.add(p);
            }
        }

        Map<Set<Object>, Service> rets = services.get(serviceClass);

        if (rets != null) {
            for (Set<Object> k : rets.keySet()) {
                if (k.containsAll(key)) {
                    ret.add((T) rets.get(k));
                }
            }
        }

        // TODO sort services in case their load, availability or remoteness can be
        // measured

        return ret;
    }

    public void registerService(Service service, Class<? extends Service> keyClass, Object... parameters) {

        Set<Object> key = new HashSet<>();
        if (parameters != null) {
            for (Object p : parameters) {
                key.add(p);
            }
        }
        Map<Set<Object>, Service> rets = services.get(keyClass);
        if (rets == null) {
            rets = new HashMap<>();
            services.put(keyClass, rets);
        }

        rets.put(key, service);

    }

    public Map<String, Authority> getAuthorities() {
        return authorities;
    }

    public void registerAuthority(Authority authority) {
        if (authority.getCapabilities().getSubAuthorities().isEmpty()) {
            this.authorities.put(authority.getName(), authority);
        } else {
            for (Pair<String, String> sub : authority.getCapabilities().getSubAuthorities()) {
                String aname = authority.getName() + (sub.getFirst().isEmpty() ? "" : ("." + sub.getFirst()));
                this.authorities.put(aname, sub.getFirst().isEmpty() ? authority :
                                            authority.subAuthority(sub.getFirst()));
            }
        }
    }

    private static int MAX_THREADS = 10;

    /**
     * Broadcast a request to all accessible services of a given type concurrently and merge the results of
     * the request function into a collection.
     *
     * @param <S>
     * @param <T>
     * @param request
     * @param serviceClass
     * @return
     */
    public <S extends KlabService, T> Collection<T> broadcastRequest(Function<S, T> request, Scope scope,
                                                                     Class<S> serviceClass) {

        return null;
    }

    /**
     * Use to concurrently submit a request to the available federated services of a particular type and merge
     * the results into a given collection.
     *
     * @param <T>                    type of result in the resulting collections
     * @param serviceClass           type of the service (use the interfaces!)
     * @param retriever              a function that retrieves results from each individual service
     * @param merger                 a function that takes the results of all services and returns the final
     *                               organization of them as a single collection
     * @param individualResponseType the type of the response object (not sure it's needed)
     * @param monitor                a monitor to check on progress and report errors
     * @return the merged collection
     */
    public <S extends KlabService, T> Collection<T> mergeServiceResults(Class<S> serviceClass,
                                                                        Supplier<Collection<T>> retriever,
                                                                        Function<Collection<Collection<T>>,
                                                                                Collection<T>> merger, Class<?
            extends T> individualResponseType,
                                                                        Channel monitor) {

        //
        // Collection<Callable<K>> tasks = new ArrayList<>();
        // ISession session = monitor.getIdentity().getParentIdentity(ISession.class);
        // for (INodeIdentity node : onlineNodes.values()) {
        // tasks.add(new Callable<K>(){
        // @Override
        // public K call() throws Exception {
        // return node.getClient().onBehalfOf(session.getUser()).get(endpoint,
        // individualResponseType, urlVariables);
        // }
        // });
        // }
        //
        // ExecutorService executor = Executors.newFixedThreadPool((onlineNodes.size() +
        // offlineNodes.size()) > MAX_THREADS
        // ? MAX_THREADS
        // : (onlineNodes.size() + offlineNodes.size()));
        //
        // int failures = 0;
        // List<K> retvals = new ArrayList<>();
        // List<Future<K>> results;
        // try {
        // results = executor.invokeAll(tasks);
        // for (Future<K> result : results) {
        // try {
        // retvals.add(result.get());
        // } catch (Exception e) {
        // failures++;
        // }
        // }
        // } catch (Exception e) {
        // throw new KlabIOException(e);
        // }
        //
        // if (failures > 0) {
        // String message = "broadcasting to network resulted in " + failures + " failed
        // calls out
        // of " + onlineNodes.size();
        // if (failures >= onlineNodes.size()) {
        // monitor.error(message);
        // } else {
        // monitor.warn(message);
        // }
        // }
        //
        // executor.shutdown();
        //
        // return merger.apply(retvals);

        return null;
    }

}
