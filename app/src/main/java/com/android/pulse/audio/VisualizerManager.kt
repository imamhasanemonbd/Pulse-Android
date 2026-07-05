package com.android.pulse.audio

import android.media.audiofx.Visualizer
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.random.Random

class VisualizerManager {
    private var visualizer: Visualizer? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    private val _pulsation = MutableStateFlow(1f)
    val pulsation: StateFlow<Float> = _pulsation

    private var useMock = false
    private var mockJob: Job? = null

    fun init(audioSessionId: Int) {
        if (audioSessionId <= 0) return
        Log.d("Visualizer", "Initializing for session: $audioSessionId")
        
        scope.launch {
            delay(500)
            try {
                releaseInternal()
                val v = Visualizer(audioSessionId)
                visualizer = v
                v.captureSize = Visualizer.getCaptureSizeRange()[0]
                
                v.setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                    override fun onWaveFormDataCapture(v: Visualizer?, waveform: ByteArray?, samplingRate: Int) {}

                    override fun onFftDataCapture(v: Visualizer?, fft: ByteArray?, samplingRate: Int) {
                        if (fft == null) return
                        var energy = 0f
                        // Safely iterate through FFT data with bounds checking
                        val maxIdx = (fft.size - 1).coerceAtMost(16)
                        for (i in 2 until maxIdx step 2) {
                            val real = fft[i].toInt()
                            val imag = fft[i + 1].toInt()
                            energy += kotlin.math.sqrt((real * real + imag * imag).toFloat())
                        }
                        
                        if (energy > 50) {
                            _pulsation.value = 1f + (energy / 1200f).coerceIn(0f, 0.15f)
                        } else {
                            _pulsation.value = 1f
                        }
                    }
                }, Visualizer.getMaxCaptureRate() / 2, false, true)
                
                v.enabled = true
                useMock = false
            } catch (e: Exception) {
                Log.e("Visualizer", "Failed to init: ${e.message}")
                useMock = true
                startMockPulsation()
            }
        }
    }

    private fun startMockPulsation() {
        mockJob?.cancel()
        mockJob = scope.launch {
            while (useMock && isActive) {
                _pulsation.value = 1f + Random.nextFloat() * 0.05f
                delay(100)
                _pulsation.value = 1f
                delay(100)
            }
        }
    }

    fun release() {
        useMock = false
        releaseInternal()
        scope.cancel()
    }

    private fun releaseInternal() {
        visualizer?.let {
            try {
                it.enabled = false
                it.release()
            } catch (e: Exception) {
                Log.e("Visualizer", "Error releasing: ${e.message}")
            }
        }
        visualizer = null
        _pulsation.value = 1f
    }
}
