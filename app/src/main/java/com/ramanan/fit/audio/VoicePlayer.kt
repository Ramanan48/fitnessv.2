package com.ramanan.fit.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.SoundPool
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.ramanan.fit.engine.Cue
import org.json.JSONObject
import java.util.Locale

/**
 * The coach's voice. Fully offline.
 *
 * - Prerecorded female clips live back-to-back in assets/voice.pack; assets/voice.json maps
 *   "<asset>" (and "<asset>@<name>" for names recorded in advance) → [offset, length, durationMs].
 * - Named lines ("Come on {name}, …"): if a clip was recorded for this exact name it is played;
 *   otherwise the phone's built-in text-to-speech says the line, but only with an on-device
 *   voice (never a network voice); if that is unavailable, the name-free clip is played.
 * - One voice at a time: anything new stops what is playing. stop() cuts audio immediately.
 * - Audio focus is held only while speaking (other audio ducks). A transient focus loss
 *   (e.g. a call) stops the voice and reports an interruption.
 */
class VoicePlayer(
    context: Context,
    private val onInterrupted: () -> Unit,
) {
    private val app = context.applicationContext
    private val audioManager = app.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val main = Handler(Looper.getMainLooper())

    private val attrs = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()

    private val pool = SoundPool.Builder().setMaxStreams(1).setAudioAttributes(attrs).build()
    private val soundIds = HashMap<String, Int>()
    private val durationsMs = HashMap<String, Long>()
    private val loaded = HashSet<Int>()
    private var currentStream = 0
    private var holdingFocus = false

    /** Allow the phone's offline TTS voice for names that were not prerecorded. */
    var phoneVoiceForNames: Boolean = true

    var volume: Float = 0.9f
    var muted: Boolean = false
        set(value) { field = value; if (value) stop() }

    /** The user's name, spoken in named lines (first name only, e.g. "Ramanan R" → "Ramanan"). */
    var name: String = ""
        set(value) {
            field = value.trim()
            // Use a prerecorded clip if ANY word of the name (or the whole name) was recorded,
            // ignoring case, spaces, dots and initials.
            val words = field.split(Regex("[\\s.]+")).map(::keyFor).filter { it.isNotEmpty() }
            nameKey = (words + keyFor(field)).firstOrNull { soundIds.containsKey("${Cue.NAME_GO.asset}@$it") }
                ?: words.firstOrNull().orEmpty()
        }
    private var nameKey = ""

    /** Clips that failed to load — reported in the UI instead of silently missing. */
    val missingClips: List<String> get() = Cue.entries.map { it.asset }.filter { it !in soundIds }

    /** True when named lines can be spoken with the user's actual name. */
    val canSayName: Boolean get() = name.isNotEmpty() && (hasBundledName() || (ttsReady && phoneVoiceForNames))

    /** True when the name is spoken in the coach's own (prerecorded) voice. */
    val nameIsPrerecorded: Boolean get() = hasBundledName()

    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                silence()
                holdingFocus = false
                if (change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) onInterrupted()
            }
            else -> Unit
        }
    }

    private val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        .setAudioAttributes(attrs)
        .setOnAudioFocusChangeListener(focusListener, main)
        .build()

    private val releaseFocus = Runnable { abandonFocus() }

    /** Kept open for the player's lifetime: SoundPool reads clips from it at byte offsets. */
    private var packFd: android.content.res.AssetFileDescriptor? = null

    // ---- on-device text-to-speech, only for names that were not recorded in advance ----
    private var tts: TextToSpeech? = null
    private var ttsReady = false

    init {
        pool.setOnLoadCompleteListener { _, sampleId, status -> if (status == 0) loaded += sampleId }
        try {
            val index = JSONObject(app.assets.open("voice.json").bufferedReader().use { it.readText() })
            val afd = app.assets.openFd("voice.pack")
            packFd = afd
            for (key in index.keys()) {
                val e = index.getJSONArray(key)
                soundIds[key] = pool.load(afd.fileDescriptor, afd.startOffset + e.getLong(0), e.getLong(1), 1)
                durationsMs[key] = e.getLong(2)
            }
        } catch (_: Exception) {
            // anything not loaded shows up in missingClips
        }
        initTts()
    }

    private fun initTts() {
        try {
            tts = TextToSpeech(app) { status ->
                val t = tts ?: return@TextToSpeech
                if (status != TextToSpeech.SUCCESS) return@TextToSpeech
                t.setAudioAttributes(attrs)
                // Offline voices only. Prefer English female voices that ship with Google TTS.
                val offline = try {
                    t.voices.orEmpty().filter { v ->
                        !v.isNetworkConnectionRequired && v.locale.language == "en" &&
                            !v.features.orEmpty().contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED)
                    }
                } catch (_: Exception) { emptyList() }
                val preferred = listOf("en-in-x-ahp", "en-us-x-sfg", "en-us-x-tpf", "en-us-x-iog", "en-gb-x-gba")
                val pick = preferred.firstNotNullOfOrNull { id -> offline.firstOrNull { it.name.startsWith(id) } }
                    ?: offline.firstOrNull { it.locale.country == "IN" }
                    ?: offline.firstOrNull { it.locale.country == "US" }
                    ?: offline.firstOrNull()
                if (pick == null) return@TextToSpeech
                t.voice = pick
                t.setSpeechRate(1.0f)
                t.setPitch(1.08f)
                t.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(id: String?) {}
                    override fun onDone(id: String?) { main.post { abandonFocus() } }
                    @Deprecated("deprecated in API") override fun onError(id: String?) { main.post { abandonFocus() } }
                })
                ttsReady = true
            }
        } catch (_: Exception) {
            ttsReady = false
        }
    }

    private fun hasBundledName() = nameKey.isNotEmpty() && soundIds.containsKey("${Cue.NAME_GO.asset}@$nameKey")

    fun play(cue: Cue) {
        silence()
        if (muted || volume <= 0f) return
        if (cue.named && name.isNotEmpty()) {
            val bundled = "${cue.asset}@$nameKey"
            if (soundIds.containsKey(bundled)) { playClip(bundled); return }
            if (ttsReady && phoneVoiceForNames && speak(cue.say(name))) return
        }
        playClip(cue.asset)
    }

    private fun playClip(key: String) {
        val id = soundIds[key] ?: return
        if (id !in loaded) return
        if (!takeFocus()) return
        currentStream = pool.play(id, volume, volume, 1, 0, 1f)
        main.removeCallbacks(releaseFocus)
        main.postDelayed(releaseFocus, (durationsMs[key] ?: 2000L) + 150L)
    }

    private fun speak(text: String): Boolean {
        val t = tts ?: return false
        if (!takeFocus()) return true            // e.g. during a call: stay silent
        main.removeCallbacks(releaseFocus)
        main.postDelayed(releaseFocus, 6_000L)   // safety net if onDone never arrives
        val params = Bundle().apply { putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, volume) }
        return t.speak(text, TextToSpeech.QUEUE_FLUSH, params, "coach") == TextToSpeech.SUCCESS
    }

    private fun takeFocus(): Boolean {
        if (!holdingFocus) {
            holdingFocus = audioManager.requestAudioFocus(focusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
        return holdingFocus
    }

    /** Immediately silence any clip or speech and release audio focus. */
    fun stop() {
        silence()
        main.removeCallbacks(releaseFocus)
        abandonFocus()
    }

    private fun silence() {
        if (currentStream != 0) {
            pool.stop(currentStream)
            currentStream = 0
        }
        try { tts?.stop() } catch (_: Exception) {}
    }

    private fun abandonFocus() {
        if (holdingFocus) {
            audioManager.abandonAudioFocusRequest(focusRequest)
            holdingFocus = false
        }
    }

    fun release() {
        stop()
        pool.release()
        try { tts?.shutdown() } catch (_: Exception) {}
        try { packFd?.close() } catch (_: Exception) {}
    }

    companion object {
        /** How a name is keyed for prerecorded clips: lowercase letters only ("Ramanan" → "ramanan"). */
        fun keyFor(name: String): String = name.lowercase(Locale.ROOT).filter { it in 'a'..'z' }
    }
}
