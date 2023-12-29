package org.integratedmodelling.klab.api.lang.impl.kim;

import org.integratedmodelling.klab.api.collections.impl.PairImpl;
import org.integratedmodelling.klab.api.lang.kim.KimConcept;
import org.integratedmodelling.klab.api.lang.kim.KimConceptStatement;
import org.integratedmodelling.klab.api.lang.kim.KimDocument;
import org.integratedmodelling.klab.api.lang.kim.KimOntology;

import java.util.Collection;
import java.util.List;

public class KimOntologyImpl extends KimDocumentImpl<KimConceptStatement> implements KimOntology {
    @Override
    public List<KimConceptStatement> getStatements() {
        return null;
    }

    @Override
    public Collection<String> getDisjointNamespaces() {
        return null;
    }

    @Override
    public List<PairImpl<String, String>> getOwlImports() {
        return null;
    }

    @Override
    public List<PairImpl<String, List<String>>> getVocabularyImports() {
        return null;
    }

    @Override
    public KimConcept getDomain() {
        return null;
    }
}
