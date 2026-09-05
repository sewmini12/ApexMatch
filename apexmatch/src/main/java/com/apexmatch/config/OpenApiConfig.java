package com.apexmatch.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI apexMatchOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("ApexMatch API")
                        .description("Real-time order matching engine API")
                        .version("1.0.0"));
    }
}
