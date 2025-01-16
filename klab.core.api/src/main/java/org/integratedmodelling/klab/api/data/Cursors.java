package org.integratedmodelling.klab.api.data;

import org.integratedmodelling.klab.api.exceptions.KlabIllegalStateException;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.geometry.impl.NDCursor;
import org.integratedmodelling.klab.api.utils.Utils;

import java.util.ArrayList;
import java.util.List;

public class Cursors {

  // use to validate that there are as many varying dimensions as the passed parameters and they
  // have
  // the stated dimensionalities
  private static List<long[]> checkDimensions(Geometry geometry, int... requiredVaryingDimensionality) {
    int n = 0;
    List<long[]> ret = new ArrayList<>();
    for (var dimension : geometry.getDimensions().stream().filter(d -> d.size() > 1).toList()) {
      if (requiredVaryingDimensionality.length <= n
          || dimension.getShape().size() != requiredVaryingDimensionality[n]) {
        throw new KlabIllegalStateException("Requested cursor is incompatible with the scanned geometry");
      } else {
        ret.add(Utils.Numbers.longArrayFromCollection(dimension.getShape()));
      }
      n++;
    }
    return ret;
  }

  protected abstract static class AbstractCursor implements Data.Cursor {

    protected final long size;
    protected long current = 0;

    public AbstractCursor(Geometry geometry) {
      this.size = geometry.size();
    }

    @Override
    public long currentOffset() {
      return current;
    }

    @Override
    public long nextLong() {
      return current++;
    }

    @Override
    public long currentOriginalOffset() {
      // TODO
      return currentOffset();
    }

    @Override
    public boolean hasNext() {
      return current < size;
    }

    @Override
    public long originalOffset(long... dimensionOffsets) {
      // TODO
      return currentOriginalOffset();
    }
  }

  public static class ScalarCursor extends AbstractCursor {

    public ScalarCursor(Geometry geometry) {
      super(geometry);
    }

    @Override
    public long offset(long... dimensionOffsets) {
      return current;
    }
  }


  public static class Linear1D extends AbstractCursor {

    public Linear1D(Geometry geometry) {
      super(geometry);
      checkDimensions(geometry, 1);
    }

    @Override
    public long offset(long... dimensionOffsets) {
      return current;
    }
  }

  public static class LinearND extends AbstractCursor {

    final NDCursor cursor;

    public LinearND(Geometry geometry) {
      super(geometry); // no validation, this works for everything
      cursor = new NDCursor(geometry, NDCursor.Order.FIRST_SLOWEST);
    }

    @Override
    public long offset(long... dimensionOffsets) {
      return cursor.getElementOffset(dimensionOffsets);
    }
  }

  public static class LinearNDInverted extends AbstractCursor {

    final NDCursor cursor;

    public LinearNDInverted(Geometry geometry) {
      super(geometry); // no validation, this works for everything
      cursor = new NDCursor(geometry, NDCursor.Order.FIRST_FASTEST);
    }

    @Override
    public long offset(long... dimensionOffsets) {
      return cursor.getElementOffset(dimensionOffsets);
    }
  }


  /** Scans a 2D */
  public static class Matrix2DXY extends LinearND {

    final long[] shape;

    public Matrix2DXY(Geometry geometry) {
      super(geometry);
      shape = checkDimensions(geometry, 2).getFirst();
    }

    @Override
    public long offset(long... dimensionOffsets) {
      // must be 2 indices; we let Java throw its own exceptions if not
      return 0; // TODO
    }
  }
}
