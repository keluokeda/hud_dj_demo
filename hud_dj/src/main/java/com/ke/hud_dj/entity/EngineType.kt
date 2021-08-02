package com.ke.hud_dj.entity

enum class EngineType(val typeName: String, val type: Int) {

    GasA("燃油车A", 0),

    GasB("燃油车B", 1),

    HEVOrMHEV("油电混动(HEV/MHEV)", 2),

    PHEV("插电混动(PHEV)", 3),

    REV("增程混动(REV)", 4),

    Electric("纯电动", 5),


}

fun fromType(type: Int):EngineType?{
   return EngineType.values().find { it.type == type }
}