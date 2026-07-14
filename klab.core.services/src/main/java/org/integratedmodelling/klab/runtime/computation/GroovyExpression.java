/*******************************************************************************
 * Copyright (C) 2007, 2015:
 *
 * - Ferdinando Villa <ferdinando.villa@bc3research.org> - integratedmodelling.org - any other
 * authors listed in @author annotations
 *
 * All rights reserved. This file is part of the k.LAB software suite, meant to enable modular,
 * collaborative, integrated development of interoperable data and model components. For details,
 * see http://integratedmodelling.org.
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * Affero General Public License Version 3 or any later version.
 *******************************************************************************/
package org.integratedmodelling.klab.runtime.computation;

import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import groovy.lang.Script;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serial;
import java.util.LinkedHashMap;
import java.util.Map;
import org.codehaus.groovy.runtime.InvokerHelper;
import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.exceptions.KlabException;
import org.integratedmodelling.klab.api.exceptions.KlabIllegalArgumentException;
import org.integratedmodelling.klab.api.knowledge.Expression;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.services.Reasoner;

/** Executable, thread-safe Groovy expression produced by {@link GroovyProcessor}. */
public class GroovyExpression implements Expression {

  @Serial private static final long serialVersionUID = -8613823176372469282L;

  private final String code;
  private final Map<String, GroovyProcessor.GroovyDescriptor.SemanticLiteral> semanticLiterals;

  /* Groovy classes and their class loaders are process-local and are rebuilt after deserialization. */
  private transient volatile Class<? extends Script> scriptClass;
  private transient Object compilationMonitor = new Object();

  GroovyExpression(String code, Expression.Descriptor descriptor) {
    if (code == null) {
      throw new IllegalArgumentException("Cannot compile an expression with null code");
    }
    this.code = code;
    this.semanticLiterals =
        descriptor instanceof GroovyProcessor.GroovyDescriptor groovyDescriptor
            ? new LinkedHashMap<>(groovyDescriptor.getSemanticLiterals())
            : Map.of();
    compileScript();
  }

  /** Convenience overload retained for callers that already have a parameter object. */
  public Object eval(Parameters<String> parameters, ContextScope scope) {
    return eval(scope, parameters == null ? Map.of() : parameters);
  }

  @Override
  public Object eval(Scope scope, Object... additionalParameters) {
    Map<String, Object> variables = defaultBindings(scope);
    addParameters(variables, additionalParameters);
    resolveSemanticLiterals(variables, scope);

    try {
      Script script = InvokerHelper.createScript(compileScript(), new Binding(variables));
      return script.run();
    } catch (Exception t) {
      throw new KlabException("Error evaluating Groovy expression: " + code, t) {};
    }
  }

  private Map<String, Object> defaultBindings(Scope scope) {
    Map<String, Object> ret = new LinkedHashMap<>();
    if (scope != null) {
      ret.put("scope", scope);
      if (scope instanceof ContextScope contextScope) {
        ret.put("context", contextScope.getContextObservation());
        ret.put("observer", contextScope.getObserver());
        ret.put("source", contextScope.getSourceObservation());
        ret.put("target", contextScope.getTargetObservation());
      }
    }
    return ret;
  }

  private void addParameters(Map<String, Object> variables, Object... parameters) {
    if (parameters == null) {
      return;
    }
    for (int i = 0; i < parameters.length; i++) {
      Object parameter = parameters[i];
      if (parameter instanceof Map<?, ?> map) {
        for (var entry : map.entrySet()) {
          if (!(entry.getKey() instanceof String key)) {
            throw new KlabIllegalArgumentException(
                "Expression parameter maps must have String keys");
          }
          variables.put(key, entry.getValue());
        }
      } else if (parameter instanceof String key) {
        if (++i >= parameters.length) {
          throw new KlabIllegalArgumentException(
              "Expression parameters must be maps or String/value pairs");
        }
        variables.put(key, parameters[i]);
      } else {
        throw new KlabIllegalArgumentException(
            "Expression parameters must be maps or String/value pairs");
      }
    }
  }

  private void resolveSemanticLiterals(Map<String, Object> variables, Scope scope) {
    if (semanticLiterals.isEmpty()) {
      return;
    }
    if (scope == null) {
      throw new KlabIllegalArgumentException(
          "A scope with a Reasoner service is required to evaluate semantic literals");
    }
    Reasoner reasoner = scope.getService(Reasoner.class);
    variables.put("reasoner", reasoner);
    for (var entry : semanticLiterals.entrySet()) {
      Object value =
          switch (entry.getValue().type()) {
            case CONCEPT -> reasoner.resolveConcept(entry.getValue().definition());
            case OBSERVABLE -> reasoner.resolveObservable(entry.getValue().definition());
          };
      variables.put(entry.getKey(), value);
    }
  }

  @SuppressWarnings("unchecked")
  private Class<? extends Script> compileScript() {
    var compiled = scriptClass;
    if (compiled == null) {
      synchronized (compilationMonitor) {
        compiled = scriptClass;
        if (compiled == null) {
          try {
            Class<?> parsed =
                new GroovyShell(GroovyExpression.class.getClassLoader())
                    .getClassLoader()
                    .parseClass(code);
            if (!Script.class.isAssignableFrom(parsed)) {
              throw new IllegalArgumentException("Groovy source did not compile to a script");
            }
            compiled = (Class<? extends Script>) parsed;
            scriptClass = compiled;
          } catch (RuntimeException e) {
            throw new IllegalArgumentException("Invalid Groovy expression: " + code, e);
          }
        }
      }
    }
    return compiled;
  }

  @Serial
  private void readObject(ObjectInputStream stream) throws IOException, ClassNotFoundException {
    stream.defaultReadObject();
    compilationMonitor = new Object();
  }

  @Override
  public String toString() {
    return code;
  }
}
