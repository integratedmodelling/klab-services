package org.integratedmodelling.klab.api.knowledge.impl;


import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.lang.kim.*;

import java.util.Set;

/**
 * A do-nothing KimVisitor for less painful derivations when only a few actions are needed.
 *
 * @author ferdinando.villa
 */
public class DefaultKimVisitor implements KlabStatement.KlabStatementVisitor {

    @Override
    public void visitAuthority(String authority, String term) {
    }

    @Override
    public void visitDeclaration(KimConcept declaration) {
    }

    @Override
    public void visitReference(String conceptName, Set<SemanticType> type, KimConcept validParent) {
    }

    @Override
    public void visitNamespace(KimNamespace kimNamespace) {
    }

    @Override
    public void visitModel(KimModel kimNamespace) {
    }

    @Override
    public void visitObserver(KimInstance kimNamespace) {
    }

    @Override
    public void visitConceptStatement(KimConceptStatement kimNamespace) {
    }
}