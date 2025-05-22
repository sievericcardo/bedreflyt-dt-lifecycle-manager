package no.uio.bedreflyt.lm.service

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.uio.bedreflyt.lm.types.TreatmentRoom
import no.uio.bedreflyt.lm.types.Ward
import org.springframework.stereotype.Service
import java.net.HttpURLConnection
import java.net.URI

@Service
class TreatmentRoomService {

    private val objectMapper = jacksonObjectMapper()

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

    fun getWardCapacities (treatmentRooms: List<TreatmentRoom>) : Map<Ward, Int>{
        return treatmentRooms.groupBy { it.treatmentWard }
            .mapValues { entry ->
                entry.value.sumOf { it.capacity }
            }
    }
}