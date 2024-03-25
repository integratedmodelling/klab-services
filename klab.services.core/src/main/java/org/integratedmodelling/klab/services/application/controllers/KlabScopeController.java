package org.integratedmodelling.klab.services.application.controllers;


import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.klab.api.ServicesAPI;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.services.runtime.Message;
import org.integratedmodelling.klab.services.application.ServiceNetworkedInstance;
import org.integratedmodelling.klab.services.application.security.EngineAuthorization;
import org.integratedmodelling.klab.services.application.security.Role;
import org.integratedmodelling.klab.services.application.security.ServiceAuthorizationManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.Map;

/**
 * Handshaking after authentication, will notify a scope from the remote side and provide it with a peer at
 * the service side. If the scope is using the secret or the principal is an administrator and requests it,
 * the scopes will be linked using websockets so that send() at one side will trigger send() with the same
 * parameters at the other.
 */
@RestController
@Secured(Role.USER)
public class KlabScopeController {

    @Autowired
    ServiceNetworkedInstance<?> service;

    @Autowired
    ServiceAuthorizationManager scopeManager;

    @Autowired
    private SimpMessagingTemplate webSocket;

    @MessageMapping(ServicesAPI.MESSAGE)
    //    @SendTo("/klab")
    public void handleMessage(Map<?, ?> payload) {

        var message = Utils.Json.convertMessage(payload, Message.Provenance.Forwarded);
        // TODO serviceScope().post() - find the scope for the ID and post it there. Scope must exist.
        //        scopeManager.


        // TODO IF RESPONSE IS RETURNED IT GOES BACK TO THE CHANNEL. Should be done asynchronously using
        //  the template except when handshaking
        System.out.println("MESSAGE " + message);

    }

    /**
     * This gets called before scope pairing is attempted at client side. If a valid "Websockets URL,Channel"
     * is returned, the server accepts the pairing and the scope will be let in. In this implementation, this
     * is contingent to the client using the secret token for authorization, which means the client is on the
     * same machine.
     * <p>
     * Note: the names in the path variables needs to stay specified because at least in Idea, the parameter
     * names are apparently not available at runtime unless some flags are passed.
     *
     * @param scopeType the type of scope
     * @param scopeId
     * @param principal
     * @return the websockets URL to use for communication with this scope with the comma-separated channel to
     * subscribe to appended, or an empty string if the feature is unavailable.
     */
    @GetMapping(ServicesAPI.SCOPE.REGISTER)
    public String registerScope(@PathVariable("scopeType") Scope.Type scopeType,
                                @PathVariable("scopeId") String scopeId, Principal principal) {

        if (principal instanceof EngineAuthorization engineAuthorization) {
            if (scopeManager.registerScope(scopeType, scopeId, engineAuthorization)) {
                // TODO (?) we may want to register a specific topic/channel linked to the scope using the
                //  scope ID. Or if the channel is only one, its name should probably be generated on startup.
                return service.klabService().getUrl().toString().replaceFirst(service.klabService().getUrl().getProtocol(), "ws")
                        + ServicesAPI.MESSAGE + ",/klab";
            }
        }
        return null;
    }


    @GetMapping(ServicesAPI.SCOPE.DISPOSE)
    public boolean disposeScope(@PathVariable String scopeId, Principal principal) {
        if (principal instanceof EngineAuthorization engineAuthorization) {
            return scopeManager.unregisterScope(scopeId, engineAuthorization);
        }
        return false;
    }

}
