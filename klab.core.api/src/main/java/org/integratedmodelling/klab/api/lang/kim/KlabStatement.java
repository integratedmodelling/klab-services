package org.integratedmodelling.klab.api.lang.kim;

import java.util.Set;

import org.integratedmodelling.klab.api.knowledge.KlabAsset;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.lang.Statement;

/**
 * 
 * @author Ferd
 *
 */
public interface KlabStatement extends Statement, KlabAsset {

	/**
	 * Visitor allows traversing all concept declarations and references.
	 * 
	 */
	interface KimVisitor extends Visitor {

		void visitAuthority(String authority, String term);

		void visitDeclaration(KimConcept declaration);

		void visitReference(String conceptName, Set<SemanticType> type, KimConcept validParent);

		void visitNamespace(KimNamespace kimNamespace);

		void visitModel(KimModel kimNamespace);

		void visitObserver(KimInstance kimNamespace);

		void visitConceptStatement(KimConceptStatement kimNamespace);

	}

	/**
	 * Scope is relevant to models and namespaces, where it affects resolution of
	 * models.
	 * 
	 * @author Ferd
	 *
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

//	/**
//	 * Documentation metadata is the content of the @documentation annotation if
//	 * present.
//	 *
//	 * @return the documentation
//	 */
//	Parameters<String> getDocumentationMetadata();

	/**
	 * The namespace ID for this object. For a KimNamespace it's also the official
	 * name (there is no getName()).
	 * 
	 * @return
	 */
	String getNamespace();

	/**
	 * Scope can be declared for namespaces and models. Default is public or
	 * whatever the containing namespace scope is. Concepts unfortunately cannot be
	 * scoped with current infrastructure.
	 * 
	 * @return
	 */
	Scope getScope();

}
