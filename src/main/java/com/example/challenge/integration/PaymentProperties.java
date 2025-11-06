package com.example.challenge.integration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "payment")
public class PaymentProperties {
  private String successUrl;
  private String failureUrl;
  private Retry retry = new Retry();

  public static class Retry {
    private int maxRedeliveries = 3;
    private long redeliveryDelayMs = 200;
    private double backoffMultiplier = 2.0;
    public int getMaxRedeliveries() { return maxRedeliveries; }
    public void setMaxRedeliveries(int v) { this.maxRedeliveries = v; }
    public long getRedeliveryDelayMs() { return redeliveryDelayMs; }
    public void setRedeliveryDelayMs(long v) { this.redeliveryDelayMs = v; }
    public double getBackoffMultiplier() { return backoffMultiplier; }
    public void setBackoffMultiplier(double v) { this.backoffMultiplier = v; }
  }

  public String getSuccessUrl() { return successUrl; }
  public void setSuccessUrl(String successUrl) { this.successUrl = successUrl; }
  public String getFailureUrl() { return failureUrl; }
  public void setFailureUrl(String failureUrl) { this.failureUrl = failureUrl; }
  public Retry getRetry() { return retry; }
  public void setRetry(Retry retry) { this.retry = retry; }
}
