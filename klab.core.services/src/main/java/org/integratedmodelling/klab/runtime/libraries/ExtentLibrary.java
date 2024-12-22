package org.integratedmodelling.klab.runtime.libraries;

import org.integratedmodelling.klab.api.Klab;
import org.integratedmodelling.klab.api.collections.Pair;
import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.data.Version;
import org.integratedmodelling.klab.api.data.mediation.Unit;
import org.integratedmodelling.klab.api.exceptions.KlabValidationException;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.Artifact.Type;
import org.integratedmodelling.klab.api.knowledge.Resource;
import org.integratedmodelling.klab.api.knowledge.observation.scale.Scale;
import org.integratedmodelling.klab.api.knowledge.observation.scale.space.Grid;
import org.integratedmodelling.klab.api.knowledge.observation.scale.space.Projection;
import org.integratedmodelling.klab.api.knowledge.observation.scale.space.Shape;
import org.integratedmodelling.klab.api.knowledge.observation.scale.space.Space;
import org.integratedmodelling.klab.api.knowledge.observation.scale.time.Time;
import org.integratedmodelling.klab.api.knowledge.observation.scale.time.Time.Resolution;
import org.integratedmodelling.klab.api.knowledge.observation.scale.time.TimeDuration;
import org.integratedmodelling.klab.api.knowledge.observation.scale.time.TimeInstant;
import org.integratedmodelling.klab.api.lang.Quantity;
import org.integratedmodelling.klab.api.lang.ServiceCall;
import org.integratedmodelling.klab.api.lang.kim.KimDate;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.services.ResourcesService;
import org.integratedmodelling.klab.api.services.UnitService;
import org.integratedmodelling.klab.api.services.runtime.extension.KlabFunction;
import org.integratedmodelling.klab.api.services.runtime.extension.KlabFunction.Argument;
import org.integratedmodelling.klab.api.services.runtime.extension.Library;
import org.integratedmodelling.klab.configuration.ServiceConfiguration;
import org.integratedmodelling.klab.runtime.scale.space.GridImpl;
import org.integratedmodelling.klab.runtime.scale.space.ShapeImpl;
import org.integratedmodelling.klab.runtime.scale.space.TileImpl;
import org.integratedmodelling.klab.runtime.scale.time.TimeImpl;
import org.integratedmodelling.klab.utilities.Utils;

/**
 * @deprecated these should be accessed through defines
 */
@Library(name = Library.CORE_LIBRARY, description = "Core extent functions to declare space and time " +
        "extents.")
public class ExtentLibrary {

    @KlabFunction(name = Klab.StandardLibrary.Extents.SPACE, description = "Create spatial extents of all " +
            "supported types", type = Type.SPATIALEXTENT, parameters = {
            @Argument(name = "shape", type = Type.TEXT, description = "A geometric shape in WKT or WKB form"),
            @Argument(name = "squareCells", type = Type.BOOLEAN, description = "Force square cells (may " +
                    "change the envelope)"),
            @Argument(name = "grid", type = {Type.QUANTITY,
                                             Type.TEXT}, description = "Grid resolution", optional = true)})
    public static Space space(ServiceCall call, Scope scope) {

        Shape shape = null;
        Double resolution = null;
        String urn = null;
        Projection projection = null;
        double simplifyFactor = Double.NaN;
        boolean gridConstraint = false;
        boolean squareCells = false;

        Parameters<String> parameters = call.getParameters();
        Space ret = null;

        // if (parameters.contains("identifier") || parameters.contains("authority")
        // || parameters.contains("baseidentity")) {
        //
        // if (parameters.contains("identifier")) {
        // KimConcept concept = parameters.get("identifier", IKimConcept.class);
        // if (concept != null) {
        // IConcept c = Concepts.INSTANCE.declare(concept);
        // if (c != null && c.is(IKimConcept.Type.IDENTITY)) {
        // return new EnumeratedSpace(c);
        // }
        // }
        // throw new KlabIllegalArgumentException(
        // "value of spatial identity unknown or illegal: " +
        // parameters.get("identifier"));
        // } else if (parameters.contains("authority")) {
        // System.out.println("ZIBU");
        // } else if (parameters.contains("baseidentity")) {
        // System.out.println("ZOBU");
        // }
        // }

        if (parameters.containsKey("shape")) {
            shape = ShapeImpl.create(parameters.get("shape", String.class));
        }
        if (parameters.containsKey("grid")) {
            if (parameters.get("grid") != null) {
                resolution = parseResolution(parameters.get("grid"));
            } else {
                // it's a constraint
                gridConstraint = true;
            }
        }
        if (parameters.containsKey("urn")) {
            urn = parameters.get("urn", String.class);
        }
        if (parameters.containsKey("projection")) {
            projection = Projection.of(parameters.get("projection", String.class));
        }
        if (parameters.containsKey("simplify")) {
            simplifyFactor = parameters.get("simplify", Double.class);
        }
        if (parameters.containsKey("squareCells")) {
            squareCells = parameters.get("squareCells", Boolean.class);
        }

        if (shape != null) {
            if (resolution != null) {
                Grid grid = GridImpl.create(resolution, squareCells);
                ret = TileImpl.create(shape, grid, true);
            } else {
                ret = shape;
            }
            if (!Double.isNaN(simplifyFactor)) {
                ret.getGeometricShape().simplify(simplifyFactor);
            }
        }
        // } else if (urn != null) {
        //
        // Space space = shapeCache.get(urn);
        // if (space == null) {
        // Pair<IArtifact, IArtifact> artifact =
        // Resources.INSTANCE.resolveResourceToArtifact(urn,
        // scope == null ? Klab.INSTANCE.getRootMonitor() : scope.getMonitor());
        // if (artifact == null || artifact.getSecond().groupSize() < 1
        // || artifact.getSecond().getGeometry().getDimension(Type.SPACE) == null) {
        // throw new KlabIllegalArgumentException("urn " + urn + " does not resolve to a
        // spatial
        // object");
        // }
        // space = ((IScale)
        // artifact.getSecond().iterator().next().getGeometry()).getSpace();
        // shapeCache.put(urn, space);
        // }
        //
        // if (projection != null) {
        // space = space.getShape().transform(projection);
        // }
        //
        // if (!Double.isNaN(simplifyFactor)) {
        // ((Shape) space.getShape()).simplify(simplifyFactor);
        // }
        //
        // if (resolution == null) {
        // return space;
        // }
        //
        // ret =
        // org.integratedmodelling.klab.components.geospace.extents.Space.create((Shape)
        // space.getShape(),
        // resolution);
        //
        // }

        // if (ret == null && gridConstraint) {
        // ret =
        // org.integratedmodelling.klab.components.geospace.extents.Space.constraint(shape,
        // true);
        // }

        return ret;
    }

    @KlabFunction(name = Klab.StandardLibrary.Extents.TIME, description = "Create temporal extents of all " +
            "supported types", type = Type.TEMPORALEXTENT, parameters = {
            @Argument(name = "grid", type = {Type.QUANTITY,
                                             Type.TEMPORAL_RESOLUTION}, description = "A grid resolution " +
                    "with a temporal unit", optional = true),
            @Argument(name = "year", type = Type.NUMBER, description = "A grid resolution with a temporal " +
                    "unit", optional = true),
            @Argument(name = "start", type = Type.DATETIME, description = "A grid resolution with a " +
                    "temporal unit", optional = true),
            @Argument(name = "end", type = Type.DATETIME, description = "A grid resolution with a temporal " +
                    "unit", optional = true),
            @Argument(name = "step", type = Type.DATETIME, description = "A grid resolution with a temporal" +
                    " unit", optional = true),
            @Argument(name = "realtime", type = Type.BOOLEAN, description = "A grid resolution with a " +
                    "temporal unit", optional = true),
            @Argument(name = "generic", type = Type.BOOLEAN, description = "A grid resolution with a " +
                    "temporal unit", optional = true),
            @Argument(name = "focus", type = Type.DATETIME, description = "A grid resolution with a " +
                    "temporal unit", optional = true)})
    public static Time time(ServiceCall call, Scope scope) {

        TimeInstant start = null;
        TimeInstant end = null;
        TimeDuration step = null;
        Time.Resolution resolution = null;
        Time.Type type = Time.Type.PHYSICAL;
        Parameters<String> parameters = call.getParameters();

        if (parameters.contains("focus")) {

            if (parameters.get("focus") instanceof Quantity) {
                resolution = Time.Resolution.of(parameters.get("focus", Quantity.class));
            } else {
                try {
                    Time.Resolution.Type focus =
                            Time.Resolution.Type.parse(parameters.get("focus").toString());
                    resolution = Time.Resolution.of(1.0, focus);
                } catch (Throwable t) {
                    // later
                }
            }

            if (resolution == null) {
                throw new KlabValidationException("wrong specification of temporal focus in time function: " +
                        "expecting "
                        + "a quantity with temporal unit (e.g. 1.year) or a"
                        + " span description (e.g. 'year', 'month', 'century'...)");
            }
        }

        if (parameters.contains("year")) {
            start = TimeInstant.create(parameters.get("year", Number.class).intValue(), 1, 1, 0, 0, 0, 0);
            end = start.plus(1, Resolution.of(1, Resolution.Type.YEAR));
        } else {
            if (parameters.contains("start")) {
                if (parameters.get("start") instanceof KimDate) {
                    start = TimeInstant.create(parameters.get("start", KimDate.class));
                } else if (parameters.get("start") instanceof Number) {
                    start = TimeInstant.create(parameters.get("start", Number.class).intValue());
                } else {
                    throw new KlabValidationException(
                            "wrong specification of start time in time function: expecting a date literal " +
                                    "or an integer year.");
                }
            }
            if (parameters.contains("end")) {
                if (parameters.get("end") instanceof KimDate) {
                    end = TimeInstant.create(parameters.get("end", KimDate.class));
                } else if (parameters.get("end") instanceof Number) {
                    end = TimeInstant.create(parameters.get("end", Number.class).intValue());
                } else {
                    throw new KlabValidationException(
                            "wrong specification of end time in time function: expecting a date literal or " +
                                    "an integer year.");
                }
            }
        }

        if (parameters.contains("step")) {

            if (parameters.get("step") instanceof Quantity) {
                var sq = parameters.get("step", Quantity.class);
                if (resolution == null) {
                    resolution = Time.Resolution.of(sq);
                    if (resolution.getType() != null && resolution.getType().isRegular()) {
                        step = TimeDuration.of(sq);
                    }
                }
            } else if (parameters.get("step") instanceof Quantity) {
                Quantity sq = parameters.get("step", Quantity.class);
                if (resolution == null) {
                    resolution = Time.Resolution.of(sq);
                    if (resolution.getType() != null && resolution.getType().isRegular()) {
                        step = TimeDuration.of(sq);
                    }
                }
            } else if (parameters.get("step") instanceof String) {
                if (resolution == null) {
                    resolution = Time.Resolution.parse(parameters.get("step", String.class));
                    if (resolution.getType() != null && resolution.getType().isRegular()) {
                        step = TimeDuration.parse(parameters.get("step", String.class));
                    }
                }
            } else if (parameters.get("step") instanceof Number && resolution != null) {
                step = TimeDuration.of(parameters.get("step", Number.class), resolution.getType());
            } else {
                throw new KlabValidationException(
                        "wrong specification of step in time function: expecting number with units. "
                                + "A number is only allowed if focus is specified");
            }

            type = Time.Type.GRID;
        }

        if (resolution == null) {
            if (parameters.contains("year")) {
                resolution = Time.Resolution.of(1, Time.Resolution.Type.YEAR);
            } else if (start != null && end != null) {
                // should attribute based on start and end
                resolution = Time.Resolution.of(start, end);
            }
        }

        if (parameters.contains("realtime") && parameters.get("realtime", Boolean.FALSE)) {
            type = Time.Type.REAL;
            if (step == null) {
                throw new KlabValidationException("real time requires specification of start, end and step");
            }
        } else if (parameters.contains("generic") && parameters.get("generic", Boolean.FALSE)) {
            type = Time.Type.LOGICAL;
            if (step != null) {
                throw new KlabValidationException("generic time must have a focus and cannot have a step");
            }
        }

        return TimeImpl.create(type, resolution.getType(), resolution.getMultiplier(), start, end, step);
    }

    @KlabFunction(name = "geometry", description = "Create a scale based on a geometry or URN " +
            "specification", type = Type.GEOMETRY, parameters = {
            @Argument(name = "value", type = {Type.TEXT,
                                              Type.URN}, description = "A geometry string or a resource " +
                    "URN, whose geometry is used")})
    public static Scale geometry(ServiceCall call, Scope scope) {

        String value = call.getParameters().get("value", String.class);
        Scale ret = null;
        if (Utils.Urns.isUrn(value)) {
            Resource resource = scope.getService(ResourcesService.class).resolveResource(value, scope);
            if (resource != null) {
                ret = Scale.create(resource.getGeometry());
            }
        } else {
            ret = Scale.create(Geometry.create(value));
        }

        return ret;
    }

    /**
     * Parse a string like "1 km" or a k.IM quantity ('1.km') and return the meters in it. Throw an exception
     * if this cannot be parsed.
     *
     * @param string
     * @return the resolution in meters
     * @throws KlabValidationException
     */
    public static double parseResolution(Object spec) {

        Pair<Double, String> pd = null;
        if (spec instanceof String) {
            pd = org.integratedmodelling.common.utils.Utils.Strings.splitNumberFromString((String) spec);
        } else if (spec instanceof Quantity) {
            pd = Pair.of(((Quantity) spec).getValue().doubleValue(),
                    ((Quantity) spec).getCurrency() == null ? ((Quantity) spec).getUnit().toString()
                                                            : ((Quantity) spec).getCurrency().toString());
        }

        if (pd == null || pd.getFirst() == null || pd.getSecond() == null)
            throw new KlabValidationException("wrong resolution specification: " + spec);

        Unit uu = ServiceConfiguration.INSTANCE.getService(UnitService.class).getUnit(pd.getSecond());
        Unit mm = ServiceConfiguration.INSTANCE.getService(UnitService.class).meters();

        return mm.convert(pd.getFirst().doubleValue(), uu).doubleValue();
    }

}
