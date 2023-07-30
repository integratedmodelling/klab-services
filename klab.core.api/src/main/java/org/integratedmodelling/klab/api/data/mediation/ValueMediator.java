/*******************************************************************************
 * Copyright (C) 2007, 2015:
 * 
 * - Ferdinando Villa <ferdinando.villa@bc3research.org> - integratedmodelling.org - any other
 * authors listed in @author annotations
 *
 * All rights reserved. This file is part of the k.LAB software suite, meant to enable modular,
 * collaborative, integrated development of interoperable data and model components. For details,
 * see http://integratedmodelling.org.
 * 
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * Affero General Public License Version 3 or any later version.
 *
 * This program is distributed in the hope that it will be useful, but without any warranty; without
 * even the implied warranty of merchantability or fitness for a particular purpose. See the Affero
 * General Public License for more details.
 * 
 * You should have received a copy of the Affero General Public License along with this program; if
 * not, write to the Free Software Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA
 * 02111-1307, USA. The license is also available at: https://www.gnu.org/licenses/agpl.html
 *******************************************************************************/
package org.integratedmodelling.klab.api.data.mediation;

import java.io.Serializable;

import org.integratedmodelling.klab.api.exceptions.KIllegalStateException;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.geometry.Locator;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.observation.scale.Scale;

/**
 * Describes any object that can mediate a value to another. In k.LAB mediations are allowed to
 * bypass geometries, aggregating or propagating as needed.
 * 
 * @author Ferd
 *
 */
public interface ValueMediator extends Serializable {

    /**
     * true if this can be converted into other. Do not throw exceptions.
     * 
     * @param other
     * @throws KIllegalStateException if this mediator was produced through
     *         {@link #contextualize(Observable, Scale)}.
     * @return true if other is compatible
     */
    boolean isCompatible(ValueMediator other);

    /**
     * Convert from the passed mediator to the one this represents.
     * 
     * @param d
     * @param scale
     * @return the converted number
     * @throws KIllegalStateException if this mediator was produced through
     *         {@link #contextualize(Observable, Scale)}.
     */
    Number convert(Number d, ValueMediator scale);

    /**
     * Obtain a mediator that will convert quantities from the mediator of the observable (unit or
     * currency) into what we represent, using the scale portion over which the observation of the
     * value is made to account for any different distribution through the context.
     * <p>
     * The resulting mediator will only accept the {@link #convert(Number, Locator)} call and throw
     * an exception in any other situation. If the observable passed has no mediator, the conversion
     * will be standard and non-contextual (using a simple conversion factor for speed). Otherwise,
     * the fastest set of transformations will be encoded in the returned mediator.
     * <p>
     * The resulting mediator will perform correcly <em>only</em> when used with locators coming
     * from the same scale that was used to produce it. It will contain transformations in
     * parametric form, so that the possible irregularity of the extents in the locators is
     * accounted for.
     * <p>
     * The strategy to create the necessary transformations, consisting in parametric
     * multiplications or divisions by an appropriately transformed extent in space and/or time, is
     * as follows:
     * <ol>
     * <li>if observable is intensive, just check compatibility and return self if compatible, throw
     * exception if not. Otherwise:
     * <li>obtain contextualized candidate forms of both the observable's base unit and self. Both
     * should have one compatible form in the candidates. If not, throw exception. If the compatible
     * form is the same, proceed as in (1). Otherwise:
     * <li>devise two strategies to mediate 1) incoming form to base form and 2) base form to this.
     * Each strategy consists of a list of parametric operations on S/T contexts with a conversion
     * factor for the basic representation (m^x for space, ms for time).
     * <li>simplify the two strategies into a single set of operations to add to the contextualized
     * unit returned, which also carries the definition of the contextual nature re: S/T and a
     * string explaining the transformations made and why.
     * </ol>
     * <p>
     * EXAMPLE
     * <p>
     * Precipitation comes from data as mm/day. The I,I form of the observable base unit (m^3) in
     * T,S is m/s, compatible. OK - proceed. Target is m, which is I,E w.r.t. the target as seen
     * matching to the contextualized extension of m^3. Specific extents must match - if AREAL and
     * LINEAL are seen together, no compatibility can exist.
     * <p>
     * <ol>
     * <li>Turn mm/day -> m/s. Only a multiplicative factor M1 is needed.</li>
     * <li>Turn m/s (I,I) into its (I,E) form using T extension -> mm: op(x * Tms * 1000)</li>
     * <li>Turn the resulting mm into m. Another multiplication factor (M2)</li>
     * <li>Final strategy is (M1*M2*1000)*x*Tms.</li>
     * </ol>
     * <p>
     * In case the target is m^3: I,I -> E,E so step 2 produces two extensions:
     * op(x*Tms*1000)op(x*S)
     * <p>
     * Model validator should always WARN if extensive is output by data AS LONG AS data come with
     * their fully specified extension (e.g. T is physical). Otherwise it's an error.
     * 
     * @param observable
     * @param scale
     * @return a contextualized mediator specialized for the conversion to this unit in this scale.
     */
    ValueMediator contextualize(Observable observable, Geometry scale);

    /**
     * Convert a quantity to the unit we represent from the one in the observable that was passed to
     * the {@link #contextualize(Observable, Scale)} call that originated this one.
     * 
     * @param value
     * @param locator
     * @throws KIllegalStateException if this is called on a mediator that was not produced
     *         through {@link #contextualize(Observable, Scale)}.
     * @return
     */
    Number convert(Number value, Locator locator);

    /**
     * If true, this mediator is meant to be used across scales using
     * {@link #convert(Number, Locator)}, otherwise it's a pure non-contextual mediator.
     * 
     * @return
     */
    boolean isContextual();

}
