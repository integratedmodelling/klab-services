package org.integratedmodelling.klab.api.lang.kim;

import org.integratedmodelling.klab.api.collections.Literal;

/**
 * The syntactic peer of a k.IM 'define' statement. These are only inserted in the statement list to navigate
 * the statements; the actual objects used, potentially translated to instance implementations according to
 * their class, are stored in the namespace after parsing, accessible through
 * {@link KimNamespace#getDefines()}.
 *
 * @author ferdinando.villa
 */
public interface KimSymbolDefinition extends KlabStatement {

    /**
     * Name this is defined with. Lowercase identifier.
     *
     * @return
     */
    String getName();

    /**
     * The URN of the symbol, incorporating the namespace.
     *
     * @return the symbol name
     */
    String getUrn();

    /**
     * If a class is specified, return it here.
     *
     * @return
     */
    String getDefineClass();

    /**
     * Can currently be the content of a  ParsedObject of various types. Converting to the object specified by
     * {@link #getDefineClass()}, if any, if done at the point of use.
     *
     * @return the value defined in the code
     */
    Literal getValue();

}
