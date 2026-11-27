package com.orvex.demucstest

/** JNI bridge matching jPaauwe/demucs-android android/kotlin/NativeEngine.kt. */
object NativeEngine {
    fun interface ProgressListener { fun onProgress(fraction: Float, stage: String): Boolean }
    init { System.loadLibrary("sdengine_jni") }
    val SOURCES = listOf("drums", "bass", "other", "vocals", "guitar", "piano")
    @JvmStatic private external fun nativeLoadModel(path: String): String
    @JvmStatic private external fun nativePrepareInput(raw: String, sampleRate: Int, channels: Int, out: String, listener: ProgressListener?): String
    @JvmStatic private external fun nativeAnalyze(path: String): DoubleArray
    @JvmStatic private external fun nativeSeparate(prepared: String, outDir: String, write: BooleanArray, threads: Int, computeThreads: Int, gpu: Boolean, listener: ProgressListener?): DoubleArray
    @JvmStatic private external fun nativeComputeInfo(computeThreads: Int, gpu: Boolean): String
    @JvmStatic private external fun nativeCancel()
    @Synchronized fun loadModel(path: String) { val err = nativeLoadModel(path); if (err.isNotEmpty()) throw IllegalStateException(err) }
    fun prepareInput(raw: String, sampleRate: Int, channels: Int, out: String, listener: ProgressListener? = null) {
        val err = nativePrepareInput(raw, sampleRate, channels, out, listener); if (err.isNotEmpty()) throw IllegalStateException(err)
    }
    data class InputStats(val durationSec: Double, val peakDb: Double?, val lufs: Double?)
    fun analyze(prepared: String): InputStats { val v = nativeAnalyze(prepared); return InputStats(v[0], v[1].takeUnless { it.isNaN() }, v[2].takeUnless { it.isNaN() }) }
    fun separate(prepared: String, outDir: String, selected: Set<String> = setOf("vocals"), threads: Int = 1, computeThreads: Int = 0, gpu: Boolean = false, listener: ProgressListener? = null): DoubleArray {
        val write = BooleanArray(SOURCES.size) { SOURCES[it] in selected }
        return nativeSeparate(prepared, outDir, write, threads, computeThreads, gpu, listener)
    }
    fun computeInfo(gpu: Boolean = false, computeThreads: Int = 0): String = nativeComputeInfo(computeThreads, gpu)
    fun cancel() = nativeCancel()
}
