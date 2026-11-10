package org.integratedmodelling.klab.api.services.runtime.extension;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.integratedmodelling.klab.api.knowledge.Resource;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.scope.ContextScope;

/**
 * Engine-neutral learning operation. The runtime supplies already bound observations and owns
 * resource identity, staging, validation and commit. Implementations populate the builder with an
 * inference adapter, its parameters, schema, metadata and staged payload references; they must not
 * publish the resource. Returning normally signals completion, not a committed resource.
 *
 * <p>This SPI defines the invocation contract; component discovery and runtime dispatch must
 * explicitly support it before an implementation can be executed from k.IM.
 */
@FunctionalInterface
public interface LearningContextualizer {

  /** Annotation selecting a dependency as a predictor. Its arguments are engine-specific. */
  String PREDICTOR_ANNOTATION = "predictor";

  /** Unsupervised engines override this explicitly; supervised learning is the default. */
  default boolean requiresTargets() {
    return true;
  }

  /**
   * A resolved input under its agreed schema name. Parameters preserve annotation arguments such
   * as weight without assigning universal engine semantics. The parameter map is a shallow,
   * immutable snapshot; the observation remains a live runtime handle.
   */
  record Input(String name, Observation observation, Map<String, Object> parameters) {
    public Input {
      if (name == null || name.isBlank()) {
        throw new IllegalArgumentException("A learning input requires a schema name");
      }
      Objects.requireNonNull(observation, "observation");
      parameters = Map.copyOf(Objects.requireNonNull(parameters, "parameters"));
    }
  }

  /**
   * Learn once over the supplied context, after any preceding target-producing computations.
   * Predictors and targets are separate ordered lists; targets may be empty for engines that do
   * not require supervision. Implementations must report failure by throwing, and must not retain
   * the builder after returning. Normal observation outputs remain owned by the runtime's
   * contextualization chain. The runtime must discard staged artifacts on failure or cancellation.
   */
  void learn(
      List<Input> predictors,
      List<Input> targets,
      Resource.Builder resource,
      ContextScope scope);
}
