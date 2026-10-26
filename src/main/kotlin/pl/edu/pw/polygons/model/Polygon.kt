package pl.edu.pw.polygons.model

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
