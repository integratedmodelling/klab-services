package org.integratedmodelling.klab.data.mediation;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import org.integratedmodelling.klab.api.data.mediation.impl.UnitImpl;
import org.integratedmodelling.klab.api.knowledge.observation.scale.*;
import org.integratedmodelling.klab.api.knowledge.observation.scale.time.Time;
import org.junit.jupiter.api.Test;

class TemporalDerivativeUnitTest {
  private final UnitServiceImpl units = new UnitServiceImpl();
  private UnitImpl derivative(String unit) { return (UnitImpl)units.temporalDerivative(units.getUnit(unit)); }
  @Test void physicalUnitsHigherDerivativesAndSignedValues() {
    var mass = derivative("kg");var speed = derivative("m/s");
    assertTrue(units.isCompatible(units.getUnit(mass.getDefinition()),units.getUnit("kg/s")));
    assertTrue(units.isCompatible(units.getUnit(speed.getDefinition()),units.getUnit("m/s^2")));
    var second = (UnitImpl)units.temporalDerivative(mass);
    assertTrue(units.isCompatible(units.getUnit(second.getDefinition()),units.getUnit("kg/s^2")));
    assertEquals(ExtentDistribution.INTENSIVE,mass.getAggregatedDimensions().get(ExtentDimension.TEMPORAL));
    assertEquals(-2,units.convert(-2,units.getUnit("kg/s"),units.getUnit(mass.getDefinition())).doubleValue());
    var scaled = derivative("km/h");
    assertEquals(1,units.convert(3.6,units.getUnit("m/s^2"),units.getUnit(scaled.getDefinition())).doubleValue(),1e-12);
    var viscosity = derivative("100 Pa*s");
    assertEquals(100,units.convert(1,units.getUnit("Pa"),units.getUnit(viscosity.getDefinition())).doubleValue(),1e-10);
  }
  @Test void affineTemperatureUsesDifferenceScaleWithoutOffset() {
    var celsius = derivative("Cel");var kelvin = derivative("K");
    assertTrue(units.isCompatible(units.getUnit(celsius.getDefinition()),units.getUnit("K/s")));
    var conversion = units.conversion(units.getUnit(kelvin.getDefinition()),units.getUnit(celsius.getDefinition()));
    assertEquals(1,conversion.factor());assertEquals(0,conversion.offset());
    assertEquals(-3,conversion.convert(-3));
    assertEquals(273.15,units.convert(0,units.getUnit("K"),units.getUnit("Cel")).doubleValue(),1e-10);
  }
  @Test void temporalDistributionDoesNotOverwriteSpatialDistributionOrEnableContextualMediation() {
    var total = (UnitImpl)units.getUnit("kg");total.setAggregatedDimensions(Map.of(ExtentDimension.AREAL,ExtentDistribution.EXTENSIVE));
    var density = (UnitImpl)units.getUnit("kg/m^2");density.setAggregatedDimensions(Map.of(ExtentDimension.AREAL,ExtentDistribution.INTENSIVE));
    var massRate = units.temporalDerivative(total);var densityRate = units.temporalDerivative(density);
    assertEquals(Map.of(ExtentDimension.AREAL,ExtentDistribution.EXTENSIVE,ExtentDimension.TEMPORAL,ExtentDistribution.INTENSIVE),massRate.getAggregatedDimensions());
    assertEquals(ExtentDistribution.INTENSIVE,densityRate.getAggregatedDimensions().get(ExtentDimension.AREAL));
    assertEquals(Map.of(ExtentDimension.AREAL,ExtentDistribution.EXTENSIVE),total.getAggregatedDimensions());
    assertThrows(UnsupportedOperationException.class,()->units.conversion(massRate,massRate));
    assertNull(units.contextualize(massRate,null,null),"General contextual mediation remains unfinished");
  }
  @Test void existingLocatorArithmeticUsesTimeSeparatelyFromSpatialAdditivity() {
    // Exercise the existing prepared-operation engine, not an ODE solver or automatic planner.
    var operation = new AbstractMediator.Mediation();operation.extentSize=AbstractMediator.ExtentSize.TIME_MS;
    operation.operation=AbstractMediator.Operation.MULTIPLY;operation.factor=0.001;
    var mediator=new AbstractMediator(){};mediator.setMediation(null,List.of(operation));
    var first=mock(Time.class);var second=mock(Time.class);
    when(first.getDimensionSize()).thenReturn(30_000.0);when(second.getDimensionSize()).thenReturn(60_000.0);
    assertEquals(60,mediator.convert(1.0+1.0,first).doubleValue()); // two disjoint total-mass sources, kg/s
    assertEquals(120,mediator.convert(1.0+1.0,second).doubleValue());
    assertEquals(-90,mediator.convert(-3.0,first).doubleValue()); // signed temperature difference, K
  }
}
