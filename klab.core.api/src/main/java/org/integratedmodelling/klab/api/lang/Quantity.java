package org.integratedmodelling.klab.api.lang;

import org.integratedmodelling.klab.api.Klab;
import org.integratedmodelling.klab.api.exceptions.KlabIllegalStateException;
import org.integratedmodelling.klab.api.knowledge.observation.scale.space.Projection;
import org.integratedmodelling.klab.api.knowledge.observation.scale.space.Shape;

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

	static Quantity of(double value, String unit) {
		return create(value + "." + unit);
	}

	static Quantity create(String textSpecification) {
		Klab.Configuration configuration = Klab.INSTANCE.getConfiguration();
		if (configuration == null) {
			throw new KlabIllegalStateException("k.LAB environment not configured to create a quantity");
		}
		return configuration.parseQuantity(textSpecification);
	}

}
