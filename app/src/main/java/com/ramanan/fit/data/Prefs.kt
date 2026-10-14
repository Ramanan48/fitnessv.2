package com.ramanan.fit.data

import android.content.Context
import com.ramanan.fit.engine.SessionLength
import org.json.JSONArray
import org.json.JSONObject

data class HistoryEntry(
    val finishedAtEpochMs: Long,
    val minutes: Int,
    val exercises: Int,
    val rounds: Int,
)

/** Local-only storage (SharedPreferences). Nothing leaves the device. */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("ramanan_fit", Context.MODE_PRIVATE)

    var sessionLength: SessionLength
        get() = SessionLength.fromName(sp.getString(KEY_LENGTH, null))
        set(v) = sp.edit().putString(KEY_LENGTH, v.name).apply()

    /** 0.0..1.0 */
    var voiceVolume: Float
        get() = sp.getFloat(KEY_VOLUME, 0.9f).coerceIn(0f, 1f)
        set(v) = sp.edit().putFloat(KEY_VOLUME, v.coerceIn(0f, 1f)).apply()

    /** Exercise guide style: cartoon avatar (default) or stick figure. */
    var avatarGuides: Boolean
        get() = sp.getBoolean(KEY_AVATAR, true)
        set(v) = sp.edit().putBoolean(KEY_AVATAR, v).apply()

    /** Show the how-to video walkthrough when Start is pressed. */
    var howtoBeforeWorkout: Boolean
        get() = sp.getBoolean(KEY_HOWTO, true)
        set(v) = sp.edit().putBoolean(KEY_HOWTO, v).apply()

    /** The user's name — asked on first launch, spoken by the coach. Empty until set. */
    var name: String
        get() = sp.getString(KEY_NAME, "") ?: ""
        set(v) = sp.edit().putString(KEY_NAME, v.trim().take(24)).apply()

    /** Use the phone's offline voice for names that are not prerecorded. */
    var phoneVoiceNames: Boolean
        get() = sp.getBoolean(KEY_PHONE_VOICE, true)
        set(v) = sp.edit().putBoolean(KEY_PHONE_VOICE, v).apply()

    var muted: Boolean
        get() = sp.getBoolean(KEY_MUTED, false)
        set(v) = sp.edit().putBoolean(KEY_MUTED, v).apply()

    fun history(): List<HistoryEntry> {
        val raw = sp.getString(KEY_HISTORY, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                HistoryEntry(o.getLong("t"), o.getInt("m"), o.getInt("e"), o.getInt("r"))
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun addHistory(e: HistoryEntry) {
        val list = (listOf(e) + history()).take(MAX_HISTORY)
        val arr = JSONArray()
        list.forEach {
            arr.put(JSONObject().put("t", it.finishedAtEpochMs).put("m", it.minutes).put("e", it.exercises).put("r", it.rounds))
        }
        sp.edit().putString(KEY_HISTORY, arr.toString()).apply()
    }

    private companion object {
        const val KEY_LENGTH = "session_length"
        const val KEY_VOLUME = "voice_volume"
        const val KEY_MUTED = "voice_muted"
        const val KEY_AVATAR = "avatar_guides"
        const val KEY_HOWTO = "howto_before_workout"
        const val KEY_NAME = "user_name"
        const val KEY_PHONE_VOICE = "phone_voice_names"
        const val KEY_HISTORY = "history"
        const val MAX_HISTORY = 50
    }
}
