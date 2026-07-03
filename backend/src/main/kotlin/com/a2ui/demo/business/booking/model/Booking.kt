package com.a2ui.demo.business.booking.model

import kotlinx.serialization.Serializable

/**
 * Réservation confirmée.
 */
@Serializable
data class Booking(
    /** Numéro de réservation unique */
    val bookingId: String,
    /** Détails du vol */
    val flight: Flight,
    /** Prénom du passager */
    val firstName: String,
    /** Nom du passager */
    val lastName: String,
    /** Classe choisie */
    val travelClass: String,
    /** Prix final payé */
    val totalPrice: Int,
    /** Statut de la réservation */
    val status: String = "CONFIRMED"
)
