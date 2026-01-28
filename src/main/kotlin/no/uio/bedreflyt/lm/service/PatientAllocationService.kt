package no.uio.bedreflyt.lm.service

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.uio.bedreflyt.lm.types.PatientAllocation
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.net.HttpURLConnection
import java.net.URI

@Service
class PatientAllocationService {

    private val objectMapper: ObjectMapper = jacksonObjectMapper().apply {
        registerModule(JavaTimeModule())
    }
    private val log: Logger = LoggerFactory.getLogger(PatientAllocationService::class.java.name)

    fun getPatientAllocations(endpoint: String): List<PatientAllocation> {
        val connection = URI(endpoint).toURL().openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.setRequestProperty("Content-Type", "application/json")
        connection.doOutput = true
        val response = connection.inputStream.bufferedReader().use { it.readText() }

        log.info("Invoking API with request to retrieve patient allocations")
        if (connection.responseCode != 200) {
            log.warn("API returned status code ${connection.responseCode} for patient allocations")
            return emptyList()
        } else {
            log.info("API returned status code ${connection.responseCode}: retrieving patient allocations")
            return objectMapper.readValue(response, object : TypeReference<List<PatientAllocation>>() {})
        }
    }
}