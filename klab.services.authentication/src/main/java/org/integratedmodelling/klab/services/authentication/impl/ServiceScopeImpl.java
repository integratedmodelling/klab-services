//package org.integratedmodelling.klab.services.authentication.impl;
//
//import org.integratedmodelling.klab.api.authentication.scope.ServiceScope;
//import org.integratedmodelling.klab.api.collections.Parameters;
//import org.integratedmodelling.klab.api.services.KlabService;
//import org.integratedmodelling.klab.services.authentication.impl.LocalServiceScope.LocalService;
//
//public class ServiceScopeImpl extends Monitor implements ServiceScope {
//
//    private static final long serialVersionUID = 605310381727313326L;
//
//    private Parameters<String> data = Parameters.create();
//    private KlabService service;
//
//    public ServiceScopeImpl(KlabService service) {
//        super(service);
//        this.service = service;;
//    }
//
//    protected ServiceScopeImpl(ServiceScopeImpl parent) {
//        super(parent.service);
//    }
//
//    @Override
//    public Parameters<String> getData() {
//        // TODO Auto-generated method stub
//        return null;
//    }
//
//    @Override
//    public boolean isLocal() {
//        // TODO Auto-generated method stub
//        return false;
//    }
//
//    @Override
//    public boolean isExclusive() {
//        // TODO Auto-generated method stub
//        return false;
//    }
//
//    @Override
//    public boolean isDedicated() {
//        // TODO Auto-generated method stub
//        return false;
//    }
//   
//
//}
