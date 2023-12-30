package org.integratedmodelling.klab.api.lang.impl.kim;

import org.integratedmodelling.klab.api.collections.impl.PairImpl;
import org.integratedmodelling.klab.api.lang.kim.KimConcept;
import org.integratedmodelling.klab.api.lang.kim.KimConceptStatement;
import org.integratedmodelling.klab.api.lang.kim.KimDocument;
import org.integratedmodelling.klab.api.lang.kim.KimOntology;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class KimOntologyImpl extends KimDocumentImpl<KimConceptStatement> implements KimOntology {

    private List<KimConceptStatement> statements = new ArrayList<>();
    private List<String> importedOntologies = new ArrayList<>();
    private List<PairImpl<String, String>> owlImports = new ArrayList<>();
    private List<PairImpl<String, List<String>>> vocabularyImports = new ArrayList<>();
    private KimConcept domain;

    @Override
    public List<KimConceptStatement> getStatements() {
        return this.statements;
    }

    @Override
    public Collection<String> getImportedOntologies() {
        return this.importedOntologies;
    }

    @Override
    public List<PairImpl<String, String>> getOwlImports() {
        return this.owlImports;
    }

    @Override
    public List<PairImpl<String, List<String>>> getVocabularyImports() {
        return this.vocabularyImports;
    }

    @Override
    public KimConcept getDomain() {
        return this.domain;
    }

    public void setStatements(List<KimConceptStatement> statements) {
        this.statements = statements;
    }

    public void setImportedOntologies(List<String> importedOntologies) {
        this.importedOntologies = importedOntologies;
    }

    public void setOwlImports(List<PairImpl<String, String>> owlImports) {
        this.owlImports = owlImports;
    }

    public void setVocabularyImports(List<PairImpl<String, List<String>>> vocabularyImports) {
        this.vocabularyImports = vocabularyImports;
    }

    public void setDomain(KimConcept domain) {
        this.domain = domain;
    }
}
