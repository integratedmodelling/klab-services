package org.integratedmodelling.klab.runtime.storage;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.time.Instant;
import java.util.*;
import org.integratedmodelling.common.knowledge.ObservableImpl;
import org.integratedmodelling.klab.api.data.*;
import org.integratedmodelling.klab.api.data.mediation.impl.*;
import org.integratedmodelling.klab.api.digitaltwin.*;
import org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.CurrencyService;
import org.integratedmodelling.klab.configuration.ServiceConfiguration;
import org.integratedmodelling.klab.data.mediation.UnitServiceImpl;
import org.junit.jupiter.api.*;

class ValueMediationTest {
  @BeforeAll static void configure() { ServiceConfiguration.injectInstantiators(); }
  private static StorageScan.Semantics semantics(String unit, String range, String currency) {
    return new StorageScan.Semantics("test:quality", unit, range, currency, "");
  }
  private static CurrencyService.Rate rate(String source, String target, double factor) {
    return new CurrencyService.Rate(source, target, Instant.parse("2026-01-01T00:00:00Z"), "fixture", "v1",
        CurrencyService.Operation.EXCHANGE, CurrencyService.Rounding.IEEE_754_BINARY64, factor);
  }
  @Test void asymmetricUnitsAffineOffsetsAndRestartDefinitions() {
    var units = new UnitServiceImpl();
    assertTrue(new UnitImpl("m").isCompatible(new UnitImpl("mm")));
    assertFalse(new UnitImpl("m").isCompatible(new CurrencyImpl("USD")));
    assertEquals(0.002, units.convert(2, new UnitImpl("m"), new UnitImpl("mm")).doubleValue(), 1e-12);
    assertEquals(2000, ValueMediation.kernel(ValueMediation.compile(semantics("m", "", ""), semantics("mm", "", ""), null)).apply(2));
    var kelvin = ValueMediation.kernel(ValueMediation.compile(semantics("Cel", "", ""), semantics("K", "", ""), null));
    assertEquals(273.15, kelvin.apply(0), 1e-12);
    assertTrue(Double.isNaN(kelvin.apply(Double.NaN)));
    assertEquals(2, units.convert(1, units.getUnit("m"), units.getUnit("2 m")).doubleValue());
    assertEquals(1, units.convert(1, units.getUnit("m"), units.getUnit("m")).doubleValue());
    assertThrows(IllegalArgumentException.class, () -> ValueMediation.compile(semantics("m", "", ""), semantics("s", "", ""), null));
    assertThrows(IllegalArgumentException.class, () -> ValueMediation.compile(semantics("mm", "", ""), semantics("m^3", "", ""), null));
  }
  @Test void rangeDomainsOpenEndpointsDegeneracyAndNoClipping() {
    var source = semantics("", "0.0:false:100.0:false", "");
    var target = semantics("", "-1.0:false:1.0:false", "");
    var kernel = ValueMediation.kernel(ValueMediation.compile(source, target, null));
    assertEquals(-1, kernel.apply(0)); assertEquals(0, kernel.apply(50)); assertEquals(1, kernel.apply(100));
    assertThrows(IllegalArgumentException.class, () -> kernel.apply(-0.01));
    assertThrows(IllegalArgumentException.class, () -> kernel.apply(Double.POSITIVE_INFINITY));
    assertTrue(Double.isNaN(kernel.apply(Double.NaN)));
    var open = ValueMediation.kernel(ValueMediation.compile(semantics("", "0:true:100:false", ""), semantics("", "0:true:1:false", ""), null));
    assertThrows(IllegalArgumentException.class, () -> open.apply(0)); assertEquals(1, open.apply(100));
    var upperOpen = ValueMediation.kernel(ValueMediation.compile(semantics("", "0:false:100:true", ""), semantics("", "0:false:1:true", ""), null));
    assertThrows(IllegalArgumentException.class, () -> upperOpen.applyFloat(99.9999999));
    for (String invalid : List.of("0:false:0:false", "1:false:0:false", "0:false:Infinity:false"))
      assertThrows(IllegalArgumentException.class, () -> ValueMediation.compile(source, semantics("", invalid, ""), null));
    assertThrows(IllegalArgumentException.class, () -> ValueMediation.compile(source, semantics("", "0:true:1:false", ""), null));
  }
  @Test void pinnedCurrencyRatesDirectionAndInflationAreExplicit() throws Exception {
    CurrencyService.RateProvider provider = (source, target, valuation, operation) -> rate(source, target, 0.8);
    var quote = provider.quote("USD", "EUR", Instant.parse("2026-01-01T00:00:00Z"), CurrencyService.Operation.EXCHANGE);
    var conversion = ValueMediation.compile(semantics("", "", "USD"), semantics("", "", "EUR"), quote);
    assertEquals(80, ValueMediation.kernel(conversion).apply(100));
    var mapper = org.integratedmodelling.common.data.jackson.JacksonConfiguration.newObjectMapper();
    assertEquals(conversion, mapper.readValue(mapper.writeValueAsString(conversion), StorageScan.Conversion.class));
    assertThrows(IllegalArgumentException.class, () -> ValueMediation.compile(semantics("", "", "USD"), semantics("", "", "EUR"), null));
    assertThrows(IllegalArgumentException.class, () -> ValueMediation.compile(semantics("", "", "EUR"), semantics("", "", "USD"), quote));
    assertThrows(IllegalArgumentException.class, () -> rate("USD@2020", "EUR@2021", 1.2));
    assertThrows(IllegalArgumentException.class, () -> rate("USD", "EUR", Double.NaN));
    var inflation = new CurrencyService.Rate("USD@2020", "USD@2021", quote.valuation(), "fixture", "v1",
        CurrencyService.Operation.INFLATION, quote.rounding(), 1.02);
    assertEquals(102, ValueMediation.kernel(ValueMediation.compile(semantics("", "", inflation.source()), semantics("", "", inflation.target()), inflation)).apply(100));
  }
  @Test void mediatedPartitionQueryExportAndPointViewsPreserveNativeSource() throws Exception {
    for (var type : List.of(Storage.Type.FLOAT, Storage.Type.DOUBLE))
      try (var f = new ConformantScanTest.Fixture(3, type, Data.FillCurve.D2_XInvY)) {
        var source = (ObservationImpl)f.storage.temporalOwner(); source.setId(42);
        var original = (ObservableImpl)source.getObservable(); original.setUnit(new UnitImpl("m"));
        var requested = new ObservableImpl(original); requested.setUnit(new UnitImpl("mm")); requested.setUrn("test:quality in mm");
        var query = source.copyForAttribution(requested); query.setId(0);
        query.getMetadata().put(Metadata.IM_QUERY_SOURCE_IDS, List.of(42L));
        var scope = mock(ContextScope.class, RETURNS_DEEP_STUBS);
        when(scope.getObservation(42L)).thenReturn(source);
        when(scope.getDigitalTwin().getStorageManager().getStorage(source)).thenReturn(f.storage);
        assertTrue(StorageReadInspector.check(scope, query, Data.FillCurve.D2_YX, 4, 8));
        assertTrue(StorageReadInspector.unitCheck(scope, source, "mm", 1000, 0));
        var request = StorageReads.request(query, null, new Data.ShardingStrategy(Data.FillCurve.D2_YX,4,0,0,null), List.of(), Storage.DoubleScanner.class);
        var plan = f.storage.plan(request);
        assertEquals(3, plan.description().version());
        var mapper = org.integratedmodelling.common.data.jackson.JacksonConfiguration.newObjectMapper();
        var restored = mapper.readValue(mapper.writeValueAsString(plan.description()), StorageScan.Description.class);
        assertEquals(plan.description(), restored); assertEquals(plan.description().fingerprint(), restored.fingerprint());
        try (var session = f.storage.open(plan)) {
          for (var scanner : session.scanners()) {
            var cells = ConformantScanTest.cells(scanner.view().partition().geometry(), Data.FillCurve.D2_YX);
            for (var cell : cells) {
              double expected = (ConformantScanTest.code(cell)+0.25)*1000;
              long before = scanner.position();
              assertEquals(expected, scanner.peek()); assertEquals(expected, scanner.peek());
              assertEquals(before, scanner.position()); assertEquals(expected, scanner.get());
            }
          }
        }
        var point = new StorageScan.Point(42, request.slice(), Data.FillCurve.D2_YX, null, request.semantics(), 1);
        assertEquals(type == Storage.Type.FLOAT ? Float.toString(100250f) : Double.toString(100250), StorageReads.text(point, scope));
        assertSame(original, source.getObservable()); assertEquals("m", ((UnitImpl)original.getUnit()).getDefinition());
        verify(scope.getDigitalTwin().getStorageManager(), never()).createStorage(any());
      }
  }
  @Test void rangeAndCurrencyDescriptionsDriveRealScannersAndCellText() {
    for (boolean currency : List.of(false,true))
      try (var f = new ConformantScanTest.Fixture(3,Storage.Type.DOUBLE,Data.FillCurve.D2_XY)) {
        var source=(ObservationImpl)f.storage.temporalOwner(); source.setId(42);
        var original=(ObservableImpl)source.getObservable();
        if(currency) original.setCurrency(new CurrencyImpl("USD")); else original.setRange(NumericRangeImpl.create(0,1000));
        var target=new ObservableImpl(original);
        if(currency) target.setCurrency(new CurrencyImpl("EUR")); else target.setRange(NumericRangeImpl.create(0,1));
        var view=(ObservationImpl)StorageReads.binding(source,target);
        var quote=currency?rate("USD","EUR",0.8):null;
        if(quote!=null)view.getMetadata().put(StorageReads.RATE,org.integratedmodelling.klab.utilities.Utils.Json.asString(quote));
        var scope=mock(ContextScope.class,RETURNS_DEEP_STUBS);when(scope.getObservation(42L)).thenReturn(source);
        when(scope.getDigitalTwin().getStorageManager().getStorage(source)).thenReturn(f.storage);
        try(var session=StorageReads.open(view,scope,null,Data.FillCurve.D2_YX,Storage.DoubleScanner.class)) {
          var cursor=session.scanners().getFirst(); cursor.seek(1);
          double expected=100.25*(currency?0.8:0.001);
          assertEquals(expected,cursor.peek(),1e-12);
          var point=new StorageScan.Point(42,session.description().slice(),Data.FillCurve.D2_YX,null,StorageScan.semantics(target),1,quote);
          assertEquals(Double.toString(cursor.peek()),StorageReads.text(point,scope));
          assertEquals(currency?"CURRENCY":"RANGE",session.description().conversion().kind());
        }
      }
  }

  @Test void bindingFollowsSourceIdentityAssignedAfterCompilation() throws Exception {
    try(var f=new ConformantScanTest.Fixture(1,Storage.Type.DOUBLE,Data.FillCurve.D2_XY)) {
      var source=(ObservationImpl)f.storage.temporalOwner();
      var original=(ObservableImpl)source.getObservable();original.setUnit(new UnitImpl("m"));
      var target=new ObservableImpl(original);target.setUnit(new UnitImpl("mm"));
      var binding=StorageReads.binding(source,target);assertEquals(-1,binding.getId());
      source.setId(42);source.setUrn("context.42");
      assertEquals(42,binding.getId());assertEquals("context.42",binding.getUrn());
      assertSame(source,StorageReads.source(binding,mock(ContextScope.class)));
      var portable=org.integratedmodelling.klab.api.knowledge.observation.Observation.forTransport(binding);
      assertEquals(42,portable.getId());assertNull(((ObservationImpl)portable).storageSource());
      var mapper=org.integratedmodelling.common.data.jackson.JacksonConfiguration.newObjectMapper();
      var decoded=mapper.readValue(mapper.writeValueAsString(binding),org.integratedmodelling.klab.api.knowledge.observation.Observation.class);
      assertEquals(42,decoded.getId());assertNull(((ObservationImpl)decoded).storageSource());
      assertSame(original,source.getObservable());assertSame(target,binding.getObservable());
    }
  }

  @Test void oldSemanticSnapshotsKeepExactNativeReadsSourceCompatible() {
    try(var f=new ConformantScanTest.Fixture(1,Storage.Type.DOUBLE,Data.FillCurve.D2_XY)) {
      var current=StorageScan.semantics(f.storage.temporalOwner().getObservable());
      var legacy=new StorageScan.Semantics(current.observable(),current.unit(),current.range(),current.currency(),current.contextualDimensions());
      var request=new StorageScan.Request<>(StorageScan.Slice.of(Scheduler.Event.initialization()),StorageScan.Layout.of(f.strategy),
          null,List.of(),legacy,Storage.DoubleScanner.class,StorageScan.Access.READ_ONLY,StorageScan.Precision.LOSSLESS,
          StorageScan.Coverage.EXACT,StorageScan.Sampling.EXACT,StorageScan.Budget.defaults());
      try(var read=f.storage.open(f.storage.plan(request))){assertEquals(0.25,read.scanners().getFirst().get());assertNull(read.description().conversion());}
    }
  }

  @Test void mediatedScannersPreserveNaNsNarrowOnceAndRejectIntegerConversions() {
    try (var f = new ConformantScanTest.Fixture(1, Storage.Type.DOUBLE, Data.FillCurve.D2_XY)) {
      var source = f.storage.temporalOwner(); var original = (ObservableImpl)source.getObservable(); original.setUnit(new UnitImpl("Cel"));
      var out = f.storage.scan(Scheduler.Event.initialization(), f.strategy, Storage.DoubleScanner.class, false).getFirst();
      out.add(Double.NaN); out.add(0); f.storage.finalizeRun(out);
      var target = new ObservableImpl(original); target.setUnit(new UnitImpl("K"));
      var request = StorageReads.request(StorageReads.binding(source, target), null, f.strategy, List.of(), Storage.FloatScanner.class);
      try (var session = f.storage.open(f.storage.plan(request))) {
        var scan = session.scanners().getFirst(); assertFalse(scan.isValid());
        assertTrue(Float.isNaN(scan.peek())); assertTrue(Float.isNaN(scan.get()));
        assertEquals((float)273.15, scan.peek()); assertEquals((float)273.15, scan.get());
      }
    }
    try (var f = new ConformantScanTest.Fixture(1, Storage.Type.INTEGER, Data.FillCurve.D2_XY)) {
      var original = (ObservableImpl)f.storage.temporalOwner().getObservable(); original.setUnit(new UnitImpl("m"));
      var target = new ObservableImpl(original); target.setUnit(new UnitImpl("mm"));
      assertThrows(UnsupportedOperationException.class, () -> f.storage.plan(StorageReads.request(
          StorageReads.binding(f.storage.temporalOwner(), target), null, f.strategy, List.of(), Storage.Scanner.class)));
    }
  }
  private static volatile double allocationSink;
  @Test void movingMediatedCursorDoesNotBoxPerValue() {
    var bean = java.lang.management.ManagementFactory.getThreadMXBean();
    Assumptions.assumeTrue(bean instanceof com.sun.management.ThreadMXBean);
    var allocations = (com.sun.management.ThreadMXBean)bean;
    Assumptions.assumeTrue(allocations.isThreadAllocatedMemorySupported());
    allocations.setThreadAllocatedMemoryEnabled(true);
    try (var f = new ConformantScanTest.Fixture(3,Storage.Type.FLOAT,Data.FillCurve.D2_XInvY)) {
      var original = (ObservableImpl)f.storage.temporalOwner().getObservable(); original.setUnit(new UnitImpl("Cel"));
      var target = new ObservableImpl(original); target.setUnit(new UnitImpl("K"));
      var request = StorageReads.request(StorageReads.binding(f.storage.temporalOwner(),target),null,
          new Data.ShardingStrategy(Data.FillCurve.D2_YX,1,0,0,null),List.of(),Storage.DoubleScanner.class);
      try (var session = f.storage.open(f.storage.plan(request))) {
        var scan = session.scanners().getFirst(); double sum=0;
        for (int i=0;i<200000;i++) { scan.seek(i%20); sum+=scan.get(); }
        long id = Thread.currentThread().threadId(); long before=allocations.getThreadAllocatedBytes(id);
        for (int i=0;i<1000000;i++) { scan.seek(i%20); sum+=scan.get(); }
        long bytes=allocations.getThreadAllocatedBytes(id)-before; allocationSink=sum;
        assertTrue(bytes<65536,"Per-value allocation: " + bytes);
        assertTrue(allocationSink>0);
        System.out.println("Mediated moving cursor allocation: " + bytes + " bytes / 1000000 reads");
      }
    }
  }

  @Test void distinctBindingsRoundTripAndRunEvidenceRollsBack() throws Exception {
    var source = semantics("m", "", ""); var mm = semantics("mm", "", ""); var km = semantics("km", "", "");
    var first = new StorageScan.Binding(1,"inputA:0",source,mm,ValueMediation.compile(source,mm,null));
    var second = new StorageScan.Binding(1,"inputB:0",source,km,ValueMediation.compile(source,km,null));
    var mapper = org.integratedmodelling.common.data.jackson.JacksonConfiguration.newObjectMapper();
    for (var binding : List.of(first, second)) {
      var decoded = mapper.readValue(mapper.writeValueAsString(binding), StorageScan.Binding.class);
      assertEquals(binding, decoded);
      assertEquals(binding.conversion(), ValueMediation.compile(decoded.source(), decoded.target(), null));
    }
    assertNotEquals(first,second);
    assertThrows(IllegalArgumentException.class, () -> new StorageScan.Binding(2,"input",source,source,null));
    try (var f = new ConformantScanTest.Fixture(1,Storage.Type.DOUBLE,Data.FillCurve.D2_XY)) {
      var scope = mock(ContextScope.class, RETURNS_DEEP_STUBS);
      var metadata = Metadata.create(); when(scope.getCurrentTransaction().getActivity().getMetadata()).thenReturn(metadata);
      var rollback = new ArrayList<Runnable>();
      var transaction = scope.getCurrentTransaction();
      doAnswer(call -> { rollback.add(call.getArgument(0)); return null; }).when(transaction).afterRollback(any());
      var description = f.storage.plan(f.request(1,Data.FillCurve.D2_XY,0,0,List.of(),null)).description();
      StorageReads.record(scope,"inputA",description); StorageReads.record(scope,"inputB",description);
      assertEquals(2,metadata.size());
      for (var value : metadata.values()) assertEquals(description, mapper.readValue(value.toString(),StorageReads.Evidence.class).plan());
      rollback.forEach(Runnable::run); assertTrue(metadata.isEmpty());
    }
  }
}
