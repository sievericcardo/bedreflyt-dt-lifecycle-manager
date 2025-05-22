package no.uio.bedreflyt.lm.service

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.springframework.stereotype.Service
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URI
import java.util.logging.Logger

@Service
class CorridorService {

    private val log: Logger = Logger.getLogger(CorridorService::class.java.name)

    fun createCorridor(endpoint: String, hospitalCode: String, wardName: String, capacity: Int): Boolean {
        val roomsEndpoint = "http://$endpoint/fuseki/rooms"
        val roomConnection = URI(roomsEndpoint).toURL().openConnection() as HttpURLConnection
        roomConnection.requestMethod = "POST"
        roomConnection.setRequestProperty("Content-Type", "application/json")
        roomConnection.doOutput = true
        val request = mapOf(
            "roomNumber" to 0,
            "capacity" to capacity,
            "hospital" to hospitalCode,
            "ward" to wardName,
            "categoryDescription" to "Korridor"
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
            log.warning("API returned status code ${roomConnection.responseCode}")
        }

        return false
    }

    fun removeCorridor(endpoint: String, hospitalCode: String, wardName: String) : Boolean {
        val roomsEndpoint = "http://$endpoint/fuseki/rooms/0/$wardName/$hospitalCode"
        val roomConnection = URI(roomsEndpoint).toURL().openConnection() as HttpURLConnection
        roomConnection.requestMethod = "DELETE"
        roomConnection.setRequestProperty("Content-Type", "application/json")
        roomConnection.doOutput = true

        log.info("Invoking API with request")
        if (roomConnection.responseCode != 200) {
            log.warning("API returned status code ${roomConnection.responseCode}")
            return false
        } else {
            log.info("API returned status code ${roomConnection.responseCode}")
        }

        return true
    }
}