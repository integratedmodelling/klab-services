package org.integratedmodelling.klab.authentication;

import org.integratedmodelling.klab.api.authentication.ResourcePrivileges;
import org.integratedmodelling.klab.api.authentication.scope.Scope;
import org.integratedmodelling.klab.api.authentication.scope.ServiceScope;
import org.integratedmodelling.klab.api.authentication.scope.UserScope;
import org.integratedmodelling.klab.api.services.Authentication;
import org.integratedmodelling.klab.api.services.KlabService;
import org.integratedmodelling.klab.authentication.impl.AnonymousUser;
import org.integratedmodelling.klab.authentication.impl.LocalServiceScope;
import org.integratedmodelling.klab.authentication.impl.UserScopeImpl;
import org.springframework.stereotype.Service;

@Service
public class AuthenticationService implements Authentication {

    private static final long serialVersionUID = -7742687519379834555L;

    private String url;
    private String localName;

    @Override
    public boolean checkPermissions(ResourcePrivileges permissions, Scope scope) {
        // TODO
        return true;
    }

    @Override
    public Capabilities getCapabilities() {
        // TODO Auto-generated method stub
        return null;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getLocalName() {
        return localName;
    }

    public void setLocalName(String localName) {
        this.localName = localName;
    }

    @Override
    public UserScope getAnonymousScope() {
        return new UserScopeImpl(new AnonymousUser());
    }

    @Override
    public ServiceScope authenticateService(KlabService service) {
        // TODO
        return new LocalServiceScope(service);
    }

}
