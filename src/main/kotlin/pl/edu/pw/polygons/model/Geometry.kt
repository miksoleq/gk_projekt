package pl.edu.pw.polygons.model

import kotlin.math.hypot

data class Point(val x: Double, val y: Double) {
    /** Moving a point by a vector produces another point. */
    operator fun plus(vector: Vector) = Point(x + vector.x, y + vector.y)

    /** Moving a point in the direction opposite to a vector produces another point. */
    operator fun minus(vector: Vector) = Point(x - vector.x, y - vector.y)

    /** The difference between two positions is a direction and distance. */
    operator fun minus(other: Point) = Vector(x - other.x, y - other.y)

    fun distanceTo(other: Point) = hypot(x - other.x, y - other.y)
}

/** A displacement, direction, or tangent. Unlike [Point], it has no absolute position. */
data class Vector(val x: Double, val y: Double) {
    operator fun plus(other: Vector) = Vector(x + other.x, y + other.y)
    operator fun minus(other: Vector) = Vector(x - other.x, y - other.y)
    operator fun times(scale: Double) = Vector(x * scale, y * scale)

    fun length() = hypot(x, y)
    fun normalized() = this * (1.0 / length())
    fun dot(other: Vector) = x * other.x + y * other.y
    fun perpendicular() = Vector(-y, x)
}
