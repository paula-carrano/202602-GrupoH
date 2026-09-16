package com.cbo.players.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OpenApiDocsTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("GET /v3/api-docs debe ser público y retornar la especificación OpenAPI")
    void apiDocsShouldBePubliclyAccessible() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("openapi")))
                .andExpect(content().string(containsString("Football Players Valuation & Market Platform API")))
                .andExpect(content().string(containsString("/api/v1/auth/register")))
                .andExpect(content().string(containsString("/api/v1/players")));
    }

    @Test
    @DisplayName("GET /swagger-ui/index.html debe ser público y retornar 200 OK")
    void swaggerUiShouldBePubliclyAccessible() throws Exception {
        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk());
    }
}
