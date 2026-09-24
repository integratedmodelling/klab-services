package org.integratedmodelling.klab.services.runtime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import org.integratedmodelling.common.knowledge.*;
import org.integratedmodelling.klab.api.data.*;
import org.integratedmodelling.klab.api.digitaltwin.Scheduler;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl;
import org.integratedmodelling.klab.api.scope.*;
import org.integratedmodelling.klab.configuration.ServiceConfiguration;
import org.integratedmodelling.klab.runtime.storage.*;
import org.integratedmodelling.klab.services.scopes.ServiceContextScope;
import org.junit.jupiter.api.*;
import org.ojalgo.array.BufferArray;

class StorageConsumerExecutionTest {
  @BeforeAll static void configure() { ServiceConfiguration.injectInstantiators(); }
  static final String GRID="T0(1){ttype=PHYSICAL,tstart=1000,tend=10000}S2(5,4){proj=EPSG:4326,shape=EPSG:4326 POLYGON ((0 0&comma;0 4&comma;5 4&comma;5 0&comma;0 0))}";
  static class Fixture implements AutoCloseable {
    final ServiceContextScope scope=mock(ServiceContextScope.class,RETURNS_DEEP_STUBS);
    final StorageManagerImpl manager=mock(StorageManagerImpl.class);
    final List<StorageImpl> stores=new ArrayList<>();
    Fixture() {
      org.mockito.Mockito.doReturn(mock(org.integratedmodelling.klab.api.services.RuntimeService.class,RETURNS_DEEP_STUBS)).when(scope).getService(org.integratedmodelling.klab.api.services.RuntimeService.class);
      when(scope.getConfiguration().getPersistence()).thenReturn(Persistence.EXPLICIT_ACTION);
      when(scope.getDigitalTwin().getStorageManager()).thenReturn(manager);
      when(manager.getDoubleBuffer(anyLong())).thenAnswer(c -> (BufferArray)BufferArray.R064.make(c.getArgument(0,Long.class)));
      when(manager.getFloatBuffer(anyLong())).thenAnswer(c -> (BufferArray)BufferArray.R032.make(c.getArgument(0,Long.class)));
      when(manager.getIntBuffer(anyLong())).thenAnswer(c -> (BufferArray)BufferArray.Z032.make(c.getArgument(0,Long.class)));
    }
    ObservationImpl quality(long id, int splits, Data.FillCurve curve, Storage.Type type, String geometry) {
      var concept=new ConceptImpl();concept.setUrn("test:quality");concept.setName("quality");concept.getType().add(SemanticType.QUALITY);
      var observation=new ObservationImpl();observation.setId(id);observation.setUrn("test:"+id);
      observation.setObservable(ObservableImpl.promote(concept,null));observation.setGeometry(Geometry.create(geometry));
      var cd=new ObservationImpl.ContextualizationDataImpl();cd.setNativeShardingStrategy(new Data.ShardingStrategy(curve,splits,0,0,type));observation.setContextualizationData(cd);
      var store=spy(new StorageImpl(observation,cd.getNativeShardingStrategy(),scope,manager));
      stores.add(store);when(manager.getStorage(observation)).thenReturn(store);return observation;
    }
    void fill(Observation observation) {
      var storage=manager.getStorage(observation);var strategy=storage.getNativeShardingStrategy();
      for(var scanner:storage.scan(Scheduler.Event.initialization(),strategy,Storage.Scanner.class,false)) {
        var space=scanner.shard().getGeometry().dimension(Geometry.Dimension.Type.SPACE);
        String[] bounds=space.getParameters().get("bbox").toString().replace('[',' ').replace(']',' ').trim().split("[\\s,]+");
        long west=(long)Double.parseDouble(bounds[0]),south=(long)Double.parseDouble(bounds[2]);
        long nx=space.getShape().get(0),ny=space.getShape().get(1);
        for(long i=0;i<scanner.size();i++) {
          long x=strategy.getCurve()==Data.FillCurve.D2_YX?i%nx:i/ny;
          long y=strategy.getCurve()==Data.FillCurve.D2_YX?i/nx:i%ny;
          if(strategy.getCurve()==Data.FillCurve.D2_XInvY)y=ny-1-y;
          int value=(int)(100*(west+x)+south+y);
          if(scanner instanceof Storage.FloatScanner f)f.add(value+.25f);
          else if(scanner instanceof Storage.IntScanner n)n.add(value);
          else ((Storage.DoubleScanner)scanner).add(value);
        }
        storage.finalizeRun(scanner);
      }
      clearInvocations(storage);
    }
    public void close(){for(var store:stores)store.close(null);}
  }
  @Test void executorAlignsMixedTypedInputsBeforeOpeningOutput() {
    try(var f=new Fixture()) {
      var floats=f.quality(11,3,Data.FillCurve.D2_YX,Storage.Type.FLOAT,GRID);
      var ints=f.quality(12,2,Data.FillCurve.D2_XInvY,Storage.Type.INTEGER,GRID);
      var output=f.quality(-1,4,Data.FillCurve.D2_XY,Storage.Type.DOUBLE,GRID);
      f.fill(floats);f.fill(ints);
      var executor=new AbstractExecutor(null,output,f.scope,Map.of("a",floats,"b",ints)) {
        protected Class<? extends Storage.Scanner> inputScannerClass(String name){return name.equals("a")?Storage.DoubleScanner.class:Storage.IntScanner.class;}
        public boolean validate(){return true;}
        protected boolean run(Scheduler.Event event,Map<String,Storage.Scanner> scans,ContextScope scope,
            org.integratedmodelling.klab.api.services.RuntimeService.ContextualizationScope context) {
          var a=(Storage.DoubleScanner)scans.get("a");var b=(Storage.IntScanner)scans.get("b");var out=(Storage.DoubleScanner)scans.get("self");
          assertEquals(a.view().partition(),b.view().partition());assertEquals(out.size(),a.size());
          while(out.hasNext()){double av=a.get();int bv=b.get();assertEquals(bv+.25,av);out.add(av+bv);}return true;
        }
      };
      assertTrue(executor.execute(Scheduler.Event.initialization(),f.scope,null),()->String.valueOf(executor.getCause())+"; cause: "+executor.getCause().getCause());
      try(var read=StorageReads.open(output,f.scope,null,Data.FillCurve.D2_YX,Storage.DoubleScanner.class)) {
        var scanner=read.scanners().getFirst();for(int y=0;y<4;y++)for(int x=0;x<5;x++)assertEquals(2*(100*x+y)+.25,scanner.get());
      }
      // Successful completion released input leases.
      assertDoesNotThrow(()->f.manager.getStorage(floats).scan(Scheduler.Event.initialization(),floats.getContextualizationData().getNativeShardingStrategy(),Storage.Scanner.class,false));
    }
  }
  @Test void oneSourceFeedsTwoIndependentlyConvertedBindingsWithoutChangingNativeValues() {
    try(var f=new Fixture()) {
      var source=f.quality(11,3,Data.FillCurve.D2_XInvY,Storage.Type.DOUBLE,GRID);
      var original=(ObservableImpl)source.getObservable(); original.setUnit(new org.integratedmodelling.klab.api.data.mediation.impl.UnitImpl("m"));
      var millimeters=new ObservableImpl(original);millimeters.setUnit(new org.integratedmodelling.klab.api.data.mediation.impl.UnitImpl("mm"));
      var kilometers=new ObservableImpl(original);kilometers.setUnit(new org.integratedmodelling.klab.api.data.mediation.impl.UnitImpl("km"));
      when(f.scope.getObservation(11L)).thenReturn(source);f.fill(source);
      var output=f.quality(-1,4,Data.FillCurve.D2_YX,Storage.Type.DOUBLE,GRID);
      var inputs=Map.of("mm",StorageReads.binding(source,millimeters),"km",StorageReads.binding(source,kilometers));
      var executor=new AbstractExecutor(null,output,f.scope,inputs) {
        public boolean validate(){return true;}
        protected boolean run(Scheduler.Event event,Map<String,Storage.Scanner> scans,ContextScope scope,
            org.integratedmodelling.klab.api.services.RuntimeService.ContextualizationScope context) {
          var mm=(Storage.DoubleScanner)scans.get("mm");var km=(Storage.DoubleScanner)scans.get("km");var out=(Storage.DoubleScanner)scans.get("self");
          while(out.hasNext()){double value=mm.get();assertEquals(value/1000000,km.get(),1e-12);out.add(value);}return true;
        }
      };
      assertTrue(executor.execute(Scheduler.Event.initialization(),f.scope,null),()->String.valueOf(executor.getCause())+"; cause: "+executor.getCause().getCause());
      try(var read=StorageReads.open(source,f.scope,null,Data.FillCurve.D2_YX,Storage.DoubleScanner.class)) {
        var scan=read.scanners().getFirst();scan.seek(1);assertEquals(100,scan.peek());
      }
      assertSame(original,source.getObservable());
    }
  }

  @Test void temporalQueryBindingUsesDurableSourceBeforeOpeningWriter() {
    try(var f=new Fixture()) {
      var source=f.quality(11,1,Data.FillCurve.D2_XY,Storage.Type.DOUBLE,GRID);
      var output=f.quality(12,1,Data.FillCurve.D2_XY,Storage.Type.DOUBLE,GRID);
      var query=RuntimeService.qualityQueryResult(source,source,source.getGeometry());
      when(f.scope.getObservation(11L)).thenReturn(source);
      var event=new Scheduler.Event() {
        public Type getType(){return Type.TEMPORAL_TRANSITION;}
        public String toKey(){return "query-event";}
        public org.integratedmodelling.klab.api.knowledge.observation.scale.time.Time getTime(){return org.integratedmodelling.klab.api.knowledge.observation.scale.time.TimePeriod.create(1000,2000);}
        public Observation getEvent(){return null;}
      };
      var geometry=TemporalGeometry.localize(output.getGeometry(),event);
      var partition=new StorageScan.Partition("task-0",geometry.encode(),geometry.size());
      var writes=mock(TemporalWriteSet.class);when(f.scope.getCurrentTransaction().getTemporalWrites()).thenReturn(writes);
      when(writes.writeLayout(output)).thenReturn(List.of(partition));
      var reader=mock(Storage.DoubleScanner.class,RETURNS_DEEP_STUBS);when(reader.view().partition()).thenReturn(partition);
      var session=mock(StorageScan.Session.class);when(session.scanners()).thenReturn(List.of(reader));
      doReturn(session).when(writes).read(eq(source),any(),eq(TemporalWriteSet.Access.CURRENT));
      var writer=mock(Storage.DoubleScanner.class);doReturn(List.of(writer)).when(writes).scan(eq(output),any(),any(),eq(TemporalWriteSet.Access.WRITE));
      var computation=mock(org.integratedmodelling.klab.api.services.runtime.ScalarComputation.class);
      when(computation.inputNames()).thenReturn(Set.of("input"));when(computation.execute(anyMap(),eq(event),eq(f.scope))).thenReturn(true);
      assertTrue(TemporalScalarExecution.run(computation,output,Map.of("input",query),event,f.scope,false));
      var ordered=inOrder(writes);ordered.verify(writes).writeLayout(output);
      ordered.verify(writes).read(eq(source),any(),eq(TemporalWriteSet.Access.CURRENT));
      ordered.verify(writes).scan(eq(output),any(),any(),eq(TemporalWriteSet.Access.WRITE));
      verify(session).close();
    }
  }

  @Test void spatialDependencyUsesOutputCellsAndRecordsResamplingEvidence() {
    try(var f=new Fixture()) {
      when(f.scope.getService(org.integratedmodelling.klab.api.services.RuntimeService.class).settings()
          .get(org.integratedmodelling.klab.api.configuration.Setting.ACCEPT_LOSSY_MEDIATIONS,Boolean.class)).thenReturn(true);
      var input=f.quality(11,3,Data.FillCurve.D2_YX,Storage.Type.DOUBLE,GRID);
      var output=f.quality(-1,2,Data.FillCurve.D2_XY,Storage.Type.DOUBLE,GRID.replace("S2(5,4)","S2(10,8)"));
      ((ObservableImpl)input.getObservable()).setUnit(new org.integratedmodelling.klab.api.data.mediation.impl.UnitImpl("m"));
      var requested=new ObservableImpl((ObservableImpl)input.getObservable());requested.setUnit(new org.integratedmodelling.klab.api.data.mediation.impl.UnitImpl("mm"));
      when(f.scope.getObservation(11L)).thenReturn(input);
      f.fill(input);
      var executor=new AbstractExecutor(null,output,f.scope,Map.of("elevation",StorageReads.binding(input,requested))) {
        public boolean validate(){return true;}
        protected boolean run(Scheduler.Event event,Map<String,Storage.Scanner> scans,ContextScope scope,
            org.integratedmodelling.klab.api.services.RuntimeService.ContextualizationScope context) {
          var in=(Storage.DoubleScanner)scans.get("elevation");var out=(Storage.DoubleScanner)scans.get("self");
          assertEquals(out.size(),in.size());
          while(out.hasNext())out.add(in.get());return true;
        }
      };
      assertTrue(executor.execute(Scheduler.Event.initialization(),f.scope,null),()->String.valueOf(executor.getCause())+"; cause: "+executor.getCause().getCause());
      try(var session=StorageReads.open(output,f.scope,null,Data.FillCurve.D2_XY,Storage.DoubleScanner.class)) {
        var scan=session.scanners().getFirst();
        for(int x=0;x<10;x++)for(int y=0;y<8;y++)assertEquals(1000*(100*(x/2)+y/2),scan.get());
      }
      verify(f.scope.getCurrentTransaction().getActivity(),atLeastOnce()).getMetadata();
    }
  }

  @Test void executorCarriesTheOutputCrsIntoCrossProjectionDependencyPlans() {
    try(var f=new Fixture()) {
      when(f.scope.getService(org.integratedmodelling.klab.api.services.RuntimeService.class).settings()
          .get(org.integratedmodelling.klab.api.configuration.Setting.ACCEPT_LOSSY_MEDIATIONS,Boolean.class)).thenReturn(true);
      var input=f.quality(11,3,Data.FillCurve.D2_XY,Storage.Type.DOUBLE,GRID);f.fill(input);
      double x=6378137*Math.toRadians(1.5),y=6378137*Math.log(Math.tan(Math.PI/4+Math.toRadians(1.5)/2));
      String shape="EPSG:3857 POLYGON (("+(x-10)+" "+(y-10)+"&comma;"+(x-10)+" "+(y+10)+"&comma;"
          +(x+10)+" "+(y+10)+"&comma;"+(x+10)+" "+(y-10)+"&comma;"+(x-10)+" "+(y-10)+"))";
      var output=f.quality(-1,1,Data.FillCurve.D2_XY,Storage.Type.DOUBLE,
          "T0(1){ttype=PHYSICAL,tstart=1000,tend=10000}S2(2,2){proj=EPSG:3857,shape="+shape+"}");
      var executor=new AbstractExecutor(null,output,f.scope,Map.of("elevation",input)) {
        public boolean validate(){return true;}
        protected boolean run(Scheduler.Event event,Map<String,Storage.Scanner> scans,ContextScope scope,
            org.integratedmodelling.klab.api.services.RuntimeService.ContextualizationScope context) {
          var in=(Storage.DoubleScanner)scans.get("elevation");var out=(Storage.DoubleScanner)scans.get("self");
          assertEquals("EPSG:3857",in.view().spatial().targetCrs());
          while(in.hasNext()) { assertEquals(101,in.peek());out.add(in.get()); } return true;
        }
      };
      assertTrue(executor.execute(Scheduler.Event.initialization(),f.scope,null),()->String.valueOf(executor.getCause())+"; cause: "+executor.getCause().getCause());
    }
  }

  @Test void incompatibleInputPreservesOutputAndConcreteCause() {
    try(var f=new Fixture()) {
      when(f.scope.getService(org.integratedmodelling.klab.api.services.RuntimeService.class).settings()
          .get(org.integratedmodelling.klab.api.configuration.Setting.ACCEPT_LOSSY_MEDIATIONS,Boolean.class)).thenReturn(false);
      var input=f.quality(11,3,Data.FillCurve.D2_YX,Storage.Type.FLOAT,GRID);
      var output=f.quality(-1,2,Data.FillCurve.D2_XY,Storage.Type.DOUBLE,GRID.replace("5 4","10 4").replace("5 0","10 0"));
      f.fill(input);f.fill(output);
      var executor=new AbstractExecutor(null,output,f.scope,Map.of("elevation",input)) {
        public boolean validate(){return true;}
        protected boolean run(Scheduler.Event event,Map<String,Storage.Scanner> scans,ContextScope scope,
            org.integratedmodelling.klab.api.services.RuntimeService.ContextualizationScope context){fail("Must fail before running");return false;}
      };
      assertFalse(executor.execute(Scheduler.Event.initialization(),f.scope,null));
      assertTrue(executor.getCause().getMessage().contains("elevation"));assertNotNull(executor.getCause().getCause());
      assertTrue(executor.getCause().getMessage().contains("ACCEPT_LOSSY_MEDIATIONS=false"));
      verify(f.manager.getStorage(output),never()).scan(any(),any(),any(),eq(false));
      try(var read=StorageReads.open(output,f.scope,null,Data.FillCurve.D2_XY,Storage.DoubleScanner.class)){assertEquals(0,read.scanners().getFirst().get());}
    }
  }
}
