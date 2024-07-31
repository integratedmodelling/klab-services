//package org.integratedmodelling.klab.api.knowledge.observation.impl;
//
//import java.util.Collection;
//
//import org.integratedmodelling.klab.api.collections.Parameters;
//import org.integratedmodelling.klab.api.geometry.Locator;
//import org.integratedmodelling.klab.api.identities.Identity;
//import org.integratedmodelling.klab.api.knowledge.Artifact;
//import org.integratedmodelling.klab.api.knowledge.Observable;
//import org.integratedmodelling.klab.api.knowledge.observation.DirectObservation;
//import org.integratedmodelling.klab.api.knowledge.observation.ObservationGroup;
//import org.integratedmodelling.klab.api.knowledge.observation.Pattern;
//import org.integratedmodelling.klab.api.scope.ContextScope;
//
//public class ObservationGroupImpl extends ObservationImpl implements ObservationGroup {
//
//	private static final long serialVersionUID = 2619794549383879630L;
//
//	public ObservationGroupImpl() {
//	}
//
//	public ObservationGroupImpl(Observable observable, String id, ContextScope scope) {
//		super(observable, id, scope);
//	}
//
//	@Override
//	public String getName() {
//		// TODO Auto-generated method stub
//		return null;
//	}
//
////	@Override
////	public Observation getChildObservation(Observable observable) {
////		// TODO Auto-generated method stub
////		return null;
////	}
////
////	@Override
////	public Collection<State> getStates() {
////		// TODO Auto-generated method stub
////		return null;
////	}
//
//	@Override
//	public DirectObservation at(Locator locator) {
//		// TODO Auto-generated method stub
//		return null;
//	}
//
//	@Override
//	public Pattern getOriginatingPattern() {
//		// TODO Auto-generated method stub
//		return null;
//	}
//
//	@Override
//	public boolean isActive() {
//		// TODO Auto-generated method stub
//		return false;
//	}
//
//	@Override
//	public Identity.Type getIdentityType() {
//		// TODO Auto-generated method stub
//		return null;
//	}
//
//	@Override
//	public Identity getParentIdentity() {
//		// TODO Auto-generated method stub
//		return null;
//	}
//
//	@Override
//	public boolean is(org.integratedmodelling.klab.api.identities.Identity.Type type) {
//		// TODO Auto-generated method stub
//		return false;
//	}
//
//	@Override
//	public Parameters<String> getData() {
//		// TODO Auto-generated method stub
//		return null;
//	}
//
//	@Override
//	public <T extends Identity> T getParentIdentity(Class<T> type) {
//		// TODO Auto-generated method stub
//		return null;
//	}
//
//	@Override
//	public <T extends Artifact> Collection<T> getChildren(Class<T> cls) {
//		// TODO Auto-generated method stub
//		return null;
//	}
//
//	@Override
//	public Artifact getGroupMember(int i) {
//		// TODO Auto-generated method stub
//		return null;
//	}
//
//}
