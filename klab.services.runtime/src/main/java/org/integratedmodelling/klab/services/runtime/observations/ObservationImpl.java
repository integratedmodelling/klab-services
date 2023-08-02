package org.integratedmodelling.klab.services.runtime.observations;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import org.integratedmodelling.klab.api.data.Metadata;
import org.integratedmodelling.klab.api.data.Version;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.geometry.Locator;
import org.integratedmodelling.klab.api.identities.Identity;
import org.integratedmodelling.klab.api.knowledge.Artifact;
import org.integratedmodelling.klab.api.knowledge.Concept;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.observation.DirectObservation;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.observation.scale.Scale;
import org.integratedmodelling.klab.api.knowledge.observation.scale.space.Space;
import org.integratedmodelling.klab.api.knowledge.observation.scale.time.Time;
import org.integratedmodelling.klab.api.lang.Annotation;
import org.integratedmodelling.klab.api.provenance.Provenance;
import org.integratedmodelling.klab.api.scope.ContextScope;

public class ObservationImpl implements Observation {

	private static final long serialVersionUID = 8993700853991252827L;

	transient ContextScope scope;

	@Override
	public Geometry getGeometry() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Metadata getMetadata() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String getUrn() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Collection<Artifact> collect(Concept concept) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Artifact trace(Concept role, DirectObservation roleContext) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Collection<Artifact> getChildArtifacts() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Collection<Artifact> collect(Concept role, DirectObservation roleContext) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public int groupSize() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public Provenance getProvenance() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Type getType() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void release() {
		// TODO Auto-generated method stub

	}

	@Override
	public boolean is(Class<?> cls) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public <T> T as(Class<?> cls) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public boolean isArchetype() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public long getLastUpdate() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public boolean hasChangedDuring(Time time) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public String getId() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public long getTimestamp() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public boolean isEmpty() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public Iterator<Artifact> iterator() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Observable getObservable() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public ContextScope getScope() {
		// TODO Auto-generated method stub
		return this.scope;
	}

	@Override
	public Identity getObserver() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Scale getScale() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Observation at(Locator locator) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public DirectObservation getContext() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public boolean isSpatiallyDistributed() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean isTemporallyDistributed() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean isTemporal() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean isSpatial() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public Space getSpace() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public boolean isDynamic() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public long getCreationTime() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public long getExitTime() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public Version getVersion() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public List<Annotation> getAnnotations() {
		// TODO Auto-generated method stub
		return null;
	}

}
