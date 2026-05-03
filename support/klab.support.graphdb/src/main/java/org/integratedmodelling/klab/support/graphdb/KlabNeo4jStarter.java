package org.integratedmodelling.klab.support.graphdb;

import com.google.api.Logging;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

import java.nio.file.Paths;

@SpringBootApplication
@ConfigurationPropertiesScan
public class KlabNeo4jStarter {

  public static void main(String[] args) {
    SpringApplication.run(KlabNeo4jStarter.class, args);
  }
}
