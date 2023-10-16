package org.integratedmodelling.tests.services.reasoner;

import org.integratedmodelling.klab.api.knowledge.Concept;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.springframework.util.Assert;

import java.util.EnumSet;
import java.util.Set;
import java.util.function.Consumer;

class ReasonerServiceTest extends ReasonerTestSetup {


    /**
     * TODO basic idea for testing unit for an observable. Anything not null must be there as expected.
     * Builder actions may be added in each record.
     *
     * @param observable
     * @param expected
     * @param expectAbstract
     */
    record ObservableTestData(String observable, Set<SemanticType> expected, boolean expectAbstract) {

        void testConsistency() {
            // TODO try them all

            // TODO use the reasoner to validate and expect same result
        }

        void testBuilding() {
            // build from the base observable definition, test consistency of the result and compare with
            // the main
        }

    }

    record ConceptData(String concept, Set<SemanticType> types) {
    }

    /**
     * These include the string declaration of an observable and a builder that will reconstruct it based on
     * its components and its base observable.
     *
     * @param observableDefinition the k.IM definition of an observable
     * @param builderConfigurator  a function that starts with a builder pre-configured with the
     *                             {@link Observable#promote(Concept)} of the {@link
     *                             <p>
     *                             <p>
     *                             org.integratedmodelling.klab.api.services.Reasoner#baseObservable
     *                             (Semantics)} returned when applied to the {@link Observable} built from the
     *                             string definition.
     */
    record RebuilderData(String observableDefinition, Consumer<Observable.Builder> builderConfigurator) {
    }

    /**
     * Like the above but specifying the base concept so that unary operators can be tested
     *
     * @param observableDefinition
     * @param baseConcept
     * @param builderConfigurator
     */
    record BuilderData(String observableDefinition, String baseConcept,
                       Consumer<Observable.Builder> builderConfigurator) {
    }

    /**
     * All individual concepts that participate in the observables below
     * TODO add the expected types and inheritance
     */
    private static ConceptData[] testConcepts = new ConceptData[]{
            new ConceptData("geography:Elevation", EnumSet.of(SemanticType.LENGTH)),
            new ConceptData("infrastructure:City", EnumSet.of(SemanticType.SUBJECT)),
            new ConceptData("landcover:Urban", EnumSet.of(SemanticType.PREDICATE)),
            new ConceptData("im:Height", EnumSet.of(SemanticType.LENGTH)),
            new ConceptData("hydrology:Watershed", EnumSet.of(SemanticType.SUBJECT))
    };

    private Concept c(String concept) {
        return reasonerService.resolveConcept(concept);
    }

    private RebuilderData[] testRebuilders = new RebuilderData[]{
            new RebuilderData("geography:Elevation of hydrology:Watershed",
                    (builder) -> builder.of(c("hydrology:Watershed")))
    };

    private BuilderData[] testBuilders = new BuilderData[]{
            new BuilderData("geography:Elevation of hydrology:Watershed",
                    "geography:Elevation",
                    (builder) -> builder.of(c("hydrology:Watershed")))
    };

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
        if (reasonerService == null) {
            prepare();
        }
    }

    @AfterEach
    void tearDown() {
        if (reasonerService != null) {
            shutdown();
        }
    }


    /**
     * First test: basic checks for the concepts
     */
    @Test
    @Order(1)
    void resolveConcept() {
        for (var declaration : testConcepts) {
            var concept = reasonerService.resolveConcept(declaration.concept);
            Assert.notNull(concept, "Concept " + declaration + " did not parse correctly");
            for (SemanticType type : declaration.types) {
                Assert.isTrue(concept.getType().contains(type), "Concept " + concept + " does not contain " +
                        "one or more of " + declaration.types);
            }
        }
    }

    /**
     * Then resolve the observables
     */
    @Order(2)
    @Test
    void resolveObservable() {
    }

    void testRebuilding() {
        // TODO take apart each observable and rebuild it using the builder
        for (var odef : testObservables) {
            var observable = reasonerService.resolveObservable(odef);
        }
    }

    @Test
    void rebuilderTests() {
        for (var bt : testRebuilders) {
            Observable observable = reasonerService.resolveObservable(bt.observableDefinition);
            Assert.notNull(observable, "observable '" + bt.observableDefinition + "' failed to parse");
            var builder =
                    Observable.promote(reasonerService.baseObservable(observable)).builder(resourcesService.scope());
            bt.builderConfigurator.accept(builder);
            Observable expected = builder.build();
            // this does not compare units or currencies or names
            Assert.isTrue(expected.equals(observable),
                    "Observable built is not equal for " + bt.observableDefinition);
            // this compares everything
            Assert.isTrue(expected.getUrn().equals(observable.getUrn()), "Observable builder: URNs differ: " +
                    "'" + expected.getUrn() + "' != '" + observable.getUrn() + "'");
        }
    }

    @Test
    void builderTests() {
        for (var bt : testBuilders) {
            Observable observable = reasonerService.resolveObservable(bt.observableDefinition);
            Assert.notNull(observable, "observable '" + bt.observableDefinition + "' failed to parse");
            var builder =
                    Observable.promote(reasonerService.resolveConcept(bt.baseConcept)).builder(resourcesService.scope());
            bt.builderConfigurator.accept(builder);
            Observable expected = builder.build();
            // this does not compare units or currencies or names
            Assert.isTrue(expected.equals(observable),
                    "Observable built is not equal for " + bt.observableDefinition);
            // this compares everything
            Assert.isTrue(expected.getUrn().equals(observable.getUrn()), "Observable builder: URNs differ: " +
                    "'" + expected.getUrn() + "' != '" + observable.getUrn() + "'");
        }
    }

    @Test
    void derived() {
    }

    @Test
    void defineConcept() {
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