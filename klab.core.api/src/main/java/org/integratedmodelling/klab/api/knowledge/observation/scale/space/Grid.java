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

	/**
	 * Cell size in projection units along the X axis (always horizontal).
	 * 
	 * @return
	 */
	double getXCellSize();

	/**
	 * Cell size in projection units along the Y axis (always vertical).
	 * 
	 * @return
	 */
	double getYCellSize();

	/**
	 * Actual resolution in projection coordinates, computed as the square root of
	 * the cell area.
	 * 
	 * @return
	 */
	double resolution();

	/**
	 * A grid that only specified a cell size with no envelope has size() == 0.
	 * 
	 * @return
	 */
	long size();

	/**
	 * This will be 0 if there is no envelope.
	 * 
	 * @return
	 */
	long getXCells();

	/**
	 * This will be 0 if there is no envelope.
	 * 
	 * @return
	 */
	long getYCells();

	/**
	 * The envelope is null if the grid is only specified in terms of cell size.
	 * 
	 * @return
	 */
	Envelope getEnvelope();

	/**
	 * The projection is never null, so there is a separate method as the envelope
	 * may be. In a grid specification used as constraint, the projection specified
	 * in the grid overrides any other specification.
	 * 
	 * @return
	 */
	Projection getProjection();

	/**
	 * Return a new grid that is as close as possible as this one after aligning it
	 * with the passed one. Alignment means that the cells of the resulting grid
	 * align with those of the incoming, possibly with a multiplicity of cells in
	 * one corresponding to one cell of the other. This may cause the projection and
	 * envelope of the result to be different from the original, as well as the
	 * "square cells" constraint.
	 * <p>
	 * If the passed grid has an envelope, the result will be intersected to be
	 * included in the incoming envelope.
	 * 
	 * @param other
	 * @return
	 */
	Grid align(Grid other);

}
