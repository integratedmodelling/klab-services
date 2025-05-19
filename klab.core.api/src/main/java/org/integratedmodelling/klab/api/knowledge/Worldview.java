package org.integratedmodelling.klab.api.knowledge;

import org.integratedmodelling.klab.api.lang.kim.KimObservationStrategyDocument;
import org.integratedmodelling.klab.api.lang.kim.KimOntology;

import java.util.Collection;
import java.util.List;

/**
 * A worldview is a container that can be produced by any resources service that advertises a worldview. It
 * contains a sorted set of validated ontologies plus all the observation strategies needed to make use of it.
 * The resource service will take care of resolving external namespaces when those are not available locally.
 * <p>
 * The non-empty worldview is always self-consistent and contains at least a full Tier 1 worldview. Any
 * additional contents depend on the asking user, whose groups may add tier-x (x>1) ontologies and additional
 * observation strategies provided by authorized components or projects.
 * <p>
 * The version of the worldview is always unique as all the projects that compose it must have the same
 * version. The modeler and any other k.LAB application must always be able to produce a read-only view of the
 * loaded worldview.
 */
public interface Worldview extends KlabAsset {

    /**
     * Internal "workspace" identifier used for ResourceSet when communicating a change that affects the
     * worldview.
     */
    public static final String WORLDVIEW_WORKSPACE_IDENTIFIER = "__WORLDVIEW__";

    /**
     * Properties for the worldview project that specify default semantics for spatial contexts and
     * observers to support the explorer.
     */
    public static final String EXPLORER_OBSERVER_SEMANTICS = "klab.explorer.semantics.observer";
    public static final String EXPLORER_LOCATION1D_SEMANTICS = "klab.explorer.semantics.location1d";
    public static final String EXPLORER_LOCATION2D_SEMANTICS = "klab.explorer.semantics.location2d";
    public static final String EXPLORER_LOCATION3D_SEMANTICS = "klab.explorer.semantics.location3d";

    /**
     * A worldview must have a unique ID that reflects its full content. The ID is used to compare worldviews
     * loaded in a service to assess if they can be updated by changes happening in other services. Once
     * assigned upon worldview creation, the ID shouldn't be changed even if updates are merged.
     *
     * @return
     */
    String getWorldviewId();

    /**
     * The ontologies are in the right order for loading and the first in the list is always the root
     * ontology, which will reference any upper ontologies it uses.
     *
     * @return
     */
    List<KimOntology> getOntologies();

    /**
     * Observation strategies cover the full extent of the k.LAB permitted observations and they will only
     * reference concepts that are resolved through the worldview. They must be loaded after all the
     * ontologies are loaded.
     *
     * @return
     */
    Collection<KimObservationStrategyDocument> getObservationStrategies();

    /**
     * An empty worldview results from errors or inconsistencies and, like other k.LAB containers, may not be
     * physically "empty" so this method should always be checked before use.
     *
     * @return
     */
    boolean isEmpty();
}
