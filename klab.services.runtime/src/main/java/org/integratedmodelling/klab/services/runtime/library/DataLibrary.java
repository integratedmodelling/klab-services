package org.integratedmodelling.klab.services.runtime.library;

import org.integratedmodelling.klab.api.data.Storage;
import org.integratedmodelling.klab.api.knowledge.Artifact.Type;
import org.integratedmodelling.klab.api.knowledge.observation.scale.Scale;
import org.integratedmodelling.klab.api.lang.ServiceCall;
import org.integratedmodelling.klab.api.services.runtime.extension.KlabFunction;
import org.integratedmodelling.klab.api.services.runtime.extension.Library;

import java.util.function.LongConsumer;

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
      geometry = "*",
      type = Type.NUMBER,
      parameters = {
        @KlabFunction.Argument(
            name = "range",
            type = Type.RANGE,
            description = "The min-max range of the values produced. Default is 0 to 1",
            optional = true)
      })
  public static void normalize(
      @KlabFunction.Input Storage.DoubleScanner filler,
      @KlabFunction.Output Storage.DoubleScanner output,
      ServiceCall call) {

    var histogram = filler.shard().getHistogram();
    filler.forEachRemaining(
        (LongConsumer)
            n ->
                output.add(
                    (filler.get() - histogram.getMin())
                        / (histogram.getMax() - histogram.getMin())));
  }
}
