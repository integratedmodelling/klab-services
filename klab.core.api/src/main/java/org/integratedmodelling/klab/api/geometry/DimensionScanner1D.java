//package org.integratedmodelling.klab.api.geometry;
//
///**
// * A specialized interface that a Geometry can be adapted to using {@link Geometry#as(Class)} to
// * quickly scan a 1D extent using offsets only. According to configuration and context of use, the
// * Offset returned by locate() may reuse a mutable threadlocal object or create a new one for
// * concurrent operation. The iterator should always reuse a single object as this may be called
// * millions of times in a single contextualizer run.
// *
// * <p>
// * Implementations can be derived to implement e.g. space-filling curves, prioritized lists or other
// * iteration strategies.
// * </p>
// * @deprecated use fillers in the {@link org.integratedmodelling.klab.api.data.Data} interface
// */
//public interface DimensionScanner1D extends Geometry, Iterable<Offset> {
//
//    /**
//     * A linear offset can be converted into the needed offset for the geometry. The returned object
//     * should be a threadlocal object that gets allocated only once per thread.
//     *
//     * @param x
//     * @return
//     */
//    Offset locate(long x);
//}
