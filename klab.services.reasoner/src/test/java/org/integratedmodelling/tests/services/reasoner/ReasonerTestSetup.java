package org.integratedmodelling.tests.services.reasoner;

import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.exceptions.KlabIllegalStateException;
import org.integratedmodelling.klab.api.identities.UserIdentity;
import org.integratedmodelling.klab.api.lang.kactors.KActorsBehavior;
import org.integratedmodelling.klab.api.scope.ServiceScope;
import org.integratedmodelling.klab.api.scope.SessionScope;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.api.services.*;
import org.integratedmodelling.klab.indexing.Indexer;
import org.integratedmodelling.klab.services.authentication.AuthenticationService;
import org.integratedmodelling.klab.services.authentication.impl.AnonymousUser;
import org.integratedmodelling.klab.services.authentication.impl.LocalServiceScope;
import org.integratedmodelling.klab.services.authentication.impl.Monitor;
import org.integratedmodelling.klab.services.reasoner.ReasonerService;
import org.integratedmodelling.klab.services.resources.ResourcesProvider;

import java.util.Collection;
import java.util.Collections;

/**
 * Inherit from this one to build the bare environment to test the reasoner. Call prepare() in an initializer
 * and use the services from this class.
 */
class ReasonerTestSetup {

    protected static ResourcesProvider resourcesService;
    protected static ReasonerService reasonerService;
    private static Indexer indexingService;
    private static ServiceScope scope = null;

    private static class AnonymousAuthenticationService extends AuthenticationService {

        AnonymousScope userScope = null;

        class AnonymousScope extends Monitor implements UserScope {

            UserIdentity user = new AnonymousUser();
            Parameters<String> data = Parameters.create();

            @Override
            public String getId() {
                return "anonymous";
            }

            @Override
            public Parameters<String> getData() {
                return data;
            }

            @Override
            public KActorsBehavior.Ref getAgent() {
                return null;
            }

            @Override
            public <T extends KlabService> T getService(Class<T> serviceClass) {
                if (serviceClass.isAssignableFrom(Reasoner.class)) {
                    return (T) reasonerService;
                } else if (serviceClass.isAssignableFrom(ResourcesService.class)) {
                    return (T) resourcesService;
                } else if (serviceClass.isAssignableFrom(Resolver.class)) {
                    throw new KlabIllegalStateException("Resolver access in reasoner test");
                } else if (serviceClass.isAssignableFrom(RuntimeService.class)) {
                    throw new KlabIllegalStateException("Runtime access in reasoner test");
                }
                return null;
            }

            @Override
            public <T extends KlabService> Collection<T> getServices(Class<T> serviceClass) {
                return Collections.singleton(getService(serviceClass));
            }

            @Override
            public Status getStatus() {
                return Status.STARTED;
            }

            @Override
            public void setStatus(Status status) {
            }

            @Override
            public void setData(String key, Object value) {

            }

            @Override
            public void stop() {

            }

            @Override
            public UserIdentity getUser() {
                return user;
            }

            @Override
            public SessionScope runSession(String sessionName) {
                throw new KlabIllegalStateException("Session access in reasoner test");
            }

            @Override
            public SessionScope run(String behaviorName, KActorsBehavior.Type behaviorType) {
                throw new KlabIllegalStateException("Session access in reasoner test");
            }

            @Override
            public void switchService(KlabService service) {

            }
        }

        @Override
        public UserScope authorizeUser(UserIdentity user) {
            if (userScope == null) {
                userScope = new AnonymousScope();
            }
            return userScope;
        }
    }

    ;

    public static void prepare() {

        AnonymousAuthenticationService authenticationService = new AnonymousAuthenticationService();

//        Services.INSTANCE.registerAuthority(new GBIFAuthority());
//        Services.INSTANCE.registerAuthority(new IUPACAuthority());
//        Services.INSTANCE.registerAuthority(new CaliperAuthority());
        scope = new LocalServiceScope(Reasoner.class) {

            @Override
            public String getId() {
                return Reasoner.class.getCanonicalName();
            }

            // no agents for services in this implementation
            @Override
            public KActorsBehavior.Ref getAgent() {
                return null;
            }

            @SuppressWarnings("unchecked")
            @Override
            public <T extends KlabService> T getService(Class<T> serviceClass) {
                if (serviceClass.isAssignableFrom(Reasoner.class)) {
                    return (T) reasonerService;
                } else if (serviceClass.isAssignableFrom(ResourcesService.class)) {
                    return (T) resourcesService;
                } else if (serviceClass.isAssignableFrom(Resolver.class)) {
                    throw new KlabIllegalStateException("Resolver access in reasoner test");
                } else if (serviceClass.isAssignableFrom(RuntimeService.class)) {
                    throw new KlabIllegalStateException("Runtime access in reasoner test");
                }
                return null;
            }

            @Override
            public void stop() {
                // do nothing
            }
        };

        reasonerService = new ReasonerService(authenticationService, scope, "Test reasoner");
        resourcesService = new ResourcesProvider(authenticationService, scope, "Test resources");
        resourcesService.initializeService();
        reasonerService.initializeService();
        // ensure the test worldview is there or set from git repo
        // TODO use a dedicated branch or a dedicated test worldview project
        resourcesService.importProject("worldview", "https://bitbucket.org/integratedmodelling/im.git#develop", false);
        var worldview = resourcesService.getWorldview();
        reasonerService.loadKnowledge(worldview, scope);
    }

    public static void shutdown() {
        resourcesService.shutdown();
        reasonerService.shutdown();
    }

}
