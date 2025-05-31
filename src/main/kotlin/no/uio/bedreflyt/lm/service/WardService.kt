package no.uio.bedreflyt.lm.service

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.uio.bedreflyt.lm.types.Ward
import org.springframework.stereotype.Service
import java.net.HttpURLConnection
import java.net.URI

@Service
class WardService {

    private val objectMapper = jacksonObjectMapper()

    fun getWardByWardNameAndHospitalCode (endpoint: String): Ward {
        val connection = URI(endpoint).toURL().openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.setRequestProperty("Content-Type", "application/json")
        connection.doOutput = true
        val officeResponse = connection.inputStream.bufferedReader().use { it.readText() }
        return objectMapper.readValue(officeResponse, object : TypeReference<Ward>() {})
    }
}