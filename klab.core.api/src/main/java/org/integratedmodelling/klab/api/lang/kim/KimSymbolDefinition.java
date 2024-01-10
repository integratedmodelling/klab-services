package org.integratedmodelling.klab.api.lang.kim;

import org.integratedmodelling.klab.api.collections.Literal;

/**
 * The syntactic peer of a k.IM 'define' statement.
 * 
 * @author ferdinando.villa
 *
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
	 * Can currently be a POD literal, Java Map, Java List, IServiceCall or
	 * IKimTable.
	 * 
	 * @return the value defined
	 */
	Literal getValue();

}
