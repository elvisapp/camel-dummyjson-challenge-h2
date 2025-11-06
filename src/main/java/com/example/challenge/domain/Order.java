package com.example.challenge.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "orders")
public class Order {

  @Id
  @Column(length = 36)
  private String id;

  private String customerId;

  @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
  private List<OrderItem> items = new ArrayList<>();

  private double total;

  @Enumerated(EnumType.STRING)
  private OrderStatus status = OrderStatus.NEW;

  public Order() {
    this.id = UUID.randomUUID().toString();
  }

  public String getId() { return id; }
  public String getCustomerId() { return customerId; }
  public void setCustomerId(String customerId) { this.customerId = customerId; }
  
  @JsonIgnore
  public List<OrderItem> getItems() { return items; }
  public void setItems(List<OrderItem> items) { this.items = items; }
  public double getTotal() { return total; }
  public void setTotal(double total) { this.total = total; }
  public OrderStatus getStatus() { return status; }
  public void setStatus(OrderStatus status) { this.status = status; }
}
