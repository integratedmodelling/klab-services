package org.integratedmodelling.klab.api.lang.kim;

import org.integratedmodelling.klab.api.collections.Literal;

/**
 * The syntactic peer of a k.IM 'define' statement.
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
     * <p>
     * Use {@link KimLiteral#getUnparsedValue(Class)} to "unparse" recursively to a POD object,
     *
     * @return the value defined in the code as a parsed KimLiteral
     */
    Literal getValue();

}
