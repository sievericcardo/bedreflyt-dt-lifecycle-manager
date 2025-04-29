package no.uio.bedreflyt.lm.tasks

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.swagger.v3.oas.annotations.Operation
import no.uio.bedreflyt.lm.config.EnvironmentConfig
import no.uio.bedreflyt.lm.model.HospitalWard
import no.uio.bedreflyt.lm.service.StateService
import no.uio.bedreflyt.lm.types.TreatmentRoom
import no.uio.bedreflyt.lm.types.Ward
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
//    private val capacityThreshold = environmentConfig.getOrDefault("DECISION_TOLERANCE", "10").toInt()

    private fun createCorridor(hospitalCode: String, wardName: String, capacity: Int): Boolean {
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

    private fun removeCorridor(hospitalCode: String, wardName: String) : Boolean {
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

    @Scheduled(cron = "0 */1 * * * *") // Execute every 5 minutes
    @Operation(summary = "Make a decision every 5 minutes")
    fun makeDecision () {
        val roomsEndpoint = "http://$endpoint/fuseki/rooms"
        val roomConnection = URI(roomsEndpoint).toURL().openConnection() as HttpURLConnection
        roomConnection.requestMethod = "GET"
        roomConnection.setRequestProperty("Content-Type", "application/json")
        roomConnection.doOutput = true
        val roomResponse = roomConnection.inputStream.bufferedReader().use { it.readText() }

        val treatmentRooms: List<TreatmentRoom> = objectMapper.readValue(roomResponse, object : TypeReference<List<TreatmentRoom>>() {})
        val corridors: Map<Ward, Boolean> = treatmentRooms.groupBy { it.treatmentWard }
            .mapValues { entry ->
                entry.value.any { it.monitoringCategory.description == "Korridor" }
            }
        val wardCapacities: Map<Ward, Int> = treatmentRooms.groupBy { it.treatmentWard }
            .mapValues { entry ->
                entry.value.sumOf { it.capacity }
            }

        val allocationsEndpoint = "http://$endpoint/patient-allocations"
        val allocationsConnection = URI(allocationsEndpoint).toURL().openConnection() as HttpURLConnection
        allocationsConnection.requestMethod = "GET"
        allocationsConnection.setRequestProperty("Content-Type", "application/json")
        allocationsConnection.doOutput = true
        val allocationResponse = allocationsConnection.inputStream.bufferedReader().use { it.readText() }
        val allocations: List<Map<String, Any>> = objectMapper.readValue(allocationResponse, object : TypeReference<List<Map<String, Any>>>() {})

        val allocationCounts = allocations.groupBy {
            it["wardName"] to it["hospitalCode"]
        }.mapValues {
            it.value.size
        }

        allocationCounts.forEach { (key, count) ->
            val (wardName, hospitalCode) = key
            log.info("Ward: $wardName, Hospital: $hospitalCode, Count: $count")
        }

        wardCapacities.forEach { (key, capacity) ->
            var currentCapacity = capacity
            val corridor = corridors[key] ?: false
            if (corridor) {
                currentCapacity -= 30
            }
            val threshold = currentCapacity - (currentCapacity.toDouble()*key.capacityThreshold/100).toInt()
            val allocationCount = allocationCounts[key.wardName to key.wardHospital.hospitalCode] ?: 0

            if (allocationCount > threshold && !corridor) {
                log.info("Creating corridor for ${key.wardName} in ${key.wardHospital.hospitalCode}")
                val hospitalWard = HospitalWard(key.wardName, key.wardHospital.hospitalCode, capacity, true)
                stateService.addWard(hospitalWard)
                if (createCorridor(key.wardHospital.hospitalCode, key.wardName, key.corridorCapacity)) {
                    log.info("Corridor created for ${key.wardName} in ${key.wardHospital.hospitalCode}")
                }
            } else if (allocationCount < threshold && corridor) {
                val hospitalWard = HospitalWard(key.wardName, key.wardHospital.hospitalCode, capacity, false)
                stateService.addWard(hospitalWard)
                if (removeCorridor(key.wardHospital.hospitalCode, key.wardName)) {
                    log.info("Corridor removed for ${key.wardName} in ${key.wardHospital.hospitalCode}")
                }
            }
        }
    }
}