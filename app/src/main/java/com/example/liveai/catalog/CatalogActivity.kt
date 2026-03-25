package com.example.liveai.catalog

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.liveai.agent.model.BackgroundTask
import com.example.liveai.agent.model.TaskProgress
import com.example.liveai.agent.model.TaskResult
import com.example.liveai.agent.model.TaskStatus
import com.example.liveai.chat.ChatColors
import com.example.liveai.chat.ChatMessage
import com.example.liveai.chat.ChatTab
import com.example.liveai.chat.MessageBubble
import com.example.liveai.chat.TaskCard
import com.example.liveai.chat.ToolCallBubble
import com.example.liveai.chat.ToolCallDisplay
import com.example.liveai.chat.ToolCallStatus
import com.example.liveai.chat.ToolCallSteps

/**
 * Debug activity for browsing all UI components with every state variant.
 * Accessible from the Developer section in MainActivity.
 */
class CatalogActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ComponentCatalog()
        }
    }
}

private val DarkBg = Color(0xFF1A1A2E)
private val CardBg = Color(0xFF16213E)
private val SectionAccent = Color(0xFF7B61FF)
private val TextPrimary = Color(0xFFE8E8E8)
private val TextSecondary = Color(0xFF8B8FA3)
private val PlaceholderBg = Color(0xFF1E2A45)
private val PlaceholderBorder = Color(0xFF2A3A5C)

@Composable
fun ComponentCatalog() {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { Spacer(modifier = Modifier.height(16.dp)) }

        // Header
        item {
            Column(modifier = Modifier.padding(bottom = 8.dp)) {
                Text(
                    text = "Component Catalog",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Browse every UI component in all states",
                    fontSize = 14.sp,
                    color = TextSecondary
                )
            }
        }

        // --- Existing: Chat Tab ---

        item {
            CatalogSection(title = "Chat Tab", count = 2) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    modifier = Modifier.padding(vertical = 8.dp)
                ) {
                    CatalogVariant("Default") {
                        Box(modifier = Modifier.size(64.dp)) {
                            ChatTab(isExpanded = false)
                        }
                    }
                    CatalogVariant("Expanded") {
                        Box(modifier = Modifier.size(64.dp)) {
                            ChatTab(isExpanded = true)
                        }
                    }
                }
            }
        }

        // --- Existing: Message Bubbles ---

        item {
            CatalogSection(title = "Message Bubbles", count = 5) {
                CatalogItem("User message") {
                    MessageBubble(
                        message = ChatMessage(
                            text = "What's on my screen?",
                            isUser = true
                        )
                    )
                }
                CatalogItem("Assistant message") {
                    MessageBubble(
                        message = ChatMessage(
                            text = "You're in WhatsApp. You have 2 new messages from Mom and a message in Work Group.",
                            isUser = false
                        )
                    )
                }
                CatalogItem("Streaming") {
                    MessageBubble(
                        message = ChatMessage(
                            text = "You're in WhatsApp. You have",
                            isUser = false,
                            isStreaming = true
                        )
                    )
                }
                CatalogItem("Short") {
                    MessageBubble(
                        message = ChatMessage(text = "Hi", isUser = true)
                    )
                }
                CatalogItem("Long response") {
                    MessageBubble(
                        message = ChatMessage(
                            text = "Your PDF summary is ready. The document is a lease renewal for 123 Oak Street. Monthly rent is \$2,400, a \$200 increase. Term runs April 1 through March 31. 60-day notice required for non-renewal.",
                            isUser = false
                        )
                    )
                }
            }
        }

        // --- Stage 2: Tool Call Bubbles ---

        item {
            CatalogSection(title = "Tool Call Bubbles", count = 3, stage = 2) {
                CatalogItem("In-progress") {
                    ToolCallBubble(
                        display = ToolCallDisplay(
                            toolName = "read_screen",
                            status = ToolCallStatus.InProgress
                        )
                    )
                }
                CatalogItem("Complete with result") {
                    ToolCallBubble(
                        display = ToolCallDisplay(
                            toolName = "read_screen",
                            status = ToolCallStatus.Complete(durationMs = 820),
                            resultPreview = "WhatsApp — Chat list: Mom (2 new), Work Group (1 new), Dad"
                        )
                    )
                }
                CatalogItem("Error") {
                    ToolCallBubble(
                        display = ToolCallDisplay(
                            toolName = "read_screen",
                            status = ToolCallStatus.Error("Accessibility service not enabled")
                        )
                    )
                }
            }
        }

        // --- Stage 2: Tool Call Steps ---

        item {
            CatalogSection(title = "Tool Call Steps", count = 1, stage = 2) {
                CatalogItem("Multi-step tool sequence") {
                    ToolCallSteps(
                        steps = listOf(
                            ToolCallDisplay(
                                toolName = "read_screen",
                                status = ToolCallStatus.Complete(durationMs = 820),
                                resultPreview = "WhatsApp — Chat list"
                            ),
                            ToolCallDisplay(
                                toolName = "tap",
                                status = ToolCallStatus.Complete(durationMs = 150),
                                resultPreview = "Tapped 'Mom'"
                            ),
                            ToolCallDisplay(
                                toolName = "read_screen",
                                status = ToolCallStatus.InProgress
                            )
                        )
                    )
                }
            }
        }

        // --- Stage 3: Task Cards ---

        item {
            CatalogSection(title = "Task Cards", count = 6, stage = 3) {
                CatalogItem("Queued") {
                    TaskCard(task = fakeTask(TaskStatus.QUEUED), onCancel = {})
                }
                CatalogItem("Running — 60%") {
                    TaskCard(
                        task = fakeTask(
                            TaskStatus.RUNNING,
                            progress = TaskProgress("summarizing", 0.6f, "page 7 of 12")
                        ),
                        onPause = {},
                        onCancel = {}
                    )
                }
                CatalogItem("Running — indeterminate") {
                    TaskCard(
                        task = fakeTask(
                            TaskStatus.RUNNING,
                            progress = TaskProgress("downloading", null, null)
                        ),
                        onPause = {},
                        onCancel = {}
                    )
                }
                CatalogItem("Suspended") {
                    TaskCard(task = fakeTask(TaskStatus.SUSPENDED), onResume = {}, onCancel = {})
                }
                CatalogItem("Completed") {
                    TaskCard(
                        task = fakeTask(
                            TaskStatus.COMPLETED,
                            result = TaskResult.Success(
                                summary = "Lease renewal: \$2,400/mo, April 1 — March 31, 60-day notice required",
                                fullContent = "...",
                                durationMs = 4200
                            )
                        ),
                        onClear = {}
                    )
                }
                CatalogItem("Failed") {
                    TaskCard(
                        task = fakeTask(
                            TaskStatus.FAILED,
                            result = TaskResult.Failure(
                                error = "Network timeout after 3 retries",
                                durationMs = 9500
                            )
                        ),
                        onClear = {}
                    )
                }
                CatalogItem("Cancelled") {
                    TaskCard(task = fakeTask(TaskStatus.CANCELLED), onClear = {})
                }
            }
        }

        // --- Stage 3: Tab Bar ---

        item {
            CatalogSection(title = "Overlay Tab Bar", count = 3, stage = 3) {
                CatalogPlaceholder("Chat selected", "Active tab highlighted")
                CatalogPlaceholder("Tasks selected", "Badge showing count: 2")
                CatalogPlaceholder("Settings selected", "Gear icon")
            }
        }

        // --- Stage 4: Agent Status Bar ---

        item {
            CatalogSection(title = "Agent Status Bar", count = 5, stage = 4) {
                CatalogPlaceholder("Queued", "Waiting...")
                CatalogPlaceholder("Generating", "Thinking... iteration 2 [Cancel]")
                CatalogPlaceholder("Executing", "Using read_screen... [Cancel]")
                CatalogPlaceholder("Cancelled", "Brief flash then hidden")
                CatalogPlaceholder("Error", "Rate limited — [Retry]")
            }
        }

        // --- Stage 5: TTS Indicator ---

        item {
            CatalogSection(title = "TTS Indicator", count = 3, stage = 5) {
                CatalogPlaceholder("Silent", "No indicator shown")
                CatalogPlaceholder("Speaking", "Speaker animation, 0 queued")
                CatalogPlaceholder("Speaking + queue", "Speaker animation, 3 queued")
            }
        }

        // --- Stage 6: Settings ---

        item {
            CatalogSection(title = "Settings Panel", count = 3, stage = 6) {
                CatalogPlaceholder("Provider selector", "Dropdown: OpenAI / Anthropic")
                CatalogPlaceholder("API key field", "Masked input with validation")
                CatalogPlaceholder("Accessibility toggle", "Not enabled [Enable]")
            }
        }

        item { Spacer(modifier = Modifier.height(32.dp)) }
    }
}

@Composable
private fun CatalogSection(
    title: String,
    count: Int,
    stage: Int? = null,
    content: @Composable () -> Unit
) {
    var expanded by rememberSaveable { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = CardBg
    ) {
        Column {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Accent dot
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (stage == null) SectionAccent else SectionAccent.copy(alpha = 0.4f))
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                    Text(
                        text = if (stage != null) "Stage $stage — $count variants" else "$count variants",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                }

                Icon(
                    imageVector = if (expanded) Icons.Rounded.ExpandMore else Icons.Rounded.ChevronRight,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    tint = TextSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }

            // Content
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(
                    modifier = Modifier.padding(
                        start = 16.dp,
                        end = 16.dp,
                        bottom = 16.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    content()
                }
            }
        }
    }
}

@Composable
private fun CatalogItem(
    label: String,
    content: @Composable () -> Unit
) {
    Column {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = SectionAccent,
            letterSpacing = 0.5.sp,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        content()
    }
}

@Composable
private fun CatalogVariant(
    label: String,
    content: @Composable () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        content()
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            color = TextSecondary
        )
    }
}

@Composable
private fun CatalogPlaceholder(
    title: String,
    description: String
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(
                Brush.horizontalGradient(
                    colors = listOf(PlaceholderBg, PlaceholderBg.copy(alpha = 0.7f))
                )
            )
            .padding(12.dp)
    ) {
        Column {
            Text(
                text = title,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = TextPrimary.copy(alpha = 0.6f)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = description,
                fontSize = 11.sp,
                color = TextSecondary.copy(alpha = 0.5f)
            )
        }
    }
}

private fun fakeTask(
    status: TaskStatus,
    progress: TaskProgress = TaskProgress(),
    result: TaskResult? = null
) = BackgroundTask(
    id = "task_${status.name.lowercase()}",
    instructions = when (status) {
        TaskStatus.QUEUED -> "Check pharmacy hours"
        TaskStatus.RUNNING -> "Summarize lease PDF"
        TaskStatus.SUSPENDED -> "Download and analyze report"
        TaskStatus.COMPLETED -> "Summarize lease PDF"
        TaskStatus.FAILED -> "Fetch weather forecast"
        TaskStatus.CANCELLED -> "Search for restaurant menus"
    },
    createdAt = java.lang.System.currentTimeMillis(),
    status = status,
    progress = progress,
    result = result
)
