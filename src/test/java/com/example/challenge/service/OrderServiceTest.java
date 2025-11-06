package com.example.challenge.service;

import com.example.challenge.api.dto.NewOrderRequest;
import com.example.challenge.api.dto.UpdateOrderRequest;
import com.example.challenge.domain.Order;
import com.example.challenge.domain.OrderItem;
import com.example.challenge.domain.OrderStatus;
import com.example.challenge.repo.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Testes para OrderService:
 * - criação, atualização condicional, exclusão condicional e markPaid/markFailed
 */
@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @InjectMocks
    private OrderService orderService;

    private Order mockOrder;
    private NewOrderRequest newOrderRequest;
    private UpdateOrderRequest updateOrderRequest;

    @BeforeEach
    void setUp() {
        mockOrder = new Order();
        // Order ID is generated in constructor, no setter available
        mockOrder.setCustomerId("customer1");
        mockOrder.setStatus(OrderStatus.NEW);

        // Mock OrderItems
        List<com.example.challenge.api.dto.NewOrderRequest.Item> items = new ArrayList<>();
        com.example.challenge.api.dto.NewOrderRequest.Item item1 = new com.example.challenge.api.dto.NewOrderRequest.Item();
        item1.setSku("SKU001");
        item1.setQty(2);
        item1.setUnitPrice(100.0);
        items.add(item1);

        newOrderRequest = new NewOrderRequest();
        newOrderRequest.setCustomerId("customer1");
        newOrderRequest.setItems(items);

        // Mock UpdateRequest
        List<com.example.challenge.api.dto.UpdateOrderRequest.Item> updateItems = new ArrayList<>();
        com.example.challenge.api.dto.UpdateOrderRequest.Item updateItem = new com.example.challenge.api.dto.UpdateOrderRequest.Item();
        updateItem.setSku("SKU002");
        updateItem.setQty(1);
        updateItem.setUnitPrice(500.0);
        updateItems.add(updateItem);

        updateOrderRequest = new UpdateOrderRequest();
        updateOrderRequest.setItems(updateItems);
    }

    @Test
    void testCreateOrder() {
        when(orderRepository.save(any(Order.class))).thenReturn(mockOrder);

        Order result = orderService.create(newOrderRequest);

        assertEquals("customer1", result.getCustomerId());
        assertEquals(OrderStatus.NEW, result.getStatus());
        assertEquals(200.0, result.getTotal()); // 2 * 100.0
        verify(orderRepository, times(1)).save(any(Order.class));
    }

    @Test
    void testCreateOrderWithMultipleItems() {
        List<com.example.challenge.api.dto.NewOrderRequest.Item> items = new ArrayList<>();
        
        com.example.challenge.api.dto.NewOrderRequest.Item item1 = new com.example.challenge.api.dto.NewOrderRequest.Item();
        item1.setSku("SKU001");
        item1.setQty(2);
        item1.setUnitPrice(100.0);
        items.add(item1);
        
        com.example.challenge.api.dto.NewOrderRequest.Item item2 = new com.example.challenge.api.dto.NewOrderRequest.Item();
        item2.setSku("SKU002");
        item2.setQty(3);
        item2.setUnitPrice(50.0);
        items.add(item2);

        NewOrderRequest request = new NewOrderRequest();
        request.setCustomerId("customer1");
        request.setItems(items);

        when(orderRepository.save(any(Order.class))).thenReturn(mockOrder);

        Order result = orderService.create(request);

        assertEquals(350.0, result.getTotal()); // (2 * 100.0) + (3 * 50.0)
    }

    @Test
    void testGetOrder() {
        when(orderRepository.findById("12345")).thenReturn(Optional.of(mockOrder));

        Optional<Order> result = orderService.get("12345");

        assertTrue(result.isPresent());
        assertEquals("12345", result.get().getId());
    }

    @Test
    void testGetOrderNotFound() {
        when(orderRepository.findById("nonexistent")).thenReturn(Optional.empty());

        Optional<Order> result = orderService.get("nonexistent");

        assertFalse(result.isPresent());
    }

    @Test
    void testUpdateItemsSuccess() {
        mockOrder.setStatus(OrderStatus.NEW);
        when(orderRepository.findById("12345")).thenReturn(Optional.of(mockOrder));
        when(orderRepository.save(any(Order.class))).thenReturn(mockOrder);

        Order result = orderService.updateItems("12345", updateOrderRequest);

        assertEquals(500.0, result.getTotal()); // 1 * 500.0
        verify(orderRepository, times(1)).save(any(Order.class));
    }

    @Test
    void testUpdateItemsOrderNotFound() {
        when(orderRepository.findById("nonexistent")).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, 
                     () -> orderService.updateItems("nonexistent", updateOrderRequest));
    }

    @Test
    void testUpdateItemsWrongStatus() {
        mockOrder.setStatus(OrderStatus.PAID);
        when(orderRepository.findById("12345")).thenReturn(Optional.of(mockOrder));

        assertThrows(IllegalStateException.class,
                     () -> orderService.updateItems("12345", updateOrderRequest));
    }

    @Test
    void testDeleteSuccess() {
        mockOrder.setStatus(OrderStatus.NEW);
        when(orderRepository.findById("12345")).thenReturn(Optional.of(mockOrder));

        orderService.delete("12345");

        verify(orderRepository, times(1)).delete(mockOrder);
    }

    @Test
    void testDeleteOrderNotFound() {
        when(orderRepository.findById("nonexistent")).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
                     () -> orderService.delete("nonexistent"));
    }

    @Test
    void testDeleteWrongStatus() {
        mockOrder.setStatus(OrderStatus.PAID);
        when(orderRepository.findById("12345")).thenReturn(Optional.of(mockOrder));

        assertThrows(IllegalStateException.class,
                     () -> orderService.delete("12345"));
    }

    @Test
    void testMarkPaid() {
        when(orderRepository.findById("12345")).thenReturn(Optional.of(mockOrder));
        when(orderRepository.save(any(Order.class))).thenReturn(mockOrder);

        orderService.markPaid("12345");

        assertEquals(OrderStatus.PAID, mockOrder.getStatus());
        verify(orderRepository, times(1)).save(mockOrder);
    }

    @Test
    void testMarkFailed() {
        when(orderRepository.findById("12345")).thenReturn(Optional.of(mockOrder));
        when(orderRepository.save(any(Order.class))).thenReturn(mockOrder);

        orderService.markFailed("12345");

        assertEquals(OrderStatus.FAILED_PAYMENT, mockOrder.getStatus());
        verify(orderRepository, times(1)).save(mockOrder);
    }
}