package com.spring.integration.model;

/**
 * Type de commande.
 *
 * <p>Cette énumération sert de "clé de routage" : dans notre flux Spring Integration,
 * le <b>Router</b> regardera cette valeur pour décider vers quelle branche (sous-flux)
 * envoyer le message.</p>
 */
public enum OrderType {
    /** Commande standard : livraison normale. */
    STANDARD,
    /** Commande express : traitement prioritaire. */
    EXPRESS
}
