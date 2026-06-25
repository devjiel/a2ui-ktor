# A2UI Kotlin/Ktor — Projet d'exploration

## Objectif
Créer un serveur agent A2UI minimal en Kotlin avec Ktor capable de générer des réponses A2UI v0.9 conformes à la spécification.

## Spécification A2UI v0.9 — Résumé technique

### Format
- Protocole déclaratif JSON/JSONL (JSON Lines pour streaming)
- Licence Apache 2.0, créé par Google, contributions CopilotKit + communauté

### Types de messages (v0.9)
1. **`createSurface`** — crée une surface de rendu :
```json
{"createSurface": {"surfaceId": "main", "catalogId": "https://a2ui.org/specification/v0_9_1/catalogs/basic/catalog.json"}}
```

2. **`surfaceUpdate`** — définit les composants UI :
```json
{"surfaceUpdate": {"surfaceId": "main", "components": [
  {"id": "root", "component": {"Column": {"children": {"explicitList": ["header", "body"]}}}},
  {"id": "header", "component": {"Text": {"text": {"path": "/title"}}}},
  {"id": "body", "component": {"Button": {"child": "btn-label", "action": {"name": "submit"}}}},
  {"id": "btn-label", "component": {"Text": {"text": {"literalString": "Submit"}}}}
]}}
```

3. **`dataModelUpdate`** / **`updateDataModel`** (v0.9 utilise `updateDataModel`) — données liées :
```json
{"updateDataModel": {"surfaceId": "main", "contents": [
  {"key": "title", "valueString": "Hello A2UI"}
]}}
```

4. **`beginRendering`** — active le rendu :
```json
{"beginRendering": {"surfaceId": "main", "root": "root"}}
```

5. **`deleteSurface`** — supprime une surface

### Composants du catalogue Basic (v0.9)
- **Layout** : Column, Row, List, Card, Tabs, Modal, Divider
- **Text** : Text, Markdown  
- **Input** : TextField, CheckBox, Slider, DateTimeInput, MultipleChoice, Dropdown
- **Média** : Image, Video, AudioPlayer, Icon
- **Action** : Button
- **Structure** : surfaceUpdate, dataModelUpdate

### Data binding
Les composants référencent les données via `{"path": "/chemin/vers/donnée"}`.
Les valeurs dans `updateDataModel` utilisent `valueString`, `valueInt`, `valueBool`, `valueMap`.

### Principes clés
- **Sécurité** : jamais de code exécutable, uniquement JSON déclaratif
- **Framework-agnostic** : le client (Flutter, React, Lit, Angular) rend avec ses propres composants
- **Streaming** : JSONL permet le rendu progressif
- **Interactions** : les actions utilisateur remontent via `userAction` events

## Contraintes techniques du projet

- **Langage** : Kotlin
- **Framework HTTP** : Ktor (server)
- **Build** : Gradle (Kotlin DSL)
- **Sérialisation JSON** : kotlinx.serialization
- **JDK** : 21+

## Ce que le projet doit contenir

1. **Modèle de données Kotlin** pour les messages A2UI v0.9 (sealed classes/data classes)
2. **Serveur Ktor** avec un endpoint WebSocket (ou SSE) qui stream des messages A2UI
3. **Générateur A2UI** — une classe qui construit des `createSurface` → `surfaceUpdate` → `updateDataModel` → `beginRendering`
4. **Exemple concret** : un formulaire de réservation (restaurant ou autre) généré dynamiquement
5. **Tests unitaires** pour la sérialisation/désérialisation A2UI
6. **README.md** avec le plan d'implémentation et comment exécuter

## Architecture cible

```
a2ui-kotlin-ktor/
├── build.gradle.kts
├── settings.gradle.kts
├── src/
│   ├── main/kotlin/com/a2ui/demo/
│   │   ├── Application.kt          # Point d'entrée Ktor
│   │   ├── model/
│   │   │   ├── A2uiMessage.kt      # Messages: CreateSurface, SurfaceUpdate, etc.
│   │   │   ├── Components.kt       # Composants: Column, Text, Button, etc.
│   │   │   └── DataModel.kt        # Data binding: path, valueString, valueInt
│   │   ├── generator/
│   │   │   └── A2uiGenerator.kt    # Builder pour créer des réponses A2UI
│   │   └── routes/
│   │       └── AgentRoutes.kt      # WebSocket endpoint pour l'agent
│   └── test/kotlin/com/a2ui/demo/
│       ├── SerializationTest.kt    # Tests sérialisation JSON
│       └── GeneratorTest.kt        # Tests du générateur
└── README.md
```

## Démarche

1. Initialiser le projet Gradle avec Ktor + kotlinx.serialization
2. Définir les data classes Kotlin pour tous les messages A2UI v0.9
3. Implémenter le serveur Ktor avec WebSocket
4. Créer le générateur qui produit le JSON A2UI pour un scénario de réservation
5. Écrire les tests de sérialisation
6. Vérifier que `./gradlew build` compile et que les tests passent

**IMPORTANT** : le projet doit compiler et les tests doivent passer. Utilise `./gradlew build` pour vérifier.
