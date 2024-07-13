package org.integratedmodelling.klab.services.runtime.server.controllers;

import jakarta.servlet.http.HttpServletRequest;
import org.integratedmodelling.klab.api.ServicesAPI;
import org.integratedmodelling.klab.services.runtime.server.objects.Context;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.ArrayList;
import java.util.List;

/**
 * Provides the GraphQL support for context access and inspection
 */
@Controller
public class RuntimeServerContextController {

    private List<Context> demoContexts = new ArrayList<>();
    @Autowired
    private HttpServletRequest request;

    public RuntimeServerContextController() {
        demoContexts.add(new Context("dio1", "dio uno", null, null));
        demoContexts.add(new Context("dio2", "dio due", null, null));
        demoContexts.add(new Context("dio3", "dio tre", null, null));
        demoContexts.add(new Context("dio4", "dio quattro", null, null));
        demoContexts.add(new Context("dio5", "dio cinque", null, null));
        demoContexts.add(new Context("dio6", "dio sei", null, null));
    }

    @SchemaMapping(typeName="Query", value="contexts")
    public List<Context> contexts(Principal principal) {
        System.out.println("PRINCIPAL IS " + principal);
        return /*id == null ?*/ demoContexts/* :
               demoContexts.stream().filter(context -> context.id().equals(id)).toList()*/;
    }

    @SchemaMapping(typeName="Query", value="context")
    public Context context(Principal principal) {
        var scopeHeader = request.getHeader(ServicesAPI.SCOPE_HEADER);
        System.out.println("PRINCIPAL IS " + principal + ", SCOPE IS " + scopeHeader);
        return demoContexts.get(2);
    }

}
