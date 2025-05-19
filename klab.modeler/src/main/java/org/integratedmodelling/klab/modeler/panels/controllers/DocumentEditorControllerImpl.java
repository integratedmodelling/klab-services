package org.integratedmodelling.klab.modeler.panels.controllers;

import org.integratedmodelling.common.view.AbstractUIPanelController;
import org.integratedmodelling.klab.api.knowledge.organization.ProjectStorage;
import org.integratedmodelling.klab.api.lang.kactors.KActorsBehavior;
import org.integratedmodelling.klab.api.lang.kim.*;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.integratedmodelling.klab.api.services.ResourcesService;
import org.integratedmodelling.klab.api.services.resources.ResourceSet;
import org.integratedmodelling.klab.api.view.UIController;
import org.integratedmodelling.klab.api.view.modeler.navigation.NavigableDocument;
import org.integratedmodelling.klab.api.view.modeler.panels.DocumentEditor;
import org.integratedmodelling.klab.api.view.modeler.panels.controllers.DocumentEditorController;
import org.integratedmodelling.klab.modeler.model.NavigableProject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class DocumentEditorControllerImpl extends AbstractUIPanelController<NavigableDocument,
        DocumentEditor> implements DocumentEditorController {

    private int position;

    public DocumentEditorControllerImpl(UIController controller) {
        super(controller);
    }

    public void documentUpdated(String newContents) {

        // temporarily disable the panel to give us time to modify
        panel().disable();

        var service = getController().engine().serviceScope().getService(ResourcesService.class);
        var reasoner = getController().engine().serviceScope().getService(Reasoner.class);

        if (service instanceof ResourcesService.Admin admin) {

            var scope = getController().engine().serviceScope();
            List<ResourceSet> changes = switch (getPayload()) {
                case KimOntology ontology -> {
                    List<ResourceSet> ret = new ArrayList<>();
                    for (var resourceSet : admin.updateDocument(ontology.getProjectName(),
                            ProjectStorage.ResourceType.ONTOLOGY, newContents, getController().user())) {
                        ret.add(resourceSet);
                    }
                    yield ret;
                }
                case KimNamespace namespace ->
                    // TODO this can have consequences
                        admin.updateDocument(namespace.getProjectName(),
                                ProjectStorage.ResourceType.MODEL_NAMESPACE, newContents,
                                getController().user());
                case KimObservationStrategyDocument strategyDocument -> {
                    List<ResourceSet> ret = new ArrayList<>();
                    for (var resourceSet : admin.updateDocument(strategyDocument.getProjectName(),
                            ProjectStorage.ResourceType.STRATEGY, newContents,
                            getController().user())) {
                        ret.add(resourceSet);
                    }
                    yield ret;
                }
                case KActorsBehavior behavior -> admin.updateDocument(behavior.getProjectName(),
                        ProjectStorage.ResourceType.BEHAVIOR, newContents, getController().user());
                default -> Collections.emptyList();
            };

            var container = getPayload().root();

            for (var change : changes) {
                getController().dispatch(this, UIEvent.WorkspaceModified, change);
            }
        }
    }

    @Override
    public void reload(NavigableDocument document) {
        panel().reload(document);
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
