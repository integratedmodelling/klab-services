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
 *
 * This program is distributed in the hope that it will be useful, but without any warranty; without
 * even the implied warranty of merchantability or fitness for a particular purpose. See the Affero
 * General Public License for more details.
 *
 * You should have received a copy of the Affero General Public License along with this program; if
 * not, write to the Free Software Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA
 * 02111-1307, USA. The license is also available at: https://www.gnu.org/licenses/agpl.html
 *******************************************************************************/
package org.integratedmodelling.klab.runtime.computation;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import groovy.lang.Binding;
import groovy.lang.MissingPropertyException;
import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.exceptions.KlabException;
import org.integratedmodelling.klab.api.exceptions.KlabInternalErrorException;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.Expression;
import org.integratedmodelling.klab.api.knowledge.Model;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.observation.scale.Scale;
import org.integratedmodelling.klab.api.lang.ServiceCall;
import org.integratedmodelling.klab.api.lang.kim.KimNamespace;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.runtime.Notification;

public class GroovyExpression /*extends Expression*/ implements Expression {

  protected String code;
  protected boolean negated = false;
  protected Object object;
  protected ServiceCall functionCall;
  protected Model model;
  protected boolean isNull = false;
  protected boolean isTrue = false;
  protected boolean fubar = false;

  private Set<String> defineIfAbsent = new HashSet<>();
  // each thread gets its own instance of the script with bindings
  private ThreadLocal<Boolean> initialized = new ThreadLocal<>();
  private ThreadLocal<ExpressionBase> script = new ThreadLocal<>();

  /*
   * either the Script or the compiled class are saved according to whether we
   * want a thread-safe expression or not.
   */
  private Class<ExpressionBase> sclass = null;

//  Geometry domain;
//  KimNamespace namespace;

  private List<Notification> errors = new ArrayList<>();
  private KlabGroovyShell shell = new KlabGroovyShell();
  private String preprocessed = null;
  private ContextScope runtimeContext;
  private Descriptor descriptor;
  private Map<String, Object> variables;
  private Set<CompilerOption> options = EnumSet.noneOf(CompilerOption.class);

  public boolean hasErrors() {
    return errors.size() > 0;
  }

  public List<Notification> getErrors() {
    return errors;
  }

  GroovyExpression(String code, boolean preprocessed, Expression.Descriptor descriptor) {
    initialized.set(Boolean.FALSE);
    this.descriptor = descriptor;
    this.code = code;
    if (preprocessed) {
      this.preprocessed = this.code;
    }
  }

  public Object eval(Parameters<String> parameters, ContextScope scope) {

    if (fubar) {
      return null;
    }

    if (isTrue) {
      return true;
    }

    if (isNull) {
      return null;
    }

    if (code != null) {

      // initialized.get() == null happens when expressions are used in lookup tables
      // or other code
      // where the creating thread has
      // finished. In this case we recycle them (TODO CHECK if this creates any
      // problems) - IGNORES OPTIONS
      if (initialized.get() == null || !initialized.get()) {
//        initialize(new HashMap<>(), new HashMap<>());
//        setupBindings(scope);
      }

      try {
//        Binding binding = script.get().getBinding();

        if (scope != null) {

          //          if (scope.getScale() != null) {
          //            binding.setVariable("scale", scope.getScale());
          //          }
          //
          //          if (scope.getScale() != null && scope.getScale().getSpace() != null) {
          //            binding.setVariable("space", scope.getScale().getSpace());
          //          }
          //
          //          if (scope.getScale() != null && scope.getScale().getTime() != null) {
          //            binding.setVariable("time", scope.getScale().getTime());
          //          }
          //
          //          if (scope.getContextObservation() != null) {
          //            binding.setVariable("context", scope.getContextObservation());
          //          }
          //
          //          if (scope.getTargetSemantics() != null) {
          //            binding.setVariable("semantics", scope.getTargetSemantics());
          //          }

          /*
           * this will override the vars if necessary. For example it will change the
           * scale in local contexts.
           */
          for (String key : parameters.keySet()) {
            Object value = parameters.get(key);
            if ("scale".equals(key) && value instanceof Scale localScale) {
//              binding.setVariable("space", localScale.getSpace());
//              binding.setVariable("time", localScale.getTime());

            } else if ("self".equals(key)
                && value instanceof Observation observation
                && observation.getObservable().is(SemanticType.COUNTABLE)) {

              /*
               * if self is an object, check if we have a pattern or network
               */
              //              Pattern pattern = observation.getOriginatingPattern();
              //              if (pattern instanceof Network) {
              //                binding.setVariable("network", pattern);
              //              } else if (pattern != null) {
              //                binding.setVariable("pattern", pattern);
              //              }

              //              binding.setVariable("observer", observation.getObserver());
              //            }
//              binding.setVariable(key, value);
            }

            //          if (descriptor.getOptions().contains(CompilerOption.DirectQualityAccess)
            //              && localScale != null) {
            //            for (String id : descriptor.getIdentifiersInScalarScope()) {
            //              Object state = parameters.get(id);
            //              if (state instanceof IState) {
            //                binding.setVariable("_" + id, ((IState) state).get(localScale));
            //              }
            //            }
          }
          /*
           * use the current scope and monitor
           */
//          binding.setVariable("_c", scope);
          //          binding.setVariable("_monitor", scope.getMonitor());
        }

        for (String v : defineIfAbsent) {
          //          if (!binding.hasVariable(v)) {
          //            binding.setVariable(v, Double.NaN);
          //          }
        }
        return true; // TODO must put this away, not run it - script.get().run(geometry);

      } catch (MissingPropertyException e) {
        String property = e.getProperty();
        if (!defineIfAbsent.contains(property)) {
          scope.warn(
              "variable "
                  + property
                  + " undefined.  Defining as numeric no-data (NaN) for subsequent evaluations.");
          defineIfAbsent.add(property);
        }
      } catch (Throwable t) {
        throw new KlabException(t) {};
      }
    } else if (object != null) {
      return object;
    } else if (functionCall != null) {
      //      return Extensions.INSTANCE.callFunction(functionCall, scope);
    }
    return null;
  }

//  /**
//   * This only gets done once per thread. Uses a new compiled class per thread and sets up the
//   * bindings with any invariant objects. The remaining variables are set before each call.
//   *
//   * @param scope
//   */
//  private void setupBindings(ContextScope scope) {
//
//    Binding bindings = new Binding();
//
//    /*
//     * inherent variables have known values at the time of compilation.
//     */
//    if (variables != null) {
//      for (String key : variables.keySet()) {
//        Object value = variables.get(key);
//        bindings.setVariable(key, value);
//      }
//    }
//
//    if (scope != null) {
//      bindings.setVariable("provenance", scope.getProvenance());
//      //      bindings.setVariable("structure", ((IRuntimeScope) scope).getStructure());
//      //      bindings.setVariable("_ns", scope.getNamespace());
//      //      bindings.setVariable("_monitor", scope.getMonitor());
//      //      if (scope.getSession().getState().getInspector() != null) {
//      //        bindings.setVariable("inspector", scope.getSession().getState().getInspector());
//      //      }
//    }
//
//    try {
//      script.set(shell.createFromClass(sclass, bindings));
//    } catch (Exception e) {
//      throw new KlabInternalErrorException(e);
//    }
//  }

  public String toString() {
    return code;
  }

  @Override
  public Object eval(
      org.integratedmodelling.klab.api.scope.Scope scope, Object... additionalParameters) {
    return null;
  }
}
