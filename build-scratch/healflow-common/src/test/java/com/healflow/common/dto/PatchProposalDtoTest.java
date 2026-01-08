package com.healflow.common.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.healflow.common.enums.AgentStatus;
import org.junit.jupiter.api.Test;

class PatchProposalDtoTest {

  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  void constructor_rejectsNullStatus() {
    NullPointerException ex = assertThrows(NullPointerException.class, () -> new PatchProposalDto("a", "b", "diff", null));
    assertEquals("agentStatus must not be null", ex.getMessage());
  }

  @Test
  void builder_buildsAndValidates() {
    PatchProposalDto proposal =
        PatchProposalDto.builder()
            .appName("order-service")
            .branch("main")
            .patch("diff --git a/x b/x")
            .agentStatus(AgentStatus.DONE)
            .build();

    assertEquals("order-service", proposal.appName());
    assertEquals("main", proposal.branch());
    assertEquals("diff --git a/x b/x", proposal.patch());
    assertEquals(AgentStatus.DONE, proposal.agentStatus());
  }

  @Test
  void valueSemantics_equalsHashCodeToString() {
    PatchProposalDto first = new PatchProposalDto("a", "b", "c", AgentStatus.ANALYZING);
    PatchProposalDto second = new PatchProposalDto("a", "b", "c", AgentStatus.ANALYZING);
    PatchProposalDto third = new PatchProposalDto("a", "b", "c", AgentStatus.DONE);

    assertEquals(first, second);
    assertEquals(first.hashCode(), second.hashCode());
    assertNotEquals(first, third);

    String text = first.toString();
    assertNotNull(text);
    assertTrue(text.contains("PatchProposalDto"));
    assertTrue(text.contains("agentStatus"));
  }

  @Test
  void json_roundTrip() throws Exception {
    PatchProposalDto proposal = new PatchProposalDto("order-service", "main", "diff --git a/x b/x", AgentStatus.DONE);

    String json = mapper.writeValueAsString(proposal);
    PatchProposalDto parsed = mapper.readValue(json, PatchProposalDto.class);

    assertEquals(proposal, parsed);
  }
}
