package org.integratedmodelling.klab.services.reasoner;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.integratedmodelling.klab.Version;
import org.integratedmodelling.klab.data.encoding.JacksonConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import springfox.documentation.builders.ApiInfoBuilder;
import springfox.documentation.builders.PathSelectors;
import springfox.documentation.builders.RequestHandlerSelectors;
import springfox.documentation.service.ApiInfo;
import springfox.documentation.service.Contact;
import springfox.documentation.spi.DocumentationType;
import springfox.documentation.spring.web.plugins.Docket;
import springfox.documentation.swagger2.annotations.EnableSwagger2;

@SpringBootApplication
@EnableSwagger2
@ConfigurationProperties(prefix = "application.info")
@ComponentScan(basePackages = {"org.integratedmodelling.klab.services.reasoner", "org.integratedmodelling.klab.indexing",
        "org.integratedmodelling.klab.services.authentication", "org.integratedmodelling.klab.services.resources",
        "org.integratedmodelling.klab.services.reasoner.controllers"})
public class ReasonerApplication {

    /*
     * overridden through properties in application.yml, if only it worked.
     */
    private String version = Version.CURRENT;
    private String basePackage = "org.integratedmodelling.klab.services.reasoner.controllers";
    private String title = "k.LAB Reasoner API";
    private String description = "API documentation for the k.LAB reasoner service. POST methods use valid concepts obtained through the resolve endpoints.";
    private String contactName = "Integrated Modelling Partnership";
    private String contactEmail = "info@integratedmodelling.org";

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

    @Bean
    public Docket api() {
        return new Docket(DocumentationType.SWAGGER_2).select().apis(RequestHandlerSelectors.basePackage(basePackage))
                .paths(PathSelectors.any()).build().directModelSubstitute(LocalDate.class, java.sql.Date.class)
                .directModelSubstitute(LocalDateTime.class, java.util.Date.class).apiInfo(apiInfo());
    }

    private ApiInfo apiInfo() {
        return new ApiInfoBuilder().title(title).description(description).version(version)
                .contact(new Contact(contactName, null, contactEmail)).build();
    }

}
