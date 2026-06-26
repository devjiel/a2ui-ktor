# A2UI Ktor — Agent Koog + A2A

## Architecture

```
HTTP :9998
  ↓
HttpJSONRPCServerTransport (transport)
  ↓
A2AServer (protocole a2a-server, transitif)
  ↓
HelloAgentExecutor (implémente AgentExecutor de a2a-server)
  ↓
AIAgent Koog (graph strategy)
  ├── install(A2AAgentServer) ← feature de agents-features-a2a-server
  ├── nodeSetup → envoie TaskState.Submitted via withA2AAgentServer
  ├── nodeLLMRequest → appelle OpenAI GPT-4o
  └── nodeProcessAssistant → envoie TaskState.Completed via withA2AAgentServer
```

## Fichiers

- `Application.kt` — AgentCard, A2AServer, HttpJSONRPCServerTransport
- `agent/HelloAgentExecutor.kt` — AgentExecutor + AIAgent graph strategy

## Dépendances

| Module Maven | Rôle |
|--------|------|
| `koog-agents` | Framework agent Koog |
| `koog-agents-features-a2a-server` | Bridge AIAgent ↔ A2A (tire `a2a-server` en transitif) |
| `koog-a2a-transport-server-jsonrpc-http` | Transport HTTP JSON-RPC |
| `ktor-server-netty` | Moteur HTTP |

## Commandes

- `./gradlew build` — compiler
- `OPENAI_API_KEY=... ./gradlew run` — lancer
- `curl http://localhost:9998/.well-known/agent-card.json` — découverte
