package com.spring.integration.config;

import com.spring.integration.model.Order;
import com.spring.integration.model.OrderType;
import com.spring.integration.service.OrderService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.annotation.IntegrationComponentScan;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.dsl.MessageChannels;

/**
 * ============================================================================
 *  LE FLUX D'INTÉGRATION (le cœur de Spring Integration)
 * ============================================================================
 *
 * <p>On décrit ici, de façon fluide (Java DSL), le "tuyau" par lequel chaque
 * commande va passer. Chaque appel chaîné ajoute une étape (un <b>endpoint</b>)
 * reliée à la précédente par un <b>canal</b> (MessageChannel).</p>
 *
 * <p>Vue d'ensemble du pipeline :</p>
 * <pre>
 *   Gateway (placeOrder)
 *        │  (Message&lt;Order&gt;)
 *        ▼
 *   [orderInputChannel]
 *        ▼
 *   FILTER  ── montant &lt;= 0 ? ──► [invalidOrderChannel] ─► handleInvalid()  (rejet)
 *        │  (garde les commandes valides)
 *        ▼
 *   TRANSFORMER  (enrich)
 *        ▼
 *   [routingChannel]
 *        ▼
 *   ROUTER  ──► STANDARD ─► handleStandard()  ┐
 *           └─► EXPRESS  ─► handleExpress()   ┘
 *        ▼
 *   OrderConfirmation  ──► réponse renvoyée automatiquement au Gateway
 * </pre>
 *
 * <p>Note : pas besoin de {@code @EnableIntegration} ici, car Spring Boot l'active
 * automatiquement via l'auto-configuration dès que {@code spring-boot-starter-integration}
 * est présent.</p>
 */
@Configuration
// Nécessaire pour que Spring détecte l'interface @MessagingGateway (OrderGateway)
// et lui génère un proxy. Spring Boot ne l'active pas automatiquement.
@IntegrationComponentScan(basePackages = "com.spring.integration.gateway")
public class OrderIntegrationConfig {

    /**
     * Flux principal de traitement des commandes.
     *
     * <p>Spring injecte le bean {@link OrderService} : on référence ses méthodes
     * (method references) pour brancher le code métier aux étapes du flux.</p>
     */
    @Bean
    public IntegrationFlow orderProcessingFlow(OrderService orderService) {
        return IntegrationFlow

                // 1) DÉPART DU FLUX ----------------------------------------------------
                // Le flux démarre en écoutant le canal "orderInputChannel".
                // C'est exactement le canal que le Gateway utilise (requestChannel).
                // S'il n'existe pas encore, le DSL crée automatiquement un DirectChannel.
                .from("orderInputChannel")

                // 2) FILTER ------------------------------------------------------------
                // Teste une condition (predicate) sur le payload.
                //  - condition vraie  -> le message continue ;
                //  - condition fausse -> le message est envoyé au "discardChannel".
                // Ici : on ne garde que les commandes dont le montant est strictement positif.
                .<Order>filter(order -> order.getAmount() > 0,
                        f -> f.discardChannel("invalidOrderChannel"))

                // 3) TRANSFORMER -------------------------------------------------------
                // Prend le payload, le transforme, et renvoie le nouveau payload.
                // Ici on "enrichit" la commande (log + éventuels champs complétés).
                .<Order, Order>transform(orderService::enrich)

                // 4) UN CANAL INTERMÉDIAIRE (facultatif, pour l'illustration) -----------
                // On peut nommer explicitement un canal entre deux étapes ;
                // pratique pour le monitoring/debug ou pour y brancher autre chose.
                .channel(MessageChannels.direct("routingChannel"))

                // 5) ROUTER ------------------------------------------------------------
                // Choisit la suite du parcours en fonction d'une clé calculée sur le message.
                // Clé = order.getType() (STANDARD ou EXPRESS).
                // Chaque clé mène à un "sous-flux" (subFlowMapping) qui appelle
                // le bon Service Activator (.handle -> méthode métier).
                .<Order, OrderType>route(Order::getType, mapping -> mapping
                        .subFlowMapping(OrderType.STANDARD,
                                sf -> sf.handle(Order.class,
                                        (payload, headers) -> orderService.handleStandard(payload)))
                        .subFlowMapping(OrderType.EXPRESS,
                                sf -> sf.handle(Order.class,
                                        (payload, headers) -> orderService.handleExpress(payload))))

                // 6) FIN --------------------------------------------------------------
                // .get() construit et renvoie l'objet IntegrationFlow.
                // La valeur renvoyée par handleStandard/handleExpress (OrderConfirmation)
                // devient le payload du message de réponse, renvoyé automatiquement au Gateway.
                .get();
    }

    /**
     * Petit flux séparé qui traite les messages REJETÉS par le filtre.
     *
     * <p>Il écoute le "invalidOrderChannel" (le discardChannel) et appelle
     * {@code handleInvalid()}. Cela montre qu'un canal peut relier des flux distincts.</p>
     */
    @Bean
    public IntegrationFlow invalidOrderFlow(OrderService orderService) {
        return IntegrationFlow
                .from("invalidOrderChannel")
                // Le message rejeté a gardé son header "replyChannel" : en renvoyant
                // une OrderConfirmation, celle-ci remonte automatiquement au Gateway.
                .<Order, com.spring.integration.model.OrderConfirmation>transform(orderService::handleInvalid)
                .get();
    }
}
