package org.integratedmodelling.klab.data.mediation;

import java.util.Map;

import org.integratedmodelling.klab.api.collections.Pair;
import org.integratedmodelling.klab.api.data.mediation.Unit;
import org.integratedmodelling.klab.api.data.mediation.ValueMediator;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.geometry.Locator;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.observation.scale.ExtentDimension;
import org.integratedmodelling.klab.api.knowledge.observation.scale.ExtentDistribution;

public class UnitImpl implements Unit {

	private static final long serialVersionUID = 2911446978520705010L;

	@Override
	public boolean isCompatible(ValueMediator other) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public Number convert(Number d, ValueMediator scale) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Number convert(Number value, Locator locator) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public boolean isContextual() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public Unit multiply(Unit unit) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Unit divide(Unit unit) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Unit scale(double scale) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Map<ExtentDimension, ExtentDistribution> getAggregatedDimensions() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Pair<Unit, Unit> splitExtent(ExtentDimension dimension) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Unit contextualize(Observable observable, Geometry scale) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public boolean isUnitless() {
		// TODO Auto-generated method stub
		return false;
	}

}
