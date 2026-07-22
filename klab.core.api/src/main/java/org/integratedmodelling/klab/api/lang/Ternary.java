package org.integratedmodelling.klab.api.lang;

import java.io.Serializable;

/**
 * A conditional value composed of a condition and the values selected when it evaluates to true
 * or false. Contained objects retain their language-specific representation; for k.Actors they
 * are {@code KActorsValue} instances.
 */
public interface Ternary extends Serializable {

  /** Value evaluated as the condition. */
  Object getCondition();

  /** Value selected when the condition is true. */
  Object getTrueCase();

  /** Value selected when the condition is false. */
  Object getFalseCase();
}
