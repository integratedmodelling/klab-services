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
     * The name of the symbol. Can only be uppercase with underscores as separator.
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
     * Can currently be a ParsedObject of various types. If getDefineClass returns a valid class, this will
     * contain the <em>original</em> parsed value, but the value in the correspondent entry from
     * {@link KimNamespace#getDefines()} will be the object correspondent to the installed implementation for
     * that class.
     *
     * @return the value defined in the code
     */
    Object getValue();

}
