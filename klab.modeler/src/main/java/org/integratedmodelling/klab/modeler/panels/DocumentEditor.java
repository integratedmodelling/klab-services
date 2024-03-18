package org.integratedmodelling.klab.modeler.panels;

import org.integratedmodelling.klab.api.lang.kim.KlabDocument;
import org.integratedmodelling.klab.api.view.Panel;
import org.integratedmodelling.klab.api.view.UIReactor;
import org.integratedmodelling.klab.api.view.annotations.UIActionHandler;
import org.integratedmodelling.klab.api.view.annotations.UIView;

@UIView(UIReactor.Type.DocumentEditor)
public interface DocumentEditor extends Panel<KlabDocument<?>> {

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
