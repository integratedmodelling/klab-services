package org.integratedmodelling.tests.services.reasoner;

import org.integratedmodelling.klab.api.knowledge.Concept;
import org.integratedmodelling.klab.api.knowledge.DescriptionType;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.services.reasoner.ReasonerService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ReasonerServiceTest {

    ReasonerService reasoner;

    /**
     * TODO basic idea for testing unit for an observable. Anything not null must be there as expected.
     * Builder actions may be added in each record.
     *
     * @param observable
     * @param expected
     * @param notExpected
     * @param expectedContext
     * @param expectedBaseType
     * @param expectedInherent
     */
    record ObservableTestData(String observable, Set<SemanticType> expected, Set<SemanticType> notExpected,
                              Concept expectedContext, Concept expectedBaseType, Concept expectedInherent,
                              DescriptionType expectedDescriptionType, Collection<Concept> expectedIdentities,
                              Collection<Concept> expectedRealms, Collection<Concept> expectedAttributes,
                              Collection<Concept> expectedGenerics, boolean expectAbstract) {

        void testConsistency() {
            // TODO try them all

            // TODO use the reasoner to validate and expect same result
        }

        void testBuilding() {
            // build from the base observable definition, test consistency of the result and compare with
            // the main
        }

    }

    private static String[] testObservables = new String[]{
            "geography:Elevation in m",
            "geography:Elevation optional",
            "geography:Elevation in m optional",
            "any geography:Elevation in m",
            "geography:Elevation in m > 100",
            "geography:Elevation in m by landcover:LandCoverType",
            "landcover:Urban of each infrastructure:Road",
            "landcover:Urban of infrastructure:Road",
            "im:Height of biology:TreeIndividual",
            "geography:Elevation in m > 100",
            "geography:Elevation in m > 100",
            "geography:Elevation in m > 100",
            "geography:Elevation in m > 100",
            "geography:Elevation in m > 100",
            "geography:Elevation in m > 100",
            "geography:Elevation in m > 100",
            "geography:Elevation in m > 100",
            "geography:Elevation in m > 100",
            "geography:Elevation in m > 100",
            "geography:Elevation in m > 100",
            "geography:Elevation in m > 100",
            "geography:Elevation in m > 100",
            "geography:Elevation in m > 100",
            "geography:Elevation in m > 100",
            "geography:Elevation in m > 100",
            };

    @BeforeEach
    void setUp() {
        // set up resolver and reasoner with stable test worldview
    }

    @AfterEach
    void tearDown() {
    }

    @Test
    void derived() {
    }

    @Test
    void defineConcept() {
    }

    @Test
    void resolveConcept() {
    }

    @Test
    void resolveObservable() {
    }

    @Test
    void operands() {
    }

    @Test
    void children() {
    }

    @Test
    void emergentResolvables() {
    }

    @Test
    void parents() {
    }

    @Test
    void allChildren() {
    }

    @Test
    void allParents() {
    }

    @Test
    void closure() {
    }

    @Test
    void resolves() {
    }

    @Test
    void semanticDistance() {
    }

    @Test
    void testSemanticDistance() {
    }

    @Test
    void testSemanticDistance1() {
    }

    @Test
    void coreDistance() {
    }

    @Test
    void coreObservable() {
    }

    @Test
    void splitOperators() {
    }

    @Test
    void describedType() {
    }

    @Test
    void traits() {
    }

    @Test
    void assertedDistance() {
    }

    @Test
    void hasTrait() {
    }

    @Test
    void roles() {
    }

    @Test
    void hasRole() {
    }

    @Test
    void directContext() {
    }

    @Test
    void context() {
    }

    @Test
    void directInherent() {
    }

    @Test
    void inherent() {
    }

    @Test
    void directGoal() {
    }

    @Test
    void goal() {
    }

    @Test
    void directCooccurrent() {
    }

    @Test
    void directCausant() {
    }

    @Test
    void directCaused() {
    }

    @Test
    void directAdjacent() {
    }

    @Test
    void directCompresent() {
    }

    @Test
    void directRelativeTo() {
    }

    @Test
    void cooccurrent() {
    }

    @Test
    void causant() {
    }

    @Test
    void caused() {
    }

    @Test
    void adjacent() {
    }

    @Test
    void compresent() {
    }

    @Test
    void relativeTo() {
    }

    @Test
    void displayLabel() {
    }

    @Test
    void displayName() {
    }

    @Test
    void style() {
    }

    @Test
    void capabilities() {
    }

    @Test
    void setCapabilities() {
    }

    @Test
    void identities() {
    }

    @Test
    void attributes() {
    }

    @Test
    void realms() {
    }

    @Test
    void baseParentTrait() {
    }

    @Test
    void hasDirectTrait() {
    }

    @Test
    void hasDirectRole() {
    }

    @Test
    void directTraits() {
    }

    @Test
    void directAttributes() {
    }

    @Test
    void directIdentities() {
    }

    @Test
    void directRealms() {
    }

    @Test
    void negated() {
    }

    @Test
    void observableType() {
    }

    @Test
    void relationshipSource() {
    }

    @Test
    void relationshipSources() {
    }

    @Test
    void relationshipTarget() {
    }

    @Test
    void relationshipTargets() {
    }

    @Test
    void satisfiable() {
    }

    @Test
    void applicableObservables() {
    }

    @Test
    void directRoles() {
    }

    @Test
    void loadKnowledge() {
    }

    @Test
    void getUrl() {
    }

    @Test
    void setUrl() {
    }

    @Test
    void getLocalName() {
    }

    @Test
    void setLocalName() {
    }

    @Test
    void subsumes() {
    }

    @Test
    void domain() {
    }

    @Test
    void declareConcept() {
    }

    @Test
    void declareObservable() {
    }

    @Test
    void compatible() {
    }

    @Test
    void testCompatible() {
    }

    @Test
    void hasParentRole() {
    }

    @Test
    void contextuallyCompatible() {
    }

    @Test
    void occurrent() {
    }

    @Test
    void affectedOrCreated() {
    }

    @Test
    void affected() {
    }

    @Test
    void created() {
    }

    @Test
    void affectedBy() {
    }

    @Test
    void createdBy() {
    }

    @Test
    void baseObservable() {
    }

    @Test
    void parent() {
    }

    @Test
    void compose() {
    }

    @Test
    void rawObservable() {
    }

    @Test
    void observableBuilder() {
    }

    @Test
    void setWorldviewPeer() {
    }

    @Test
    void build() {
    }

    @Test
    void leastGeneral() {
    }

    @Test
    void leastGeneralCommon() {
    }

    @Test
    void registerEmergent() {
    }

    @Test
    void declare() {
    }

    @Test
    void registerConcept() {
    }

    @Test
    void rolesFor() {
    }

    @Test
    void impliedRole() {
    }

    @Test
    void impliedRoles() {
    }

    @Test
    void semanticSearch() {
    }

    @Test
    void shutdown() {
    }

    @Test
    void inferStrategies() {
    }

    @Test
    void hasDistributedInherency() {
    }

    @Test
    void collectComponents() {
    }

    @Test
    void replaceComponent() {
    }

    @Test
    void buildConcept() {
    }

    @Test
    void buildObservable() {
    }

    @Test
    void exportNamespace() {
    }
}