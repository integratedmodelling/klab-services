//package org.integratedmodelling.common.knowledge;
//
//import org.integratedmodelling.klab.api.knowledge.Instance;
//import org.integratedmodelling.klab.api.knowledge.Observable;
//import org.integratedmodelling.klab.api.knowledge.observation.scale.Scale;
//import org.integratedmodelling.klab.api.lang.impl.kim.KimStatementImpl;
//
//import java.util.ArrayList;
//import java.util.List;
//import java.util.Objects;
//
//public class InstanceImpl extends KimStatementImpl implements Instance {
//
//	private String urn;
//	private String namespace;
//	private Observable observable;
//	private List<KimInstance> children = new ArrayList<>();
//	private List<Observable> states = new ArrayList<>();
//	private Scale scale;
//	private Scope scope;
//
//	@Override
//	public String getUrn() {
//		return this.urn;
//	}
//
//	@Override
//	public Observable getObservable() {
//		return this.observable;
//	}
//
//	@Override
//	public List<Instance> getChildren() {
//		return this.children;
//	}
//
//	@Override
//	public List<Observable> getStates() {
//		return this.states;
//	}
//
//	@Override
//	public Scale getScale() {
//		return this.scale;
//	}
//
//	public void setUrn(String urn) {
//		this.urn = urn;
//	}
//
//	public void setObservable(Observable observable) {
//		this.observable = observable;
//	}
//
//	public void setChildren(List<Instance> children) {
//		this.children = children;
//	}
//
//	public void setStates(List<Observable> states) {
//		this.states = states;
//	}
//
//	public void setScale(Scale scale) {
//		this.scale = scale;
//	}
//
//	@Override
//	public String toString() {
//		return "(I) " + urn;
//	}
//
//	@Override
//	public String getNamespace() {
//		return namespace;
//	}
//
//	public void setNamespace(String namespace) {
//		this.namespace = namespace;
//	}
//
//	@Override
//	public int hashCode() {
//		return Objects.hash(urn);
//	}
//
//	@Override
//	public boolean equals(Object obj) {
//		if (this == obj)
//			return true;
//		if (obj == null)
//			return false;
//		if (getClass() != obj.getClass())
//			return false;
//		InstanceImpl other = (InstanceImpl) obj;
//		return Objects.equals(urn, other.urn);
//	}
//
//	@Override
//	public Scope getScope() {
//		return scope;
//	}
//
//	@Override
//	public void visit(KlabStatementVisitor visitor) {
//
//	}
//
//	public void setScope(Scope scope) {
//		this.scope = scope;
//	}
//}
