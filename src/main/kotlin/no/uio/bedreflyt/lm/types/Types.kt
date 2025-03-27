package no.uio.bedreflyt.lm.types

data class Floor (
    val floorNumber: Int
)

data class City (
    val cityName: String
)

data class Hospital (
    val hospitalName: String,
    val hospitalCode: String,
    val hospitalCity: City
)

data class Ward (
    val wardName: String,
    val wardCode: String?,
    val wardHospital: Hospital,
    val wardFloor: Floor
)

data class MonitoringCategory (
    val category: Int,
    val description: String
)

data class TreatmentRoom (
    val roomNumber: Int,
    val capacity: Int,
    val treatmentWard: Ward,
    val hospital: Hospital,
    val monitoringCategory: MonitoringCategory
)
