package org.integratedmodelling.klab.api.services.resolver;

import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.Concept;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.resolver.objects.ResolutionConstraintImpl;

import java.io.Serializable;
import java.util.List;

/**
 * One or more resolution constraints may be added to a
 * {@link org.integratedmodelling.klab.api.scope.ContextScope},  through the API, k.Actors or any other
 * observer that require customizing the resolution environment, such as modifying the accessibility of
 * specific models or resources. The purpose of a resolution constraint is to (de)prioritize a candidate model
 * when resolving an observation, based on several possible conditions, expressed as the constraint's
 * {@link Type}. The associated data are visible only to enable serialization but are handled internally.
 * <p>
 * Resolution constraints include:
 * <ol>
 * <li>black/whitelisting models, projects, namespaces, resources or resource services, either relative to
 * a specific observable or set thereof, or globally;</li>
 * <li>setting specified namespaces and/or projects as prioritary in resolution to alter the scope of
 * resolution based on the lexical scope of a model being resolved</li>
 * <li>defining priority scenarios to use first as resolution sources;</li>
 * <li>forcing the use of a specified model for a specified observable;</li>
 * <li>communicating externally set concrete predicates to expand an abstract predicate in an observable
 * instead of observing the abstract predicate first. </li>
 * <li>communicating interactively defined parameters to substitute defaults for the contextualizers in the
 * dataflow. </li>
 * </ol><p>
 * Any resolution constraints are found in a {@link org.integratedmodelling.klab.api.scope.ContextScope},
 * returned by its {@link ContextScope#getResolutionConstraints()} method and accessed through the family
 * of <code>getConstraint[s]</code> methods.
 *
 * Note: constraints with any null-valued payload will be ignored. TODO check if these should be a signal to
 * remove existing constraints of the same class.
 *
 * @author Ferd
 */
public interface ResolutionConstraint extends Serializable {

    /**
     * Defines type, behavior and intended payload class for the constraint. Each class MUST be serializable
     * to JSON without incident.
     */
    enum Type {

        Scenarios(String.class, false /* debatable */),
        Geometry(Geometry.class, false),
        ResolutionNamespace(String.class, false),
        ResolutionProject(String.class, false),
        UsingModel(String.class, false),
        ConcretePredicates(Concept.class, true),
        Whitelist(String.class, false),
        Blacklist(String.class, false),
        Parameters(Parameters.class, true);

        /**
         * Class of intended data types, used for runtime validation
         */
        public final Class<?> dataClass;

        /**
         * If true, the constraint data will be added to any other pre-existing in the scope. If false, any
         * new data will override the previous.
         */
        public final boolean incremental;

        private Type(Class<?> dataClass, boolean incremental) {
            this.dataClass = dataClass;
            this.incremental = incremental;
        }
    }

    /**
     * Number of items of the intended type in the internal data. Shorthand for <code>get(..).size()</code>,
     * provided for fluency.
     *
     * @return
     */
    int size();

    /**
     * True if there is no payload or the payload contains null data. Checked before use, so that adding
     * nulls in algorithms can be handled properly.
     *
     * @return
     */
    boolean empty();

    Type getType();

    /**
     * The
     *
     * @param dataClass
     * @param <T>
     * @return
     */
    <T> List<T> payload(Class<T> dataClass);

    /**
     * Used internally to merge a constraint with a previous one and return a new constraint.
     *
     * @param constraint
     * @return
     */
    ResolutionConstraint merge(ResolutionConstraint constraint);

    static ResolutionConstraint of(Type type, Object data) {
        return new ResolutionConstraintImpl(type, data);
    }

}
