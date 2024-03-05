package org.integratedmodelling.klab.services.community;

import org.integratedmodelling.klab.api.scope.ServiceScope;
import org.integratedmodelling.klab.api.services.Community;
import org.integratedmodelling.klab.utilities.Utils;

import java.net.URL;

public class CommunityService implements Community {

    private static final long serialVersionUID = -7321738633095243998L;
    private String hardwareSignature = Utils.Names.getHardwareId();

    @Override
    public ServiceCapabilities capabilities() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public ServiceStatus status() {
        return null;
    }

    @Override
    public URL getUrl() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public String getLocalName() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public String getServiceName() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public ServiceScope scope() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public boolean shutdown() {
        // TODO Auto-generated method stub
        return false;
    }

}
