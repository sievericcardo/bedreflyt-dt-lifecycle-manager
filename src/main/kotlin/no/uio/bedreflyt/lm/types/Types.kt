package no.uio.bedreflyt.lm.types

import com.fasterxml.jackson.annotation.JsonAlias
import com.fasterxml.jackson.annotation.JsonProperty
import java.time.LocalDateTime

data class TriggerAllocationRequest (
    val incomingPatients: Int,
    val simulation: Boolean,
)

data class Floor (
    val floorNumber: Int
)

data class City (
    val cityName: String
)

data class Hospital (
    val hospitalName: String,
    val hospitalCode: String,
    @JsonProperty("hospitalCity")
    @JsonAlias("city")
    val hospitalCity: City
)

data class Ward (
    val wardName: String,
    val wardCode: String?,
    val capacityThreshold: Double,
    val corridorPenalty: Double,
    val officePenalty: Double,
    val corridorCapacity: Int,
    @JsonProperty("wardHospital")
    @JsonAlias("hospital")
    val wardHospital: Hospital,
    @JsonProperty("wardFloor")
    @JsonAlias("floor")
    val wardFloor: Floor
)

data class MonitoringCategory (
    val category: Int,
    val description: String
)

data class TreatmentRoom (
    val roomNumber: Int,
    val capacity: Int,
    val penalty: Double,
    @JsonProperty("treatmentWard")
    @JsonAlias("ward")
    val treatmentWard: Ward,
    val hospital: Hospital,
    val monitoringCategory: MonitoringCategory
)

data class Office (
    val roomNumber: Int,
    val capacity: Int,
    val penalty: Double,
    val available: Boolean,
    @JsonProperty("treatmentWard")
    @JsonAlias("ward")
    val treatmentWard: Ward,
    val hospital: Hospital,
    val monitoringCategory: MonitoringCategory
)

data class Corridor (
    val roomNumber: Int,
    val capacity: Int,
    val penalty: Double,
    @JsonProperty("treatmentWard")
    @JsonAlias("ward")
    val treatmentWard: Ward,
    val hospital: Hospital,
    val monitoringCategory: MonitoringCategory
)

data class RoomInfo (
    val roomNumber: Int,
    val category: MonitoringCategory,
    val capacity: Int,
    val penalty: Int
)

data class RoomRequest (
    val currentFreeCapacity: Int,
    val incomingPatients: Int,
    val roomNumbers: List<Int>,
    val capacities: List<Int>,
    val penalties: List<Int>
)

data class RoomResponse (
    val roomNumbers: List<Int>?
)

data class Patient (
    val patientId: String,
    val patientName: String,
    val patientSurname: String,
    val patientAddress: String,
    val city: String,
    val patientBirthDate: LocalDateTime,
    val gender: String
)

data class PatientAllocation (
    val id: Long,
    val patientId: Patient,
    val acute: Boolean,
    val diagnosisCode: String,
    val diagnosisName: String,
    val acuteCategory: Int,
    val careCategory: Int,
    val monitoringCategory: Int,
    val careId: Int,
    val contagious: Boolean,
    val wardName: String,
    val hospitalCode: String,
    val roomNumber: Int,
    val dueDate: LocalDateTime,
    val simulated: Boolean
)