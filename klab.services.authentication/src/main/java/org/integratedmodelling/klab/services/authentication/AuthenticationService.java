//package org.integratedmodelling.klab.services.authentication;
//
//import java.io.Serial;
//
//import org.integratedmodelling.klab.api.authentication.ResourcePrivileges;
//import org.integratedmodelling.klab.api.identities.UserIdentity;
//import org.integratedmodelling.klab.api.scope.Scope;
//import org.integratedmodelling.klab.api.scope.UserScope;
//import org.integratedmodelling.klab.api.services.Authentication;
//import org.integratedmodelling.klab.services.authentication.impl.AnonymousUser;
//import org.springframework.stereotype.Service;
//
//@Deprecated
//@Service
//public abstract class AuthenticationService implements Authentication {
//
//    @Serial
//    private static final long serialVersionUID = -7742687519379834555L;
//
//    private String url;
//    private String localName;
//
//    public AuthenticationService() {
//    }
//
//    @Override
//    public boolean checkPermissions(ResourcePrivileges permissions, Scope scope) {
//        // TODO
//        return true;
//    }
//
//    public String getUrl() {
//        return url;
//    }
//
//    public void setUrl(String url) {
//        this.url = url;
//    }
//
//    public String getLocalName() {
//        return localName;
//    }
//
//    public void setLocalName(String localName) {
//        this.localName = localName;
//    }
//
//    @Override
//    public UserScope getAnonymousScope() {
//        return authorizeUser(new AnonymousUser());
//    }
//
//    @Override
//    public abstract UserScope authorizeUser(UserIdentity user);
//
//}
