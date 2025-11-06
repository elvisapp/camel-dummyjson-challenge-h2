package com.example.challenge.service;

import com.example.challenge.api.dto.NewOrderRequest;
import com.example.challenge.api.dto.UpdateOrderRequest;
import com.example.challenge.api.dto.DummyJsonProduct;
import com.example.challenge.domain.Order;
import com.example.challenge.domain.OrderItem;
import com.example.challenge.domain.OrderStatus;
import com.example.challenge.repo.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class OrderService {

  private static final Logger log = LoggerFactory.getLogger(OrderService.class);

  private final OrderRepository repo;
  private final DummyJsonProductService productService;

  public OrderService(OrderRepository repo, DummyJsonProductService productService) {
    this.repo = repo;
    this.productService = productService;
  }

  public static double calculateTotal(List<OrderItem> items) {
    return items.stream().mapToDouble(i -> i.getQty() * i.getUnitPrice()).sum();
  }

  @Transactional
  public Order create(NewOrderRequest req) {
    log.info("Criando novo pedido para customerId: {}", req.getCustomerId());
    
    Order order = new Order();
    order.setCustomerId(req.getCustomerId());
    order.setStatus(OrderStatus.NEW);

    // Extrair SKUs para validação via DummyJSON
    List<String> skus = req.getItems().stream()
                          .map(item -> item.getSku())
                          .collect(Collectors.toList());
    
    log.info("Validando {} SKUs via DummyJSON API: {}", skus.size(), skus);
    
    // Validar SKUs e obter produtos da API
    Map<String, DummyJsonProduct> validProducts;
    try {
      validProducts = productService.validateSkus(skus);
      log.info("SKUs validados com sucesso: {} produtos encontrados", validProducts.size());
    } catch (IllegalArgumentException e) {
      log.error("Falha na validação de SKUs: {}", e.getMessage());
      throw new IllegalArgumentException("Erro na validação de produtos: " + e.getMessage(), e);
    }

    List<OrderItem> items = new ArrayList<>();
    for (NewOrderRequest.Item itemReq : req.getItems()) {
      DummyJsonProduct product = validProducts.get(itemReq.getSku());
      
      OrderItem item = new OrderItem();
      item.setSku(itemReq.getSku());
      item.setQty(itemReq.getQty());
      // Usar preço autoritativo da API (desconto aplicado se houver)
      item.setUnitPrice(product.getFinalPrice());
      item.setOrder(order);
      items.add(item);
      
      log.info("Item adicionado - SKU: {}, Título: {}, Qty: {}, Preço API: {:.2f} (original: {:.2f}, desconto: {}%)", 
               item.getSku(), product.getTitle(), item.getQty(), 
               product.getFinalPrice(), product.getPrice(), product.getDiscountPercentage());
    }
    
    order.setItems(items);
    order.setTotal(calculateTotal(items));
    
    log.info("Pedido criado com sucesso - ID: {}, Total: {:.2f}, {} itens", 
             order.getId(), order.getTotal(), items.size());

    return repo.save(order);
  }

  public Optional<Order> get(String id) {
    return repo.findById(id);
  }

  public List<Order> list(Optional<OrderStatus> status) {
    return status.map(repo::findByStatus).orElseGet(repo::findAll);
  }

  @Transactional
  public Order updateItems(String id, UpdateOrderRequest req) {
    log.info("Atualizando itens do pedido: {}", id);
    
    Order order = repo.findById(id).orElseThrow(() -> new IllegalArgumentException("Order not found"));
    if (order.getStatus() != OrderStatus.NEW) {
      throw new IllegalStateException("Can only update items for NEW orders");
    }

    // Extrair SKUs para validação via DummyJSON
    List<String> skus = req.getItems().stream()
                          .map(item -> item.getSku())
                          .collect(Collectors.toList());
    
    log.info("Validando {} SKUs para atualização via DummyJSON API: {}", skus.size(), skus);
    
    // Validar SKUs e obter produtos da API
    Map<String, DummyJsonProduct> validProducts;
    try {
      validProducts = productService.validateSkus(skus);
      log.info("SKUs validados para atualização com sucesso: {} produtos encontrados", validProducts.size());
    } catch (IllegalArgumentException e) {
      log.error("Falha na validação de SKUs para atualização: {}", e.getMessage());
      throw new IllegalArgumentException("Erro na validação de produtos para atualização: " + e.getMessage(), e);
    }

    List<OrderItem> items = new ArrayList<>();
    for (UpdateOrderRequest.Item itemReq : req.getItems()) {
      DummyJsonProduct product = validProducts.get(itemReq.getSku());
      
      OrderItem item = new OrderItem();
      item.setSku(itemReq.getSku());
      item.setQty(itemReq.getQty());
      // Usar preço autoritativo da API
      item.setUnitPrice(product.getFinalPrice());
      item.setOrder(order);
      items.add(item);
      
      log.info("Item atualizado - SKU: {}, Título: {}, Qty: {}, Preço API: {:.2f}", 
               item.getSku(), product.getTitle(), item.getQty(), product.getFinalPrice());
    }
    
    order.getItems().clear();
    order.getItems().addAll(items);
    order.setTotal(calculateTotal(items));
    
    log.info("Itens do pedido {} atualizados com sucesso - Novo total: {:.2f}", 
             id, order.getTotal());

    return repo.save(order);
  }

  @Transactional
  public void delete(String id) {
    log.info("Excluindo pedido: {}", id);
    
    Order order = repo.findById(id).orElseThrow(() -> new IllegalArgumentException("Order not found"));
    if (order.getStatus() != OrderStatus.NEW) {
      throw new IllegalStateException("Can only delete NEW orders");
    }
    repo.delete(order);
    log.info("Pedido {} excluído com sucesso", id);
  }

  @Transactional
  public void markPaid(String id) {
    log.info("Marcando pedido {} como PAID", id);
    
    Order order = repo.findById(id).orElseThrow(() -> new IllegalArgumentException("Order not found"));
    order.setStatus(OrderStatus.PAID);
    repo.save(order);
    
    log.info("Pedido {} marcado como PAID - Total: {:.2f}, Status anterior: {}", 
             id, order.getTotal(), OrderStatus.NEW);
  }

  @Transactional
  public void markFailed(String id) {
    log.info("Marcando pedido {} como FAILED_PAYMENT", id);
    
    Order order = repo.findById(id).orElseThrow(() -> new IllegalArgumentException("Order not found"));
    order.setStatus(OrderStatus.FAILED_PAYMENT);
    repo.save(order);
    
    log.info("Pedido {} marcado como FAILED_PAYMENT - Total: {:.2f}, Status anterior: {}", 
             id, order.getTotal(), OrderStatus.NEW);
  }
}
