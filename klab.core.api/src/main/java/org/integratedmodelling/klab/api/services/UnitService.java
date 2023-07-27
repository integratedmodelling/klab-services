package org.integratedmodelling.klab.api.services;

import org.integratedmodelling.klab.api.data.mediation.Unit;
import org.integratedmodelling.klab.api.data.mediation.ValueMediator;
import org.integratedmodelling.klab.api.knowledge.Concept;

public interface UnitService extends Service {

	Unit getDefaultUnitFor(Concept concept);

	Unit getUnit(String string);

	/**
	 * Return the unit for meters.
	 * 
	 * @return
	 */
	Unit meters();

	/**
	 * Square meters
	 * 
	 * @return
	 */
	Unit squareMeters();

}
