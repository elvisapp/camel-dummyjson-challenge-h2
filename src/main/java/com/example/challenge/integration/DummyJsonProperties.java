package com.example.challenge.integration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Propriedades de configuração para integração com DummyJSON
 */
@Component
@ConfigurationProperties(prefix = "dummyjson")
public class DummyJsonProperties {
  
  private String baseUrl = "https://dummyjson.com";
  private Products products = new Products();
  
  public static class Products {
    private int timeoutMs = 5000;
    private int maxRetries = 3;
    private boolean enableCache = true;
    private long cacheExpirationMs = 300000; // 5 minutos
    
    public int getTimeoutMs() { return timeoutMs; }
    public void setTimeoutMs(int timeoutMs) { this.timeoutMs = timeoutMs; }
    
    public int getMaxRetries() { return maxRetries; }
    public void setMaxRetries(int maxRetries) { this.maxRetries = maxRetries; }
    
    public boolean isEnableCache() { return enableCache; }
    public void setEnableCache(boolean enableCache) { this.enableCache = enableCache; }
    
    public long getCacheExpirationMs() { return cacheExpirationMs; }
    public void setCacheExpirationMs(long cacheExpirationMs) { this.cacheExpirationMs = cacheExpirationMs; }
  }
  
  public String getBaseUrl() { return baseUrl; }
  public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
  
  public Products getProducts() { return products; }
  public void setProducts(Products products) { this.products = products; }
}