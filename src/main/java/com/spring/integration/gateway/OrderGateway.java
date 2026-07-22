package com.spring.integration.gateway;

import com.spring.integration.model.Order;
import com.spring.integration.model.OrderConfirmation;
import org.springframework.integration.annotation.MessagingGateway;

/**
 * PORTE D'ENTRÉE du flux (Messaging Gateway).
 *
 * <p>Un des principes fondamentaux de Spring Integration est de <b>ne pas polluer</b>
 * le code métier avec l'API de messagerie. Le {@code @MessagingGateway} réalise cela :
 * c'est une <b>simple interface Java</b> (aucune implémentation à écrire !) que Spring
 * transforme en proxy à l'exécution.</p>
 *
 * <p>Quand on appelle {@link #placeOrder(Order)} :</p>
 * <ol>
 *     <li>Spring enveloppe l'objet {@code Order} dans un {@code Message<Order>} ;</li>
 *     <li>il l'envoie sur le canal {@code orderInputChannel} (début du flux) ;</li>
 *     <li>il attend la réponse sur le canal de réponse (géré automatiquement) ;</li>
 *     <li>il extrait le payload de la réponse et le renvoie ({@code OrderConfirmation}).</li>
 * </ol>
 *
 * <p>Ainsi, l'appelant (notre contrôleur REST) a l'impression d'appeler une méthode
 * Java classique, alors qu'en réalité tout un pipeline de messagerie s'exécute derrière.</p>
 */
@MessagingGateway
public interface OrderGateway {

    /**
     * Envoie une commande dans le flux d'intégration et récupère la confirmation.
     *
     * <p>{@code defaultRequestChannel} indique le canal sur lequel le message est déposé.
     * Ce nom doit correspondre au canal de départ défini dans la configuration du flux.</p>
     *
     * @param order la commande à traiter (deviendra le payload du message)
     * @return la confirmation renvoyée à la fin du flux
     */
    @org.springframework.integration.annotation.Gateway(requestChannel = "orderInputChannel")
    OrderConfirmation placeOrder(Order order);
}
