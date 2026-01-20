package com.f1bets.infrastructure.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
            .info(new Info()
                .title("F1 Bets API")
                .version("1.0.0")
                .description("Production-grade F1 betting REST API with Clean Architecture")
                .contact(new Contact()
                    .name("F1 Bets Development Team")
                    .email("dechosl@gmail.com")));
    }
}
