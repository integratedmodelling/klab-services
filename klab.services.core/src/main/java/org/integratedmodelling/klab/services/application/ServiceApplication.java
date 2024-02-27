package org.integratedmodelling.klab.services.application;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.annotation.PreDestroy;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.integratedmodelling.klab.api.services.KlabService;
import org.integratedmodelling.klab.services.base.BaseService;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.http.converter.protobuf.ProtobufHttpMessageConverter;
import org.springframework.http.converter.protobuf.ProtobufJsonFormatHttpMessageConverter;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.ui.ModelMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.context.request.WebRequestInterceptor;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.handler.WebRequestHandlerInterceptorAdapter;

@Component
@EnableAutoConfiguration
@ComponentScan(basePackages = {"org.integratedmodelling.klab.services.security",
                               "org.integratedmodelling.klab.services.resources",
                               "org.integratedmodelling.klab.services.controllers"})
public class ServiceApplication implements WebMvcConfigurer {

    private Service service;
//    private AtomicBoolean maintenanceMode = new AtomicBoolean(false);
//    private AtomicBoolean atomicOperationMode = new AtomicBoolean(false);

//    public void run(Service<?> klabService, String[] args) {
//        this.service = klabService;
//        ServiceStartupOptions options = new ServiceStartupOptions();
//        options.initialize(args);
//        klabService.start();
//    }

    public void run(String[] args) {
        ServiceStartupOptions options = new ServiceStartupOptions();
        options.initialize(args);
        // TODO
    }

    @PreDestroy
    public void shutdown() {
        // TODO engine shutdown if needed
    }

    @Bean
    public ProtobufJsonFormatHttpMessageConverter ProtobufJsonFormatHttpMessageConverter() {
        return new ProtobufJsonFormatHttpMessageConverter();
    }

    @Bean
    public RestTemplate restTemplate(ProtobufHttpMessageConverter hmc) {
        return new RestTemplate(Arrays.asList(hmc));
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {

        /**
         * Handle maintenance mode and wait mode, defaulting to maintenance mode after configurable timeout
         */
        registry.addInterceptor(new HandlerInterceptor() {
            @Override
            public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                                     Object handler) throws Exception {
                // response.sendRedirect(maintenanceMapping); return false;
                return HandlerInterceptor.super.preHandle(request, response, handler);
            }

        });
    }

    @Bean
    public BCryptPasswordEncoder bCryptPasswordEncoder() {
        return new BCryptPasswordEncoder();
    }

    //    public static void main(String args[]) {
    //        new ServiceApplication().run(args);
    //    }

}
