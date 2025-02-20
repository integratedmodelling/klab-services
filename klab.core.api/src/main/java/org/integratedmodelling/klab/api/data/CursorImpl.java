package org.integratedmodelling.klab.api.data;

import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.geometry.impl.NDCursor;

public class CursorImpl extends NDCursor implements Data.Cursor {

  protected long next = 0;

  private Data.LongArrayToLongFunction inverseMapper;
  private Data.LongToLongArrayFunction mapper;
  private Data.LongArrayToLongFunction desiredInverseMapper;
  private Data.LongToLongArrayFunction desiredMapper;

  /**
   * Constructor for simple mapping of spatial offsets according to a filling curve
   *
   * @param geometry
   * @param spaceFillingCurve
   */
  public CursorImpl(Geometry geometry, Data.SpaceFillingCurve spaceFillingCurve) {
    super(geometry, Order.FIRST_SLOWEST);
    var mappers = spaceFillingCurve.offsetMappers(geometry);
    this.mapper = mappers.getFirst();
    this.inverseMapper = mappers.getSecond();
  }

  /**
   * Constructor for remapping a filling curve to the offsets implied by an original one .
   *
   * @param geometry
   * @param desiredFillingCurve
   * @param originalFillingCurve
   */
  public CursorImpl(
      Geometry geometry,
      Data.SpaceFillingCurve desiredFillingCurve,
      Data.SpaceFillingCurve originalFillingCurve) {
    this(geometry, originalFillingCurve);
    var mappers = desiredFillingCurve.offsetMappers(geometry);
    this.desiredMapper = mappers.getFirst();
    this.desiredInverseMapper = mappers.getSecond();

  }

  @Override
  public long offset(Data.Cursor other, long... dimensionOffsets) {
    long[] cofs = new long[getExtents().length];
    for (var x : getExtents()) {
      if (x > 1) {}
    }
    return 0;
  }

  @Override
  public long nextLong() {
    return next++;
  }

  @Override
  public boolean hasNext() {
    return next < getMultiplicity();
  }
}
