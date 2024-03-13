package org.integratedmodelling.engine.client.distribution;


import org.integratedmodelling.klab.api.configuration.Configuration;
import org.integratedmodelling.klab.api.exceptions.KlabIllegalStateException;
import org.integratedmodelling.klab.api.utils.Utils;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

public enum JreModel {

    INSTANCE;

    public static final String JRE_FOLDER_NAME = "jre21";
    private final String currentJavaExecutable;

    File jreDirectory;
    boolean haveSpecifiedJre;
    // boolean haveJavaHome;
    boolean haveKlabSetting;
    boolean isPublicJavaOk;
    Set<Action> possibleActions = new HashSet<>();

    enum Action {
        INSTALL_LOCAL, SPECIFY_PUBLIC_JRE, EXIT_AND_FIX_PUBLIC_INSTALLATION
    }

    private JreModel() {
        // this works in non-embedded contexts TODO validate
        this.currentJavaExecutable = ProcessHandle.current()
                                .info()
                                .command()
                                .or(() -> null).get();

        refresh();
    }

    public void refresh() {

        Properties properties = Configuration.INSTANCE.getProperties();
        // try to find klab settings
        haveKlabSetting = properties.getProperty(Configuration.JREDIR_PROPERTY) != null;
        // if is a refresh with a jre setted, we don't want to change it
        if (jreDirectory == null) {
            // is not a refresh
            jreDirectory = new File(
                    properties.getProperty(Configuration.JREDIR_PROPERTY, getJREBinPath().getPath()));
        }
        haveSpecifiedJre = jreDirectory.exists() && jreDirectory.isDirectory();
        if (haveSpecifiedJre) {
            // if jre is detected, we don't need to check the public java
            isPublicJavaOk = true;
        } else {
            isPublicJavaOk = false;
            // try to find a solution using the $JAVA_HOME or the java.home system property
            String javaHome = System.getenv("JAVA_HOME");
            if (javaHome == null) {
                javaHome = System.getProperty("java.home");
            }
            if (javaHome != null) {
                // try to find bin directory
                // before if is JRE...
                String binPath = javaHome + File.separator + "bin";
                isPublicJavaOk = new File(binPath).isDirectory();
                if (!isPublicJavaOk) {
                    // ...else if is JDK, we search jre/bin directory
                    binPath = javaHome + File.separator + "jre" + File.separator + "bin";
                    isPublicJavaOk = new File(binPath).isDirectory();
                }
                if (!haveSpecifiedJre) {
                    jreDirectory = new File(binPath);
                }
            }
        }
    }

    public String concernMessage() {
	    /*
		String ret = null;

		if (haveKlabSetting && !haveSpecifiedJre) {
			ret = "Your k.LAB settings specify a JRE that does not seem to exist.";
		} else if (haveJavaHome && !isPublicJavaOk) {
			ret = "Your java executable does not seem to be standard distribution.";
		} else if (jreDirectory == null || !haveJavaHome && !haveSpecifiedJre) {
			ret = "You don't seem to have Java installed.";
		}

		return ret;
		*/
        return isPublicJavaOk ? null : "Download OpenJDK JRE";
    }

    public String getJavaExecutable() {
        if (jreDirectory == null && currentJavaExecutable != null) {
            return currentJavaExecutable;
        } else if (jreDirectory != null) {
            return jreDirectory + File.separator + "java" + (Utils.OS.get() == Utils.OS.WIN ? ".exe" : "");
        }
        throw new KlabIllegalStateException("Cannot find a path to a valid JRE executable");
    }

    public void connectLocalJre() {
        this.jreDirectory = getJREBinPath();
        refresh();
    }

    public File getJREPath() {
        return new File(Configuration.INSTANCE.getDataPath() + File.separator + JRE_FOLDER_NAME);
    }

    public File getJREBinPath() {
        return new File(getJREPath() + File.separator + "bin");
    }
}