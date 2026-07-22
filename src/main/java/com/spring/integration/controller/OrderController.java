package com.spring.integration.controller;

import com.spring.integration.gateway.OrderGateway;
import com.spring.integration.model.Order;
import com.spring.integration.model.OrderConfirmation;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Contrôleur REST : le point d'entrée HTTP de l'application.
 *
 * <p>Il ne connaît RIEN de la plomberie Spring Integration. Il se contente
 * d'appeler {@link OrderGateway#placeOrder(Order)}, comme une méthode Java normale.
 * C'est le Gateway qui transforme cet appel en message et déclenche tout le flux.</p>
 *
 * <p>C'est la démonstration de la <b>séparation des responsabilités</b> :</p>
 * <ul>
 *     <li>le contrôleur gère HTTP ;</li>
 *     <li>le Gateway fait le pont vers la messagerie ;</li>
 *     <li>le flux orchestre les étapes ;</li>
 *     <li>le service contient la logique métier.</li>
 * </ul>
 */
@RestController
@RequestMapping("/orders")
public class OrderController {

    private final OrderGateway orderGateway;

    // Injection par constructeur du Gateway (Spring fournit le proxy généré).
    public OrderController(OrderGateway orderGateway) {
        this.orderGateway = orderGateway;
    }

    /**
     * Reçoit une commande en JSON, la fait passer dans le flux, et renvoie la confirmation.
     *
     * <p>Exemple d'appel :</p>
     * <pre>
     * curl -X POST http://localhost:8080/orders \
     *      -H "Content-Type: application/json" \
     *      -d '{"product":"Livre","quantity":2,"amount":39.9,"type":"EXPRESS"}'
     * </pre>
     */
    @PostMapping
    public OrderConfirmation placeOrder(@RequestBody Order order) {
        // Cet appel "simple" déclenche : filter -> transform -> route -> service activator,
        // puis récupère la réponse. Toute la magie est dans le Gateway + le flux.
        return orderGateway.placeOrder(order);
    }
}
