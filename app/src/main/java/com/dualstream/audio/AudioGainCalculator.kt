package com.dualstream.audio

class AudioGainCalculator(
    private val targetPeakRatio: Float = 0.9f,
    private val attack: Float = 0.3f,
    private val release: Float = 0.05f
) {
    var currentGain: Float = 1.0f
        private set

    fun applyGainCompensation(monoBuffer: ByteArray) {
        if (monoBuffer.isEmpty()) return
        
        val maxSampleValue = 32768f
        var peak = 0
        
        // Find peak sample
        for (i in 0 until (monoBuffer.size / 2)) {
            val low = monoBuffer[i * 2].toInt() and 0xFF
            val high = monoBuffer[i * 2 + 1].toInt() shl 8
            val sample = (low or high).toShort()
            val absSample = Math.abs(sample.toInt())
            if (absSample > peak) peak = absSample
        }

        val peakFloat = peak.toFloat() / maxSampleValue
        val targetGain = if (peakFloat > 0.01f) {
            minOf(15.0f, targetPeakRatio / peakFloat) // cap gain at 15x
        } else {
            1.0f
        }

        if (targetGain > currentGain) {
            currentGain += (targetGain - currentGain) * attack
        } else {
            currentGain += (targetGain - currentGain) * release
        }

        // Apply gain and prevent clipping
        for (i in 0 until (monoBuffer.size / 2)) {
            val low = monoBuffer[i * 2].toInt() and 0xFF
            val high = monoBuffer[i * 2 + 1].toInt() shl 8
            val sample = (low or high).toShort()
            var newSample = (sample * currentGain).toInt()
            
            if (newSample > Short.MAX_VALUE) newSample = Short.MAX_VALUE.toInt()
            if (newSample < Short.MIN_VALUE) newSample = Short.MIN_VALUE.toInt()
            
            val newShort = newSample.toShort()
            monoBuffer[i * 2] = (newShort.toInt() and 0xFF).toByte()
            monoBuffer[i * 2 + 1] = ((newShort.toInt() ushr 8) and 0xFF).toByte()
        }
    }
}
