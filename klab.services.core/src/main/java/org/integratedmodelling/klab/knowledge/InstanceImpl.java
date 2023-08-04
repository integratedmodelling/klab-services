package org.integratedmodelling.klab.knowledge;

import java.util.ArrayList;
import java.util.List;

import org.integratedmodelling.klab.api.data.Metadata;
import org.integratedmodelling.klab.api.data.Version;
import org.integratedmodelling.klab.api.knowledge.Instance;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.observation.scale.Scale;
import org.integratedmodelling.klab.api.lang.Annotation;

public class InstanceImpl implements Instance {

	private static final long serialVersionUID = 3290903165179045061L;

	private String urn;
	private String namespace;
	private Version version;
	private Metadata metadata = Metadata.create();
	private List<Annotation> annotations = new ArrayList<>();
	private Observable observable;
	private List<Instance> children = new ArrayList<>();
	private List<Observable> states = new ArrayList<>();
	private Scale scale;

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
	public Observable getObservable() {
		return this.observable;
	}

	@Override
	public List<Instance> getChildren() {
		return this.children;
	}

	@Override
	public List<Observable> getStates() {
		return this.states;
	}

	@Override
	public Scale getScale() {
		return this.scale;
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

	public void setObservable(Observable observable) {
		this.observable = observable;
	}

	public void setChildren(List<Instance> children) {
		this.children = children;
	}

	public void setStates(List<Observable> states) {
		this.states = states;
	}

	public void setScale(Scale scale) {
		this.scale = scale;
	}

	@Override
	public String toString() {
		return "(I) " + urn;
	}

	@Override
    public String getNamespace() {
        return namespace;
    }

    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

}
