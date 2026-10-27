package pl.edu.pw.polygons.model

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
}

internal const val EPSILON = 1e-9
