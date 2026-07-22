package com.spring.integration.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Représente une commande passée par un client.
 *
 * <p>C'est le <b>payload</b> (la charge utile) de nos messages Spring Integration.
 * Rappel : un {@code Message<T>} de Spring Integration est composé de deux parties :
 * <ul>
 *     <li>le <b>payload</b> : l'objet métier transporté (ici, un {@code Order}) ;</li>
 *     <li>les <b>headers</b> : des métadonnées (id, timestamp, etc.).</li>
 * </ul>
 * Ici, {@code Order} est simplement le payload : Spring Integration l'enveloppera
 * automatiquement dans un {@code Message} lorsqu'il entrera dans le flux.</p>
 *
 * <p>Les annotations Lombok ({@code @Data}, etc.) génèrent automatiquement les getters,
 * setters, {@code toString()}, {@code equals()}/{@code hashCode()} et les constructeurs.</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Order {

    /** Identifiant du produit commandé. */
    private String product;

    /** Quantité commandée. */
    private int quantity;

    /** Montant total de la commande (en euros). Sert au filtre : une commande <= 0 est invalide. */
    private double amount;

    /** Type de commande : sert de clé au routeur (STANDARD / EXPRESS). */
    private OrderType type;
}
