package no.uio.bedreflyt.lm.model

import no.uio.bedreflyt.lm.types.WardState

class HospitalWard (
    val wardName: String,
    val hospitalCode: String,
    val capacity: Int,
    private var fullCapacity: Boolean
) {
    fun getState() : WardState {
        if (fullCapacity) {
            return WardState.FULL
        }
        return WardState.NOT_FULL
    }

    fun setFullCapacity() {
        fullCapacity = !fullCapacity
    }
}