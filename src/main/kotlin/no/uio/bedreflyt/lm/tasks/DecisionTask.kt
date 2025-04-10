package no.uio.bedreflyt.lm.tasks

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.swagger.v3.oas.annotations.Operation
import no.uio.bedreflyt.lm.config.EnvironmentConfig
import no.uio.bedreflyt.lm.model.HospitalWard
import no.uio.bedreflyt.lm.service.StateService
import no.uio.bedreflyt.lm.types.TreatmentRoom
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URI
import java.util.logging.Logger

@Component
class DecisionTask (
    private val stateService: StateService,
    private val environmentConfig: EnvironmentConfig
) {

    private val log: Logger = Logger.getLogger(DecisionTask::class.java.name)
    private val objectMapper = jacksonObjectMapper()
    private val endpoint = environmentConfig.getOrDefault("BEDREFLYT_API", "localhost") + ":" + environmentConfig.getOrDefault("BEDREFLYT_PORT", "8090") + "/api/v1"
    private val capacityThreshold = environmentConfig.getOrDefault("DECISION_TOLERANCE", "10").toInt()

    private fun createCorridor(hospitalCode: String, wardName: String): Boolean {
        val roomsEndpoint = "http://$endpoint/fuseki/rooms"
        val roomConnection = URI(roomsEndpoint).toURL().openConnection() as HttpURLConnection
        roomConnection.requestMethod = "POST"
        roomConnection.setRequestProperty("Content-Type", "application/json")
        roomConnection.doOutput = true
        val request = mapOf(
            "roomNumber" to 0,
            "capacity" to 30,
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

    private fun removeCorridor(hospitalCode: String, wardName: String) : Boolean {
        val roomsEndpoint = "http://$endpoint/fuseki/rooms/0/$wardName/$hospitalCode"
        val roomConnection = URI(roomsEndpoint).toURL().openConnection() as HttpURLConnection
        roomConnection.requestMethod = "DELETE"
        roomConnection.setRequestProperty("Content-Type", "application/json")
        roomConnection.doOutput = true

        log.info("Invoking API with request")
        if (roomConnection.responseCode != 200) {
            log.warning("API returned status code ${roomConnection.responseCode}")
            return true
        }

        return false
    }

    @Scheduled(cron = "0 */1 * * * *") // Execute every 5 minutes
    @Operation(summary = "Make a decision every 5 minutes")
    fun makeDecision () {
        val roomsEndpoint = "http://$endpoint/fuseki/rooms"
        val roomConnection = URI(roomsEndpoint).toURL().openConnection() as HttpURLConnection
        roomConnection.requestMethod = "GET"
        roomConnection.setRequestProperty("Content-Type", "application/json")
        roomConnection.doOutput = true
        val roomResponse = roomConnection.inputStream.bufferedReader().use { it.readText() }

        var corridor = false
        val treatmentRooms: List<TreatmentRoom> = objectMapper.readValue(roomResponse, object : TypeReference<List<TreatmentRoom>>() {})
        val wardCapacities = treatmentRooms.groupBy { it.treatmentWard.wardName to it.hospital.hospitalCode }
            .mapValues { entry ->
                if (entry.value.any { it.roomNumber == 0 } || entry.value.any { it.monitoringCategory.description == "Korridor"}) {
                    corridor = true
                }
                entry.value.sumOf { it.capacity }
            }

        val allocationsEndpoint = "http://$endpoint/patient-allocations"
        val allocationsConnection = URI(allocationsEndpoint).toURL().openConnection() as HttpURLConnection
        allocationsConnection.requestMethod = "GET"
        allocationsConnection.setRequestProperty("Content-Type", "application/json")
        allocationsConnection.doOutput = true
        val allocationResponse = allocationsConnection.inputStream.bufferedReader().use { it.readText() }
        val allocationCount = objectMapper.readValue(allocationResponse, object : TypeReference<List<Any>>() {}).size

        wardCapacities.forEach { (key, capacity) ->
            val (wardName, hospitalCode) = key
            var currentCapacity = capacity
            if (corridor) {
                currentCapacity -= 30
            }
            if (currentCapacity > (allocationCount - (allocationCount.toDouble()*capacityThreshold/100).toInt()) && !corridor) {
                log.info("Creating corridor for $wardName in $hospitalCode")
                val hospitalWard = HospitalWard(wardName, hospitalCode, capacity, true)
                stateService.addWard(hospitalWard)
                if (createCorridor(hospitalCode, wardName)) {
                    log.info("Corridor created for $wardName in $hospitalCode")
                }
            } else if (currentCapacity < (allocationCount - (allocationCount.toDouble()*capacityThreshold/100).toInt()) && corridor) {
                val hospitalWard = HospitalWard(wardName, hospitalCode, capacity, false)
                stateService.addWard(hospitalWard)
                if (removeCorridor(hospitalCode, wardName)) {
                    log.info("Corridor removed for $wardName in $hospitalCode")
                }
            }
        }
    }
}