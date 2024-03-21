package org.integratedmodelling.klab.api.view.modeler.panels;

import org.integratedmodelling.klab.api.knowledge.Concept;
import org.integratedmodelling.klab.api.view.PanelController;
import org.integratedmodelling.klab.api.view.UIReactor;
import org.integratedmodelling.klab.api.view.annotations.UIView;

@UIView(UIReactor.Type.KnowledgeEditor)
public interface KnowledgeEditor extends PanelController<Concept> {
}
