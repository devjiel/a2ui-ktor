package com.a2ui.demo.business.booking.model

import kotlinx.serialization.Serializable

/**
 * Destination spatiale disponible pour la réservation.
 */
@Serializable
data class Destination(
    /** Code IATA-like (ex: MARS, LUNA) */
    val code: String,
    /** Nom complet de la destination */
    val name: String,
    /** Type de destination */
    val type: String,
    /** Distance en unités astronomiques (AU) depuis la Terre */
    val distanceAU: Double,
    /** Temps de trajet estimé (texte lisible) */
    val travelTime: String,
    /** Description courte */
    val description: String
)
