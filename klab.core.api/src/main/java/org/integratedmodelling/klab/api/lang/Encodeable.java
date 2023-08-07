package org.integratedmodelling.klab.api.lang;

/**
 * Objects that implement this can produce their source code definition. There
 * is no choice of language: according to the type of object, the encoding must
 * be compatible with all the k.LAB languages it is supposed to work with.
 * 
 * @author Ferd
 *
 */
public interface Encodeable {

	String encode();

}
