package org.integratedmodelling.klab.services.runtime.server.controllers;

import org.integratedmodelling.klab.services.runtime.server.objects.Context;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

/**
 * Provides the GraphQL support for context access and inspection
 */
@Controller
public class RuntimeServerContextController {

    private List<Context> demoContexts = new ArrayList<>();

    public RuntimeServerContextController() {
        demoContexts.add(new Context("dio1", "dio uno", null, null));
        demoContexts.add(new Context("dio2", "dio due", null, null));
        demoContexts.add(new Context("dio3", "dio tre", null, null));
        demoContexts.add(new Context("dio4", "dio quattro", null, null));
        demoContexts.add(new Context("dio5", "dio cinque", null, null));
        demoContexts.add(new Context("dio6", "dio sei", null, null));
    }

    @QueryMapping
    List<Context> contexts(String id) {
        return id == null ? demoContexts :
               demoContexts.stream().filter(context -> context.id().equals(id)).toList();
    }

}
