package com.example.challenge.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

@Entity
@Table(name = "order_items")
public class OrderItem {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @NotBlank
  private String sku;

  @Min(1)
  private int qty;

  @Min(0)
  private double unitPrice;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "order_id")
  private Order order;

  public Long getId() { return id; }
  public String getSku() { return sku; }
  public void setSku(String sku) { this.sku = sku; }
  public int getQty() { return qty; }
  public void setQty(int qty) { this.qty = qty; }
  public double getUnitPrice() { return unitPrice; }
  public void setUnitPrice(double unitPrice) { this.unitPrice = unitPrice; }
  public Order getOrder() { return order; }
  public void setOrder(Order order) { this.order = order; }
}
