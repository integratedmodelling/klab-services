package org.integratedmodelling.klab.api.view.modeler.views;

import org.integratedmodelling.klab.api.knowledge.Resource;
import org.integratedmodelling.klab.api.lang.Statement;
import org.integratedmodelling.klab.api.services.KlabService;
import org.integratedmodelling.klab.api.view.View;

import java.util.List;

/**
 * Displays info about some piece of knowledge or a service. Normally linked to single-click selection of
 * things in the UI.
 */
public interface KnowledgeInspector extends View {

    void showStatements(List<Statement> statementPath);

    void showResource(Resource resource);

    void showService(KlabService.ServiceCapabilities capabilities);
}
