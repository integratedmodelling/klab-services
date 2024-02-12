package org.integratedmodelling.klab.api.modeler.panels;

import org.integratedmodelling.klab.api.lang.kim.KlabDocument;
import org.integratedmodelling.klab.api.modeler.ModelerPanel;
import org.integratedmodelling.klab.api.modeler.UIReactor;
import org.integratedmodelling.klab.api.modeler.annotations.UIActionHandler;
import org.integratedmodelling.klab.api.modeler.annotations.UIView;

@UIView(UIReactor.Type.DocumentEditor)
public interface DocumentEditor extends ModelerPanel {

    KlabDocument<?> getDocument();

    String getContents();

    int getPosition();

    boolean isReadOnly();

    @UIActionHandler(UIAction.ChangeDocumentPosition)
    default void documentPositionChanged(int position) {
        getModeler().dispatch(this, UIEvent.DocumentPositionChanged, getDocument(), position);
    }

    @UIActionHandler(UIAction.DocumentUpdate)
    default void documentUpdated() {
        getModeler().dispatch(this, UIEvent.DocumentUpdateRequest, getDocument().getUrn(), getContents());
    }

}
