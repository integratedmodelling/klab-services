package org.integratedmodelling.klab.support.graphdb;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class KlabNeo4jStarter {

    public static void main(String[] args) {
        SpringApplication.run(KlabNeo4jStarter.class, args);
    }
}
