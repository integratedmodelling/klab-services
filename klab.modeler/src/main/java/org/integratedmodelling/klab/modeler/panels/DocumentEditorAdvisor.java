package org.integratedmodelling.klab.modeler.panels;

import org.integratedmodelling.klab.api.view.PanelController;
import org.integratedmodelling.klab.api.view.PanelView;
import org.integratedmodelling.klab.api.view.modeler.navigation.NavigableDocument;
import org.integratedmodelling.klab.api.view.modeler.panels.DocumentEditor;

public class DocumentEditorAdvisor extends BasePanelAdvisor<NavigableDocument> implements DocumentEditor {


    public DocumentEditorAdvisor(PanelController<NavigableDocument, PanelView<NavigableDocument>> controller) {
        super(controller);
    }

    @Override
    public void load(NavigableDocument payload) {
        System.out.println("CHUPA CHUPA");
    }

    @Override
    public boolean close() {
        return true;
    }
}
