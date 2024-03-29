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
package org.integratedmodelling.klab.api.knowledge.observation.scale.time;

import org.integratedmodelling.klab.api.knowledge.observation.scale.Dimensional;

/**
 * Tag interface that will assign temporal nature to a class, so that
 * inferences can be made when checking units or other types of use.
 *
 * @author ferdinando.villa
 * @version $Id: $Id
 */
public interface Temporal extends Dimensional {
    /**
     * <p>getDimensionCount.</p>
     *
     * @return a int.
     */
    default int getDimensionCount() {
        return 1;
    }
}
