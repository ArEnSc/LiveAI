package com.example.liveai.gyroscope

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Which tilt axis drives the binding.
 */
enum class GyroAxis {
    TILT_X,  // left/right roll
    TILT_Y   // forward/back pitch
}

/**
 * A single mapping from a gyro axis to a Live2D parameter.
 *
 * @param paramId      Live2D parameter ID (e.g. "ParamAngleX")
 * @param displayName  friendly name from the model's CDI file, or paramId as fallback
 * @param axis         which device tilt axis drives this param
 * @param scale        multiplier in degrees; negative = inverted
 * @param enabled      whether this binding is active
 */
data class GyroBinding(
    val paramId: String,
    val displayName: String,
    val axis: GyroAxis,
    val scale: Float,
    val enabled: Boolean = true
)

/**
 * Full gyroscope motion configuration: global enable + list of bindings.
 */
data class GyroMotionConfig(
    val enabled: Boolean = true,
    val bindings: List<GyroBinding> = defaultBindings()
)

fun defaultBindings(): List<GyroBinding> = listOf(
    GyroBinding("ParamAngleX", "ParamAngleX", GyroAxis.TILT_X, 15f),
    GyroBinding("ParamAngleY", "ParamAngleY", GyroAxis.TILT_Y, -15f),
    GyroBinding("ParamBodyAngleX", "ParamBodyAngleX", GyroAxis.TILT_X, 6f)
)

// --- Persistence ---

private const val PREFS_NAME = "gyro_motion_config"
private const val KEY_ENABLED = "enabled"
private const val KEY_BINDINGS = "bindings"

fun GyroMotionConfig.save(context: Context) {
    val arr = JSONArray()
    for (b in bindings) {
        arr.put(JSONObject().apply {
            put("paramId", b.paramId)
            put("displayName", b.displayName)
            put("axis", b.axis.name)
            put("scale", b.scale.toDouble())
            put("enabled", b.enabled)
        })
    }
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
        .putBoolean(KEY_ENABLED, enabled)
        .putString(KEY_BINDINGS, arr.toString())
        .apply()
}

fun loadGyroMotionConfig(context: Context): GyroMotionConfig {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val enabled = prefs.getBoolean(KEY_ENABLED, true)
    val json = prefs.getString(KEY_BINDINGS, null)
        ?: return GyroMotionConfig(enabled = enabled)

    val bindings = mutableListOf<GyroBinding>()
    val arr = JSONArray(json)
    for (i in 0 until arr.length()) {
        val obj = arr.getJSONObject(i)
        val paramId = obj.getString("paramId")
        bindings.add(
            GyroBinding(
                paramId = paramId,
                displayName = obj.optString("displayName", paramId),
                axis = GyroAxis.valueOf(obj.getString("axis")),
                scale = obj.getDouble("scale").toFloat(),
                enabled = obj.optBoolean("enabled", true)
            )
        )
    }
    return GyroMotionConfig(enabled = enabled, bindings = bindings)
}
