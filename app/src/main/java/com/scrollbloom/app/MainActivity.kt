package com.scrollbloom.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            ScrollBloomTheme {
                ScrollBloomApp()
            }
        }
    }
}

/* =========================================================
   MAIN APP
========================================================= */

@Composable
fun ScrollBloomApp() {

    val context = LocalContext.current
    val repository = remember { ScrollBloomRepository.get(context) }

    var snapshot by remember {
        androidx.compose.runtime.mutableStateOf(repository.snapshot())
    }

    // Low-frequency dashboard refresh. Detection itself is event-driven.
    LaunchedEffect(Unit) {
        while (true) {
            snapshot = repository.snapshot()
            delay(1000)
        }
    }

    val health = snapshot.health
    val state = plantStateFromHealth(health, snapshot.recoveryActive)
    val message = plantMessage(snapshot)

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFFF7FBF6)
    ) {

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 18.dp)
        ) {

            Header()

            Spacer(modifier = Modifier.height(18.dp))

            PlantCard(
                reels = snapshot.dailyCount,
                health = health,
                state = state,
                message = message
            )

            Spacer(modifier = Modifier.height(16.dp))

            StatsRow(snapshot)

            Spacer(modifier = Modifier.height(16.dp))

            GardenPreview(snapshot)
        }
    }
}

/* =========================================================
   HEADER
========================================================= */

@Composable
fun Header() {

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {

        Text(
            text = "Focenta",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF245C35)
        )

        Spacer(
            modifier = Modifier.weight(1f)
        )

        Text(
            text = "🌱",
            fontSize = 30.sp
        )
    }
}

/* =========================================================
   PLANT CARD
========================================================= */

@Composable
fun PlantCard(
    reels: Int,
    health: Int,
    state: PlantState,
    message: String
) {

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 3.dp
        )
    ) {

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            Text(
                text = "Your attention garden",
                fontSize = 15.sp,
                color = Color.Gray
            )

            Spacer(
                modifier = Modifier.height(6.dp)
            )

            Text(
                text = state.label,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = state.bodyColor
            )

            Spacer(
                modifier = Modifier.height(8.dp)
            )

            PlantCharacter(
                health = health,
                state = state,
                modifier = Modifier.size(190.dp)
            )

            Spacer(
                modifier = Modifier.height(8.dp)
            )

            Text(
                text = message,
                fontSize = 15.sp,
                color = Color.DarkGray
            )

            Spacer(
                modifier = Modifier.height(14.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {

                Text(
                    text = "Plant health",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp
                )

                Spacer(
                    modifier = Modifier.width(10.dp)
                )

                LinearProgressIndicator(
                    progress = {
                        health / 100f
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(9.dp)
                        .clip(
                            RoundedCornerShape(20.dp)
                        ),
                    color = state.bodyColor,
                    trackColor = Color(0xFFE5E5E5)
                )

                Spacer(
                    modifier = Modifier.width(10.dp)
                )

                Text(
                    text = "$health%",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }

            Spacer(
                modifier = Modifier.height(10.dp)
            )

            Text(
                text = "$reels reels detected today",
                fontSize = 13.sp,
                color = Color.Gray
            )
        }
    }
}

/* =========================================================
   STATS
========================================================= */

@Composable
fun StatsRow(snapshot: BehaviorSnapshot) {

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        StatCard(
            modifier = Modifier.weight(1f),
            title = "Today",
            value = snapshot.dailyCount.toString(),
            subtitle = "reels"
        )

        StatCard(
            modifier = Modifier.weight(1f),
            title = "Health",
            value = "${snapshot.health}%",
            subtitle = if (snapshot.recoveryActive) "recovering" else "recent load"
        )

        StatCard(
            modifier = Modifier.weight(1f),
            title = "Sessions",
            value = snapshot.sessionCount.toString(),
            subtitle = "today"
        )
    }
}

@Composable
fun StatCard(
    modifier: Modifier,
    title: String,
    value: String,
    subtitle: String
) {

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        )
    ) {

        Column(
            modifier = Modifier.padding(14.dp)
        ) {

            Text(
                text = title,
                fontSize = 12.sp,
                color = Color.Gray
            )

            Spacer(
                modifier = Modifier.height(4.dp)
            )

            Text(
                text = value,
                fontSize = 21.sp,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = subtitle,
                fontSize = 11.sp,
                color = Color.Gray
            )
        }
    }
}

/* =========================================================
   GARDEN
========================================================= */

@Composable
fun GardenPreview(snapshot: BehaviorSnapshot) {
    val breakMinutes = snapshot.longestBreakMs / 60_000L
    val sessionMinutes = snapshot.sessionDurationMs / 60_000L

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFEFF7ED))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Your Garden",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = when {
                    snapshot.recoveryActive -> "🌿 Your plant is recovering from your break."
                    snapshot.sessionActive -> "🌱 You're in an active scrolling session."
                    snapshot.dailyCount == 0 -> "🌱 Your garden is ready for a healthy day."
                    else -> "🌸 Your plant is resting between sessions."
                },
                fontSize = 13.sp,
                color = Color.Gray
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                Text("Sessions ${snapshot.sessionCount}", fontSize = 12.sp, color = Color.DarkGray)
                Text("Longest break ${breakMinutes}m", fontSize = 12.sp, color = Color.DarkGray)
                if (snapshot.sessionActive) {
                    Text("Current ${sessionMinutes}m", fontSize = 12.sp, color = Color.DarkGray)
                }
            }
        }
    }
}

/* =========================================================
   PLANT CHARACTER
========================================================= */

@Composable
fun PlantCharacter(
    health: Int,
    state: PlantState,
    modifier: Modifier = Modifier
) {

    val infiniteTransition =
        rememberInfiniteTransition(
            label = "plant_animation"
        )

    val sway by infiniteTransition.animateFloat(
        initialValue = -2f,
        targetValue = 2f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = tween(
                durationMillis = 1600,
                easing = FastOutSlowInEasing
            ),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
        ),
        label = "plant_sway"
    )

    Canvas(
        modifier = modifier
    ) {

        val centerX = size.width / 2f
        val centerY = size.height / 2f

        val bodyWidth = size.width * 0.52f
        val bodyHeight = size.height * 0.48f

        val bodyLeft =
            centerX - bodyWidth / 2f + sway

        val bodyTop =
            centerY - bodyHeight / 2f + 15f

        val bodyRight =
            centerX + bodyWidth / 2f + sway

        val bodyBottom =
            centerY + bodyHeight / 2f + 15f

        /* Ground */

        drawOval(
            color = Color(0xFFDDEAD9),
            topLeft = Offset(
                centerX - size.width * 0.30f,
                bodyBottom + 18f
            ),
            size = Size(
                size.width * 0.60f,
                24f
            )
        )

        /* Left leaf */

        drawLeaf(
            center = Offset(
                bodyLeft + 2f,
                bodyTop + bodyHeight * 0.38f
            ),
            angle = -25f,
            color = state.leafColor
        )

        /* Right leaf */

        drawLeaf(
            center = Offset(
                bodyRight - 2f,
                bodyTop + bodyHeight * 0.38f
            ),
            angle = 25f,
            color = state.leafColor
        )

        /* Body */

        drawOval(
            color = state.bodyColor,
            topLeft = Offset(
                bodyLeft,
                bodyTop
            ),
            size = Size(
                bodyWidth,
                bodyHeight
            )
        )

        /* Top leaves */

        drawLeaf(
            center = Offset(
                centerX - 18f + sway,
                bodyTop + 2f
            ),
            angle = -25f,
            color = state.leafColor
        )

        drawLeaf(
            center = Offset(
                centerX + 18f + sway,
                bodyTop + 2f
            ),
            angle = 25f,
            color = state.leafColor
        )

        /* Flower */

        if (health >= 30) {

            val flowerX = centerX + sway
            val flowerY = bodyTop - 16f

            drawCircle(
                color = Color(0xFFF6A6C8),
                radius = 13f,
                center = Offset(
                    flowerX - 8f,
                    flowerY
                )
            )

            drawCircle(
                color = Color(0xFFF6A6C8),
                radius = 13f,
                center = Offset(
                    flowerX + 8f,
                    flowerY
                )
            )

            drawCircle(
                color = Color(0xFFFFD166),
                radius = 9f,
                center = Offset(
                    flowerX,
                    flowerY
                )
            )
        }

        /* Eyes */

        val eyeY =
            bodyTop + bodyHeight * 0.38f

        val eyeSpacing = 24f

        drawCircle(
            color = Color(0xFF25352B),
            radius = 5f,
            center = Offset(
                centerX - eyeSpacing + sway,
                eyeY
            )
        )

        drawCircle(
            color = Color(0xFF25352B),
            radius = 5f,
            center = Offset(
                centerX + eyeSpacing + sway,
                eyeY
            )
        )

        /* Eye highlights */

        drawCircle(
            color = Color.White,
            radius = 2f,
            center = Offset(
                centerX - eyeSpacing - 1f + sway,
                eyeY - 2f
            )
        )

        drawCircle(
            color = Color.White,
            radius = 2f,
            center = Offset(
                centerX + eyeSpacing - 1f + sway,
                eyeY - 2f
            )
        )

        /* Mouth */

        val mouthY =
            bodyTop + bodyHeight * 0.57f

        if (health > 65) {

            drawArc(
                color = Color(0xFF25352B),
                startAngle = 20f,
                sweepAngle = 140f,
                useCenter = false,
                topLeft = Offset(
                    centerX - 16f + sway,
                    mouthY - 3f
                ),
                size = Size(
                    32f,
                    22f
                ),
                style = Stroke(
                    width = 3f
                )
            )

        } else if (health > 30) {

            drawLine(
                color = Color(0xFF25352B),
                start = Offset(
                    centerX - 10f + sway,
                    mouthY
                ),
                end = Offset(
                    centerX + 10f + sway,
                    mouthY
                ),
                strokeWidth = 3f
            )

        } else {

            drawArc(
                color = Color(0xFF25352B),
                startAngle = 200f,
                sweepAngle = 140f,
                useCenter = false,
                topLeft = Offset(
                    centerX - 15f + sway,
                    mouthY
                ),
                size = Size(
                    30f,
                    18f
                ),
                style = Stroke(
                    width = 3f
                )
            )
        }

        /* Feet */

        drawOval(
            color = state.leafColor,
            topLeft = Offset(
                centerX - 36f + sway,
                bodyBottom - 2f
            ),
            size = Size(
                28f,
                15f
            )
        )

        drawOval(
            color = state.leafColor,
            topLeft = Offset(
                centerX + 8f + sway,
                bodyBottom - 2f
            ),
            size = Size(
                28f,
                15f
            )
        )
    }
}

/* =========================================================
   LEAF DRAWING
========================================================= */

private fun DrawScope.drawLeaf(
    center: Offset,
    angle: Float,
    color: Color
) {

    val radians =
        Math.toRadians(
            angle.toDouble()
        )

    val directionX =
        cos(radians).toFloat()

    val directionY =
        sin(radians).toFloat()

    val perpendicularX =
        -directionY

    val perpendicularY =
        directionX

    val length = 34f
    val width = 18f

    val tip = Offset(
        center.x + directionX * length,
        center.y + directionY * length
    )

    val control1 = Offset(
        center.x +
                directionX * length * 0.55f +
                perpendicularX * width,

        center.y +
                directionY * length * 0.55f +
                perpendicularY * width
    )

    val control2 = Offset(
        center.x +
                directionX * length * 0.55f -
                perpendicularX * width,

        center.y +
                directionY * length * 0.55f -
                perpendicularY * width
    )

    val path = Path()

    path.moveTo(
        center.x,
        center.y
    )

    path.quadraticTo(
        control1.x,
        control1.y,
        tip.x,
        tip.y
    )

    path.quadraticTo(
        control2.x,
        control2.y,
        center.x,
        center.y
    )

    drawPath(
        path = path,
        color = color
    )
}

/* =========================================================
   BEHAVIOR → PLANT PRESENTATION
========================================================= */

data class PlantState(
    val label: String,
    val bodyColor: Color,
    val leafColor: Color
)

fun plantStateFromHealth(health: Int, recovering: Boolean): PlantState {
    if (recovering && health < 90) {
        return PlantState(
            label = "Recovering 🌿",
            bodyColor = Color(0xFF8FBE70),
            leafColor = Color(0xFF57965A)
        )
    }

    return when {
        health >= 85 -> PlantState("Very Happy 🌱", Color(0xFF86C96B), Color(0xFF4E9F55))
        health >= 70 -> PlantState("Happy 🌿", Color(0xFF7FBD65), Color(0xFF4B934F))
        health >= 50 -> PlantState("Getting Tired 😐", Color(0xFF9EB65E), Color(0xFF718B45))
        health >= 30 -> PlantState("Thirsty 🥱", Color(0xFFB4A65D), Color(0xFF7D7944))
        health >= 15 -> PlantState("Exhausted 😵", Color(0xFF9E9A64), Color(0xFF6D7048))
        else -> PlantState("Very Exhausted 🥀", Color(0xFF858878), Color(0xFF626655))
    }
}

fun plantMessage(snapshot: BehaviorSnapshot): String {
    return when {
        snapshot.recoveryActive && snapshot.recoveryDurationMs >= 15 * 60 * 1000L ->
            "Nice break. Your plant is settling back in. 🌸"
        snapshot.recoveryActive ->
            "Your plant is resting with you. 🌿"
        snapshot.dailyCount == 0 ->
            "You're starting with a clean garden today. 🌱"
        snapshot.health >= 85 ->
            "You're taking care of your attention. 🌱"
        snapshot.health >= 70 ->
            "A little rest can keep your plant happy."
        snapshot.health >= 50 ->
            "You've been scrolling for a while."
        snapshot.health >= 30 ->
            "Your plant could use some rest."
        else ->
            "This has been a heavy scrolling session. Consider a break."
    }
}

/* =========================================================
   THEME
========================================================= */

@Composable
fun ScrollBloomTheme(
    content: @Composable () -> Unit
) {

    MaterialTheme(
        content = content
    )
}