# Comprendre Spring Integration — Guide par l'exemple

Ce guide accompagne un exemple **complet et fonctionnel** : un petit pipeline de
traitement de commandes. Il a été testé de bout en bout (l'application démarre et
répond réellement en HTTP). L'objectif est de te faire comprendre les concepts
fondamentaux de Spring Integration en les voyant à l'œuvre.

> Basé sur la [documentation officielle Spring Integration](https://docs.spring.io/spring-integration/reference/)
> (chapitres *Overview* et *Java DSL*).

---

## 1. C'est quoi Spring Integration ? (l'idée en 2 minutes)

Spring Integration applique un style d'architecture appelé **« Pipes and Filters »**
(tuyaux et filtres), issu du livre *Enterprise Integration Patterns* (EIP).

L'idée : au lieu que tes objets s'appellent directement les uns les autres, ils
**s'échangent des messages** à travers des **canaux**. Chaque étape de traitement
est un composant indépendant qui :

1. reçoit un message depuis un canal (le « tuyau » d'entrée) ;
2. fait **une seule** chose (filtrer, transformer, router, appeler un service…) ;
3. dépose le résultat sur un canal de sortie.

**Pourquoi c'est utile ?** Les composants sont **découplés** : ils ne se connaissent
pas entre eux, ils ne connaissent que les canaux. On peut ajouter, retirer ou
réorganiser des étapes sans toucher au code métier. Le code métier, lui, reste
**pur** (de simples classes Java, sans dépendance à l'API de messagerie).

---

## 2. Les 3 briques de base

| Brique | Rôle | Analogie |
|--------|------|----------|
| **Message** | Une enveloppe contenant un `payload` (l'objet métier) + des `headers` (métadonnées : id, timestamp, canal de réponse…). | Une lettre : le contenu + l'enveloppe avec l'adresse. |
| **Message Channel** | Le « tuyau » qui transporte les messages d'un composant à l'autre. Découple l'émetteur du récepteur. | Un tapis roulant. |
| **Message Endpoint** | Un composant branché sur des canaux qui traite les messages (filtre, transformer, routeur, service activator…). | Un poste de travail sur la chaîne. |

Un message ressemble à ça (conceptuellement) :

```
Message<Order>
 ├─ payload : Order(product="Livre", quantity=2, amount=39.9, type=EXPRESS)
 └─ headers : { id=..., timestamp=..., replyChannel=... }
```

---

## 3. Les types d'endpoints (les « postes de travail »)

Ce sont les patterns EIP les plus courants. Notre exemple en utilise plusieurs :

| Endpoint | Ce qu'il fait | Dans notre exemple |
|----------|---------------|--------------------|
| **Gateway** | Point d'entrée : transforme un appel de méthode Java en message, et récupère la réponse. | `OrderGateway.placeOrder(...)` |
| **Filter** | Laisse passer ou rejette un message selon une condition (`true`/`false`). | garde les commandes `amount > 0` |
| **Transformer** | Modifie le payload (ou les headers). | `enrich(...)` |
| **Router** | Choisit vers quel canal/branche envoyer le message. | route selon `STANDARD`/`EXPRESS` |
| **Service Activator** | Branche une méthode métier « normale » sur le flux. | `handleStandard(...)`, `handleExpress(...)` |
| **Splitter / Aggregator** | Découpe un message en plusieurs / recombine plusieurs messages en un. | *(non utilisés ici)* |
| **Channel Adapter** | Relie un canal à un système externe (fichier, HTTP, JMS…). | *(non utilisé ici)* |

---

## 4. L'exemple : un pipeline de traitement de commandes

### 4.1 Le parcours d'un message

```
   Contrôleur REST  (POST /orders)
        │  appelle
        ▼
   OrderGateway.placeOrder(order)          ← GATEWAY : enveloppe l'Order dans un Message
        │  Message<Order>
        ▼
   [orderInputChannel]                     ← CANAL de départ
        ▼
   FILTER : amount > 0 ?
        │                └── non ──► [invalidOrderChannel] ─► handleInvalid()  → "refusée"
        │  oui
        ▼
   TRANSFORMER : enrich(order)             ← enrichit / logue la commande
        ▼
   [routingChannel]                        ← CANAL intermédiaire (nommé, pour le debug)
        ▼
   ROUTER : selon order.getType()
        ├── STANDARD ─► handleStandard()   ← SERVICE ACTIVATOR
        └── EXPRESS  ─► handleExpress()    ← SERVICE ACTIVATOR
        ▼
   OrderConfirmation
        ▼
   réponse renvoyée automatiquement au Gateway ──► au contrôleur ──► au client HTTP
```

### 4.2 Les fichiers créés

```
src/main/java/com/spring/integration/
├── IntegrationApplication.java      # démarrage Spring Boot
├── model/
│   ├── Order.java                   # le payload (la commande)
│   ├── OrderType.java               # STANDARD / EXPRESS (clé de routage)
│   └── OrderConfirmation.java       # le payload de réponse
├── gateway/
│   └── OrderGateway.java            # @MessagingGateway : la porte d'entrée
├── config/
│   └── OrderIntegrationConfig.java  # LE FLUX (Java DSL) — cœur de l'exemple
├── service/
│   └── OrderService.java            # code métier PUR (aucune dépendance SI)
└── controller/
    └── OrderController.java         # endpoint REST pour déclencher le flux
```

---

## 5. Le code, expliqué morceau par morceau

### 5.1 Le Gateway — la porte d'entrée sans implémentation

```java
@MessagingGateway
public interface OrderGateway {
    @Gateway(requestChannel = "orderInputChannel")
    OrderConfirmation placeOrder(Order order);
}
```

- C'est une **simple interface** : tu n'écris **aucune** implémentation.
- Spring crée un **proxy** à l'exécution.
- Appeler `placeOrder(order)` : Spring enveloppe `order` dans un `Message`, l'envoie sur
  `orderInputChannel`, attend la réponse, et te renvoie le payload (`OrderConfirmation`).
- **Bénéfice clé** : ton appelant (le contrôleur) ne voit aucune API de messagerie. Il
  croit appeler une méthode Java normale.

> ⚠️ Pour que Spring détecte cette interface, il faut `@IntegrationComponentScan`
> (placé ici sur la classe de configuration). Spring Boot ne l'active pas tout seul.

### 5.2 Le flux (Java DSL) — l'assemblage du pipeline

C'est le fichier central `OrderIntegrationConfig.java`. Le **Java DSL** décrit le flux
de façon fluide (chaînée) :

```java
@Bean
public IntegrationFlow orderProcessingFlow(OrderService orderService) {
    return IntegrationFlow
            .from("orderInputChannel")                                   // 1. départ
            .<Order>filter(order -> order.getAmount() > 0,               // 2. FILTER
                    f -> f.discardChannel("invalidOrderChannel"))
            .<Order, Order>transform(orderService::enrich)              // 3. TRANSFORMER
            .channel(MessageChannels.direct("routingChannel"))          // 4. canal nommé
            .<Order, OrderType>route(Order::getType, mapping -> mapping // 5. ROUTER
                    .subFlowMapping(OrderType.STANDARD,
                            sf -> sf.handle(Order.class,
                                    (p, h) -> orderService.handleStandard(p)))
                    .subFlowMapping(OrderType.EXPRESS,
                            sf -> sf.handle(Order.class,
                                    (p, h) -> orderService.handleExpress(p))))
            .get();                                                     // 6. construit le flux
}
```

Décryptage opérateur par opérateur :

| Opérateur | Ce qu'il fait ici |
|-----------|-------------------|
| `IntegrationFlow.from("...")` | Le flux démarre en écoutant ce canal (celui du Gateway). |
| `.filter(predicate, ...)` | Ne garde que `amount > 0`. Les autres partent sur le `discardChannel`. |
| `.transform(fn)` | Applique `enrich` : renvoie le nouveau payload. |
| `.channel(...)` | Insère un canal nommé entre deux étapes (utile pour observer/brancher). |
| `.route(clé, mapping)` | Calcule une clé (`getType()`) et dirige vers le bon sous-flux. |
| `.handle(Class, (p,h)->...)` | Service Activator : appelle la méthode métier. Sa valeur de retour = payload de réponse. |
| `.get()` | Termine et construit l'objet `IntegrationFlow`. |

> 💡 **Le piège du `.handle(...)`** : `.handle(maMéthode)` attend un handler à **deux**
> arguments `(payload, headers)`. Comme nos méthodes métier n'en prennent qu'un, on
> utilise la forme typée `.handle(Order.class, (p, h) -> orderService.handleStandard(p))`.

### 5.3 Le code métier — totalement isolé

```java
@Service
public class OrderService {
    public Order enrich(Order order) { ... }
    public OrderConfirmation handleStandard(Order order) { ... }
    public OrderConfirmation handleExpress(Order order) { ... }
    public OrderConfirmation handleInvalid(Order order) { ... }
}
```

Remarque **essentielle** : cette classe n'importe **rien** de Spring Integration.
C'est le principe du *POJO programming style* recommandé par la doc officielle : la
logique métier ne doit pas être polluée par la plomberie. C'est le flux qui « branche »
ces méthodes aux étapes.

### 5.4 Le flux de rejet — un canal peut relier deux flux

Quand le filtre rejette un message, il l'envoie sur `invalidOrderChannel`. Un **second
flux** écoute ce canal :

```java
@Bean
public IntegrationFlow invalidOrderFlow(OrderService orderService) {
    return IntegrationFlow
            .from("invalidOrderChannel")
            .<Order, OrderConfirmation>transform(orderService::handleInvalid)
            .get();
}
```

Astuce importante : le message rejeté **conserve ses headers d'origine**, dont
`replyChannel`. En renvoyant une `OrderConfirmation`, celle-ci **remonte
automatiquement** jusqu'au Gateway. Résultat : le client reçoit *"Commande refusée"*
au lieu de rester bloqué à attendre une réponse.

---

## 6. Lancer et tester

### Démarrer l'application

```bash
./mvnw spring-boot:run
```

L'application démarre un serveur web sur le port **8080**.

### Tester les 3 scénarios (résultats réels obtenus)

**1) Commande EXPRESS (valide) → branche EXPRESS**
```bash
curl -X POST http://localhost:8080/orders -H "Content-Type: application/json" \
     -d '{"product":"Livre","quantity":2,"amount":39.9,"type":"EXPRESS"}'
```
```json
{"trackingId":"EXP-a082def1","message":"Commande EXPRESS traitée. Livraison sous 24 h."}
```

**2) Commande STANDARD (valide) → branche STANDARD**
```bash
curl -X POST http://localhost:8080/orders -H "Content-Type: application/json" \
     -d '{"product":"Clavier","quantity":1,"amount":79.0,"type":"STANDARD"}'
```
```json
{"trackingId":"STD-19c89d3f","message":"Commande STANDARD traitée. Livraison sous 5 jours."}
```

**3) Commande invalide (montant 0) → rejetée par le filtre**
```bash
curl -X POST http://localhost:8080/orders -H "Content-Type: application/json" \
     -d '{"product":"Stylo","quantity":1,"amount":0,"type":"STANDARD"}'
```
```json
{"trackingId":"N/A","message":"Commande refusée : le montant doit être strictement positif."}
```

### Ce que tu verras dans la console (logs métier)

```
[TRANSFORMER] Enrichissement de la commande : Order(product=Livre, ..., type=EXPRESS)
[EXPRESS] Traitement prioritaire de : Order(...) -> EXP-a082def1
[TRANSFORMER] Enrichissement de la commande : Order(product=Clavier, ..., type=STANDARD)
[STANDARD] Traitement standard de : Order(...) -> STD-19c89d3f
[REJET] Commande invalide : Order(product=Stylo, ..., amount=0.0, ...)
```

---

## 7. Vérifier avec les tests

Le fichier `src/test/java/.../OrderFlowTest.java` teste le flux via le Gateway :

```java
@SpringBootTest
class OrderFlowTest {
    @Autowired OrderGateway orderGateway;

    @Test void commandeExpress_estRoutéeVersLaBrancheExpress() {
        var conf = orderGateway.placeOrder(new Order("Livre", 2, 39.90, OrderType.EXPRESS));
        assertThat(conf.getTrackingId()).startsWith("EXP-");
    }
    // + un test STANDARD et un test de rejet
}
```

Tester un flux par sa **porte d'entrée** (le Gateway) est la méthode la plus simple :
on ne teste pas chaque canal individuellement, mais le comportement global.

```bash
./mvnw test
```

---

## 8. DSL vs Annotations — les deux styles

La doc officielle propose **deux façons** de configurer un flux. On a choisi le **Java DSL**
(plus lisible pour visualiser un pipeline), mais tu peux aussi utiliser des **annotations** :

```java
// Style ANNOTATIONS (équivalent d'un Service Activator)
@ServiceActivator(inputChannel = "routingChannel", outputChannel = "resultChannel")
public OrderConfirmation handle(Order order) { ... }

// Filter, Transformer, Router ont aussi leurs annotations :
@Filter, @Transformer, @Router
```

| | Java DSL | Annotations |
|--|----------|-------------|
| Lisibilité d'un pipeline | ✅ excellente (tout est chaîné, visuel) | ⚠️ éclatée sur plusieurs méthodes |
| Contrôle fin | ✅ | ✅ |
| Recommandé pour | flux complexes, branches, sous-flux | endpoints simples et isolés |

---

## 9. À retenir (le résumé)

1. **Message = payload + headers.** Tout circule sous forme de messages.
2. **Les canaux découplent** les composants ; ils ne se connaissent pas entre eux.
3. **Chaque endpoint fait une seule chose** (filtrer, transformer, router, activer un service).
4. **Le Gateway** cache toute la plomberie derrière un simple appel de méthode.
5. **Le code métier reste pur** (POJO) ; c'est le flux qui l'orchestre.
6. **Le Java DSL** (`IntegrationFlow.from(...).filter(...).transform(...).route(...).get()`)
   décrit le pipeline de façon lisible et fluide.

---

## 10. Pour aller plus loin

- [Documentation officielle Spring Integration](https://docs.spring.io/spring-integration/reference/)
- [Chapitre Java DSL](https://docs.spring.io/spring-integration/reference/dsl.html)
- [Exemples officiels (spring-integration-samples)](https://github.com/spring-projects/spring-integration-samples)
- Patterns à explorer ensuite : **Splitter**, **Aggregator**, **Channel Adapters**
  (fichier, HTTP, JMS, Kafka), **Poller**, **error channel**.
```
```
