package org.integratedmodelling.klab.services.reasoner.internal;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import org.integratedmodelling.common.data.jackson.JacksonConfiguration;
import org.integratedmodelling.klab.api.knowledge.Worldview;

/** Immutable submission handle. Every exposed bean or collection belongs to the caller. */
final class FrozenWorldviewSnapshot implements InvocationHandler {
  private final String json;
  private FrozenWorldviewSnapshot(Worldview source) {
    try { json = JacksonConfiguration.newObjectMapper().writeValueAsString(source); }
    catch (Exception e) { throw new IllegalArgumentException("Cannot freeze semantic snapshot", e); }
  }
  static Worldview freeze(Worldview source) {
    if (Proxy.isProxyClass(source.getClass()) && Proxy.getInvocationHandler(source) instanceof FrozenWorldviewSnapshot)
      return source;
    return (Worldview)Proxy.newProxyInstance(Worldview.class.getClassLoader(),
        new Class<?>[]{Worldview.class}, new FrozenWorldviewSnapshot(source));
  }
  static Worldview copyOf(Worldview source) {
    if (Proxy.isProxyClass(source.getClass()) && Proxy.getInvocationHandler(source) instanceof FrozenWorldviewSnapshot frozen)
      return frozen.copy();
    return JacksonConfiguration.newObjectMapper().convertValue(source, Worldview.class);
  }
  private Worldview copy() {
    try { return JacksonConfiguration.newObjectMapper().readValue(json, Worldview.class); }
    catch (Exception e) { throw new IllegalStateException("Cannot read frozen semantic snapshot", e); }
  }
  @Override public Object invoke(Object proxy, Method method, Object[] arguments) throws Throwable {
    if (method.getDeclaringClass() == Object.class) return switch (method.getName()) {
      case "hashCode" -> System.identityHashCode(proxy);
      case "equals" -> proxy == arguments[0];
      case "toString" -> "Frozen worldview snapshot";
      default -> throw new UnsupportedOperationException();
    };
    if (method.getParameterCount() != 0 || method.getReturnType() == void.class)
      throw new UnsupportedOperationException("Submitted semantic snapshots are immutable");
    // Decode a private copy for the requested read: callers never obtain the queued object graph.
    var copy = copy();
    try { return method.invoke(copy); }
    catch (InvocationTargetException e) { throw e.getCause(); }
  }
}
