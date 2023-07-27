package org.integratedmodelling.klab.data.mediation;

import org.integratedmodelling.klab.api.data.mediation.Unit;
import org.integratedmodelling.klab.api.knowledge.Concept;
import org.integratedmodelling.klab.api.services.UnitService;

public class UnitServiceImpl implements UnitService {

	private static Unit meters;
	private static Unit squareMeters;
	
	
	@Override
	public Unit getDefaultUnitFor(Concept concept) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Unit getUnit(String string) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String getServiceName() {
		return "units";
	}

	@Override
	public Unit meters() {
		return meters;
	}

	@Override
	public Unit squareMeters() {
		return squareMeters;
	}

}
