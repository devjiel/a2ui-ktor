# A2UI Ktor — Multi-Agent A2UI Generation

## Architecture

```
HTTP :9998 (public)                       HTTP :9999 (interne)
  ↓                                         ↓
HttpJSONRPCServerTransport                HttpJSONRPCServerTransport
  ↓                                         ↓
A2AServer (IntentAgent)                   A2AServer (A2UIGeneratorAgent)
  ↓                                         ↓
IntentAgentExecutor                       A2UIGeneratorAgentExecutor
  ↓                                         ↓
AIAgent (graph strategy)                  AIAgent (graph strategy)
├── nodeSetup                             ├── nodeSetup
├── nodeLLMRequest (gemini-flash)         ├── nodeLLMRequest (gemini-2.5-pro)
│   ├── tool: validate_intent             │   ├── tool: validate_a2ui (12 règles)
│   └── tool-calling loop                 │   └── tool-calling loop (Generator-Critic)
├── nodeForwardToGenerator                └── nodeProcessResponse → Completed
│   └── A2A Client → port 9999
└── nodeFinish
```

## Fichiers

### Agents
- `agent/A2AHelpers.kt` — Helpers A2A partagés (createTask, updateTaskStatus)
- `agent/intent/IntentAgentExecutor.kt` — Point d'entrée A2A, analyse d'intention
- `agent/intent/IntentResult.kt` — Data class intention (userMessage, intent, justification, uiType)
- `agent/intent/IntentValidationTool.kt` — Validation déterministe de l'intention (5 règles)
- `agent/generator/A2UIGeneratorAgentExecutor.kt` — Génération A2UI (graph Generator-Critic)
- `agent/generator/A2UIValidationTool.kt` — Validation déterministe A2UI (12 règles)

### Ressources
- `resources/catalog/catalog.json` — Catalogue A2UI complet (18 composants, 14 fonctions)
- `resources/catalog/rules.txt` — Règles de validation enrichies
- `resources/catalog/a2ui_spec_summary.txt` — Résumé de la spec A2UI v0.9
- `resources/catalog/examples.json` — 5 exemples few-shot

## Commandes

- `./gradlew build` — compiler
- `OPENROUTER_API_KEY=... ./gradlew run` — lancer les deux agents
- `curl http://localhost:9998/.well-known/agent-card.json` — découverte IntentAgent
- `curl http://localhost:9999/.well-known/agent-card.json` — découverte GeneratorAgent
