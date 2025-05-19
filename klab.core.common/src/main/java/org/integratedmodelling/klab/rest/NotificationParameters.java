package org.integratedmodelling.klab.rest;

import java.util.Optional;

import org.integratedmodelling.klab.api.collections.Pair;
import org.integratedmodelling.klab.rest.HubNotificationMessage.ExtendedInfo;
import org.integratedmodelling.klab.rest.HubNotificationMessage.Type;

public abstract class NotificationParameters {
    Pair<ExtendedInfo, Object>[] info;
    Optional<Type> type;
    
    public NotificationParameters(Pair<ExtendedInfo, Object>[] info, Type type) {
        this.info = info;
        this.type = Optional.ofNullable(type);
    }
    
}
