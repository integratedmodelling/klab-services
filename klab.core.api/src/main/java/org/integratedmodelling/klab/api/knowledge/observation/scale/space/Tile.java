package org.integratedmodelling.klab.api.knowledge.observation.scale.space;

/**
 * A regularly tiled space, including the single cell.Covers raster grids and
 * any other regular subdivition, Named Tile to reflect the fact that
 * implementation may provide tiles of different shape than rectangular.
 * 
 * @author Ferd
 *
 */
public interface Tile extends Space {

	Grid grid();

}
