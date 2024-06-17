package org.integratedmodelling.klab.modeler.views.controllers;

import org.integratedmodelling.common.view.AbstractUIViewController;
import org.integratedmodelling.klab.api.data.mediation.classification.Classification;
import org.integratedmodelling.klab.api.knowledge.Expression;
import org.integratedmodelling.klab.api.knowledge.Resource;
import org.integratedmodelling.klab.api.lang.Prototype;
import org.integratedmodelling.klab.api.services.KlabService;
import org.integratedmodelling.klab.api.view.UIController;
import org.integratedmodelling.klab.api.view.modeler.navigation.NavigableDocument;
import org.integratedmodelling.klab.api.view.modeler.views.KnowledgeInspector;
import org.integratedmodelling.klab.api.view.modeler.views.controllers.KnowledgeInspectorController;

public class KnowledgeInspectorControllerImpl extends AbstractUIViewController<KnowledgeInspector> implements KnowledgeInspectorController  {


    public KnowledgeInspectorControllerImpl(UIController controller) {
        super(controller);
    }


    @Override
    public void focusResource(Resource resource) {
        view().showResource(resource);
    }

    @Override
    public void focusDocument(NavigableDocument document, Integer position) {
        var statementPath = document.getStatementPath(position);
        if (!statementPath.isEmpty()) {
            view().showStatements(statementPath);
        }
    }

    @Override
    public void focusService(KlabService.ServiceCapabilities capabilities) {
        view().showService(capabilities);
    }

}
