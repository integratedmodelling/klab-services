package org.integratedmodelling.klab.resources;

import org.integratedmodelling.klab.api.exceptions.KIOException;
import org.integratedmodelling.klab.api.knowledge.organization.ProjectStorage;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;

public class FileProjectStorage implements ProjectStorage {

    File rootFolder;

    @Override
    public URL getUrl() {
        try {
            return rootFolder.toURI().toURL();
        } catch (MalformedURLException e) {
            throw new KIOException(e);
        }
    }

    @Override
    public List<URL> listResources(ResourceType... types) {
        return null;
    }

    @Override
    public URL create(String resourceId, ResourceType resourceType) {
        return null;
    }
}
