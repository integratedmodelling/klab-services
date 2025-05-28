/*
 * This file is part of k.LAB.
 *
 * k.LAB is free software: you can redistribute it and/or modify it under the terms of the Affero
 * GNU General Public License as published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * A copy of the GNU Affero General Public License is distributed in the root directory of the k.LAB
 * distribution (LICENSE.txt). If this cannot be found see <http://www.gnu.org/licenses/>.
 *
 * Copyright (C) 2007-2018 integratedmodelling.org and any authors mentioned in author tags. All
 * rights reserved.
 */
package org.integratedmodelling.klab.api.data.mediation;

import java.util.Map;

import org.integratedmodelling.klab.api.collections.Pair;
import org.integratedmodelling.klab.api.data.mediation.impl.UnitImpl;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.observation.scale.ExtentDimension;
import org.integratedmodelling.klab.api.knowledge.observation.scale.ExtentDistribution;

/**
 * Units of measurement. Creation and inquiry methods are provided by
 * {@link org.integratedmodelling.klab.api.services.UnitService}.
 * <p>
 * Much more complex than (not-really-)standard JRS-375 units due to the need of scale awareness and
 * semantic-driven value aggregation and propagation. When a quality is extensive, its value can be translated to
 * units that are incompatible because they either add or remove one or more contextual dimensions
 * (space and/or time). Also, the unit may already contain an implicit contextualization and be
 * valid for the semantics of the observable once the contextualization is factored in: for example,
 * volume of rain can be measured in mm as long as the context is 2D space, in which case mm means
 * the density of the volume (mm^3/mm^2) and is valid for the semantics of a volume.
 * <p>
 * k.LAB validates unit appropriateness <em>in context</em> and requires declaration of any
 * discrepancy between intensive/extensive character using annotations. If those are provided
 * correctly, context translations are possible even within the same model: for example, a weather
 * process model may produce extensive rainfall in both space and time (e.g. in m^2, applied to each
 * timestep and area subdivision) and area/time intensive snow (e.g. in mm/day) at the same time.
 * <p>
 * The unit API enables contextual conversion by using a specialized {@link Unit} implementation.
 * When the context is factored in, the scale of computation for the unit is incorporated in the
 * declared unit by obtaining a contextualized unit from
 *
 * @author Ferd
 * @version $Id: $Id
 */
public interface Unit extends ValueMediator {

    /**
     * Return a new unit multiplied by the passed one.
     *
     * @param unit a {@link org.integratedmodelling.klab.api.data.mediation.Unit} object.
     * @return a new product unit
     */
    Unit multiply(Unit unit);

    /**
     * Return a new unit divided by the passed one.
     *
     * @param unit a {@link org.integratedmodelling.klab.api.data.mediation.Unit} object.
     * @return a new unit
     */
    Unit divide(Unit unit);

    /**
     * Return a new unit scaled according to the passed double.
     *
     * @param scale a double.
     * @return a new unit
     */
    Unit scale(double scale);

    /**
     * Return the set of aggregated dimensions in case this one results from re-contextualizing a
     * stated unit to a geometry, so that we can reconstruct the original values.
     * <p>
     * TODO hide from API
     *
     * @return a map of extent dimensions to their distributions for aggregated dimensions
     */
    Map<ExtentDimension, ExtentDistribution> getAggregatedDimensions();

    /**
     * Assuming the unit is distributed over the passed extent, split the unit from the extent and
     * return them separately. This will also infer spatial unit if called on the factorized form
     * (e.g. for mm over AREAL, it will return (mm^3, mm^2)).
     *
     * @param dimension the extent dimension to split
     * @return a pair containing the numerator unit and denominator unit after splitting
     */
    Pair<Unit, Unit> splitExtent(ExtentDimension dimension);

    @Override
    Unit contextualize(Observable observable, Geometry scale);

    /**
     * True if unitless.
     *
     * @return true if this unit is dimensionless (has no units), false otherwise
     */
    boolean isUnitless();

//    static Unit create(String unitStatement) {
//        return new UnitImpl(unitStatement);
//    }

}
