package com.grp8.freelance.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch

data class OnboardingSlide(
    val title: String,
    val description: String,
    val theme: SlideTheme
)

enum class SlideTheme {
    Welcome, Optimizer, Schedule, Todo, Income
}

val onboardingSlides = listOf(
    OnboardingSlide(
        title = "Welcome to PlanProfit",
        description = "Your all-in-one platform for managing freelance projects, schedules, and income effortlessly.",
        theme = SlideTheme.Welcome
    ),
    OnboardingSlide(
        title = "Maximize Performance",
        description = "Add freelance projects, compare income vs effort, and prioritize opportunities using the Optimizer.",
        theme = SlideTheme.Optimizer
    ),
    OnboardingSlide(
        title = "Smart Timelines",
        description = "Generate schedules, manage your time, and plan your work efficiently with intelligent agenda blocking.",
        theme = SlideTheme.Schedule
    ),
    OnboardingSlide(
        title = "Stay on Track",
        description = "Track your tasks, mark work as complete, and monitor your overall progress seamlessly.",
        theme = SlideTheme.Todo
    ),
    OnboardingSlide(
        title = "Track Your Growth",
        description = "View earnings, track completed project revenue, and monitor your performance in one central dashboard.",
        theme = SlideTheme.Income
    )
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppOnboardingDialog(
    onDismiss: () -> Unit,
    onGetStarted: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            val pagerState = rememberPagerState(pageCount = { onboardingSlides.size })
            val coroutineScope = rememberCoroutineScope()

            Column(modifier = Modifier.fillMaxSize()) {
                // Skip Button Top Right
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onGetStarted) {
                        Text("Skip", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) { page ->
                    val slide = onboardingSlides[page]
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        ProceduralVisualCanvas(
                            theme = slide.theme,
                            modifier = Modifier
                                .size(240.dp)
                                .padding(16.dp)
                        )
                        Spacer(modifier = Modifier.height(48.dp))
                        Text(
                            text = slide.title,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = slide.description,
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Bottom Controls Section
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Previous Button
                    if (pagerState.currentPage > 0) {
                        TextButton(onClick = {
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(pagerState.currentPage - 1)
                            }
                        }) {
                            Text("Previous")
                        }
                    } else {
                        // Spacer to keep layout balanced
                        Spacer(modifier = Modifier.width(88.dp))
                    }

                    // Interactive Indicators
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        repeat(onboardingSlides.size) { index ->
                            val isSelected = pagerState.currentPage == index
                            Box(
                                modifier = Modifier
                                    .padding(4.dp)
                                    .size(if (isSelected) 10.dp else 8.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                    )
                            )
                        }
                    }

                    // Next / Finish Logic
                    Button(
                        onClick = {
                            if (pagerState.currentPage < onboardingSlides.size - 1) {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                }
                            } else {
                                onGetStarted()
                            }
                        }
                    ) {
                        val isLastPage = pagerState.currentPage == onboardingSlides.size - 1
                        AnimatedContent(
                            targetState = isLastPage,
                            transitionSpec = { fadeIn(animationSpec = tween(220)) togetherWith fadeOut(animationSpec = tween(220)) },
                            label = "buttonTextTransition"
                        ) { lastPageActive ->
                            Text(text = if (lastPageActive) "Finish" else "Next")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ProceduralVisualCanvas(theme: SlideTheme, modifier: Modifier = Modifier) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val tertiaryColor = MaterialTheme.colorScheme.tertiaryContainer
    val onSurfaceVariantColor = MaterialTheme.colorScheme.onSurfaceVariant

    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val center = Offset(width / 2, height / 2)

        when (theme) {
            SlideTheme.Welcome -> {
                // A welcoming radiant sun/starburst shape
                val gradient = Brush.radialGradient(
                    colors = listOf(primaryColor.copy(alpha = 0.8f), primaryColor.copy(alpha = 0.1f)),
                    center = center,
                    radius = width * 0.4f
                )
                drawCircle(brush = gradient, radius = width * 0.4f, center = center)
                drawCircle(color = primaryColor, radius = width * 0.15f, center = center)
                drawCircle(color = secondaryColor, radius = width * 0.2f, center = center, style = Stroke(width = 8f))
            }
            SlideTheme.Optimizer -> {
                // Interlocking gear abstract shapes
                val gradient = Brush.linearGradient(listOf(primaryColor, secondaryColor))
                drawCircle(
                    brush = gradient,
                    radius = width * 0.25f,
                    center = center.copy(x = center.x - width * 0.15f, y = center.y - height * 0.1f),
                    style = Stroke(width = 24f)
                )
                drawCircle(
                    brush = Brush.linearGradient(listOf(secondaryColor, tertiaryColor)),
                    radius = width * 0.18f,
                    center = center.copy(x = center.x + width * 0.2f, y = center.y + height * 0.15f),
                    style = Stroke(width = 18f, pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(20f, 10f)))
                )
                drawCircle(color = primaryColor, radius = 12f, center = center.copy(x = center.x - width * 0.15f, y = center.y - height * 0.1f))
            }
            SlideTheme.Schedule -> {
                // Linear matrix abstract lines
                val gradient = Brush.linearGradient(listOf(secondaryColor, primaryColor))
                val spacing = height / 6
                for (i in 1..5) {
                    val yPos = spacing * i
                    drawLine(
                        brush = gradient,
                        start = Offset(width * 0.1f, yPos),
                        end = Offset(width * 0.9f, yPos),
                        strokeWidth = if (i % 2 == 0) 12f else 6f,
                        cap = StrokeCap.Round
                    )
                    // Add matrix nodes
                    if (i % 2 != 0) {
                        drawCircle(
                            color = tertiaryColor,
                            radius = 8f,
                            center = Offset(width * (0.2f + 0.1f * i), yPos)
                        )
                    }
                }
            }
            SlideTheme.Todo -> {
                // Checkmark / Checklist visual
                val boxSize = width * 0.25f
                val spacing = height * 0.3f
                for (i in 0..2) {
                    val yPos = (height * 0.2f) + (spacing * i)
                    drawRoundRect(
                        color = secondaryColor.copy(alpha = 0.3f),
                        topLeft = Offset(width * 0.2f, yPos),
                        size = Size(boxSize, boxSize),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(8f)
                    )
                    if (i < 2) {
                        // Checkmarks
                        val path = Path().apply {
                            moveTo(width * 0.25f, yPos + boxSize * 0.5f)
                            lineTo(width * 0.3f, yPos + boxSize * 0.8f)
                            lineTo(width * 0.4f, yPos + boxSize * 0.2f)
                        }
                        drawPath(path = path, color = primaryColor, style = Stroke(width = 8f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                    }
                    // Line next to box
                    drawLine(
                        color = secondaryColor,
                        start = Offset(width * 0.55f, yPos + boxSize * 0.5f),
                        end = Offset(width * 0.9f, yPos + boxSize * 0.5f),
                        strokeWidth = 12f,
                        cap = StrokeCap.Round
                    )
                }
            }
            SlideTheme.Income -> {
                // Ascending charts and layered nodes
                val path = Path().apply {
                    moveTo(width * 0.1f, height * 0.8f)
                    lineTo(width * 0.3f, height * 0.6f)
                    lineTo(width * 0.5f, height * 0.7f)
                    lineTo(width * 0.7f, height * 0.4f)
                    lineTo(width * 0.9f, height * 0.2f)
                }
                drawPath(
                    path = path,
                    color = primaryColor,
                    style = Stroke(width = 16f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
                
                val nodes = listOf(
                    Offset(width * 0.1f, height * 0.8f),
                    Offset(width * 0.3f, height * 0.6f),
                    Offset(width * 0.5f, height * 0.7f),
                    Offset(width * 0.7f, height * 0.4f),
                    Offset(width * 0.9f, height * 0.2f)
                )
                
                // Draw chart nodes
                nodes.forEach { point ->
                    drawCircle(color = tertiaryColor, radius = 12f, center = point)
                }
                
                // Draw layered gradient bars underneath nodes
                val barGradient = Brush.verticalGradient(listOf(secondaryColor.copy(alpha = 0.5f), Color.Transparent))
                nodes.forEach { point ->
                    drawRect(
                        brush = barGradient,
                        topLeft = Offset(point.x - 10f, point.y),
                        size = Size(20f, height * 0.9f - point.y)
                    )
                }
                
                // Base structure line
                drawLine(
                    color = onSurfaceVariantColor,
                    start = Offset(width * 0.05f, height * 0.9f),
                    end = Offset(width * 0.95f, height * 0.9f),
                    strokeWidth = 4f
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun AppOnboardingDialogPreview() {
    MaterialTheme {
        var isVisible by rememberSaveable { mutableStateOf(true) }
        if (isVisible) {
            AppOnboardingDialog(
                onDismiss = { isVisible = false },
                onGetStarted = { isVisible = false }
            )
        }
    }
}