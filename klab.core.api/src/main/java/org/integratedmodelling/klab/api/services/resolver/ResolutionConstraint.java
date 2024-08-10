package org.integratedmodelling.klab.api.services.resolver;

import javax.xml.stream.events.Namespace;

import org.integratedmodelling.klab.api.knowledge.Model;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.Resource;
import org.integratedmodelling.klab.api.scope.Scope;

/**
 * One or more resolution constraints may be added to a session through k.Actors to implement tests or other
 * scripts that require customizing the accessibility of specific models or resources.
 * <p>
 * The default behavior when black/whitelisting a model is to only set the constraint to resolve its own
 * observable. All other constraints are global unless otherwise specified.
 * <p>
 * Any resolution constraints are found as a collection in a {@link Scope}'s metadata using the key
 * {@link #RESOLUTION_CONSTRAINTS_METADATA_KEY}.
 * <p>
 * TODO expand and add to ContextScope as a set of transmissible constraints. Make types and static idiomatic
 *  constructors. Remove all explilcit constraint functions from
 *  {@link org.integratedmodelling.klab.api.scope.ContextScope} and add a withConstraints(....) function.
 *
 * @author Ferd
 */
public interface ResolutionConstraint {

    public static String RESOLUTION_CONSTRAINTS_METADATA_KEY = "klab.constraints.resolution";

    /**
     * Return true if the model is for a different observable or it's for the passed observable but part of a
     * whitelist, is not blacklisted, comes from an accepted namespace, and uses accepted resources.
     *
     * @param model
     * @return
     */
    boolean accepts(Model model, Observable observable);

    /**
     * Check for blacklisted or whitelisted resources. Also used by {@link #accepts(IModel, IObservable)} when
     * the model passed to it contains resources.
     *
     * @param model
     * @return
     */
    boolean accepts(Resource model);

    /**
     * @param namespace
     * @return
     */
    boolean accepts(Namespace namespace);

}
