package org.integratedmodelling.klab.modeler.panels;

import org.integratedmodelling.klab.api.knowledge.Concept;
import org.integratedmodelling.klab.api.view.Panel;
import org.integratedmodelling.klab.api.view.UIReactor;
import org.integratedmodelling.klab.api.view.annotations.UIView;

@UIView(UIReactor.Type.KnowledgeEditor)
public interface KnowledgeEditor extends Panel<Concept> {
}