package org.integratedmodelling.klab.modeler.panels.controllers;

import org.integratedmodelling.common.view.AbstractUIPanelController;
import org.integratedmodelling.klab.api.lang.kactors.KActorsBehavior;
import org.integratedmodelling.klab.api.lang.kim.*;
import org.integratedmodelling.klab.api.services.ResourcesService;
import org.integratedmodelling.klab.api.view.UIController;
import org.integratedmodelling.klab.api.view.UIReactor;
import org.integratedmodelling.klab.api.view.annotations.UIPanelController;
import org.integratedmodelling.klab.api.view.modeler.navigation.NavigableDocument;
import org.integratedmodelling.klab.api.view.modeler.panels.DocumentEditor;
import org.integratedmodelling.klab.api.view.modeler.panels.controllers.DocumentEditorController;

public class DocumentEditorControllerImpl extends AbstractUIPanelController<NavigableDocument,
        DocumentEditor> implements DocumentEditorController {

    public DocumentEditorControllerImpl(UIController controller) {
        super(controller);
    }

    public void documentUpdated(String newContents) {
        var service = getController().engine().serviceScope().getService(ResourcesService.class);
        if (service instanceof ResourcesService.Admin admin) {
            switch (getPayload()) {
                case KimOntology ontology -> admin.updateOntology(ontology.getProjectName(), newContents);
                case KimNamespace namespace -> admin.updateNamespace(namespace.getProjectName(), newContents);
                case KimObservationStrategyDocument strategyDocument -> admin.updateObservationStrategies(strategyDocument.getProjectName(), newContents);
                case KActorsBehavior behavior -> admin.updateBehavior(behavior.getProjectName(), newContents);
                default -> {
                }
            }
        }
//        getController().dispatch(this, UIEvent.AssetUpdateRequest, getDocument().getUrn(), newContents);
    }

    @Override
    public NavigableDocument submitOnSave() {
        return null;
    }

    @Override
    public String getContents() {
        return null;
    }

    @Override
    public int getPosition() {
        return 0;
    }

    @Override
    public boolean isReadOnly() {
        return false;
    }
}
