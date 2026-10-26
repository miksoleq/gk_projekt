package pl.edu.pw.polygons.model

import kotlin.math.PI

/** Temporary interaction hints; they are not saved as part of a geometric constraint. */
internal data class ConstraintEdit(
    val movedVertexIds: Set<Long> = emptySet(),
    val preferredEdgeIds: Set<Long> = emptySet(),
    val draggedControl: Pair<Long, Int>? = null,
)

internal fun Scene.solveConstraints(edit: ConstraintEdit = ConstraintEdit()): ConstraintUpdate {
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

private fun Scene.enforceLength(constraint: LengthConstraint, edit: ConstraintEdit, priorities: Map<Long, Int>): Scene? {
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

private fun Scene.enforcePerpendicular(constraint: PerpendicularConstraint, edit: ConstraintEdit, priorities: Map<Long, Int>): Scene? {
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

internal fun Scene.incidentEdgeIds(vertexIds: Set<Long>): Set<Long> = buildSet {
    polygons.forEach { polygon ->
        polygon.edges.forEachIndexed { index, edge ->
            if (polygon.vertices[index].id in vertexIds ||
                polygon.vertices[(index + 1) % polygon.vertices.size].id in vertexIds) add(edge.id)
        }
    }
}

/** One driver per connected component prevents competing sources during a joint drag. */
private fun Scene.perpendicularPriorities(preferredEdgeIds: Set<Long>): Map<Long, Int> {
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

private fun Scene.keepEndForCorrection(edge: EdgeGeometry, edit: ConstraintEdit, priorities: Map<Long, Int>): Boolean {
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

private fun Scene.constraintsSatisfied(): Boolean = constraints.all { constraint ->
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

private fun Scene.arcContinuityError(): String? {
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

private fun Scene.enforceArcs(): Scene? {
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

private fun Scene.arcsSatisfied(): Boolean = polygons.all { polygon ->
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

private fun Scene.enforceContinuity(edit: ConstraintEdit): Scene? {
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

private fun Scene.enforceContinuityAt(vertexId: Long, type: Continuity): Scene? {
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
internal fun Scene.propagateDraggedControl(edgeId: Long, controlIndex: Int): Scene? {
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

private fun Scene.tangentForContinuity(source: Vector, oldTarget: Vector, type: Continuity): Vector =
    if (type == Continuity.C1) source else source.normalized() * oldTarget.length()

private fun Scene.tangentForLine(source: Vector, lineLength: Double, type: Continuity): Vector =
    if (type == Continuity.C1) source * 3.0 else source.normalized() * lineLength

private fun Scene.continuitySatisfied(vertexId: Long, type: Continuity): Boolean {
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

private fun Scene.adjacentEdges(vertexId: Long): Pair<Long, Long>? = polygons.asSequence().mapNotNull { polygon ->
    val index = polygon.vertices.indexOfFirst { it.id == vertexId }
    if (index < 0) null else polygon.edges[(index - 1 + polygon.edges.size) % polygon.edges.size].id to polygon.edges[index].id
}.firstOrNull()

internal fun Scene.hasBezierAt(vertexId: Long): Boolean = adjacentEdges(vertexId)?.let { (incoming, outgoing) ->
    edgeGeometry(incoming)?.isBezier == true || edgeGeometry(outgoing)?.isBezier == true
} == true

internal fun Scene.hasArcAt(vertexId: Long): Boolean = adjacentEdges(vertexId)?.let { (incoming, outgoing) ->
    edgeGeometry(incoming)?.isArc == true || edgeGeometry(outgoing)?.isArc == true
} == true

private fun Scene.setBezierControl(edgeId: Long, controlIndex: Int, point: Point): Scene = replaceEdge(edgeId) { edge ->
    val curve = edge.shape as? EdgeShape.CubicBezier ?: return@replaceEdge edge
    edge.copy(shape = if (controlIndex == 0) curve.copy(firstControl = point) else curve.copy(secondControl = point))
}

internal fun Scene.replaceEdge(edgeId: Long, transform: (Edge) -> Edge): Scene = copy(polygons = polygons.map { polygon ->
    polygon.copy(edges = polygon.edges.map { edge -> if (edge.id == edgeId) transform(edge) else edge })
})

private fun Scene.moveVertexTo(vertexId: Long, point: Point): Scene = copy(polygons = polygons.map { polygon ->
    polygon.copy(vertices = polygon.vertices.map { vertex ->
        if (vertex.id == vertexId) vertex.copy(point = point) else vertex
    })
})

private val EdgeGeometry.vertexIds: Set<Long>
    get() = setOf(startVertexId, endVertexId)

private const val CONSTRAINT_TOLERANCE = 1e-5
private const val MAX_SOLVER_ITERATIONS = 16
