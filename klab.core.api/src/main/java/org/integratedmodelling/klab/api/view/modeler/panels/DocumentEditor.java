package org.integratedmodelling.klab.api.view.modeler.panels;

import org.integratedmodelling.klab.api.view.PanelView;
import org.integratedmodelling.klab.api.view.modeler.navigation.NavigableDocument;

public interface DocumentEditor extends PanelView<NavigableDocument> {

    void moveCaretTo(int position);

    /**
     * Called on an open editor with a modified document whenever there is a reparse of the contents.
     *
     * @param document
     */
    void reload(NavigableDocument document);
}
