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

    @Scheduled(cron = "0 */1 * * * *") // Execute every 5 minutes
    @Operation(summary = "Make a decision every 5 minutes")
    fun makeDecision () {
        val endpoint = environmentConfig.getOrDefault("BEDREFLYT_API", "localhost") + ":" + environmentConfig.getOrDefault("BEDREFLYT_PORT", "8090") + "/api/v1"

        val roomsEndpoint = "http://$endpoint/fuseki/rooms"
        val roomConnection = URI(roomsEndpoint).toURL().openConnection() as HttpURLConnection
        roomConnection.requestMethod = "GET"
        roomConnection.setRequestProperty("Content-Type", "application/json")
        roomConnection.doOutput = true
        val roomResponse = roomConnection.inputStream.bufferedReader().use { it.readText() }

        val treatmentRooms: List<TreatmentRoom> = objectMapper.readValue(roomResponse, object : TypeReference<List<TreatmentRoom>>() {})
        val wardCapacities = treatmentRooms.groupBy { it.treatmentWard.wardName to it.hospital.hospitalCode }
            .mapValues { entry -> entry.value.sumOf { it.capacity } }

        wardCapacities.forEach { (key, capacity) ->
            val (wardName, hospitalCode) = key
            if (capacity > 40) {
                val hospitalWard = HospitalWard(wardName, hospitalCode, capacity, capacity > 40)
                stateService.addWard(hospitalWard)
            }
        }
    }
}