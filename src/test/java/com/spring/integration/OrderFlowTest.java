package com.spring.integration;

import com.spring.integration.gateway.OrderGateway;
import com.spring.integration.model.Order;
import com.spring.integration.model.OrderConfirmation;
import com.spring.integration.model.OrderType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test de bout en bout du flux d'intégration.
 *
 * <p>On appelle directement le Gateway (comme le ferait le contrôleur) et on vérifie
 * la confirmation renvoyée à la sortie du flux. C'est la façon la plus simple de
 * tester un flux Spring Integration : par sa porte d'entrée.</p>
 */
@SpringBootTest
class OrderFlowTest {

    @Autowired
    private OrderGateway orderGateway;

    @Test
    void commandeExpress_estRoutéeVersLaBrancheExpress() {
        Order order = new Order("Livre", 2, 39.90, OrderType.EXPRESS);

        OrderConfirmation confirmation = orderGateway.placeOrder(order);

        assertThat(confirmation.getTrackingId()).startsWith("EXP-");
        assertThat(confirmation.getMessage()).contains("EXPRESS");
    }

    @Test
    void commandeStandard_estRoutéeVersLaBrancheStandard() {
        Order order = new Order("Clavier", 1, 79.00, OrderType.STANDARD);

        OrderConfirmation confirmation = orderGateway.placeOrder(order);

        assertThat(confirmation.getTrackingId()).startsWith("STD-");
        assertThat(confirmation.getMessage()).contains("STANDARD");
    }

    @Test
    void commandeAvecMontantNul_estRejetéeParLeFiltre() {
        Order order = new Order("Stylo", 1, 0.0, OrderType.STANDARD);

        OrderConfirmation confirmation = orderGateway.placeOrder(order);

        assertThat(confirmation.getTrackingId()).isEqualTo("N/A");
        assertThat(confirmation.getMessage()).contains("refusée");
    }
}
