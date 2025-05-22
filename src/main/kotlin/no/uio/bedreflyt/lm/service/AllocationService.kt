package no.uio.bedreflyt.lm.service

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.net.HttpURLConnection
import java.net.URI
import kotlin.text.get

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

    fun getCounts (allocations: List<Map<String, Any>>): Map<Pair<String, String>, Int> {
        return allocations.groupBy { it["wardName"] to it["hospitalCode"] }.mapValues { it.value.size } as Map<Pair<String, String>, Int>
    }
}