package pl.edu.pw.polygons.drawing

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import pl.edu.pw.polygons.model.EdgeGeometry
import pl.edu.pw.polygons.model.EdgeShape
import pl.edu.pw.polygons.model.Point
import pl.edu.pw.polygons.model.Scene
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.roundToInt

data class Viewport(val scale: Float, val left: Float, val top: Float) {
    fun scenePoint(screen: Offset) = Point(
        ((screen.x - left) / scale).toDouble(),
        ((screen.y - top) / scale).toDouble(),
    )

    fun screenPoint(scene: Point) = Point(scene.x * scale + left, scene.y * scale + top)
}

data class RenderSelection(
    val polygonId: Long?,
    val vertexId: Long?,
    val controlEdgeId: Long?,
    val controlIndex: Int?,
    val edgeIds: List<Long>,
)
fun DrawScope.drawPolygonScene(
    scene: Scene,
    viewport: Viewport,
    selection: RenderSelection,
    lineAlgorithm: LineAlgorithm,
    palette: List<Color>,
    highlight: Color,
) {
    val scale = viewport.scale
    scene.polygons.forEachIndexed { index, polygon ->
        val color = palette[index % palette.size]
        val selected = selection.polygonId == polygon.id
        val edges = polygon.edges.map { edge ->
            val geometry = scene.edgeGeometry(edge.id)!!
            val shape = when (val original = edge.shape) {
                is EdgeShape.CubicBezier -> original.copy(
                    firstControl = viewport.screenPoint(original.firstControl),
                    secondControl = viewport.screenPoint(original.secondControl),
                )
                else -> original
            }
            geometry.copy(start = viewport.screenPoint(geometry.start), end = viewport.screenPoint(geometry.end), shape = shape)
        }
        val samples = edges.associate { edge -> edge.edgeId to if (edge.isBezier) {
            incrementalBezier(edge.start, edge.firstControl, edge.secondControl, edge.end,
                bezierStepCount(edge.start, edge.firstControl, edge.secondControl, edge.end))
        } else emptyList() }
        val fill = Path().apply {
            fillType = PathFillType.NonZero
            moveTo(edges.first().start.x.toFloat(), edges.first().start.y.toFloat())
            edges.forEach { appendEdge(it, samples.getValue(it.edgeId)) }
            close()
        }
        drawPath(fill, color.copy(alpha = if (selected) 0.20f else 0.11f))
        edges.forEach { edge ->
            drawEdge(edge, samples.getValue(edge.edgeId), lineAlgorithm, color, (if (selected) 3.5f else 2.5f) * scale)
        }
        edges.filter { it.isBezier }.forEach { edge ->
            val dash = PathEffect.dashPathEffect(floatArrayOf(6f * scale, 5f * scale))
            val guideColor = Color(0xFF8591A2)
            drawLine(guideColor, edge.start.offset(), edge.firstControl.offset(), 1.5f * scale, pathEffect = dash)
            drawLine(guideColor, edge.firstControl.offset(), edge.secondControl.offset(), 1.5f * scale, pathEffect = dash)
            drawLine(guideColor, edge.secondControl.offset(), edge.end.offset(), 1.5f * scale, pathEffect = dash)
        }
        edges.filter { it.edgeId in selection.edgeIds }.forEach { edge ->
            drawEdge(edge, samples.getValue(edge.edgeId), lineAlgorithm, highlight, 5f * scale)
        }
        polygon.vertices.forEach { vertex ->
            val active = selection.vertexId == vertex.id
            val radius = (if (active) 7.5f else 5.5f) * scale
            val point = viewport.screenPoint(vertex.point).offset()
            drawCircle(if (active) highlight else Color.White, radius, point)
            drawCircle(color, radius, point, style = Stroke(2f * scale))
        }
        edges.filter { it.isBezier }.forEach { edge ->
            listOf(edge.firstControl, edge.secondControl).forEachIndexed { controlIndex, point ->
                val active = selection.controlEdgeId == edge.edgeId && selection.controlIndex == controlIndex
                val radius = (if (active) 6.5f else 5f) * scale
                drawCircle(if (active) highlight else Color.White, radius, point.offset())
                drawCircle(highlight, radius, point.offset(), style = Stroke(2f * scale))
            }
        }
    }
}

private fun DrawScope.drawEdge(edge: EdgeGeometry, samples: List<Point>, algorithm: LineAlgorithm, color: Color, width: Float) {
    when {
        edge.isArc -> {
            val path = Path().apply {
                moveTo(edge.start.x.toFloat(), edge.start.y.toFloat())
                appendEdge(edge, emptyList())
            }
            drawPath(path, color, style = Stroke(width, cap = StrokeCap.Round))
        }
        algorithm == LineAlgorithm.Bresenham -> {
            drawBresenhamPolyline(if (edge.isBezier) samples else listOf(edge.start, edge.end), color, width)
        }
        edge.isBezier -> {
            val path = Path().apply {
                moveTo(edge.start.x.toFloat(), edge.start.y.toFloat())
                samples.drop(1).forEach { lineTo(it.x.toFloat(), it.y.toFloat()) }
            }
            drawPath(path, color, style = Stroke(width, cap = StrokeCap.Round))
        }
        else -> drawLine(color, edge.start.offset(), edge.end.offset(), width, StrokeCap.Round)
    }
}
internal fun DrawScope.drawBresenhamPolyline(points: List<Point>, color: Color, width: Float) {
    val brushWidth = (2 * (width.coerceAtLeast(1f) / 2).toInt() + 1).toFloat()
    val padding = (brushWidth / 2).toDouble()
    val pixels = mutableListOf<Offset>()
    var previous: Pixel? = null
    for (index in 1 until points.size) {
        val segment = clipLine(points[index - 1], points[index], -padding, -padding,
            size.width - 1.0 + padding, size.height - 1.0 + padding) ?: continue
        val start = Pixel(segment.first.x.roundToInt(), segment.first.y.roundToInt())
        val end = Pixel(segment.second.x.roundToInt(), segment.second.y.roundToInt())
        bresenhamLine(start, end) { pixel ->
            if (pixel != previous) pixels += Offset(pixel.x + 0.5f, pixel.y + 0.5f)
            previous = pixel
        }
    }
    drawPoints(pixels, PointMode.Points, color, brushWidth, StrokeCap.Square)
}

private fun Path.appendEdge(edge: EdgeGeometry, samples: List<Point>) {
    when (edge.shape) {
        EdgeShape.Line -> lineTo(edge.end.x.toFloat(), edge.end.y.toFloat())
        is EdgeShape.CubicBezier -> samples.drop(1).forEach { lineTo(it.x.toFloat(), it.y.toFloat()) }
        is EdgeShape.CircularArc -> {
            val arc = edge.arc!!
            val center = arc.center
            val radius = arc.radius
            val startAngle = atan2(edge.start.y - center.y, edge.start.x - center.x)
            arcTo(
                Rect((center.x - radius).toFloat(), (center.y - radius).toFloat(),
                    (center.x + radius).toFloat(), (center.y + radius).toFloat()),
                (startAngle * 180.0 / PI).toFloat(), (arc.sweep * 180.0 / PI).toFloat(), false,
            )
        }
    }
}

private fun Point.offset() = Offset(x.toFloat(), y.toFloat())
