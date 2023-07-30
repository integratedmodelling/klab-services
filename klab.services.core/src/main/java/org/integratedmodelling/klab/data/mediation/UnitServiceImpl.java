package org.integratedmodelling.klab.data.mediation;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.integratedmodelling.klab.api.collections.Pair;
import org.integratedmodelling.klab.api.data.mediation.Unit;
import org.integratedmodelling.klab.api.data.mediation.impl.UnitImpl;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.geometry.Locator;
import org.integratedmodelling.klab.api.knowledge.Concept;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.observation.scale.ExtentDimension;
import org.integratedmodelling.klab.api.services.UnitService;
import org.integratedmodelling.klab.api.utils.Utils;
import org.integratedmodelling.klab.exceptions.KlabValidationException;
import org.integratedmodelling.klab.utils.MiscUtilities;

import si.uom.NonSI;
import tech.units.indriya.format.SimpleUnitFormat;

public class UnitServiceImpl implements UnitService {

    private Unit meters;
    private Unit squareMeters;

    class FunctionalUnit extends AbstractMediator {

        @SuppressWarnings("rawtypes")
        javax.measure.Unit unit;

        public FunctionalUnit(javax.measure.Unit<?> unit) {
            this.unit = unit;
        }
    }

    private Map<String, javax.measure.Unit<?>> units = Collections.synchronizedMap(new HashMap<>());
    private SimpleUnitFormat formatter;

    public UnitServiceImpl() {

        this.formatter = SimpleUnitFormat.getInstance(SimpleUnitFormat.Flavor.ASCII);
        formatter.label(tech.units.indriya.unit.Units.LITRE, "L");
        formatter.label(tech.units.indriya.unit.Units.WEEK, "wk");
        formatter.label(NonSI.DEGREE_ANGLE, "degree_angle");

        // necessary ugliness
        UnitImpl.setService(this);
        this.meters = getUnit("m");
        this.squareMeters = getUnit("m^2");
    }

    @Override
    public Unit getDefaultUnitFor(Concept concept) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Unit getUnit(String string) {

        if (string.trim().isEmpty()) {
            return null;
        }

        Pair<Double, String> pd = Utils.Strings.splitNumberFromString(string);
        javax.measure.Unit<?> peer = units.get(pd.getSecond());
        if (peer == null) {
            try {
                peer = (javax.measure.Unit<?>) formatter.parse(string);
                this.units.put(pd.getSecond(), peer);
            } catch (Throwable e) {
                // KLAB-156: Error getting the default unit
                // caught in org.integratedmodelling.klab.model.Model.java:488
                throw new KlabValidationException("Invalid unit: " + string);
            }
        }

        if (pd.getFirst() != null) {
            double factor = pd.getFirst();
            if (factor != 1.0) {
                peer = peer.multiply(factor);
            }
        }

        return new UnitImpl(string).withData(new FunctionalUnit(peer));
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

    @Override
    public boolean isCompatible(Unit unit, Unit other) {
        // TODO Auto-generated method stub
        return false;
    }

    @SuppressWarnings("unchecked")
    @Override
    public Number convert(Number d, Unit from, Unit to) {
        // hostia
        return ((UnitImpl) to).data(FunctionalUnit.class).unit.getConverterTo(((UnitImpl) from).data(FunctionalUnit.class).unit)
                .convert(d);
    }

    @Override
    public Number convert(Number value, Unit unit, Locator locator) {

        if (Utils.Data.isNodata(value)) {
            return value;
        }

        UnitImpl ui = (UnitImpl) unit;

        // try {

        /*
         * trivial cases: no context, intensive semantics, or original unit required no
         * transformation. Also no locator so no context information, although this may generate
         * unseen errors. FIXME the locator condition should be removed once the data builders'
         * add() accepts a locator.
         */
        // if (ui.data(FunctionalUnit.class).mediators == null || locator == null) {
        // return this.convert(value, ui.data(FunctionalUnit.class).unit);
        // }

        return null;
    }

    @Override
    public Unit multiply(Unit unit, Unit other) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Unit divide(Unit unit, Unit other) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Unit scale(Unit unit, double scale) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Pair<Unit, Unit> splitExtent(Unit unit, ExtentDimension dimension) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Unit contextualize(Unit unit, Observable observable, Geometry scale) {
        // TODO Auto-generated method stub
        return null;
    }

}
