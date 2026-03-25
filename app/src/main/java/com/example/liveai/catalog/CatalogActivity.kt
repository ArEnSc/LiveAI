package com.example.liveai.catalog

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.liveai.chat.ChatColors
import com.example.liveai.chat.ChatMessage
import com.example.liveai.chat.ChatTab
import com.example.liveai.chat.MessageBubble

/**
 * Debug-only activity for browsing all UI components with every state variant.
 * Like Storybook — tap a section to expand, see each component rendered.
 */
class CatalogActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                ComponentCatalog()
            }
        }
    }
}

@Composable
fun ComponentCatalog() {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF5F5F5))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text(
                text = "Component Catalog",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }

        // --- Existing components ---

        item {
            CatalogSection("Chat Tab") {
                CatalogItem("Default") {
                    Box(modifier = Modifier.size(76.dp)) {
                        ChatTab(isExpanded = false)
                    }
                }
                CatalogItem("Expanded") {
                    Box(modifier = Modifier.size(76.dp)) {
                        ChatTab(isExpanded = true)
                    }
                }
            }
        }

        item {
            CatalogSection("Message Bubbles") {
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
                CatalogItem("Assistant streaming") {
                    MessageBubble(
                        message = ChatMessage(
                            text = "You're in WhatsApp. You have 2 new",
                            isUser = false,
                            isStreaming = true
                        )
                    )
                }
                CatalogItem("Short user message") {
                    MessageBubble(
                        message = ChatMessage(text = "Hi", isUser = true)
                    )
                }
                CatalogItem("Long assistant message") {
                    MessageBubble(
                        message = ChatMessage(
                            text = "Your PDF summary is ready. The document is a lease renewal agreement for the apartment at 123 Oak Street. The monthly rent is \$2,400, which is a \$200 increase from the previous lease. The term runs from April 1st through March 31st of next year. There's a 60-day notice requirement for non-renewal.",
                            isUser = false
                        )
                    )
                }
            }
        }

        // --- Stage 2: Tool Call Bubbles (placeholder) ---

        item {
            CatalogSection("Tool Call Bubbles (Stage 2)") {
                CatalogPlaceholder("In-progress — reading screen")
                CatalogPlaceholder("Complete — with result preview")
                CatalogPlaceholder("Error — service not enabled")
            }
        }

        // --- Stage 3: Task Cards (placeholder) ---

        item {
            CatalogSection("Task Cards (Stage 3)") {
                CatalogPlaceholder("QUEUED — waiting in line")
                CatalogPlaceholder("RUNNING — 60% progress bar")
                CatalogPlaceholder("SUSPENDED — paused mid-work")
                CatalogPlaceholder("COMPLETED — with result summary")
                CatalogPlaceholder("FAILED — with error message")
                CatalogPlaceholder("CANCELLED")
            }
        }

        // --- Stage 3: Tab Bar (placeholder) ---

        item {
            CatalogSection("Overlay Tab Bar (Stage 3)") {
                CatalogPlaceholder("Chat tab selected")
                CatalogPlaceholder("Tasks tab selected (with badge count)")
                CatalogPlaceholder("Settings tab selected")
            }
        }

        // --- Stage 4: Agent Status Bar (placeholder) ---

        item {
            CatalogSection("Agent Status Bar (Stage 4)") {
                CatalogPlaceholder("Idle — hidden")
                CatalogPlaceholder("Queued — Waiting...")
                CatalogPlaceholder("Generating — Thinking... (iter 2)")
                CatalogPlaceholder("Executing tools — Using read_screen...")
                CatalogPlaceholder("Cancelled — brief flash")
                CatalogPlaceholder("Error — with retry button")
            }
        }

        // --- Stage 5: TTS Indicator (placeholder) ---

        item {
            CatalogSection("TTS Indicator (Stage 5)") {
                CatalogPlaceholder("Silent")
                CatalogPlaceholder("Speaking (0 queued)")
                CatalogPlaceholder("Speaking (3 queued)")
            }
        }

        // --- Stage 6: Settings Panel (placeholder) ---

        item {
            CatalogSection("Settings Panel (Stage 6)") {
                CatalogPlaceholder("Provider selector dropdown")
                CatalogPlaceholder("API key field (masked)")
                CatalogPlaceholder("Accessibility toggle")
            }
        }

        item { Spacer(modifier = Modifier.height(32.dp)) }
    }
}

@Composable
fun CatalogSection(
    title: String,
    content: @Composable () -> Unit
) {
    var expanded by rememberSaveable { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = Color.White,
        shadowElevation = 2.dp
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (expanded) "v" else ">",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = ChatColors.Purple,
                    modifier = Modifier.width(20.dp)
                )
                Text(
                    text = title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier.padding(
                        start = 16.dp,
                        end = 16.dp,
                        bottom = 16.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    content()
                }
            }
        }
    }
}

@Composable
fun CatalogItem(
    label: String,
    content: @Composable () -> Unit
) {
    Column {
        Text(
            text = label,
            fontSize = 12.sp,
            color = ChatColors.OnSurfaceDim,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFFF9F9F9))
                .padding(8.dp)
        ) {
            content()
        }
    }
}

@Composable
fun CatalogPlaceholder(label: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(ChatColors.SurfaceDim)
            .padding(12.dp)
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            color = ChatColors.OnSurfaceDim,
            fontWeight = FontWeight.Medium
        )
    }
}
