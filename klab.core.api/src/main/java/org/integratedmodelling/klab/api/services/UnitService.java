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

  /**
   * Convert a value from the source unit to the destination unit. The current implementation
   * takes the destination first and the source second, matching
   * {@code target.convert(value, source)} in {@link Unit}. The historical parameter names
   * {@code from} and {@code to} are therefore misleading; do not reverse arguments based on them.
   *
   * @param d value in the source unit
   * @param from destination unit (historical name)
   * @param to source unit (historical name)
   * @return value in the destination unit
   */
  Number convert(Number d, Unit from, Unit to);

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
