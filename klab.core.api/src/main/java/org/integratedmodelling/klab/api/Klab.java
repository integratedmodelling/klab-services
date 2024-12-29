package org.integratedmodelling.klab.api;

import org.integratedmodelling.klab.api.data.Data;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.knowledge.observation.scale.Extent;
import org.integratedmodelling.klab.api.knowledge.observation.scale.Scale;
import org.integratedmodelling.klab.api.knowledge.observation.scale.space.Projection;
import org.integratedmodelling.klab.api.knowledge.observation.scale.space.Shape;
import org.integratedmodelling.klab.api.lang.Contextualizable;
import org.integratedmodelling.klab.api.lang.Quantity;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.services.resolver.Coverage;
import org.integratedmodelling.klab.api.services.runtime.extension.KlabFunction;
import org.integratedmodelling.klab.api.services.runtime.extension.Library;

import java.util.Collection;
import java.util.Map;

/**
 * Holds global configurations and functions that allow generic interfaces to expose constructor methods that
 * produce implementation classes that depend on complex dependencies. Implements a poor-man injection pattern
 * that needs to be configured in a static block, as done in <code>klab.services.core</code>. This permits
 * complex classes like Scale or Projection to have generic builders declared in the API package.
 *
 * @author Ferd
 */
public enum Klab {

    INSTANCE;

    /**
     * Error codes for all situations. These can (should) be passed, along with an ErrorContext, to
     * {@link org.integratedmodelling.klab.api.services.runtime.Channel#error(Object...)} to qualify the
     * resulting actions.
     */
    public enum ErrorCode {
        NO_ERROR,
        RESOURCE_VALIDATION,
        UNRESOLVED_REFERENCE,
        CIRCULAR_REFERENCES,
        MISMATCHED_VERSION,
        READ_FAILED,
        WRITE_FAILED,
        INTERNAL_ERROR
    }

    public enum ErrorContext {
        PROJECT,
        NAMESPACE,
        ONTOLOGY,
        RESOURCE,
        RUNTIME,
        OBSERVATION_STRATEGY,
        BEHAVIOR,
        RESOURCES_SERVICE
    }

    /**
     * Names for the core functions linked to the runtime. These are the service calls that every runtime must
     * implement without having to resort to components, although specially authorized components may override
     * them.
     * <p>
     * The standard library is divided into separate parts corresponding to sub-interfaces. Each must have a
     * NAMESPACE field that is the namespace prefix for all the service calls in it. The actual function names
     * must <em>not include</em> the namespace. Static functions produce the service calls themselves
     * providing a contract and type checking for the parameters.
     * <p>
     * The actual implementations must be provided in {@link Library}-annotated classes (with the NAMESPACE as
     * ID) with subclasses annotated with {@link KlabFunction} providing the individual functions,
     * corresponding exactly to the calls generated.
     *
     * @author Ferd
     */
    public interface StandardLibrary {

        public interface Extents {

            public static final String NAMESPACE = Library.CORE_LIBRARY;

            public static final String SPACE = "space";

            public static final String TIME = "time";

        }

    }

    /**
     * This is implemented and configured by services so that static constructors of classes that need complex
     * dependencies can be provided with the correspondent interfaces in the <code>klab.core.api</code>
     * package. Implements a poor-man injection pattern without the pain of actual injection.
     *
     * @author Ferd
     */
    public interface Configuration {

        Observable promoteConceptToObservable(Concept concept);

        Observable.Builder getObservableBuilder(Concept observable, Scope scope);

        Observable.Builder getObservableBuilder(Observable observable, Scope scope);

        Scale promoteGeometryToScale(Geometry geometry, Scope scope);

        Projection getDefaultSpatialProjection();

        Projection getLatLonSpatialProjection();

        Scale createScaleFromExtents(Collection<Extent<?>> extents);

        Shape createShapeFromTextSpecification(String shapeText, Projection projection);

        Projection getSpatialProjection(String string);

        Coverage promoteScaleToCoverage(Scale geometry, double coverage);

        Model.Builder getModelBuilder(Observable observable);

        Model.Builder getModelBuilder(Artifact.Type nonSemanticType);

        Model.Builder getModelBuilder(Resource resource);

        Model.Builder getModelBuilder(Object value);

        Model.Builder getModelLearner(String outputResourceUrn);

        Data.Builder getDataBuilder();

        Quantity parseQuantity(String quantityDescription);

        /**
         * Deep copy of an extent - anything not immutable must be a new object.
         *
         * @param extent
         * @return
         */
        Extent<?> createExtentCopy(Extent<?> extent);
    }

    private Configuration configuration;

    /**
     * Call this in the static block of the core package configuration to ensure that the constructors know
     * how to do their job.
     *
     * @param configuration
     */
    public void setConfiguration(Configuration configuration) {
        this.configuration = configuration;
    }

    public Configuration getConfiguration() {
        return this.configuration;
    }

}
