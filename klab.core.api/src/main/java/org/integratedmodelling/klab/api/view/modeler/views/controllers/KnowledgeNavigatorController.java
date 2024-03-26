package org.integratedmodelling.klab.api.view.modeler.views.controllers;

import org.integratedmodelling.klab.api.knowledge.Semantics;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.integratedmodelling.klab.api.view.ViewController;
import org.integratedmodelling.klab.api.view.UIReactor;
import org.integratedmodelling.klab.api.view.annotations.UIView;
import org.integratedmodelling.klab.api.view.modeler.views.KnowledgeNavigator;


/**
 * The Knowledge Navigator interfaces with the currently selected
 * {@link org.integratedmodelling.klab.api.services.Reasoner} to support all allowed interactions with it. It
 * supports incremental building of a {@link org.integratedmodelling.klab.api.knowledge.Concept} or
 * {@link org.integratedmodelling.klab.api.knowledge.Observable} and will dispatch it to other views as focal
 * knowledge event whenever the semantics under definition is consistent.
 */
@UIView(value = UIReactor.Type.KnowledgeNavigator, target = Reasoner.class)
public interface KnowledgeNavigatorController extends ViewController<KnowledgeNavigator> {

    void queryModified(String query);

    /**
     * Expose the results of a query so that user can select them.
     */
    void queryResultsReceived(/* TODO */);

    /**
     * User has chosen a result and we should update the knowledge under our purview and dispatch it through a
     * focal knowledge event (TODO) or null if focus is removed.
     */
    void queryResultChosen(/* TODO */);

    void knowledgeFocused(Semantics focus);

    /**
     * Double-click on either a concept from query results or the current expression should bring the
     * KnowledgeEditor into focus.
     *
     * @param selection
     */
    void knowledgeSelected(Semantics selection);
}
