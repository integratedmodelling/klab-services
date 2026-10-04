package org.integratedmodelling.klab.runtime.scale.space;

import org.integratedmodelling.klab.api.configuration.Configuration;
import org.integratedmodelling.klab.api.exceptions.KlabIllegalArgumentException;
import org.integratedmodelling.klab.api.exceptions.KlabUnimplementedException;
import org.integratedmodelling.klab.api.geometry.Geometry.Dimension;
import org.integratedmodelling.klab.api.geometry.impl.GeometryImpl;
import org.integratedmodelling.klab.api.knowledge.Resource;
import org.integratedmodelling.klab.api.knowledge.observation.scale.space.*;
import org.integratedmodelling.klab.api.lang.Quantity;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.api.services.ResourcesService;
import org.integratedmodelling.klab.api.utils.Utils;
import org.integratedmodelling.klab.runtime.scale.ExtentImpl;
import org.locationtech.jts.geom.GeometryFactory;

import java.io.Serial;
import java.util.List;

public abstract class SpaceImpl extends ExtentImpl<Space> implements Space {

  @Serial private static final long serialVersionUID = 1L;

  static GeometryFactory gFactory = new GeometryFactory();

  public SpaceImpl() {
    super(Dimension.Type.SPACE);
  }

  public static Space create(Dimension dimension) {
    return create(dimension, null);
  }

  public static Space create(Dimension dimension, Scope scope) {

    Space ret = null;
    var resourceUrn =
        dimension.getParameters().get(GeometryImpl.PARAMETER_SPACE_RESOURCE_URN, String.class);
    var bboxDefinition = dimension.getParameters().get(GeometryImpl.PARAMETER_SPACE_BOUNDINGBOX);
    var pointDefinition =
        dimension.getParameters().get(GeometryImpl.PARAMETER_SPACE_LONLAT, String.class);

    if (resourceUrn != null) {
      if (scope == null || scope.getService(ResourcesService.class) == null) {
        throw new KlabIllegalArgumentException(
            "cannot create spatial extent from resource: " + "resource services not available");
      }
      if (!(scope instanceof UserScope userScope)) {
        throw new KlabIllegalArgumentException("A user scope is required to retrieve resources");
      }
      Resource resource =
          scope.getService(ResourcesService.class).retrieve(resourceUrn, Resource.class, userScope);
      dimension =
          resource.getGeometry().getDimensions().stream()
              .filter(d -> d.getType() == Type.SPACE)
              .findAny()
              .get();
    }

    var shapeDefinition =
        dimension.getParameters().get(GeometryImpl.PARAMETER_SPACE_SHAPE, String.class);
    Projection projection =
        new ProjectionImpl(
            dimension.getParameters().get(GeometryImpl.PARAMETER_SPACE_PROJECTION, "EPSG:4326"));
    Envelope envelope = null;

    Shape shape = null;
    if (shapeDefinition != null) {
      shape = ShapeImpl.create(shapeDefinition);
      projection = shape.getProjection();
      envelope = shape.getEnvelope();
    } else if (pointDefinition != null) {
      throw new KlabUnimplementedException(
          "cannot create point from lat/lon coordinates definition " + "yet");
    }

    if (bboxDefinition != null) {
      List<Double> corners = null;
      if (bboxDefinition instanceof List<?> list) {
        corners = list.stream().map(value -> value instanceof Number number ? number.doubleValue() : Double.parseDouble(value.toString())).toList();
      } else if (bboxDefinition instanceof String string) {
        corners = java.util.Arrays.stream(string.replace('[',' ').replace(']',' ').trim().split("[\\s,]+"))
            .map(Double::parseDouble).toList();
      }
      if (corners==null || corners.size()!=4) throw new KlabIllegalArgumentException("Spatial bounds require four coordinates");
      envelope =
          EnvelopeImpl.create(
              corners.get(0), corners.get(1), corners.get(2), corners.get(3), projection);
      if (shape == null) shape = envelope.asShape();
    }

    if (dimension.isRegular()) {

      Grid grid = null;
      boolean adjust = true;
      var gridResolution =
          dimension.getParameters().get(GeometryImpl.PARAMETER_SPACE_GRIDRESOLUTION);
      var gridUrn =
          dimension.getParameters().get(GeometryImpl.PARAMETER_SPACE_GRIDURN, String.class);
      if (gridUrn != null) {
        if (scope == null || scope.getService(ResourcesService.class) == null) {
          throw new KlabIllegalArgumentException(
              "cannot create spatial extent from resource: " + "resource services not available");
        }
        if (!(scope instanceof UserScope userScope)) {
          throw new KlabIllegalArgumentException("A user scope is required to resolve resources");
        }
        if (envelope == null) throw new KlabIllegalArgumentException("Named grid requires spatial bounds");
        var requested = dimension.getShape()!=null && dimension.getShape().size()==2 && dimension.getShape().stream().allMatch(n -> n>0)
            ? new GridImpl(envelope,shape,dimension.getShape().get(0),dimension.getShape().get(1))
            : new GridImpl(envelope,shape,1,1);
        grid = GridAlignmentSupport.align(requested,GridAlignmentSupport.resolve(gridUrn,scope));
        adjust = false;
      } else if (gridResolution != null && envelope != null) {
        Quantity resolution =
            gridResolution instanceof Quantity quantity
                ? quantity
                : Quantity.create(gridResolution.toString());
        grid =
            new GridImpl(
                envelope,
                resolution,
                Boolean.parseBoolean(
                    Configuration.INSTANCE.getProperty(
                        Configuration.KLAB_USE_IN_MEMORY_DATABASE, "true")),
                dimension.getParameters().containsKey("world") ? GridAlignmentSupport.worldBounds(projection) : new double[0]);
      } else if (shape != null
          && dimension.getShape() != null
          && dimension.getShape().size() > 1
          && dimension.getShape().stream().allMatch(size -> size > 0)) {
        if (envelope == null) {
          envelope = shape.getEnvelope();
        }
        // predefined, assume it's been created correctly from a previous envelope. This is the way
        // grids are communicated through the runtime.
        grid =
            new GridImpl(envelope, shape, dimension.getShape().get(0), dimension.getShape().get(1));
        Object world = dimension.getParameters().get("world");
        if (world != null) {
          var values=java.util.Arrays.stream(world.toString().replace('[',' ').replace(']',' ').trim().split("[\\s,]+"))
              .mapToDouble(Double::parseDouble).toArray();
          ((GridImpl)grid).setWorldBounds(values);
        }
        adjust = false;
      }

      if (shape != null && grid != null) {
        if (!shape.getProjection().equals(grid.getProjection())) shape=ShapeImpl.promote(shape).transform(grid.getProjection());
        // Only newly resolved grids expand rectangular requests. A transported grid may carry
        // an explicit rectangular crop and must keep that mask.
        if (gridUrn != null) shape = GridAlignmentSupport.rectangularSupport(shape,grid);
        return new TileImpl(shape, grid, adjust);
      } else if (shape != null) {
        return shape;
      }

    } else if (dimension.size() > 1) {
      throw new KlabUnimplementedException("cannot create point from this definition " + "yet");
    } else if (shape != null) {
      return shape;
    }
    return dimension.isRegular() ? new TileImpl() : new ShapeImpl();
  }
}
