package no.uio.bedreflyt.lm.service

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.uio.bedreflyt.lm.model.HospitalWard
import no.uio.bedreflyt.lm.types.Ward
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URI
import kotlin.collections.component1
import kotlin.collections.component2

@Service
class CorridorService (
    private val stateService: StateService
) {

    private val log: Logger = LoggerFactory.getLogger(CorridorService::class.java.name)

    fun createCorridor(endpoint: String, hospitalCode: String, wardName: String, capacity: Int): Boolean {
        val roomConnection = URI(endpoint).toURL().openConnection() as HttpURLConnection
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
            log.warn("API returned status code ${roomConnection.responseCode}")
        }

        return false
    }

    fun removeCorridor(endpoint: String, hospitalCode: String, wardName: String) : Boolean {
        val roomsEndpoint = "$endpoint/0/$wardName/$hospitalCode"
        val roomConnection = URI(roomsEndpoint).toURL().openConnection() as HttpURLConnection
        roomConnection.requestMethod = "DELETE"
        roomConnection.setRequestProperty("Content-Type", "application/json")
        roomConnection.doOutput = true

        log.info("Invoking API with request")
        if (roomConnection.responseCode != 200) {
            log.warn("API returned status code ${roomConnection.responseCode}")
            return false
        } else {
            log.info("API returned status code ${roomConnection.responseCode}")
        }

        return true
    }

    fun checkCorridor (endpoint: String, wardCapacities: Map<Ward, Int>, corridors: Map<Ward, Boolean>, allocationCounts: Map<Pair<String, String>, Int>) {
        wardCapacities.forEach { (key, capacity) ->
            var currentCapacity = capacity
            val corridor = corridors[key] ?: false
            if (corridor) {
                currentCapacity -= key.corridorCapacity
            }
            val threshold = currentCapacity - (currentCapacity.toDouble()*key.capacityThreshold/100).toInt()
            val allocationCount = allocationCounts[key.wardName to key.wardHospital.hospitalCode] ?: 0

            if (allocationCount > threshold && !corridor) {
                log.info("Creating corridor for ${key.wardName} in ${key.wardHospital.hospitalCode}")
                val hospitalWard = HospitalWard(key.wardName, key.wardHospital.hospitalCode, capacity, true)
                stateService.addWard(hospitalWard)
                if (createCorridor(endpoint, key.wardHospital.hospitalCode, key.wardName, key.corridorCapacity)) {
                    log.info("Corridor created for ${key.wardName} in ${key.wardHospital.hospitalCode}")
                }
            } else if (allocationCount < threshold && corridor) {
                val hospitalWard = HospitalWard(key.wardName, key.wardHospital.hospitalCode, capacity, false)
                stateService.addWard(hospitalWard)
                if (removeCorridor(endpoint, key.wardHospital.hospitalCode, key.wardName)) {
                    log.info("Corridor removed for ${key.wardName} in ${key.wardHospital.hospitalCode}")
                }
            }
        }
    }
}