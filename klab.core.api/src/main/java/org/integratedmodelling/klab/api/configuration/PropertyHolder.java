package org.integratedmodelling.klab.api.configuration;

import org.integratedmodelling.klab.api.exceptions.KlabIOException;

import java.io.*;
import java.util.Properties;

/**
 * Adopt this to acquire an "application.property" file in the configuration path identified by
 * {@link #configurationPath()} and methods to access and persist Java properties linked to it.
 */
public interface PropertyHolder {

    public static String PROPERTY_FILENAME = "application.properties";

    Properties _properties = new Properties();

    /**
     * Return the <em>relative</em> path where the application.properties file will be located. The path will
     * be relative to {@link Configuration#getDataPath()} and should be separated by forward slashes.
     *
     * @return
     */
    String configurationPath();

    default Properties getProperties() {
        boolean isNew = _properties.isEmpty();
        if (isNew) {
            var pFile =
                    Configuration.INSTANCE.getFileWithTemplate(configurationPath() + File.separator + PROPERTY_FILENAME, "creation.time=" + System.currentTimeMillis());
            try (InputStream input = new FileInputStream(pFile)) {
                this._properties.load(input);
            } catch (Exception e) {
                throw new KlabIOException("cannot read configuration properties");
            }

        }
        return _properties;
    }

    default void saveProperties() {
        var pFile =
                Configuration.INSTANCE.getFileWithTemplate(configurationPath() + File.separator + PROPERTY_FILENAME, "creation.time=" + System.currentTimeMillis());
        Properties p = new Properties();
        p.putAll(getProperties());
        try {
            p.store(new FileOutputStream(pFile), null);
        } catch (Exception e) {
            throw new KlabIOException(e);
        }
    }
}
