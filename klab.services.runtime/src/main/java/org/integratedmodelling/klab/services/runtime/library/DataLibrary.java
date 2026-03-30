package org.integratedmodelling.klab.services.runtime.library;

import org.integratedmodelling.klab.api.data.Storage;
import org.integratedmodelling.klab.api.knowledge.Artifact.Type;
import org.integratedmodelling.klab.api.knowledge.observation.scale.Scale;
import org.integratedmodelling.klab.api.lang.ServiceCall;
import org.integratedmodelling.klab.api.services.runtime.extension.KlabFunction;
import org.integratedmodelling.klab.api.services.runtime.extension.Library;

@Library(
    name = "klab.data",
    description =
        """
        General-purpose data manipulation functions.
        """)
public class DataLibrary {

  @KlabFunction(
      name = "normalize",
      description =
          """
              Normalize values to a specified range.
          """,
          geometry = "*", // filter for grids and extract the S2 geometry from the scale before calling
          type = Type.NUMBER,
          split = 1,
          parameters = {
        @KlabFunction.Argument(
            name = "range",
            type = Type.RANGE,
            description = "The min-max range of the values produced. Default is 0 to 1",
            optional = true)
      })
  public void normalize(Storage.DoubleScanner filler, Scale scale, ServiceCall call) {

//    var range = call.getParameters().get("range", NumericRange.create(0., 4000., false, false));
//    var xy = scale.getSpace().getShape();
//    var xx = xy.get(0);
//    var yy = xy.get(1);
//    var terrain =
//        new Terrain(
//            call.getParameters().get("detail", 8),
//            call.getParameters().get("roughness", 0.55),
//            range.getLowerBound(),
//            range.getUpperBound());
//
//    double dx = 1.0 / (double) xx;
//    double dy = 1.0 / (double) yy;
//
//    for (int x = 0; x < xx; x++) {
//      for (int y = 0; y < yy; y++) {
//        filler.add(terrain.getAltitude(x * dx, y * dy));
//      }
//    }
  }
}
