package org.integratedmodelling.klab.api.services.resources.impl;

import java.util.ArrayList;
import java.util.List;

import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.data.Metadata;
import org.integratedmodelling.klab.api.data.Version;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.Artifact;
import org.integratedmodelling.klab.api.knowledge.Resource;
import org.integratedmodelling.klab.api.lang.Annotation;
import org.integratedmodelling.klab.api.services.runtime.Notification;

public class ResourceImpl implements Resource {

	private static final long serialVersionUID = -4380402174665342610L;

	private String urn;
	private Version version;
	private String adapterType;
	private String localPath;
	private Geometry geometry;
	// only set in local resources
	private String projectName;
	// only in local resources, the short name for k.IM
	private String localName;
	private Artifact.Type type;
	private long timestamp;
	private Metadata metadata = Metadata.create();
	private Parameters<String> parameters = Parameters.create();
	private List<String> localPaths = new ArrayList<>();
	private List<Resource> history = new ArrayList<>();
	private List<Notification> notifications = new ArrayList<>();
	private List<Attribute> attributes = new ArrayList<>();
//	private SpatialExtent spatialExtent;
	private List<Attribute> inputs = null;
	private List<Attribute> outputs = null;
	private List<String> categorizables = new ArrayList<>();
	private List<String> codelists = new ArrayList<>();

	public List<Attribute> getOutputs() {
		return outputs;
	}

	public void setOutputs(List<Attribute> outputs) {
		this.outputs = outputs;
	}

//	private Map<String, String> exportFormats = new LinkedHashMap<>();
	private String localProjectName;
	private List<Annotation> annotations;

	public ResourceImpl() {
	}

	public ResourceImpl(ResourceImpl other) {
		this.urn = other.urn;
		this.version = other.version;
		this.adapterType = other.adapterType;
		this.localPath = other.localPath;
		this.geometry = other.geometry;
		this.projectName = other.projectName;
		this.localName = other.localName;
		this.type = other.type;
		this.timestamp = other.timestamp;
		this.metadata.putAll(other.metadata);
		this.parameters.putAll(other.parameters);
		this.localPaths.addAll(other.localPaths);
		this.history.addAll(other.history);
//		this.spatialExtent = other.spatialExtent;
		this.notifications.addAll(other.notifications);
		this.attributes.addAll(other.attributes);
		this.inputs = other.inputs == null ? null : new ArrayList<>(other.inputs);
		this.outputs = other.outputs == null ? null : new ArrayList<>(other.outputs);
//		this.exportFormats.putAll(other.exportFormats);
		this.categorizables.addAll(other.categorizables);
		this.codelists.addAll(other.codelists);
	}

	public String getUrn() {
		return urn;
	}

	public void setUrn(String urn) {
		this.urn = urn;
	}

	public Version getVersion() {
		return version;
	}

	public void setVersion(Version version) {
		this.version = version;
	}

	public String getAdapterType() {
		return adapterType;
	}

	public void setAdapterType(String adapterType) {
		this.adapterType = adapterType;
	}

	public String getLocalPath() {
		return localPath;
	}

	public void setLocalPath(String localPath) {
		this.localPath = localPath;
	}

	public Geometry getGeometry() {
		return geometry;
	}

	public void setGeometry(Geometry geometry) {
		this.geometry = geometry;
	}

	public Artifact.Type getType() {
		return type;
	}

	public void setType(Artifact.Type type) {
		this.type = type;
	}

	public long getTimestamp() {
		return timestamp;
	}

	public void setTimestamp(long resourceTimestamp) {
		this.timestamp = resourceTimestamp;
	}

	public Metadata getMetadata() {
		return metadata;
	}

	public void setMetadata(Metadata metadata) {
		this.metadata = metadata;
	}

	public Parameters<String> getParameters() {
		return parameters;
	}

	public void setParameters(Parameters<String> parameters) {
		this.parameters = parameters;
	}

	public List<String> getLocalPaths() {
		return localPaths;
	}

	public void setLocalPaths(List<String> localPaths) {
		this.localPaths = localPaths;
	}

	public List<Resource> getHistory() {
		return history;
	}

	public void setHistory(List<Resource> history) {
		this.history = history;
	}

	public List<Notification> getNotifications() {
		return notifications;
	}

	public void setNotifications(List<Notification> notifications) {
		this.notifications = notifications;
	}

	public String getProjectName() {
		return projectName;
	}

	public void setProjectName(String projectName) {
		this.projectName = projectName;
	}

	public String getLocalName() {
		return localName;
	}

	public void setLocalName(String localName) {
		this.localName = localName;
	}

//	public SpatialExtent getSpatialExtent() {
//		return spatialExtent;
//	}
//
//	public void setSpatialExtent(SpatialExtent spatialExtent) {
//		this.spatialExtent = spatialExtent;
//	}

	public List<Attribute> getAttributes() {
		return attributes;
	}

	public void setAttributes(List<Attribute> attributes) {
		this.attributes = attributes;
	}

	public List<Attribute> getInputs() {
		return inputs;
	}

	public void setInputs(List<Attribute> dependencies) {
		this.inputs = dependencies;
	}

//	public Map<String, String> getExportFormats() {
//		return exportFormats;
//	}
//
//	public void setExportFormats(Map<String, String> exportFormats) {
//		this.exportFormats = exportFormats;
//	}

	public List<String> getCategorizables() {
		return categorizables;
	}

	public void setCategorizables(List<String> categorizables) {
		this.categorizables = categorizables;
	}

	public List<String> getCodelists() {
		return codelists;
	}

	public void setCodelists(List<String> codelists) {
		this.codelists = codelists;
	}

	@Override
	public List<Annotation> getAnnotations() {
		return annotations;
	}

	@Override
	public String getLocalProjectName() {
		return localProjectName;
	}

	public String toString() {
		return "(R) " + urn;
	}
}
