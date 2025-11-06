package com.example.challenge.api;

import com.example.challenge.api.dto.NewOrderRequest;
import com.example.challenge.api.dto.UpdateOrderRequest;
import com.example.challenge.domain.Order;
import com.example.challenge.domain.OrderStatus;
import com.example.challenge.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.apache.camel.ProducerTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/orders")
@Tag(name="Orders")
public class OrderController {

  private final OrderService service;
  private final ProducerTemplate template;

  public OrderController(OrderService service, ProducerTemplate template) {
    this.service = service;
    this.template = template;
  }

  @Operation(summary = "Cria um novo pedido")
  @PostMapping
  public ResponseEntity<Order> create(@Valid @RequestBody NewOrderRequest req) {
    Order order = service.create(req);
    URI location = URI.create("/api/orders/" + order.getId());
    return ResponseEntity.created(location).body(order);
  }

  @Operation(summary = "Busca um pedido por ID")
  @GetMapping("/{id}")
  public ResponseEntity<Order> get(@PathVariable String id) {
    Optional<Order> order = service.get(id);
    if (order.isPresent()) {
      return ResponseEntity.ok(order.get());
    } else {
      return ResponseEntity.notFound().build();
    }
  }

  @Operation(summary = "Lista pedidos (opcional filtrar por status)")
  @GetMapping
  public ResponseEntity<List<Order>> list(@RequestParam Optional<OrderStatus> status) {
    List<Order> orders = service.list(status);
    return ResponseEntity.ok(orders);
  }

  @Operation(summary = "Atualiza itens de um pedido (apenas se NEW)")
  @PutMapping("/{id}")
  public ResponseEntity<Order> update(@PathVariable String id, @Valid @RequestBody UpdateOrderRequest req) {
    try {
      Order order = service.updateItems(id, req);
      return ResponseEntity.ok(order);
    } catch (IllegalArgumentException e) {
      return ResponseEntity.notFound().build();
    } catch (IllegalStateException e) {
      return ResponseEntity.badRequest().build();
    }
  }

  @Operation(summary = "Exclui um pedido (apenas se NEW)")
  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(@PathVariable String id) {
    try {
      service.delete(id);
      return ResponseEntity.noContent().build();
    } catch (IllegalArgumentException e) {
      return ResponseEntity.notFound().build();
    } catch (IllegalStateException e) {
      return ResponseEntity.badRequest().build();
    }
  }

  @Operation(summary = "Processa pagamento do pedido via Camel chamando DummyJSON")
  @PostMapping("/{id}/pay")
  public ResponseEntity<Order> pay(@PathVariable String id) {
    Optional<Order> orderOpt = service.get(id);
    if (orderOpt.isEmpty()) {
      return ResponseEntity.notFound().build();
    }
    Order order = orderOpt.get();
    if (order.getStatus() != OrderStatus.NEW) {
      return ResponseEntity.badRequest().build();
    }
    // Enviar para Camel
    Map<String, Object> headers = new HashMap<>();
    headers.put("orderId", id);
    headers.put("amount", order.getTotal());
    template.sendBodyAndHeaders("direct:payOrder", null, headers);
    // Recargar order después del pago
    Order updatedOrder = service.get(id).orElseThrow();
    return ResponseEntity.ok(updatedOrder);
  }
}
