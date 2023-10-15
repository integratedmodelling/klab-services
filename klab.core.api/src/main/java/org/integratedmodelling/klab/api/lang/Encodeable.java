package org.integratedmodelling.klab.api.lang;

/**
 * Objects that implement this can produce their source code definition. There is no obligation to support all
 * languages. The constants in {@link org.integratedmodelling.klab.api.services.Language} should be supported
 * when the object needs encoding in them.
 *
 * @author Ferd
 */
public interface Encodeable {

    String encode(String language);

}
