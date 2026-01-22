package no.uio.bedreflyt.lm.service

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.uio.bedreflyt.lm.types.RoomRequest
import no.uio.bedreflyt.lm.types.TreatmentRoom
import no.uio.bedreflyt.lm.types.Ward
import org.springframework.stereotype.Service
import java.net.HttpURLConnection
import java.net.URI

@Service
class TreatmentRoomService {

    private val objectMapper: ObjectMapper = jacksonObjectMapper().apply {
        registerModule(JavaTimeModule())
    }

    fun retrieveRooms(endpoint: String) : List<TreatmentRoom> {
        val roomConnection = URI(endpoint).toURL().openConnection() as HttpURLConnection
        roomConnection.requestMethod = "GET"
        roomConnection.setRequestProperty("Content-Type", "application/json")
        roomConnection.doOutput = true
        val roomResponse = roomConnection.inputStream.bufferedReader().use { it.readText() }
        val rooms = objectMapper.readValue(roomResponse, object : TypeReference<List<TreatmentRoom>>() {})
        return rooms
    }

    fun getWardCorridors (treatmentRooms: List<TreatmentRoom>) : Map<Ward, Boolean>{
        return treatmentRooms.groupBy { it.treatmentWard }
            .mapValues { entry ->
                entry.value.any { it.monitoringCategory.description == "Korridor" }
            }
    }

    fun getWardCapacities (treatmentRooms: List<TreatmentRoom>) : Map<Ward, Pair<Int, Int>>{
        return treatmentRooms.groupBy { it.treatmentWard }
            .mapValues { entry ->
                val totalCapacity = entry.value.sumOf { it.capacity }
                val initialCapacity = entry.value.filter { it.monitoringCategory.description != "Korridor" && it.monitoringCategory.description != "Midlertidig" }
                    .sumOf { it.capacity }
                Pair(totalCapacity, initialCapacity)
            }
    }

    fun createRoom(endpoint: String, room: TreatmentRoom): Boolean {
        val connection = URI(endpoint).toURL().openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.setRequestProperty("Content-Type", "application/json")
        connection.doOutput = true
        connection.outputStream.use { outputStream ->
            outputStream.write(objectMapper.writeValueAsString(room).toByteArray(Charsets.UTF_8))
        }

        return connection.responseCode == HttpURLConnection.HTTP_OK
    }

    fun getAppropriateRooms (endpoint: String, roomRequest: RoomRequest) : List<Int>? {
        // Make a POST request to the endpoint with the room request
        val connection = URI(endpoint).toURL().openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.setRequestProperty("Content-Type", "application/json")
        connection.doOutput = true
        connection.outputStream.use { outputStream ->
            outputStream.write(objectMapper.writeValueAsString(roomRequest).toByteArray(Charsets.UTF_8))
        }

        // Read the response
        return if (connection.responseCode == HttpURLConnection.HTTP_OK) {
            connection.inputStream.bufferedReader().use { it.readText() }
                .let { objectMapper.readValue(it, object : TypeReference<List<Int>>() {}) }
        } else {
            null
        }
    }
}