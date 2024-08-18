package org.integratedmodelling.klab.api.lang.kim;

import org.integratedmodelling.klab.api.data.Metadata;
import org.integratedmodelling.klab.api.knowledge.KlabAsset;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.lang.Annotation;
import org.integratedmodelling.klab.api.lang.Statement;

import java.util.List;
import java.util.Set;

/**
 * @author Ferd
 */
public interface KlabStatement extends Statement, KlabAsset {

    /**
     * Visitor allows traversing all concept declarations and references.
     */
    interface KlabStatementVisitor extends Statement.Visitor {

        //		void visitAuthority(String authority, String term);
        //
        //		void visitDeclaration(KimConcept declaration);
        //
        //		void visitReference(String conceptName, Set<SemanticType> type, KimConcept validParent);
        //
        //		void visitNamespace(KimNamespace kimNamespace);
        //
        //		void visitModel(KimModel kimNamespace);
        //
        ////		void visitObserver(KimInstance kimNamespace);
        //
        //		void visitConceptStatement(KimConceptStatement kimNamespace);

        void visitMetadata(Metadata metadata);

    }

    /**
     * Scope is relevant to models and namespaces, where it affects resolution of models.
     *
     * @author Ferd
     */
    enum Scope {

        PUBLIC, PRIVATE, PROJECT_PRIVATE;

        public Scope narrowest(Scope... scopes) {
            Scope ret = scopes == null || scopes.length == 0 ? null : scopes[0];
            if (ret != null) {
                for (int i = 1; i < scopes.length; i++) {
                    if (scopes[i].ordinal() < ret.ordinal()) {
                        ret = scopes[i];
                    }
                }
            }
            return ret;
        }
    }

    /**
     * The namespace ID for this object. For a KimNamespace it's also the official name (there is no
     * getName()).
     *
     * @return the namespace or null
     */
    String getNamespace();

    /**
     * Statement are usually defined within a project, unless they're "unhinged" observables and concepts.
     *
     * @return the project or null
     */
    String getProjectName();

    /**
     * The knowledge class of the containing document, if any (or if this is a document). Used for reporting
     * and to compile portable parsing results.
     *
     * @return the class of the (containing) document, or null if the statement was defined outside of one.
     */
    KnowledgeClass getDocumentClass();

    /**
     * Scope can be declared for namespaces and models. Default is public or whatever the containing namespace
     * scope is. Concepts unfortunately cannot be scoped with current infrastructure.
     *
     * @return
     */
    Scope getScope();

    //	/**
    //	 *
    //	 * @param visitor
    //	 */
    //	void visit(KlabStatementVisitor visitor);

}
