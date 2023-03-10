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
package org.integratedmodelling.klab.api.collections;

import java.io.Serializable;

import org.integratedmodelling.klab.api.collections.impl.PairImpl;

/**
 * The Interface IPair.
 *
 * @author ferdinando.villa
 * @version $Id: $Id
 * @param <T1> the generic type
 * @param <T2> the generic type
 */
public interface Pair<T1, T2> extends Serializable {

    /**
     * <p>getFirst.</p>
     *
     * @return a T1 object.
     */
    T1 getFirst();
    
    /**
     * <p>getSecond.</p>
     *
     * @return a T2 object.
     */
    T2 getSecond();
    
    public static <A, B> Pair<A, B> of(A a, B b) {
        return new PairImpl<A, B>(a, b);
    }

}
