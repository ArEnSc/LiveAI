package com.example.liveai.chat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

class ChatHeadTuningActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val initial = ChatHeadSettings.load(this)
        setContent {
            ChatHeadTuningScreen(
                initial = initial,
                onSave = { physics ->
                    ChatHeadSettings.save(this, physics)
                    finish()
                },
                onReset = {
                    ChatHeadSettings.reset(this)
                },
                onCancel = { finish() }
            )
        }
    }
}

private val BgColor = Color(0xFFF5F5F7)
private val TextPrimary = Color(0xFF1F1F1F)
private val TextSecondary = Color(0xFF5F6368)
private val Accent = Color(0xFF7B61FF)

@Composable
private fun ChatHeadTuningScreen(
    initial: ChatHeadSettings.Physics,
    onSave: (ChatHeadSettings.Physics) -> Unit,
    onReset: () -> Unit,
    onCancel: () -> Unit
) {
    var stiffness by remember { mutableFloatStateOf(initial.springStiffness) }
    var damping by remember { mutableFloatStateOf(initial.springDamping) }
    var friction by remember { mutableFloatStateOf(initial.flingFriction) }
    var snapEnabled by remember { mutableStateOf(initial.snapToEdge) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgColor)
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        Text(
            text = "Chat Head Physics",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Tune the feel of the floating chat ball",
            fontSize = 14.sp,
            color = TextSecondary
        )
        Spacer(modifier = Modifier.height(24.dp))

        // Snap to edge toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Snap to Edge", fontWeight = FontWeight.Medium, color = TextPrimary)
                Text("Ball snaps to nearest screen edge on release", fontSize = 12.sp, color = TextSecondary)
            }
            Switch(
                checked = snapEnabled,
                onCheckedChange = { snapEnabled = it }
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Spring Stiffness
        TuningSlider(
            label = "Spring Stiffness",
            description = "Low = floaty drift, High = snappy",
            value = stiffness,
            range = ChatHeadSettings.STIFFNESS_MIN..ChatHeadSettings.STIFFNESS_MAX,
            format = { "${it.roundToInt()}" },
            onValueChange = { stiffness = it },
            enabled = snapEnabled
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Spring Damping
        TuningSlider(
            label = "Spring Damping",
            description = "Low = more bounce, 1.0 = no bounce",
            value = damping,
            range = ChatHeadSettings.DAMPING_MIN..ChatHeadSettings.DAMPING_MAX,
            format = { "%.2f".format(it) },
            onValueChange = { damping = it },
            enabled = snapEnabled
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Fling Friction
        TuningSlider(
            label = "Fling Friction",
            description = "Low = coasts further, High = stops fast",
            value = friction,
            range = ChatHeadSettings.FRICTION_MIN..ChatHeadSettings.FRICTION_MAX,
            format = { "%.1f".format(it) },
            onValueChange = { friction = it },
            enabled = snapEnabled
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Buttons
        Button(
            onClick = {
                onSave(
                    ChatHeadSettings.Physics(
                        springStiffness = stiffness,
                        springDamping = damping,
                        flingFriction = friction,
                        snapToEdge = snapEnabled
                    )
                )
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Save & Close")
        }

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedButton(
            onClick = {
                val defaults = ChatHeadSettings.Physics()
                stiffness = defaults.springStiffness
                damping = defaults.springDamping
                friction = defaults.flingFriction
                snapEnabled = defaults.snapToEdge
                onReset()
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Reset to Defaults")
        }

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedButton(
            onClick = onCancel,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Cancel")
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun TuningSlider(
    label: String,
    description: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    format: (Float) -> String,
    onValueChange: (Float) -> Unit,
    enabled: Boolean = true
) {
    val alpha = if (enabled) 1f else 0.4f

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                fontWeight = FontWeight.Medium,
                color = TextPrimary.copy(alpha = alpha)
            )
            Text(
                text = format(value),
                fontWeight = FontWeight.Medium,
                color = Accent.copy(alpha = alpha),
                fontSize = 14.sp
            )
        }
        Text(
            text = description,
            fontSize = 12.sp,
            color = TextSecondary.copy(alpha = alpha)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            enabled = enabled
        )
    }
}
