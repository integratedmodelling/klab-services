package org.integratedmodelling.klab.runtime.computation

import org.integratedmodelling.klab.api.services.Reasoner
import org.integratedmodelling.klab.api.services.Resolver
import org.integratedmodelling.klab.api.services.ResourcesService
import org.integratedmodelling.klab.api.services.RuntimeService
import org.integratedmodelling.klab.services.scopes.ServiceContextScope

trait KlabOps {

    ServiceContextScope scope;

    @Lazy Reasoner reasoner = { scope.getService(Reasoner.class) }()
    @Lazy ResourcesService resourcesService = { scope.getService(ResourcesService.class) }()
    @Lazy ResourcesService resourcesServices = { scope.getServices(ResourcesService.class) }()
    @Lazy Resolver resolver = { scope.getService(Resolver.class) }()
    @Lazy RuntimeService runtime = { scope.getService(RuntimeService.class) }()
    @Lazy RuntimeService runtimes = { scope.getServices(RuntimeService.class) }()



}