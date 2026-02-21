package com.shipping.freightops.service;

import com.shipping.freightops.dto.CreateFreightOrderRequest;
import com.shipping.freightops.entity.*;
import com.shipping.freightops.enums.ContainerSize;
import com.shipping.freightops.enums.VoyageStatus;
import com.shipping.freightops.exception.BadRequestException;
import com.shipping.freightops.repository.*;
import java.math.BigDecimal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Handles freight order creation and queries. */
@Service
public class FreightOrderService {

  private final FreightOrderRepository orderRepository;
  private final VoyageRepository voyageRepository;
  private final ContainerRepository containerRepository;
  private final CustomerRepository customerRepository;
  private final VoyagePriceRepository voyagePriceRepository;

  public FreightOrderService(
      FreightOrderRepository orderRepository,
      VoyageRepository voyageRepository,
      ContainerRepository containerRepository,
      CustomerRepository customerRepository,
      VoyagePriceRepository voyagePriceRepository) {
    this.orderRepository = orderRepository;
    this.voyageRepository = voyageRepository;
    this.containerRepository = containerRepository;
    this.customerRepository = customerRepository;
    this.voyagePriceRepository = voyagePriceRepository;
  }

  @Transactional
  public FreightOrder createOrder(CreateFreightOrderRequest request) {
    Voyage voyage =
        voyageRepository
            .findById(request.getVoyageId())
            .orElseThrow(
                () -> new IllegalArgumentException("Voyage not found: " + request.getVoyageId()));

    if (voyage.getStatus() == VoyageStatus.CANCELLED) {
      throw new IllegalStateException("Cannot book freight on a cancelled voyage");
    }

    Container container =
        containerRepository
            .findById(request.getContainerId())
            .orElseThrow(
                () ->
                    new IllegalArgumentException(
                        "Container not found: " + request.getContainerId()));

    Customer customer =
        customerRepository
            .findById(request.getCustomerId())
            .orElseThrow(
                () ->
                    new IllegalArgumentException("Customer not found: " + request.getCustomerId()));

    ContainerSize containerSize = container.getSize();
    VoyagePrice voyagePrice =
        voyagePriceRepository
            .findByVoyageAndContainerSize(voyage, containerSize)
            .orElseThrow(
                () -> new BadRequestException("No price defined for voyage and container size"));

    BigDecimal basePriceUsd = voyagePrice.getBasePriceUsd();
    BigDecimal discountPercentage =
        request.getDiscountPercent() != null ? request.getDiscountPercent() : BigDecimal.ZERO;
    BigDecimal finalPriceUsd =
        basePriceUsd.multiply(
            (BigDecimal.ONE.subtract(discountPercentage.divide(BigDecimal.valueOf(100)))));

    FreightOrder order = new FreightOrder();
    order.setVoyage(voyage);
    order.setContainer(container);
    order.setCustomer(customer);
    order.setOrderedBy(request.getOrderedBy());
    order.setNotes(request.getNotes());
    order.setBasePriceUsd(basePriceUsd);
    order.setDiscountPercent(discountPercentage);
    order.setFinalPrice(finalPriceUsd);

    return orderRepository.save(order);
  }

  @Transactional(readOnly = true)
  public FreightOrder getOrder(Long id) {
    return orderRepository
        .findById(id)
        .orElseThrow(() -> new IllegalArgumentException("Freight order not found: " + id));
  }

  @Transactional(readOnly = true)
  public Page<FreightOrder> getAllOrders(Pageable pageable) {
    return orderRepository.findAll(pageable);
  }

  @Transactional(readOnly = true)
  public Page<FreightOrder> getOrdersByVoyage(Long voyageId, Pageable pageable) {
    return orderRepository.findByVoyageId(voyageId, pageable);
  }
}
