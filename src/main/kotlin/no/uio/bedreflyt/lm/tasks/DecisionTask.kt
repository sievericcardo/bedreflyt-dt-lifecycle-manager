package no.uio.bedreflyt.lm.tasks

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.swagger.v3.oas.annotations.Operation
import no.uio.bedreflyt.lm.config.EnvironmentConfig
import no.uio.bedreflyt.lm.service.*
import no.uio.bedreflyt.lm.types.Office
import no.uio.bedreflyt.lm.types.RoomInfo
import no.uio.bedreflyt.lm.types.RoomRequest
import no.uio.bedreflyt.lm.types.TreatmentRoom
import no.uio.bedreflyt.lm.types.Ward
import org.springframework.stereotype.Component
import java.util.logging.Logger

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
    private val solverEndpoint = environmentConfig.getOrDefault("SOLVER_API", "localhost") + ":" + environmentConfig.getOrDefault("SOLVER_PORT", "8000") + "/api"

    private val roomsEndpoint = "http://$endpoint/fuseki/rooms"
    private val officeEndpoint = "http://$endpoint/fuseki/offices"
    private val wardEndpoint = "http://$endpoint/fuseki/wards"
    private val allocationsEndpoint = "http://$endpoint/patient-allocations"
    private val simulatedAllocationsEndpoint = "http://$endpoint/patient-allocations/simulated"

    private val roomOpenerEndpoint = "http://$solverEndpoint/room-opener"

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
    ): List<RoomInfo> {
        val roomInfos = mutableListOf<RoomInfo>()

        // Check if corridor can be opened (if not already open)
        val corridorAvailable = !corridorOpen && treatmentRooms.any {
            it.treatmentWard == ward && it.monitoringCategory.description == "Korridor"
        }

        if (!corridorAvailable) {
            roomInfos.add(RoomInfo(0, ward.corridorCapacity, ward.corridorPenalty.toInt())) // 0 represents corridor
        }

        // Find available offices (not already in treatment rooms)
        val availableOffices = offices.filter { office ->
            office.treatmentWard == ward &&
                    treatmentRooms.none { it.treatmentWard == ward && it.roomNumber == office.roomNumber }
        }

        availableOffices.forEach { office ->
            roomInfos.add(RoomInfo(
                roomNumber = office.roomNumber,
                capacity = office.capacity,
                penalty = ward.officePenalty.toInt()
            ))
        }

        return roomInfos
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

    fun findAppropriateRoom(wardName: String, hospitalCode: String, incomingPatients: Int): Boolean {
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

        val roomInfo: List<RoomInfo> = getAvailableRoomsWithPenalties(
            wardService.getWardByWardNameAndHospitalCode("$wardEndpoint/$wardName/$hospitalCode"),
            corridors[wardService.getWardByWardNameAndHospitalCode("$wardEndpoint/$wardName/$hospitalCode")] ?: false,
            treatmentRooms,
            offices
        )

        if (wardCapacities.size != 1) {
            log.warning("Expected only one ward in wardCapacities, found: ${wardCapacities.size}")
            return false
        }
        val currentCapacity = wardCapacities.values.firstOrNull() ?: 0
        val freeCapacity = currentCapacity - allocationCounts.getOrDefault(Pair(wardName, hospitalCode), 0)
        val request = RoomRequest (
            currentFreeCapacity = freeCapacity,
            incomingPatients = incomingPatients,
            roomNumbers = roomInfo.map { it.roomNumber },
            capacities = roomInfo.map { it.capacity },
            penalties = roomInfo.map { it.penalty }
        )

        log.info("Requesting appropriate rooms with request: $request")
        val appropriateRooms: List<Int>? = treatmentRoomService.getAppropriateRooms(roomOpenerEndpoint, request)
        log.info("Appropriate rooms response: $appropriateRooms")
        if (appropriateRooms == null) {
            log.warning("No appropriate rooms found for ward $wardName in hospital $hospitalCode")
            return false
        }
        appropriateRooms.ifEmpty { return true }

        log.info("Appropriate rooms found for ward $wardName in hospital $hospitalCode: $appropriateRooms")
        appropriateRooms.forEach { roomNumber ->
            if (roomNumber == 0) {
                log.info("Opening corridor for ward $wardName in hospital $hospitalCode")
                corridorService.createCorridor(roomsEndpoint, hospitalCode, wardName, wardCapacities.values.firstOrNull() ?: 0)
            } else {
                log.info("Opening office room number $roomNumber for ward $wardName in hospital $hospitalCode")
                val room = TreatmentRoom(
                    roomNumber = roomNumber,
                    capacity = 1, // Assuming a default capacity of 1 for the office
                    treatmentWard = wardService.getWardByWardNameAndHospitalCode("$wardEndpoint/$wardName/$hospitalCode"),
                    hospital = wardService.getWardByWardNameAndHospitalCode("$wardEndpoint/$wardName/$hospitalCode").wardHospital,
                    monitoringCategory = treatmentRooms.firstOrNull { it.roomNumber == roomNumber }?.monitoringCategory
                        ?: throw IllegalArgumentException("Monitoring category not found for room $roomNumber")
                )
                treatmentRoomService.createRoom(roomsEndpoint, room)
            }
        }

        log.info("Rooms opened successfully for ward $wardName in hospital $hospitalCode")
        return true
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

        corridorService.checkCorridor(roomsEndpoint, wardCapacities, corridors, allocationCounts)
    }
}