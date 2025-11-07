package com.example.challenge.service;

import com.example.challenge.api.dto.DummyJsonProduct;
import com.example.challenge.integration.DummyJsonProperties;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.ResourceAccessException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Serviço para integração com DummyJSON Products
 * - Validação de SKUs
 * - Preço autoritativo da API
 * - Cache em memória
 */
@Service
public class DummyJsonProductService {
  
  private final RestTemplate restTemplate;
  private final DummyJsonProperties dummyJsonProperties;
  private final ConcurrentHashMap<String, CachedProduct> productCache = new ConcurrentHashMap<>();
  
  // Cache interno para evitar requisições desnecessárias
  private static class CachedProduct {
    private final DummyJsonProduct product;
    private final long timestamp;
    
    public CachedProduct(DummyJsonProduct product) {
      this.product = product;
      this.timestamp = System.currentTimeMillis();
    }
    
    public DummyJsonProduct getProduct() { return product; }
    public long getTimestamp() { return timestamp; }
    public boolean isExpired(long expirationTime) {
      return System.currentTimeMillis() - timestamp > expirationTime;
    }
  }
  
  public DummyJsonProductService() {
    this.restTemplate = new RestTemplate();
    this.dummyJsonProperties = new DummyJsonProperties();
  }
  
  public DummyJsonProductService(RestTemplate restTemplate, DummyJsonProperties dummyJsonProperties) {
    this.restTemplate = restTemplate;
    this.dummyJsonProperties = dummyJsonProperties;
  }
  
  /**
   * Busca produto por SKU (ID) na API DummyJSON
   * Implementa cache e tratamento de erros
   */
  public DummyJsonProduct getProductBySku(String sku) {
    if (!dummyJsonProperties.getProducts().isEnableCache()) {
      return fetchProductFromApi(sku);
    }
    
    String cacheKey = sku.trim();
    CachedProduct cached = productCache.get(cacheKey);
    
    if (cached != null && !cached.isExpired(dummyJsonProperties.getProducts().getCacheExpirationMs())) {
      return cached.getProduct();
    }
    
    DummyJsonProduct product = fetchProductFromApi(sku);
    productCache.put(cacheKey, new CachedProduct(product));
    
    return product;
  }
  
  private DummyJsonProduct fetchProductFromApi(String sku) {
    String productUrl = String.format("%s/products/%s", dummyJsonProperties.getBaseUrl(), sku.trim());
    
    try {
      return restTemplate.getForObject(productUrl, DummyJsonProduct.class);
    } catch (HttpClientErrorException.NotFound e) {
      throw new IllegalArgumentException("Produto com SKU '" + sku + "' não encontrado na API DummyJSON");
    } catch (HttpClientErrorException e) {
      throw new IllegalArgumentException("Erro ao consultar produto SKU '" + sku + "': HTTP " + e.getStatusCode(), e);
    } catch (ResourceAccessException e) {
      throw new IllegalArgumentException("Erro de rede ao consultar produto SKU '" + sku + "': " + e.getMessage(), e);
    }
  }
  
  /**
   * Valida múltiplos SKUs e retorna map de produtos
   * Retorna apenas SKUs válidos para processamento
   */
  public Map<String, DummyJsonProduct> validateSkus(List<String> skus) {
    Map<String, DummyJsonProduct> validProducts = new HashMap<>();
    List<String> invalidSkus = new ArrayList<>();
    
    for (String sku : skus) {
      try {
        DummyJsonProduct product = getProductBySku(sku);
        validProducts.put(sku, product);
      } catch (Exception e) {
        invalidSkus.add(sku);
      }
    }
    
    if (!invalidSkus.isEmpty()) {
      throw new IllegalArgumentException("SKUs inválidos encontrados: " + String.join(", ", invalidSkus));
    }
    
    return validProducts;
  }
  
  /**
   * Calcula preço total usando preços autoritativos da API
   */
  public double calculateTotalWithApiPrices(java.util.Map<String, Integer> skuQuantities) {
    double total = 0.0;
    
    for (java.util.Map.Entry<String, Integer> entry : skuQuantities.entrySet()) {
      String sku = entry.getKey();
      int qty = entry.getValue();
      
      DummyJsonProduct product = getProductBySku(sku);
      double unitPrice = product.getFinalPrice();
      total += qty * unitPrice;
    }
    
    return total;
  }
  
  /**
   * Limpa cache de produtos
   */
  public void clearCache() {
    productCache.clear();
  }
  
  /**
   * Estatísticas do cache
   */
  public int getCacheSize() {
    return productCache.size();
  }
}