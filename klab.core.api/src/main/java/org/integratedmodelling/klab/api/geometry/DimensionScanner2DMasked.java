package org.integratedmodelling.klab.api.geometry;

/**
 * Same API as the parent, but the offsets iterated do not cover the 2D shape that serves as mask for the
 * extent if one exists. Because the cost of checking for inclusion can be significant, this is a separate
 * interface to use only when needed.
 *
 * @deprecated revise using {@link org.integratedmodelling.klab.api.data.Data.Filler}
 */
public interface DimensionScanner2DMasked extends DimensionScanner2D {
}
