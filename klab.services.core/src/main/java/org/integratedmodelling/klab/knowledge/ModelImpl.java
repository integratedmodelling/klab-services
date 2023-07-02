package org.integratedmodelling.klab.knowledge;

import java.util.ArrayList;
import java.util.List;

import org.integratedmodelling.klab.api.data.Metadata;
import org.integratedmodelling.klab.api.data.Version;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.Artifact.Type;
import org.integratedmodelling.klab.api.knowledge.DescriptionType;
import org.integratedmodelling.klab.api.knowledge.Model;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.lang.Annotation;
import org.integratedmodelling.klab.api.lang.Contextualizable;

public class ModelImpl implements Model {

	private static final long serialVersionUID = -303420101007056751L;
	private String urn;
	private Version version;
	private Metadata metadata = Metadata.create();
	private List<Annotation> annotations = new ArrayList<>();
	private String namespace;
	private Type type;
	private List<Observable> observables = new ArrayList<>();
	private List<Observable> dependencies = new ArrayList<>();
	private Geometry coverage;
	private List<Contextualizable> computation = new ArrayList<>();
	private DescriptionType descriptionType;

	@Override
	public String getUrn() {
		return this.urn;
	}

	@Override
	public Version getVersion() {
		return this.version;
	}

	@Override
	public Metadata getMetadata() {
		return this.metadata;
	}

	@Override
	public List<Annotation> getAnnotations() {
		return this.annotations;
	}

	@Override
	public String getNamespace() {
		return this.namespace;
	}

	@Override
	public Type getType() {
		return this.type;
	}

	@Override
	public List<Observable> getObservables() {
		return this.observables;
	}

	@Override
	public List<Observable> getDependencies() {
		return this.dependencies;
	}

	@Override
	public Geometry getCoverage() {
		return this.coverage;
	}

	public void setUrn(String urn) {
		this.urn = urn;
	}

	public void setVersion(Version version) {
		this.version = version;
	}

	public void setMetadata(Metadata metadata) {
		this.metadata = metadata;
	}

	public void setAnnotations(List<Annotation> annotations) {
		this.annotations = annotations;
	}

	public void setNamespace(String namespace) {
		this.namespace = namespace;
	}

	public void setType(Type type) {
		this.type = type;
	}

	public void setObservables(List<Observable> observables) {
		this.observables = observables;
	}

	public void setDependencies(List<Observable> dependencies) {
		this.dependencies = dependencies;
	}

	public void setCoverage(Geometry coverage) {
		this.coverage = coverage;
	}

	@Override
	public List<Contextualizable> getComputation() {
		return computation;
	}

	@Override
	public DescriptionType getDescriptionType() {
		return this.descriptionType;
	}

	public void setComputation(List<Contextualizable> computation) {
		this.computation = computation;
	}

	public void setDescriptionType(DescriptionType descriptionType) {
		this.descriptionType = descriptionType;
	}

}
