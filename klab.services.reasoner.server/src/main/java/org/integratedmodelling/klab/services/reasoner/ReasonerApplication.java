package org.integratedmodelling.klab.services.reasoner;

import org.integratedmodelling.klab.data.encoding.JacksonConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

@SpringBootApplication
@ComponentScan(basePackages = {"org.integratedmodelling.klab.services.reasoner", "org.integratedmodelling.klab.indexing",
        "org.integratedmodelling.klab.services.authentication", "org.integratedmodelling.klab.services.resources",
        "org.integratedmodelling.klab.services.reasoner.controllers"})
public class ReasonerApplication {

    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper objectMapper = new ObjectMapper().enable(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
                .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS).enable(SerializationFeature.WRITE_NULL_MAP_VALUES);
        // objectMapper.getSerializerProvider().setNullKeySerializer(new Jsr310NullKeySerializer());
        JacksonConfiguration.configureObjectMapperForKlabTypes(objectMapper);
        return objectMapper;
    }

    public static void main(String[] args) {
        SpringApplication.run(ReasonerApplication.class, args);
    }

}
