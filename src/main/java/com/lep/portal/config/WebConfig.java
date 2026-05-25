package com.lep.portal.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    // Additional MVC configuration (e.g. interceptors, resource handlers) goes here.
    // Currently using Spring Boot defaults.
}
