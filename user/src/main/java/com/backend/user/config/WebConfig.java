package com.backend.user.config;

import com.backend.user.filter.JwtAuthenticationFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Autowired
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        // CORS is handled by gateway, manage service does not need CORS configuration
        // All requests should go through the gateway which handles CORS properly
    }

    @Bean
    public FilterRegistrationBean<JwtAuthenticationFilter> jwtAuthenticationFilterRegistration() {
        FilterRegistrationBean<JwtAuthenticationFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(jwtAuthenticationFilter);
        registration.addUrlPatterns("/*");
        registration.setOrder(1);
        registration.setName("jwtAuthenticationFilter");
        return registration;
    }
}
