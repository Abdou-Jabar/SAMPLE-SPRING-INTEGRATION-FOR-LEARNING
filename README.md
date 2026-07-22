# Guide & Démonstration : Spring Integration par la Pratique

Bienvenue dans mon projet de démonstration de **Spring Integration** !

J'ai conçu ce projet pour illustrer de façon concrète et fonctionnelle comment mettre en place un pipeline de traitement de données (ici, un pipeline de traitement de commandes) en utilisant **Spring Boot** et **Spring Integration (Java DSL)**.

L'objectif de ce projet est de vous expliquer simplement les principes d'une architecture orientée événements / messages et de vous montrer comment découpler totalement le code métier de la plomberie d'intégration.

---

## 1. C'est quoi Spring Integration ? (L'idée reçue & ma vision)

Dans la plupart de nos applications traditionnelles, les objets s'appellent directement entre eux (méthode A appelle méthode B). Quand la logique grandit ou qu'il faut ajouter des filtres, des contrôles, ou du routage, le code devient vite un plat de spaghettis.

Pour résoudre ça, j'ai appliqué dans ce projet le style d'architecture **« Pipes and Filters »** (tuyaux et filtres), issu des célèbres design patterns *Enterprise Integration Patterns (EIP)*.

**Le principe que j'ai mis en œuvre :**
1. Mes composants ne s'appellent plus directement.
2. Ils s'échangent des **messages** à travers des **canaux** (Message Channels).
3. Chaque composant (endpoint) réalise **une seule tâche** (filtrer, enrichir/transformer, router, exécuter la logique métier) et dépose le résultat sur le canal suivant.

### Pourquoi cette approche ?
- **Découplage total :** Les composants métier n'ont aucune idée d'où viennent les données ni où elles vont.
- **Code métier pur (POJO) :** Le code de gestion des commandes ne contient aucune référence aux bibliothèques de messagerie.
- **Évolutivité :** On peut réorganiser, ajouter ou supprimer une étape dans le pipeline sans toucher au code métier.

---

## 2. Les 3 briques fondamentales

Voici les trois concepts clés que j'utilise dans ce projet :

| Brique | Mon explication | Mon analogie |
|--------|-----------------|--------------|
| **Message** | L'enveloppe contenant les données métier (`payload`) et des métadonnées (`headers` : ID, timestamp, canal de retour...). | Une lettre avec sa feuille (payload) et son enveloppe avec adresse (headers). |
| **Message Channel** | Le tuyau invisible qui transporte les messages d'un composant à l'autre. | Un tapis roulant dans une usine. |
| **Message Endpoint** | Le poste de travail connecté aux tuyaux qui traite le message (filtre, transformation, routage, action métier). | Un ouvrier spécialisé sur la chaîne d'assemblage. |

Dans mon code, un message ressemble conceptuellement à ceci :
```text
Message<Order>
 ├─ payload : Order(product="Livre", quantity=2, amount=39.9, type=EXPRESS)
 └─ headers : { id=..., timestamp=..., replyChannel=... }
```

---

## 3. Les Endpoints que j'ai intégrés

J'ai combiné plusieurs types d'endpoints classiques d'EIP dans ce pipeline :

- **Gateway (`OrderGateway`) :** Ma porte d'entrée. Elle transforme un simple appel de méthode Java en message Spring Integration.
- **Filter :** Il élimine les commandes invalides (ex: `amount <= 0`) et les redirige vers un canal de rejet.
- **Transformer :** Il enrichit et prépare la commande avant traitement.
- **Router :** Il aiguille la commande vers le bon sous-flux selon son type (`STANDARD` ou `EXPRESS`).
- **Service Activator :** Il connecte mes méthodes Java métier "pures" au flux d'intégration.

---

## 4. Architecture & Parcours d'une commande dans mon pipeline

Voici le flux complet que j'ai construit pour traiter les commandes arrivant via une API REST :

```text
   Contrôleur REST (POST /orders)
        │
        ▼
   OrderGateway.placeOrder(order)          ← GATEWAY : enveloppe l'Order dans un Message
        │
        ▼
   [orderInputChannel]                     ← CANAL d'entrée
        │
        ▼
   FILTER : montant > 0 ?
        │                └── NON ──► [invalidOrderChannel] ─► handleInvalid() ──► "Refusée"
        │  OUI
        ▼
   TRANSFORMER : enrich(order)             ← Enrichissement & Log de la commande
        │
        ▼
   [routingChannel]                        ← CANAL intermédiaire (nommé)
        │
        ▼
   ROUTER : selon order.getType()
        ├── STANDARD ──► handleStandard()  ← SERVICE ACTIVATOR
        └── EXPRESS  ──► handleExpress()   ← SERVICE ACTIVATOR
        │
        ▼
   OrderConfirmation (Réponse)
        │
        ▼
   Retour automatique au Gateway ──► Contrôleur REST ──► Client HTTP
```

---

## 5. Structure de mon projet

J'ai organisé le projet de manière claire et modulaire :

```text
src/main/java/com/spring/integration/
├── IntegrationApplication.java      # Classe principale de démarrage Spring Boot
├── model/
│   ├── Order.java                   # Payload d'entrée (la commande)
│   ├── OrderType.java               # Enumération (STANDARD / EXPRESS)
│   └── OrderConfirmation.java       # Payload de sortie (confirmation/rejet)
├── gateway/
│   └── OrderGateway.java            # Interface Gateway avec annotation @MessagingGateway
├── config/
│   └── OrderIntegrationConfig.java  # Le cœur du projet : Définition du flux Java DSL
├── service/
│   └── OrderService.java            # Logique métier pure (POJO sans dépendance SI)
└── controller/
    └── OrderController.java         # Exposition REST (POST /orders)
```

---

## 6. Explication du code pas à pas

### 6.1 Le Gateway (Porte d'entrée transparente)
```java
@MessagingGateway
public interface OrderGateway {
    @Gateway(requestChannel = "orderInputChannel")
    OrderConfirmation placeOrder(Order order);
}
```
* **Ce que j'ai fait ici :** C'est une simple interface Java ! Je n'ai rédigé aucune implémentation. Spring va créer un proxy dynamiquement.
* **Résultat :** Dans mon contrôleur REST, j'appelle `orderGateway.placeOrder(order)` comme une méthode classique, sans me soucier de la messagerie sous-jacente.
* *Note :* J'ai ajouté `@IntegrationComponentScan` dans ma classe de configuration pour que Spring scanne cette interface.

### 6.2 Le Flux principal (Java DSL)
Dans `OrderIntegrationConfig.java`, j'ai choisi le **Java DSL** pour une lisibilité maximale :

```java
@Bean
public IntegrationFlow orderProcessingFlow(OrderService orderService) {
    return IntegrationFlow
            .from("orderInputChannel")                                   // 1. Point d'entrée
            .<Order>filter(order -> order.getAmount() > 0,               // 2. Filtre
                    f -> f.discardChannel("invalidOrderChannel"))
            .<Order, Order>transform(orderService::enrich)              // 3. Transformation
            .channel(MessageChannels.direct("routingChannel"))          // 4. Canal intermédiaire
            .<Order, OrderType>route(Order::getType, mapping -> mapping // 5. Routage dynamique
                    .subFlowMapping(OrderType.STANDARD,
                            sf -> sf.handle(Order.class,
                                    (p, h) -> orderService.handleStandard(p)))
                    .subFlowMapping(OrderType.EXPRESS,
                            sf -> sf.handle(Order.class,
                                    (p, h) -> orderService.handleExpress(p))))
            .get();                                                     // 6. Construction du flux
}
```

### 6.3 Le Code Métier (POJO pur)
Mon service `OrderService.java` ne contient aucune annotation ni aucun import lié à Spring Integration :

```java
@Service
public class OrderService {
    public Order enrich(Order order) { ... }
    public OrderConfirmation handleStandard(Order order) { ... }
    public OrderConfirmation handleExpress(Order order) { ... }
    public OrderConfirmation handleInvalid(Order order) { ... }
}
```
Cette isolation me permet de tester mes règles métier facilement en dehors du flux d'intégration.

### 6.4 Le Flux de Gestion des Rejets
Lorsque le filtre rejette une commande (`amount <= 0`), elle est aiguillée vers `invalidOrderChannel`. J'ai créé un second flux pour écouter ce canal :

```java
@Bean
public IntegrationFlow invalidOrderFlow(OrderService orderService) {
    return IntegrationFlow
            .from("invalidOrderChannel")
            .<Order, OrderConfirmation>transform(orderService::handleInvalid)
            .get();
}
```
Grâce aux métadonnées (`headers`), la réponse de rejet est automatiquement réexpédiée au `replyChannel` du Gateway.

---

## 7. Comment lancer et tester mon application

### Lancer l'application
Vous pouvez démarrer l'application avec le wrapper Maven :
```bash
./mvnw spring-boot:run
```
Le serveur démarre sur le port `8080`.

### Résultats des tests manuels (cURL)

**1. Commande EXPRESS (Valide)**
```bash
curl -X POST http://localhost:8080/orders -H "Content-Type: application/json" \
     -d '{"product":"Livre","quantity":2,"amount":39.9,"type":"EXPRESS"}'
```
*Réponse obtenue :*
```json
{"trackingId":"EXP-a082def1","message":"Commande EXPRESS traitée. Livraison sous 24 h."}
```

**2. Commande STANDARD (Valide)**
```bash
curl -X POST http://localhost:8080/orders -H "Content-Type: application/json" \
     -d '{"product":"Clavier","quantity":1,"amount":79.0,"type":"STANDARD"}'
```
*Réponse obtenue :*
```json
{"trackingId":"STD-19c89d3f","message":"Commande STANDARD traitée. Livraison sous 5 jours."}
```

**3. Commande Invalide (Montant = 0)**
```bash
curl -X POST http://localhost:8080/orders -H "Content-Type: application/json" \
     -d '{"product":"Stylo","quantity":1,"amount":0,"type":"STANDARD"}'
```
*Réponse obtenue :*
```json
{"trackingId":"N/A","message":"Commande refusée : le montant doit être strictement positif."}
```

---

## 8. Tests unitaires et d'intégration

J'ai également écrit une suite de tests automatisés dans `OrderFlowTest.java` pour valider le comportement global via le Gateway :

```bash
./mvnw test
```

---

## 9. Pourquoi j'ai préféré le Java DSL aux Annotations

Spring Integration permet deux approches de configuration : le **Java DSL** et les **Annotations** (ex: `@Filter`, `@Transformer`, `@ServiceActivator`).

J'ai fait le choix explicite du **Java DSL** pour ce projet pour les raisons suivantes :
- **Vision d'ensemble :** Le pipeline complet est décrit au même endroit de manière fluide et lisible.
- **Maintenance :** Avec les annotations, la logique du flux est dispersée sur plusieurs classes/méthodes, ce qui me rendrait la chaîne plus difficile à visualiser d'un seul coup d'œil.

---

## 10. Résumé des points clés

1. **Messages = Payload + Headers.** Tout transite sous forme de messages enveloppés.
2. **Canaux = Découplage.** Émetteurs et récepteurs ne se connaissent pas.
3. **Endpoints spécialisés.** Filtre, Transformer, Router et Service Activator ont chacun un rôle précis.
4. **Gateway.** Encapsule la complexité de l'intégration derrière une simple interface Java.
5. **POJO.** Le code métier reste propre et indépendant du framework.

---

## 11. Pour aller plus loin

Si vous souhaitez explorer davantage les concepts que j'ai présentés ici :
- [Documentation officielle Spring Integration](https://docs.spring.io/spring-integration/reference/)
- [Spring Integration Java DSL Reference](https://docs.spring.io/spring-integration/reference/dsl.html)
- [Spring Integration Samples (GitHub)](https://github.com/spring-projects/spring-integration-samples)

N'hésitez pas à forker le projet, tester et modifier les flux pour vous entraîner !
