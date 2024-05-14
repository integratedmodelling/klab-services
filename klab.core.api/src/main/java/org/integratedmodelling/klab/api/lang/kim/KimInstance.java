package org.integratedmodelling.klab.api.lang.kim;

import org.integratedmodelling.klab.api.knowledge.Resolvable;
import org.integratedmodelling.klab.api.services.runtime.extension.Instance;

import java.util.List;

/**
 * A KimInstance is the k.IM recipe to build an observation. It must specify semantics and scale completely so
 * that a fully consistent observation can be built - if the observation is intended as the root observation,
 * only a Subject semantics is possible.
 */
public interface KimInstance extends KlabStatement, Resolvable {

    /**
     * Optional URN to retrieve the observer from.
     *
     * @return
     */
    String getUrn();

    /**
     * Mandatory name for the resulting observation.
     *
     * @return the name
     */
    String getName();

    /**
     * The type of the stated observation.
     *
     * @return the observable
     */
    KimObservable getObservable();

    /**
     * Any states declared for the object. These observables are only legal if they are pre-resolved to
     * values.
     *
     * @return the states
     */
    List<KimObservable> getStates();

    /**
     * Docstring if any.
     *
     * @return the docstring
     */
    String getDocstring();

    List<KimInstance> getChildren();

}
