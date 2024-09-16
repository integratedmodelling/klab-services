package org.integratedmodelling.klab.api.lang;

import org.integratedmodelling.klab.api.knowledge.impl.QuantityImpl;
import org.integratedmodelling.klab.api.utils.Utils;

import java.io.Serializable;

/**
 * Just a number with units.
 * 
 * @author ferdinando.villa
 *
 */
public interface Quantity extends Serializable {
	
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
		int dot = specification.indexOf('.');
		if (dot < 0) {
			dot = specification.indexOf(' ');
		}

		if (dot > 0) {
			String number = specification.substring(0,dot);
			String unit = specification.substring(dot +1);
			QuantityImpl ret = new QuantityImpl();
			ret.setValue(Utils.Data.parseAsType(number, Double.class));
			if (unit.contains("@")) {
				ret.setCurrency(unit);
			} else {
				ret.setUnit(unit);
			}
		}

		return null;
	}
}
