package org.integratedmodelling.klab.api.lang;

/**
 * Just a number with units.
 * 
 * @author ferdinando.villa
 *
 */
public interface Quantity  {
	
	/**
	 * May be an integer or a double.
	 * 
	 * @return
	 */
	Number getValue();

	/**
	 * Unvalidated unit as a string.
	 * 
	 * @return
	 */
	String getUnit();
	
	/**
	 * 
	 * @return
	 */
	String getCurrency();
	
	static Quantity parse(String specification) {
	    // TODO
	    return null;
	}
}
