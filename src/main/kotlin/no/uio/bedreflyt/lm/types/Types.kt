package no.uio.bedreflyt.lm.types

import com.fasterxml.jackson.annotation.JsonAlias
import com.fasterxml.jackson.annotation.JsonProperty

data class TriggerAllocationRequest (
    val incomingPatients: Int
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
    @JsonProperty("treatmentWard")
    @JsonAlias("ward")
    val treatmentWard: Ward,
    val hospital: Hospital,
    val monitoringCategory: MonitoringCategory
)