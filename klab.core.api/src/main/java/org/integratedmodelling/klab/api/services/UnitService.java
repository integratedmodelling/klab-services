package org.integratedmodelling.klab.api.services;

import org.integratedmodelling.klab.api.collections.Pair;
import org.integratedmodelling.klab.api.data.mediation.Unit;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.geometry.Locator;
import org.integratedmodelling.klab.api.knowledge.Concept;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.observation.scale.ExtentDimension;

public interface UnitService extends Service {

  /**
   * @param string
   * @return
   */
  Unit getUnit(String string);

  /**
   * @param concept
   * @return
   */
  Unit getDefaultUnitFor(Concept concept);

  /**
   * Return the unit for meters.
   *
   * @return
   */
  Unit meters();

  /**
   * Square meters
   *
   * @return
   */
  Unit squareMeters();

  Unit milliseconds();

  /**
   * @param unit
   * @param other
   * @return
   */
  boolean isCompatible(Unit unit, Unit other);

  /** Convert from source into destination, preserving the destination-first receiver convention. */
  Number convert(Number value, Unit destination, Unit source);

  /** Portable coefficients for an ordinary, context-free affine conversion. */
  record Conversion(double factor, double offset) {
    public Conversion {
      if (!Double.isFinite(factor) || factor <= 0 || !Double.isFinite(offset))
        throw new IllegalArgumentException("Invalid affine unit conversion");
    }
    public double convert(double value) { return value * factor + offset; }
  }

  default Conversion conversion(Unit destination, Unit source) {
    throw new UnsupportedOperationException("Primitive unit compilation is unavailable");
  }

  /**
   * @param value
   * @param unit
   * @param locator
   * @return
   */
  Number convert(Number value, Unit unit, Locator locator);

  /**
   * @param unit
   * @param other
   * @return
   */
  Unit multiply(Unit unit, Unit other);

  /**
   * @param unit
   * @param other
   * @return
   */
  Unit divide(Unit unit, Unit other);

  /**
   * @param unit
   * @param scale
   * @return
   */
  Unit scale(Unit unit, double scale);

  /**
   * @param unit
   * @param dimension
   * @return
   */
  Pair<Unit, Unit> splitExtent(Unit unit, ExtentDimension dimension);

  /**
   * @param unit
   * @param observable
   * @param scale
   * @return
   */
  Unit contextualize(Unit unit, Observable observable, Geometry scale);
}
