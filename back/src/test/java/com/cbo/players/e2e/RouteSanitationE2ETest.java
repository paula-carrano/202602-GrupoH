package com.cbo.players.e2e;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class RouteSanitationE2ETest extends AbstractE2ETest {

    @Test
    @DisplayName("GET /api/players debe requerir autenticación (401)")
    void getPlayersWithoutAuthReturns401() throws Exception {
        mockMvc.perform(get("/api/players"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Rutas legacy con prefijo /v1 no deben estar expuestas públicamente ni permitidas")
    void legacyV1RoutesAreNotPubliclyPermitted() throws Exception {
        mockMvc.perform(get("/api/v1/players"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /api/auth/register debe estar expuesto sin /v1 y ser accesible sin autenticación previa")
    void registerEndpointIsAccessibleWithoutV1Prefix() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("POST /api/auth/login debe estar expuesto sin /v1 y ser accesible sin autenticación previa")
    void loginEndpointIsAccessibleWithoutV1Prefix() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("GET /actuator/health debe responder 200 OK")
    void actuatorHealthIsAccessible() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("OpenAPI spec debe documentar rutas bajo /api y ninguna bajo /api/v1")
    void openApiSpecDocumentsSanitizedRoutesWithoutV1() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/api/auth/register")))
                .andExpect(content().string(containsString("/api/auth/login")))
                .andExpect(content().string(containsString("/api/players")))
                .andExpect(content().string(not(containsString("/api/v1/"))));
    }
}
