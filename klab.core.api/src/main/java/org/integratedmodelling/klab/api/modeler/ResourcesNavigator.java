package org.integratedmodelling.klab.api.modeler;

import org.integratedmodelling.klab.api.modeler.navigation.NavigableAsset;
import org.integratedmodelling.klab.api.modeler.navigation.NavigableDocument;

public interface ResourcesNavigator extends ModelerView {

    default void selectDocument(NavigableDocument document) {
        getModeler().dispatch(UIEvent.DocumentSelected, document);
    }

}
