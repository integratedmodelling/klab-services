package org.integratedmodelling.klab.api.lang.kim;

import java.io.Serializable;
import java.util.List;
import java.util.Set;

import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.lang.kim.KimMacro.Field;

public interface KimScope extends Serializable {

	/**
	 * Visitor allows traversing all concept declarations and references.
	 * 
	 */
	public static interface Visitor {

		void visitAuthority(String authority, String term);

		void visitDeclaration(KimConcept declaration);

		void visitReference(String conceptName, Set<SemanticType> type, KimConcept validParent);

		void visitNamespace(KimNamespace kimNamespace);

		void visitModel(KimModelStatement kimNamespace);

		void visitObserver(KimInstance kimNamespace);

		void visitConceptStatement(KimConceptStatement kimNamespace);

        void visitTemplate(Field valueOf, KimConcept validParent, boolean mandatory);

	}

	List<KimScope> getChildren();

	/**
	 * Return a parseable string that describes the location of this code scope.
	 * 
	 * @return the location
	 */
	String getLocationDescriptor();

	String getUri();

	void visit(Visitor visitor);

}
