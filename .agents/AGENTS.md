# Règles du projet a2ui-ktor

## Documentation Koog (framework AI agents Kotlin)

Ce projet utilise le framework **Koog** (by JetBrains) pour construire des agents IA en Kotlin.

### Comment chercher la documentation Koog

Utiliser le serveur MCP **context7** avec les étapes suivantes :

1. **Résoudre l'ID de la bibliothèque** via `resolve-library-id` :
   ```json
   {
     "libraryName": "Koog",
     "query": "<ta question spécifique>"
   }
   ```
   Les IDs Context7 disponibles pour Koog :
   - `/jetbrains/koog` — Source officielle JetBrains (High reputation)
   - `/websites/api_koog_ai` — API reference complète (13k+ snippets, score 82)
   - `/websites/koog_ai` — Site documentation (1.8k snippets)

2. **Interroger la documentation** via `query-docs` :
   ```json
   {
     "libraryId": "/websites/api_koog_ai",
     "query": "<ta question spécifique>"
   }
   ```
   Préférer `/websites/api_koog_ai` pour les questions sur l'API (LLModel, LLMCapability, PromptExecutor, etc.)
   Préférer `/jetbrains/koog` pour les guides et concepts généraux.

### Classes clés Koog utilisées dans ce projet

- `LLModel` — Représente un modèle LLM (provider, id, capabilities, contextLength, maxOutputTokens)
- `LLMProvider` — Fournisseur LLM (OpenRouter, OpenAI, Google, Anthropic, etc.)
- `LLMCapability` — Capacités d'un modèle (Temperature, Tools, Schema.JSON.Full, Vision, etc.)
- `OpenRouterModels` — Modèles prédéfinis pour OpenRouter (ex: `OpenRouterModels.Gemini2_5Flash`)
- `MultiLLMPromptExecutor` — Exécuteur de prompts multi-providers
- `AgentExecutor` — Interface pour les agents A2A

### Exemple : créer un modèle custom via OpenRouter

```kotlin
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.llm.LLMProvider

val myModel = LLModel(
    provider = LLMProvider.OpenRouter,
    id = "vendor/model-name",
    capabilities = listOf(
        LLMCapability.Temperature,
        LLMCapability.Tools,
        LLMCapability.Schema.JSON.Full
    ),
    contextLength = 1_000_000L,
    maxOutputTokens = 16_384L,
)
```
