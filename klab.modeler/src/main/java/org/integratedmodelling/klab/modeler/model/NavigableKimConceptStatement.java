package org.integratedmodelling.klab.modeler.model;

import org.integratedmodelling.klab.api.collections.impl.PairImpl;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.lang.kim.KimConcept;
import org.integratedmodelling.klab.api.lang.kim.KimConceptStatement;

import java.util.List;
import java.util.Set;

public class NavigableKimConceptStatement extends NavigableKlabStatement<KimConceptStatement> implements KimConceptStatement {
    public NavigableKimConceptStatement(KimConceptStatement asset, NavigableKlabAsset<?> parent) {
        super(asset, parent);
    }

    @Override
    public List<KimConceptStatement> getChildren() {
        return delegate.getChildren();
    }

    @Override
    public Set<SemanticType> getType() {
        return delegate.getType();
    }

    @Override
    public String getUpperConceptDefined() {
        return delegate.getUpperConceptDefined();
    }

    @Override
    public String getAuthorityDefined() {
        return delegate.getAuthorityDefined();
    }

    @Override
    public String getAuthorityRequired() {
        return delegate.getAuthorityRequired();
    }

    @Override
    public List<KimConcept> getQualitiesAffected() {
        return delegate.getQualitiesAffected();
    }

    @Override
    public List<KimConcept> getObservablesCreated() {
        return delegate.getObservablesCreated();
    }

    @Override
    public List<KimConcept> getTraitsConferred() {
        return delegate.getTraitsConferred();
    }

    @Override
    public List<KimConcept> getTraitsInherited() {
        return delegate.getTraitsInherited();
    }

    @Override
    public List<KimConcept> getRequiredExtents() {
        return delegate.getRequiredExtents();
    }

    @Override
    public List<KimConcept> getRequiredRealms() {
        return delegate.getRequiredRealms();
    }

    @Override
    public List<KimConcept> getRequiredAttributes() {
        return delegate.getRequiredAttributes();
    }

    @Override
    public List<KimConcept> getRequiredIdentities() {
        return delegate.getRequiredIdentities();
    }

    @Override
    public List<KimConcept> getEmergenceTriggers() {
        return delegate.getEmergenceTriggers();
    }

    @Override
    public KimConcept getDeclaredParent() {
        return delegate.getDeclaredParent();
    }

    @Override
    public KimConcept getDeclaredInherent() {
        return delegate.getDeclaredInherent();
    }

    @Override
    public boolean isAlias() {
        return delegate.isAlias();
    }

    @Override
    public boolean isAbstract() {
        return delegate.isAbstract();
    }

    @Override
    public boolean isSubjective() {
        return delegate.isSubjective();
    }

    @Override
    public boolean isSealed() {
        return delegate.isSealed();
    }

    @Override
    public List<PairImpl<KimConcept, DescriptionType>> getObservablesDescribed() {
        return delegate.getObservablesDescribed();
    }

    @Override
    public List<ApplicableConcept> getSubjectsLinked() {
        return delegate.getSubjectsLinked();
    }

    @Override
    public List<ApplicableConcept> getAppliesTo() {
        return delegate.getAppliesTo();
    }

    @Override
    public String getDocstring() {
        return delegate.getDocstring();
    }
}
