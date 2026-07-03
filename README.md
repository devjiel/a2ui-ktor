# A2UI Ktor — Multi-Agent A2UI Generation + Flutter GenUI Frontend

Monorepo contenant un système multi-agent A2UI avec un backend Kotlin/Ktor (Koog) et un frontend Flutter (GenUI SDK).

## Architecture

```
a2ui-ktor/
├── backend/     ← Agents Koog/Ktor (IntentAgent + GeneratorAgent)
└── frontend/    ← Application Flutter avec GenUI SDK
```

### Backend — Agents A2A

```
HTTP :9998 (public)                       HTTP :9999 (interne)
  ↓                                         ↓
IntentAgent                               A2UIGeneratorAgent
  ↓ analyse l'intention                     ↓ génère l'A2UI JSON
  └── délègue au GeneratorAgent ──────────→ └── retourne DataPart(application/a2ui+json)
```

### Frontend — Flutter GenUI

```
User Input → Conversation → A2uiAgentConnector → IntentAgent (port 9998)
                                                        ↓
GenUiSurface ← SurfaceController ← A2uiTransportAdapter ← DataPart(a2ui+json)
```

## Commandes

### Backend
```bash
cd backend
./gradlew build                                  # Compiler
OPENROUTER_API_KEY=... ./gradlew run             # Lancer les deux agents
curl http://localhost:9998/.well-known/agent-card.json  # Agent Card IntentAgent
```

### Frontend
```bash
cd frontend
flutter pub get        # Installer les dépendances
flutter run -d chrome  # Lancer sur Chrome
flutter run -d iPhone  # Lancer sur le simulateur iOS
```

## Stack Technique

| Composant | Technologie |
|-----------|-------------|
| Backend | Kotlin 2.2, Ktor 3.1, Koog 1.0 |
| Frontend | Flutter 3.41+, Dart 3.11+ |
| Protocole | A2A (JSON-RPC HTTP), A2UI v0.9 |
| GenUI SDK | genui 0.9, genui_a2a 0.9 |
