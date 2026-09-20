package org.integratedmodelling.klab.data.mediation;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import org.integratedmodelling.klab.api.data.mediation.impl.CurrencyImpl;
import org.integratedmodelling.klab.api.data.mediation.impl.NumericRangeImpl;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.geometry.Locator;
import org.integratedmodelling.klab.api.knowledge.observation.scale.space.Space;
import org.integratedmodelling.klab.api.knowledge.observation.scale.time.Time;
import org.junit.jupiter.api.Test;

/** Known gaps are characterized explicitly; passing these tests does not enable scanner mediation. */
class UnitMediationBaselineTest {
  @Test
  void receiverIsDestinationButServiceParameterNamesSuggestTheOpposite() {
    var service = new UnitServiceImpl();
    var meters = service.getUnit("m");
    var millimeters = service.getUnit("mm");
    assertEquals(0.002, meters.convert(2, millimeters).doubleValue(), 1e-12);
    assertEquals(2000, millimeters.convert(2, meters).doubleValue(), 1e-9);
    // Current service arguments are effectively (target, source), despite names (from, to).
    assertEquals(2000, service.convert(2, millimeters, meters).doubleValue(), 1e-9);
    var celsius = service.getUnit("Cel");
    var kelvin = service.getUnit("K");
    assertEquals(273.15, kelvin.convert(0, celsius).doubleValue(), 1e-9);
    assertEquals(0, celsius.convert(273.15, kelvin).doubleValue(), 1e-9);
    // Reparse definitions as a restart would; executable converter data are not durable identity.
    assertEquals(0.002, service.getUnit("m")
        .convert(2, service.getUnit("mm")).doubleValue(), 1e-12);
  }

  @Test
  void contextualServiceAndCurrencyRemainStubsWhileRangeConversionWorksForBoundedInputs() {
    var service = new UnitServiceImpl();
    var meters = service.getUnit("m");
    assertTrue(service.isCompatible(meters, service.getUnit("mm")));
    assertNull(service.contextualize(meters, null, Geometry.UNIVERSAL), "Known stage-5 gap");
    assertNull(service.convert(1, meters, (Locator) null), "Known stage-5 gap");
    assertTrue(Double.isNaN(service.convert(Double.NaN, meters, (Locator) null).doubleValue()));
    var percent = NumericRangeImpl.create(0, 100);
    var fraction = NumericRangeImpl.create(0, 1);
    assertEquals(25, percent.convert(0.25, fraction).doubleValue());
    assertEquals(0.25, fraction.convert(25, percent).doubleValue());
    assertThrows(IllegalArgumentException.class, () -> percent.convert(2, fraction));
    assertNull(percent.convert(1, (Locator) null), "Range locator conversion is also unfinished");
    var currency = new CurrencyImpl("USD");
    assertFalse(currency.isCompatible(new CurrencyImpl("EUR")));
    assertThrows(UnsupportedOperationException.class, () -> currency.convert(1, new CurrencyImpl("EUR")));
  }

  @Test
  void existingOperationEngineUsesEachLocatorInsteadOfCachingWholeContextAreaOrDuration() {
    var areaOperation = new AbstractMediator.Mediation();
    areaOperation.extentSize = AbstractMediator.ExtentSize.SPACE_M2;
    areaOperation.operation = AbstractMediator.Operation.MULTIPLY;
    areaOperation.factor = 0.001; // depth mm -> m, then multiply by area m2
    var mediator = new AbstractMediator() {};
    mediator.setMediation(null, List.of(areaOperation));
    var first = mock(Space.class);
    var second = mock(Space.class);
    when(first.getStandardizedArea()).thenReturn(100.0);
    when(second.getStandardizedArea()).thenReturn(250.0);
    assertEquals(1, mediator.convert(10, first).doubleValue());
    assertEquals(2.5, mediator.convert(10, second).doubleValue());
    assertEquals(1, mediator.convert(10, first).doubleValue());

    var duration = new AbstractMediator.Mediation();
    duration.extentSize = AbstractMediator.ExtentSize.TIME_MS;
    duration.operation = AbstractMediator.Operation.DIVIDE;
    duration.factor = 1.0 / 86_400_000;
    mediator.setMediation(null, List.of(duration));
    var february = mock(Time.class);
    var march = mock(Time.class);
    when(february.getDimensionSize()).thenReturn(28.0 * 86_400_000);
    when(march.getDimensionSize()).thenReturn(31.0 * 86_400_000);
    assertEquals(31, mediator.convert(868, february).doubleValue(), 1e-9);
    assertEquals(28, mediator.convert(868, march).doubleValue(), 1e-9);
  }
}
