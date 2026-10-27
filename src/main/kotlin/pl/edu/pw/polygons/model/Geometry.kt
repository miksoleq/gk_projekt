package pl.edu.pw.polygons.model

import kotlin.math.hypot

data class Point(val x: Double, val y: Double) {
    operator fun plus(vector: Vector) = Point(x + vector.x, y + vector.y)
    operator fun minus(vector: Vector) = Point(x - vector.x, y - vector.y)
    operator fun minus(other: Point) = Vector(x - other.x, y - other.y)

    fun distanceTo(other: Point) = hypot(x - other.x, y - other.y)
}
data class Vector(val x: Double, val y: Double) {
    operator fun plus(other: Vector) = Vector(x + other.x, y + other.y)
    operator fun minus(other: Vector) = Vector(x - other.x, y - other.y)
    operator fun times(scale: Double) = Vector(x * scale, y * scale)

    fun length() = hypot(x, y)
    fun normalized() = this * (1.0 / length())
    fun dot(other: Vector) = x * other.x + y * other.y
    fun perpendicular() = Vector(-y, x)
}
