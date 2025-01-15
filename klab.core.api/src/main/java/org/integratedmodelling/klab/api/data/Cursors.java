package org.integratedmodelling.klab.api.data;

import org.integratedmodelling.klab.api.exceptions.KlabIllegalStateException;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.geometry.impl.NDCursor;

import java.util.List;

public class Cursors {

  // use to validate that there are as many varying dimensions as the passed parameters and they
  // have
  // the stated dimensionalities
  private static void checkDimensions(Geometry geometry, int... requiredVaryingDimensionality) {
    int n = 0;
    for (var dimension : geometry.getDimensions().stream().filter(d -> d.size() > 1).toList()) {
      if (requiredVaryingDimensionality.length <= n
          || dimension.getShape().size() != requiredVaryingDimensionality[n]) {
        throw new KlabIllegalStateException("Requested cursor is incompatible with the scanned geometry");
      }
      n++;
    }
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
    public long originalOffset(long... dimensionOffsets) {
      // TODO
      return currentOriginalOffset();
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


    @Override
    public boolean hasNext() {
      return current < size;
    }
  }

  public static class LinearND extends AbstractCursor {

    final NDCursor cursor;

    public LinearND(Geometry geometry) {
      super(geometry);
      cursor = new NDCursor(geometry);
    }

    @Override
    public long offset(long... dimensionOffsets) {
      return cursor.getElementOffset(dimensionOffsets);
    }

    @Override
    public boolean hasNext() {
      return current < cursor.getMultiplicity();
    }
  }

  /** Scans a 2D */
  public static class Matrix2DXY extends LinearND {

    public Matrix2DXY(Geometry geometry) {
      super(geometry);
      checkDimensions(geometry, 2);
    }

  }
}
