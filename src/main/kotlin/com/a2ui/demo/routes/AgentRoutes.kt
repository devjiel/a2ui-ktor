package com.a2ui.demo.routes

import com.a2ui.demo.generator.A2uiGenerator
import com.a2ui.demo.generator.json
import com.a2ui.demo.model.A2uiMessage
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.delay
import kotlinx.serialization.encodeToString

fun Application.configureRoutes() {
    routing {
        // WebSocket endpoint — stream JSONL A2UI progressif
        webSocket("/a2ui/agent") {
            val surfaceId = call.parameters["surfaceId"] ?: "main"
            val messages = A2uiGenerator(surfaceId).generateReservationForm()

            for (message in messages) {
                val line = json.encodeToString<A2uiMessage>(message)
                send(Frame.Text(line))
                delay(50)
            }

            // Écoute des actions utilisateur entrantes
            for (frame in incoming) {
                if (frame is Frame.Text) {
                    val userAction = frame.readText()
                    send(Frame.Text("""{"userAction":$userAction}"""))
                }
            }
        }

        // Endpoint HTTP de prévisualisation — retourne le JSONL complet
        get("/a2ui/preview") {
            val surfaceId = call.parameters["surfaceId"] ?: "main"
            val jsonl = A2uiGenerator(surfaceId).generateReservationFormJsonl()
            call.respondText(jsonl, ContentType.Text.Plain)
        }
    }
}
