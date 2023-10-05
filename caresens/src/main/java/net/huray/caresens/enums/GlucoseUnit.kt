package net.huray.caresens.enums

enum class GlucoseUnit(val unit: String) {
    MG("mg/dL"),
    MMOL("mmol/L");

    companion object {
        fun getValue(glucoseUnit: GlucoseUnit?) =
            when (glucoseUnit) {
                MG -> "mg/dL"
                MMOL -> "mmol/L"
                else -> ""
            }
    }
}