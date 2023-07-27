package org.integratedmodelling.klab.runtime.scale;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.geometry.Geometry.Dimension.Type;
import org.integratedmodelling.klab.api.geometry.Locator;
import org.integratedmodelling.klab.api.geometry.impl.NDCursor;
import org.integratedmodelling.klab.api.knowledge.observation.scale.Extent;
import org.integratedmodelling.klab.api.knowledge.observation.scale.Scale;
import org.integratedmodelling.klab.api.knowledge.observation.scale.space.Space;
import org.integratedmodelling.klab.api.knowledge.observation.scale.time.Time;
import org.integratedmodelling.klab.api.lang.LogicalConnector;
import org.integratedmodelling.klab.api.services.resolver.Coverage;
import org.integratedmodelling.klab.exceptions.KlabUnimplementedException;
import org.integratedmodelling.klab.runtime.scale.space.SpaceImpl;
import org.integratedmodelling.klab.runtime.scale.time.TimeImpl;

public class ScaleImpl implements Scale {

	private static final long serialVersionUID = -4518044986262539876L;

	Extent<?>[] extents;
	long size;

	/**
	 * Internal locator class f. Uses the enclosing scale in a lazy fashion for
	 * everything and just maintains the offset[s] and the index[es] of the extend
	 * that is changing.
	 * 
	 * TODO make 2 more that consider masking in extents
	 * 
	 * @author Ferd
	 *
	 */
	abstract class ScaleLocator extends DelegatingScale {

		private static final long serialVersionUID = 797929992176158102L;
		boolean empty;
		long offset;

		abstract Scale advance();
	}

	/**
	 * For >1 extents changing at the same time.
	 * 
	 * @author Ferd
	 *
	 */
	class ScaleLocatorND extends ScaleLocator {

		private static final long serialVersionUID = 2969366247696737476L;
		NDCursor cursor;

		Scale advance() {
			return this;
		}

	}

	/**
	 * Internal locator class for the situation where a single dimension is changing
	 * and the others are locked at a specified extent.
	 * 
	 * @author Ferd
	 *
	 */
	class ScaleLocator1D extends ScaleLocator {

		private static final long serialVersionUID = -4207775306893203109L;
		long offset;
		int changingIndex;
		long[] extents;
		boolean empty;

		Scale advance() {
			return this;
		}

	}

	/**
	 * The scale iterator uses a threadlocal locator to avoid constant object
	 * instantiation.
	 * 
	 * @author Ferd
	 *
	 */
	class ScaleIterator implements Iterator<Scale> {

		ThreadLocal<ScaleLocator> locator = new ThreadLocal<>();

		ScaleIterator() {
			// TODO initialize the locator to the most appropriate of the above, pointing at
			// the first usable offset
		}

		@Override
		public boolean hasNext() {
			return !locator.get().empty;
		}

		@Override
		public Scale next() {
			return locator.get().advance();
		}

	};

	public ScaleImpl(Geometry geometry) {
		List<Extent<?>> extents = new ArrayList<>(3);
		for (Geometry.Dimension dimension : geometry.getDimensions()) {
			if (dimension.getType() == Type.SPACE) {
				extents.add(SpaceImpl.create(dimension));
			} else if (dimension.getType() == Type.TIME) {
				extents.add(TimeImpl.create(dimension));
			} else if (dimension.getType() == Type.NUMEROSITY) {
				// TODO
				throw new KlabUnimplementedException("numerosity extent");
			}
		}
		define(extents);
	}

	public ScaleImpl(List<Extent<?>> extents) {
		define(extents);
	}

	protected void define(List<Extent<?>> extents) {
		Collections.sort(extents, new Comparator<Extent<?>>() {
			// use the natural order in the dimension type enum
			@Override
			public int compare(Extent<?> o1, Extent<?> o2) {
				return o1.getType().compareTo(o2.getType());
			}

		});
		this.extents = extents.toArray(new Extent[extents.size()]);
		this.size = 1;
		for (Extent<?> extent : extents) {
			size *= extent.size();
		}
	}

	protected void adoptExtents(Collection<Extent<?>> extents) {
		// TODO was setExtents()
	}

	@SuppressWarnings("unchecked")
	@Override
	public <T extends Locator> T as(Class<T> cls) {
		if (Geometry.class.equals(cls)) {

		} else if (Coverage.class.equals(cls)) {
			return (T) new CoverageImpl(this, 1.0);
		}
		return null;
	}

	@Override
	public Iterator<Scale> iterator() {
		return new ScaleIterator();
	}

	@Override
	public String encode(Encoding... options) {
		return as(Geometry.class).encode(options);
	}

	@Override
	public List<Dimension> getDimensions() {
		return Arrays.asList(extents);
	}

	@Override
	public Dimension dimension(Type type) {
		for (Extent<?> extent : extents) {
			if (extent.getType() == type) {
				return extent;
			}
		}
		return null;
	}

	@Override
	public Granularity getGranularity() {
		Dimension gr = dimension(Type.NUMEROSITY);
		return (gr == null || gr.size() == 1) ? Granularity.SINGLE : Granularity.MULTIPLE;
	}

	@Override
	public boolean isScalar() {
		return this.size == 1;
	}

	@Override
	public long size() {
		return this.size;
	}

	@Override
	public boolean infiniteTime() {
		return false;
	}

	@Override
	public boolean contains(Scale o) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean overlaps(Scale o) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean intersects(Scale o) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public Space getSpace() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Time getTime() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public boolean isTemporallyDistributed() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean isSpatiallyDistributed() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public int getExtentCount() {
		return this.extents.length;
	}

	@Override
	public List<Extent<?>> getExtents() {
		return Arrays.asList(this.extents);
	}

	@Override
	public boolean isEmpty() {
		return extents == null || extents.length == 0;
	}

//	@Override
	public Scale mergeContext(Scale scale, Type... dimensions) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Scale merge(Scale other, LogicalConnector how) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Scale initialization() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Scale termination() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Scale without(Type dimension) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Scale at(Object... dimensions) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Scale collapse(Type... dimensions) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Scale except(Type dimension) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Extent<?> extent(Type extentType) {
		// TODO Auto-generated method stub
		return null;
	}
	
	@Override
	public boolean isGeneric() {
		// a scale only exists if it's NOT generic
		return false;
	}


	/*
	 * Default base for locators, delegating anything not explicitly overridden to
	 * the containing instance.
	 * 
	 * @author Ferd
	 *
	 */
	class DelegatingScale implements Scale {

		private static final long serialVersionUID = -8416028789360949571L;

		@Override
		public String encode(Encoding... options) {
			return ScaleImpl.this.encode(options);
		}

		@Override
		public List<Dimension> getDimensions() {
			return ScaleImpl.this.getDimensions();
		}

		@Override
		public Dimension dimension(Type type) {
			return ScaleImpl.this.dimension(type);
		}

		@Override
		public Granularity getGranularity() {
			return ScaleImpl.this.getGranularity();
		}

		@Override
		public boolean isScalar() {
			return ScaleImpl.this.isScalar();
		}

		@Override
		public long size() {
			return ScaleImpl.this.size();
		}

		@Override
		public boolean infiniteTime() {
			return ScaleImpl.this.infiniteTime();
		}

		@Override
		public <T extends Locator> T as(Class<T> cls) {
			return ScaleImpl.this.as(cls);
		}

		@Override
		public boolean contains(Scale o) {
			return ScaleImpl.this.contains(o);
		}

		@Override
		public boolean overlaps(Scale o) {
			return ScaleImpl.this.overlaps(o);
		}

		@Override
		public boolean intersects(Scale o) {
			return ScaleImpl.this.intersects(o);
		}

		@Override
		public Iterator<Scale> iterator() {
			return ScaleImpl.this.iterator();
		}

		@Override
		public Space getSpace() {
			return ScaleImpl.this.getSpace();
		}

		@Override
		public Time getTime() {
			return ScaleImpl.this.getTime();
		}

		@Override
		public boolean isTemporallyDistributed() {
			return ScaleImpl.this.isTemporallyDistributed();
		}

		@Override
		public boolean isSpatiallyDistributed() {
			return ScaleImpl.this.isSpatiallyDistributed();
		}

		@Override
		public int getExtentCount() {
			return ScaleImpl.this.getExtentCount();
		}

		@Override
		public List<Extent<?>> getExtents() {
			return ScaleImpl.this.getExtents();
		}

		@Override
		public boolean isEmpty() {
			return ScaleImpl.this.isEmpty();
		}

//		@Override
//		public Scale mergeContext(Scale scale, Type... dimensions) {
//			return ScaleImpl.this.mergeContext(scale, dimensions);
//		}

		@Override
		public Scale merge(Scale other, LogicalConnector how) {
			return ScaleImpl.this.merge(other, how);
		}

		@Override
		public Scale initialization() {
			return ScaleImpl.this.initialization();
		}

		@Override
		public Scale termination() {
			return ScaleImpl.this.termination();
		}

		@Override
		public Scale except(Type dimension) {
			return ScaleImpl.this.except(dimension);
		}

		@Override
		public Scale without(Type dimension) {
			return ScaleImpl.this.without(dimension);
		}

		@Override
		public Scale at(Object... dimensions) {
			return ScaleImpl.this.at(dimensions);
		}

		@Override
		public Scale collapse(Type... dimensions) {
			return ScaleImpl.this.collapse(dimensions);
		}

		@Override
		public Extent<?> extent(Type extentType) {
			return ScaleImpl.this.extent(extentType);
		}

		@Override
		public boolean isGeneric() {
			// a generic scale cannot be iterated
			return false;
		}

	}

}
