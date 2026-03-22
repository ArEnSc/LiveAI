package com.example.liveai.interaction

import android.content.Context
import android.graphics.Color
import android.graphics.RectF
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Persists interaction zones as JSON in SharedPreferences.
 * Provides default zones and migration from the old HitZoneConfig format.
 */
object ZoneRepository {

    private const val TAG = "ZoneRepository"
    private const val PREFS_NAME = "interaction_zones_v2"
    private const val KEY_ZONES_JSON = "zones"

    // Old HitZoneConfig prefs (for migration)
    private const val OLD_PREFS_NAME = "interaction_hit_zones"

    // Default zone colors
    private const val HEAD_COLOR = 0xFF4488FF.toInt()
    private const val BODY_COLOR = 0xFF44FF88.toInt()

    private val DEFAULT_ZONE_COLORS = listOf(
        0xFFFF6B6B.toInt(), // red
        0xFFFFD93D.toInt(), // yellow
        0xFFFF8C42.toInt(), // orange
        0xFF6BCB77.toInt(), // green
        0xFF4D96FF.toInt(), // blue
        0xFFC77DFF.toInt(), // purple
        0xFFFF6B9D.toInt(), // pink
        0xFF00D2D3.toInt()  // teal
    )

    fun loadZones(context: Context): List<InteractionZone> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_ZONES_JSON, null)

        if (json != null) {
            return deserializeZones(json)
        }

        // Try migrating from old format
        val migrated = migrateFromOldFormat(context)
        if (migrated.isNotEmpty()) {
            saveZones(context, migrated)
            return migrated
        }

        // First run — return defaults
        val defaults = createDefaultZones()
        saveZones(context, defaults)
        return defaults
    }

    fun saveZones(context: Context, zones: List<InteractionZone>) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_ZONES_JSON, serializeZones(zones))
            .apply()
    }

    fun createDefaultZones(): List<InteractionZone> = listOf(
        InteractionZone(
            id = UUID.randomUUID().toString(),
            name = "Head",
            color = HEAD_COLOR,
            rect = RectF(0.30f, 0.10f, 0.70f, 0.35f),
            bindings = listOf(
                ParameterBinding("ParamAngleX", "Angle X", DragAxis.HORIZONTAL, 1.0f, 30f),
                ParameterBinding("ParamAngleY", "Angle Y", DragAxis.VERTICAL, -1.0f, 30f)
            ),
            spring = SpringConfig(durationMs = 1000L, decay = 8f, frequency = 2.5f, sinMultiplier = 12f)
        ),
        InteractionZone(
            id = UUID.randomUUID().toString(),
            name = "Body",
            color = BODY_COLOR,
            rect = RectF(0.25f, 0.35f, 0.75f, 0.65f),
            bindings = listOf(
                ParameterBinding("ParamBodyAngleX", "Body Angle X", DragAxis.HORIZONTAL, 1.0f, 30f),
                ParameterBinding("ParamBodyAngleY", "Body Angle Y", DragAxis.VERTICAL, -1.0f, 30f),
                ParameterBinding("ParamBodyAngleZ", "Body Angle Z", DragAxis.HORIZONTAL, 0.3f, 15f)
            ),
            spring = SpringConfig(durationMs = 1200L, decay = 10f, frequency = 3f, sinMultiplier = 10f)
        )
    )

    /** Pick a color not yet used by existing zones. */
    fun nextColor(existingZones: List<InteractionZone>): Int {
        val usedColors = existingZones.map { it.color }.toSet()
        return DEFAULT_ZONE_COLORS.firstOrNull { it !in usedColors }
            ?: DEFAULT_ZONE_COLORS.random()
    }

    /** Default spring config for new user-created zones. */
    fun defaultSpring(): SpringConfig =
        SpringConfig(durationMs = 1000L, decay = 8f, frequency = 2.5f, sinMultiplier = 12f)

    // --- Serialization ---

    private fun serializeZones(zones: List<InteractionZone>): String {
        val arr = JSONArray()
        for (zone in zones) {
            arr.put(serializeZone(zone))
        }
        return arr.toString()
    }

    private fun serializeZone(zone: InteractionZone): JSONObject = JSONObject().apply {
        put("id", zone.id)
        put("name", zone.name)
        put("color", zone.color)
        put("rect", JSONObject().apply {
            put("left", zone.rect.left.toDouble())
            put("top", zone.rect.top.toDouble())
            put("right", zone.rect.right.toDouble())
            put("bottom", zone.rect.bottom.toDouble())
        })
        put("bindings", JSONArray().apply {
            for (b in zone.bindings) {
                put(JSONObject().apply {
                    put("paramId", b.paramId)
                    put("displayName", b.displayName)
                    put("axis", b.axis.name)
                    put("strength", b.strength.toDouble())
                    put("maxValue", b.maxValue.toDouble())
                })
            }
        })
        put("sensitivity", zone.sensitivity.toDouble())
        put("spring", JSONObject().apply {
            put("durationMs", zone.spring.durationMs)
            put("decay", zone.spring.decay.toDouble())
            put("frequency", zone.spring.frequency.toDouble())
            put("sinMultiplier", zone.spring.sinMultiplier.toDouble())
        })
    }

    private fun deserializeZones(json: String): List<InteractionZone> {
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { deserializeZone(arr.getJSONObject(it)) }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to deserialize zones, using defaults", e)
            createDefaultZones()
        }
    }

    private fun deserializeZone(obj: JSONObject): InteractionZone {
        val rect = obj.getJSONObject("rect")
        val bindingsArr = obj.getJSONArray("bindings")
        val springObj = obj.getJSONObject("spring")

        return InteractionZone(
            id = obj.getString("id"),
            name = obj.getString("name"),
            color = obj.getInt("color"),
            rect = RectF(
                rect.getDouble("left").toFloat(),
                rect.getDouble("top").toFloat(),
                rect.getDouble("right").toFloat(),
                rect.getDouble("bottom").toFloat()
            ),
            bindings = (0 until bindingsArr.length()).map { i ->
                val b = bindingsArr.getJSONObject(i)
                ParameterBinding(
                    paramId = b.getString("paramId"),
                    displayName = b.getString("displayName"),
                    axis = DragAxis.valueOf(b.getString("axis")),
                    strength = b.getDouble("strength").toFloat(),
                    maxValue = b.getDouble("maxValue").toFloat()
                )
            },
            sensitivity = obj.optDouble("sensitivity", InteractionZone.DEFAULT_SENSITIVITY.toDouble()).toFloat(),
            spring = SpringConfig(
                durationMs = springObj.getLong("durationMs"),
                decay = springObj.getDouble("decay").toFloat(),
                frequency = springObj.getDouble("frequency").toFloat(),
                sinMultiplier = springObj.getDouble("sinMultiplier").toFloat()
            )
        )
    }

    // --- Migration from old HitZoneConfig ---

    private fun migrateFromOldFormat(context: Context): List<InteractionZone> {
        val oldPrefs = context.getSharedPreferences(OLD_PREFS_NAME, Context.MODE_PRIVATE)
        if (oldPrefs.all.isEmpty()) return emptyList()

        Log.d(TAG, "Migrating from old HitZoneConfig format")

        val headRect = RectF(
            oldPrefs.getFloat("head_left", 0.30f),
            oldPrefs.getFloat("head_top", 0.10f),
            oldPrefs.getFloat("head_right", 0.70f),
            oldPrefs.getFloat("head_bottom", 0.35f)
        )
        val bodyRect = RectF(
            oldPrefs.getFloat("body_left", 0.25f),
            oldPrefs.getFloat("body_top", 0.35f),
            oldPrefs.getFloat("body_right", 0.75f),
            oldPrefs.getFloat("body_bottom", 0.65f)
        )

        val defaults = createDefaultZones()
        return listOf(
            defaults[0].copy(rect = headRect),
            defaults[1].copy(rect = bodyRect)
        )
    }
}
