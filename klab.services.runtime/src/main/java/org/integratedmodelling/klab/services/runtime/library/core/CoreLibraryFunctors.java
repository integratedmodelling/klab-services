package org.integratedmodelling.klab.services.runtime.library.core;

import io.micrometer.common.annotation.ValueResolver;
import org.integratedmodelling.klab.api.Klab;
import org.integratedmodelling.klab.api.data.Version;
import org.integratedmodelling.klab.api.data.mediation.Currency;
import org.integratedmodelling.klab.api.data.mediation.NumericRange;
import org.integratedmodelling.klab.api.data.mediation.Unit;
import org.integratedmodelling.klab.api.knowledge.Artifact;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.observation.DirectObservation;
import org.integratedmodelling.klab.api.knowledge.observation.State;
import org.integratedmodelling.klab.api.lang.ServiceCall;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.runtime.extension.Instantiator;
import org.integratedmodelling.klab.api.services.runtime.extension.KlabFunction;
import org.integratedmodelling.klab.api.services.runtime.extension.KlabFunction.Argument;
import org.integratedmodelling.klab.api.services.runtime.extension.Library;
import org.integratedmodelling.klab.api.services.runtime.extension.Resolver;

import java.util.List;

/**
 * Implementations for the core library functions. The language service will handle these and give access to
 * the functors through their {@link ServiceCall}s. Each service call must correspond to those built by the
 * {@link org.integratedmodelling.klab.api.services.CoreLibrary} service implemented across communicating
 * k.LAB services.
 * <p>
 * @deprecated the StandardLibrary.KlabCore enum should suffice, implemented in the
 *  runtime and specifically translated by the executors and mappers. Each enum should contain the
 *   scalar/vector nature and the needed <parameter,type> pairs for dataflow validation.
 */
@Library(name = Klab.StandardLibrary.KlabCore.NAMESPACE, description = "")
public class CoreLibraryFunctors {

    @Library(name = "resources", description = "")
    public static class Resources {

        // TODO
        @KlabFunction(name = Klab.StandardLibrary.KlabCore.URN_RESOLVER,
                      dataflowLabel =
                              "Resource", description = "Contextualize a quality resource to obtain data",
                      parameters = {
                              @Argument(name = "urn", description = "The URN of the resource to " +
                                      "contextualize", type =
                                                Artifact.Type.TEXT)}, type = Artifact.Type.VALUE)
        public static class ResourceResolver implements Resolver<State> {

            /*
             * TODO allow inserting a list of ValueResolver (or other functions) to perform mediations
             * and/or classifications that are declared in sequence after the resource.
             */

            @Override
            public void resolve(State observation, ServiceCall call, ContextScope scope) {
                // TODO Auto-generated method stub

                /**
                 * TODO
                 *
                 * For multi-valued even grids in non-local resources, if we can split the scale into
                 * comparable contiguous sections and we find the resource in >1 services, we can
                 * retrieve tiles concurrently instead of downloading the entire monster. The storage
                 * can be tiled or not. This can be triggered after considering the size of the context.
                 */
            }

        }

        @KlabFunction(name = Klab.StandardLibrary.KlabCore.LUT_RESOLVER,
                      dataflowLabel =
                              "Lookup table", description = "Compute outputs by looking up dependency " +
                "values in a table",
                      parameters = {
                              @Argument(name = "urn", description = "The URN of table to use", type =
                                      Artifact.Type.TEXT)}, type =
                              Artifact.Type.VALUE)
        public static class LookupTableResolver implements ValueResolver {

            @Override
            public String resolve(Object o) {
                return null;
            }
        }

        @KlabFunction(name = Klab.StandardLibrary.KlabCore.URN_INSTANTIATOR,
                      dataflowLabel = "Resource", description = "Contextualize a Type.OBJECT resource to " +
                "obtain " +
                "objects", parameters = {
                @Argument(name = "urn", description = "The URN of the resource to contextualize", type =
                        Artifact.Type.TEXT),
                @Argument(name = "whole", description = "Cut the spatial extent of the resulting object " +
                        "instead of " +
                        "intersecting with the context shape", type = Artifact.Type.BOOLEAN)}, type =
                              Artifact.Type.OBJECT)
        public static class SubjectResourceInstantiator implements Instantiator<DirectObservation> {

            @Override
            public List<DirectObservation> resolve(Observable semantics, ServiceCall call,
                                                   ContextScope scope) {

                /**
                 * TODO
                 *
                 * Investigate if we can split the scale into comparable contiguous sections and we find
                 * the resource in >1 services to access the data concurrently in sections. There are
                 * issues with overlapping objects and decisions about the number of objects that would
                 * require previous knowledge (e.g. the "density" of objects in each tile). This would
                 * benefit from creating and using machine-learned metadata.
                 */

                return null;
            }

        }
    }

    @Library(name = "values", description = "")
    public static class ValueOperators {
        // TODO value operator handlers - add POD type for non-boxing ones in declaration
    }

    @Library(name = "mediators", description = "")
    public static class Mediators {
        // TODO mediator methods

        public static double convertUnit(double original, Unit originalUnit, Unit targetUnit) {
            return 0;
        }

        public static double convertCurrency(double original, Currency originalUnit, Currency targetUnit) {
            return 0;
        }

        public static double convertRange(double original, NumericRange originalUnit,
                                          NumericRange targetUnit) {
            return 0;
        }
    }

    @Library(name = "dereifiers", description = "")
    public static class Dereifiers {
        // TODO mediator methods
    }

}
