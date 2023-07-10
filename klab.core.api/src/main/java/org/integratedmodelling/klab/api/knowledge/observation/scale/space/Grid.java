/*******************************************************************************
 *  Copyright (C) 2007, 2015:
 *  
 *    - Ferdinando Villa <ferdinando.villa@bc3research.org>
 *    - integratedmodelling.org
 *    - any other authors listed in @author annotations
 *
 *    All rights reserved. This file is part of the k.LAB software suite,
 *    meant to enable modular, collaborative, integrated 
 *    development of interoperable data and model components. For
 *    details, see http://integratedmodelling.org.
 *    
 *    This program is free software; you can redistribute it and/or
 *    modify it under the terms of the Affero General Public License 
 *    Version 3 or any later version.
 *
 *    This program is distributed in the hope that it will be useful,
 *    but without any warranty; without even the implied warranty of
 *    merchantability or fitness for a particular purpose.  See the
 *    Affero General Public License for more details.
 *  
 *     You should have received a copy of the Affero General Public License
 *     along with this program; if not, write to the Free Software
 *     Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 *     The license is also available at: https://www.gnu.org/licenses/agpl.html
 *******************************************************************************/
package org.integratedmodelling.klab.api.knowledge.observation.scale.space;

import java.io.Serializable;

/**
 * A grid is the specification for a square-tiled Tile extent. It can be passed
 * to a Tile constructor to ensure alignment of grids. Every Tile extent that is
 * made of rectangular tiles must be able to produce the Grid that defines it.
 * <p>
 * Grids can be defined as k.IM class defines so that they can be stored and
 * used to define consistent gridded spatial contexts.
 * 
 * @author Ferd
 *
 */
public interface Grid extends Serializable {
	
	int getXCells();
	
	int getYCells();
	
	Envelope getEnvelope();

}
