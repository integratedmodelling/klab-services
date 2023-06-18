package org.integratedmodelling.klab.api.data;

/**
 * A proxy for an object, obtained through get(). Used in Java placeholders for use in k.Actors.
 * 
 * @author mario
 *
 */
public interface Proxy {
    Object get();
}
