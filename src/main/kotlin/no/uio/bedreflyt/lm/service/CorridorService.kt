package no.uio.bedreflyt.lm.service

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.uio.bedreflyt.lm.model.HospitalWard
import no.uio.bedreflyt.lm.types.Corridor
import no.uio.bedreflyt.lm.types.Ward
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URI

@Service
class CorridorService (
    private val stateService: StateService
) {

    private val log: Logger = LoggerFactory.getLogger(CorridorService::class.java.name)
    private val objectMapper = jacksonObjectMapper()

    /**
     * Retrieves all corridors for a given ward in a hospital.
     * @param endpoint The API endpoint to call.
     * @param hospitalCode The code of the hospital.
     * @param wardName The name of the ward.
     * @return A list of corridors in the specified ward.
     */
    fun getCorridors(endpoint: String, hospitalCode: String, wardName: String): List<Corridor> {
        val roomsEndpoint = "$endpoint/$wardName/$hospitalCode"
        val roomConnection = URI(roomsEndpoint).toURL().openConnection() as HttpURLConnection
        roomConnection.requestMethod = "GET"
        roomConnection.setRequestProperty("Content-Type", "application/json")
        roomConnection.doOutput = true
        val roomResponse = roomConnection.inputStream.bufferedReader().use { it.readText() }

        log.info("Invoking API with request to retrieve corridors")
        if (roomConnection.responseCode != 200) {
            log.warn("API returned status code ${roomConnection.responseCode} for corridors")
            return emptyList()
        } else {
            log.info("API returned status code ${roomConnection.responseCode}: retrieving corridors")
            val corridors = objectMapper.readValue(roomResponse, object : TypeReference<List<Corridor>>() {})
            return corridors
        }
    }

    /**
     * Creates a corridor for the specified ward in the specified hospital. The corridor is created as a treatment room
     * @param endpoint The API endpoint to call.
     * @param hospitalCode The code of the hospital where the corridor is to be created.
     * @param wardName The name of the ward for which the corridor is to be created.
     * @param capacity The capacity of the corridor.
     * @return True if the corridor was successfully created, false otherwise.
     */
    fun createCorridor(endpoint: String, hospitalCode: String, wardName: String, capacity: Int, roomNumber: Int): Boolean {
        val roomConnection = URI(endpoint).toURL().openConnection() as HttpURLConnection
        roomConnection.requestMethod = "POST"
        roomConnection.setRequestProperty("Content-Type", "application/json")
        roomConnection.doOutput = true
        val request = mapOf(
            "roomNumber" to roomNumber,
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

    /**
     * Removes a corridor from the specified ward in the specified hospital.
     * @param endpoint The API endpoint to call.
     * @param corridor The corridor to be removed.
     * @return True if the corridor was successfully removed, false otherwise.
     */
    fun removeCorridor(endpoint: String, corridor: Corridor) : Boolean {
        val roomsEndpoint = "$endpoint/${corridor.roomNumber}/${corridor.treatmentWard.wardName}/${corridor.hospital.hospitalCode}"
        val roomConnection = URI(roomsEndpoint).toURL().openConnection() as HttpURLConnection
        roomConnection.requestMethod = "DELETE"
        roomConnection.setRequestProperty("Content-Type", "application/json")
        roomConnection.doOutput = true

        log.info("Invoking API with request")
        if (roomConnection.responseCode != 200) {
            log.warn("Error while removing corridor: API returned status code ${roomConnection.responseCode}")
            return false
        } else {
            log.info("Corridor ${corridor.roomNumber} in ward ${corridor.treatmentWard.wardName} at hospital ${corridor.hospital.hospitalCode} removed successfully")
        }

        return true
    }

//    fun checkCorridor (endpoint: String, wardCapacities: Map<Ward, Int>, corridors: Map<Ward, Boolean>, allocationCounts: Map<Pair<String, String>, Int>) {
//        wardCapacities.forEach { (key, capacity) ->
//            var currentCapacity = capacity
//            val corridor = corridors[key] ?: false
//            if (corridor) {
//                currentCapacity -= key.corridorCapacity
//            }
//            val threshold = currentCapacity - (currentCapacity.toDouble()*key.capacityThreshold/100).toInt()
//            val allocationCount = allocationCounts[key.wardName to key.wardHospital.hospitalCode] ?: 0
//
//            if (allocationCount > threshold && !corridor) {
//                log.info("Creating corridor for ${key.wardName} in ${key.wardHospital.hospitalCode}")
//                val hospitalWard = HospitalWard(key.wardName, key.wardHospital.hospitalCode, capacity, true)
//                stateService.addWard(hospitalWard)
//                if (createCorridor(endpoint, key.wardHospital.hospitalCode, key.wardName, key.corridorCapacity)) {
//                    log.info("Corridor created for ${key.wardName} in ${key.wardHospital.hospitalCode}")
//                }
//            } else if (allocationCount < threshold && corridor) {
//                val hospitalWard = HospitalWard(key.wardName, key.wardHospital.hospitalCode, capacity, false)
//                stateService.addWard(hospitalWard)
//                if (removeCorridor(endpoint, key.wardHospital.hospitalCode, key.wardName)) {
//                    log.info("Corridor removed for ${key.wardName} in ${key.wardHospital.hospitalCode}")
//                }
//            }
//        }
//    }
}