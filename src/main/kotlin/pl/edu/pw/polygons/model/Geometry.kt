package pl.edu.pw.polygons.model

import kotlin.math.hypot
import kotlin.math.PI

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

/** `explicit` distinguishes a user's choice from the default selected for a new curve. */
data class ContinuitySetting(val continuity: Continuity, val explicit: Boolean)

/** An edge owns its ID; its end points are the adjacent vertices in polygon order. */
data class Edge(val id: Long, val shape: EdgeShape = EdgeShape.Line)

sealed interface GeometricConstraint {
    val id: Long
    fun references(edgeId: Long): Boolean
}

data class LengthConstraint(
    override val id: Long,
    val edgeId: Long,
    val desiredLength: Double,
) : GeometricConstraint {
    override fun references(edgeId: Long) = this.edgeId == edgeId
}

data class PerpendicularConstraint(
    override val id: Long,
    val firstEdgeId: Long,
    val secondEdgeId: Long,
) : GeometricConstraint {
    override fun references(edgeId: Long) = firstEdgeId == edgeId || secondEdgeId == edgeId
}

sealed interface ConstraintUpdate {
    data class Applied(val scene: Scene) : ConstraintUpdate
    data class Rejected(val reason: String) : ConstraintUpdate
}

/** Temporary interaction hints; they are not saved as part of a geometric constraint. */
private data class ConstraintEdit(
    val movedVertexIds: Set<Long> = emptySet(),
    val preferredEdgeIds: Set<Long> = emptySet(),
    val draggedControl: Pair<Long, Int>? = null,
)

data class EdgeGeometry(
    val edgeId: Long,
    val startVertexId: Long,
    val endVertexId: Long,
    val start: Point,
    val end: Point,
    val shape: EdgeShape,
) {
    val direction get() = end - start
    val chordLength get() = direction.length()
    val isBezier get() = shape is EdgeShape.CubicBezier
    val isArc get() = shape is EdgeShape.CircularArc
    val arc: CircularArcGeometry?
        get() = (shape as? EdgeShape.CircularArc)?.let { CircularArcGeometry(start, end, it.sweep) }
    val firstControl get() = when (val current = shape) {
        EdgeShape.Line, is EdgeShape.CircularArc -> start + direction * ONE_THIRD
        is EdgeShape.CubicBezier -> current.firstControl
    }
    val secondControl get() = when (val current = shape) {
        EdgeShape.Line, is EdgeShape.CircularArc -> start + direction * TWO_THIRDS
        is EdgeShape.CubicBezier -> current.secondControl
    }
    val startTangent get() = arc?.startTangent ?: (firstControl - start)
    val endTangent get() = arc?.endTangent ?: (end - secondControl)
    val length get() = arc?.length ?: if (isBezier) arcLength() else chordLength

    fun pointAt(t: Double): Point {
        arc?.let { return it.pointAt(t) }
        val u = 1.0 - t
        return start + (firstControl - start) * (3.0 * u * u * t) +
            (secondControl - start) * (3.0 * u * t * t) +
            (end - start) * (t * t * t)
    }

    fun arcLength(steps: Int = 40): Double {
        var result = 0.0
        var previous = start
        for (index in 1..steps) {
            val current = pointAt(index.toDouble() / steps)
            result += previous.distanceTo(current)
            previous = current
        }
        return result
    }
}

data class Polygon(
    val id: Long,
    val name: String,
    val vertices: List<Vertex>,
    val edges: List<Edge>,
) {
    init {
        require(vertices.size >= 3) { "A polygon needs at least three vertices" }
        require(edges.size == vertices.size) { "Each vertex needs one outgoing edge" }
        require(vertices.map { it.id }.distinct().size == vertices.size)
        require(edges.map { it.id }.distinct().size == edges.size)
    }

    fun edgeStart(index: Int) = vertices[index].point
    fun edgeEnd(index: Int) = vertices[(index + 1) % vertices.size].point
}

data class Scene(
    val polygons: List<Polygon>,
    val nextId: Long,
    val constraints: List<GeometricConstraint> = emptyList(),
    val continuity: Map<Long, ContinuitySetting> = emptyMap(),
) {
    init {
        val ids = polygons.flatMap { polygon ->
            listOf(polygon.id) + polygon.vertices.map(Vertex::id) + polygon.edges.map(Edge::id)
        } + constraints.map(GeometricConstraint::id)
        require(ids.distinct().size == ids.size) { "Scene IDs must be unique" }
        require(nextId > (ids.maxOrNull() ?: 0L))
        val edgeIds = polygons.flatMap { it.edges }.map(Edge::id).toSet()
        val vertexIds = polygons.flatMap { it.vertices }.map(Vertex::id).toSet()
        require(continuity.keys.all { it in vertexIds }) { "Continuity must reference existing vertices" }
        require(constraints.all { constraint ->
            when (constraint) {
                is LengthConstraint -> constraint.edgeId in edgeIds && constraint.desiredLength > EPSILON
                is PerpendicularConstraint -> constraint.firstEdgeId in edgeIds && constraint.secondEdgeId in edgeIds
            }
        }) { "Constraints must reference existing edges" }
    }

    fun polygon(id: Long) = polygons.firstOrNull { it.id == id }

    /** A closed polygon; self-intersections are allowed, zero-length sides are not. */
    fun addPolygon(points: List<Point>): ConstraintUpdate {
        if (points.size < 3) return ConstraintUpdate.Rejected("Wielokąt musi mieć co najmniej trzy wierzchołki.")
        if (points.any { !it.x.isFinite() || !it.y.isFinite() }) return ConstraintUpdate.Rejected("Współrzędne muszą być skończone.")
        if (points.indices.any { points[it].distanceTo(points[(it + 1) % points.size]) <= EPSILON }) {
            return ConstraintUpdate.Rejected("Sąsiednie wierzchołki muszą być różne.")
        }
        var next = nextId
        val id = next++
        val vertices = points.map { Vertex(next++, it) }
        val edges = points.map { Edge(next++) }
        return ConstraintUpdate.Applied(copy(
            polygons = polygons + Polygon(id, "Wielokąt ${polygons.size + 1}", vertices, edges), nextId = next,
        ))
    }

    fun edgeGeometry(edgeId: Long): EdgeGeometry? {
        polygons.forEach { polygon ->
            val edgeIndex = polygon.edges.indexOfFirst { it.id == edgeId }
            if (edgeIndex >= 0) {
                return EdgeGeometry(
                    edgeId = edgeId,
                    startVertexId = polygon.vertices[edgeIndex].id,
                    endVertexId = polygon.vertices[(edgeIndex + 1) % polygon.vertices.size].id,
                    start = polygon.edgeStart(edgeIndex),
                    end = polygon.edgeEnd(edgeIndex),
                    shape = polygon.edges[edgeIndex].shape,
                )
            }
        }
        return null
    }

    fun moveVertex(vertexId: Long, delta: Vector): Scene = when (val result = tryMoveVertex(vertexId, delta)) {
        is ConstraintUpdate.Applied -> result.scene
        is ConstraintUpdate.Rejected -> this
    }

    fun tryMoveVertex(vertexId: Long, delta: Vector): ConstraintUpdate = copy(polygons = polygons.map { polygon ->
        polygon.copy(vertices = polygon.vertices.map { vertex ->
            if (vertex.id == vertexId) vertex.copy(point = vertex.point + delta) else vertex
        })
    }).solveConstraints(ConstraintEdit(
        movedVertexIds = setOf(vertexId),
        preferredEdgeIds = incidentEdgeIds(setOf(vertexId)),
    ))

    fun movePolygon(polygonId: Long, delta: Vector): Scene = when (val result = tryMovePolygon(polygonId, delta)) {
        is ConstraintUpdate.Applied -> result.scene
        is ConstraintUpdate.Rejected -> this
    }

    fun tryMovePolygon(polygonId: Long, delta: Vector): ConstraintUpdate {
        val moved = polygon(polygonId)
        return copy(polygons = polygons.map { polygon ->
            if (polygon.id == polygonId) polygon.copy(
                vertices = polygon.vertices.map { vertex -> vertex.copy(point = vertex.point + delta) },
                edges = polygon.edges.map { edge -> edge.translateBy(delta) },
            ) else polygon
        }).solveConstraints(ConstraintEdit(
            movedVertexIds = moved?.vertices?.map(Vertex::id)?.toSet() ?: emptySet(),
            preferredEdgeIds = moved?.edges?.map(Edge::id)?.toSet() ?: emptySet(),
        ))
    }

    fun toggleBezier(edgeId: Long): ConstraintUpdate {
        val geometry = edgeGeometry(edgeId) ?: return ConstraintUpdate.Rejected("Wybrana krawędź już nie istnieje.")
        val updatedShape = when (geometry.shape) {
            EdgeShape.Line, is EdgeShape.CircularArc -> EdgeShape.CubicBezier(geometry.firstControl, geometry.secondControl)
            is EdgeShape.CubicBezier -> EdgeShape.Line
        }
        val updated = replaceEdge(edgeId) { it.copy(shape = updatedShape) }
        val defaults = updated.continuity.toMutableMap()
        listOf(geometry.startVertexId, geometry.endVertexId).forEach { vertexId ->
            if (defaults[vertexId]?.explicit != true) {
                val type = if (updated.hasArcAt(vertexId)) Continuity.G0 else Continuity.C1
                defaults[vertexId] = ContinuitySetting(type, false)
            }
        }
        return updated.copy(continuity = defaults).solveConstraints()
    }

    fun toggleArc(edgeId: Long): ConstraintUpdate {
        val geometry = edgeGeometry(edgeId) ?: return ConstraintUpdate.Rejected("Wybrana krawędź już nie istnieje.")
        if (geometry.chordLength <= EPSILON) return ConstraintUpdate.Rejected("Nie można utworzyć łuku dla zerowej krawędzi.")
        val shape = if (geometry.isArc) EdgeShape.Line else EdgeShape.CircularArc()
        val updated = replaceEdge(edgeId) { it.copy(shape = shape) }
        val defaults = updated.continuity.toMutableMap()
        if (!geometry.isArc) {
            listOf(geometry.startVertexId, geometry.endVertexId).forEach { vertexId ->
                // Arcs support G0/G1. Replace a default or previous C1 with G0.
                if (defaults[vertexId]?.continuity != Continuity.G1) {
                    defaults[vertexId] = ContinuitySetting(Continuity.G0, false)
                }
            }
        }
        return updated.copy(continuity = defaults).solveConstraints()
    }

    fun tryMoveControlPoint(edgeId: Long, controlIndex: Int, delta: Vector): ConstraintUpdate {
        val geometry = edgeGeometry(edgeId) ?: return ConstraintUpdate.Rejected("Wybrana krawędź już nie istnieje.")
        val curve = geometry.shape as? EdgeShape.CubicBezier
            ?: return ConstraintUpdate.Rejected("Ta krawędź nie jest segmentem Béziera.")
        if (controlIndex !in 0..1) return ConstraintUpdate.Rejected("Nieznany punkt kontrolny.")
        val changed = if (controlIndex == 0) curve.copy(firstControl = curve.firstControl + delta)
        else curve.copy(secondControl = curve.secondControl + delta)
        return replaceEdge(edgeId) { it.copy(shape = changed) }
            .propagateDraggedControl(edgeId, controlIndex)
            ?.solveConstraints(ConstraintEdit(preferredEdgeIds = setOf(edgeId), draggedControl = edgeId to controlIndex))
            ?: ConstraintUpdate.Rejected("Nie można zachować ciągłości przy zerowej stycznej.")
    }

    fun setContinuity(vertexId: Long, type: Continuity): ConstraintUpdate {
        if (availableContinuities(vertexId).isEmpty()) return ConstraintUpdate.Rejected("Ten wierzchołek nie sąsiaduje z krzywą.")
        if (hasArcAt(vertexId) && type == Continuity.C1) return ConstraintUpdate.Rejected("Przy łuku dostępne są tylko G0 i G1.")
        return copy(continuity = continuity + (vertexId to ContinuitySetting(type, true))).solveConstraints()
    }

    fun continuityAt(vertexId: Long): Continuity? =
        if (availableContinuities(vertexId).isEmpty()) null else continuity[vertexId]?.continuity ?: Continuity.G0

    fun availableContinuities(vertexId: Long): List<Continuity> = when {
        hasArcAt(vertexId) -> listOf(Continuity.G0, Continuity.G1)
        hasBezierAt(vertexId) -> Continuity.entries
        else -> emptyList()
    }

    fun canSetContinuity(vertexId: Long, type: Continuity): Boolean {
        if (type !in availableContinuities(vertexId)) return false
        return type != Continuity.G1 || polygons.all { polygon ->
            polygon.edges.indices.all { index ->
                if (polygon.edges[index].shape !is EdgeShape.CircularArc) true else {
                    val start = polygon.vertices[index].id
                    val end = polygon.vertices[(index + 1) % polygon.vertices.size].id
                    when (vertexId) {
                        start -> continuityAt(end) != Continuity.G1
                        end -> continuityAt(start) != Continuity.G1
                        else -> true
                    }
                }
            }
        }
    }

    fun addLengthConstraint(edgeId: Long, desiredLength: Double): ConstraintUpdate {
        if (!desiredLength.isFinite() || desiredLength <= EPSILON) return ConstraintUpdate.Rejected("Długość musi być skończoną liczbą większą od zera.")
        if (edgeGeometry(edgeId) == null) return ConstraintUpdate.Rejected("Wybrana krawędź już nie istnieje.")
        val updatedConstraints = constraints.filterNot { it is LengthConstraint && it.edgeId == edgeId } +
            LengthConstraint(nextId, edgeId, desiredLength)
        return copy(constraints = updatedConstraints, nextId = nextId + 1)
            .solveConstraints(ConstraintEdit(preferredEdgeIds = setOf(edgeId)))
    }

    fun addPerpendicularConstraint(firstEdgeId: Long, secondEdgeId: Long): ConstraintUpdate {
        if (firstEdgeId == secondEdgeId) return ConstraintUpdate.Rejected("Wybierz dwie różne krawędzie.")
        if (edgeGeometry(firstEdgeId) == null || edgeGeometry(secondEdgeId) == null) {
            return ConstraintUpdate.Rejected("Jedna z wybranych krawędzi już nie istnieje.")
        }
        val alreadyDefined = constraints.any {
            it is PerpendicularConstraint &&
                setOf(it.firstEdgeId, it.secondEdgeId) == setOf(firstEdgeId, secondEdgeId)
        }
        if (alreadyDefined) return ConstraintUpdate.Rejected("Ta prostopadłość jest już zdefiniowana.")
        return copy(
            constraints = constraints + PerpendicularConstraint(nextId, firstEdgeId, secondEdgeId),
            nextId = nextId + 1,
        ).solveConstraints()
    }

    fun removeConstraint(constraintId: Long): Scene = copy(
        constraints = constraints.filterNot { it.id == constraintId },
    )

    /** Split the selected edge at its geometric midpoint. The old edge keeps its ID. */
    fun insertAtEdgeMidpoint(edgeId: Long): Scene {
        val polygonIndex = polygons.indexOfFirst { polygon -> polygon.edges.any { it.id == edgeId } }
        if (polygonIndex < 0) return this
        val polygon = polygons[polygonIndex]
        val edgeIndex = polygon.edges.indexOfFirst { it.id == edgeId }
        val oldEdge = polygon.edges[edgeIndex]
        val geometry = edgeGeometry(edgeId)!!
        val midpoint = geometry.pointAt(0.5)
        val newVertices = polygon.vertices.toMutableList().apply {
            add(edgeIndex + 1, Vertex(nextId, midpoint))
        }
        val newEdges = polygon.edges.toMutableList().apply {
            if (oldEdge.shape is EdgeShape.CubicBezier) {
                val split = splitBezier(geometry)
                this[edgeIndex] = oldEdge.copy(shape = split.first)
                add(edgeIndex + 1, Edge(nextId + 1, split.second))
            } else if (oldEdge.shape is EdgeShape.CircularArc) {
                val half = EdgeShape.CircularArc(oldEdge.shape.sweep * 0.5)
                this[edgeIndex] = oldEdge.copy(shape = half)
                add(edgeIndex + 1, Edge(nextId + 1, half))
            } else add(edgeIndex + 1, Edge(nextId + 1))
        }
        val newContinuity = when (oldEdge.shape) {
            is EdgeShape.CubicBezier -> continuity + (nextId to ContinuitySetting(Continuity.C1, false))
            is EdgeShape.CircularArc -> {
                // G1 at the new joint preserves both halves when the outer ends are G0.
                // If an outer end already has G1, the new joint must stay G0.
                val type = if (continuityAt(geometry.startVertexId) == Continuity.G1 ||
                    continuityAt(geometry.endVertexId) == Continuity.G1) Continuity.G0 else Continuity.G1
                continuity + (nextId to ContinuitySetting(type, false))
            }
            EdgeShape.Line -> continuity
        }
        val updated = replacePolygon(
            polygonIndex,
            polygon.copy(vertices = newVertices, edges = newEdges),
            nextId + 2,
            constraints.filterNot { it.references(edgeId) },
            newContinuity,
        )
        return when (val result = updated.solveConstraints()) {
            is ConstraintUpdate.Applied -> result.scene
            is ConstraintUpdate.Rejected -> this
        }
    }

    /** Keep the incoming edge ID; the removed vertex's outgoing edge disappears. */
    fun removeVertex(vertexId: Long): Scene {
        val polygonIndex = polygons.indexOfFirst { polygon -> polygon.vertices.any { it.id == vertexId } }
        if (polygonIndex < 0) return this
        val polygon = polygons[polygonIndex]
        if (polygon.vertices.size == 3) return this
        val vertexIndex = polygon.vertices.indexOfFirst { it.id == vertexId }
        val edgeIdsToInvalidate = setOf(
            polygon.edges[vertexIndex].id,
            polygon.edges[(vertexIndex - 1 + polygon.edges.size) % polygon.edges.size].id,
        )
        val newVertices = polygon.vertices.toMutableList().apply { removeAt(vertexIndex) }
        val newEdges = polygon.edges.toMutableList().apply { removeAt(vertexIndex) }
        val updated = replacePolygon(
            polygonIndex,
            polygon.copy(vertices = newVertices, edges = newEdges),
            nextId,
            constraints.filterNot { constraint -> edgeIdsToInvalidate.any(constraint::references) },
            continuity - vertexId,
        )
        return when (val result = updated.solveConstraints()) {
            is ConstraintUpdate.Applied -> result.scene
            is ConstraintUpdate.Rejected -> this
        }
    }

    private fun replacePolygon(
        index: Int,
        polygon: Polygon,
        next: Long,
        updatedConstraints: List<GeometricConstraint> = constraints,
        updatedContinuity: Map<Long, ContinuitySetting> = continuity,
    ) = copy(
        polygons = polygons.toMutableList().apply { this[index] = polygon },
        nextId = next,
        constraints = updatedConstraints,
        continuity = updatedContinuity,
    )

    private fun solveConstraints(edit: ConstraintEdit = ConstraintEdit()): ConstraintUpdate {
        arcContinuityError()?.let { return ConstraintUpdate.Rejected(it) }
        val priorities = perpendicularPriorities(edit.preferredEdgeIds)
        // These positions are hard interaction targets: never silently move the point away from the cursor.
        val vertexTargets = polygons.flatMap { it.vertices }.filter { it.id in edit.movedVertexIds }.associate { it.id to it.point }
        val controlTarget = edit.draggedControl?.let { (edgeId, index) ->
            val edge = edgeGeometry(edgeId) ?: return ConstraintUpdate.Rejected("Brak przeciąganego uchwytu.")
            if (index == 0) edge.firstControl else edge.secondControl
        }
        var solved = this
        repeat(MAX_SOLVER_ITERATIONS) {
            constraints.forEach { constraint ->
                solved = when (constraint) {
                    is LengthConstraint -> solved.enforceLength(constraint, edit, priorities)
                        ?: return ConstraintUpdate.Rejected("Nie można wyznaczyć długości dla zerowej krawędzi.")
                    is PerpendicularConstraint -> solved.enforcePerpendicular(constraint, edit, priorities)
                        ?: return ConstraintUpdate.Rejected("Nie można wyznaczyć prostopadłości dla zerowej krawędzi.")
                }
            }
            solved = solved.enforceArcs() ?: return ConstraintUpdate.Rejected(
                "Nie można wyznaczyć skończonego łuku: końce muszą być różne, a styczna G1 nie może być równoległa do cięciwy.",
            )
            solved = solved.enforceContinuity(edit) ?: return ConstraintUpdate.Rejected("Nie można zachować ciągłości przy zerowej stycznej.")
        }
        val verticesFollow = solved.polygons.flatMap { it.vertices }.all { vertex ->
            vertexTargets[vertex.id]?.let { vertex.point.distanceTo(it) <= CONSTRAINT_TOLERANCE } ?: true
        }
        val controlFollows = edit.draggedControl?.let { (edgeId, index) ->
            val edge = solved.edgeGeometry(edgeId) ?: return@let false
            (if (index == 0) edge.firstControl else edge.secondControl).distanceTo(controlTarget!!) <= CONSTRAINT_TOLERANCE
        } ?: true
        if (!verticesFollow || !controlFollows) return ConstraintUpdate.Rejected(
            "Nie udało się zachować relacji przy wskazanej pozycji kursora. Zmień pozycję lub usuń kolidującą relację.",
        )
        return if (solved.constraintsSatisfied()) {
            ConstraintUpdate.Applied(solved)
        } else {
            ConstraintUpdate.Rejected("Nowe ograniczenie jest sprzeczne z istniejącymi relacjami.")
        }
    }

    private fun enforceLength(constraint: LengthConstraint, edit: ConstraintEdit, priorities: Map<Long, Int>): Scene? {
        val edge = edgeGeometry(constraint.edgeId) ?: return null
        if (edge.length <= EPSILON) return null
        if (kotlin.math.abs(edge.length - constraint.desiredLength) <= CONSTRAINT_TOLERANCE) return this
        // With a dragged handle, scale around that handle. Its own endpoints may move
        // only here, because a fixed arc length explicitly requires a geometric correction.
        if (edge.isBezier && edit.draggedControl?.first == edge.edgeId) {
            val anchor = if (edit.draggedControl.second == 0) edge.firstControl else edge.secondControl
            val scale = constraint.desiredLength / edge.length
            return replaceEdge(edge.edgeId) { current ->
                current.copy(shape = EdgeShape.CubicBezier(
                    anchor + (edge.firstControl - anchor) * scale,
                    anchor + (edge.secondControl - anchor) * scale,
                ))
            }.moveVertexTo(edge.startVertexId, anchor + (edge.start - anchor) * scale)
                .moveVertexTo(edge.endVertexId, anchor + (edge.end - anchor) * scale)
        }
        val keepEnd = keepEndForCorrection(edge, edit, priorities)
        val anchor = if (keepEnd) edge.end else edge.start
        val scale = constraint.desiredLength / edge.length
        val movingVertexId = if (keepEnd) edge.startVertexId else edge.endVertexId
        val movingPoint = if (keepEnd) edge.start else edge.end
        return if (edge.isBezier) {
            replaceEdge(edge.edgeId) { current ->
                val curve = current.shape as EdgeShape.CubicBezier
                current.copy(shape = EdgeShape.CubicBezier(
                    anchor + (curve.firstControl - anchor) * scale,
                    anchor + (curve.secondControl - anchor) * scale,
                ))
            }.moveVertexTo(movingVertexId, anchor + (movingPoint - anchor) * scale)
        } else {
            val direction = edge.direction
            if (direction.length() <= EPSILON) null
            else moveVertexTo(movingVertexId, anchor + (movingPoint - anchor) * scale)
        }
    }

    private fun enforcePerpendicular(constraint: PerpendicularConstraint, edit: ConstraintEdit, priorities: Map<Long, Int>): Scene? {
        val a = edgeGeometry(constraint.firstEdgeId) ?: return null
        val b = edgeGeometry(constraint.secondEdgeId) ?: return null
        // The edge closer to this interaction drives the relation. With no interaction,
        // stable IDs break ties so clicking the pair in reverse order gives the same result.
        val aPriority = priorities[a.edgeId] ?: Int.MAX_VALUE
        val bPriority = priorities[b.edgeId] ?: Int.MAX_VALUE
        val aLeads = aPriority < bPriority || (aPriority == bPriority && a.edgeId < b.edgeId)
        val first = if (aLeads) a else b
        val second = if (aLeads) b else a
        if (first.chordLength <= EPSILON || second.chordLength <= EPSILON) return null
        if (kotlin.math.abs(first.direction.normalized().dot(second.direction.normalized())) <= CONSTRAINT_TOLERANCE) return this
        var perpendicularDirection = first.direction.normalized().perpendicular()
        if (perpendicularDirection.dot(second.direction) < 0.0) perpendicularDirection = perpendicularDirection * -1.0

        // Preserve the reference joint, or an indirect joint with an upstream edge.
        // Otherwise a later correction could move the source of the entire chain.
        val keepEnd = when {
            second.endVertexId in first.vertexIds -> true
            second.startVertexId in first.vertexIds -> false
            else -> keepEndForCorrection(second, edit, priorities)
        }
        return if (keepEnd) {
            moveVertexTo(second.startVertexId, second.end - perpendicularDirection * second.chordLength)
        } else {
            moveVertexTo(second.endVertexId, second.start + perpendicularDirection * second.chordLength)
        }
    }

    private fun incidentEdgeIds(vertexIds: Set<Long>): Set<Long> = buildSet {
        polygons.forEach { polygon ->
            polygon.edges.forEachIndexed { index, edge ->
                if (polygon.vertices[index].id in vertexIds ||
                    polygon.vertices[(index + 1) % polygon.vertices.size].id in vertexIds) add(edge.id)
            }
        }
    }

    /** One driver per connected component prevents competing sources during a joint drag. */
    private fun perpendicularPriorities(preferredEdgeIds: Set<Long>): Map<Long, Int> {
        val neighbors = mutableMapOf<Long, MutableList<Long>>()
        constraints.filterIsInstance<PerpendicularConstraint>().forEach { relation ->
            neighbors.getOrPut(relation.firstEdgeId) { mutableListOf() }.add(relation.secondEdgeId)
            neighbors.getOrPut(relation.secondEdgeId) { mutableListOf() }.add(relation.firstEdgeId)
        }
        val distance = mutableMapOf<Long, Int>()
        preferredEdgeIds.filter { it !in neighbors }.forEach { distance[it] = 0 }
        val visited = mutableSetOf<Long>()
        for (seed in neighbors.keys) {
            if (!visited.add(seed)) continue
            val component = mutableListOf<Long>()
            val queue = ArrayDeque<Long>()
            queue.addLast(seed)
            while (queue.isNotEmpty()) {
                val edgeId = queue.removeFirst()
                component.add(edgeId)
                neighbors[edgeId].orEmpty().forEach { neighbor ->
                    if (visited.add(neighbor)) queue.addLast(neighbor)
                }
            }
            val root = component.filter { it in preferredEdgeIds }.minOrNull() ?: component.min()
            distance[root] = 0
            queue.addLast(root)
            while (queue.isNotEmpty()) {
                val edgeId = queue.removeFirst()
                neighbors[edgeId].orEmpty().forEach { neighbor ->
                    if (neighbor !in distance) {
                        distance[neighbor] = distance.getValue(edgeId) + 1
                        queue.addLast(neighbor)
                    }
                }
            }
        }
        return distance
    }

    private fun keepEndForCorrection(edge: EdgeGeometry, edit: ConstraintEdit, priorities: Map<Long, Int>): Boolean {
        val startMoved = edge.startVertexId in edit.movedVertexIds
        val endMoved = edge.endVertexId in edit.movedVertexIds
        if (startMoved != endMoved) return endMoved

        // Preserve a joint with an upstream edge or a sibling at the same distance.
        // Moving a sibling joint would undo a correction from the same driver.
        val ownPriority = priorities[edge.edgeId] ?: Int.MAX_VALUE
        fun upstreamPriority(vertexId: Long): Int = incidentEdgeIds(setOf(vertexId))
            .filter { it != edge.edgeId }.minOfOrNull { priorities[it] ?: Int.MAX_VALUE } ?: Int.MAX_VALUE
        val startPriority = upstreamPriority(edge.startVertexId)
        val endPriority = upstreamPriority(edge.endVertexId)
        val jointPriority = minOf(startPriority, endPriority)
        if (jointPriority != Int.MAX_VALUE && jointPriority <= ownPriority && startPriority != endPriority) {
            return endPriority < startPriority
        }
        return edge.isArc && continuityAt(edge.endVertexId) == Continuity.G1
    }

    private fun constraintsSatisfied(): Boolean = constraints.all { constraint ->
        when (constraint) {
            is LengthConstraint -> {
                val edge = edgeGeometry(constraint.edgeId) ?: return false
                kotlin.math.abs(edge.length - constraint.desiredLength) <= CONSTRAINT_TOLERANCE
            }
            is PerpendicularConstraint -> {
                val first = edgeGeometry(constraint.firstEdgeId) ?: return false
                val second = edgeGeometry(constraint.secondEdgeId) ?: return false
                first.length > EPSILON && second.length > EPSILON &&
                    kotlin.math.abs(first.direction.normalized().dot(second.direction.normalized())) <= CONSTRAINT_TOLERANCE
            }
        }
    } && continuity.all { (vertexId, setting) -> continuitySatisfied(vertexId, setting.continuity) } && arcsSatisfied()

    private fun arcContinuityError(): String? {
        polygons.forEach { polygon ->
            polygon.edges.forEachIndexed { index, edge ->
                if (edge.shape is EdgeShape.CircularArc) {
                    val start = continuityAt(polygon.vertices[index].id)
                    val end = continuityAt(polygon.vertices[(index + 1) % polygon.vertices.size].id)
                    if (start == Continuity.C1 || end == Continuity.C1) return "Przy łuku dostępne są tylko G0 i G1."
                    if (start == Continuity.G1 && end == Continuity.G1) return "Tylko jeden koniec łuku może mieć ciągłość G1. Najpierw ustaw G0 na drugim końcu."
                }
            }
        }
        return null
    }

    private fun enforceArcs(): Scene? {
        var updated = this
        polygons.forEach { polygon ->
            polygon.edges.forEachIndexed { index, edge ->
                if (edge.shape is EdgeShape.CircularArc) {
                    val geometry = updated.edgeGeometry(edge.id) ?: return null
                    if (geometry.chordLength <= EPSILON) return null
                    val atStart = updated.continuityAt(geometry.startVertexId) == Continuity.G1
                    val atEnd = updated.continuityAt(geometry.endVertexId) == Continuity.G1
                    val sweep = if (atStart || atEnd) {
                        val neighborIndex = if (atStart) (index - 1 + polygon.edges.size) % polygon.edges.size
                            else (index + 1) % polygon.edges.size
                        val neighbor = updated.edgeGeometry(polygon.edges[neighborIndex].id) ?: return null
                        val tangent = if (atStart) neighbor.endTangent else neighbor.startTangent
                        CircularArcGeometry.fromTangent(geometry.start, geometry.end, tangent, atStart)?.sweep ?: return null
                    } else PI
                    updated = updated.replaceEdge(edge.id) { it.copy(shape = EdgeShape.CircularArc(sweep)) }
                }
            }
        }
        return updated
    }

    private fun arcsSatisfied(): Boolean = polygons.all { polygon ->
        polygon.edges.all { edge ->
            val geometry = edgeGeometry(edge.id)!!
            if (!geometry.isArc) true else {
                geometry.chordLength > EPSILON && geometry.length.isFinite() &&
                    (continuityAt(geometry.startVertexId) == Continuity.G1 ||
                        continuityAt(geometry.endVertexId) == Continuity.G1 ||
                        kotlin.math.abs(geometry.arc!!.radius - geometry.chordLength * 0.5) <= CONSTRAINT_TOLERANCE)
            }
        }
    }

    private fun enforceContinuity(edit: ConstraintEdit): Scene? {
        var updated = this
        val dragged = edit.draggedControl
        val draggedVertex = dragged?.let { (edgeId, index) ->
            val edge = edgeGeometry(edgeId) ?: return null
            if (index == 0) edge.startVertexId else edge.endVertexId
        }
        continuity.forEach { (vertexId, setting) ->
            updated = if (vertexId == draggedVertex) {
                updated.propagateDraggedControl(dragged.first, dragged.second) ?: return null
            } else updated.enforceContinuityAt(vertexId, setting.continuity) ?: return null
        }
        return updated
    }

    private fun enforceContinuityAt(vertexId: Long, type: Continuity): Scene? {
        if (type == Continuity.G0) return this
        val pair = adjacentEdges(vertexId) ?: return this
        val incoming = edgeGeometry(pair.first) ?: return null
        val outgoing = edgeGeometry(pair.second) ?: return null
        // At an arc joint the circle follows its neighbor, including a Bezier handle.
        if (incoming.isArc || outgoing.isArc) return this
        if (!incoming.isBezier && !outgoing.isBezier) return this

        return if (outgoing.isBezier) {
            val source = incoming.endTangent
            if (source.length() <= EPSILON) null else {
                val targetLength = outgoing.startTangent.length()
                val target = if (type == Continuity.C1) source else source.normalized() * targetLength
                setBezierControl(outgoing.edgeId, 0, outgoing.start + target)
            }
        } else {
            val source = outgoing.startTangent
            if (source.length() <= EPSILON) null else {
                val targetLength = incoming.endTangent.length()
                val target = if (type == Continuity.C1) source else source.normalized() * targetLength
                setBezierControl(incoming.edgeId, 1, incoming.end - target)
            }
        }
    }

    /**
     * A drag makes its own handle the source of truth. At G1/C1 the opposite
     * handle, or the far end of a neighboring line, follows it.
     */
    private fun propagateDraggedControl(edgeId: Long, controlIndex: Int): Scene? {
        val edge = edgeGeometry(edgeId) ?: return null
        val vertexId = if (controlIndex == 0) edge.startVertexId else edge.endVertexId
        val type = continuity[vertexId]?.continuity ?: return this
        if (type == Continuity.G0) return this
        val pair = adjacentEdges(vertexId) ?: return this
        val incoming = edgeGeometry(pair.first) ?: return null
        val outgoing = edgeGeometry(pair.second) ?: return null

        return if (controlIndex == 0) {
            val tangent = outgoing.startTangent
            if (tangent.length() <= EPSILON) null
            else if (incoming.isArc) this
            else if (incoming.isBezier) {
                val vector = tangentForContinuity(tangent, incoming.endTangent, type)
                setBezierControl(incoming.edgeId, 1, incoming.end - vector)
            } else {
                val vector = tangentForLine(tangent, incoming.chordLength, type)
                moveVertexTo(incoming.startVertexId, incoming.end - vector)
            }
        } else {
            val tangent = incoming.endTangent
            if (tangent.length() <= EPSILON) null
            else if (outgoing.isArc) this
            else if (outgoing.isBezier) {
                val vector = tangentForContinuity(tangent, outgoing.startTangent, type)
                setBezierControl(outgoing.edgeId, 0, outgoing.start + vector)
            } else {
                val vector = tangentForLine(tangent, outgoing.chordLength, type)
                moveVertexTo(outgoing.endVertexId, outgoing.start + vector)
            }
        }
    }

    private fun tangentForContinuity(source: Vector, oldTarget: Vector, type: Continuity): Vector =
        if (type == Continuity.C1) source else source.normalized() * oldTarget.length()

    private fun tangentForLine(source: Vector, lineLength: Double, type: Continuity): Vector =
        if (type == Continuity.C1) source * 3.0 else source.normalized() * lineLength

    private fun continuitySatisfied(vertexId: Long, type: Continuity): Boolean {
        if (type == Continuity.G0) return true
        val pair = adjacentEdges(vertexId) ?: return true
        val incoming = edgeGeometry(pair.first) ?: return false
        val outgoing = edgeGeometry(pair.second) ?: return false
        if (!incoming.isBezier && !outgoing.isBezier && !incoming.isArc && !outgoing.isArc) return true
        val first = incoming.endTangent
        val second = outgoing.startTangent
        if (first.length() <= EPSILON || second.length() <= EPSILON) return false
        val sameDirection = kotlin.math.abs(first.normalized().dot(second.normalized()) - 1.0) <= CONSTRAINT_TOLERANCE
        return sameDirection && (type != Continuity.C1 || (first - second).length() <= CONSTRAINT_TOLERANCE)
    }

    private fun adjacentEdges(vertexId: Long): Pair<Long, Long>? = polygons.asSequence().mapNotNull { polygon ->
        val index = polygon.vertices.indexOfFirst { it.id == vertexId }
        if (index < 0) null else polygon.edges[(index - 1 + polygon.edges.size) % polygon.edges.size].id to polygon.edges[index].id
    }.firstOrNull()

    private fun hasBezierAt(vertexId: Long): Boolean = adjacentEdges(vertexId)?.let { (incoming, outgoing) ->
        edgeGeometry(incoming)?.isBezier == true || edgeGeometry(outgoing)?.isBezier == true
    } == true

    private fun hasArcAt(vertexId: Long): Boolean = adjacentEdges(vertexId)?.let { (incoming, outgoing) ->
        edgeGeometry(incoming)?.isArc == true || edgeGeometry(outgoing)?.isArc == true
    } == true

    private fun setBezierControl(edgeId: Long, controlIndex: Int, point: Point): Scene = replaceEdge(edgeId) { edge ->
        val curve = edge.shape as? EdgeShape.CubicBezier ?: return@replaceEdge edge
        edge.copy(shape = if (controlIndex == 0) curve.copy(firstControl = point) else curve.copy(secondControl = point))
    }

    private fun replaceEdge(edgeId: Long, transform: (Edge) -> Edge): Scene = copy(polygons = polygons.map { polygon ->
        polygon.copy(edges = polygon.edges.map { edge -> if (edge.id == edgeId) transform(edge) else edge })
    })

    private fun moveVertexTo(vertexId: Long, point: Point): Scene = copy(polygons = polygons.map { polygon ->
        polygon.copy(vertices = polygon.vertices.map { vertex ->
            if (vertex.id == vertexId) vertex.copy(point = point) else vertex
        })
    })

    private companion object {
        const val EPSILON = 1e-9
        const val CONSTRAINT_TOLERANCE = 1e-5
        const val MAX_SOLVER_ITERATIONS = 16
    }
}

private fun Edge.translateBy(delta: Vector): Edge = when (val current = shape) {
    EdgeShape.Line, is EdgeShape.CircularArc -> this
    is EdgeShape.CubicBezier -> copy(shape = current.copy(
        firstControl = current.firstControl + delta,
        secondControl = current.secondControl + delta,
    ))
}

private fun splitBezier(edge: EdgeGeometry): Pair<EdgeShape.CubicBezier, EdgeShape.CubicBezier> {
    val a = edge.start + (edge.firstControl - edge.start) * 0.5
    val b = edge.firstControl + (edge.secondControl - edge.firstControl) * 0.5
    val c = edge.secondControl + (edge.end - edge.secondControl) * 0.5
    val d = a + (b - a) * 0.5
    val e = b + (c - b) * 0.5
    return EdgeShape.CubicBezier(a, d) to EdgeShape.CubicBezier(e, c)
}

private val EdgeGeometry.vertexIds: Set<Long>
    get() = setOf(startVertexId, endVertexId)

private const val ONE_THIRD = 1.0 / 3.0
private const val TWO_THIRDS = 2.0 / 3.0

fun distanceToSegment(point: Point, start: Point, end: Point): Double {
    val direction = end - start
    val lengthSquared = direction.x * direction.x + direction.y * direction.y
    if (lengthSquared == 0.0) return point.distanceTo(start)
    val relative = point - start
    val t = ((relative.x * direction.x + relative.y * direction.y) / lengthSquared).coerceIn(0.0, 1.0)
    return point.distanceTo(start + direction * t)
}

/** Non-zero winding matches the renderer and includes the center of the self-intersecting star. */
fun containsPoint(polygon: Polygon, point: Point): Boolean {
    var winding = 0
    polygonBoundarySegments(polygon).forEach { (a, b) ->
        val side = (b.x - a.x) * (point.y - a.y) - (b.y - a.y) * (point.x - a.x)
        if (a.y <= point.y) {
            if (b.y > point.y && side > 0.0) winding++
        } else {
            if (b.y <= point.y && side < 0.0) winding--
        }
    }
    return winding != 0
}

private fun polygonBoundarySegments(polygon: Polygon): List<Pair<Point, Point>> {
    val segments = mutableListOf<Pair<Point, Point>>()
    polygon.edges.forEachIndexed { index, edge ->
        val start = polygon.edgeStart(index)
        val end = polygon.edgeEnd(index)
        if (edge.shape is EdgeShape.Line) {
            segments += start to end
        } else {
            val geometry = EdgeGeometry(edge.id, 0, 0, start, end, edge.shape)
            var previous = start
            for (step in 1..40) {
                val current = geometry.pointAt(step / 40.0)
                segments += previous to current
                previous = current
            }
        }
    }
    return segments
}
