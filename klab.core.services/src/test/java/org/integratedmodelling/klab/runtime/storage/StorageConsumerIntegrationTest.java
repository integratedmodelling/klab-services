package org.integratedmodelling.klab.runtime.storage;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.io.*;
import java.util.*;
import org.integratedmodelling.klab.api.data.*;
import org.integratedmodelling.klab.api.digitaltwin.Scheduler;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.runtime.language.*;
import org.integratedmodelling.klab.runtime.libraries.CoreActorLibrary.Inspector;
import org.integratedmodelling.klab.configuration.ServiceConfiguration;
import org.junit.jupiter.api.*;

class StorageConsumerIntegrationTest {
  @BeforeAll static void configure() { ServiceConfiguration.injectInstantiators(); }
  private ContextScope scope(ConformantScanTest.Fixture f) {
    var scope = mock(ContextScope.class, RETURNS_DEEP_STUBS);
    when(scope.getDigitalTwin().getStorageManager().getStorage(f.storage.temporalOwner())).thenReturn(f.storage);
    when(scope.getObservation(42L)).thenReturn(f.storage.temporalOwner());
    return scope;
  }
  @Test void scalarExpressionsAndLookupTablesWalkRealCoveredWritersOncePerCell() {
    for (String shape : List.of(SpatialCoverageTest.HOLE,SpatialCoverageTest.ISLANDS))
      for (var curve : List.of(Data.FillCurve.D2_XY,Data.FillCurve.D2_YX,Data.FillCurve.D2_XInvY))
        for (int mode=0;mode<6;mode++) {
          try (var f=new ConformantScanTest.Fixture(3,Storage.Type.DOUBLE,curve,SpatialCoverageTest.geometry(shape));
               var source=new ConformantScanTest.Fixture(3,Storage.Type.DOUBLE,curve,SpatialCoverageTest.geometry(shape));
               var inputs=source.storage.open(source.storage.plan(source.request(3,curve,0,0,List.of(),null)))) {
            var target=(ObservationImpl)f.storage.temporalOwner();
            var data=new ObservationImpl.ContextualizationDataImpl(); data.setNativeShardingStrategy(f.strategy);
            target.setContextualizationData(data);
            ((ObservationImpl)source.storage.temporalOwner()).setContextualizationData(data);
            var builder=org.integratedmodelling.klab.runtime.computation.ScalarComputationGroovy.builder(
                target,mock(ContextScope.class),mock(org.integratedmodelling.klab.api.services.runtime.Actuator.class),Map.of("input",source.storage.temporalOwner()));
            if(mode==0 || mode==3 || mode==4) {
              assertTrue(builder.add(new org.integratedmodelling.common.lang.ServiceCallImpl(
                  org.integratedmodelling.klab.api.services.RuntimeService.CoreFunctor.EXPRESSION_RESOLVER.getServiceCallName(),
                  "expression",org.integratedmodelling.klab.api.lang.ExpressionCode.of(mode==4 ? "input * 2" : "self * 2", "groovy"))));
            } else {
              if(mode==2) assertTrue(builder.add(new org.integratedmodelling.common.lang.ServiceCallImpl(
                  org.integratedmodelling.klab.api.services.RuntimeService.CoreFunctor.CONSTANT_RESOLVER.getServiceCallName(),"value",3.0)));
              var table=new org.integratedmodelling.klab.api.lang.kim.impl.KimTableImpl();
              var any=new org.integratedmodelling.klab.api.lang.kim.impl.KimClassifierImpl(); any.setCatchAll(true);
              var result=new org.integratedmodelling.klab.api.lang.kim.impl.KimClassifierImpl();
              result.setExpressionMatch(org.integratedmodelling.klab.api.lang.ExpressionCode.of("self * 2", "groovy"));
              table.setRows(List.<org.integratedmodelling.klab.api.lang.kim.KimClassifier[]>of(new org.integratedmodelling.klab.api.lang.kim.KimClassifier[]{any,result}));
              var lookup=new org.integratedmodelling.klab.api.lang.kim.impl.KimLookupTableImpl(); lookup.setTable(table);
              var input=new org.integratedmodelling.klab.api.lang.kim.KimLookupTable.Argument(); input.id=mode==5 ? "input" : "self";
              var output=new org.integratedmodelling.klab.api.lang.kim.KimLookupTable.Argument(); output.id="?";
              lookup.setArguments(List.of(input,output));
              org.integratedmodelling.klab.api.lang.kim.impl.KimValueMappingValidator.validateAndNormalize(lookup,false);
              assertTrue(builder.add(new org.integratedmodelling.common.lang.ServiceCallImpl(
                  org.integratedmodelling.klab.api.services.RuntimeService.CoreFunctor.LUT_RESOLVER.getServiceCallName(),"lookupTable",lookup)));
            }
            var computation=builder.build(); assertNotNull(computation,target.getNotifications().toString());
            long count=0;
            int part=0;
            for(var scanner:f.storage.scan(Scheduler.Event.initialization(),f.strategy,Storage.DoubleScanner.class,false)) {
              var scanners=new HashMap<String,Storage.Scanner>(); scanners.put("self",scanner);
              if(mode>=3) scanners.put(mode==3 ? "__prior_self" : "input",inputs.scanners().get(part));
              assertTrue(computation.execute(scanners,null,null),target.getNotifications().toString());
              if(mode>=3) assertFalse(inputs.scanners().get(part).hasNext());
              part++;
              assertEquals(scanner.size(),scanner.position()); f.storage.finalizeRun(scanner);
            }
            try(var session=f.storage.open(f.storage.plan(f.request(2,Data.FillCurve.D2_YX,0,0,List.of(),null)))) {
              for(var scanner:session.scanners()) while(scanner.hasNext()) {
                var cell=scanner.cell();
                double expected=mode==2 ? 6 : 2*(100*cell.bounds().getFirst().minX()+cell.bounds().getFirst().minY()+.25);
                assertEquals(expected,((Storage.DoubleScanner)scanner).get()); count++;
              }
            }
            var polygon=org.integratedmodelling.klab.runtime.scale.space.ShapeImpl.create("EPSG:4326 "+shape).getJTSGeometry();
            long expectedCount=0;
            for(int x=0;x<5;x++) for(int y=0;y<4;y++) if(polygon.covers(polygon.getFactory().createPoint(new org.locationtech.jts.geom.Coordinate(x+.5,y+.5)))) expectedCount++;
            assertEquals(expectedCount,count);
          }
        }
  }
  @Test void maskedPointAccessDoesNotReturnTheNextCoveredCellAndInspectorHandlesGaps() {
    try (var f = new ConformantScanTest.Fixture(3,Storage.Type.DOUBLE,Data.FillCurve.D2_XY,
        SpatialCoverageTest.geometry(SpatialCoverageTest.HOLE))) {
      var scope=scope(f); var observation=f.storage.temporalOwner();
      assertEquals("null",StorageReads.text(observation,scope,Scheduler.Event.initialization(),Data.FillCurve.D2_XY,10));
      assertEquals("201.25",StorageReads.text(observation,scope,Scheduler.Event.initialization(),Data.FillCurve.D2_XY,9));
      assertEquals("null",StorageReads.text(new StorageScan.Point(42,StorageScan.Slice.of(Scheduler.Event.initialization()),
          Data.FillCurve.D2_XY,null,null,10),scope));
      assertTrue(StorageReadInspector.check(scope,observation,Data.FillCurve.D2_YX,3,8));
    }
  }
  @Test void exportPointAndInspectorAgreeForAllPrimitiveTypesAndCurves() {
    for (var type : List.of(Storage.Type.DOUBLE, Storage.Type.FLOAT, Storage.Type.INTEGER, Storage.Type.LONG, Storage.Type.BOOLEAN))
      try (var f = new ConformantScanTest.Fixture(3, type, Data.FillCurve.D2_XInvY)) {
        var observation = f.storage.temporalOwner(); var scope = scope(f);
        for (var curve : List.of(Data.FillCurve.D2_XY, Data.FillCurve.D2_YX, Data.FillCurve.D2_XInvY)) {
          assertTrue(Inspector.scancheck(null, scope, observation, curve, 4, 8));
          try (var session = StorageReads.open(observation, scope, null, curve, Storage.Scanner.class)) {
            var scanner = session.scanners().getFirst();
            var cells = ConformantScanTest.cells(scanner.view().partition().geometry(), curve);
            for (int i = 0; i < cells.size(); i++) {
              scanner.seek(i);
              var point = new StorageScan.Point(42, StorageScan.Slice.of(Scheduler.Event.initialization()), curve, null, null, i);
              assertEquals(StorageScan.textValue(scanner), StorageReads.text(point, scope));
              ConformantScanTest.check(scanner, type, ConformantScanTest.code(cells.get(i)));
            }
            assertThrows(IndexOutOfBoundsException.class, () -> scanner.seek(-1));
            assertThrows(IndexOutOfBoundsException.class, () -> scanner.seek(scanner.size()+1));
            scanner.seek(scanner.size()); assertFalse(scanner.hasNext());
          }
        }
      }
  }
  @Test void pointRequestTransportPreservesLongOffsetsAndSemanticPolicy() throws Exception {
    var mapper=org.integratedmodelling.common.data.jackson.JacksonConfiguration.newObjectMapper();
    var point=new StorageScan.Point(42,StorageScan.Slice.of(Scheduler.Event.initialization()),Data.FillCurve.D2_YX,
        null,new StorageScan.Semantics("test:quality","m","","",""),9_000_000_007L);
    assertEquals(point,mapper.readValue(mapper.writeValueAsString(point),StorageScan.Point.class));
    assertThrows(IllegalArgumentException.class,()->new StorageScan.Point(0,point.slice(),point.curve(),null,null,0));
    try(var f=new ConformantScanTest.Fixture(3,Storage.Type.LONG,Data.FillCurve.D2_XY)) {
      var scope=scope(f);
      assertThrows(UnsupportedOperationException.class,()->StorageReads.text(
          new StorageScan.Point(42,point.slice(),point.curve(),null,point.semantics(),0),scope));
      assertThrows(IndexOutOfBoundsException.class,()->StorageReads.text(
          new StorageScan.Point(42,point.slice(),point.curve(),null,null,20),scope));
      verifyNoInteractions(f.manager);
    }
  }

  @Test void detachedQueriesResolveDurableSourceAndKeepIndependentRequests() {
    try (var f = new ConformantScanTest.Fixture(3, Storage.Type.LONG, Data.FillCurve.D2_XY)) {
      var source = (ObservationImpl) f.storage.temporalOwner(); source.setId(42);
      var scope = scope(f);
      var query = new ObservationImpl(); query.setId(0); query.setObservable(source.getObservable());
      query.setGeometry(source.getGeometry()); query.getMetadata().put(Metadata.IM_QUERY_SOURCE_IDS,List.of(42L));
      try (var first = StorageReads.open(query, scope, null, Data.FillCurve.D2_YX, Storage.LongScanner.class);
           var second = StorageReads.open(query, scope, null, Data.FillCurve.D2_XInvY, Storage.LongScanner.class)) {
        assertEquals(Long.MAX_VALUE, first.scanners().getFirst().get());
        assertEquals(Long.MAX_VALUE-3, second.scanners().getFirst().get());
        assertEquals(Long.MAX_VALUE-100, first.scanners().getFirst().get());
        assertEquals(0, query.getId());
        verify(scope.getDigitalTwin().getStorageManager(), never()).getStorage(query);
        verify(scope.getDigitalTwin().getStorageManager(), never()).createStorage(any());
      }
      query.getMetadata().clear();
      assertThrows(IllegalArgumentException.class, () -> StorageReads.open(query,scope,null,Data.FillCurve.D2_XY,Storage.Scanner.class));
    }
  }
  @Test void streamingOwnershipLastsUntilCloseEofOrFailure() throws Exception {
    try (var f = new ConformantScanTest.Fixture(3, Storage.Type.LONG, Data.FillCurve.D2_XY)) {
      for (int mode=0; mode<3; mode++) {
        var resources = new ScanResources();
        var session = resources.add(StorageReads.open(f.storage.temporalOwner(),scope(f),null,Data.FillCurve.D2_YX,Storage.LongScanner.class));
        final int failureMode = mode;
        var stream = resources.transfer(new InputStream() {
          int remaining=2;
          public int read() throws IOException {
            if (failureMode==2) throw new IOException("export failed");
            return remaining-- > 0 ? (int)(session.scanners().getFirst().get()%127) : -1;
          }
        });
        resources.close(); assertFalse(session.isClosed());
        assertThrows(IllegalStateException.class, () -> f.storage.scan(Scheduler.Event.initialization(),f.strategy,Storage.Scanner.class,false));
        if (mode==0) stream.close();
        else if (mode==1) stream.readAllBytes();
        else assertThrows(IOException.class,stream::read);
        assertTrue(session.isClosed()); stream.close();
      }
    }
  }
  @org.integratedmodelling.klab.api.services.runtime.extension.Library(name="test.storage.export",description="Storage export lifecycle")
  public static class Exporters {
    @org.integratedmodelling.klab.api.services.resources.adapters.Exporter(schema="stream",mediaType="application/test",
        knowledgeClass=org.integratedmodelling.klab.api.knowledge.KlabAsset.KnowledgeClass.OBSERVATION,
        fillCurve=Data.FillCurve.D2_YX,description="Lazy long stream")
    public InputStream stream(Storage.LongScanner scanner) {
      assertEquals(Data.FillCurve.D2_YX,scanner.view().curve());
      assertEquals(20,scanner.size());
      assertThrows(IllegalStateException.class,()->scanner.add(1));
      return new InputStream() { public int read() { return scanner.hasNext() ? (int)(Long.MAX_VALUE-scanner.get()) % 256 : -1; } };
    }
    @org.integratedmodelling.klab.api.services.resources.adapters.Exporter(schema="failure",mediaType="application/test",
        knowledgeClass=org.integratedmodelling.klab.api.knowledge.KlabAsset.KnowledgeClass.OBSERVATION,
        fillCurve=Data.FillCurve.D2_YX,description="Exporter failure")
    public InputStream failure(Storage.LongScanner scanner) { throw new IllegalStateException("exporter failure"); }
  }
  @Test void reflectedExporterUsesItsCurveAndTransfersOrReleasesSessions() throws Exception {
    try(var f=new ConformantScanTest.Fixture(3,Storage.Type.LONG,Data.FillCurve.D2_XInvY)) {
      var registry=mock(org.integratedmodelling.klab.components.ComponentRegistry.class,CALLS_REAL_METHODS);
      var libraries=new ArrayList<org.integratedmodelling.klab.api.services.runtime.extension.Extensions.LibraryDescriptor>();
      var register=org.integratedmodelling.klab.components.ComponentRegistry.class.getDeclaredMethod("registerLibrary",
          org.integratedmodelling.klab.api.services.runtime.extension.Library.class,Class.class,List.class);
      register.setAccessible(true);register.invoke(registry,Exporters.class.getAnnotation(org.integratedmodelling.klab.api.services.runtime.extension.Library.class),Exporters.class,libraries);
      var language=new LanguageService();language.setComponentRegistry(registry);
      for(var pair:libraries.getFirst().exporters()) {
        var descriptor=pair.getSecond();
        var call=org.integratedmodelling.common.lang.ServiceCallImpl.create(descriptor.serviceInfo.getName());
        doReturn(List.of(descriptor)).when(registry).getFunctionDescriptor(eq(call),any());
        if(registry.implementation(descriptor).method.getName().equals("failure")) {
          assertThrows(org.integratedmodelling.klab.api.exceptions.KlabResourceAccessException.class,
              ()->language.execute(call,scope(f),InputStream.class,f.storage.temporalOwner()));
        } else {
          try(var stream=language.execute(call,scope(f),InputStream.class,f.storage.temporalOwner())) {
            assertNotNull(stream);assertEquals(0,stream.read());assertEquals(100,stream.read());assertEquals(200,stream.read());
            assertThrows(IllegalStateException.class,()->f.storage.close(null));
          }
        }
      }
    }
  }

  @Test void missingTextAndExactLongTextAreLocaleIndependent() {
    var original = Locale.getDefault();
    try {
      Locale.setDefault(Locale.FRANCE);
      var d = mock(Storage.DoubleScanner.class); when(d.isValid()).thenReturn(true); when(d.peek()).thenReturn(1.25);
      assertEquals("1.25",StorageScan.textValue(d));
      when(d.isValid()).thenReturn(false); assertEquals("null",StorageScan.textValue(d));
      var l=mock(Storage.LongScanner.class);when(l.isValid()).thenReturn(true);when(l.peek()).thenReturn(Long.MAX_VALUE);
      assertEquals("9223372036854775807",StorageScan.textValue(l));
      var b=mock(Storage.BooleanScanner.class);when(b.isValid()).thenReturn(true);
      assertEquals("false",StorageScan.textValue(b));
    } finally { Locale.setDefault(original); }
  }
}
