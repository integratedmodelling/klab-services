package org.integratedmodelling.klab.api.services.resolver;

import javax.xml.stream.events.Namespace;

import org.integratedmodelling.klab.api.knowledge.Model;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.Resource;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.scope.Scope;

/**
 * One or more resolution constraints may be added to a
 * {@link org.integratedmodelling.klab.api.scope.ContextScope},  through the API, k.Actors or any other
 * observer that require customizing the accessibility of specific models or resources. The purpose of a
 * resolution constraint is to (de)prioritize a candidate model when resolving an observation, based on
 * several possible conditions, expressed as the constraint's {@link Type}. The associated data are visible
 * only to enable serialization but are handled internally.
 * <p>
 * Resolution constraints include:
 * <ol>
 * <li>black/whitelisting models, projects, namespaces, resources or resource services, either relative to
 * a specific observable or set thereof, or globally;</li>
 * <li>setting specified namespaces and/or projects as prioritary in resolution to alter the scope of
 * resolution based on the lexical scope of a model being resolved</li>
 * <li>defining priority scenarios to use first as resolution sources;</li>
 * <li>forcing the use of a specified model for a specified observable;</li>
 * <li>communicating observables that have been already resolved within the scope, so that references will
 * be compiled in instead of resolving them;</li>
 * <li>communicating externally set concrete predicates to expand an abstract predicate in an observable
 * instead of observing the abstract predicate first. </li>
 * </ol><p>
 * Any resolution constraints are found in a {@link org.integratedmodelling.klab.api.scope.ContextScope},
 * returned by its {@link ContextScope#getResolutionConstraints()} method.
 *
 * @author Ferd
 */
public interface ResolutionConstraint {

    @Deprecated
    public static String RESOLUTION_CONSTRAINTS_METADATA_KEY = "klab.constraints.resolution";

    enum Type {
        Scenarios,
        ResolveWith,
        Resolved,
        ResolutionScope,
        ConcretePredicates,
        Whitelist,
        Blacklist
    }

    Type getType();

    /**
     * Return true if the model is for a different observable or it's for the passed observable but part of a
     * whitelist, is not blacklisted, comes from an accepted namespace, and uses accepted resources.
     * <p>
     * TODO revise
     *
     * @param model
     * @return
     */
    boolean accepts(Model model);

}
