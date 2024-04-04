package org.integratedmodelling.klab.api.view.modeler.panels.controllers;

import org.integratedmodelling.klab.api.lang.kim.KlabDocument;
import org.integratedmodelling.klab.api.view.PanelController;
import org.integratedmodelling.klab.api.view.UIReactor;
import org.integratedmodelling.klab.api.view.annotations.UIActionHandler;
import org.integratedmodelling.klab.api.view.annotations.UIPanelController;
import org.integratedmodelling.klab.api.view.modeler.navigation.NavigableDocument;
import org.integratedmodelling.klab.api.view.modeler.panels.DocumentEditor;

@UIPanelController(value = UIReactor.Type.DocumentEditor, panelType = DocumentEditor.class, target = KlabDocument.class)
public interface DocumentEditorController extends PanelController<NavigableDocument, DocumentEditor> {

    String getContents();

    int getPosition();

    boolean isReadOnly();

    @UIActionHandler(UIAction.ChangeDocumentPosition)
    default void documentPositionChanged(int position) {
        getController().dispatch(this, UIEvent.DocumentPositionChanged, getPayload(), position);
    }

    @UIActionHandler(UIAction.DocumentUpdate)
    default void documentUpdated(String newContents) {
        getController().dispatch(this, UIEvent.AssetUpdateRequest, getPayload().getUrn(), newContents);
    }

}
