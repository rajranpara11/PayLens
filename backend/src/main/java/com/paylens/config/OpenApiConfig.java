package com.paylens.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI payLensOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("PayLens API")
                        .description("Employee salary management API")
                        .version("v1"));
    }
}
