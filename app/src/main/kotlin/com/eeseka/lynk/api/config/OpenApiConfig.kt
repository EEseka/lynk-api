package com.eeseka.lynk.api.config

import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Contact
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.security.SecurityRequirement
import io.swagger.v3.oas.models.security.SecurityScheme
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class OpenApiConfig {

    companion object {
        private const val API_KEY_SCHEME = "apiKey"
        private const val BEARER_SCHEME = "bearerAuth"
    }

    /**
     * Both schemes are required together, not as alternatives: every request carries the API key,
     * and everything past sign-in also carries the access token. Listing them as two entries on one
     * requirement is what makes Swagger UI send both.
     */
    @Bean
    fun lynkOpenApi(): OpenAPI = OpenAPI()
        .info(
            Info()
                .title("Lynk API")
                .version("v1")
                .description(
                    "Hangout planning: create a hangout, invite your squad, vote on a spot and " +
                        "split the bill. Every endpoint needs an X-API-Key header, and everything " +
                        "beyond sign-in also needs a bearer token from /api/auth."
                )
                .contact(Contact().name("Lynk").email("support@lynk.com.ng"))
        )
        .components(
            Components()
                .addSecuritySchemes(
                    API_KEY_SCHEME,
                    SecurityScheme()
                        .type(SecurityScheme.Type.APIKEY)
                        .`in`(SecurityScheme.In.HEADER)
                        .name("X-API-Key")
                        .description("Mint one with POST /api/auth/apiKey.")
                )
                .addSecuritySchemes(
                    BEARER_SCHEME,
                    SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("The accessToken returned by /api/auth/google or /api/auth/guest.")
                )
        )
        .addSecurityItem(
            SecurityRequirement()
                .addList(API_KEY_SCHEME)
                .addList(BEARER_SCHEME)
        )
}
