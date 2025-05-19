package org.integratedmodelling.klab.api.view.modeler.panels.controllers;

import org.integratedmodelling.klab.api.knowledge.Concept;
import org.integratedmodelling.klab.api.view.PanelController;
import org.integratedmodelling.klab.api.view.UIReactor;
import org.integratedmodelling.klab.api.view.annotations.UIPanelController;
import org.integratedmodelling.klab.api.view.modeler.panels.KnowledgeEditor;

@UIPanelController(value = UIReactor.Type.KnowledgeEditor, panelType = KnowledgeEditor.class, target = Concept.class)
public interface KnowledgeEditorController extends PanelController<Concept, KnowledgeEditor> {
}
