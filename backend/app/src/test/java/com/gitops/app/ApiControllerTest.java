package com.gitops.app;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
class ApiControllerTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @org.junit.jupiter.api.BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    void root_returnsVersionAndBgColor() throws Exception {
        mockMvc.perform(get("/api"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.version").value("0.1.0"))
                .andExpect(jsonPath("$.bg_color").value("green"));
    }

    @Test
    void healthz_returnsOkWhenPgdbDisabled() throws Exception {
        mockMvc.perform(get("/api/healthz"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"));
    }

    @Test
    void env_returnsAllSixVars() throws Exception {
        mockMvc.perform(get("/api/env"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.FRONTEND_BG_COLOR").value("green"))
                .andExpect(jsonPath("$.BACKEND_VERSION").value("0.1.0"))
                .andExpect(jsonPath("$.PGDB_ENABLE").value(false))
                .andExpect(jsonPath("$.PGDB_URL").value("jdbc:postgresql://postgres:5432/demo_db"))
                .andExpect(jsonPath("$.OOM_ENABLE").value(false))
                .andExpect(jsonPath("$.OOM_TIME").value(0));
    }
}
