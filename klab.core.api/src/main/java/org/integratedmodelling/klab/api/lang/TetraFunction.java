package org.integratedmodelling.klab.api.lang;

import java.util.Objects;
import java.util.function.Function;

@FunctionalInterface
public interface TetraFunction<T, U, V, Z, R> {

  R apply(T t, U u, V v, Z z);

  default <K> TetraFunction<T, U, V, Z, K> andThen(Function<? super R, ? extends K> after) {
    Objects.requireNonNull(after);
    return (T t, U u, V v, Z z) -> after.apply(apply(t, u, v, z));
  }
}
