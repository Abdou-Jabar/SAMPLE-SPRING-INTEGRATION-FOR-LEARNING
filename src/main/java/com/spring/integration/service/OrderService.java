package com.spring.integration.service;

import com.spring.integration.model.Order;
import com.spring.integration.model.OrderConfirmation;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Le code MÉTIER pur.
 *
 * <p>Point important : cette classe ne connaît RIEN de Spring Integration.
 * Pas d'import {@code org.springframework.integration.*}, pas de {@code Message},
 * pas de {@code Channel}. C'est un simple bean Spring avec des méthodes POJO.</p>
 *
 * <p>C'est le flux (dans {@code OrderIntegrationConfig}) qui va "brancher" ces méthodes
 * aux différentes étapes via des <b>Service Activators</b> ({@code .handle(...)}).
 * On sépare ainsi totalement la <b>logique de traitement</b> (ici) de la
 * <b>plomberie de messagerie</b> (là-bas). C'est tout l'intérêt du pattern.</p>
 */
@Service
public class OrderService {

    /**
     * TRANSFORMER : enrichit la commande.
     *
     * <p>On génère un identifiant de suivi et on l'ajoute à la commande.
     * Un transformer prend un payload et en renvoie un autre (ou le même, modifié).</p>
     */
    public Order enrich(Order order) {
        // On simule un enrichissement : ici on ne change pas la commande elle-même,
        // mais on pourrait, par exemple, appliquer une remise, compléter des champs, etc.
        System.out.println("🛠️  [TRANSFORMER] Enrichissement de la commande : " + order);
        return order;
    }

    /**
     * SERVICE ACTIVATOR de la branche STANDARD.
     *
     * @return la confirmation, qui deviendra le payload du message de réponse
     */
    public OrderConfirmation handleStandard(Order order) {
        String trackingId = "STD-" + UUID.randomUUID().toString().substring(0, 8);
        System.out.println("[STANDARD] Traitement standard de : " + order + " -> " + trackingId);
        return new OrderConfirmation(trackingId,
                "Commande STANDARD traitée. Livraison sous 5 jours.");
    }

    /**
     * SERVICE ACTIVATOR de la branche EXPRESS.
     *
     * @return la confirmation, qui deviendra le payload du message de réponse
     */
    public OrderConfirmation handleExpress(Order order) {
        String trackingId = "EXP-" + UUID.randomUUID().toString().substring(0, 8);
        System.out.println("[EXPRESS] Traitement prioritaire de : " + order + " -> " + trackingId);
        return new OrderConfirmation(trackingId,
                "Commande EXPRESS traitée. Livraison sous 24 h.");
    }

    /**
     * Traite les commandes REJETÉES par le filtre (montant <= 0).
     *
     * <p>Ces messages partent sur un canal séparé (le "discardChannel").
     * Comme le message conserve ses headers d'origine (dont "replyChannel"),
     * on peut renvoyer une confirmation de rejet qui remontera jusqu'au Gateway.</p>
     */
    public OrderConfirmation handleInvalid(Order order) {
        System.err.println("[REJET] Commande invalide : " + order);
        return new OrderConfirmation("N/A",
                "Commande refusée : le montant doit être strictement positif.");
    }
}
