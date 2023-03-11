//package org.integratedmodelling.klab.api.services;
//
//import org.integratedmodelling.klab.api.authentication.scope.Scope;
//
///**
// * A federated service is a service that joins a federation of other services that it knows about.
// * 
// * @author Ferd
// *
// */
//public interface KlabFederatedService extends KlabService {
//
//    interface FederatedServiceCapabilities extends ServiceCapabilities {
//        boolean isExclusive();
//        boolean isDedicated();
//    }
//    
//    @Override
//    FederatedServiceCapabilities getCapabilities();
//    
//    /**
//     * Normal mode of operation is federated, i.e. all the methods will return a merge of own
//     * content with that of the federation. In case exclusive operation is needed, using only one
//     * server and ignoring the federation, the server returned from this method should be used. The
//     * capabilities should reflect the exclusive status.
//     * 
//     * @param <T>
//     * @return
//     */
//    <T extends KlabFederatedService> T exclusive(Scope scope);
//
//    /**
//     * A dedicated service is one that is exclusive and is not used by others to provide content.
//     * Administration operations should be done on the service in dedicated mode.
//     * 
//     * @param <T>
//     * @return
//     */
//    <T extends KlabFederatedService> T dedicated(Scope scope);
//
//}
