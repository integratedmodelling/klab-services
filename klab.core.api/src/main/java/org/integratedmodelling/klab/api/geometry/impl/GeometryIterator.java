package org.integratedmodelling.klab.api.geometry.impl;

import java.util.Iterator;

import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.geometry.Locator;

/**
 * Simplest iterator for a geometry, producing {@link Offset} objects.
 * 
 * @author ferdinando.villa
 *
 */
public class GeometryIterator implements Iterator<Locator> {

	NDCursor cursor;
	Geometry geometry;
	long[] locked;
	long offset = 0;

	public GeometryIterator(Geometry geometry, Offset offset) {
		this.locked = offset.pos;
		this.cursor = new NDCursor(geometry, locked);
		this.geometry = geometry;
	}

	public GeometryIterator(GeometryImpl geometry) {
		this.geometry = geometry;
		this.cursor = new NDCursor(geometry);
	}

	@Override
	public boolean hasNext() {
		return offset < cursor.getMultiplicity();
	}

	@Override
	public Locator next() {
		long[] pos = cursor.getIndices(offset++);
		boolean scalar = true;
		if (locked != null) {
			for (int i = 0; i < pos.length; i++) {
				if (locked[i] >= 0) {
					pos[i] = locked[i];
				} else {
					scalar = false;
				}
			}
		}
		Offset ret = new Offset();
		ret.pos = pos;
		ret.length = pos.length;
		ret.linear = scalar ? ret.computeOffset(pos, geometry) : -1;
		ret.scalar = scalar;
		ret.setGeometry(geometry);
		return ret;
	}

}
