package org.integratedmodelling.klab.modeler.panels.controllers;

import org.integratedmodelling.common.view.AbstractUIPanelController;
import org.integratedmodelling.klab.api.knowledge.organization.ProjectStorage;
import org.integratedmodelling.klab.api.lang.kactors.KActorsBehavior;
import org.integratedmodelling.klab.api.lang.kim.*;
import org.integratedmodelling.klab.api.services.ResourcesService;
import org.integratedmodelling.klab.api.view.UIController;
import org.integratedmodelling.klab.api.view.modeler.navigation.NavigableDocument;
import org.integratedmodelling.klab.api.view.modeler.panels.DocumentEditor;
import org.integratedmodelling.klab.api.view.modeler.panels.controllers.DocumentEditorController;
import org.integratedmodelling.klab.modeler.model.NavigableProject;

import java.util.Collections;

public class DocumentEditorControllerImpl extends AbstractUIPanelController<NavigableDocument,
        DocumentEditor> implements DocumentEditorController {

    private int position;

    public DocumentEditorControllerImpl(UIController controller) {
        super(controller);
    }

    public void documentUpdated(String newContents) {

        var service = getController().engine().serviceScope().getService(ResourcesService.class);

        if (service instanceof ResourcesService.Admin admin) {

            var authToken = getController().engine().serviceScope().getIdentity().getId();
            var changes = switch (getPayload()) {
                case KimOntology ontology ->
                        admin.updateDocument(ontology.getProjectName(), ProjectStorage.ResourceType.ONTOLOGY, newContents, authToken);
                case KimNamespace namespace ->
                        admin.updateDocument(namespace.getProjectName(), ProjectStorage.ResourceType.MODEL_NAMESPACE, newContents, authToken);
                case KimObservationStrategyDocument strategyDocument ->
                        admin.updateDocument(strategyDocument.getProjectName(), ProjectStorage.ResourceType.STRATEGY, newContents,
                                authToken);
                case KActorsBehavior behavior ->
                        admin.updateDocument(behavior.getProjectName(), ProjectStorage.ResourceType.BEHAVIOR, newContents, authToken);
                default -> Collections.emptyList();
            };

            for (var change : changes) {
                getController().dispatch(this, UIEvent.WorkspaceModified, change);
            }
        }
    }

    @Override
    public void moveCaretTo(int position) {
        panel().moveCaretTo(this.position = position);
    }

    @Override
    public int getPosition() {
        return position;
    }

    @Override
    public boolean isReadOnly() {
        var project = getPayload().parent(NavigableProject.class);
        return project == null || !project.isLocked();
    }
}
