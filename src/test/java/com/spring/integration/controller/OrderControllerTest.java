package com.spring.integration.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spring.integration.gateway.OrderGateway;
import com.spring.integration.model.Order;
import com.spring.integration.model.OrderConfirmation;
import com.spring.integration.model.OrderType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OrderController.class)
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private OrderGateway orderGateway;

    @Test
    void placeOrder_renvoieConfirmationLorsquAppele() throws Exception {
        Order order = new Order("Livre", 2, 39.90, OrderType.EXPRESS);
        OrderConfirmation confirmation = new OrderConfirmation("EXP-12345", "Commande EXPRESS traitée. Livraison sous 24 h.");

        given(orderGateway.placeOrder(any(Order.class))).willReturn(confirmation);

        mockMvc.perform(post("/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(order)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trackingId").value("EXP-12345"))
                .andExpect(jsonPath("$.message").value(containsString("EXPRESS")));
    }
}
