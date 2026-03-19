package com.example.ttsdemo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val viewModel: TtsDemoViewModel = viewModel()
                    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                    TtsDemoScreen(
                        uiState = uiState,
                        onEvent = viewModel::onEvent
                    )
                }
            }
        }
    }
}

@Composable
private fun TtsDemoScreen(
    uiState: TtsDemoUiState,
    onEvent: (TtsDemoUiEvent) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Pocket TTS Demo",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "ONNX INT8 Voice Cloning",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))

        when (uiState) {
            is TtsDemoUiState.Initial -> {
                CircularProgressIndicator()
                Text("Initializing...")
            }
            is TtsDemoUiState.Loading -> {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(8.dp))
                Text(uiState.status, style = MaterialTheme.typography.bodyMedium)
            }
            is TtsDemoUiState.Error -> {
                Text(
                    text = "Error: ${uiState.message}",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
            is TtsDemoUiState.Ready -> {
                ReadyContent(uiState = uiState, onEvent = onEvent)
            }
        }
    }
}

@Composable
private fun ReadyContent(
    uiState: TtsDemoUiState.Ready,
    onEvent: (TtsDemoUiEvent) -> Unit
) {
    // Text input
    OutlinedTextField(
        value = uiState.text,
        onValueChange = { onEvent(TtsDemoUiEvent.TextChanged(it)) },
        label = { Text("Text to synthesize") },
        modifier = Modifier.fillMaxWidth(),
        minLines = 3,
        maxLines = 5,
        enabled = !uiState.isGenerating
    )

    Spacer(modifier = Modifier.height(12.dp))

    // Parameters
    ParameterControls(onEvent = onEvent, enabled = !uiState.isGenerating)

    Spacer(modifier = Modifier.height(12.dp))

    // Action buttons
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Button(
            onClick = { onEvent(TtsDemoUiEvent.Generate) },
            enabled = !uiState.isGenerating && uiState.text.isNotBlank(),
            modifier = Modifier.weight(1f)
        ) {
            Text(if (uiState.isGenerating) "Generating..." else "Generate")
        }

        Button(
            onClick = {
                if (uiState.isPlaying) onEvent(TtsDemoUiEvent.StopAudio)
                else onEvent(TtsDemoUiEvent.PlayAudio)
            },
            enabled = uiState.hasAudio && !uiState.isGenerating,
            modifier = Modifier.weight(1f)
        ) {
            Text(if (uiState.isPlaying) "Stop" else "Play")
        }
    }

    if (uiState.isGenerating) {
        Spacer(modifier = Modifier.height(8.dp))
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
    }

    Spacer(modifier = Modifier.height(12.dp))

    // Performance metrics
    uiState.metrics?.let { metrics ->
        MetricsCard(metrics = metrics)
        Spacer(modifier = Modifier.height(8.dp))
    }

    // Memory info
    MemoryCard(systemMemMb = uiState.systemMemoryMb, appMemMb = uiState.appMemoryMb)

    Spacer(modifier = Modifier.height(8.dp))

    // Generation log
    if (uiState.generationLog.isNotEmpty()) {
        LogCard(log = uiState.generationLog)
    }
}

@Composable
private fun ParameterControls(
    onEvent: (TtsDemoUiEvent) -> Unit,
    enabled: Boolean
) {
    var lsdSteps by remember { mutableIntStateOf(PocketTtsEngine.DEFAULT_LSD_STEPS) }
    var temperature by remember { mutableFloatStateOf(PocketTtsEngine.DEFAULT_TEMPERATURE) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("Parameters", fontWeight = FontWeight.Bold, fontSize = 14.sp)

            Spacer(modifier = Modifier.height(4.dp))

            Text("LSD Steps: $lsdSteps", fontSize = 12.sp)
            Slider(
                value = lsdSteps.toFloat(),
                onValueChange = {
                    lsdSteps = it.toInt()
                    onEvent(TtsDemoUiEvent.LsdStepsChanged(lsdSteps))
                },
                valueRange = 1f..20f,
                steps = 18,
                enabled = enabled
            )

            Text("Temperature: ${String.format("%.2f", temperature)}", fontSize = 12.sp)
            Slider(
                value = temperature,
                onValueChange = {
                    temperature = it
                    onEvent(TtsDemoUiEvent.TemperatureChanged(temperature))
                },
                valueRange = 0f..1.5f,
                enabled = enabled
            )
        }
    }
}

@Composable
private fun MetricsCard(metrics: PocketTtsEngine.PerformanceMetrics) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("Performance Metrics", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Spacer(modifier = Modifier.height(4.dp))

            MetricRow("Voice encode", "${metrics.voiceEncodeTimeMs}ms")
            MetricRow("Text condition", "${metrics.textConditionTimeMs}ms")
            MetricRow("Generation", "${metrics.generationTimeMs}ms")
            MetricRow("Total time", "${metrics.totalTimeMs}ms")
            MetricRow("Frames", "${metrics.framesGenerated}")
            MetricRow("Audio duration", "${String.format("%.2f", metrics.audioDurationSec)}s")
            MetricRow("Realtime factor", "${String.format("%.2f", metrics.realtimeFactor)}x")
            MetricRow("Peak memory", "${String.format("%.1f", metrics.peakMemoryMb)} MB")
        }
    }
}

@Composable
private fun MemoryCard(systemMemMb: Float, appMemMb: Float) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("Memory", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            MetricRow("System available", "${String.format("%.0f", systemMemMb)} MB")
            MetricRow("App heap used", "${String.format("%.1f", appMemMb)} MB")
        }
    }
}

@Composable
private fun MetricRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
        Text(value, fontSize = 12.sp, fontWeight = FontWeight.Medium, fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun LogCard(log: List<String>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("Log", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Spacer(modifier = Modifier.height(4.dp))
            for (entry in log) {
                Text(
                    text = entry,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 14.sp
                )
            }
        }
    }
}
