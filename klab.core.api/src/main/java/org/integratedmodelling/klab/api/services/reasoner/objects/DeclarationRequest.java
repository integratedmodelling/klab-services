package org.integratedmodelling.klab.api.services.reasoner.objects;

import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.lang.kim.KimConcept;
import org.integratedmodelling.klab.api.lang.kim.KimObservable;

/**
 * Payload for POST requests that submit a syntactic observable or concept and return its semantic version,
 * also admitting a pattern observable with associated pattern variables.
 */
public class DeclarationRequest {

    private KimObservable observableDeclaration;
    private KimConcept conceptDeclaration;
    private Parameters<String> patternVariables = Parameters.create();

    public KimObservable getObservableDeclaration() {
        return observableDeclaration;
    }

    public void setObservableDeclaration(KimObservable observableDeclaration) {
        this.observableDeclaration = observableDeclaration;
    }

    public KimConcept getConceptDeclaration() {
        return conceptDeclaration;
    }

    public void setConceptDeclaration(KimConcept conceptDeclaration) {
        this.conceptDeclaration = conceptDeclaration;
    }

    public Parameters<String> getPatternVariables() {
        return patternVariables;
    }

    public void setPatternVariables(Parameters<String> patternVariables) {
        this.patternVariables = patternVariables;
    }
}
