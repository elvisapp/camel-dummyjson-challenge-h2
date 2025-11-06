package com.example.challenge.api.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.util.List;

public class UpdateOrderRequest {
  @NotEmpty
  private List<Item> items;

  public List<Item> getItems() { return items; }
  public void setItems(List<Item> items) { this.items = items; }

  public static class Item {
    @NotBlank
    private String sku;
    @Min(1)
    private int qty;
    @Min(0)
    private double unitPrice;

    public String getSku() { return sku; }
    public void setSku(String sku) { this.sku = sku; }
    public int getQty() { return qty; }
    public void setQty(int qty) { this.qty = qty; }
    public double getUnitPrice() { return unitPrice; }
    public void setUnitPrice(double unitPrice) { this.unitPrice = unitPrice; }
  }
}
