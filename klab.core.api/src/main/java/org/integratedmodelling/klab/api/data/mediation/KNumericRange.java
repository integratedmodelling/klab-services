/*
 * This file is part of k.LAB.
 * 
 * k.LAB is free software: you can redistribute it and/or modify
 * it under the terms of the Affero GNU General Public License as published
 * by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * A copy of the GNU Affero General Public License is distributed in the root
 * directory of the k.LAB distribution (LICENSE.txt). If this cannot be found 
 * see <http://www.gnu.org/licenses/>.
 * 
 * Copyright (C) 2007-2018 integratedmodelling.org and any authors mentioned
 * in author tags. All rights reserved.
 */
package org.integratedmodelling.klab.api.data.mediation;

/**
 * The Interface INumericRange.
 *
 * @author ferdinando.villa
 * @version $Id: $Id
 */
public interface KNumericRange extends KValueMediator {
    
    /**
     * Any unbounded boundary will be the corresponding Double.(NEGATIVE_)INFINITE.
     *
     * @return the range of the scale.
     */
    double getLowerBound();

    /**
     * Any unbounded boundary will be the corresponding Double.(NEGATIVE_)INFINITE.
     *
     * @return the range of the scale.
     */
    double getUpperBound();

    /**
     * Both bounds are defined
     *
     * @return true if both boundaries are defined.
     */
    boolean isBounded();

    /**
     * If only integers should be used.
     *
     * @return true if the scale is not meant to have values in non-integer numbers.
     */
    boolean isInteger();
}
