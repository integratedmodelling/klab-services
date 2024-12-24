package org.integratedmodelling.klab.runtime.scale.space;

import org.integratedmodelling.common.knowledge.KnowledgeRepository;
import org.integratedmodelling.klab.api.configuration.Configuration;
import org.integratedmodelling.klab.api.exceptions.KlabIllegalArgumentException;
import org.integratedmodelling.klab.api.exceptions.KlabUnimplementedException;
import org.integratedmodelling.klab.api.geometry.Geometry.Dimension;
import org.integratedmodelling.klab.api.geometry.impl.GeometryImpl;
import org.integratedmodelling.klab.api.knowledge.observation.scale.space.*;
import org.integratedmodelling.klab.api.lang.Quantity;
import org.integratedmodelling.klab.api.lang.kim.KimSymbolDefinition;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.services.ResourcesService;
import org.integratedmodelling.klab.api.utils.Utils;
import org.integratedmodelling.klab.runtime.scale.ExtentImpl;
import org.locationtech.jts.geom.GeometryFactory;

import java.io.Serial;
import java.util.List;
import java.util.Map;

public abstract class SpaceImpl extends ExtentImpl<Space> implements Space {

    @Serial
    private static final long serialVersionUID = 1L;

    static GeometryFactory gFactory = new GeometryFactory();

    public SpaceImpl() {
        super(Dimension.Type.SPACE);
    }

    //	Envelope envelope;

    //	@Override
    //	public Space at(Object... locators) {
    //		// TODO Auto-generated method stub
    //		return null;
    //	}
    //
    //	@Override
    //	public int getRank() {
    //		// TODO Auto-generated method stub
    //		return 0;
    //	}
    //
    //	@Override
    //	public Space collapsed() {
    //		// TODO Auto-generated method stub
    //		return null;
    //	}
    //
    //	@Override
    //	public double getDimensionSize() {
    //		// TODO Auto-generated method stub
    //		return 0;
    //	}
    //
    //	public Unit getDimensionUnit() {
    //		return null;
    //	}
    //
    //	@Override
    //	public Space getExtent(long stateIndex) {
    //		// TODO Auto-generated method stub
    //		return null;
    //	}
    //
    //	@Override
    //	public <T extends Locator> T as(Class<T> cls) {
    //		// TODO Auto-generated method stub
    //		return null;
    //	}

    //	@Override
    //	public boolean contains(Space o) {
    //		// TODO Auto-generated method stub
    //		return false;
    //	}
    //
    //	@Override
    //	public boolean overlaps(Space o) {
    //		// TODO Auto-generated method stub
    //		return false;
    //	}
    //
    //	@Override
    //	public boolean intersects(Space o) {
    //		// TODO Auto-generated method stub
    //		return false;
    //	}
    //
    //	@Override
    //	public Iterator<Space> iterator() {
    //		// TODO Auto-generated method stub
    //		return null;
    //	}
    //
    //	@Override
    //	public Shape getGeometricShape() {
    //		// TODO Auto-generated method stub
    //		return null;
    //	}
    //
    //	@Override
    //	public Envelope getEnvelope() {
    //		// TODO Auto-generated method stub
    //		return null;
    //	}
    //
    //	@Override
    //	public Projection getProjection() {
    //		// TODO Auto-generated method stub
    //		return null;
    //	}
    //
    //	@Override
    //	public double getStandardizedVolume() {
    //		// TODO Auto-generated method stub
    //		return 0;
    //	}
    //
    //	@Override
    //	public double getStandardizedArea() {
    //		// TODO Auto-generated method stub
    //		return 0;
    //	}
    //
    //	@Override
    //	public double getStandardizedWidth() {
    //		// TODO Auto-generated method stub
    //		return 0;
    //	}
    //
    //	@Override
    //	public double[] getStandardizedCentroid() {
    //		// TODO Auto-generated method stub
    //		return null;
    //	}
    //
    //	@Override
    //	public double getStandardizedHeight() {
    //		// TODO Auto-generated method stub
    //		return 0;
    //	}
    //
    //	@Override
    //	public double getStandardizedDepth() {
    //		// TODO Auto-generated method stub
    //		return 0;
    //	}
    //
    //	@Override
    //	public double getStandardizedLength() {
    //		// TODO Auto-generated method stub
    //		return 0;
    //	}
    //
    //	@Override
    //	public double getStandardizedDistance(Space extent) {
    //		// TODO Auto-generated method stub
    //		return 0;
    //	}
    //
    //	@Override
    //	public double getStandardizedDimension(Locator locator) {
    //		// TODO Auto-generated method stub
    //		return 0;
    //	}
    //
    //	@Override
    //	public boolean isEmpty() {
    //		// TODO Auto-generated method stub
    //		return false;
    //	}
    //
    //	@Override
    //	public Extent<?> merge(Extent<?> other, LogicalConnector how) {
    //		// TODO Auto-generated method stub
    //		return null;
    //	}
    //
    //	@Override
    //	public boolean matches(Collection<Constraint> constraints) {
    //		// TODO Auto-generated method stub
    //		return false;
    //	}

    public static Space create(Dimension dimension) {
        return create(dimension, null);
    }

    public static Space create(Dimension dimension, Scope scope) {

        Space ret = null;
        var resourceUrn = dimension.getParameters().get(
                GeometryImpl.PARAMETER_SPACE_RESOURCE_URN,
                String.class);
        var bboxDefinition = dimension.getParameters().get(GeometryImpl.PARAMETER_SPACE_BOUNDINGBOX);
        var pointDefinition = dimension.getParameters().get(
                GeometryImpl.PARAMETER_SPACE_LONLAT,
                String.class);

        if (resourceUrn != null) {
            if (scope == null || scope.getService(ResourcesService.class) == null) {
                throw new KlabIllegalArgumentException("cannot create spatial extent from resource: " +
                        "resource services not available");
            }
            var resource = scope.getService(ResourcesService.class).resolveResource(resourceUrn, scope);
            dimension =
                    resource.getGeometry().getDimensions().stream().filter(
                            d -> d.getType() == Type.SPACE).findAny().get();
        }

        var shapeDefinition = dimension.getParameters().get(GeometryImpl.PARAMETER_SPACE_SHAPE, String.class);
        Projection projection =
                new ProjectionImpl(dimension.getParameters().get(
                        GeometryImpl.PARAMETER_SPACE_PROJECTION,
                        "EPSG:4326"));
        Envelope envelope = null;

        Shape shape = null;
        if (shapeDefinition != null) {
            shape = ShapeImpl.create(shapeDefinition);
            projection = shape.getProjection();
            envelope = shape.getEnvelope();
        } else if (pointDefinition != null) {
            throw new KlabUnimplementedException("cannot create point from lat/lon coordinates definition " +
                    "yet");
        }

        if (bboxDefinition != null) {
            List<Double> corners = null;
            if (bboxDefinition instanceof List list) {
                corners = list;
            } else if (bboxDefinition instanceof String string) {
                corners = Utils.Data.parseList(string, Double.class);
            }
            envelope = EnvelopeImpl.create(corners.get(0), corners.get(1), corners.get(2), corners.get(3),
                    projection);
        }

        if (dimension.isRegular()) {

            Grid grid = null;
            boolean adjust = true;
            var gridResolution = dimension.getParameters().get(GeometryImpl.PARAMETER_SPACE_GRIDRESOLUTION);
            var gridUrn = dimension.getParameters().get(GeometryImpl.PARAMETER_SPACE_GRIDURN, String.class);
            Grid imposedGrid = null;
            if (gridUrn != null) {
                if (scope == null || scope.getService(ResourcesService.class) == null) {
                    throw new KlabIllegalArgumentException("cannot create spatial extent from resource: " +
                            "resource services not available");
                }
                var definition = scope.getService(ResourcesService.class).resolve(gridUrn, scope);
                // TODO ingest the resource set and parse the symbol
                if (Utils.Notifications.hasErrors(definition.getNotifications())) {
                    throw new KlabUnimplementedException("cannot create grid from definition yet");
                }
                var result =
                        KnowledgeRepository.INSTANCE.ingest(
                                scope.getService(ResourcesService.class).resolve(gridUrn, scope), scope,
                                KimSymbolDefinition.class);
                if (result.size() == 1) {
                    var gridDef = result.getFirst();
                    if (gridDef instanceof Map map) {
                        grid = new GridImpl(map);
                    }
                }
            } else if (gridResolution != null && envelope != null) {
                Quantity resolution = gridResolution instanceof Quantity quantity ? quantity :
                                      Quantity.create(gridResolution.toString());
                grid = new GridImpl(envelope, resolution,
                        Boolean.parseBoolean(
                                Configuration.INSTANCE.getProperty(Configuration.KLAB_USE_IN_MEMORY_DATABASE,
                                        "true")));
            } else if (shape != null && dimension.getShape().size() > 1 && dimension.getShape().stream().reduce(
                    1L, (a, b) -> a * b) > 1) {
                // predefined, assume it's been created correctly from a previous envelope. This is the way
                // grids are communicated through the runtime.
                grid = new GridImpl(envelope, shape, dimension.getShape().get(0), dimension
                        .getShape().get(1));
                adjust = false;
            }
            if (grid != null && imposedGrid != null) {
                grid = grid.align(imposedGrid);
            }

            if (shape != null && grid != null) {
                return new TileImpl(shape, grid, adjust);
            } else if (shape != null) {
                return shape;
            }

        } else if (dimension.size() > 1) {
            throw new KlabUnimplementedException("cannot create point from this definition " +
                    "yet");
        } else if (shape != null) {
            return shape;
        }
        return null;
    }

}
