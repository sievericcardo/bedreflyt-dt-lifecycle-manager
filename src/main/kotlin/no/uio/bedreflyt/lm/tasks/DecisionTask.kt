package no.uio.bedreflyt.lm.tasks

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.swagger.v3.oas.annotations.Operation
import no.uio.bedreflyt.lm.config.EnvironmentConfig
import no.uio.bedreflyt.lm.model.HospitalWard
import no.uio.bedreflyt.lm.service.AllocationService
import no.uio.bedreflyt.lm.service.CorridorService
import no.uio.bedreflyt.lm.service.OfficeService
import no.uio.bedreflyt.lm.service.StateService
import no.uio.bedreflyt.lm.service.TreatmentRoomService
import no.uio.bedreflyt.lm.service.WardService
import no.uio.bedreflyt.lm.types.Office
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
    private val officeService: OfficeService,
    private val allocationService: AllocationService,
    private val wardService: WardService,
    environmentConfig: EnvironmentConfig
) {

    private val log: Logger = Logger.getLogger(DecisionTask::class.java.name)
    private val objectMapper = jacksonObjectMapper()
    private val endpoint = environmentConfig.getOrDefault("BEDREFLYT_API", "localhost") + ":" + environmentConfig.getOrDefault("BEDREFLYT_PORT", "8090") + "/api/v1"

    private val roomsEndpoint = "http://$endpoint/fuseki/rooms"
    private val officeEndpoint = "http://$endpoint/fuseki/offices"
    private val wardEndpoint = "http://$endpoint/fuseki/wards"
    private val allocationsEndpoint = "http://$endpoint/patient-allocations"
    private val simulatedAllocationsEndpoint = "http://$endpoint/patient-allocations/simulated"

    /**
     * Gets available rooms that can be opened with their corresponding penalties
     *
     * @param ward The ward to check
     * @param corridorOpen Whether the corridor is already open
     * @param treatmentRooms List of existing treatment rooms
     * @param offices List of offices
     * @return Pair of lists: first contains room numbers (0 for corridor), second contains penalties
     */
    private fun getAvailableRoomsWithPenalties(
        ward: Ward,
        corridorOpen: Boolean,
        treatmentRooms: List<TreatmentRoom>,
        offices: List<Office>
    ): Pair<List<Int>, List<Int>> {
        val availableRoomNumbers = mutableListOf<Int>()
        val penalties = mutableListOf<Int>()

        // Check if corridor can be opened (if not already open)
        val corridorAvailable = !corridorOpen && treatmentRooms.any {
            it.treatmentWard == ward && it.monitoringCategory.description == "Korridor"
        }

        if (!corridorAvailable) {
            availableRoomNumbers.add(0)  // 0 represents corridor
            penalties.add(ward.corridorPenalty.toInt())
        }

        // Find available offices (not already in treatment rooms)
        val availableOffices = offices.filter { office ->
            office.treatmentWard == ward &&
                    treatmentRooms.none { it.treatmentWard == ward && it.roomNumber == office.roomNumber }
        }

        availableOffices.forEach { office ->
            availableRoomNumbers.add(office.roomNumber)
            penalties.add(ward.officePenalty.toInt())
        }

        return Pair(availableRoomNumbers, penalties)
    }

    private fun minMaxAllocation(
        incomingPatients: Int,
        allocationCounts: Map<Pair<String, String>, Int>,
        corridors: Map<Ward, Boolean>,
        wardCapacities: Map<Ward, Int>,
        treatmentRooms: List<TreatmentRoom>,
        offices: List<Office>
    ): Map<Pair<String, String>, List<Int>> {
        log.info("Calculating minimum and maximum allocation for incoming patients")
        val allocationCountsWithIncoming = allocationCounts.mapValues { it.value + incomingPatients }
        val minMaxAllocations = mutableMapOf<Pair<String, String>, List<Int>>()

        allocationCountsWithIncoming.forEach { (key, count) ->
            val (wardName, hospitalCode) = key
            val ward = wardService.getWardByWardNameAndHospitalCode("$wardEndpoint/$wardName/$hospitalCode")
            val corridorOpen = corridors[ward] ?: false
            val wardCapacity = wardCapacities[ward] ?: 0

            if (count > wardCapacity) {
                log.info("Ward $wardName in hospital $hospitalCode has exceeded capacity with $count patients. Finding appropriate rooms to open.")
                val patientsExceedingCapacity = count - wardCapacity

                val availableRooms = treatmentRooms.filter { it.treatmentWard == ward }
                val availableOffices = offices.filter { office ->
                    office.treatmentWard == ward &&
                            treatmentRooms.none { it.treatmentWard == ward && it.roomNumber == office.roomNumber }
                }

                // Check if corridor can be opened (if not already open)
                val corridorPenalty = if (!corridorOpen && availableRooms.any { it.monitoringCategory.description == "Korridor" }) {
                    ward.corridorPenalty
                } else {
                    Int.MAX_VALUE
                }

                // Calculate total office penalties
                val officePenaltySum = availableOffices.size * ward.officePenalty

                val roomsToOpen = if (corridorPenalty.toDouble() < officePenaltySum.toDouble()) {
                    log.info("Opening corridor for ward $wardName in hospital $hospitalCode")
                    listOf(0) // 0 represents corridor
                } else {
                    log.info("Opening offices for ward $wardName in hospital $hospitalCode")
                    // Return the room numbers of offices that need to be opened
                    availableOffices.map { it.roomNumber }
                }

                minMaxAllocations[key] = roomsToOpen
            } else {
                minMaxAllocations[key] = emptyList() // No need to open any rooms
            }
        }

        return minMaxAllocations
    }

    fun findAppropriateRoom(wardName: String, hospitalCode: String, incomingPatients: Int) {
        log.info("Find the proper room to open for the incoming patients")
        val treatmentRooms: List<TreatmentRoom> = treatmentRoomService.retrieveRooms("$roomsEndpoint/$wardName/$hospitalCode")
        val corridors: Map<Ward, Boolean> = treatmentRoomService.getWardCorridors(treatmentRooms)
        val wardCapacities: Map<Ward, Int> = treatmentRoomService.getWardCapacities(treatmentRooms)
        val offices: List<Office> = officeService.retrieveOffices("$officeEndpoint/$wardName/$hospitalCode")

        val allocations: List<Map<String, Any>> = allocationService.retrieveAllocations("$allocationsEndpoint/$wardName/$hospitalCode")
        val simulatedAllocations: List<Map<String, Any>> = allocationService.retrieveAllocations("$simulatedAllocationsEndpoint/$wardName/$hospitalCode")

        val actualCounts = allocationService.getCounts(allocations)
        val simulatedCounts = allocationService.getCounts(simulatedAllocations)

        val allocationCounts = (actualCounts.keys + simulatedCounts.keys).associateWith { key ->
            maxOf(actualCounts[key] ?: 0, simulatedCounts[key] ?: 0)
        }

        allocationCounts.forEach { (key, count) ->
            val (wardName, hospitalCode) = key
            log.info("Ward: $wardName, Hospital: $hospitalCode, Max Count: $count")
        }

        log.info("Rooms: ${getAvailableRoomsWithPenalties( 
            wardService.getWardByWardNameAndHospitalCode("$wardEndpoint/$wardName/$hospitalCode"),
            corridors[wardService.getWardByWardNameAndHospitalCode("$wardEndpoint/$wardName/$hospitalCode")] ?: false,
            treatmentRooms,
            offices
        )}")

        minMaxAllocation(
            incomingPatients,
            allocationCounts,
            corridors,
            wardCapacities,
            treatmentRooms,
            offices
        ).forEach { (key, value) ->
            log.info("Ward: ${key.first}, Hospital: ${key.second}, Min Max Allocation: $value")
        }
    }

//    @Scheduled(cron = "0 */1 * * * *") // Execute every 5 minutes
    @Operation(summary = "Make a decision every 5 minutes")
    fun makeDecision () {
        log.info("Making decision")
        val treatmentRooms: List<TreatmentRoom> = treatmentRoomService.retrieveRooms(roomsEndpoint)
        val corridors: Map<Ward, Boolean> = treatmentRoomService.getWardCorridors(treatmentRooms)
        val wardCapacities: Map<Ward, Int> = treatmentRoomService.getWardCapacities(treatmentRooms)

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

        corridorService.checkCorridor(endpoint, wardCapacities, corridors, allocationCounts)
    }
}