package com.spring.integration.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Résultat renvoyé au bout du flux : la confirmation de traitement d'une commande.
 *
 * <p>C'est le payload du message de <b>réponse</b> (reply). Grâce au
 * {@code @MessagingGateway}, cette valeur remonte automatiquement jusqu'à
 * l'appelant (notre contrôleur REST), via le header "replyChannel".</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderConfirmation {

    /** Identifiant unique attribué à la commande pendant le traitement. */
    private String trackingId;

    /** Message lisible décrivant ce qui a été fait. */
    private String message;
}
