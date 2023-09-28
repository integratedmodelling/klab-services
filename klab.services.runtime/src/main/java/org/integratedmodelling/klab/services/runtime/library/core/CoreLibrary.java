package org.integratedmodelling.klab.services.runtime.library.core;

import java.util.List;

import io.micrometer.common.annotation.ValueResolver;
import org.integratedmodelling.klab.Version;
import org.integratedmodelling.klab.api.Klab;
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

@Library(name = Klab.StandardLibrary.KlabCore.NAMESPACE, description = "")
public class CoreLibrary {

    // TODO
    @KlabFunction(version = Version.CURRENT, name = Klab.StandardLibrary.KlabCore.URN_RESOLVER, dataflowLabel = "Resource", description = "Contextualize a quality resource to obtain data", parameters = {
            @Argument(name = "urn", description = "The URN of the resource to contextualize", type = Artifact.Type.TEXT)}, type = Artifact.Type.VALUE)
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

    @KlabFunction(version = Version.CURRENT, name = Klab.StandardLibrary.KlabCore.LUT_RESOLVER, dataflowLabel = "Lookup table", description = "Compute outputs by looking up dependency values in a table", parameters = {
            @Argument(name = "urn", description = "The URN of table to use", type = Artifact.Type.TEXT)}, type = Artifact.Type.VALUE)
    public static class LookupTableResolver implements ValueResolver {

        @Override
        public String resolve(Object o) {
            return null;
        }
    }

    @KlabFunction(version = Version.CURRENT, name = Klab.StandardLibrary.KlabCore.URN_INSTANTIATOR, dataflowLabel = "Resource", description = "Contextualize a Type.OBJECT resource to obtain objects", parameters = {
            @Argument(name = "urn", description = "The URN of the resource to contextualize", type = Artifact.Type.TEXT),
            @Argument(name = "whole", description = "Cut the spatial extent of the resulting object instead of intersecting with the context shape", type = Artifact.Type.BOOLEAN)}, type = Artifact.Type.OBJECT)
    public static class SubjectResourceInstantiator implements Instantiator<DirectObservation> {

        @Override
        public List<DirectObservation> resolve(Observable semantics, ServiceCall call, ContextScope scope) {

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
