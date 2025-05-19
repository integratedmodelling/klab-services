package org.integratedmodelling.klab.api.services.resources.impl;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.data.Metadata;
import org.integratedmodelling.klab.api.data.Version;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.Artifact;
import org.integratedmodelling.klab.api.knowledge.Codelist;
import org.integratedmodelling.klab.api.knowledge.Resource;
import org.integratedmodelling.klab.api.services.runtime.Notification;

public class ResourceBuilderImpl implements Resource.Builder {

    private Metadata metadata = Metadata.create();
    private Parameters<String> parameters = Parameters.create();
    private Geometry geometry;
    private String localPath;
    private List<String> resourcePaths = new ArrayList<>();
    private List<Resource> history = new ArrayList<>();
    private List<Notification> notifications = new ArrayList<>();
    private List<Resource.Attribute> attributes = new ArrayList<>();
    private List<Resource.Attribute> inputs = new ArrayList<>();
    private List<Resource.Attribute> outputs = new ArrayList<>();
    private long resourceTimestamp = System.currentTimeMillis();
    private Version resourceVersion;
    private boolean errors = false;
    private String adapterType;
    private Artifact.Type type;
    private String projectName;
    private String localName;
    private List<Codelist> codelists = new ArrayList<>();

    // for importers
    private String resourceId;
    private List<File> importedFiles = new ArrayList<>();

    private String urn;

    public ResourceBuilderImpl(String urn) {
        this.urn = urn;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Resource build() {

        ResourceImpl ret = new ResourceImpl();
        // TODO!
        //		ret.urn = urn;
        //		ret.parameters.putAll(this.parameters);
        //		ret.metadata.putAll(metadata);
        //		ret.geometry = this.geometry;
        //		ret.outputs.addAll(this.outputs);
        //		ret.notifications.addAll(this.notifications);
        //		ret.history.addAll(this.history);
        //		ret.resourceTimestamp = this.resourceTimestamp;
        //		ret.version = this.resourceVersion;
        //		ret.adapterType = this.adapterType;
        //		ret.localPath = this.localPath;
        //		ret.localPaths.addAll(resourcePaths);
        //		ret.type = type;
        //		ret.projectName = this.projectName;
        //		ret.localName = this.localName;
        //		ret.spatialExtent = this.spatialExtent;
        //		ret.attributes.addAll(this.attributes);
        //		ret.inputs.addAll(this.dependencies);
        //		ret.categorizables.addAll(this.categorizables);
        //		if (this.requiredUrns != null) {
        //			ret.dependencies.addAll(this.requiredUrns);
        //		}
        //		for (Codelist cl : this.codelists) {
        //		    File clfile = getLocalFile("code_" + cl.getId() + ".json");
        //		    JsonUtils.save(cl, clfile);
        //		    ret.codelists.add(cl.getId());
        //		}
        return ret;
    }

    //	private File getLocalFile(String filename) {
    //	    if (this.projectName != null && this.localPath != null) {
    //	        IProject project = Resources.INSTANCE.getProject(this.projectName);
    //	        if (project != null) {
    //	            File ret = new File(project.getRoot().getParentFile() + File.separator + this.localPath);
    //	            ret.mkdirs();
    //	            return new File(ret + File.separator + filename);
    //	        }
    //	    }
    //	    return null;
    //	}

    /**
     * {@inheritDoc}
     */
    @Override
    public Resource.Builder withMetadata(String key, Object value) {
        metadata.put(key, value);
        return this;
    }

    //	@Override
    //	public Resource.Builder withDependency(String urn) {
    //		requiredUrns.add(urn);
    //		return this;
    //	}

    /**
     * {@inheritDoc}
     */
    @Override
    public Resource.Builder withParameter(String key, Object value) {
        parameters.put(key, value);
        return this;
    }

    @Override
    public Resource.Builder withLocalPath(String localPath) {
        this.localPath = localPath;
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Resource.Builder withResourceVersion(Version v) {
        this.resourceVersion = v;
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Resource.Builder withResourceTimestamp(long timestamp) {
        this.resourceTimestamp = timestamp;
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Resource.Builder addHistory(Resource notification) {
        this.history.add(notification);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Resource.Builder withGeometry(Geometry s) {
        this.geometry = s;
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Resource.Builder withAdapterType(String string) {
        this.adapterType = string;
        return this;
    }

    @Override
    public Resource.Builder addLocalResourcePath(String path) {
        this.resourcePaths.add(path);
        return this;
    }

    @Override
    public Resource.Builder withNotifications(Notification... notifications) {
        if (notifications != null) {
            this.notifications.addAll(Arrays.asList(notifications));
        }
        return this;
    }

    @Override
    public Resource.Builder withParameters(Parameters<String> parameters) {
        this.parameters.putAll(parameters);
        return this;
    }

    @Override
    public Resource.Builder withType(Artifact.Type type) {
        this.type = type;
        return this;
    }

    @Override
    public Resource.Builder withProjectName(String name) {
        this.projectName = name;
        return this;
    }

    @Override
    public Collection<File> getImportedFiles() {
        return importedFiles;
    }

    @Override
    public String getResourceId() {
        return resourceId;
    }

    @Override
    public void setResourceId(String identifier) {
        this.resourceId = identifier;
    }

    @Override
    public void addImportedFile(File file) {
        this.importedFiles.add(file);
    }

    @Override
    public Resource.Builder withLocalName(String localName) {
        this.localName = localName;
        return this;
    }

    //	@Override
    //	public Resource.Builder withSpatialExtent(SpatialExtent extent) {
    //		this.spatialExtent = extent;
    //		return this;
    //	}

    @Override
    public Resource.Builder withAttribute(String name, Artifact.Type type, boolean key, boolean optional) {
        AttributeImpl attribute = new AttributeImpl();
        attribute.setName(name);
        attribute.setType(type);
        attribute.setKey(key);
        attribute.setOptional(optional);
        // TODO example
        this.attributes.add(attribute);
        return this;
    }

    @Override
    public Resource.Builder withOutput(String name, Artifact.Type type) {
        AttributeImpl attribute = new AttributeImpl();
        attribute.setName(name);
        attribute.setType(type);
        attribute.setKey(false);
        attribute.setOptional(true);
        this.outputs.add(attribute);
        return this;
    }

    @Override
    public Resource.Builder withInput(String name, Artifact.Type type, boolean key, boolean optional) {
        AttributeImpl attribute = new AttributeImpl();
        attribute.setName(name);
        attribute.setType(type);
        attribute.setKey(key);
        attribute.setOptional(optional);
        this.inputs.add(attribute);
        return this;
    }

    @Override
    public Resource.Builder addCodeList(Codelist codelist) {
        this.codelists.add(codelist);
        return this;
    }

    @Override
    public String getUrn() {
        return this.urn;
    }

    public Parameters<String> getParameters() {
        return this.parameters;
    }

}
