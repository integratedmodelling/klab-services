package org.integratedmodelling.klab.runtime.storage;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Proxy;
import java.util.*;
import org.integratedmodelling.klab.api.data.*;
import org.integratedmodelling.klab.api.data.impl.HistogramImpl;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.observation.scale.space.Tile;
import org.integratedmodelling.klab.configuration.ServiceConfiguration;
import org.integratedmodelling.klab.runtime.scale.space.SpaceImpl;
import org.integratedmodelling.klab.services.runtime.library.DataLibrary;
import org.junit.jupiter.api.*;

class DataLibraryScannerTest {
  @BeforeAll static void configure() { ServiceConfiguration.injectInstantiators(); }
  private static final String RECTANGLE = "POLYGON ((0 0,5 0,5 4,0 4,0 0))";
  private static final String HOLE = "POLYGON ((0 0,5 0,5 4,0 4,0 0),(1.5 1.5,1.5 3.5,3.5 3.5,3.5 1.5,1.5 1.5))";
  private static final String ISLANDS = "MULTIPOLYGON (((0 0,1 0,1 1,0 1,0 0)),((4 3,5 3,5 4,4 4,4 3)))";
  private static String geometry(String shape) {
    return "S2(5,4){proj=EPSG:4326,bbox=[0 5 0 4],shape=EPSG:4326 " + shape.replace(",","&comma;") + "}";
  }
  private static Storage.DoubleScanner covered(ArrayScanner scanner,String geometry,Data.FillCurve curve) {
    var support=SpatialCoverage.support(geometry);
    return (Storage.DoubleScanner)CoveredScanner.wrap(scanner,new SpatialCoverage(geometry,"EPSG:4326",curve,support));
  }
  @Test void normalizeVisitsCoveredCellsOnceAndLeavesExcludedCellsUntouched() {
    for(var shape:List.of(RECTANGLE,HOLE,ISLANDS))
      for(var curve:List.of(Data.FillCurve.D2_XY,Data.FillCurve.D2_YX,Data.FillCurve.D2_XInvY)) {
        var geometry=geometry(shape);
        var input=new ArrayScanner(20,false); var output=new ArrayScanner(20,true);
        var in=covered(input,geometry,curve); var out=covered(output,geometry,curve);
        var expected=new HashSet<Integer>();
        var support=SpatialCoverage.support(geometry);
        var coverage=new SpatialCoverage(geometry,"EPSG:4326",curve,support);
        var polygon=org.integratedmodelling.klab.runtime.scale.space.ShapeImpl.create("EPSG:4326 "+shape).getJTSGeometry();
        for(int i=0;i<20;i++) {
          var xy=new long[2]; coverage.coordinates(i,xy);
          if(polygon.covers(polygon.getFactory().createPoint(new org.locationtech.jts.geom.Coordinate(xy[0]+.5,xy[1]+.5)))) expected.add(i);
        }
        if(shape.equals(RECTANGLE)) { assertSame(input,in); assertSame(output,out); }
        DataLibrary.normalize(in,out,null);
        assertFalse(in.hasNext()); assertFalse(out.hasNext());
        assertEquals(expected.size(),input.reads); assertEquals(expected.size(),output.writes);
        for(int i=0;i<20;i++) assertEquals(expected.contains(i)?i/100.0:-1,output.values[i]);
      }
  }
  @Test void normalizeHandlesAnEmptyCoveredPartitionWithoutReading() {
    var geometry=geometry("POLYGON ((6 6,7 6,7 7,6 7,6 6))");
    var input=new ArrayScanner(20,false); var output=new ArrayScanner(20,true);
    DataLibrary.normalize(covered(input,geometry,Data.FillCurve.D2_XY),covered(output,geometry,Data.FillCurve.D2_XY),null);
    assertEquals(0,input.reads); assertEquals(0,output.writes);
  }
  @Test void normalizeInPlaceAdvancesOnlyOnWrites() {
    var original=new ArrayScanner(20,false); var scanner=covered(original,geometry(ISLANDS),Data.FillCurve.D2_XY);
    DataLibrary.normalize(scanner,scanner,null);
    assertEquals(0,original.reads); assertEquals(2,original.writes);
    assertEquals(0,original.values[0]); assertEquals(.19,original.values[19]);
    assertEquals(10,original.values[10]); assertFalse(scanner.hasNext());
  }
  @Test void normalizePreservesNaNWithoutAffectingFiniteNeighbours() {
    var input=new ArrayScanner(3,false); input.values[1]=Double.NaN;
    var output=new ArrayScanner(3,true); DataLibrary.normalize(input,output,null);
    assertEquals(0,output.values[0]); assertTrue(Double.isNaN(output.values[1])); assertEquals(.02,output.values[2]);
  }
  @Test void salamellaRectangleExpandsWithTheFiftyMetreGridAndKeepsTheUnmaskedPath() {
    var geometry=Geometry.create("S2{proj=EPSG:4326,sgrid=50.m,shape=EPSG:4326 POLYGON ((21.910616123751442 38.79364335500986&comma;21.26838806193504 38.79364335500986&comma;21.26838806193504 39.03962334822924&comma;21.910616123751442 39.03962334822924&comma;21.910616123751442 38.79364335500986))}");
    var tile=(Tile)SpaceImpl.create(geometry.dimension(Geometry.Dimension.Type.SPACE));
    assertTrue(tile.getEnvelope().getMaxY()>39.03962334822924);
    assertNull(SpatialCoverage.support(tile.encode()));
    assertTrue(tile.isCellCovered(tile.getGrid().getXCells()-1,tile.getGrid().getYCells()-1));
    var input=new ArrayScanner(Math.toIntExact(tile.size()),false);
    var output=new ArrayScanner(Math.toIntExact(tile.size()),true);
    var in=covered(input,tile.encode(),Data.FillCurve.D2_XY);
    assertSame(input,in); DataLibrary.normalize(in,covered(output,tile.encode(),Data.FillCurve.D2_XY),null);
    assertEquals(tile.size(),input.reads); assertEquals(tile.size(),output.writes);
    assertEquals((tile.size()-1)/100.0,output.values[output.values.length-1]);
  }
  @Test void locatingAPartialGridInATileUsesTheLocatedEnvelopeAndCoversAllCells() {
    var shape=org.integratedmodelling.klab.runtime.scale.space.ShapeImpl.create("EPSG:3857 POLYGON ((0 0,3 0,3 3,0 3,0 0))");
    var partial=new org.integratedmodelling.klab.runtime.scale.space.GridImpl(2);
    var tile=new org.integratedmodelling.klab.runtime.scale.space.TileImpl(shape,partial,true);
    assertEquals(4,tile.size()); assertEquals(3,tile.getEnvelope().getMaxX());
    assertNull(SpatialCoverage.support(tile.encode())); assertTrue(tile.isCellCovered(1,1));
    assertNull(partial.getEnvelope());
  }
  @Test void constructingAMetricNonrectangularGridPreservesItsMask() {
    var geometry=Geometry.create("S2{proj=EPSG:3857,sgrid=1.m,shape=EPSG:3857 POLYGON ((0 0&comma;5 0&comma;0 4&comma;0 0))}");
    var tile=(Tile)SpaceImpl.create(geometry.dimension(Geometry.Dimension.Type.SPACE));
    assertNotNull(SpatialCoverage.support(tile.encode()));
    assertFalse(tile.isCellCovered(tile.getGrid().getXCells()-1,tile.getGrid().getYCells()-1));
  }
  @Test void alignedWrappedRectanglesCoverTheirNormalizedCellsWithoutExtendingPastWorldEdges() {
    var alignment=org.integratedmodelling.klab.runtime.scale.space.GridAlignmentSupport.decode(
        Map.of("anchor","POINT (1.3 2.4)","span",60,"snap",false));
    var requested=Geometry.create("S2{proj=EPSG:4326,shape=EPSG:4326 POLYGON ((179 0&comma;181 0&comma;181 1&comma;179 1&comma;179 0))}");
    var aligned=org.integratedmodelling.klab.runtime.scale.space.GridAlignmentSupport.alignGeometry(requested,alignment);
    var tile=(Tile)SpaceImpl.create(aligned.dimension(Geometry.Dimension.Type.SPACE));
    var physical=((org.integratedmodelling.klab.runtime.scale.space.TileImpl)tile).getJTSGeometry().getEnvelopeInternal();
    assertTrue(physical.getMinX()>=-180 && physical.getMaxX()<=180);
    assertTrue(physical.getMinY()>=-90 && physical.getMaxY()<=90);
    for(long x=0;x<tile.getGrid().getXCells();x++) for(long y=0;y<tile.getGrid().getYCells();y++) assertTrue(tile.isCellCovered(x,y));
  }
  private static final class ArrayScanner implements Storage.DoubleScanner {
    final double[] values;
    final Storage.Shard shard;
    int cursor,reads,writes;
    ArrayScanner(int size,boolean output) {
      values=new double[size]; for(int i=0;i<size;i++) values[i]=output?-1:i;
      var histogram=new HistogramImpl(); histogram.setMin(0); histogram.setMax(100);
      shard=(Storage.Shard)Proxy.newProxyInstance(Storage.Shard.class.getClassLoader(),new Class<?>[]{Storage.Shard.class},
          (proxy,method,args)-> { if(method.getName().equals("getHistogram")) return histogram; throw new UnsupportedOperationException(method.getName()); });
    }
    public double get() { reads++; return values[cursor++]; }
    public double peek() { return values[cursor]; }
    public void add(double value) { values[cursor++]=value; writes++; }
    public boolean hasNext() { return cursor<values.length; }
    public long position() { return cursor; }
    public void seek(long offset) { cursor=Math.toIntExact(offset); }
    public long nextLong() { return cursor++; }
    public long size() { return values.length; }
    public Storage.Shard shard() { return shard; }
  }
}
