package org.integratedmodelling.common.lang.kim;

import org.integratedmodelling.klab.api.data.Metadata;
import org.integratedmodelling.klab.api.lang.kim.KlabStatement;
import org.integratedmodelling.klab.api.services.runtime.Notification;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;

/**
 * 
 * @author Ferd
 *
 */
public abstract class KimStatementImpl extends KimAssetImpl implements KlabStatement {

    @Serial
    private static final long serialVersionUID = -7273214821906819558L;

    private Metadata metadata = Metadata.create();
    private String namespace;
    private Scope scope = Scope.PUBLIC;
    private List<Notification> notifications = new ArrayList<>();

    public KimStatementImpl() {

    }
    protected KimStatementImpl(KimStatementImpl other) {
        super(other);
        this.metadata = other.metadata;
        this.namespace = other.namespace;
        this.scope = other.scope;
        this.notifications.addAll(other.notifications);
    }

    @Override
    public Metadata getMetadata() {
        return this.metadata;
    }

////    @Override
////    public void visit(Visitor visitor) {
////        // TODO Auto-generated method stub
////
//    }

    @Override
    public String getNamespace() {
        return this.namespace;
    }

    @Override
    public Scope getScope() {
        return this.scope;
    }

    public void setMetadata(Metadata metadata) {
        this.metadata = metadata;
    }

    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    public void setScope(Scope scope) {
        this.scope = scope;
    }
    @Override
    public List<Notification> getNotifications() {
        return notifications;
    }

    public void setNotifications(List<Notification> notifications) {
        this.notifications = notifications;
    }

}
