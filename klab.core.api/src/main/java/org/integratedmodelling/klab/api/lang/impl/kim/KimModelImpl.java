package org.integratedmodelling.klab.api.lang.impl.kim;

import org.integratedmodelling.klab.api.collections.Literal;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.Artifact;
import org.integratedmodelling.klab.api.knowledge.Artifact.Type;
import org.integratedmodelling.klab.api.lang.Contextualizable;
import org.integratedmodelling.klab.api.lang.kim.KimConcept;
import org.integratedmodelling.klab.api.lang.kim.KimModel;
import org.integratedmodelling.klab.api.lang.kim.KimObservable;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;

public class KimModelImpl extends KimStatementImpl implements KimModel {

	@Serial
	private static final long serialVersionUID = -6068429551009652469L;

	private List<KimObservable> dependencies = new ArrayList<>();
	private List<KimObservable> observables = new ArrayList<>();
	private Type type;
	private List<String> resourceUrns = new ArrayList<>();
	private boolean learningModel;
	private String urn;
	private List<Contextualizable> contextualization = new ArrayList<>();
	private String docstring;
	private String projectName;
	private boolean inactive;
	private Geometry coverage;

	@Override
	public boolean isInactive() {
		return inactive;
	}

	public void setInactive(boolean inactive) {
		this.inactive = inactive;
	}
	@Override
	public List<KimObservable> getDependencies() {
		return this.dependencies;
	}

	@Override
	public List<KimObservable> getObservables() {
		return this.observables;
	}

	@Override
	public Artifact.Type getType() {
		return this.type;
	}

	@Override
	public List<String> getResourceUrns() {
		return this.resourceUrns;
	}

	@Override
	public boolean isLearningModel() {
		return this.learningModel;
	}

	@Override
	public String getUrn() {
		return this.urn;
	}

	@Override
	public List<Contextualizable> getContextualization() {
		return this.contextualization;
	}

	@Override
	public String getDocstring() {
		return this.docstring;
	}

	public void setDependencies(List<KimObservable> dependencies) {
		this.dependencies = dependencies;
	}

	public void setObservables(List<KimObservable> observables) {
		this.observables = observables;
	}

	public void setType(Type type) {
		this.type = type;
	}

	public void setResourceUrns(List<String> resourceUrns) {
		this.resourceUrns = resourceUrns;
	}

	public void setLearningModel(boolean learningModel) {
		this.learningModel = learningModel;
	}

	public void setUrn(String urn) {
		this.urn = urn;
	}

	public void setContextualization(List<Contextualizable> contextualization) {
		this.contextualization = contextualization;
	}

	public void setDocstring(String docstring) {
		this.docstring = docstring;
	}

	@Override
	public String getProjectName() {
		return projectName;
	}

	public void setProjectName(String projectName) {
		this.projectName = projectName;
	}

	@Override
	public Geometry getCoverage() {
		return coverage;
	}

	public void setCoverage(Geometry coverage) {
		this.coverage = coverage;
	}

	@Override
	public void visit(KlabStatementVisitor visitor) {

	}
}
