package org.integratedmodelling.klab.api.view.modeler.navigation;

import org.integratedmodelling.klab.api.knowledge.KlabAsset;
import org.integratedmodelling.klab.api.lang.Statement;
import org.integratedmodelling.klab.api.lang.kim.KlabDocument;

public interface NavigableDocument extends NavigableAsset {

    /**
     * Any document can live in a file which should have the extension returned here.
     */
    String getFileExtension();

}
