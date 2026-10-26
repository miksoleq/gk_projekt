package pl.edu.pw.polygons.interaction

import pl.edu.pw.polygons.model.ConstraintUpdate
import pl.edu.pw.polygons.model.Point
import pl.edu.pw.polygons.model.Scene
import pl.edu.pw.polygons.model.Vector

/** UI-independent targets; the grab offset prevents a jump when clicking beside a point's center. */
sealed interface DragTarget {
    data class Vertex(val id: Long) : DragTarget
    data class Control(val edgeId: Long, val index: Int) : DragTarget
    data class Polygon(val id: Long) : DragTarget

    fun position(scene: Scene): Point? = when (this) {
        is Vertex -> scene.polygons.asSequence().flatMap { it.vertices.asSequence() }.firstOrNull { it.id == id }?.point
        is Control -> scene.edgeGeometry(edgeId)?.let { if (index == 0) it.firstControl else it.secondControl }
        is Polygon -> scene.polygon(id)?.vertices?.firstOrNull()?.point
    }
}

data class DragGesture(val target: DragTarget, val grabOffset: Vector) {
    fun move(scene: Scene, pointer: Point): ConstraintUpdate {
        val current = target.position(scene) ?: return ConstraintUpdate.Rejected("Przeciągany element już nie istnieje.")
        val delta = (pointer - grabOffset) - current
        return when (target) {
            is DragTarget.Vertex -> scene.tryMoveVertex(target.id, delta)
            is DragTarget.Control -> scene.tryMoveControlPoint(target.edgeId, target.index, delta)
            is DragTarget.Polygon -> scene.tryMovePolygon(target.id, delta)
        }
    }

    companion object {
        fun begin(scene: Scene, target: DragTarget, pointer: Point): DragGesture? =
            target.position(scene)?.let { DragGesture(target, pointer - it) }
    }
}
