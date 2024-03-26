package org.integratedmodelling.klab.api.view.modeler.panels.controllers;

import org.integratedmodelling.klab.api.lang.kim.KlabDocument;
import org.integratedmodelling.klab.api.view.PanelController;
import org.integratedmodelling.klab.api.view.UIReactor;
import org.integratedmodelling.klab.api.view.annotations.UIActionHandler;
import org.integratedmodelling.klab.api.view.annotations.UIView;
import org.integratedmodelling.klab.api.view.modeler.navigation.NavigableDocument;
import org.integratedmodelling.klab.api.view.modeler.panels.DocumentEditor;

@UIView(value = UIReactor.Type.DocumentEditor, target = KlabDocument.class)
public interface DocumentEditorController extends PanelController<NavigableDocument, DocumentEditor> {

    KlabDocument<?> getDocument();

    String getContents();

    int getPosition();

    boolean isReadOnly();

    @UIActionHandler(UIAction.ChangeDocumentPosition)
    default void documentPositionChanged(int position) {
        getController().dispatch(this, UIEvent.DocumentPositionChanged, getDocument(), position);
    }

    @UIActionHandler(UIAction.DocumentUpdate)
    default void documentUpdated() {
        getController().dispatch(this, UIEvent.AssetUpdateRequest, getDocument().getUrn(), getContents());
    }

}
