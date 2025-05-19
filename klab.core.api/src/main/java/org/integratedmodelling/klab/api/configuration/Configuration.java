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
package org.integratedmodelling.klab.api.configuration;

import org.integratedmodelling.klab.api.engine.distribution.Distribution;
import org.integratedmodelling.klab.api.exceptions.KlabIOException;
import org.integratedmodelling.klab.api.exceptions.KlabInternalErrorException;
import org.integratedmodelling.klab.api.lang.ServiceInfo;
import org.integratedmodelling.klab.api.services.KlabService;
import org.integratedmodelling.klab.api.services.runtime.extension.Verb;
import org.integratedmodelling.klab.api.utils.Utils;
import org.integratedmodelling.klab.api.utils.Utils.OS;

import java.io.*;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.util.*;
import java.util.logging.Level;

/**
 * Basic interface for the configuration stored in klab.properties and access to the shared workspace.
 * <p>
 * TODO use a declarative approach for all properties, so that there is one place for all default
 * settings and it's possible to override any of them through global JVM settings.
 *
 * @author Ferd
 * @version $Id: $Id
 */
public enum Configuration {

    INSTANCE;


    // TODO move all these to enum and reconcile with org.integratedmodelling.klab.api.engine.distribution
    //  .Settings
    public static final String DEFAULT_PRODUCTS_BRANCH = "master";
    public static final String JREDIR_PROPERTY = "klab.directory.jre";
    public static final String KLAB_OFFLINE = "klab.offline";
    public static final String KLAB_EXPORT_PATH = "klab.export.path";
    public static final String KLAB_USE_IN_MEMORY_DATABASE = "klab.database.inmemory";
    public static final String CERTFILE_PROPERTY = "klab.certificate";

    /**
     * Absolute path of work directory. Overrides the default which is ${user.home}/THINKLAB_WORK_DIRECTORY
     */
    public static final String KLAB_DATA_DIRECTORY = "klab.data.directory";

    // configurable temp dir for (potentially very large) storage during simulation.
    public static final String KLAB_TEMPORARY_DATA_DIRECTORY = "klab.temporary.data.directory";
    /**
     * Name of work directory relative to ${user.home}. Ignored if THINKLAB_DATA_DIRECTORY_PROPERTY is
     * specified.
     */
    public static final String KLAB_WORK_DIRECTORY = "klab.work.directory";

    /**
     * Development source repository on this machine. If set, a testing {@link Distribution} will be created
     * from the Maven compiled products in it and used by the default
     * {@link org.integratedmodelling.klab.api.engine.Engine} client. Defaults to $HOME/git/klab-services.
     */
    public static final String KLAB_DEVELOPMENT_SOURCE_REPOSITORY = "klab.development.source.repository";

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
                throw new KlabIOException("cannot write to configuration directory");
            }
        }
        try (InputStream input = new FileInputStream(pFile)) {
            this.properties.load(input);
        } catch (Exception e) {
            throw new KlabIOException("cannot read configuration properties");
        }

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

        Properties p = new Properties();
        p.putAll(getProperties());
        try {
            p.store(new FileOutputStream(td), null);
        } catch (Exception e) {
            throw new KlabIOException(e);
        }

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

    public File getDataPath() {
        return dataPath;
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
    /**
     * Retrieve the file with the passed relative path, creating subdirs as required. Must use forward slash
     * as separator. File may not exist but the paths leading to it will be created.
     *
     * @param relativeFilePath
     * @return
     */
    public File getFile(String relativeFilePath) {
        File directory = getDataPath();
        String[] path = relativeFilePath.split("\\/");
        for (int i = 0; i < path.length - 1; i++) {
            directory = new File(directory + File.separator + path[i]);
            directory.mkdirs();
        }
        return new File(directory + File.separator + path[path.length - 1]);
        //        directory = new File(directory + File.separator + path[path.length - 1]);
        //        if (!directory.exists()) {
        //            try {
        //                directory.createNewFile();
        //            } catch (IOException e) {
        //                throw new KlabIOException(e);
        //            }
        //        }
        //        return directory;
    }

    /**
     * Same as {@link #getFile(String)} but if the file does not exist, create it with the content specified
     * in the second argument.
     *
     * @param relativeFilePath
     * @return
     */
    public File getFileWithTemplate(String relativeFilePath, String template) {
        File directory = getDataPath();
        String[] path = relativeFilePath.split("\\/");
        for (int i = 0; i < path.length - 1; i++) {
            directory = new File(directory + File.separator + path[i]);
            directory.mkdirs();
        }
        directory = new File(directory + File.separator + path[path.length - 1]);
        if (!directory.exists()) {
            Utils.Files.writeStringToFile(template, directory);
        }
        return directory;
    }

    public String getServiceSecret(KlabService.Type serviceType) {
        File secretFile =
                Configuration.INSTANCE.getFile("services/" + serviceType.name().toLowerCase() +
                        "/secret.key");
        if (secretFile.exists()) {
            try {
                return Files.readString(secretFile.toPath());
            } catch (IOException e) {
                return null;
            }
        }
        return null;
    }

}
