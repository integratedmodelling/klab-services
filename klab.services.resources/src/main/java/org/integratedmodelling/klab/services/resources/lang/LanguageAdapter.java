package org.integratedmodelling.klab.services.resources.lang;

import com.google.inject.Injector;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.integratedmodelling.klab.api.lang.impl.kim.KimConceptImpl;
import org.integratedmodelling.klab.api.lang.impl.kim.KimObservableImpl;
import org.integratedmodelling.klab.api.lang.kim.KimConcept;
import org.integratedmodelling.klab.api.lang.kim.KimObservable;
import org.integratedmodelling.klab.api.lang.kim.KimOntology;
import org.integratedmodelling.languages.KimStandaloneSetup;
import org.integratedmodelling.languages.ObservableStandaloneSetup;
import org.integratedmodelling.languages.OntologySyntaxImpl;
import org.integratedmodelling.languages.WorldviewStandaloneSetup;
import org.integratedmodelling.languages.api.ObservableSyntax;
import org.integratedmodelling.languages.api.OntologySyntax;
import org.integratedmodelling.languages.api.ParsedObject;
import org.integratedmodelling.languages.api.SemanticSyntax;
import org.integratedmodelling.languages.kim.Model;
import org.integratedmodelling.languages.observable.ObservableSemantics;
import org.integratedmodelling.languages.validation.BasicObservableValidationScope;
import org.integratedmodelling.languages.validation.LanguageValidationScope;
import org.integratedmodelling.languages.worldview.Ontology;

import java.io.InputStream;

/**
 * Adapter to substitute the current ones, based on older k.IM grammars.
 */
public enum LanguageAdapter {

    INSTANCE;

    private LanguageAdapter() {
    }

    public KimObservable adaptObservable(ObservableSyntax observableSyntax) {

        KimObservableImpl ret = new KimObservableImpl();

        ret.setUrn(observableSyntax.encode());
        ret.setSemantics(adaptSemantics(observableSyntax.getSemantics()));


        return ret;
    }

    public KimConcept adaptSemantics(SemanticSyntax semantics) {
        KimConceptImpl ret = new KimConceptImpl();
        return ret;
    }


    public KimOntology adaptOntology(OntologySyntax ontology) {
        return null; // TODO
    }
}
