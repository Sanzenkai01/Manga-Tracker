package com.sanzenkai.mangatracker.ui.opening

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.sanzenkai.mangatracker.R
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select

@Composable
fun OpeningAnimation(
    skipEntrance: Boolean,
    onStarted: () -> Unit,
    onFinished: () -> Unit,
) {
    val iconAlpha = remember { Animatable(if (skipEntrance) 1f else 0f) }
    val iconScale = remember { Animatable(if (skipEntrance) 1f else 0.85f) }
    val overlayAlpha = remember { Animatable(1f) }
    var skipRequested by remember { mutableStateOf(false) }
    var isExiting by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        onStarted()
        if (!skipEntrance) {
            coroutineScope {
                val entrance = launch {
                    coroutineScope {
                        launch { iconAlpha.animateTo(1f, tween(durationMillis = 300)) }
                        launch {
                            iconScale.animateTo(
                                targetValue = 1f,
                                animationSpec = spring(
                                    dampingRatio = 0.9f,
                                    stiffness = 240f,
                                ),
                            )
                        }
                    }
                    delay(400)
                }
                val skipWatcher = async {
                    snapshotFlow { skipRequested }.first { it }
                }
                select<Unit> {
                    entrance.onJoin {}
                    skipWatcher.onAwait {}
                }
                entrance.cancel()
                skipWatcher.cancel()
            }
        }

        isExiting = true
        overlayAlpha.animateTo(0f, tween(durationMillis = 300))
        onFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = overlayAlpha.value }
            .background(colorResource(R.color.ic_launcher_background))
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val isTap = event.changes.any { it.changedToUpIgnoreConsumed() }
                        event.changes.forEach { it.consume() }
                        if (isTap && !isExiting) skipRequested = true
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_launcher),
            contentDescription = stringResource(R.string.opening_animation_content_description),
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .size(192.dp)
                .graphicsLayer {
                    alpha = iconAlpha.value
                    scaleX = iconScale.value
                    scaleY = iconScale.value
                },
        )
    }
}
