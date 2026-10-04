package org.integratedmodelling.klab.runtime.scale.space;

import static org.junit.jupiter.api.Assertions.*;

import org.geotools.referencing.GeodeticCalculator;
import org.integratedmodelling.klab.api.knowledge.observation.scale.space.Projection;
import org.integratedmodelling.klab.api.lang.Quantity;
import org.integratedmodelling.klab.configuration.ServiceConfiguration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;

class GridImplTest {
  @BeforeAll static void configure() { ServiceConfiguration.injectInstantiators(); }

  private static EnvelopeImpl box(double west, double east, double south, double north, String crs) {
    return EnvelopeImpl.create(west,east,south,north,Projection.of(crs));
  }

  @Test void partialGridsHaveNoCellsUntilLocated() {
    var grid = new GridImpl(50);
    assertEquals(0,grid.size());
    assertEquals(0,grid.getXCells());
    assertEquals(0,grid.getYCells());
    assertNull(grid.getEnvelope());
    assertNotNull(grid.getProjection());
    assertEquals(50,grid.getAssertedResolution().getValue().doubleValue());
    var located = grid.locate(box(0,100,0,150,"EPSG:3857"));
    assertEquals(6,located.size());
    assertEquals(0,grid.size());
  }

  @Test void metricGridsCoverTheRequestedEnvelopeWithoutCountTruncation() {
    var envelope = box(1000000,1000000.3,2000000,2000000.4,"EPSG:3857");
    var grid = new GridImpl(envelope,0.1,true);
    assertEquals(3,grid.getXCells());
    assertEquals(4,grid.getYCells());
    assertEquals(12,grid.size());
    assertTrue(grid.isSquareCells());
    assertEquals(0.1,grid.getXCellSize());
    assertEquals(1000000.3,grid.getEnvelope().getMaxX());
    var uneven = new GridImpl(box(0,101,0,149,"EPSG:3857"),50,true);
    assertEquals(3,uneven.getXCells());
    assertEquals(3,uneven.getYCells());
    assertEquals(150,uneven.getEnvelope().getMaxX());
    assertEquals(150,uneven.getEnvelope().getMaxY());
  }

  @Test void unconstrainedCellsKeepTheEnvelopeAndFitTheResolution() {
    var grid = new GridImpl(box(0,101,0,149,"EPSG:3857"),50,false);
    assertFalse(grid.isSquareCells());
    assertEquals(101,grid.getEnvelope().getMaxX());
    assertEquals(149,grid.getEnvelope().getMaxY());
    assertEquals(101.0/3,grid.getXCellSize());
    assertEquals(149.0/3,grid.getYCellSize());
  }

  @Test void projectedFeetAreConvertedInsteadOfTreatedAsDegrees() {
    var grid = new GridImpl(box(1000000,1001000,200000,201000,"EPSG:2263"),100,true);
    assertEquals(4,grid.getXCells());
    assertEquals(4,grid.getYCells());
    assertEquals(100/0.3048006096012192,grid.getXCellSize(),1e-8);
  }

  @Test void quantityUnitsAreConvertedToMetres() {
    var grid = new GridImpl(box(0,2000,0,3000,"EPSG:3857"),Quantity.create("1.km"),true);
    assertEquals(2,grid.getXCells());
    assertEquals(3,grid.getYCells());
    assertEquals(1000,grid.getXCellSize());
  }

  @Test void transportedGridKeepsItsNativeSpacingWhenRelocated() {
    var grid = new GridImpl(box(0,10,0,20,"EPSG:3857"),null,5,5);
    var moved = grid.locate(box(20,26,40,48,"EPSG:3857"));
    assertEquals(2,moved.getXCellSize());
    assertEquals(4,moved.getYCellSize());
    assertEquals(3,moved.getXCells());
    assertEquals(2,moved.getYCells());
    assertEquals(6,moved.size());
    assertEquals(25,grid.size());
    assertEquals(0,grid.getEnvelope().getMinX());
  }

  @Test void nativeUnitAnchorSnapsOutwardAndSurvivesCopies() {
    var anchor = new GeometryFactory().createPoint(new Coordinate(1,2));
    var grid = new GridImpl(anchor,Projection.of("EPSG:3857"),10);
    var located = grid.locate(box(-4,22,-3,23,"EPSG:3857"));
    assertEquals(-9,located.getEnvelope().getMinX());
    assertEquals(31,located.getEnvelope().getMaxX());
    assertEquals(-8,located.getEnvelope().getMinY());
    assertEquals(32,located.getEnvelope().getMaxY());
    assertEquals(16,located.size());
    assertEquals(1,grid.getAnchorPoints().size());
    assertEquals(0,grid.size());
    var copy = grid.copy();
    copy.getAnchorPoints().clear();
    assertEquals(1,grid.getAnchorPoints().size());
  }

  @Test void metreAnchorUsesThePointCRS() {
    var anchor = new GeometryFactory().createPoint(new Coordinate(1,2));
    anchor.setSRID(3857);
    var grid = new GridImpl(anchor,10);
    assertEquals("EPSG:3857",grid.getProjection().getCode());
    assertEquals(-9,grid.locate(box(-4,22,-3,23,"EPSG:3857")).getEnvelope().getMinX());
    assertThrows(IllegalArgumentException.class,() -> new GridImpl(anchor,Projection.of("EPSG:4326"),1));
  }

  @Test void anchorCRSReprojectsTheRequestedEnvelope() {
    var anchor = new GeometryFactory().createPoint(new Coordinate(0,0));
    var grid = new GridImpl(anchor,Projection.of("EPSG:3857"),1000);
    var located = grid.locate(box(1,1.01,40,40.01,"EPSG:4326"));
    assertEquals("EPSG:3857",located.getProjection().getCode());
    assertTrue(located.getEnvelope().getMinX() > 100000);
    assertEquals(0,located.getEnvelope().getMinX()%1000);
    assertTrue(located.size()>0);
  }

  @Test void salamellaUsesTheActualMidpointAndExpandsByAtMostOneCell() {
    var envelope = box(21.26838806193504,21.910616123751442,
        38.79364335500986,39.03962334822924,"EPSG:4326");
    var grid = new GridImpl(envelope,50,true);
    var calculator = new GeodeticCalculator(ProjectionImpl.promote(envelope.getProjection()).getCRS());
    calculator.setStartingGeographicPoint(envelope.getMinX(),(envelope.getMinY()+envelope.getMaxY())/2);
    calculator.setDestinationGeographicPoint(envelope.getMaxX(),(envelope.getMinY()+envelope.getMaxY())/2);
    assertEquals(Math.ceil(calculator.getOrthodromicDistance()/50),grid.getXCells());
    assertTrue(grid.getXCells()<1200);
    assertTrue(grid.getEnvelope().getMaxX()>=envelope.getMaxX());
    assertTrue(grid.getEnvelope().getMaxX()-envelope.getMaxX()<grid.getXCellSize());
    assertTrue(grid.getEnvelope().getMaxY()>=envelope.getMaxY());
    assertTrue(grid.getEnvelope().getMaxY()-envelope.getMaxY()<grid.getYCellSize());
    var fitted = new GridImpl(envelope,50,false);
    assertEquals(grid.getXCells(),fitted.getXCells());
    assertEquals(envelope.getMaxX(),fitted.getEnvelope().getMaxX());
  }

  @Test void highLatitudeAndWideGeographicRegionsKeepTheirCoverage() {
    var equator = new GridImpl(box(-10,10,-1,1,"EPSG:4326"),1000,false);
    var arctic = new GridImpl(box(-10,10,69,71,"EPSG:4326"),1000,false);
    assertTrue(arctic.getXCells()<equator.getXCells()/2);
    var wide = new GridImpl(box(-170,170,-1,1,"EPSG:4326"),100000,false);
    assertTrue(wide.getXCells()>350);
    var world = new GridImpl(box(-180,180,-90,90,"EPSG:4326"),100000,true);
    assertEquals(180,world.getEnvelope().getMaxX());
    assertEquals(90,world.getEnvelope().getMaxY());
    assertTrue(world.getXCells()>400);
    assertTrue(world.size()>0);
  }

  @Test void locatingAnAlreadyLocatedEnvelopeIsIdempotent() {
    for (var envelope : new EnvelopeImpl[]{box(0,101,0,149,"EPSG:3857"),box(21,22,38,39,"EPSG:4326")}) {
      var grid = new GridImpl(envelope,50,true);
      var again = grid.locate(grid.getEnvelope());
      assertEquals(grid.getXCells(),again.getXCells());
      assertEquals(grid.getYCells(),again.getYCells());
      assertEquals(grid.getXCellSize(),again.getXCellSize());
      assertNotSame(grid,again);
      assertNotSame(grid.getEnvelope(),again.getEnvelope());
    }
  }

  @Test void invalidResolutionsCountsBoundsAndOverflowAreRejected() {
    for (double resolution : new double[]{0,-1,Double.NaN,Double.POSITIVE_INFINITY}) {
      assertThrows(IllegalArgumentException.class,() -> new GridImpl(resolution));
      assertThrows(IllegalArgumentException.class,() -> new GridImpl(box(0,10,0,10,"EPSG:3857"),resolution,true));
    }
    assertThrows(IllegalArgumentException.class,() -> new GridImpl(box(0,10,0,10,"EPSG:3857"),null,0,1));
    assertThrows(IllegalArgumentException.class,() -> new GridImpl(box(0,0,0,10,"EPSG:3857"),1,true));
    assertThrows(IllegalArgumentException.class,() -> new GridImpl(box(-181,180,0,10,"EPSG:4326"),50,true));
    assertThrows(IllegalArgumentException.class,() -> new GridImpl(box(0,10,0,10,"EPSG:3857"),Double.MIN_VALUE,true));
    assertThrows(ArithmeticException.class,() -> new GridImpl(box(0,10,0,10,"EPSG:3857"),null,Long.MAX_VALUE,2));
  }

  @Test void setterBasedGridsKeepTheirSizeAndResolutionConsistent() {
    var grid = new GridImpl();
    grid.setxCells(2);
    grid.setyCells(3);
    assertEquals(6,grid.size());
    assertThrows(IllegalArgumentException.class,() -> grid.setSize(7));
    assertThrows(IllegalArgumentException.class,() -> grid.setxCells(-1));
    assertThrows(ArithmeticException.class,() -> grid.setxCells(Long.MAX_VALUE));
    assertEquals(6,grid.size());
    var partial = new GridImpl();
    partial.setAssertedResolution(Quantity.create("1.km"));
    assertEquals(6,partial.locate(box(0,2000,0,3000,"EPSG:3857")).size());
    var envelope = box(0,10,0,20,"EPSG:3857");
    grid.setEnvelope(envelope);
    assertNotSame(envelope,grid.getEnvelope());
    assertEquals(5,grid.getXCellSize());
    assertEquals(20.0/3,grid.getYCellSize());
    assertTrue(grid.locate(box(20,30,40,60,"EPSG:3857")).size()>0);
    assertThrows(IllegalArgumentException.class,() -> grid.setProjection(new ProjectionImpl("EPSG:4326")));
    var complete = new GridImpl(box(0,100,0,100,"EPSG:3857"),50,true);
    complete.setAssertedResolution(Quantity.create("10.m"));
    assertEquals(100,complete.locate(complete.getEnvelope()).size());
  }

  @Test void coarseCellsAndLargeNativeAreasStayFinite() {
    var coarse = new GridImpl(box(1000000,1000000.000000001,0,1,"EPSG:3857"),1e10,true);
    assertEquals(1,coarse.getXCells());
    assertEquals(1,coarse.getYCells());
    var large = new GridImpl(box(0,1e200,0,1e200,"EPSG:3857"),null,1,1);
    assertEquals(1e200,large.resolution(),1e186);
    var anchor = new GeometryFactory().createPoint(new Coordinate(0,0));
    var angular = new GridImpl(anchor,Projection.of("EPSG:4326"),0.7);
    assertThrows(IllegalArgumentException.class,() -> angular.locate(box(179.5,180,0,1,"EPSG:4326")));
  }
}
