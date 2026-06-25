# A2UI Kotlin/Ktor — Serveur agent A2UI v0.9

Serveur agent minimal implémentant le protocole [A2UI v0.9](https://a2ui.org/specification/v0_9_1/catalogs/basic/catalog.json) en Kotlin avec Ktor. Génère un formulaire de réservation de restaurant en streaming JSONL.

## Prérequis

- JDK 21+

## Lancer le serveur

```bash
./gradlew run
```

Le serveur démarre sur `http://localhost:8080`.

## Endpoints

| Endpoint | Description |
|----------|-------------|
| `WS /a2ui/agent` | WebSocket — stream JSONL A2UI progressif |
| `GET /a2ui/preview` | HTTP — retourne le JSONL complet (debug) |

Paramètre optionnel : `?surfaceId=my-surface` (défaut : `main`).

## Tester

```bash
./gradlew test
```

## Flux A2UI généré

Pour le formulaire de réservation, le serveur émet 4 messages JSONL dans l'ordre requis par le protocole :

```jsonl
{"createSurface":{"surfaceId":"main","catalogId":"https://a2ui.org/specification/v0_9_1/catalogs/basic/catalog.json"}}
{"surfaceUpdate":{"surfaceId":"main","components":[...]}}
{"updateDataModel":{"surfaceId":"main","contents":[...]}}
{"beginRendering":{"surfaceId":"main","root":"root"}}
```

## Architecture

```
src/main/kotlin/com/a2ui/demo/
├── Application.kt              # Point d'entrée Ktor (Netty, WebSockets)
├── model/
│   ├── A2uiMessage.kt          # Messages A2UI (sealed class + serializer wrapper-key)
│   ├── Components.kt           # 20 composants du catalogue Basic
│   └── DataModel.kt            # TextValue, ChildrenBinding, DataModelEntry, Action
├── generator/
│   └── A2uiGenerator.kt        # Construit le formulaire de réservation
└── routes/
    └── AgentRoutes.kt          # WebSocket + HTTP preview
```

## Composants implémentés (catalogue Basic)

**Layout** : Column, Row, Card, List, Divider, Modal, Tabs  
**Text** : Text, Markdown  
**Input** : TextField, CheckBox, Slider, DateTimeInput, MultipleChoice, Dropdown  
**Média** : Image, Video, AudioPlayer, Icon  
**Action** : Button
