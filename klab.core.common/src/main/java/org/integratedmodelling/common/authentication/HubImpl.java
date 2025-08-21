package org.integratedmodelling.common.authentication;

import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.identities.Hub;
import org.integratedmodelling.klab.api.identities.Identity;
import org.integratedmodelling.klab.api.identities.PartnerIdentity;
import org.integratedmodelling.klab.rest.HubReference;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;

public class HubImpl extends IdentityImpl implements Hub {
    // TODO flesh out
    String name;
    PartnerIdentity parent;
    List<String> urls = new ArrayList<>();
    Date bootTime = new Date();
    boolean online;
    int retryPeriod = 15;

    private long lastCheck = System.currentTimeMillis();

    private Utils.Http.Client client;
    Parameters<String> globalState = Parameters.create();

/*    @Override
    public Parameters<String> getState() {
        return globalState;
    }*/

    public HubImpl(String name, PartnerIdentity owner) {
        this.name = name;
        this.parent = owner;
    }

    public HubImpl(HubReference hub) {
//        PartnerIdentity partner = Authentication.INSTANCE.requirePartner(hub.getPartner());
        this.name = hub.getId();
        this.urls.addAll(hub.getUrls());
//        this.parent = partner;
    }

    public HubImpl() {

    }

    /**
     * Force a check for online status, set the online flag and
     * return the result. Should be executed automatically every
     * retryPeriod minutes unless the server is offline from construction (retry
     * period will be 0 in that case).
     *
     * @return true if online
     */
/*    public boolean ping() {
        this.online = false;
        for (String url : urls) {
            if ((this.online = client.ping(url))) {
                break;
            }
        }
        this.lastCheck = System.currentTimeMillis();
        return this.online;
    }*/

 /*   @Override
    public String getName() {
        return this.name;
    }

    @Override
    public Date getBootTime() {
        return bootTime;
    }

    @Override
    public Client getClient() {
        if (this.client == null) {
            this.client = Client.create().onBehalfOf(this);
            this.client.setUrl(this.urls.toArray(new String[this.urls.size()]));
        }
        return this.client;
    }

    @Override
    public Monitor getMonitor() {
        // TODO Auto-generated method stub
        return null;
    }*/


    @Override
    public Identity.Type getIdentityType() {
        return Identity.Type.IM_PARTNER;
    }

    @Override
    public String getId() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public boolean is(Type type) {
        return type == getIdentityType();
    }

/*    @Override
    public <T extends Identity> T getParentIdentity(Class<T> type) {
        return Identity.findParent(this, type);
    }

    @Override
    public PartnerIdentity getParentIdentity() {
        return parent;
    }

    @Override
    public boolean isOnline() {
        if ((System.currentTimeMillis() - lastCheck) > retryPeriod * (1000 * 60)) {
            return ping();
        }
        return this.online;
    }

    @Override
    public Collection<String> getUrls() {
        return urls;
    }*/

    public void setOnline(boolean b) {
        this.online = b;
    }

/*    @Override
    public boolean stop() {
        // TODO Auto-generated method stub
        return false;
    }*/
}
