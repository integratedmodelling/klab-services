package org.integratedmodelling.klab.api.geometry;

/**
 * A specialized interface that a Geometry can be adapted to using {@link Geometry#as(Class)} to quickly scan a 1D
 * extent using offsets only. According to configuration and context of use, the Offset returned by locate() may reuse a
 * mutable threadlocal object or create a new one for concurrent operation. The iterator should always reuse a single
 * object.
 *
 * <p>Implementations can be derived to implement e.g. space-filling curves, prioritized lists or other iteration
 * strategies.</p>
 */
public interface Scanner1D extends Geometry, Iterable<Offset> {
    Offset locate(long x);
}
