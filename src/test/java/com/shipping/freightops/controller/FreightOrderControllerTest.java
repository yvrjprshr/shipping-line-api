package com.shipping.freightops.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shipping.freightops.dto.CreateFreightOrderRequest;
import com.shipping.freightops.entity.Agent;
import com.shipping.freightops.entity.Container;
import com.shipping.freightops.entity.Port;
import com.shipping.freightops.entity.Vessel;
import com.shipping.freightops.entity.Voyage;
import com.shipping.freightops.enums.AgentType;
import com.shipping.freightops.enums.ContainerSize;
import com.shipping.freightops.enums.ContainerType;
import com.shipping.freightops.repository.AgentRepository;
import com.shipping.freightops.repository.ContainerRepository;
import com.shipping.freightops.repository.FreightOrderRepository;
import com.shipping.freightops.repository.PortRepository;
import com.shipping.freightops.repository.VesselRepository;
import com.shipping.freightops.repository.VoyageRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Integration test for {@link FreightOrderController}.
 *
 * <p>Uses H2 in-memory DB (see src/test/resources/application.properties). This is a good reference
 * for writing additional controller tests.
 */
@SpringBootTest
@AutoConfigureMockMvc
class FreightOrderControllerTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private PortRepository portRepository;
  @Autowired private VesselRepository vesselRepository;
  @Autowired private ContainerRepository containerRepository;
  @Autowired private VoyageRepository voyageRepository;
  @Autowired private FreightOrderRepository freightOrderRepository;
  @Autowired private AgentRepository agentRepository;

  private Voyage savedVoyage;
  private Container savedContainer;
  private Agent savedAgent;

  @BeforeEach
  void setUp() {
    // Clear state between tests — children first to respect FK constraints
    freightOrderRepository.deleteAll();
    voyageRepository.deleteAll();
    containerRepository.deleteAll();
    vesselRepository.deleteAll();
    portRepository.deleteAll();
    agentRepository.deleteAll();

    Port departure = portRepository.save(new Port("AEJEA", "Jebel Ali", "UAE"));
    Port arrival = portRepository.save(new Port("CNSHA", "Shanghai", "China"));
    Vessel vessel = vesselRepository.save(new Vessel("MV Test", "9999999", 3000));

    Voyage voyage = new Voyage();
    voyage.setVoyageNumber("VOY-001");
    voyage.setVessel(vessel);
    voyage.setDeparturePort(departure);
    voyage.setArrivalPort(arrival);
    voyage.setDepartureTime(LocalDateTime.now().plusDays(3));
    voyage.setArrivalTime(LocalDateTime.now().plusDays(10));
    savedVoyage = voyageRepository.save(voyage);

    savedContainer =
        containerRepository.save(
            new Container("TSTU1234567", ContainerSize.TWENTY_FOOT, ContainerType.DRY));

    Agent agent = new Agent();
    agent.setName("Test Agent");
    agent.setEmail("test@agent.com");
    agent.setCommissionPercent(new BigDecimal("5.00"));
    agent.setType(AgentType.INTERNAL);
    savedAgent = agentRepository.save(agent);
  }

  @Test
  @DisplayName("POST /api/v1/freight-orders → 201 Created")
  void createOrder_returnsCreated() throws Exception {
    CreateFreightOrderRequest request = new CreateFreightOrderRequest();
    request.setVoyageId(savedVoyage.getId());
    request.setContainerId(savedContainer.getId());
    request.setAgentId(savedAgent.getId());
    request.setNotes("Urgent delivery");

    mockMvc
        .perform(
            post("/api/v1/freight-orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.voyageNumber").value("VOY-001"))
        .andExpect(jsonPath("$.containerCode").value("TSTU1234567"))
        .andExpect(jsonPath("$.agentId").value(savedAgent.getId()))
        .andExpect(jsonPath("$.agentName").value("Test Agent"))
        .andExpect(jsonPath("$.status").value("PENDING"));
  }

  @Test
  @DisplayName("POST /api/v1/freight-orders with missing fields → 400 Bad Request")
  void createOrder_withMissingFields_returnsBadRequest() throws Exception {
    CreateFreightOrderRequest request = new CreateFreightOrderRequest();
    // intentionally leaving required fields empty

    mockMvc
        .perform(
            post("/api/v1/freight-orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("POST /api/v1/freight-orders with non-existent agentId → 404")
  void createOrder_withNonExistentAgent_returnsNotFound() throws Exception {
    CreateFreightOrderRequest request = new CreateFreightOrderRequest();
    request.setVoyageId(savedVoyage.getId());
    request.setContainerId(savedContainer.getId());
    request.setAgentId(9999L);

    mockMvc
        .perform(
            post("/api/v1/freight-orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("POST /api/v1/freight-orders with inactive agent → 409 Conflict")
  void createOrder_withInactiveAgent_returnsConflict() throws Exception {
    Agent inactiveAgent = new Agent();
    inactiveAgent.setName("Inactive Agent");
    inactiveAgent.setEmail("inactive@agent.com");
    inactiveAgent.setCommissionPercent(new BigDecimal("3.00"));
    inactiveAgent.setType(AgentType.EXTERNAL);
    inactiveAgent.setActive(false);
    inactiveAgent = agentRepository.save(inactiveAgent);

    CreateFreightOrderRequest request = new CreateFreightOrderRequest();
    request.setVoyageId(savedVoyage.getId());
    request.setContainerId(savedContainer.getId());
    request.setAgentId(inactiveAgent.getId());

    mockMvc
        .perform(
            post("/api/v1/freight-orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isConflict());
  }

  @Test
  @DisplayName("GET /api/v1/freight-orders → 200 OK with list")
  void listOrders_returnsOk() throws Exception {
    mockMvc
        .perform(get("/api/v1/freight-orders"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray());
  }
}
