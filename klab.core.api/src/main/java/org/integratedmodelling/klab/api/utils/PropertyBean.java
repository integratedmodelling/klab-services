package org.integratedmodelling.klab.api.utils;

import org.integratedmodelling.klab.api.exceptions.KlabIOException;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.Properties;

/**
 * Simple object that can load a properties file and save the modified properties back to the same file. Will
 * behave nicely if the passed file is null.
 */
public class PropertyBean {

    private File propertiesFile;
    private Properties properties;

    public PropertyBean(File file) {
        propertiesFile = file;
    }

    public Properties getProperties() {
        if (properties == null) {
            properties = new Properties();
            if (propertiesFile != null && propertiesFile.exists()) {
                try (var input = new FileInputStream(propertiesFile)) {
                    properties.load(input);
                } catch (IOException e) {
                    throw new KlabIOException(e);
                }
            }
        }
        return properties;
    }

    public void setProperty(String propertyName, String propertyValue) {
        getProperties().setProperty(propertyName, propertyValue);
    }

    public void setProperty(String propertyName, String propertyValue, String defaultValue) {
        getProperties().setProperty(propertyName, propertyValue == null ? defaultValue : propertyValue);
    }

    public String getProperty(String propertyName) {
        return getProperties().getProperty(propertyName);
    }

    public String getProperty(String propertyName, String defaultValue) {
        return getProperties().getProperty(propertyName, defaultValue);
    }

    /**
     * Change the property file and save to that. The change of output file remains in the object.
     *
     * @param newPropertyFile
     */
    public void saveProperties(File newPropertyFile) {
        this.propertiesFile = newPropertyFile;
        saveProperties();
    }

    public void saveProperties() {
        if (propertiesFile != null) {
            try (var output = new FileOutputStream(propertiesFile)) {
                getProperties().store(output, "Created by k.LAB on " + Instant.now());
            } catch (IOException e) {
                throw new KlabIOException(e);
            }
        }
    }
}
