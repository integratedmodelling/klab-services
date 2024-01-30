package org.integratedmodelling.klab.services.resources.lang;

import org.eclipse.emf.ecore.EObject;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.integratedmodelling.klab.api.services.ResourcesService;
import org.integratedmodelling.languages.api.ObservableSyntax;
import org.integratedmodelling.languages.api.SemanticSyntax;
import org.integratedmodelling.languages.validation.LanguageValidationScope;
import org.integratedmodelling.languages.validation.ReasoningValidationScope;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class DelegatingValidationScope implements ReasoningValidationScope {

    Map<String, ConceptDescriptor> descriptorMap = new HashMap<>();

    @Override
    public ConceptDescriptor getConceptDescriptor(String conceptUrn) {
        if (!descriptorMap.containsKey(conceptUrn) && resourcesService() != null) {
            var descriptor = resourcesService().describeConcept(conceptUrn);
            descriptorMap.put(conceptUrn, descriptor == null ? null :
                                          new ConceptDescriptor(descriptor.namespace(),
                                                  descriptor.conceptName(),
                                                  WorldviewValidationScope.getMainType(descriptor.mainDeclaredType()),
                                                  descriptor.label(), descriptor.description(),
                                                  descriptor.isAbstract()));
        }
        return descriptorMap.get(conceptUrn);
    }

    @Override
    public LanguageValidationScope contextualize(EObject context) {
        return this;
    }

    @Override
    public SemanticSyntax.Type validate(SemanticSyntax concept, List<ValidationMessage> messages) {
        if (reasoner() != null) {
            // TODO
        }
        return null;
    }

    @Override
    public List<ValidationMessage> validateObservable(ObservableSyntax observable) {
        if (reasoner() != null) {
            // TODO
        }
        return Collections.emptyList();
    }

    protected abstract Reasoner reasoner();

    protected abstract ResourcesService resourcesService();

}
