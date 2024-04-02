package org.integratedmodelling.klab.modeler.panels.controllers;

import org.integratedmodelling.common.view.AbstractUIPanelController;
import org.integratedmodelling.klab.api.lang.kim.KlabDocument;
import org.integratedmodelling.klab.api.view.UIController;
import org.integratedmodelling.klab.api.view.UIReactor;
import org.integratedmodelling.klab.api.view.annotations.UIPanelController;
import org.integratedmodelling.klab.api.view.modeler.navigation.NavigableDocument;
import org.integratedmodelling.klab.api.view.modeler.panels.DocumentEditor;
import org.integratedmodelling.klab.api.view.modeler.panels.controllers.DocumentEditorController;

public class DocumentEditorControllerImpl extends AbstractUIPanelController<NavigableDocument, DocumentEditor> implements DocumentEditorController {

    protected DocumentEditorControllerImpl(UIController controller, DocumentEditor documentEditor) {
        super(controller, documentEditor);
    }

    @Override
    public void load(NavigableDocument payload) {

    }

    @Override
    public NavigableDocument submitOnSave() {
        return null;
    }

    @Override
    public KlabDocument<?> getDocument() {
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
