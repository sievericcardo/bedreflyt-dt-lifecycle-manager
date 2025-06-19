package no.uio.bedreflyt.lm.tasks

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.swagger.v3.oas.annotations.Operation
import no.uio.bedreflyt.lm.config.EnvironmentConfig
import no.uio.bedreflyt.lm.service.*
import no.uio.bedreflyt.lm.types.Corridor
import no.uio.bedreflyt.lm.types.Office
import no.uio.bedreflyt.lm.types.RoomInfo
import no.uio.bedreflyt.lm.types.RoomRequest
import no.uio.bedreflyt.lm.types.TreatmentRoom
import no.uio.bedreflyt.lm.types.Ward
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.util.logging.Logger

@Component
class DecisionTask (
    private val stateService: StateService,
    private val corridorService: CorridorService,
    private val treatmentRoomService: TreatmentRoomService,
    private val officeService: OfficeService,
    private val allocationService: AllocationService,
    private val patientAllocationService: PatientAllocationService,
    private val wardService: WardService,
    environmentConfig: EnvironmentConfig
) {

    private val log: Logger = Logger.getLogger(DecisionTask::class.java.name)
    private val objectMapper = jacksonObjectMapper()
    private val endpoint = environmentConfig.getOrDefault("BEDREFLYT_API", "localhost") + ":" + environmentConfig.getOrDefault("BEDREFLYT_PORT", "8090") + "/api/v1"
    private val solverEndpoint = environmentConfig.getOrDefault("SOLVER_API", "localhost") + ":" + environmentConfig.getOrDefault("SOLVER_PORT", "8000") + "/api"

    private val roomsEndpoint = "http://$endpoint/fuseki/rooms"
    private val officeEndpoint = "http://$endpoint/fuseki/offices"
    private val corridorEndpoint = "http://$endpoint/fuseki/corridors"
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
        treatmentRooms: List<TreatmentRoom>,
        corridors: List<Corridor>,
        offices: List<Office>
    ): List<RoomInfo> {
        val roomInfos = mutableListOf<RoomInfo>()

        val availableCorridors = corridors.filter { corridor ->
            corridor.treatmentWard == ward && treatmentRooms.none { it.treatmentWard == ward && it.roomNumber == corridor.roomNumber }
        }

        availableCorridors.forEach { corridor ->
            roomInfos.add(RoomInfo(
                roomNumber = 0, // 0 represents corridor
                corridor.monitoringCategory,
                capacity = corridor.capacity,
                penalty = corridor.penalty.toInt()
            ))
        }

        // Find available offices (not already in treatment rooms)
        val availableOffices = offices.filter { office ->
            office.treatmentWard == ward &&
                    treatmentRooms.none { it.treatmentWard == ward && it.roomNumber == office.roomNumber }
        }

        availableOffices.forEach { office ->
            roomInfos.add(RoomInfo(
                roomNumber = office.roomNumber,
                category = office.monitoringCategory,
                capacity = office.capacity,
                penalty = office.penalty.toInt()
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

    private fun removeOpenedCorridorsOffice(treatmentRooms: List<TreatmentRoom>, wardName: String, hospitalCode: String, simulation: Boolean) {
        log.info("Removing opened corridors and offices")
        val allocations = if (simulation) {
            patientAllocationService.getPatientAllocations("$simulatedAllocationsEndpoint/$wardName/$hospitalCode")
        } else {
            patientAllocationService.getPatientAllocations("$allocationsEndpoint/$wardName/$hospitalCode")
        }

        treatmentRooms.forEach { room ->
            if (room.monitoringCategory.description == "Korridor" && !allocations.any { it.roomNumber == room.roomNumber }) {
                log.info("Removing corridor room ${room.roomNumber} in ward $wardName at hospital $hospitalCode")
                corridorService.removeCorridor(roomsEndpoint, room)
            } else if (room.monitoringCategory.description == "Midlertidig" && !allocations.any { it.roomNumber == room.roomNumber }) {
                log.info("Removing office room ${room.roomNumber} in ward $wardName at hospital $hospitalCode")
                officeService.removeOffice(roomsEndpoint, room)
            }
        }
    }

    private fun computeThreshold(completeCapacity: Double, threshold: Double): Int {
        return (completeCapacity * threshold / 100).toInt()
    }

    /**
     * Finds the appropriate room to open for incoming patients in a specific ward and hospital.
     *
     * This method checks the current allocations, ward capacities, and available treatment rooms and offices.
     * It determines if a corridor can be opened or if office rooms need to be opened based on the incoming patients.
     * It also handles both actual and simulated allocations based on the `simulation` parameter.
     * It returns true if appropriate rooms were found and opened, false otherwise.
     *
     * @param wardName The name of the ward.
     * @param hospitalCode The code of the hospital.
     * @param incomingPatients The number of incoming patients.
     * @param simulation Whether this is a simulation or not.
     * @return True if appropriate rooms were found and opened, false otherwise.
     */
    fun findAppropriateRoom(wardName: String, hospitalCode: String, incomingPatients: Int, simulation: Boolean): Boolean {
        log.info("Find the proper room to open for the incoming patients")
        val treatmentRooms: List<TreatmentRoom> = treatmentRoomService.retrieveRooms("$roomsEndpoint/$wardName/$hospitalCode")
//        val corridors: Map<Ward, Boolean> = treatmentRoomService.getWardCorridors(treatmentRooms)
        val wardCapacities: Map<Ward, Int> = treatmentRoomService.getWardCapacities(treatmentRooms)
        val offices: List<Office> = officeService.retrieveOffices("$officeEndpoint/$wardName/$hospitalCode")
        val corridors: List<Corridor> = corridorService.getCorridors(corridorEndpoint, hospitalCode, wardName)
        val currentWard= wardService.getWardByWardNameAndHospitalCode("$wardEndpoint/$wardName/$hospitalCode")

        if (wardCapacities.size != 1) {
            log.warning("Expected only one ward in wardCapacities, found: ${wardCapacities.size}")
            return false
        }

        val allocations = if (simulation) {
            allocationService.retrieveAllocations("$simulatedAllocationsEndpoint/$wardName/$hospitalCode")
        } else {
            allocationService.retrieveAllocations("$allocationsEndpoint/$wardName/$hospitalCode")
        }
        val allocationCounts = allocationService.getCounts(allocations)

        allocationCounts.forEach { (key, count) ->
            val (wardName, hospitalCode) = key
            log.info("Ward: $wardName, Hospital: $hospitalCode, Max Count: $count")
        }
        wardCapacities[currentWard]?.let {
            val threshold = computeThreshold(it.toDouble(), currentWard.capacityThreshold)
            log.info("Threshold for ward $wardName in hospital $hospitalCode is $threshold and incoming patients are $incomingPatients")
            if (threshold > allocationCounts.getOrDefault(Pair(wardName, hospitalCode), 0) + incomingPatients) {
                log.info("Ward $wardName in hospital $hospitalCode below threshold")
                removeOpenedCorridorsOffice(treatmentRooms, wardName, hospitalCode, simulation)
                return true
            }
        }

        val roomInfo: List<RoomInfo> = getAvailableRoomsWithPenalties(
            wardService.getWardByWardNameAndHospitalCode("$wardEndpoint/$wardName/$hospitalCode"),
            treatmentRooms,
            corridors,
            offices
        )

        val currentCapacity = wardCapacities.values.firstOrNull() ?: 0
        val freeCapacity = computeThreshold(currentCapacity.toDouble(), currentWard.capacityThreshold) - allocationCounts.getOrDefault(Pair(wardName, hospitalCode), 0)
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
            // check if the room number is in the corridor list
            if (corridors.any { it.roomNumber == roomNumber }) {
                log.info("Opening corridor for ward $wardName in hospital $hospitalCode")
                val corridor: Corridor = corridors.first { it.roomNumber == roomNumber }
                corridorService.createCorridor(roomsEndpoint, hospitalCode, wardName, corridor.capacity, corridor.roomNumber, corridor.penalty)
            }else {
                log.info("Opening office room number $roomNumber for ward $wardName in hospital $hospitalCode")
                val office: Office? = offices.firstOrNull { it.roomNumber == roomNumber }
                office.let {
                    if (it != null) {
                        officeService.createOffice(roomsEndpoint, hospitalCode, wardName, it.capacity, it.roomNumber, it.penalty)
                    } else {
                        log.warning("Office with room number $roomNumber not found for ward $wardName in hospital $hospitalCode")
                    }
                }
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

//        corridorService.checkCorridor(roomsEndpoint, wardCapacities, corridors, allocationCounts)
    }
}