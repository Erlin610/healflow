package com.healflow.starter.reporter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.healflow.starter.config.HealFlowProperties;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

class IncidentReporterTest {

  private static RestTemplate restTemplate() {
    ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    RestTemplate restTemplate = new RestTemplate();
    restTemplate
        .getMessageConverters()
        .add(0, new MappingJackson2HttpMessageConverter(objectMapper));
    return restTemplate;
  }

  @Test
  void postsReportIncludingRepoUrl() {
    HealFlowProperties properties = new HealFlowProperties();
    properties.setEnabled(true);
    properties.setAppId("demo-app");
    properties.setServerUrl("http://example.test");
    properties.setGitUrl("https://github.com/Erlin610/healflow.git");

    RestTemplate restTemplate = restTemplate();
    MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();

    server
        .expect(requestTo("http://example.test/api/v1/incidents/report"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(
            content()
                .string(
                    Matchers.containsString(
                        "\"repoUrl\":\"https://github.com/Erlin610/healflow.git\"")))
        .andRespond(withSuccess());

    new IncidentReporter(properties, restTemplate).report(new RuntimeException("boom"));

    server.verify();
  }

  @Test
  void postsReportIncludingHttpContextWhenPresent() {
    HealFlowProperties properties = new HealFlowProperties();
    properties.setEnabled(true);
    properties.setAppId("demo-app");
    properties.setServerUrl("http://example.test");

    RestTemplate restTemplate = restTemplate();
    MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();

    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/orders");
    request.setScheme("http");
    request.setServerName("client.example");
    request.setServerPort(8080);
    request.setQueryString("id=1");
    request.addHeader("X-Trace-Id", "trace-123");
    RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

    try {
      server
          .expect(requestTo("http://example.test/api/v1/incidents/report"))
          .andExpect(method(HttpMethod.POST))
          .andExpect(content().string(Matchers.containsString("\"requestMethod\":\"GET\"")))
          .andExpect(content().string(Matchers.containsString("\"requestParams\":\"id=1\"")))
          .andExpect(content().string(Matchers.containsString("\"traceId\":\"trace-123\"")))
          .andExpect(content().string(Matchers.containsString("/api/orders")))
          .andRespond(withSuccess());

      new IncidentReporter(properties, restTemplate).report(new RuntimeException("boom"));

      server.verify();
    } finally {
      RequestContextHolder.resetRequestAttributes();
    }
  }

  @Test
  void doesNotPostWhenDisabled() {
    HealFlowProperties properties = new HealFlowProperties();
    properties.setEnabled(false);
    properties.setAppId("demo-app");
    properties.setServerUrl("http://example.test");

    RestTemplate restTemplate = restTemplate();
    MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();

    assertDoesNotThrow(() -> new IncidentReporter(properties, restTemplate).report(new RuntimeException("boom")));
    server.verify();
  }

  @Test
  void httpErrorsDoNotEscapeReporter() {
    HealFlowProperties properties = new HealFlowProperties();
    properties.setEnabled(true);
    properties.setAppId("demo-app");
    properties.setServerUrl("http://example.test");

    RestTemplate restTemplate = restTemplate();
    MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();

    server
        .expect(requestTo("http://example.test/api/v1/incidents/report"))
        .andExpect(method(HttpMethod.POST))
        .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

    assertDoesNotThrow(() -> new IncidentReporter(properties, restTemplate).report(new RuntimeException("boom")));
    server.verify();
  }

  @Test
  void bindsNullGitUrlToEmptyString() {
    HealFlowProperties properties = new HealFlowProperties();
    properties.setGitUrl(null);
    assertThat(properties.getGitUrl()).isEmpty();
  }
}

