package com.cbo.players.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    public static final String BEARER_AUTH = "bearerAuth";
    public static final String API_KEY_AUTH = "apiKeyAuth";

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Football Players Valuation & Market Platform API")
                        .description("API REST para la plataforma de mercado y valoración de jugadores de fútbol (Tokens de Jugadores) - Entrega 1 Foundation")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("Grupo H - Construcción de Software")
                                .email("soporte@cbo-players.local")))
                .servers(List.of(
                        new Server().url("/").description("Servidor por defecto")
                ))
                .components(new Components()
                        .addSecuritySchemes(BEARER_AUTH, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Token JWT Bearer emitido tras login o registro"))
                        .addSecuritySchemes(API_KEY_AUTH, new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .name("X-API-Key")
                                .description("API Key para integración M2M y servicios automatizados")));
    }
}
