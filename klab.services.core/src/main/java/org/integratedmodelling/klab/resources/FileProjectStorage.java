package org.integratedmodelling.klab.resources;

import org.integratedmodelling.klab.api.exceptions.KlabIOException;
import org.integratedmodelling.klab.api.knowledge.organization.ProjectStorage;
import org.integratedmodelling.klab.api.utils.Utils;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class FileProjectStorage implements ProjectStorage {

    File rootFolder;

    public FileProjectStorage(File rootFolder) {
        this.rootFolder = rootFolder;
    }

    @Override
    public URL getUrl() {
        try {
            return rootFolder.toURI().toURL();
        } catch (MalformedURLException e) {
            throw new KlabIOException(e);
        }
    }

    @Override
    public List<URL> listResources(ResourceType... types) {
        List<URL> ret = new ArrayList<>();
        for (var type : types) {
            switch (type) {
                case ONTOLOGY -> {
                    collectResources("kvw", "src", true,ret);
                }
                case MODEL_NAMESPACE -> {
                    collectResources("kim", "src", true, ret);
                }
                case MANIFEST -> {
                    collectResources("manifest.json", "META-INF", false, ret);
                }
                case DOCUMENTATION_NAMESPACE -> {
                    collectResources("json", "docs", false, ret);
                }
                case STRATEGY -> {
                    collectResources("obs", "strategies", false, ret);
                }
                case BEHAVIOR -> {
                    collectResources("kactors", "src", false, ret);
                }
                case APPLICATION -> {
                    collectResources("kactors", "apps", false, ret);
                }
                case SCRIPT -> {
                    collectResources("kactors", "scripts", false, ret);
                }
                case TESTCASE -> {
                    collectResources("kactors", "tests", false, ret);
                }
                case BEHAVIOR_COMPONENT -> {
                    collectResources("kactors", "components", false, ret);
                }
                case RESOURCE -> {
                    collectResources("resource.json", "resources", false, ret);
                }
                case RESOURCE_ASSET -> {
                    // ehm - requires the resource name, then pass * as extension
                }
            }
        }
        return ret;
    }

    /**
     * Redefine to implement different storage strategies.
     *
     * @param extension extension w/o dot, or * for all files
     * @param sourceFolder will be recursed into unless recurse is false
     * @param recurse
     * @param resultUrls
     */
    protected void collectResources(String extension, String sourceFolder, boolean recurse, List<URL> resultUrls) {

        File root = new File(this.rootFolder + File.separator + sourceFolder.replaceAll("/", File.separator));
        if (root.isDirectory()) {
            scanDirectory(root, extension, recurse, resultUrls);
        }
    }

    private void scanDirectory(File root, String extension, boolean recurse, List<URL> resultUrls) {
        var files = root.listFiles();
        if (files != null) {
            for (var file : files) {
                if (file.isDirectory() && recurse) {
                    scanDirectory(file, extension, true, resultUrls);
                } else if (extension.equals("*") || extension.equals(Utils.Files.getFileExtension(file))) {
                    try {
                        resultUrls.add(file.toURI().toURL());
                    } catch (MalformedURLException e) {
                        throw new KlabIOException(e);
                    }
                }
            }
        }
    }


    @Override
    public URL create(String resourceId, ResourceType resourceType) {
        if (!rootFolder.exists()) {
            rootFolder.mkdirs();
        }
        return null;
    }

    @Override
    public boolean isReadOnly() {
        return false;
    }

    @Override
    public boolean isFilesystemBased() {
        return false;
    }
}
