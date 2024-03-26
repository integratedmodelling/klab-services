package org.integratedmodelling.klab.api.view.modeler.panels.controllers;

import org.integratedmodelling.klab.api.knowledge.Concept;
import org.integratedmodelling.klab.api.view.PanelController;
import org.integratedmodelling.klab.api.view.UIReactor;
import org.integratedmodelling.klab.api.view.annotations.UIView;
import org.integratedmodelling.klab.api.view.modeler.panels.KnowledgeEditor;

@UIView(UIReactor.Type.KnowledgeEditor)
public interface KnowledgeEditorController extends PanelController<Concept, KnowledgeEditor> {
}
