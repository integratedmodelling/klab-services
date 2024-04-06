package org.integratedmodelling.klab.api.view.modeler.panels.controllers;

import org.integratedmodelling.klab.api.lang.kim.KlabDocument;
import org.integratedmodelling.klab.api.view.PanelController;
import org.integratedmodelling.klab.api.view.UIReactor;
import org.integratedmodelling.klab.api.view.annotations.UIActionHandler;
import org.integratedmodelling.klab.api.view.annotations.UIPanelController;
import org.integratedmodelling.klab.api.view.modeler.navigation.NavigableDocument;
import org.integratedmodelling.klab.api.view.modeler.panels.DocumentEditor;

@UIPanelController(value = UIReactor.Type.DocumentEditor, panelType = DocumentEditor.class, target =
        KlabDocument.class)
public interface DocumentEditorController extends PanelController<NavigableDocument, DocumentEditor> {

    String getContents();

    int getPosition();

    boolean isReadOnly();

    /**
     * View should call this when the caret is moved in the editor. The change of focus is dispatched so that
     * remaining views can react and align any knowledge tree or info panel.
     *
     * @param position
     */
    @UIActionHandler(UIAction.ReportChangeOfPositionInDocument)
    default void caretMovedTo(int position) {
        getController().dispatch(this, UIEvent.DocumentPositionChanged, getPayload(), position);
    }


    @UIActionHandler(UIAction.DocumentUpdate)
    default void documentUpdated(String newContents) {
        getController().dispatch(this, UIEvent.AssetUpdateRequest, getPayload().getUrn(), newContents);
    }

    /**
     * Controllers call this when an external event makes it necessary to move to a specific position in the
     * handled document.
     *
     * @param position
     */
    @UIActionHandler(UIAction.RequestChangeOfPositionInDocument)
    void moveCaretTo(int position);

}
