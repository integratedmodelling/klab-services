package org.integratedmodelling.klab.support.graphdb.config;

import jakarta.validation.constraints.NotEmpty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.List;

@Validated
@ConfigurationProperties(prefix = "app.security")
public record AppSecurityProperties(@NotEmpty List<String> allowedHosts) {
}
