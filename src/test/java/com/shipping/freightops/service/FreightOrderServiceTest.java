package com.shipping.freightops.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shipping.freightops.dto.CreateFreightOrderRequest;
import com.shipping.freightops.entity.Agent;
import com.shipping.freightops.entity.Container;
import com.shipping.freightops.entity.FreightOrder;
import com.shipping.freightops.entity.Voyage;
import com.shipping.freightops.enums.AgentType;
import com.shipping.freightops.enums.ContainerSize;
import com.shipping.freightops.enums.ContainerType;
import com.shipping.freightops.enums.OrderStatus;
import com.shipping.freightops.enums.VoyageStatus;
import com.shipping.freightops.repository.AgentRepository;
import com.shipping.freightops.repository.ContainerRepository;
import com.shipping.freightops.repository.FreightOrderRepository;
import com.shipping.freightops.repository.VoyageRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Unit tests for {@link FreightOrderService}. */
@ExtendWith(MockitoExtension.class)
class FreightOrderServiceTest {

  @Mock private FreightOrderRepository orderRepository;
  @Mock private VoyageRepository voyageRepository;
  @Mock private ContainerRepository containerRepository;
  @Mock private AgentRepository agentRepository;

  @InjectMocks private FreightOrderService freightOrderService;

  // ── CREATE ORDER ──────────────────────────────────────────

  @Nested
  @DisplayName("createOrder")
  class CreateOrder {

    @Test
    @DisplayName("happy path — sets voyage, container, agent, notes, and saves")
    void happyPath() {
      Voyage voyage = buildVoyage(1L, VoyageStatus.PLANNED);
      Container container = buildContainer(2L);
      Agent agent = buildAgent(3L, true);

      when(voyageRepository.findById(1L)).thenReturn(Optional.of(voyage));
      when(containerRepository.findById(2L)).thenReturn(Optional.of(container));
      when(agentRepository.findById(3L)).thenReturn(Optional.of(agent));
      when(orderRepository.save(any(FreightOrder.class)))
          .thenAnswer(
              inv -> {
                FreightOrder saved = inv.getArgument(0);
                saved.setId(100L);
                return saved;
              });

      CreateFreightOrderRequest request = new CreateFreightOrderRequest();
      request.setVoyageId(1L);
      request.setContainerId(2L);
      request.setAgentId(3L);
      request.setNotes("Handle with care");

      FreightOrder result = freightOrderService.createOrder(request);

      ArgumentCaptor<FreightOrder> captor = ArgumentCaptor.forClass(FreightOrder.class);
      verify(orderRepository).save(captor.capture());
      FreightOrder captured = captor.getValue();

      assertThat(captured.getVoyage()).isSameAs(voyage);
      assertThat(captured.getContainer()).isSameAs(container);
      assertThat(captured.getAgent()).isSameAs(agent);
      assertThat(captured.getNotes()).isEqualTo("Handle with care");
      assertThat(result.getId()).isEqualTo(100L);
    }

    @Test
    @DisplayName("throws when voyage not found")
    void throwsWhenVoyageNotFound() {
      when(voyageRepository.findById(99L)).thenReturn(Optional.empty());

      CreateFreightOrderRequest request = new CreateFreightOrderRequest();
      request.setVoyageId(99L);

      assertThatThrownBy(() -> freightOrderService.createOrder(request))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Voyage not found: 99");

      verify(orderRepository, never()).save(any());
    }

    @Test
    @DisplayName("throws when voyage is cancelled")
    void throwsWhenVoyageCancelled() {
      Voyage cancelled = buildVoyage(1L, VoyageStatus.CANCELLED);
      when(voyageRepository.findById(1L)).thenReturn(Optional.of(cancelled));

      CreateFreightOrderRequest request = new CreateFreightOrderRequest();
      request.setVoyageId(1L);

      assertThatThrownBy(() -> freightOrderService.createOrder(request))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("Cannot book freight on a cancelled voyage");

      verify(orderRepository, never()).save(any());
    }

    @Test
    @DisplayName("throws when container not found")
    void throwsWhenContainerNotFound() {
      Voyage voyage = buildVoyage(1L, VoyageStatus.PLANNED);
      when(voyageRepository.findById(1L)).thenReturn(Optional.of(voyage));
      when(containerRepository.findById(99L)).thenReturn(Optional.empty());

      CreateFreightOrderRequest request = new CreateFreightOrderRequest();
      request.setVoyageId(1L);
      request.setContainerId(99L);

      assertThatThrownBy(() -> freightOrderService.createOrder(request))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Container not found: 99");

      verify(orderRepository, never()).save(any());
    }

    @Test
    @DisplayName("throws when agent not found")
    void throwsWhenAgentNotFound() {
      Voyage voyage = buildVoyage(1L, VoyageStatus.PLANNED);
      Container container = buildContainer(2L);
      when(voyageRepository.findById(1L)).thenReturn(Optional.of(voyage));
      when(containerRepository.findById(2L)).thenReturn(Optional.of(container));
      when(agentRepository.findById(99L)).thenReturn(Optional.empty());

      CreateFreightOrderRequest request = new CreateFreightOrderRequest();
      request.setVoyageId(1L);
      request.setContainerId(2L);
      request.setAgentId(99L);

      assertThatThrownBy(() -> freightOrderService.createOrder(request))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Agent not found: 99");

      verify(orderRepository, never()).save(any());
    }

    @Test
    @DisplayName("throws when agent is inactive")
    void throwsWhenAgentInactive() {
      Voyage voyage = buildVoyage(1L, VoyageStatus.PLANNED);
      Container container = buildContainer(2L);
      Agent inactive = buildAgent(3L, false);
      when(voyageRepository.findById(1L)).thenReturn(Optional.of(voyage));
      when(containerRepository.findById(2L)).thenReturn(Optional.of(container));
      when(agentRepository.findById(3L)).thenReturn(Optional.of(inactive));

      CreateFreightOrderRequest request = new CreateFreightOrderRequest();
      request.setVoyageId(1L);
      request.setContainerId(2L);
      request.setAgentId(3L);

      assertThatThrownBy(() -> freightOrderService.createOrder(request))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("Cannot place order with inactive agent");

      verify(orderRepository, never()).save(any());
    }
  }

  // ── GET ORDER ─────────────────────────────────────────────

  @Nested
  @DisplayName("getOrder")
  class GetOrder {

    @Test
    @DisplayName("returns order when found")
    void returnsOrderWhenFound() {
      FreightOrder order = new FreightOrder();
      order.setId(1L);
      when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

      FreightOrder result = freightOrderService.getOrder(1L);

      assertThat(result.getId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("throws when order not found")
    void throwsWhenNotFound() {
      when(orderRepository.findById(99L)).thenReturn(Optional.empty());

      assertThatThrownBy(() -> freightOrderService.getOrder(99L))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Freight order not found: 99");
    }
  }

  // ── GET ALL ORDERS ────────────────────────────────────────

  @Nested
  @DisplayName("getAllOrders")
  class GetAllOrders {

    @Test
    @DisplayName("returns all orders from repository")
    void returnsAll() {
      FreightOrder o1 = new FreightOrder();
      o1.setId(1L);
      FreightOrder o2 = new FreightOrder();
      o2.setId(2L);
      when(orderRepository.findAll()).thenReturn(List.of(o1, o2));

      List<FreightOrder> result = freightOrderService.getAllOrders();

      assertThat(result).hasSize(2);
      verify(orderRepository).findAll();
    }
  }

  // ── GET ORDERS BY VOYAGE ──────────────────────────────────

  @Nested
  @DisplayName("getOrdersByVoyage")
  class GetOrdersByVoyage {

    @Test
    @DisplayName("delegates to repository findByVoyageId")
    void delegatesToRepo() {
      FreightOrder o1 = new FreightOrder();
      o1.setId(1L);
      when(orderRepository.findByVoyageId(5L)).thenReturn(List.of(o1));

      List<FreightOrder> result = freightOrderService.getOrdersByVoyage(5L);

      assertThat(result).hasSize(1);
      verify(orderRepository).findByVoyageId(5L);
    }
  }

  // ── HELPERS ───────────────────────────────────────────────

  private Voyage buildVoyage(Long id, VoyageStatus status) {
    Voyage voyage = new Voyage();
    voyage.setId(id);
    voyage.setStatus(status);
    return voyage;
  }

  private Container buildContainer(Long id) {
    Container container = new Container();
    container.setId(id);
    return container;
  }

  private Agent buildAgent(Long id, boolean active) {
    Agent agent = new Agent();
    agent.setId(id);
    agent.setName("Test Agent");
    agent.setEmail("test@agent.com");
    agent.setCommissionPercent(new BigDecimal("5.00"));
    agent.setType(AgentType.INTERNAL);
    agent.setActive(active);
    return agent;
  }
}
