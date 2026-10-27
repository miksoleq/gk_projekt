package pl.edu.pw.polygons.model

import kotlin.math.PI

data class Vertex(val id: Long, val point: Point)

sealed interface EdgeShape {
    data object Line : EdgeShape
    data class CubicBezier(val firstControl: Point, val secondControl: Point) : EdgeShape
    data class CircularArc(val sweep: Double = PI) : EdgeShape {
        init {
            require(sweep.isFinite() && kotlin.math.abs(sweep) > 1e-7 && kotlin.math.abs(sweep) < 2.0 * PI - 1e-7)
        }
    }
}

enum class Continuity { G0, G1, C1 }
data class ContinuitySetting(val continuity: Continuity, val explicit: Boolean)
data class Edge(val id: Long, val shape: EdgeShape = EdgeShape.Line)
