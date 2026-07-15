package com.a2ui.demo.business.booking.repository

import com.a2ui.demo.business.booking.model.Booking
import com.a2ui.demo.business.booking.model.Destination
import com.a2ui.demo.business.booking.model.Flight
import kotlin.random.Random

/**
 * Repository de données mockées pour les vols spatiaux.
 * 
 * - Destinations : liste fixe de 6 destinations spatiales
 * - Vols : générés dynamiquement avec prix pseudo-aléatoires
 * - Bookings : stockés en mémoire (durée de vie = durée du process)
 */
object MockFlightRepository {

    // ── Compagnies spatiales ──
    private val AIRLINES = listOf(
        "StarLine" to "SL",
        "Cosmos Express" to "CX",
        "Orbital Transit" to "OT",
        "Deep Space Airlines" to "DS",
        "Nova Cruisers" to "NC"
    )

    // ── Destinations ──
    val DESTINATIONS = listOf(
        Destination("EARTH", "Earth — Spaceport International", "Spaceport", 0.0, "-",
            "Main departure point. Spaceport located in low Earth orbit."),
        Destination("LUNA", "Lunar Station Alpha", "Orbital station", 0.0026, "3 days",
            "Station in lunar orbit. Gravity 0.16g. Breathtaking view of the Earth."),
        Destination("MARS", "Mars Colony One", "Colony", 0.52, "45 days",
            "First permanent colony on Mars. Pressurized dome, 12,000 inhabitants."),
        Destination("EURO", "Europa Research Base", "Scientific base", 4.2, "6 months",
            "Research base under the ice of Europa. Accessible subsurface ocean."),
        Destination("TITAN", "Titan Outpost", "Outpost", 8.5, "1 year",
            "Outpost on Titan, Saturn's moon. Methane lakes, dense atmosphere."),
        Destination("PROX", "Proxima Station", "Interstellar station", 268000.0, "4.2 years",
            "First interstellar station. Cryo-sleep voyage mandatory."),
    )


    // ── Prix de base par distance (crédits galactiques) ──
    private fun basePrice(distanceAU: Double): Int = when {
        distanceAU < 0.01 -> 500       // Lune
        distanceAU < 1.0 -> 2_500      // Mars
        distanceAU < 5.0 -> 8_000      // Jupiter/Europa
        distanceAU < 10.0 -> 15_000    // Saturne/Titan
        else -> 50_000                  // Interstellaire
    }

    // ── Stockage en mémoire des réservations ──
    private val bookings = mutableMapOf<String, Booking>()

    /**
     * Liste toutes les destinations disponibles.
     */
    fun listDestinations(): List<Destination> = DESTINATIONS

    /**
     * Recherche des vols entre deux destinations pour une date donnée.
     * Génère 3-5 vols mockés avec des prix pseudo-aléatoires.
     */
    fun searchFlights(origin: String, destination: String, date: String): List<Flight> {
        val originDest = DESTINATIONS.find { it.code == origin.uppercase() }
        val destDest = DESTINATIONS.find { it.code == destination.uppercase() }

        if (originDest == null || destDest == null) return emptyList()
        if (origin.uppercase() == destination.uppercase()) return emptyList()

        // Seed pseudo-aléatoire basé sur les paramètres pour reproductibilité
        val seed = "$origin-$destination-$date".hashCode().toLong()
        val random = Random(seed)
        val numFlights = random.nextInt(3, 6) // 3 à 5 vols

        val distance = Math.abs(destDest.distanceAU - originDest.distanceAU)
            .coerceAtLeast(originDest.distanceAU.coerceAtLeast(destDest.distanceAU))
        val base = basePrice(distance)

        return (1..numFlights).map { i ->
            val (airline, prefix) = AIRLINES[random.nextInt(AIRLINES.size)]
            val flightNum = random.nextInt(1000, 9999)
            val hourOffset = random.nextInt(0, 24)
            val priceVariance = (base * random.nextDouble(0.8, 1.2)).toInt()

            Flight(
                flightId = "$prefix-$flightNum",
                airline = airline,
                origin = origin.uppercase(),
                destination = destination.uppercase(),
                departureTime = "${date}T${hourOffset.toString().padStart(2, '0')}:00:00Z",
                travelDuration = destDest.travelTime,
                priceEconomy = priceVariance,
                priceBusiness = (priceVariance * 2.5).toInt(),
                priceFirst = priceVariance * 5,
                seatsAvailable = random.nextInt(1, 50)
            )
        }.sortedBy { it.priceEconomy }
    }

    /**
     * Réserve un vol pour un passager.
     */
    fun bookFlight(
        flightId: String,
        firstName: String,
        lastName: String,
        travelClass: String,
        flights: List<Flight>
    ): Booking? {
        val flight = flights.find { it.flightId == flightId } ?: return null

        val price = when (travelClass.lowercase()) {
            "business" -> flight.priceBusiness
            "first" -> flight.priceFirst
            else -> flight.priceEconomy
        }

        val bookingId = "BK-${Random.nextInt(100000, 999999)}"
        val booking = Booking(
            bookingId = bookingId,
            flight = flight,
            firstName = firstName,
            lastName = lastName,
            travelClass = travelClass,
            totalPrice = price
        )

        bookings[bookingId] = booking
        return booking
    }
}
