package no.uio.bedreflyt.lm.tasks

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.swagger.v3.oas.annotations.Operation
import no.uio.bedreflyt.lm.config.EnvironmentConfig
import no.uio.bedreflyt.lm.model.HospitalWard
import no.uio.bedreflyt.lm.service.AllocationService
import no.uio.bedreflyt.lm.service.CorridorService
import no.uio.bedreflyt.lm.service.StateService
import no.uio.bedreflyt.lm.service.TreatmentRoomService
import no.uio.bedreflyt.lm.types.TreatmentRoom
import no.uio.bedreflyt.lm.types.Ward
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URI
import java.util.logging.Logger
import kotlin.io.inputStream

@Component
class DecisionTask (
    private val stateService: StateService,
    private val corridorService: CorridorService,
    private val treatmentRoomService: TreatmentRoomService,
    private val allocationService: AllocationService,
    environmentConfig: EnvironmentConfig
) {

    private val log: Logger = Logger.getLogger(DecisionTask::class.java.name)
    private val objectMapper = jacksonObjectMapper()
    private val endpoint = environmentConfig.getOrDefault("BEDREFLYT_API", "localhost") + ":" + environmentConfig.getOrDefault("BEDREFLYT_PORT", "8090") + "/api/v1"

    @Scheduled(cron = "0 */1 * * * *") // Execute every 5 minutes
    @Operation(summary = "Make a decision every 5 minutes")
    fun makeDecision () {
        log.info("Making decision")
        val roomsEndpoint = "http://$endpoint/fuseki/rooms"
        val treatmentRooms: List<TreatmentRoom> = treatmentRoomService.retrieveRooms(roomsEndpoint)

        val corridors: Map<Ward, Boolean> = treatmentRoomService.getWardCorridors(treatmentRooms)
        val wardCapacities: Map<Ward, Int> = treatmentRoomService.getWardCapacities(treatmentRooms)

        val allocationsEndpoint = "http://$endpoint/patient-allocations"
        val simulatedAllocationsEndpoint = "http://$endpoint/patient-allocations/simulated"

        val allocations: List<Map<String, Any>> = allocationService.retrieveAllocations(allocationsEndpoint)
        val simulatedAllocations: List<Map<String, Any>> = allocationService.retrieveAllocations(simulatedAllocationsEndpoint)

        val actualCounts = allocationService.getCounts(allocations)
        val simulatedCounts = allocationService.getCounts(simulatedAllocations)

        val allocationCounts = (actualCounts.keys + simulatedCounts.keys).associateWith { key ->
            maxOf(actualCounts[key] ?: 0, simulatedCounts[key] ?: 0)
        }

        allocationCounts.forEach { (key, count) ->
            val (wardName, hospitalCode) = key
            log.info("Ward: $wardName, Hospital: $hospitalCode, Max Count: $count")
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
                if (corridorService.createCorridor(endpoint, key.wardHospital.hospitalCode, key.wardName, key.corridorCapacity)) {
                    log.info("Corridor created for ${key.wardName} in ${key.wardHospital.hospitalCode}")
                }
            } else if (allocationCount < threshold && corridor) {
                val hospitalWard = HospitalWard(key.wardName, key.wardHospital.hospitalCode, capacity, false)
                stateService.addWard(hospitalWard)
                if (corridorService.removeCorridor(endpoint, key.wardHospital.hospitalCode, key.wardName)) {
                    log.info("Corridor removed for ${key.wardName} in ${key.wardHospital.hospitalCode}")
                }
            }
        }
    }
}