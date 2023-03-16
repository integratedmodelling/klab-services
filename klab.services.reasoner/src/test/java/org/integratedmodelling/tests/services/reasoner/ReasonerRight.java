package org.integratedmodelling.tests.services.reasoner;

import org.junit.jupiter.api.Test;

/**
 * Test every endpoint to ensure it does the right thing. Look at ReasonerWrong for the tests of
 * correct behavior when things are wrong instead.
 * 
 * @author Ferd
 *
 */
class ReasonerRight {

    @Test
    void resolveConcept() {
        // resolveConcept(String definition);
    }

    @Test
    void resolveObservable() {
        // Observable resolveObservable(String definition);
    }

    @Test
    void declareConcept() {
        // Concept declareConcept(KimConcept conceptDeclaration);
    }

    @Test
    void declareObservable() {
        // Observable declareObservable(KimObservable observableDeclaration);
    }

    @Test
    void subsumes() {
        // boolean subsumes(Semantics conceptImpl, Semantics other);
    }

    @Test
    void operands() {
        // Collection<Concept> operands(Semantics target);
    }

    @Test
    void children() {
        // Collection<Concept> children(Semantics target);
    }

    @Test
    void parents() {
        // Collection<Concept> parents(Semantics target);
    }

    @Test
    void observableBuilder() {
        // Builder observableBuilder(Observable observableImpl);
    }

    @Test
    void parent() {
        // Concept parent(Semantics c);
    }

    @Test
    void leastGeneralCommon() {
        // Concept leastGeneralCommon(Collection<Concept> cc);
    }

    @Test
    void allChildren() {
        // Collection<Concept> allChildren(Semantics target);
    }

    @Test
    void allParents() {
        // Collection<Concept> allParents(Semantics target);
    }

    @Test
    void closure() {
        // Collection<Concept> closure(Semantics target);
    }

    @Test
    void semanticDistance() {
        // int semanticDistance(Semantics target, Semantics other);
    }

    @Test
    void semanticDistanceContextual() {
        // int semanticDistance(Semantics target, Semantics other, Semantics context);
    }

    @Test
    void coreObservable() {
        // Concept coreObservable(Semantics first);
    }

    @Test
    void splitOperators() {
        // Pair<Concept, List<SemanticType>> splitOperators(Semantics concept);
    }

    @Test
    void assertedDistance() {
        // int assertedDistance(Semantics from, Semantics to);
    }

    @Test
    void roles() {
        // Collection<Concept> roles(Semantics concept);
    }

    @Test
    void hasRole() {
        // boolean hasRole(Semantics concept, Concept role);
    }

    @Test
    void directContext() {
        // Concept directContext(Semantics concept);
    }

    @Test
    void context() {
        // Concept context(Semantics concept);
    }

    @Test
    void directInherent() {
        // Concept directInherent(Semantics concept);
    }

    @Test
    void inherent() {
        // Concept inherent(Semantics concept);
    }

    @Test
    void directGoal() {
        // Concept directGoal(Semantics concept);
    }

    @Test
    void goal() {
        // Concept goal(Semantics concept);
    }

    @Test
    void directCooccurrent() {
        // Concept directCooccurrent(Semantics concept);
    }

    @Test
    void directCausant() {
        // Concept directCausant(Semantics concept);
    }

    @Test
    void directCaused() {
        // Concept directCaused(Semantics concept);
    }

    @Test
    void directAdjacent() {
        // Concept directAdjacent(Semantics concept);
    }

    @Test
    void directCompresent() {
        // Concept directCompresent(Semantics concept);
    }

    @Test
    void directRelativeTo() {
        // Concept directRelativeTo(Semantics concept);
    }

    @Test
    void cooccurrent() {
        // Concept cooccurrent(Semantics concept);
    }

    @Test
    void causant() {
        // Concept causant(Semantics concept);
    }

    @Test
    void caused() {
        // Concept caused(Semantics concept);
    }

    @Test
    void adjacent() {
        // Concept adjacent(Semantics concept);
    }

    @Test
    void compresent() {
        // Concept compresent(Semantics concept);
    }

    @Test
    void relativeTo() {
        // Concept relativeTo(Semantics concept);
    }

    @Test
    void traits() {
        // Collection<Concept> traits(Semantics concept);
    }

    @Test
    void identities() {
        // Collection<Concept> identities(Semantics concept);
    }

    @Test
    void attributes() {
        // Collection<Concept> attributes(Semantics concept);
    }

    @Test
    void realms() {
        // Collection<Concept> realms(Semantics concept);
    }

    @Test
    void baseParentTrait() {
        // Concept baseParentTrait(Semantics trait);
    }

    @Test
    void baseObservable() {
        // Concept baseObservable(Semantics observable);
    }

    @Test
    void rawObservable() {
        // Concept rawObservable(Semantics observable);
    }
    @Test
    void hasTrait() {
        // boolean hasTrait(Semantics type, Concept trait);
    }

    @Test
    void hasDirectTrait() {
        // boolean hasDirectTrait(Semantics type, Concept trait);
    }

    @Test
    void hasParentRole() {
        // boolean hasParentRole(Semantics o1, Concept t);
    }

    @Test
    void directTraits() {
        // Collection<Concept> directTraits(Semantics concept);
    }

    @Test
    void directRoles() {
        // Collection<Concept> directRoles(Semantics concept);
    }

    @Test
    void displayLabel() {
        // String displayLabel(Semantics concept);
    }

    @Test
    void style() {
        // String style(Concept concept);
    }

    @Test
    void observationClass() {
        // Class<? extends Observation> observationClass(Semantics observable);
    }

    @Test
    void observableType() {
        // SemanticType observableType(Semantics observable, boolean acceptTraits);
    }

    @Test
    void relationshipSource() {
        // Concept relationshipSource(Semantics relationship);
    }

    @Test
    void relationshipSources() {
        // Collection<Concept> relationshipSources(Semantics relationship);
    }

    @Test
    void relationshipTarget() {
        // Concept relationshipTarget(Semantics relationship);
    }

    @Test
    void relationshipTargets() {
        // Collection<Concept> relationshipTargets(Semantics relationship);
    }

    @Test
    void negated() {
        // Concept negated(Concept concept);
    }

    @Test
    void satisfiable() {
        // boolean satisfiable(Semantics ret);
    }

    @Test
    void domain() {
        // Semantics domain(Semantics conceptImpl);
    }

    @Test
    void applicableObservables() {
        // Collection<Concept> applicableObservables(Concept main);
    }

    @Test
    void describedType() {
        // Concept describedType(Semantics concept);
    }

    @Test
    void compatible() {
        // boolean compatible(Semantics concept, Semantics other);
    }

    @Test
    void contextuallyCompatible() {
        // boolean contextuallyCompatible(Semantics focus, Semantics context1, Semantics context2);
    }

    @Test
    void occurrent() {
        // boolean occurrent(Semantics concept);
    }

    @Test
    void affectedBy() {
        // boolean affectedBy(Semantics concept, Semantics affecting);
    }
}
