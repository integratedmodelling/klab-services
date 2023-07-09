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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.logging.Level;

import org.integratedmodelling.klab.api.Klab;
import org.integratedmodelling.klab.api.exceptions.KException;
import org.integratedmodelling.klab.api.exceptions.KInternalErrorException;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.Concept;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.Observable.Builder;
import org.integratedmodelling.klab.api.knowledge.observation.scale.Extent;
import org.integratedmodelling.klab.api.knowledge.observation.scale.Scale;
import org.integratedmodelling.klab.api.knowledge.observation.scale.space.Projection;
import org.integratedmodelling.klab.api.knowledge.observation.scale.space.Shape;
import org.integratedmodelling.klab.components.LocalComponentRepository;
import org.integratedmodelling.klab.exceptions.KlabException;
import org.integratedmodelling.klab.exceptions.KlabIOException;
import org.integratedmodelling.klab.logging.Logging;
import org.integratedmodelling.klab.utils.MiscUtilities;
import org.integratedmodelling.klab.utils.NameGenerator;
import org.integratedmodelling.klab.utils.OS;
import org.integratedmodelling.klab.utils.Pair;
import org.pf4j.DefaultPluginManager;
import org.pf4j.PluginManager;
import org.pf4j.update.PluginInfo;
import org.pf4j.update.UpdateManager;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;

/**
 * TODO use a declarative approach for all properties, so that there is one
 * place for all default settings and it's possible to override any of them
 * through global JVM settings.
 *
 * @author Ferd
 * @version $Id: $Id
 */
public enum Configuration {

	INSTANCE;

	PluginManager componentManager = new DefaultPluginManager();
	UpdateManager componentUpdateManager;

	static {

		/*
		 * "injector" for the crucial k.LAB constructors
		 */
		Klab.INSTANCE.setConfiguration(new Klab.Configuration() {

			@Override
			public Scale promoteGeometryToScale(Geometry geometry) {
				// TODO Auto-generated method stub
				throw new KException("IMPLEMENTAMI OSTIA");
			}

			@Override
			public Observable promoteConceptToObservable(Concept concept) {
				// TODO Auto-generated method stub
				throw new KException("IMPLEMENTAMI OSTIA");
			}

			@Override
			public Builder getObservableBuilder(Concept observable) {
				// TODO Auto-generated method stub
				throw new KException("IMPLEMENTAMI OSTIA");
			}

			@Override
			public Projection getLatLonSpatialProjection() {
				// TODO Auto-generated method stub
				throw new KException("IMPLEMENTAMI OSTIA");
			}

			@Override
			public Projection getDefaultSpatialProjection() {
				// TODO Auto-generated method stub
				throw new KException("IMPLEMENTAMI OSTIA");
			}

			@Override
			public Shape createShapeFromTextSpecification(String shapeText, Projection projection) {
				// TODO Auto-generated method stub
				throw new KException("IMPLEMENTAMI OSTIA");
			}

			@Override
			public Scale createScaleFromExtents(Collection<Extent<?>> extents) {
				// TODO Auto-generated method stub
				throw new KException("IMPLEMENTAMI OSTIA");
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
	 * If false, coverage of merged spatial layers is interpreted strictly, i.e. if
	 * a covered portion with higher priority has nodata and a filler with lower
	 * priority has data, the nodata from the covered portions substitute the
	 * filler's data.
	 */
	public static final String KLAB_FILL_COVERED_NODATA = "klab.space.fillcoverednodata";

	/**
	 * Minutes after which a session times out. Default 60.
	 */
	public static final String KLAB_SESSION_TIMEOUT_MINUTES = "klab.session.timeout";

	/**
	 * Absolute path of work directory. Overrides the default which is
	 * ${user.home}/THINKLAB_WORK_DIRECTORY
	 */
	public static final String KLAB_DATA_DIRECTORY = "klab.data.directory";

	// configurable temp dir for (potentially very large) storage during simulation.
	public static final String KLAB_TEMPORARY_DATA_DIRECTORY = "klab.temporary.data.directory";

	public static final String KLAB_DISABLE_CONSOLE_ECHO = "klab.disable.console.echo";

	public static final String KLAB_ACCEPTED_WAIT_TIME_SECONDS = "klab.accepted.wait.time";

	/**
	 * Name of work directory relative to ${user.home}. Ignored if
	 * THINKLAB_DATA_DIRECTORY_PROPERTY is specified.
	 */
	public static final String KLAB_WORK_DIRECTORY = "klab.work.directory";

	public static final String KLAB_ENGINE_CERTIFICATE = "klab.engine.certificate";

	/** The Constant KLAB_ENGINE_USE_DEVELOPER_NETWORK. */
	public static final String KLAB_ENGINE_USE_DEVELOPER_NETWORK = "klab.engine.useDeveloperNetwork";

	/**
	 * Class to choose to create storage - used only to disambiguate if > 1 storage
	 * providers are available.
	 */
	public static final String STORAGE_PROVIDER_COMPONENT = "klab.storage.provider.class";

	/**
	 * Class to choose to create dataflow runtimes - used only to disambiguate if >
	 * 1 runtime providers are available.
	 */
	public static final String RUNTIME_PROVIDER_COMPONENT = "klab.runtime.provider.class";

	/**
	 * If defined, the engine will print times for each actuator run
	 */
	public static final String KLAB_SHOWTIMES_PROPERTY = "klab.showtimes";

	/**
	 * If defined and set to <code>true</code>, then the region context will be
	 * extended assure square grid cells.
	 */
	public static final String KLAB_GRID_CONSTRAINT = "klab.grid.forceSquarecells";

	/**
	 * If defined and set to <code>true</code>, then intermediate data processed by
	 * the models are to be dumped to disk.
	 */
	public static final String KLAB_MODEL_DUMP_INTERMEDIATE = "klab.model.dumpIntermediateData";

	/**
	 * URL of local node (must match certfile) when running in develop config. Pass
	 * to hub as -D to override the default (which won't work on Win), normally with
	 * a 127.0.0.1-based URL.
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
	private Map<Class<? extends Annotation>, BiConsumer<Annotation, Class<?>>> annotationHandlers = new HashMap<>();

	/** The klab relative work path. */
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
				throw new KlabIOException("cannot write to configuration directory");
			}
		}
		try (InputStream input = new FileInputStream(pFile)) {
			this.properties.load(input);
		} catch (Exception e) {
			throw new KlabIOException("cannot read configuration properties");
		}

		/*
		 * TODO register handlers for the annotations providing core functionalities:
		 * adapters, contextualizers, libraries, prototypes
		 */

		/*
		 * TODO configure plugin management. Should have a repositories.json managed
		 * through the API; each repository's content and plugins.json should be
		 * verified, managed and installed via admin API.
		 */

	}

	/**
	 * Check for updates and load all registered components; return the set of
	 * packages to scan from them.
	 * 
	 * TODO!
	 * 
	 * @return
	 */
	public Set<String> updateAndLoadComponents() {

		Set<String> ret = new LinkedHashSet<>();
		if (componentUpdateManager == null) {
			componentUpdateManager = new UpdateManager(componentManager, new ArrayList<>());
			componentUpdateManager.addRepository(new LocalComponentRepository());
		}
		
		if (componentUpdateManager.hasUpdates()) {
			List<PluginInfo> updates = componentUpdateManager.getUpdates();
			Logging.INSTANCE.debug("Found {} updates", updates.size());
			for (PluginInfo plugin : updates) {
				Logging.INSTANCE.debug("Found update for plugin '{}'", plugin.id);
				PluginInfo.PluginRelease lastRelease = componentUpdateManager.getLastPluginRelease(plugin.id);
				String lastVersion = lastRelease.version;
				String installedVersion = componentManager.getPlugin(plugin.id).getDescriptor().getVersion();
				Logging.INSTANCE.debug("Update plugin '{}' from version {} to version {}", plugin.id, installedVersion,
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
	 * Register a class annotation and its handler for processing when
	 * {@link #scanPackage(String)} is called.
	 * 
	 * @param annotationClass
	 * @param handler
	 */
	public void registerAnnotationHandler(Class<? extends Annotation> annotationClass,
			BiConsumer<Annotation, Class<?>> handler) {
		annotationHandlers.put(annotationClass, handler);
	}

	/**
	 * Single scanning loop for all registered annotations in a package. Done on the
	 * main codebase and in each component based on the declared packages.
	 * 
	 * @param packageId
	 * @return all annotations found with the corresponding class
	 * @throws KlabException
	 */
	public List<Pair<Annotation, Class<?>>> scanPackage(String packageId) throws KlabException {

		List<Pair<Annotation, Class<?>>> ret = new ArrayList<>();

		ClassPathScanningCandidateComponentProvider provider = new ClassPathScanningCandidateComponentProvider(false);
		for (Class<? extends Annotation> ah : annotationHandlers.keySet()) {
			provider.addIncludeFilter(new AnnotationTypeFilter(ah));
		}

		Set<BeanDefinition> beans = provider.findCandidateComponents(packageId);
		for (BeanDefinition bd : beans) {
			for (Class<? extends Annotation> ah : annotationHandlers.keySet()) {
				try {
					Class<?> cls = Class.forName(bd.getBeanClassName());
					Annotation annotation = cls.getAnnotation(ah);
					if (annotation != null) {
						annotationHandlers.get(ah).accept(annotation, ah);
						ret.add(new Pair<>(annotation, cls));
					}
				} catch (ClassNotFoundException e) {
					Logging.INSTANCE.error(e);
					continue;
				}
			}
		}

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
	 * Non-API Save the properties after making changes from outside configuration.
	 * Should be used only internally, or removed in favor of a painful setting API.
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
			throw new KlabIOException(e);
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
	 * Applies the standard k.LAB property pattern "klab.{service}.{property}" and
	 * retrieves the correspondent property.
	 * 
	 * @param service
	 * @param property
	 * @return
	 */
	public String getServiceProperty(String service, String property) {
		return getProperty("klab." + service + "." + property, null);
	}

	/**
	 * Applies the standard k.LAB property pattern "klab.{service}.{property}" and
	 * retrieves the correspondent property.
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
		File ret = new File(getProperties().getProperty(KLAB_EXPORT_PATH, dataPath + File.separator + "export"));
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
		return System.getProperty("mmap") == null
				&& properties.getProperty(KLAB_USE_IN_MEMORY_STORAGE, "true").equals("true");
	}

	public File getExportFile(String export) {
		if (MiscUtilities.isRelativePath(export)) {
			return new File(getDefaultExportDirectory() + File.separator + export);
		}
		return new File(export);
	}

	public boolean forceResourcesOnline() {
		return System.getProperty("forceResourcesOnline") != null;
	}

	public File getTemporaryDataDirectory() {
		return new File(
				getProperties().getProperty(KLAB_TEMPORARY_DATA_DIRECTORY, System.getProperty("java.io.tmpdir")));
	}

	/**
	 * Return a new directory in the temporary area
	 * 
	 * @param directoryName
	 * @return
	 */
	public File getScratchDataDirectory(String directoryPrefix) {
		File ret = new File(getTemporaryDataDirectory() + File.separator + directoryPrefix + NameGenerator.shortUUID());
		ret.mkdirs();
		return ret;
	}

	public boolean synchronousDataflow() {
		return System.getProperty("synchronous") != null;
	}

	public boolean isEchoEnabled() {
		return !"true".equals(getProperty(Configuration.KLAB_DISABLE_CONSOLE_ECHO, "false"));
	}

	/**
	 * Find a file correspondent to the passed argument. If the string encodes a
	 * full path, just return the correspondent file; otherwise explore any
	 * configured filepath. In all case only return non-null if the file exists and
	 * is readable.
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

	public URL getLocalComponentRepositoryURL() {
		try {
			return getDataPath("components").toURI().toURL();
		} catch (MalformedURLException e) {
			throw new KInternalErrorException(e);
		}
	}
}
