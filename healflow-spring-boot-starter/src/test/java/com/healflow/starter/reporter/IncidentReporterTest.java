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
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class IncidentReporterTest {

  private static RestClient.Builder restClientBuilder(HealFlowProperties properties) {
    ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    return RestClient.builder()
        .baseUrl(properties.getServerUrl())
        .messageConverters(converters -> converters.add(new MappingJackson2HttpMessageConverter(objectMapper)));
  }

  @Test
  void postsReportIncludingRepoUrl() {
    HealFlowProperties properties = new HealFlowProperties();
    properties.setEnabled(true);
    properties.setAppId("demo-app");
    properties.setServerUrl("http://example.test");
    properties.setGitUrl("https://github.com/Erlin610/healflow.git");

    RestClient.Builder builder = restClientBuilder(properties);
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    RestClient restClient = builder.build();

    server
        .expect(requestTo("http://example.test/api/v1/incidents/report"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(content().string(org.hamcrest.Matchers.containsString("\"repoUrl\":\"https://github.com/Erlin610/healflow.git\"")))
        .andRespond(withSuccess());

    new IncidentReporter(properties, restClient).report(new RuntimeException("boom"));

    server.verify();
  }

  @Test
  void doesNotPostWhenDisabled() {
    HealFlowProperties properties = new HealFlowProperties();
    properties.setEnabled(false);
    properties.setAppId("demo-app");
    properties.setServerUrl("http://example.test");

    RestClient.Builder builder = restClientBuilder(properties);
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    RestClient restClient = builder.build();

    assertDoesNotThrow(() -> new IncidentReporter(properties, restClient).report(new RuntimeException("boom")));
    server.verify();
  }

  @Test
  void httpErrorsDoNotEscapeReporter() {
    HealFlowProperties properties = new HealFlowProperties();
    properties.setEnabled(true);
    properties.setAppId("demo-app");
    properties.setServerUrl("http://example.test");

    RestClient.Builder builder = restClientBuilder(properties);
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    RestClient restClient = builder.build();

    server
        .expect(requestTo("http://example.test/api/v1/incidents/report"))
        .andExpect(method(HttpMethod.POST))
        .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

    assertDoesNotThrow(() -> new IncidentReporter(properties, restClient).report(new RuntimeException("boom")));
    server.verify();
  }

  @Test
  void bindsNullGitUrlToEmptyString() {
    HealFlowProperties properties = new HealFlowProperties();
    properties.setGitUrl(null);
    assertThat(properties.getGitUrl()).isEmpty();
  }
}
