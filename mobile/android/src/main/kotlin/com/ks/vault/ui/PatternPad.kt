package com.ks.vault.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlin.math.hypot

/**
 * A 3 x 3 pattern pad. Dots are 0 to 8, left to right and top to bottom. A dot passed over between
 * two chosen dots in a straight line is added too, as on Android's own lock screen. [onDone] gets the
 * dots when the finger lifts; the pad clears itself. [error] draws the last pattern in the error colour.
 */
@Composable
fun PatternPad(
    onDone: (List<Int>) -> Unit,
    modifier: Modifier = Modifier,
    description: String,
    enabled: Boolean = true,
) {
    val dots = remember { mutableStateListOf<Int>() }
    var finger by remember { mutableStateOf<Offset?>(null) }
    val line = MaterialTheme.colorScheme.primary
    val dotColor = MaterialTheme.colorScheme.onSurfaceVariant

    Canvas(
        modifier
            .widthIn(max = 360.dp)
            .fillMaxWidth()
            .aspectRatio(1f)
            .semantics { contentDescription = description }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                val cell = size.width / 3f
                fun center(i: Int) = Offset(cell * (i % 3) + cell / 2, cell * (i / 3) + cell / 2)
                fun hit(p: Offset): Int? = (0..8).firstOrNull { i ->
                    val c = center(i)
                    hypot(p.x - c.x, p.y - c.y) < cell * 0.32f
                }
                fun add(i: Int) {
                    if (i in dots) return
                    val last = dots.lastOrNull()
                    if (last != null) {
                        // The dot between two others in a row, column or diagonal.
                        val (r1, c1) = last / 3 to last % 3
                        val (r2, c2) = i / 3 to i % 3
                        if ((r1 + r2) % 2 == 0 && (c1 + c2) % 2 == 0) {
                            val mid = (r1 + r2) / 2 * 3 + (c1 + c2) / 2
                            if (mid != last && mid != i && mid !in dots) dots.add(mid)
                        }
                    }
                    dots.add(i)
                }
                detectDragGestures(
                    onDragStart = { p ->
                        dots.clear()
                        hit(p)?.let(::add)
                        finger = p
                    },
                    onDrag = { change, _ ->
                        finger = change.position
                        hit(change.position)?.let(::add)
                    },
                    onDragEnd = {
                        val result = dots.toList()
                        dots.clear()
                        finger = null
                        if (result.isNotEmpty()) onDone(result)
                    },
                    onDragCancel = {
                        dots.clear()
                        finger = null
                    },
                )
            },
    ) {
        val cell = size.width / 3f
        fun center(i: Int) = Offset(cell * (i % 3) + cell / 2, cell * (i / 3) + cell / 2)
        for (k in 1 until dots.size) {
            drawLine(line, center(dots[k - 1]), center(dots[k]), strokeWidth = 10f, cap = StrokeCap.Round)
        }
        val f = finger
        if (f != null && dots.isNotEmpty()) {
            drawLine(line.copy(alpha = 0.5f), center(dots.last()), f, strokeWidth = 10f, cap = StrokeCap.Round)
        }
        for (i in 0..8) {
            val on = i in dots
            drawCircle(if (on) line else dotColor, radius = if (on) cell * 0.11f else cell * 0.07f, center = center(i))
        }
    }
}
