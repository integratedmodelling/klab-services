package org.integratedmodelling.klab.api.data;

/**
 * Base storage providing the general methods. Children enable either boxed I/O or faster native operation
 * (recommended). The runtime makes the choice. The default state implementation keeps the getStorage method as an
 * abstract method that must be completed by the runtime.
 *
 * @author Ferd
 */
public interface Storage extends RuntimeAsset {

    public enum Type {
        BOXING,
        DOUBLE,
        FLOAT,
        INTEGER,
        KEYED,
        BOOLEAN
    }

    Type getType();

    Histogram getHistogram();

}
