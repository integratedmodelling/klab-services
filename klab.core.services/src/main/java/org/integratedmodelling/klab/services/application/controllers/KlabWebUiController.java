package org.integratedmodelling.klab.services.application.controllers;

import org.integratedmodelling.klab.services.application.ServiceNetworkedInstance;
import org.integratedmodelling.klab.services.application.web.WebUiConfiguration;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

/** Serves the public dashboard shell and its service-specific configuration. */
@Controller
public class KlabWebUiController {

  private final ServiceNetworkedInstance<?> instance;

  public KlabWebUiController(ServiceNetworkedInstance<?> instance) {
    this.instance = instance;
  }

  @GetMapping(value = {"", "/"}, produces = MediaType.TEXT_HTML_VALUE)
  public String dashboard() {
    return "forward:/index.html";
  }

  @ResponseBody
  @GetMapping(value = "/public/ui/config", produces = MediaType.APPLICATION_JSON_VALUE)
  public WebUiConfiguration configuration() {
    return instance.webUiConfiguration();
  }
}
