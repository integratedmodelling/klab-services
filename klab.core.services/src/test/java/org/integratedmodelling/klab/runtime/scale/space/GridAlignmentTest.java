package org.integratedmodelling.klab.runtime.scale.space;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.integratedmodelling.klab.api.digitaltwin.DigitalTwin;
import org.integratedmodelling.klab.api.digitaltwin.GridAlignment;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.observation.scale.space.Projection;
import org.integratedmodelling.klab.api.lang.Quantity;
import org.integratedmodelling.klab.api.lang.kim.impl.KimSymbolDefinitionImpl;
import org.integratedmodelling.klab.configuration.ServiceConfiguration;
import org.integratedmodelling.klab.runtime.language.KimValidator;
import org.integratedmodelling.klab.api.services.runtime.Notification;
import org.integratedmodelling.klab.utilities.Utils;
import org.junit.jupiter.api.*;

class GridAlignmentTest {
  @BeforeAll static void setup() { ServiceConfiguration.injectInstantiators(); }
  static KimSymbolDefinitionImpl definition(Map<String,Object> values) {
    var def=new KimSymbolDefinitionImpl(); def.setDefineClass("grid"); def.setUrn("test.grid"); def.setValue(values); return def;
  }
  static GridAlignment decode(Object span,boolean snap,boolean strict) {
    return GridAlignmentSupport.decode(definition(Map.of("projection","EPSG:4326","anchor","POINT (1.3 2.4)","span",span,"snap",snap,"strict",strict)));
  }
  @Test void plainWktUsesTheSeparateProjectionAndUnitMismatchWarnsDuringValidation() {
    var definition=definition(Map.of("projection","EPSG:4326","anchor","POINT (21.910616123751442 38.79364335500986)","span",Quantity.of(50,"m")));
    var grid=GridAlignmentSupport.decode(definition);
    assertTrue(grid.snap()); assertTrue(grid.strict());
    assertTrue(grid.warnings().stream().anyMatch(w->w.contains("anchor point")));
    assertTrue(grid.warnings().stream().anyMatch(w->w.contains("snapped")));
    assertTrue(new KimValidator().validateStatement(definition,null).stream().allMatch(n->n.getLevel()==Notification.Level.Warning));
    assertEquals(Math.rint(360/grid.stepX()),360/grid.stepX(),1e-7);
    assertEquals(Math.rint(180/grid.stepY()),180/grid.stepY(),1e-7);
  }
  @Test void strictWorldGridCoversTheWorldWithoutDuplicateLongitudeCells() {
    var alignment=decode(60,true,true);
    var requested=new GridImpl(EnvelopeImpl.create(-180,180,-90,90,Projection.of("EPSG:4326")),null,6,3);
    var grid=GridAlignmentSupport.align(requested,alignment);
    assertEquals(6,grid.getXCells()); assertEquals(3,grid.getYCells());
    assertEquals(-180,grid.getEnvelope().getMinX(),1e-10); assertEquals(180,grid.getEnvelope().getMaxX(),1e-10);
    assertEquals(-90,grid.getEnvelope().getMinY(),1e-10); assertEquals(90,grid.getEnvelope().getMaxY(),1e-10);
  }
  @Test void exactAnchorHasSplitSeamCellsAndClippedPolarRowsWithNoOverlap() {
    var alignment=decode(60,false,true);
    assertEquals(1.3,alignment.anchorX()); assertEquals(2.4,alignment.anchorY());
    var grid=GridAlignmentSupport.align(new GridImpl(EnvelopeImpl.create(-180,180,-90,90,Projection.of("EPSG:4326")),null,6,3),alignment);
    assertEquals(6,grid.getXCells()); assertEquals(4,grid.getYCells());
    var footprints=new ArrayList<org.integratedmodelling.klab.api.knowledge.observation.scale.space.Envelope>();
    for(long x=0;x<grid.getXCells();x++) for(long y=0;y<grid.getYCells();y++) footprints.addAll(grid.getCellBounds(x,y));
    double area=0;
    for(int i=0;i<footprints.size();i++) {
      var a=footprints.get(i); area+=a.getWidth()*a.getHeight();
      assertTrue(a.getMinX()>=-180 && a.getMaxX()<=180 && a.getMinY()>=-90 && a.getMaxY()<=90);
      for(int j=0;j<i;j++) {
        var b=footprints.get(j);
        double overlap=Math.max(0,Math.min(a.getMaxX(),b.getMaxX())-Math.max(a.getMinX(),b.getMinX()))
            *Math.max(0,Math.min(a.getMaxY(),b.getMaxY())-Math.max(a.getMinY(),b.getMinY()));
        assertEquals(0,overlap,1e-9);
      }
    }
    assertEquals(360*180,area,1e-7);
  }
  @Test void normalizationSurvivesTransportAndIsIdempotent() {
    var alignment=decode(60,false,false);
    var original=Geometry.create("S2(6,3){proj=EPSG:4326,bbox=[-180 180 -90 90]}");
    var geometry=GridAlignmentSupport.alignGeometry(original,alignment);
    assertEquals(geometry.encode(),GridAlignmentSupport.alignGeometry(geometry,alignment).encode());
    var tile=(TileImpl)new org.integratedmodelling.klab.runtime.scale.ScaleImpl(Geometry.create(geometry.encode())).getSpace();
    assertArrayEquals(new double[]{-180,180,-90,90},tile.getGrid().getWorldBounds(),1e-9);
    assertTrue(tile.encode().contains("world="));
  }
  @Test void projectedAlignmentPreservesCoverageOutsideTheAnchor() {
    var alignment=GridAlignmentSupport.decode(definition(Map.of("projection","EPSG:32634","anchor","POINT (500000 4200000)","span",50,"strict",true)));
    var grid=GridAlignmentSupport.align(new GridImpl(EnvelopeImpl.create(500009,500010,4200009,4200010,Projection.of("EPSG:32634")),null,1,1),alignment);
    assertEquals(1,grid.size()); assertEquals(500000,grid.getEnvelope().getMinX()); assertEquals(500050,grid.getEnvelope().getMaxX());
  }
  @Test void customLevelsAreNestedAndTheClosestCellCountWins() {
    var alignment=GridAlignmentSupport.decode(definition(Map.of("projection","EPSG:32634","anchor","POINT (0 0)","span",50,"strict",false,"resolutions",List.of(10,50,100))));
    var grid=GridAlignmentSupport.align(new GridImpl(EnvelopeImpl.create(1,89,1,89,Projection.of("EPSG:32634")),null,10,10),alignment);
    assertEquals(10,grid.getXCellSize()); assertEquals(81,grid.size());
    assertThrows(IllegalArgumentException.class,()->GridAlignmentSupport.decode(definition(Map.of("anchor","POINT (0 0)","span",10,"resolutions",List.of(9,10)))));
  }
  @Test void configurationCopiesAndJsonRoundTripsPreserveFrozenInstructions() {
    var grid=decode(60,false,true);
    assertEquals(grid,Utils.Json.parseObject(Utils.Json.asString(grid),GridAlignment.class));
    var configuration=DigitalTwin.Configuration.builder().name("test").gridAlignment(grid).build();
    var copy=DigitalTwin.Configuration.builder(configuration).build();
    assertEquals(grid,copy.getGridAlignment()); assertEquals("test.grid",copy.getGridUrn());
    var transported=Utils.Json.parseObject(Utils.Json.asString(configuration),org.integratedmodelling.klab.api.digitaltwin.impl.ConfigurationImpl.class);
    assertEquals(grid,transported.getGridAlignment());
    assertThrows(IllegalArgumentException.class,()->DigitalTwin.Configuration.builder().grid("test.grid").grid("other.grid"));
  }
  @Test void inlineMapsAreFrozenHaveStableIdentityAndSurviveTransport() {
    var values = new LinkedHashMap<String,Object>();
    values.put("anchor", "POINT (500000 4200000)"); values.put("projection", "EPSG:32634");
    values.put("span", 50); values.put("strict", false); values.put("resolutions",new ArrayList<>(List.of(10,50,100)));
    var configuration=DigitalTwin.Configuration.builder().grid(values).build();
    values.put("span",100);
    ((List<Integer>)values.get("resolutions")).clear();
    var grid=GridAlignmentSupport.decode(configuration.getGridDefinition());
    assertEquals(configuration.getGridUrn(),grid.getUrn()); assertEquals(50,grid.stepX());
    assertTrue(grid.codeDefinition().contains("span: 50"));
    assertEquals(configuration.getGridUrn(),org.integratedmodelling.klab.api.digitaltwin.GridSpecification.urn(new HashMap<>(configuration.getGridDefinition())));
    var copied=DigitalTwin.Configuration.builder(configuration).build();
    assertEquals(configuration.getGridDefinition(),copied.getGridDefinition());
    var transported=Utils.Json.parseObject(Utils.Json.asString(configuration),org.integratedmodelling.klab.api.digitaltwin.impl.ConfigurationImpl.class);
    assertEquals(configuration.getGridUrn(),transported.getGridUrn());
    assertEquals(grid,GridAlignmentSupport.decode(transported.getGridDefinition()));
    assertThrows(UnsupportedOperationException.class,()->configuration.getGridDefinition().put("span",100));
    assertThrows(IllegalArgumentException.class,()->DigitalTwin.Configuration.builder(configuration).grid(values));
  }
  @Test void inlineQuantitiesRetainUnitsAndShareIdentityWithEquivalentText() {
    var quantities=DigitalTwin.Configuration.builder().grid(Map.of("anchor","POINT (0 0)","span",Quantity.of(50,"m"))).build();
    var text=DigitalTwin.Configuration.builder().grid(Map.of("anchor","POINT (0 0)","span","50.m")).build();
    assertEquals(text.getGridUrn(),quantities.getGridUrn());
    assertEquals(GridAlignmentSupport.decode(text.getGridDefinition()),GridAlignmentSupport.decode(quantities.getGridDefinition()));
    assertTrue(GridAlignmentSupport.decode(quantities.getGridDefinition()).codeDefinition().contains("span: 50.m"));
  }
  @Test void namedAlignmentUsesTheStandardResourcesRetrieveContract() {
    var scope=org.mockito.Mockito.mock(org.integratedmodelling.klab.api.scope.UserScope.class);
    var resources=org.mockito.Mockito.mock(org.integratedmodelling.klab.api.services.ResourcesService.class);
    var alignment=decode(60,true,true);
    org.mockito.Mockito.when(scope.getService(org.integratedmodelling.klab.api.services.ResourcesService.class)).thenReturn(resources);
    org.mockito.Mockito.when(resources.retrieve("test.grid",GridAlignment.class,scope)).thenReturn(alignment);
    assertEquals(alignment,GridAlignmentSupport.resolve("test.grid",scope));
    var type=org.integratedmodelling.klab.api.knowledge.KlabAsset.KnowledgeClass.GRID_ALIGNMENT;
    assertEquals(type,org.integratedmodelling.klab.api.knowledge.KlabAsset.classify(alignment));
    assertEquals(type,org.integratedmodelling.klab.api.knowledge.KlabAsset.KnowledgeClass.classify(GridAlignment.class));
    assertEquals(GridAlignment.class,type.getAssetClass());
    org.mockito.Mockito.verify(resources).retrieve("test.grid",GridAlignment.class,scope);
    org.mockito.Mockito.verifyNoMoreInteractions(resources);
  }
  @Test void suppressionSurvivesDefinitionDtoTransportAndCannotHideErrors() {
    var definition=definition(Map.of("anchor","POINT (1 2)","span","50.m"));
    var suppress=org.integratedmodelling.klab.api.lang.Annotation.of("suppress");
    suppress.putUnnamed("warnings"); definition.getAnnotations().add(suppress);
    var grid=GridAlignmentSupport.decode(definition);
    assertFalse(grid.warnings().isEmpty()); assertTrue(grid.emittedWarnings().isEmpty());
    assertTrue(new KimValidator().validateStatement(definition,null).isEmpty());
    var copy=Utils.Json.parseObject(Utils.Json.asString(grid),GridAlignment.class);
    assertEquals(grid,copy); assertTrue(copy.emittedWarnings().isEmpty());
    copy.getAnnotations().iterator().next().put("value","errors");
    assertTrue(copy.emittedWarnings().isEmpty());
    suppress.put("value","errors"); assertTrue(grid.emittedWarnings().isEmpty());
    definition.setValue(Map.of("anchor","POINT (0 0)","span",0));
    assertEquals(Notification.Level.Error,new KimValidator().validateStatement(definition,null).getFirst().getLevel());
    assertFalse(org.integratedmodelling.klab.api.lang.NotificationSuppression.suppresses(definition.getAnnotations(),Notification.Level.Error));
  }
  @Test void malformedInstructionsFailValidation() {
    assertThrows(IllegalArgumentException.class,()->decode(0,true,true));
    var definition=definition(Map.of("anchor","POINT (0 0)","span",10,"snap","false"));
    assertEquals(Notification.Level.Error,new KimValidator().validateStatement(definition,null).getFirst().getLevel());
  }
  @Test void metricDatelineSpecificationsAndCrossProjectionShapesNormalizeBeforeResolution() {
    var alignment=decode(1,false,true);
    var crossing=Geometry.create("S2{proj=EPSG:4326,bbox=[179 181 0 1],sgrid=50.m}");
    var aligned=GridAlignmentSupport.alignGeometry(crossing,alignment);
    assertNotNull(new org.integratedmodelling.klab.runtime.scale.ScaleImpl(aligned).getSpace());
    var local=GridAlignmentSupport.decode(definition(Map.of("projection","EPSG:32634","anchor","POINT (500000 4200000)","span",1000)));
    var projected=GridAlignmentSupport.alignGeometry(Geometry.create("S2(2,2){proj=EPSG:4326,bbox=[21 21.01 38 38.01]}"),local);
    assertEquals("EPSG:32634",ShapeImpl.create(projected.dimension(Geometry.Dimension.Type.SPACE).getParameters().get("shape").toString()).getProjection().getCode());
  }

  @Test void decimalQuantitiesAndGlobalMercatorStepsAreSupported() {
    assertEquals("m",Quantity.of(50.5,"m").getUnit()); assertEquals(50.5,Quantity.create("50.5.m").getValue().doubleValue());
    var alignment=GridAlignmentSupport.decode(definition(Map.of("projection","EPSG:3857","anchor","POINT (123 456)","span",50,"snap",false)));
    double period=2*Math.PI*6378137;
    assertEquals(Math.rint(period/alignment.stepX()),period/alignment.stepX(),1e-7);
    var world=GridAlignmentSupport.align(new GridImpl(EnvelopeImpl.create(-period/2,period/2,-period/2,period/2,Projection.of("EPSG:3857")),null,1,1),alignment);
    assertEquals(Math.round(period/alignment.stepX()),world.getXCells());
  }

  @Test void metreSpanAtAPoleWarnsAndUsesTheAdjacentRowRatherThanRejectingTheUnits() {
    var grid=GridAlignmentSupport.decode(definition(Map.of("anchor","POINT (0 90)","span",Quantity.create("50.m"))));
    assertTrue(grid.stepX()>0 && grid.stepY()>0);
    assertTrue(grid.warnings().stream().anyMatch(w->w.contains("undefined at the pole")));
  }

}
