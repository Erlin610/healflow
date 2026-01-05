package com.healflow.platform.controller;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.healflow.platform.HealflowPlatformApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class ReportControllerTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;

  @Test
  void statusEndpointWorks() throws Exception {
    mockMvc.perform(get("/api/status")).andExpect(status().isOk()).andExpect(jsonPath("$.status", is("UP")));
  }

  @Test
  void reportEndpointReturnsAnalysis() throws Exception {
    String body = objectMapper.writeValueAsString(new ReportRequest("panic: something bad"));
    mockMvc
        .perform(post("/api/report").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.severity", is("HIGH")))
        .andExpect(jsonPath("$.message", is("triage_required")));
  }

  @Test
  void reportEndpointValidatesRequest() throws Exception {
    String body = objectMapper.writeValueAsString(new ReportRequest(" "));
    mockMvc.perform(post("/api/report").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
  }

  @Test
  void applicationMainStartsInNonWebMode() {
    HealflowPlatformApplication.main(
        new String[] {"--spring.main.web-application-type=none", "--healflow.exception-listener-enabled=false"});
  }
}
