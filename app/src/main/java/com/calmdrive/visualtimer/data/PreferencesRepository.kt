package com.calmdrive.visualtimer.data

import android.content.Context
import android.content.SharedPreferences
import com.calmdrive.visualtimer.model.DEFAULT_ROUTINES
import com.calmdrive.visualtimer.model.NumberMode
import com.calmdrive.visualtimer.model.PaletteTheme
import com.calmdrive.visualtimer.model.Routine
import com.calmdrive.visualtimer.model.VisualStyle
import org.json.JSONArray
import org.json.JSONObject

class PreferencesRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("calmdrive_prefs", Context.MODE_PRIVATE)

    var visualStyle: VisualStyle
        get() = VisualStyle.fromId(prefs.getString("visual_style", VisualStyle.DISC.id))
        set(value) = prefs.edit().putString("visual_style", value.id).apply()

    var numberMode: NumberMode
        get() = NumberMode.fromId(prefs.getString("number_mode", NumberMode.ROUNDED.id))
        set(value) = prefs.edit().putString("number_mode", value.id).apply()

    var paletteTheme: PaletteTheme
        get() = PaletteTheme.fromId(prefs.getString("palette_theme", PaletteTheme.TRAFFIC.id))
        set(value) = prefs.edit().putString("palette_theme", value.id).apply()

    var chimesEnabled: Boolean
        get() = prefs.getBoolean("chimes_enabled", true)
        set(value) = prefs.edit().putBoolean("chimes_enabled", value).apply()

    var voiceEnabled: Boolean
        get() = prefs.getBoolean("voice_enabled", true)
        set(value) = prefs.edit().putBoolean("voice_enabled", value).apply()

    var mapsSyncEnabled: Boolean
        get() = prefs.getBoolean("maps_sync_enabled", false)
        set(value) = prefs.edit().putBoolean("maps_sync_enabled", value).apply()

    var activeRoutineId: String
        get() = prefs.getString("active_routine_id", DEFAULT_ROUTINES.first().id)
            ?: DEFAULT_ROUTINES.first().id
        set(value) = prefs.edit().putString("active_routine_id", value).apply()

    fun getRoutines(): List<Routine> {
        val jsonString = prefs.getString("custom_routines_json", null) ?: return DEFAULT_ROUTINES
        return try {
            val array = JSONArray(jsonString)
            val list = mutableListOf<Routine>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    Routine(
                        id = obj.getString("id"),
                        firstTitle = obj.getString("firstTitle"),
                        firstIcon = obj.getString("firstIcon"),
                        firstMins = obj.getInt("firstMins"),
                        thenTitle = obj.optString("thenTitle", null),
                        thenIcon = obj.optString("thenIcon", null),
                        thenMins = obj.optInt("thenMins", 0),
                        destStreet = obj.optString("destStreet", obj.getString("firstTitle"))
                    )
                )
            }
            if (list.isEmpty()) DEFAULT_ROUTINES else list
        } catch (e: Exception) {
            DEFAULT_ROUTINES
        }
    }

    fun saveRoutines(routines: List<Routine>) {
        try {
            val array = JSONArray()
            routines.forEach { r ->
                val obj = JSONObject().apply {
                    put("id", r.id)
                    put("firstTitle", r.firstTitle)
                    put("firstIcon", r.firstIcon)
                    put("firstMins", r.firstMins)
                    put("thenTitle", r.thenTitle)
                    put("thenIcon", r.thenIcon)
                    put("thenMins", r.thenMins)
                    put("destStreet", r.destStreet)
                }
                array.put(obj)
            }
            prefs.edit().putString("custom_routines_json", array.toString()).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
