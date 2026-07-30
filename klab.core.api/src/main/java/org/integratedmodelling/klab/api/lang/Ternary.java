package org.integratedmodelling.klab.api.lang;

import java.io.Serializable;
import org.integratedmodelling.klab.api.lang.kactors.KActorsStatement;
import org.integratedmodelling.klab.api.lang.kactors.KActorsValue;

/**
 * A conditional value composed of a condition and the values selected when it evaluates to true
 * or false. Contained objects retain their language-specific representation. In k.Actors the
 * condition is a {@code KActorsValue}; each branch is either a {@code KActorsValue}, a functional
 * {@code KActorsStatement.Verb}, or a functional {@code KActorsStatement.Switch}. Implementations
 * remain ordinary JavaBeans so ternaries survive JSON transport without parser objects.
 */
public interface Ternary extends Serializable {

  /** Value evaluated as the condition. */
  Object getCondition();

  /** Value selected when the condition is true. */
  Object getTrueCase();

  /** Value selected when the condition is false. */
  Object getFalseCase();
}
