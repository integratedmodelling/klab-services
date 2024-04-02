package org.integratedmodelling.klab.api.lang.impl.kim;

import org.integratedmodelling.klab.api.collections.Pair;
import org.integratedmodelling.klab.api.lang.kim.KimConcept;
import org.integratedmodelling.klab.api.lang.kim.KimConceptStatement;
import org.integratedmodelling.klab.api.lang.kim.KimOntology;

import java.util.*;

public class KimOntologyImpl extends KimDocumentImpl<KimConceptStatement> implements KimOntology {

    private List<KimConceptStatement> statements = new ArrayList<>();
    private List<String> importedOntologies = new ArrayList<>();
    private List<Pair<String, String>> owlImports = new ArrayList<>();
    private List<Pair<String, List<String>>> vocabularyImports = new ArrayList<>();
    private KimConcept domain;

    @Override
    public List<KimConceptStatement> getStatements() {
        return this.statements;
    }

    @Override
    public Set<String> importedNamespaces(boolean withinType) {
        // no visiting necessary; any non-referenced imported ontologies are a syntax error
        return new HashSet<>(importedOntologies);
    }

    @Override
    public void visit(DocumentVisitor visitor) {
    }

    @Override
    public Collection<String> getImportedOntologies() {
        return this.importedOntologies;
    }

    @Override
    public List<Pair<String, String>> getOwlImports() {
        return this.owlImports;
    }

    @Override
    public List<Pair<String, List<String>>> getVocabularyImports() {
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

    public void setOwlImports(List<Pair<String, String>> owlImports) {
        this.owlImports = owlImports;
    }

    public void setVocabularyImports(List<Pair<String, List<String>>> vocabularyImports) {
        this.vocabularyImports = vocabularyImports;
    }

    public void setDomain(KimConcept domain) {
        this.domain = domain;
    }

}
