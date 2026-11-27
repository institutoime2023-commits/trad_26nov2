package com.orvex.demucstest

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Bundle
import android.os.Debug
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest

class MainActivity : ComponentActivity() {
    private lateinit var status: TextView
    private lateinit var downloadButton: Button
    private lateinit var recordButton: Button
    private lateinit var separateButton: Button
    private lateinit var playButton: Button
    private lateinit var gpuInfoButton: Button
    private var recordingFile: File? = null
    private var vocalsFile: File? = null
    private var modelLoaded = false
    private var busy = false
    private var player: MediaPlayer? = null
    private var peakPssMb = 0L
    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startRecording() else setStatus("Permiso de micrófono denegado. No se grabó audio.")
    }
    private val modelUrl = "https://huggingface.co/adefossez/HTDemucs-6s/resolve/3c5ee475be622df764938de97e4281a7b07ffa58/5c90dfd2.safetensors"
    private val modelSha256 = "d2a1745f0744721f6b8ca5bf469b67c651ea5ed1b52998cab033b2158609d411"
    private val sampleRate = 44100
    private val captureSeconds = 10

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(28, 28, 28, 28) }
        val scroll = ScrollView(this)
        val title = TextView(this).apply { text = "PRUEBA AISLADA — DEMUCS C++\nXiaomi Redmi 12C · 4 GB RAM"; textSize = 21f }
        status = TextView(this).apply {
            text = "Esta aplicación es independiente del Traductor V0.6.4.\n\nPrueba inicial conservadora: 10 segundos de micrófono, un hilo y CPU. Solo se guardará la pista VOCALS."
            textSize = 15f; setPadding(0, 22, 0, 22)
        }
        downloadButton = makeButton("1. DESCARGAR MODELO (55 MB)") { downloadModel() }
        recordButton = makeButton("2. GRABAR CANCIÓN (10 SEGUNDOS)") { requestMicAndRecord() }
        separateButton = makeButton("3. SEPARAR VOZ — CPU / 1 HILO") { separateVocals() }
        playButton = makeButton("4. ESCUCHAR VOZ SEPARADA") { playVocals() }
        gpuInfoButton = makeButton("CONSULTAR COMPATIBILIDAD VULKAN") { inspectGpu() }
        listOf(title, status, downloadButton, recordButton, separateButton, playButton, gpuInfoButton).forEach { content.addView(it) }
        scroll.addView(content); setContentView(scroll); updateButtons()
    }
    private fun makeButton(label: String, action: () -> Unit) = Button(this).apply { text = label; textSize = 14f; setOnClickListener { action() } }
    private fun setStatus(message: String) { runOnUiThread { status.text = message } }
    private fun updateButtons() {
        downloadButton.isEnabled = !busy && !modelLoaded
        recordButton.isEnabled = !busy && modelLoaded
        separateButton.isEnabled = !busy && recordingFile?.exists() == true
        playButton.isEnabled = !busy && vocalsFile?.exists() == true
        gpuInfoButton.isEnabled = !busy
    }

    private fun downloadModel() {
        if (busy || modelLoaded) return
        busy = true; updateButtons(); setStatus("Descargando modelo oficial de 55 MB…")
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val target = File(filesDir, "5c90dfd2.safetensors")
                    if (!target.exists() || sha256(target) != modelSha256) {
                        val part = File(filesDir, "model.part")
                        val connection = (URL(modelUrl).openConnection() as HttpURLConnection).apply { connectTimeout = 20000; readTimeout = 60000; instanceFollowRedirects = true }
                        try {
                            connection.connect(); if (connection.responseCode !in 200..299) error("Descarga HTTP ${connection.responseCode}")
                            val total = connection.contentLengthLong
                            BufferedInputStream(connection.inputStream).use { input -> FileOutputStream(part).use { output ->
                                val buffer = ByteArray(64 * 1024); var done = 0L
                                while (true) {
                                    val count = input.read(buffer); if (count < 0) break; if (count == 0) continue
                                    output.write(buffer, 0, count); done += count
                                    val pct = if (total > 0) (done * 100 / total).toInt() else -1
                                    setStatus("Descargando modelo… ${if (pct >= 0) "$pct%" else "${done / 1_000_000} MB"}")
                                }
                            } }
                        } finally { connection.disconnect() }
                        val actual = sha256(part); if (actual != modelSha256) { part.delete(); error("SHA-256 incorrecto. Descarga descartada.") }
                        if (!part.renameTo(target)) { part.copyTo(target, overwrite = true); part.delete() }
                    }
                }
                withContext(Dispatchers.IO) { NativeEngine.loadModel(File(filesDir, "5c90dfd2.safetensors").absolutePath) }
                modelLoaded = true
                setStatus("MODELO CARGADO Y VERIFICADO.\n\nMemoria PSS actual: ${Debug.getPss() / 1024} MB.\n\nColoca el celular a una distancia moderada del parlante y evita saturar el micrófono.")
            } catch (e: Exception) { setStatus("ERROR AL CARGAR MODELO: ${e.message}") }
            finally { busy = false; updateButtons() }
        }
    }

    private fun requestMicAndRecord() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) startRecording()
        else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }
    private fun startRecording() {
        if (busy || !modelLoaded) return
        busy = true; updateButtons(); setStatus("Preparando micrófono…")
        lifecycleScope.launch {
            try {
                recordingFile = withContext(Dispatchers.IO) { captureTenSeconds() }; vocalsFile = null
                setStatus("GRABACIÓN COMPLETA: 10 segundos.\nArchivo capturado: ${(recordingFile!!.length() / 1024)} KB.\n\nYa puedes separar la voz.")
            } catch (e: Exception) { setStatus("ERROR DE MICRÓFONO: ${e.message}") }
            finally { busy = false; updateButtons() }
        }
    }
    private fun captureTenSeconds(): File {
        val minBytes = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        check(minBytes > 0) { "El teléfono no admite captura PCM a 44,1 kHz (código $minBytes)." }
        val recorder = AudioRecord(MediaRecorder.AudioSource.MIC, sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, max(minBytes, sampleRate / 5 * 2))
        check(recorder.state == AudioRecord.STATE_INITIALIZED) { "No se pudo inicializar AudioRecord." }
        val out = File(filesDir, "redmi_capture_f32.raw"); val shorts = ShortArray(2048)
        val bytes = ByteArray(shorts.size * 4); val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val targetSamples = sampleRate * captureSeconds; var written = 0
        try {
            FileOutputStream(out).use { stream ->
                recorder.startRecording()
                while (written < targetSamples) {
                    val count = recorder.read(shorts, 0, minOf(shorts.size, targetSamples - written), AudioRecord.READ_BLOCKING)
                    check(count > 0) { "Error leyendo micrófono: $count" }
                    bb.clear(); for (i in 0 until count) bb.putFloat(shorts[i] / 32768.0f)
                    stream.write(bytes, 0, count * 4); written += count
                    setStatus("Grabando canción… ${written * 100 / targetSamples}%")
                }
                recorder.stop()
            }
        } finally { recorder.release() }
        return out
    }

    private fun separateVocals() {
        val raw = recordingFile ?: return
        if (busy || !modelLoaded) return
        busy = true; updateButtons(); peakPssMb = Debug.getPss() / 1024L
        setStatus("Preparando audio para Demucs…\nConfiguración segura: 1 hilo de segmento, CPU, solo salida VOCALS.")
        lifecycleScope.launch {
            try {
                val start = System.nanoTime(); val prepared = File(filesDir, "prepared_44100_stereo.f32")
                val output = File(filesDir, "demucs_output").apply { mkdirs() }
                withContext(Dispatchers.IO) {
                    NativeEngine.prepareInput(raw.absolutePath, sampleRate, 1, prepared.absolutePath) { fraction, stage ->
                        peakPssMb = maxOf(peakPssMb, Debug.getPss() / 1024L)
                        setStatus("Preparando audio: ${(fraction * 100).toInt()}% · $stage · RAM PSS máx. observada ${peakPssMb} MB"); true
                    }
                    val stats = NativeEngine.analyze(prepared.absolutePath)
                    setStatus("Separando voz cantada…\nDuración de muestra: ${"%.2f".format(stats.durationSec)} s\nNo cierres la aplicación.")
                    NativeEngine.separate(prepared.absolutePath, output.absolutePath, selected = setOf("vocals"), threads = 1, computeThreads = 4, gpu = false) { fraction, stage ->
                        peakPssMb = maxOf(peakPssMb, Debug.getPss() / 1024L)
                        setStatus("Demucs: ${(fraction * 100).toInt()}% · $stage\nRAM PSS máx. observada: ${peakPssMb} MB"); true
                    }
                }
                val elapsed = (System.nanoTime() - start) / 1_000_000_000.0
                val vocal = File(output, "vocals.wav"); check(vocal.exists() && vocal.length() > 44) { "Demucs no generó vocals.wav" }
                vocalsFile = vocal
                setStatus("SEPARACIÓN TERMINADA.\n\nAudio capturado: $captureSeconds s\nTiempo total (preparación + separación): ${"%.2f".format(elapsed)} s\nRelación procesamiento/audio: ${"%.2f".format(elapsed / captureSeconds)}x\nPSS máxima observada durante callbacks: ${peakPssMb} MB\nArchivo vocal: ${vocal.length() / 1024} KB\n\nPulsa ESCUCHAR VOZ SEPARADA para evaluar la calidad.")
            } catch (e: OutOfMemoryError) { setStatus("MEMORIA INSUFICIENTE: Android agotó memoria durante Demucs. No se integrará en el traductor con esta configuración.") }
            catch (e: Exception) { setStatus("ERROR DE SEPARACIÓN: ${e.message}") }
            finally { busy = false; updateButtons() }
        }
    }
    private fun playVocals() {
        val file = vocalsFile ?: return
        try { player?.release(); player = MediaPlayer.create(this, Uri.fromFile(file)); checkNotNull(player) { "No se pudo abrir vocals.wav" }.start() }
        catch (e: Exception) { setStatus("No se pudo reproducir la voz: ${e.message}") }
    }
    private fun inspectGpu() {
        if (busy) return
        busy = true; updateButtons(); setStatus("Consultando compatibilidad Vulkan…")
        lifecycleScope.launch {
            try {
                val info = withContext(Dispatchers.IO) { NativeEngine.computeInfo(gpu = true, computeThreads = 4) }
                setStatus("INFORMACIÓN DE CÓMPUTO:\n$info\n\nEsta consulta no ejecuta separación con GPU. La primera prueba permanece en CPU para reducir riesgos en los 4 GB de RAM.")
            } catch (e: Exception) { setStatus("No se pudo consultar Vulkan: ${e.message}") }
            finally { busy = false; updateButtons() }
        }
    }
    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input -> val buffer = ByteArray(64 * 1024); while (true) { val n = input.read(buffer); if (n < 0) break; digest.update(buffer, 0, n) } }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
    override fun onDestroy() { try { player?.release() } catch (_: Exception) {}; super.onDestroy() }
}
