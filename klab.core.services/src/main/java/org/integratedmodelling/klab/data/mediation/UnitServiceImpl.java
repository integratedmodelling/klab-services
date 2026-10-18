package org.integratedmodelling.klab.data.mediation;

import org.integratedmodelling.klab.api.collections.Pair;
import org.integratedmodelling.klab.api.data.mediation.Unit;
import org.integratedmodelling.klab.api.data.mediation.impl.UnitImpl;
import org.integratedmodelling.klab.api.exceptions.KlabValidationException;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.geometry.Locator;
import org.integratedmodelling.klab.api.knowledge.Concept;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.observation.scale.ExtentDimension;
import org.integratedmodelling.klab.api.services.UnitService;
import org.integratedmodelling.klab.api.utils.Utils;
import si.uom.NonSI;
import tech.units.indriya.format.SimpleUnitFormat;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class UnitServiceImpl implements UnitService {

  private Unit meters;
  private Unit squareMeters;
  private Unit milliseconds;

  class FunctionalUnit extends AbstractMediator {

    @SuppressWarnings("rawtypes")
    javax.measure.Unit unit;

    public FunctionalUnit(javax.measure.Unit<?> unit) {
      this.unit = unit;
    }
  }

  private Map<String, javax.measure.Unit<?>> units = new ConcurrentHashMap<>();
  private SimpleUnitFormat formatter;

  public UnitServiceImpl() {

    this.formatter = SimpleUnitFormat.getInstance(SimpleUnitFormat.Flavor.ASCII);
    formatter.label(tech.units.indriya.unit.Units.LITRE, "L");
    formatter.label(tech.units.indriya.unit.Units.WEEK, "wk");
    formatter.label(NonSI.DEGREE_ANGLE, "degree_angle");

    // necessary ugliness
    UnitImpl.setService(this);
    this.meters = getUnit("m");
    this.milliseconds = getUnit("ms");
    this.squareMeters = getUnit("m^2");
  }

  @Override
  public Unit getDefaultUnitFor(Concept concept) {
    // Direct annotations only; operator construction materializes an unambiguous inherited unit.
    Object definition = concept.getMetadata().get("odo:unit");
    if (definition == null) return null;
    if (!(definition instanceof String text) || text.isBlank())
      throw new IllegalArgumentException("Invalid default unit annotation");
    var result = (UnitImpl)getUnit(text);
    if (concept.is(org.integratedmodelling.klab.api.knowledge.SemanticType.RATE))
      result.setAggregatedDimensions(Map.of(ExtentDimension.TEMPORAL,
          org.integratedmodelling.klab.api.knowledge.observation.scale.ExtentDistribution.INTENSIVE));
    return result;
  }

  @Override
  public Unit temporalDerivative(Unit operand) {
    if (!(operand instanceof UnitImpl definition) || definition.isContextual())
      throw new UnsupportedOperationException("Derivative units require an ordinary unit definition");
    var ordinary = getUnit(definition.getDefinition());
    var source = peer(ordinary);
    var system = source.getSystemUnit();
    var systemUnit = getUnit(formatter.format(system));
    // Use only the affine scale: d(a*x+b)/dt = a*dx/dt. In particular Cel/s is K/s,
    // without adding 273.15 to a derivative value. No numerical integration occurs here.
    double factor = conversion(systemUnit, ordinary).factor();
    var physicalDerivative = system.divide(tech.units.indriya.unit.Units.SECOND);
    var derivative = physicalDerivative.multiply(factor);
    // Indriya may format a transformed unit with a numeric factor inside a product,
    // which its ASCII parser cannot read back. Our portable unit syntax already
    // supports an explicit leading scale followed by an ordinary physical unit.
    String physicalDefinition = formatter.format(physicalDerivative);
    String portableDefinition = factor == 1.0 ? physicalDefinition : factor + " " + physicalDefinition;
    var result = new UnitImpl(portableDefinition).withData(new FunctionalUnit(derivative));
    var distributions = new HashMap<>(operand.getAggregatedDimensions());
    distributions.put(ExtentDimension.TEMPORAL,
        org.integratedmodelling.klab.api.knowledge.observation.scale.ExtentDistribution.INTENSIVE);
    result.setAggregatedDimensions(distributions);
    return result;
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
        peer = (javax.measure.Unit<?>) formatter.parse(pd.getSecond());
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
  public Unit meters() {
    return meters;
  }

  @Override
  public Unit squareMeters() {
    return squareMeters;
  }

  @Override
  public Unit milliseconds() {
    return milliseconds;
  }

  @Override
  public boolean isCompatible(Unit unit, Unit other) {
    if (unit == null || other == null) return false;
    return peer(unit).isCompatible(peer(other));
  }

  private javax.measure.Unit<?> peer(Unit unit) {
    if (!(unit instanceof UnitImpl definition)) throw new IllegalArgumentException("Unit definition is not portable");
    if (definition.isContextual() || !definition.getAggregatedDimensions().isEmpty())
      throw new UnsupportedOperationException("Contextual unit conversion requires cell support (S5)");
    var functional = definition.data(FunctionalUnit.class);
    return functional == null ? ((UnitImpl)getUnit(definition.getDefinition())).data(FunctionalUnit.class).unit : functional.unit;
  }

  @Override
  public Conversion conversion(Unit destination, Unit source) {
    try {
      var converter = peer(source).getConverterToAny(peer(destination));
      for (var step : converter.getConversionSteps())
        if (!step.isLinear() && !(step instanceof tech.units.indriya.function.AddConverter))
          throw new UnsupportedOperationException("Non-affine unit conversion is not supported");
      double zero = converter.convert(0.0);
      return new Conversion(converter.convert(1.0) - zero, zero);
    } catch (javax.measure.IncommensurableException | javax.measure.UnconvertibleException e) {
      throw new IllegalArgumentException("Incompatible units", e);
    }
  }

  @Override
  public Number convert(Number value, Unit destination, Unit source) {
    if (value == null || Double.isNaN(value.doubleValue())) return value;
    return conversion(destination, source).convert(value.doubleValue());
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

  @Override
  public String serviceName() {
    return "k.LAB Unit Service";
  }
}
