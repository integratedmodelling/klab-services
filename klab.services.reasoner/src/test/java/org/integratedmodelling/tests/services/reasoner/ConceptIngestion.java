package org.integratedmodelling.tests.services.reasoner;

import java.util.List;

import org.integratedmodelling.klab.api.authentication.scope.Scope;
import org.integratedmodelling.klab.api.authentication.scope.ServiceScope;
import org.integratedmodelling.klab.api.authentication.scope.UserScope;
import org.integratedmodelling.klab.api.exceptions.KServiceAccessException;
import org.integratedmodelling.klab.api.identities.UserIdentity;
import org.integratedmodelling.klab.api.knowledge.Concept;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.services.KlabService;
import org.integratedmodelling.klab.configuration.Services;
import org.integratedmodelling.klab.indexing.Indexer;
import org.integratedmodelling.klab.services.authentication.AuthenticationService;
import org.integratedmodelling.klab.services.authentication.impl.LocalServiceScope;
import org.integratedmodelling.klab.services.reasoner.ReasonerService;
import org.integratedmodelling.klab.services.reasoner.authorities.CaliperAuthority;
import org.integratedmodelling.klab.services.reasoner.authorities.GBIFAuthority;
import org.integratedmodelling.klab.services.reasoner.authorities.IUPACAuthority;
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
    private static Indexer indexingService;
    private static Scope scope = null;

    @BeforeAll
    public static void prepare() {
        
        AuthenticationService authenticationService = new AuthenticationService() {

            @Override
            public UserScope authorizeUser(UserIdentity user) {
                // TODO Auto-generated method stub
                return null;
            }

            @Override
            public ServiceScope authorizeService(KlabService service) {
                // TODO Auto-generated method stub
                return new LocalServiceScope(service) {
                    
                    @Override
                    public <T extends KlabService> T getService(Class<T> serviceClass) {
                        throw new KServiceAccessException(serviceClass);
                    }
                };
            }
            
        };
        scope = authenticationService.getAnonymousScope();
        reasonerService = new ReasonerService(authenticationService,
                resourcesService = new ResourcesService(authenticationService), indexingService = new Indexer());
        Services.INSTANCE.registerAuthority(new GBIFAuthority());
        Services.INSTANCE.registerAuthority(new IUPACAuthority());
        Services.INSTANCE.registerAuthority(new CaliperAuthority());
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
        reasonerService.loadKnowledge(resourcesService.projects(List.of("im"), scope), scope);
        // File output = Configuration.INSTANCE.getExportFile("im.owl");
        // OWL.INSTANCE.getOntology("im").write(output, false);
    }

}
