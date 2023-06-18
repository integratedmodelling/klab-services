/*
 * This file is part of k.LAB.
 * 
 * k.LAB is free software: you can redistribute it and/or modify it under the terms of the Affero
 * GNU General Public License as published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * A copy of the GNU Affero General Public License is distributed in the root directory of the k.LAB
 * distribution (LICENSE.txt). If this cannot be found see <http://www.gnu.org/licenses/>.
 * 
 * Copyright (C) 2007-2018 integratedmodelling.org and any authors mentioned in author tags. All
 * rights reserved.
 */
package org.integratedmodelling.klab.api.lang;

import java.io.Serializable;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

import org.integratedmodelling.klab.api.authentication.scope.ContextScope;
import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.knowledge.observation.scale.Scale;
import org.integratedmodelling.klab.api.services.runtime.Channel;

/**
 * Simple execution interface for expressions. A new expression is generated per each call to the
 * corresponding language statement, so each object can store local data about its call context.
 *
 * @author ferdinando.villa
 * @version $Id: $Id
 */
public interface Expression extends Serializable {

    public enum Forcing {
        AsNeeded, Always
    }

    public enum CompilerScope {
        /**
         * Execution is triggered by state-by-state logic, so specific subdivision of the context is
         * located at execution, and observation IDs point to state values insteaf of the entire
         * observation when the observation is a quality. Called very many times and concurrently.
         * Irrelevant unless Contextual is also passed.
         */
        Scalar,
        /**
         * Execution is observation-wise, so if states of qualities are needed, the correspondent
         * observations must be iterated within the expression. Irrelevant unless Contextual is also
         * passed.
         */
        Observation,
        /**
         * The expression will be executed in the scope of an observation, so all the auxiliary
         * variables are defined and the observations will be located to the space/time of
         * contextualization. If not passed, expression is normal Groovy code with the k.LAB
         * extensions for reasoning etc.
         */
        Contextual
    }

    // TODO still have to support these instead of passing flags to the compiler
    public enum CompilerOption {

        /**
         * Don't try to process code related to observations and just consider each variable with
         * its own name.
         */
        IgnoreContext,

        /**
         * Translate identifiers like id@ctx into id["ctx"] instead of inserting the
         * recontextualization hooks for states.
         */
        RecontextualizeAsMap,

        /**
         * Ignore the recontextualizations done with @. Passed when expressions are compiled as part
         * of documentation templates, which use @ for internal purposes.
         */
        IgnoreRecontextualization,

        /**
         * Skip k.LAB preprocessing altogether.
         */
        DoNotPreprocess,

        /**
         * Refer to quality values coming from states directly instead of compiling in a
         * state.get(scale) instruction. Values must be inserted in parameters at eval(). Use when
         * speed is critical - Groovy takes a long time dispatching the messages.
         */
        DirectQualityAccess
    }

    /**
     * The context to compile an expression. If passed, it is used to establish the role of the
     * identifiers, which may affect preprocessing.
     * 
     * @author ferdinando.villa
     *
     */
    public interface Scope {

        /**
         * The expected return type, if known.
         * 
         * @return
         */
        SemanticType getReturnType();

        // /**
        // * Namespace of evaluation, if any.
        // *
        // * @return
        // */
        // INamespace getNamespace();

        /**
         * All known identifiers at the time of evaluation.
         * 
         * @return
         */
        Collection<String> getIdentifiers();

        /**
         * Add a scalar identifier that we want recognized at compilation. TODO this should use
         * IArtifact.Type
         */
        void addKnownIdentifier(String id, SemanticType type);

        /**
         * All known identifiers of quality observations at the time of evaluation.
         * 
         * @return
         */
        Collection<String> getStateIdentifiers();

        /**
         * The type of the passed identifier.
         * 
         * @param identifier
         * @return
         */
        SemanticType getIdentifierType(String identifier);

        /**
         * The scale of evaluation, or null.
         * 
         * @return
         */
        Scale getScale();

        /**
         * A monitoring channel for notifications.
         * 
         * @return
         */
        Channel getMonitor();

        /**
         * The type of compilation we desire. This should automatically be set to Contextual if a
         * contextualization scope is passed.
         * 
         * @return
         */
        CompilerScope getCompilerScope();

        /**
         * Return a scope that will cause the execution of the expression to be scalar, i.e.
         * state-by-state within the context. The forcing passed defines the type of constraint: if
         * {@link Forcing#AsNeeded}, the expression will be scalar only if it mentions quality
         * variables in a scalar scope; if {@link Forcing#Always}, scalar behavior will be forced no
         * matter the statement.
         * 
         * @param forceScalar
         * @return
         */
        Scope scalar(Forcing forcing);

        /**
         * If the expression scope was created during contextualization, return the scope here.
         * 
         * @return
         */
        ContextScope getRuntimeScope();

        /**
         * If true, we have requested the expression to be evaluated in a scalar fashion no matter
         * what it says.
         * 
         * @return
         */
        boolean isForcedScalar();

    }

    /**
     * The language service can compile a string expression into a descriptor, which can in turn be
     * compiled into the executable expression. The descriptor contains a list of identifiers and
     * ideally should be able to determine in which context (scalar value or not) they are used
     * within the expression.
     * 
     * @author Ferd
     *
     */
    interface Descriptor {

        /**
         * Return all identifiers detected.
         * 
         * @return set of identifiers
         */
        Collection<String> getIdentifiers();

        /**
         * Return all contextualizers encountered (in expressions such as "elevation@nw")
         * 
         * @return set of contextualizers
         */
        Collection<String> getContextualizers();

        /**
         * True if the expression contains scalar usage for one or more of the identifiers used in a
         * scalar fashion. This may be false even if the expression was compiled in scalar scope.
         * 
         * @return
         */
        boolean isScalar();

        /**
         * Return true if the expression contains scalar usage for the passed identifiers within a
         * transition (i.e. used alone or with locator semantics for space or other non-temporal
         * domain).
         * 
         * @param identifier identifiers representing states
         * 
         * @return true if the identifier is used in a scalar context.
         */
        boolean isScalar(String identifier);

        /**
         * Return true if the expression contains non-scalar usage for the passed identifiers within
         * a transition (i.e. used as an object, with methods called on it).
         * 
         * @param identifier identifiers representing states
         * 
         * @return true if the identifier is used in a scalar context.
         */
        boolean isNonscalar(String identifier);

        /**
         * Return true if the expression contains scalar usage for any of the passed identifiers
         * within a transition (i.e. used alone or with locator semantics for space or other
         * non-temporal domain).
         * 
         * @param stateIdentifiers identifiers representing states
         * 
         * @return true if any of the identifiers is used in a scalar context.
         */
        boolean isScalar(Collection<String> stateIdentifiers);

        /**
         * Return true if the expression contains non-scalar usage for any of the passed identifiers
         * within a transition (i.e. used as an object, with methods called on it).
         * 
         * @param stateIdentifiers identifiers representing states
         * 
         * @return true if any of the identifiers is used in a scalar context.
         */
        boolean isNonscalar(Collection<String> stateIdentifiers);

        /**
         * In order to avoid duplicated action, the descriptor alone must be enough to compile the
         * expression. If we have a valid descriptor the returned expression must be valid so no
         * exceptions are thrown unless the descriptor has errors, which causes an
         * IllegalArgumentException.
         * 
         * @return a compiled expression ready for execution in the context that produced the
         *         descriptor
         * @throws IllegalArgumentException if the descriptor has errors
         */
        Expression compile();

        /**
         * 
         * @return
         */
        Collection<String> getIdentifiersInScalarScope();

        /**
         * 
         * @return
         */
        Collection<String> getIdentifiersInNonscalarScope();

        /**
         * If the expression was compiled with the {@link CompilerOption#RecontextualizeAsMap}
         * option, any identifier seen as id@ctx will have been turned into id["ctx"] and the id
         * plus all the keys will be available here.
         * 
         * @return
         */
        Map<String, Set<String>> getMapIdentifiers();

        /**
         * Return the set of options that were passed when this expression was compiled. May be
         * empty, never null.
         * 
         * @return
         */
        Collection<CompilerOption> getOptions();

        /**
         * Predefined variables that have been inserted in the code and whose value is known at the
         * time of compilation. Typically translations of k.IM identifiers and URNs into the
         * correspondent objects.
         * 
         * @return
         */
        Parameters<String> getVariables();
    }

    /**
     * Execute the expression
     *
     * @param parameters from context or defined in a language call
     * @param scope possibly empty, may be added to determine the result of the evaluation according
     *        to the calling context. The {@link IContextualizationScope#getMonitor() monitor in the
     *        context} will never be null and can be used to send messages or interrupt the
     *        computation.
     * @param additionalParameters add key, value pairs for any additional parameter to add
     * @return the result of evaluating the expression
     * @throws org.integratedmodelling.klab.KException.KlabException TODO
     */
    Object eval(org.integratedmodelling.klab.api.authentication.scope.Scope scope, Object... additionalParameters);

}
