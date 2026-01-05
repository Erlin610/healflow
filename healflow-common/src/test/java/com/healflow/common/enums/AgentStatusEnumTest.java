package com.healflow.common.enums;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class AgentStatusEnumTest {

  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  void valueOf_matchesDeclaredNames() {
    assertEquals(AgentStatus.ANALYZING, AgentStatus.valueOf("ANALYZING"));
    assertEquals(AgentStatus.WAITING_AUTH, AgentStatus.valueOf("WAITING_AUTH"));
    assertEquals(AgentStatus.DONE, AgentStatus.valueOf("DONE"));
  }

  @Test
  void json_serializesAndDeserializes() throws Exception {
    String json = mapper.writeValueAsString(AgentStatus.DONE);
    assertEquals("\"DONE\"", json);

    AgentStatus parsed = mapper.readValue(json, AgentStatus.class);
    assertEquals(AgentStatus.DONE, parsed);
  }
}

