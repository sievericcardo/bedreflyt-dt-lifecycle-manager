package no.uio.bedreflyt.lm.service

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.uio.bedreflyt.lm.types.Office
import no.uio.bedreflyt.lm.types.TreatmentRoom
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URI

@Service
class OfficeService {

    private val log: Logger = LoggerFactory.getLogger(OfficeService::class.java.name)
    private val objectMapper = jacksonObjectMapper()

    fun createOffice(endpoint: String, hospitalCode: String, wardName: String, capacity: Int, roomNumber: Int, penalty: Double): Boolean {
        val roomConnection = URI(endpoint).toURL().openConnection() as HttpURLConnection
        roomConnection.requestMethod = "POST"
        roomConnection.setRequestProperty("Content-Type", "application/json")
        roomConnection.doOutput = true
        val request = mapOf(
            "roomNumber" to roomNumber,
            "capacity" to capacity,
            "penalty" to penalty,
            "hospital" to hospitalCode,
            "ward" to wardName,
            "categoryDescription" to "Midlertidig"
        )

        val jsonBody = jacksonObjectMapper().writeValueAsString(request)

        log.info("Invoking API with request $jsonBody")
        OutputStreamWriter(roomConnection.outputStream).use {
            it.write(jsonBody)
        }

        if (roomConnection.responseCode == 200) {
            log.info("API returned status code ${roomConnection.responseCode}")
            return true
        } else {
            log.warn("API returned status code ${roomConnection.responseCode}")
        }

        return false
    }

    fun retrieveOffices(endpoint: String): List<Office> {
        val officeConnection = URI(endpoint).toURL().openConnection() as HttpURLConnection
        officeConnection.requestMethod = "GET"
        officeConnection.setRequestProperty("Content-Type", "application/json")
        officeConnection.doOutput = true
        val officeResponse = officeConnection.inputStream.bufferedReader().use { it.readText() }
        return objectMapper.readValue(officeResponse, object : TypeReference<List<Office>>() {})
    }

    /**
     * Removes a corridor from the specified ward in the specified hospital.
     * @param endpoint The API endpoint to call.
     * @param room The corridor to be removed.
     * @return True if the corridor was successfully removed, false otherwise.
     */
    fun removeOffice(endpoint: String, room: TreatmentRoom) : Boolean {
        val roomsEndpoint = "$endpoint/${room.roomNumber}/${room.treatmentWard.wardName}/${room.hospital.hospitalCode}"
        val roomConnection = URI(roomsEndpoint).toURL().openConnection() as HttpURLConnection
        roomConnection.requestMethod = "DELETE"
        roomConnection.setRequestProperty("Content-Type", "application/json")
        roomConnection.doOutput = true

        log.info("Invoking API with request")
        if (roomConnection.responseCode != 200) {
            log.warn("Error while removing corridor: API returned status code ${roomConnection.responseCode}")
            return false
        } else {
            log.info("Corridor ${room.roomNumber} in ward ${room.treatmentWard.wardName} at hospital ${room.hospital.hospitalCode} removed successfully")
        }

        return true
    }
}