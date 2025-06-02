package no.uio.bedreflyt.lm.service

import no.uio.bedreflyt.lm.model.HospitalWard
import no.uio.bedreflyt.lm.types.WardState
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap

@Service
class StateService {
    private val wards = ConcurrentHashMap<String, HospitalWard>()

    fun addWard(ward: HospitalWard) {
        val key = "${ward.wardName}-${ward.hospitalCode}"
        wards[key] = ward
    }

    private fun getWard(wardName: String, hospitalCode: String): HospitalWard? {
        val key = "$wardName-$hospitalCode"
        return wards[key]
    }

    fun getState(wardName: String, hospitalCode: String): WardState? {
        val ward = getWard(wardName, hospitalCode)
        return ward?.getState()
    }
}