package com.example.challenge.service;

import com.example.challenge.api.dto.DummyJsonProduct;
import com.example.challenge.integration.DummyJsonProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Testes para DummyJsonProductService:
 * - produto existente na API
 * - produto inexistente 
 * - erro de rede
 * - cache em memória
 * - cálculo de preço com desconto
 */
@ExtendWith(MockitoExtension.class)
class DummyJsonProductServiceTest {

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private DummyJsonProperties dummyJsonProperties;

    @InjectMocks
    private DummyJsonProductService productService;

    private DummyJsonProduct mockProduct;
    private DummyJsonProperties.Products mockProductsConfig;

    @BeforeEach
    void setUp() {
        mockProduct = new DummyJsonProduct();
        mockProduct.setId(1);
        mockProduct.setTitle("iPhone 12");
        mockProduct.setPrice(999.99);
        mockProduct.setDiscountPercentage(10.0);
        mockProduct.setRating(4.5);
        mockProduct.setBrand("Apple");
        mockProduct.setCategory("smartphones");
        
        mockProductsConfig = new DummyJsonProperties.Products();
        mockProductsConfig.setEnableCache(true);
        mockProductsConfig.setCacheExpirationMs(300000); // 5 minutos
        mockProductsConfig.setMaxRetries(3);
        mockProductsConfig.setTimeoutMs(5000);
        
        when(dummyJsonProperties.getBaseUrl()).thenReturn("https://dummyjson.com");
        when(dummyJsonProperties.getProducts()).thenReturn(mockProductsConfig);
    }

    @Test
    void testGetProductBySku_ExistingProduct() {
        String sku = "1";
        when(restTemplate.getForObject(eq("https://dummyjson.com/products/1"), eq(DummyJsonProduct.class)))
            .thenReturn(mockProduct);

        DummyJsonProduct result = productService.getProductBySku(sku);

        assertNotNull(result);
        assertEquals(1, result.getId());
        assertEquals("iPhone 12", result.getTitle());
        assertEquals(999.99, result.getPrice());
        assertEquals(10.0, result.getDiscountPercentage());
        assertEquals(899.991, result.getFinalPrice(), 0.001); // 999.99 * (1 - 0.10)
        
        verify(restTemplate, times(1)).getForObject(anyString(), eq(DummyJsonProduct.class));
    }

    @Test
    void testGetProductBySku_NonExistingProduct() {
        String sku = "9999";
        when(restTemplate.getForObject(eq("https://dummyjson.com/products/9999"), eq(DummyJsonProduct.class)))
            .thenThrow(new HttpClientErrorException(org.springframework.http.HttpStatus.NOT_FOUND));

        assertThrows(IllegalArgumentException.class, 
                     () -> productService.getProductBySku(sku),
                     "Produto inexistente deve lançar IllegalArgumentException");
        
        verify(restTemplate, times(1)).getForObject(anyString(), eq(DummyJsonProduct.class));
    }

    @Test
    void testGetProductBySku_NetworkError() {
        String sku = "1";
        when(restTemplate.getForObject(eq("https://dummyjson.com/products/1"), eq(DummyJsonProduct.class)))
            .thenThrow(new ResourceAccessException("Connection timeout"));

        assertThrows(RuntimeException.class, 
                     () -> productService.getProductBySku(sku),
                     "Erro de rede deve lançar RuntimeException");
        
        verify(restTemplate, times(1)).getForObject(anyString(), eq(DummyJsonProduct.class));
    }

    @Test
    void testGetProductBySku_CacheEnabled() {
        String sku = "1";
        when(restTemplate.getForObject(eq("https://dummyjson.com/products/1"), eq(DummyJsonProduct.class)))
            .thenReturn(mockProduct);

        // Primeira chamada - deve fazer requisição
        DummyJsonProduct result1 = productService.getProductBySku(sku);
        assertNotNull(result1);
        
        // Segunda chamada - deve usar cache (não deve fazer nova requisição)
        DummyJsonProduct result2 = productService.getProductBySku(sku);
        assertNotNull(result2);
        
        // Apenas uma chamada para a API (segunda vem do cache)
        verify(restTemplate, times(1)).getForObject(anyString(), eq(DummyJsonProduct.class));
    }

    @Test
    void testGetProductBySku_CacheDisabled() {
        // Desabilitar cache
        mockProductsConfig.setEnableCache(false);
        String sku = "1";
        
        when(restTemplate.getForObject(eq("https://dummyjson.com/products/1"), eq(DummyJsonProduct.class)))
            .thenReturn(mockProduct);

        // Primeira chamada
        DummyJsonProduct result1 = productService.getProductBySku(sku);
        assertNotNull(result1);
        
        // Segunda chamada - deve fazer nova requisição (cache desabilitado)
        DummyJsonProduct result2 = productService.getProductBySku(sku);
        assertNotNull(result2);
        
        // Duas chamadas para a API
        verify(restTemplate, times(2)).getForObject(anyString(), eq(DummyJsonProduct.class));
    }

    @Test
    void testValidateSkus_AllValid() {
        List<String> skus = List.of("1", "2", "3");
        
        // Mockar produtos para cada SKU
        when(restTemplate.getForObject(eq("https://dummyjson.com/products/1"), eq(DummyJsonProduct.class)))
            .thenReturn(mockProduct);
        when(restTemplate.getForObject(eq("https://dummyjson.com/products/2"), eq(DummyJsonProduct.class)))
            .thenReturn(mockProduct);
        when(restTemplate.getForObject(eq("https://dummyjson.com/products/3"), eq(DummyJsonProduct.class)))
            .thenReturn(mockProduct);

        Map<String, DummyJsonProduct> result = productService.validateSkus(skus);

        assertEquals(3, result.size());
        assertTrue(result.containsKey("1"));
        assertTrue(result.containsKey("2"));
        assertTrue(result.containsKey("3"));
        
        verify(restTemplate, times(3)).getForObject(anyString(), eq(DummyJsonProduct.class));
    }

    @Test
    void testValidateSkus_OneInvalid() {
        List<String> skus = List.of("1", "invalid", "3");
        
        when(restTemplate.getForObject(eq("https://dummyjson.com/products/1"), eq(DummyJsonProduct.class)))
            .thenReturn(mockProduct);
        when(restTemplate.getForObject(eq("https://dummyjson.com/products/invalid"), eq(DummyJsonProduct.class)))
            .thenThrow(new HttpClientErrorException(org.springframework.http.HttpStatus.NOT_FOUND));
        when(restTemplate.getForObject(eq("https://dummyjson.com/products/3"), eq(DummyJsonProduct.class)))
            .thenReturn(mockProduct);

        assertThrows(IllegalArgumentException.class, 
                     () -> productService.validateSkus(skus),
                     "SKU inválido deve resultar em IllegalArgumentException");
        
        verify(restTemplate, times(3)).getForObject(anyString(), eq(DummyJsonProduct.class));
    }

    @Test
    void testValidateSkus_AllInvalid() {
        List<String> skus = List.of("invalid1", "invalid2");
        
        when(restTemplate.getForObject(anyString(), eq(DummyJsonProduct.class)))
            .thenThrow(new HttpClientErrorException(org.springframework.http.HttpStatus.NOT_FOUND));

        assertThrows(IllegalArgumentException.class, 
                     () -> productService.validateSkus(skus));
        
        verify(restTemplate, times(2)).getForObject(anyString(), eq(DummyJsonProduct.class));
    }

    @Test
    void testCalculateTotalWithApiPrices() {
        // Mockar produto
        DummyJsonProduct product1 = new DummyJsonProduct();
        product1.setPrice(100.0);
        product1.setDiscountPercentage(0); // Sem desconto
        
        DummyJsonProduct product2 = new DummyJsonProduct();
        product2.setPrice(200.0);
        product2.setDiscountPercentage(10.0); // 10% de desconto
        
        // Map de produtos
        Map<String, DummyJsonProduct> products = new HashMap<>();
        products.put("1", product1);
        products.put("2", product2);
        
        // Map de quantidades
        Map<String, Integer> skuQuantities = Map.of("1", 2, "2", 1);
        
        // Mockar chamadas para API
        when(restTemplate.getForObject(eq("https://dummyjson.com/products/1"), eq(DummyJsonProduct.class)))
            .thenReturn(product1);
        when(restTemplate.getForObject(eq("https://dummyjson.com/products/2"), eq(DummyJsonProduct.class)))
            .thenReturn(product2);

        double total = productService.calculateTotalWithApiPrices(skuQuantities);

        // Cálculo: (2 * 100.0) + (1 * 180.0) = 200.0 + 180.0 = 380.0
        assertEquals(380.0, total, 0.01);
    }

    @Test
    void testClearCache() {
        String sku = "1";
        when(restTemplate.getForObject(eq("https://dummyjson.com/products/1"), eq(DummyJsonProduct.class)))
            .thenReturn(mockProduct);

        // Carregar produto no cache
        productService.getProductBySku(sku);
        assertEquals(1, productService.getCacheSize());

        // Limpar cache
        productService.clearCache();
        assertEquals(0, productService.getCacheSize());
    }

    @Test
    void testGetFinalPrice_WithoutDiscount() {
        mockProduct.setDiscountPercentage(0.0);
        assertEquals(999.99, mockProduct.getFinalPrice(), 0.01);
    }

    @Test
    void testGetFinalPrice_WithDiscount() {
        mockProduct.setDiscountPercentage(10.0);
        assertEquals(899.991, mockProduct.getFinalPrice(), 0.001);
    }

    @Test
    void testGetFinalPrice_HighDiscount() {
        mockProduct.setPrice(100.0);
        mockProduct.setDiscountPercentage(50.0);
        assertEquals(50.0, mockProduct.getFinalPrice(), 0.01);
    }
}