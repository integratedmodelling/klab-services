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
        doReturn(List.of(descriptor)).when(registry).getFunctionDescriptor(call);
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
