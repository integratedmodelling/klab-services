package org.integratedmodelling.klab.api.lang.impl.kim;

import org.integratedmodelling.klab.api.collections.impl.PairImpl;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.lang.kim.KimConcept;
import org.integratedmodelling.klab.api.lang.kim.KimConceptStatement;

import java.io.Serial;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

public class KimConceptStatementImpl extends KimStatementImpl implements KimConceptStatement {

    @Serial
    private static final long serialVersionUID = 2640057106561346868L;

    private Set<SemanticType> type = EnumSet.noneOf(SemanticType.class);
    private String authorityRequired;
    private List<KimConcept> qualitiesAffected = new ArrayList<>();
    private List<KimConcept> observablesCreated = new ArrayList<>();
    private List<KimConcept> traitsConferred = new ArrayList<>();
    private List<KimConcept> traitsInherited = new ArrayList<>();
    private List<KimConcept> requiredExtents = new ArrayList<>();
    private List<KimConcept> requiredRealms = new ArrayList<>();
    private List<KimConcept> requiredAttributes = new ArrayList<>();
    private List<KimConcept> requiredIdentities = new ArrayList<>();
    private List<KimConcept> emergenceTriggers = new ArrayList<>();
//    private List<KimRestriction> restrictions = new ArrayList<>();
    private KimConcept declaredParent;
    private List<KimConceptStatement> children = new ArrayList<>();
    private KimConcept declaredInherent;
    private boolean alias;
    private boolean isAbstract;
    private boolean subjective;
    private boolean sealed;
    private String urn;
    //    private boolean macro;
    private List<PairImpl<KimConcept, DescriptionType>> observablesDescribed = new ArrayList<>();
    private List<ApplicableConcept> subjectsLinked = new ArrayList<>();
    private List<ApplicableConcept> appliesTo = new ArrayList<>();
    private String docstring;
    private String upperConceptDefined;
    private String authorityDefined;
//    private List<ParentConcept> parents = new ArrayList<>();

    @Override
    public Set<SemanticType> getType() {
        return this.type;
    }

    @Override
    public String getAuthorityRequired() {
        return this.authorityRequired;
    }

    @Override
    public List<KimConcept> getQualitiesAffected() {
        return this.qualitiesAffected;
    }

    @Override
    public List<KimConcept> getObservablesCreated() {
        return this.observablesCreated;
    }

    @Override
    public List<KimConcept> getTraitsConferred() {
        return this.traitsConferred;
    }

    @Override
    public List<KimConcept> getTraitsInherited() {
        return this.traitsInherited;
    }

    @Override
    public List<KimConcept> getRequiredExtents() {
        return this.requiredExtents;
    }

    @Override
    public List<KimConcept> getRequiredRealms() {
        return this.requiredRealms;
    }

    @Override
    public List<KimConcept> getRequiredAttributes() {
        return this.requiredAttributes;
    }

    @Override
    public List<KimConcept> getRequiredIdentities() {
        return this.requiredIdentities;
    }

    @Override
    public List<KimConcept> getEmergenceTriggers() {
        return this.emergenceTriggers;
    }

//    @Override
//    public List<KimRestriction> getRestrictions() {
//        return this.restrictions;
//    }

    @Override
    public boolean isAlias() {
        return this.alias;
    }

    @Override
    public boolean isAbstract() {
        return this.isAbstract;
    }

    @Override
    public String getUrn() {
        return this.urn;
    }

//    @Override
//    public boolean isMacro() {
//        return this.macro;
//    }

    @Override
    public List<PairImpl<KimConcept, DescriptionType>> getObservablesDescribed() {
        return this.observablesDescribed;
    }

    @Override
    public List<ApplicableConcept> getSubjectsLinked() {
        return this.subjectsLinked;
    }

    @Override
    public List<ApplicableConcept> getAppliesTo() {
        return this.appliesTo;
    }

    @Override
    public String getDocstring() {
        return this.docstring;
    }

    public void setType(Set<SemanticType> type) {
        this.type = type;
    }

    public void setAuthorityRequired(String authorityRequired) {
        this.authorityRequired = authorityRequired;
    }

    public void setQualitiesAffected(List<KimConcept> qualitiesAffected) {
        this.qualitiesAffected = qualitiesAffected;
    }

    public void setObservablesCreated(List<KimConcept> observablesCreated) {
        this.observablesCreated = observablesCreated;
    }

    public void setTraitsConferred(List<KimConcept> traitsConferred) {
        this.traitsConferred = traitsConferred;
    }

    public void setTraitsInherited(List<KimConcept> traitsInherited) {
        this.traitsInherited = traitsInherited;
    }

    public void setRequiredExtents(List<KimConcept> requiredExtents) {
        this.requiredExtents = requiredExtents;
    }

    public void setRequiredRealms(List<KimConcept> requiredRealms) {
        this.requiredRealms = requiredRealms;
    }

    public void setRequiredAttributes(List<KimConcept> requiredAttributes) {
        this.requiredAttributes = requiredAttributes;
    }

    public void setRequiredIdentities(List<KimConcept> requiredIdentities) {
        this.requiredIdentities = requiredIdentities;
    }

    public void setEmergenceTriggers(List<KimConcept> emergenceTriggers) {
        this.emergenceTriggers = emergenceTriggers;
    }

//    public void setRestrictions(List<KimRestriction> restrictions) {
//        this.restrictions = restrictions;
//    }

    public void setAlias(boolean alias) {
        this.alias = alias;
    }

    public void setAbstract(boolean isAbstract) {
        this.isAbstract = isAbstract;
    }

    public void setUrn(String urn) {
        this.urn = urn;
    }

//    public void setMacro(boolean macro) {
//        this.macro = macro;
//    }

    public void setObservablesDescribed(List<PairImpl<KimConcept, DescriptionType>> observablesDescribed) {
        this.observablesDescribed = observablesDescribed;
    }

    public void setSubjectsLinked(List<ApplicableConcept> subjectsLinked) {
        this.subjectsLinked = subjectsLinked;
    }

    public void setAppliesTo(List<ApplicableConcept> appliesTo) {
        this.appliesTo = appliesTo;
    }

    public void setDocstring(String docstring) {
        this.docstring = docstring;
    }

    @Override
    public String getUpperConceptDefined() {
        return this.upperConceptDefined;
    }

    @Override
    public String getAuthorityDefined() {
        return this.authorityDefined;
    }

    public void setUpperConceptDefined(String upperConceptDefined) {
        this.upperConceptDefined = upperConceptDefined;
    }

    public void setAuthorityDefined(String authorityDefined) {
        this.authorityDefined = authorityDefined;
    }

//    @Override
//    public List<ParentConcept> getParents() {
//        return parents;
//    }
//
//    public void setParents(List<ParentConcept> parents) {
//        this.parents = parents;
//    }

    @Override
    public boolean isSubjective() {
        return subjective;
    }

    public void setSubjective(boolean subjective) {
        this.subjective = subjective;
    }

    @Override
    public boolean isSealed() {
        return sealed;
    }

    public void setSealed(boolean sealed) {
        this.sealed = sealed;
    }

    @Override
    public KimConcept getDeclaredParent() {
        return declaredParent;
    }

    public void setDeclaredParent(KimConcept declaredParent) {
        this.declaredParent = declaredParent;
    }


    @Override
    public KimConcept getDeclaredInherent() {
        return declaredInherent;
    }

    public void setDeclaredInherent(KimConcept declaredInherent) {
        this.declaredInherent = declaredInherent;
    }

    @Override
    public List<KimConceptStatement> getChildren() {
        return children;
    }

    public void setChildren(List<KimConceptStatement> children) {
        this.children = children;
    }

    @Override
    public void visit(KlabStatementVisitor visitor) {

    }
}
