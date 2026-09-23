package com.example.audio

import java.util.UUID

data class CustomEqPreset(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val gains: FloatArray,
    val bassBoost: Float = 0.5f
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as CustomEqPreset
        return id == other.id && name == other.name && gains.contentEquals(other.gains) && bassBoost == other.bassBoost
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + gains.contentHashCode()
        result = 31 * result + bassBoost.hashCode()
        return result
    }
}
