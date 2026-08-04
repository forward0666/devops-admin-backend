package com.backend.utils.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.Contact;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Shared OpenAPI / Swagger configuration.
 * Modules can override by providing their own OpenAPI bean.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI devopsOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("DevOps Admin API")
                        .description("REST API for DevOps management platform")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("DevOps Team")));
    }
}