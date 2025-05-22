package no.uio.bedreflyt.lm.service

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.net.HttpURLConnection
import java.net.URI

class AllocationService {

    private val objectMapper = jacksonObjectMapper()

    fun retrieveAllocations(endpoint: String): List<Map<String, Any>> {
        val allocationConnection = URI(endpoint).toURL().openConnection() as HttpURLConnection
        allocationConnection.requestMethod = "GET"
        allocationConnection.setRequestProperty("Content-Type", "application/json")
        allocationConnection.doOutput = true
        val allocationResponse = allocationConnection.inputStream.bufferedReader().use { it.readText() }
        val allocations = objectMapper.readValue(allocationResponse, object : TypeReference<List<Map<String, Any>>>() {})
        return allocations
    }
}