package org.integratedmodelling.klab.api.lang.kim;

import java.util.List;
import java.util.Set;

import org.integratedmodelling.klab.api.collections.impl.PairImpl;
import org.integratedmodelling.klab.api.knowledge.SemanticType;

public interface KimConceptStatement extends KlabStatement {

    /**
     * Types of descriptional relationships to other concepts
     *
     * @author ferdinando.villa
     */
    enum DescriptionType {
        DESCRIBES, INCREASES_WITH, DECREASES_WITH, MARKS, CLASSIFIES, DISCRETIZES
    }

    /**
     * Anything that "applies to" (including subject linked by relationships) gets this descriptor. If the
     * application is defined for a role, the original observable is also indicated.
     *
     * @author ferdinando.villa
     */
    interface ApplicableConcept {

        /**
         * If the application is through a role, the original observable that is expected to incarnate it.
         * Otherwise null.
         *
         * @return a concept declaration or null
         */
        KimConcept getOriginalObservable();

        /**
         * Only filled in when the target concept is a relationship.
         *
         * @return a concept declaration or null
         */
        KimConcept getSource();

        /**
         * The concept that constitutes the target of the application. In relationships, the target of the
         * relationship.
         *
         * @return a concept declaration or null
         */
        KimConcept getTarget();
    }

    List<KimConceptStatement> getChildren();

    Set<SemanticType> getType();

    String getUpperConceptDefined();

    String getAuthorityDefined();

    String getAuthorityRequired();

    List<KimConcept> getQualitiesAffected();

    List<KimConcept> getObservablesCreated();

    List<KimConcept> getTraitsConferred();

    List<KimConcept> getTraitsInherited();

    List<KimConcept> getRequiredExtents();

    List<KimConcept> getRequiredRealms();

    List<KimConcept> getRequiredAttributes();

    List<KimConcept> getRequiredIdentities();

    List<KimConcept> getEmergenceTriggers();

    KimConcept getDeclaredParent();

    /**
     * The semantics in the <code>within</code> clause.
     *
     * @return
     */
    KimConcept getDeclaredInherent();

//    List<KimRestriction> getRestrictions();

    boolean isAlias();

    boolean isAbstract();

    String getNamespace();

    String getUrn();

//    boolean isMacro();

    boolean isSubjective();

    boolean isSealed();

    List<PairImpl<KimConcept, DescriptionType>> getObservablesDescribed();

    List<ApplicableConcept> getSubjectsLinked();

    List<ApplicableConcept> getAppliesTo();

    String getDocstring();

}
