package org.integratedmodelling.tests.services.reasoner;

import org.integratedmodelling.klab.api.authentication.scope.Scope;
import org.integratedmodelling.klab.api.knowledge.Concept;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.authentication.AuthenticationService;
import org.integratedmodelling.klab.services.reasoner.ReasonerService;
import org.integratedmodelling.klab.services.resources.ResourcesService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ConceptIngestion {

    /*
     * TODO substitute with online service filled in with as many observable use cases as possible
     */
    private static String[] testObservables = {"geography:Elevation in m", "geography:Elevation optional",
            "geography:Elevation in m optional", "any geography:Elevation in m", "geography:Elevation in m > 100",
            "geography:Elevation in m by landcover:LandCoverType"};

    private static String[] testConcepts = {"geography:Elevation in m", "geography:Elevation optional",
            "geography:Elevation in m optional", "any geography:Elevation in m", "geography:Elevation in m > 100",
            "geography:Elevation in m by landcover:LandCoverType"};

    private static ResourcesService resourcesService;
    private static ReasonerService reasonerService;
    private static Scope scope = null;

    @BeforeAll
    public static void prepare() {
        AuthenticationService authenticationService = new AuthenticationService();
        scope = authenticationService.getAnonymousScope();
        reasonerService = new ReasonerService(authenticationService,
                resourcesService = new ResourcesService(authenticationService));
    }

    @Test
    void concepts() {
        for (String c : testConcepts) {
            Concept concept = reasonerService.resolveConcept(c);
            System.out.println(concept);
        }
    }

    @Test
    void observables() {
        for (String concept : testObservables) {
            Observable observable = reasonerService.resolveObservable(concept);
            System.out.println(observable);
        }
    }

    @Test
    void worldview() {
        reasonerService.loadKnowledge(resourcesService.worldview(scope), scope);
    }

}
