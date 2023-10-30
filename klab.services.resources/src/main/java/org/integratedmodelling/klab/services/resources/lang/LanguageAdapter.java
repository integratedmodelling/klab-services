package org.integratedmodelling.klab.services.resources.lang;

import org.integratedmodelling.klab.api.lang.impl.kim.KimConceptImpl;
import org.integratedmodelling.klab.api.lang.impl.kim.KimObservableImpl;
import org.integratedmodelling.klab.api.lang.kim.KimConcept;
import org.integratedmodelling.klab.api.lang.kim.KimObservable;
import org.integratedmodelling.languages.api.ObservableSyntax;
import org.integratedmodelling.languages.api.SemanticSyntax;

/**
 * Adapter to substitute the current one, based on older k.IM grammars.
 */
public class LanguageAdapter {

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

}
