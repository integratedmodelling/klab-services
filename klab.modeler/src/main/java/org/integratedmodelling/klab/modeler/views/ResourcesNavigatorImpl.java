package org.integratedmodelling.klab.modeler.views;

import org.integratedmodelling.common.view.AbstractUIView;
import org.integratedmodelling.klab.api.services.ResourcesService;
import org.integratedmodelling.klab.api.services.resources.ResourceSet;
import org.integratedmodelling.klab.api.view.UIController;
import org.integratedmodelling.klab.api.view.modeler.navigation.NavigableAsset;
import org.integratedmodelling.klab.api.view.modeler.navigation.NavigableDocument;
import org.integratedmodelling.klab.api.view.modeler.views.ResourcesNavigator;

public class ResourcesNavigatorImpl extends AbstractUIView implements ResourcesNavigator {

    public ResourcesNavigatorImpl(UIController controller) {
        super(controller);
    }

    @Override
    public UIController getController() {
        return null;
    }

    @Override
    public void loadService(ResourcesService service) {

    }

    @Override
    public void assetChanged(NavigableAsset asset, ResourceSet changeset) {

    }

    @Override
    public void selectAsset(NavigableAsset asset) {

    }

    @Override
    public void focusAsset(NavigableAsset asset) {

    }

    @Override
    public void removeAsset(NavigableAsset asset) {

    }

    @Override
    public void handleDocumentPositionChange(NavigableDocument document, int position) {

    }
}
