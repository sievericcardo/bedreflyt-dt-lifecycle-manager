package no.uio.bedreflyt.lm.service

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.uio.bedreflyt.lm.types.Office
import org.springframework.stereotype.Service
import java.net.HttpURLConnection
import java.net.URI

@Service
class OfficeService {

    private val objectMapper = jacksonObjectMapper()

    fun retrieveOffices(endpoint: String): List<Office> {
        val officeConnection = URI(endpoint).toURL().openConnection() as HttpURLConnection
        officeConnection.requestMethod = "GET"
        officeConnection.setRequestProperty("Content-Type", "application/json")
        officeConnection.doOutput = true
        val officeResponse = officeConnection.inputStream.bufferedReader().use { it.readText() }
        return objectMapper.readValue(officeResponse, object : TypeReference<List<Office>>() {})
    }
}