package org.example.localhostneo4j;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class LocalhostNeo4jApplication {

    public static void main(String[] args) {
        SpringApplication.run(LocalhostNeo4jApplication.class, args);
    }
}
