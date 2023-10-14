package org.integratedmodelling.klab.api.services;

import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.scope.ContextScope;

/**
 * The core library is a service that provides builders for service calls for k.LAB core functions. The
 * service must be available to the reasoner (to implement
 * {@link Reasoner#inferStrategies(Observable, ContextScope)}) and to the resolver (to validate the calls to
 * insert in dataflows). The runtime must also provide the functors using
 * {@link org.integratedmodelling.klab.api.services.runtime.extension.Library} and related annotations,
 * although they are not required to be part of a class implementing this interface.
 */
public interface CoreLibrary extends Service {

    default String getServiceName() {
        return "klab.corelibrary.service";
    }
}
