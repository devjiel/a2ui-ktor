package com.a2ui.demo.business.booking.model

import kotlinx.serialization.Serializable

/**
 * Vol spatial disponible à la réservation.
 */
@Serializable
data class Flight(
    /** Identifiant unique du vol (ex: SL-4821) */
    val flightId: String,
    /** Compagnie spatiale */
    val airline: String,
    /** Code destination d'origine */
    val origin: String,
    /** Code destination d'arrivée */
    val destination: String,
    /** Date et heure de départ (ISO 8601) */
    val departureTime: String,
    /** Temps de trajet estimé */
    val travelDuration: String,
    /** Prix en crédits galactiques (classe Economy) */
    val priceEconomy: Int,
    /** Prix en crédits galactiques (classe Business) */
    val priceBusiness: Int,
    /** Prix en crédits galactiques (classe First) */
    val priceFirst: Int,
    /** Places disponibles */
    val seatsAvailable: Int
)
