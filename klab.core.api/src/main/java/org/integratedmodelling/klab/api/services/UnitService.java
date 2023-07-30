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
     * 
     * @param string
     * @return
     */
    Unit getUnit(String string);

    /**
     * 
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

    /**
     * 
     * @param unitImpl
     * @param other
     * @return
     */
    boolean isCompatible(Unit unit, Unit other);

    /**
     * 
     * @param d
     * @param from
     * @param to
     * @return
     */
    Number convert(Number d, Unit from, Unit to);

    /**
     * 
     * @param value
     * @param unitImpl
     * @param locator
     * @return
     */
    Number convert(Number value, Unit unit, Locator locator);

    /**
     * 
     * @param unitImpl
     * @param unit
     * @return
     */
    Unit multiply(Unit unit, Unit other);

    /**
     * 
     * @param unitImpl
     * @param unit
     * @return
     */
    Unit divide(Unit unit, Unit other);

    /**
     * 
     * @param unitImpl
     * @param scale
     * @return
     */
    Unit scale(Unit unit, double scale);

    /**
     * 
     * @param unitImpl
     * @param dimension
     * @return
     */
    Pair<Unit, Unit> splitExtent(Unit unit, ExtentDimension dimension);

    /**
     * 
     * @param unitImpl
     * @param observable
     * @param scale
     * @return
     */
    Unit contextualize(Unit unit, Observable observable, Geometry scale);

}
