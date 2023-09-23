package org.integratedmodelling.klab.runtime.temporary;

import org.integratedmodelling.klab.api.data.Version;
import org.integratedmodelling.klab.api.data.mediation.impl.Range;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.geometry.Geometry.Dimension;
import org.integratedmodelling.klab.api.geometry.Offset;
import org.integratedmodelling.klab.api.geometry.Scanner2D;
import org.integratedmodelling.klab.api.knowledge.Artifact.Type;
import org.integratedmodelling.klab.api.knowledge.observation.State;
import org.integratedmodelling.klab.api.lang.ServiceCall;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.runtime.extension.KlabFunction;
import org.integratedmodelling.klab.api.services.runtime.extension.Library;
import org.integratedmodelling.klab.api.services.runtime.extension.Resolver;
import org.integratedmodelling.klab.runtime.storage.DoubleStorage;

import java.util.List;

@Library(name = "klab.geospatial.generators", description = "Contextualizers that generate realistic geographic " +
        "terrains and features for stress-testing")
public class Generators {

    @KlabFunction(name = "terrain", description = "Generate fractal surfaces within a user-defined range and with a " +
            "configurable degree of smoothness, apt to simulating several terrain patterns such as elevation or slope" +
            ". As the generator works in RAM, this should not be used on very large grids.", geometry = "S2",
                  type = Type.NUMBER, version = Version.CURRENT,
                  parameters = {
                          @KlabFunction.Argument(name = "range", type = Type.RANGE, description = "The min-max range " +
                                  "of the values produced. Default is 0 to 4000", optional = true),
                          @KlabFunction.Argument(name = "detail", type = Type.NUMBER, description = "Controls the " +
                                  "amount of detail in the generated structure. Default is 8, appropriate for " +
                                  "elevation", optional = true),
                          @KlabFunction.Argument(name = "roughness", type = Type.NUMBER, description = "Controls the " +
                                  "roughness of the generated terrain. Default is 0.55, appropriate for elevation",
                                                 optional = true)
                  })
    public static class FractalTerrain implements Resolver<State> {

        @Override
        public State resolve(State observation, ServiceCall call, ContextScope scope) {

            Range range = call.getParameters().get("range", new Range(0., 4000., false, false));
            List<Long> xy = scope.getGeometry().dimension(Dimension.Type.SPACE).getShape();
            var storage = observation.getStorage(DoubleStorage.class);
            Terrain terrain = new Terrain(call.getParameters().get("detail", 8), call.getParameters().get("roughness"
                    , 0.55),
                    range.getLowerBound(), range.getUpperBound());

            /*
             appropriate pattern for generic scale when we handle only one dimension, even if in most situations (all
              at the moment) only one subscale will be returned. Inside the loop, adapt each sub-scale to a grid
             scanner for speed. The geometry requirement ensures that we get a regular 2D spatial extent.
            */
            for (Geometry subscale : scope.getGeometry().without(Dimension.Type.SPACE)) {
                double dx = 1.0 / (double) xy.get(0);
                double dy = 1.0 / (double) xy.get(1);
                // iterate to Offsets after adapting to a Scanner2D for speed
                for (Offset offset : subscale.as(Scanner2D.class)) {
                    storage.set(terrain.getAltitude(dx * offset.offsets()[0], dy * offset.offsets()[1]), offset);
                }
            }
            return observation;
        }

    }

}
