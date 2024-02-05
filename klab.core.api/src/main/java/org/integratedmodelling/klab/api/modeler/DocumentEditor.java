package org.integratedmodelling.klab.api.modeler;

import org.integratedmodelling.klab.api.lang.kim.KlabDocument;

public interface DocumentEditor extends ModelerPanel {

    KlabDocument<?> getDocument();

    boolean isReadOnly();
}
