package com.a2ui.demo.business.booking.tools

import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
import ai.koog.agents.core.tools.reflect.ToolSet
import com.a2ui.demo.business.booking.model.Flight
import com.a2ui.demo.business.booking.repository.MockFlightRepository
import kotlinx.serialization.json.Json

/**
 * Tools métier pour la réservation de vols spatiaux.
 * Accessibles par le GeneratorAgent pour récupérer des données
 * et construire les interfaces A2UI adaptées.
 */
@LLMDescription("Tools for searching and booking space flights")
class BookingTools : ToolSet {

    private val json = Json { prettyPrint = false }

    /** Derniers vols retournés par search — pour pouvoir booker ensuite */
    private var lastSearchResults: List<Flight> = emptyList()

    @Tool
    @LLMDescription(
        "Lists all available space destinations with their codes, names, " +
        "types, distances, and travel times. Call this first to know what " +
        "destinations exist before searching for flights."
    )
    fun list_destinations(): String {
        val destinations = MockFlightRepository.listDestinations()
        return json.encodeToString(destinations)
    }

    @Tool
    @LLMDescription(
        "Searches for available flights between two destinations on a given date. " +
        "Returns a list of flights with prices, airlines, and departure times. " +
        "Use destination codes (e.g., EARTH, MARS, LUNA, EURO, TITAN, PROX). " +
        "Date format: YYYY-MM-DD."
    )
    fun search_flights(
        @LLMDescription("Origin destination code (e.g., EARTH)")
        origin: String,
        @LLMDescription("Arrival destination code (e.g., MARS)")
        destination: String,
        @LLMDescription("Travel date in YYYY-MM-DD format")
        date: String
    ): String {
        val flights = MockFlightRepository.searchFlights(origin, destination, date)
        lastSearchResults = flights

        if (flights.isEmpty()) {
            return """{"error": "No flights found for $origin → $destination on $date. Check destination codes."}"""
        }

        return json.encodeToString(flights)
    }

    @Tool
    @LLMDescription(
        "Books a flight for a passenger. Use a flightId from search_flights results. " +
        "Returns the booking confirmation with booking ID and total price."
    )
    fun book_flight(
        @LLMDescription("Flight ID from search results (e.g., SL-4821)")
        flightId: String,
        @LLMDescription("Passenger's first name")
        firstName: String,
        @LLMDescription("Passenger's last name")
        lastName: String,
        @LLMDescription("Travel class: economy, business, or first")
        travelClass: String
    ): String {
        val booking = MockFlightRepository.bookFlight(
            flightId, firstName, lastName, travelClass, lastSearchResults
        )

        if (booking == null) {
            return """{"error": "Flight $flightId not found in last search results. Run search_flights first."}"""
        }

        return json.encodeToString(booking)
    }
}
