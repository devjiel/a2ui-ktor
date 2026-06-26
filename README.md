# A2UI Ktor — Agent IA Koog avec A2A

Agent IA propulsé par [Koog](https://koog.ai) (JetBrains) exposé via le protocole [A2A](https://a2aprotocol.org) (Agent-to-Agent).

## Prérequis

- JDK 21+
- Variable d'environnement `OPENAI_API_KEY`

## Lancer

```bash
OPENAI_API_KEY=sk-your-key ./gradlew run
```

Serveur A2A sur `http://localhost:9998`.

## Tester

```bash
# Découverte de l'agent
curl http://localhost:9998/.well-known/agent-card.json

# Interrogation via JSON-RPC
curl -X POST http://localhost:9998/hello \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","method":"message/send","params":{"message":{"messageId":"1","role":"user","parts":[{"kind":"text","text":"Bonjour !"}]}},"id":"1"}'
```

## Stack

Kotlin 2.2 · Koog 1.0 · A2A 0.3.0 · OpenAI GPT-4o · Ktor Netty
