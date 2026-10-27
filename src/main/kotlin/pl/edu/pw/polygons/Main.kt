package pl.edu.pw.polygons

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Divider
import androidx.compose.material.AlertDialog
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.RadioButton
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.Alignment
import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import pl.edu.pw.polygons.drawing.LineAlgorithm
import pl.edu.pw.polygons.drawing.RenderSelection
import pl.edu.pw.polygons.drawing.Viewport
import pl.edu.pw.polygons.drawing.drawPolygonScene
import pl.edu.pw.polygons.model.Point
import pl.edu.pw.polygons.model.ConstraintUpdate
import pl.edu.pw.polygons.model.Continuity
import pl.edu.pw.polygons.model.EdgeShape
import pl.edu.pw.polygons.model.EdgeGeometry
import pl.edu.pw.polygons.model.GeometricConstraint
import pl.edu.pw.polygons.model.LengthConstraint
import pl.edu.pw.polygons.model.PerpendicularConstraint
import pl.edu.pw.polygons.model.Scene
import pl.edu.pw.polygons.model.containsPoint
import pl.edu.pw.polygons.model.demoScene
import pl.edu.pw.polygons.model.distanceToSegment
import pl.edu.pw.polygons.interaction.DragGesture
import pl.edu.pw.polygons.interaction.DragTarget
import pl.edu.pw.polygons.ui.HelpDialog
import kotlin.math.min

private val ink = Color(0xFF213047)
private val blue = Color(0xFF4B75D1)
private val orange = Color(0xFFED8D5A)
private val background = Color(0xFFF3F6FB)
private val palette = listOf(Color(0xFF567DDA), Color(0xFF45A894), Color(0xFFD9875C), Color(0xFF9A78C9))

private const val WORLD_WIDTH = 900f
private const val WORLD_HEIGHT = 470f

private sealed interface Selection {
    val polygonId: Long
    data class PolygonBody(override val polygonId: Long) : Selection
    data class Vertex(override val polygonId: Long, val vertexId: Long) : Selection
    data class Edge(override val polygonId: Long, val edgeId: Long) : Selection
    data class ControlPoint(override val polygonId: Long, val edgeId: Long, val controlIndex: Int) : Selection
}

private fun viewport(width: Float, height: Float): Viewport {
    val scale = min(width / WORLD_WIDTH, height / WORLD_HEIGHT).coerceAtLeast(0.01f)
    return Viewport(scale, (width - WORLD_WIDTH * scale) / 2f, (height - WORLD_HEIGHT * scale) / 2f)
}

private fun hitTest(scene: Scene, point: Point, tolerance: Double): Selection? {
    for (polygon in scene.polygons.asReversed()) {
        for (vertex in polygon.vertices.asReversed()) {
            if (vertex.point.distanceTo(point) <= tolerance) return Selection.Vertex(polygon.id, vertex.id)
        }
    }
    for (polygon in scene.polygons.asReversed()) {
        for (edge in polygon.edges.asReversed()) {
            val geometry = scene.edgeGeometry(edge.id) ?: continue
            if (!geometry.isBezier) continue
            if (geometry.firstControl.distanceTo(point) <= tolerance) return Selection.ControlPoint(polygon.id, edge.id, 0)
            if (geometry.secondControl.distanceTo(point) <= tolerance) return Selection.ControlPoint(polygon.id, edge.id, 1)
        }
    }
    for (polygon in scene.polygons.asReversed()) {
        for (index in polygon.edges.indices.reversed()) {
            val geometry = scene.edgeGeometry(polygon.edges[index].id) ?: continue
            if (distanceToEdge(point, geometry) <= tolerance) {
                return Selection.Edge(polygon.id, polygon.edges[index].id)
            }
        }
    }
    return scene.polygons.asReversed().firstOrNull { containsPoint(it, point) }
        ?.let { Selection.PolygonBody(it.id) }
}

fun main() = application {
    Window(onCloseRequest = ::exitApplication, title = "Edytor wielokątów — punkty 1–9") {
        PolygonEditor()
    }
}

@Preview
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun PolygonEditor() {
    var scene by remember { mutableStateOf(demoScene()) }
    var lineAlgorithm by remember { mutableStateOf(LineAlgorithm.Library) }
    var selection by remember { mutableStateOf<Selection?>(null) }
    var selectedEdgeIds by remember { mutableStateOf<List<Long>>(emptyList()) }
    var dragGesture by remember { mutableStateOf<DragGesture?>(null) }
    var showHelp by remember { mutableStateOf(false) }
    val editorFocus = remember { FocusRequester() }
    var lengthDialogEdgeId by remember { mutableStateOf<Long?>(null) }
    var lengthText by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("Kliknij wierzchołek, krawędź lub wnętrze wielokąta.") }

    LaunchedEffect(Unit) { editorFocus.requestFocus() }

    MaterialTheme {
        Row(Modifier.fillMaxSize().background(background).onPreviewKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown || lengthDialogEdgeId != null) return@onPreviewKeyEvent false
            when (event.key) {
                Key.F1 -> { showHelp = true; dragGesture = null; true }
                Key.Escape -> {
                    dragGesture = null
                    selection = null; selectedEdgeIds = emptyList()
                    message = "Anulowano bieżącą czynność i wyczyszczono wybór."
                    true
                }
                Key.Delete -> {
                    val selected = selection
                    if (selected is Selection.Vertex) {
                        val updated = scene.removeVertex(selected.vertexId)
                        if (updated != scene) {
                            scene = updated; selection = null; selectedEdgeIds = emptyList(); dragGesture = null
                            message = "Usunięto wierzchołek."
                        } else message = "Nie można usunąć wierzchołka: minimum trzy punkty lub konflikt relacji."
                        true
                    } else false
                }
                else -> false
            }
        }) {
            Column(
                Modifier.width(250.dp).fillMaxHeight().background(Color.White).padding(18.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("Edytor wielokątów", fontSize = 20.sp, color = ink)
                Text("Zadanie 1 · punkty 1–9", fontSize = 13.sp, color = blue)
                Button(onClick = { showHelp = true; dragGesture = null }, modifier = Modifier.fillMaxWidth()) { Text("Pomoc · F1") }
                Divider()
                Text("RYSOWANIE ODCINKÓW", fontSize = 11.sp, color = Color.Gray)
                Column(Modifier.selectableGroup()) {
                    LineAlgorithm.entries.forEach { algorithm ->
                        Row(
                            modifier = Modifier.fillMaxWidth().selectable(
                                selected = lineAlgorithm == algorithm,
                                onClick = { lineAlgorithm = algorithm },
                                role = Role.RadioButton,
                            ).padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = lineAlgorithm == algorithm, onClick = null)
                            Spacer(Modifier.width(8.dp))
                            Text(algorithm.label, color = ink, fontSize = 13.sp)
                        }
                    }
                }
                Text("Bézier: algorytm przyrostowy w obu trybach. Łączenie próbek korzysta z wybranej metody odcinków.", fontSize = 12.sp, color = Color.DarkGray)
                Divider()
                Text("SCENA", fontSize = 11.sp, color = Color.Gray)
                scene.polygons.forEachIndexed { index, polygon ->
                    Button(
                        onClick = {
                            selection = Selection.PolygonBody(polygon.id)
                            message = "Wybrano: ${polygon.name}. Przeciągnij jego wnętrze lub krawędź."
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(backgroundColor = palette[index % palette.size].copy(alpha = 0.16f)),
                    ) {
                        Text("${polygon.name} · ${polygon.vertices.size} wierzch.", color = ink)
                    }
                }
                Divider()
                Text("EDYCJA", fontSize = 11.sp, color = Color.Gray)
                val chosenPolygon = selection?.let { scene.polygon(it.polygonId) }
                Text(
                    when (val selected = selection) {
                        is Selection.Vertex -> "Wierzchołek #${selected.vertexId} · ${chosenPolygon?.name}"
                        is Selection.Edge -> "Krawędź #${selected.edgeId} · ${chosenPolygon?.name}"
                        is Selection.ControlPoint -> "Punkt kontrolny ${selected.controlIndex + 1} · krawędź #${selected.edgeId}"
                        is Selection.PolygonBody -> "${chosenPolygon?.name} · cały wielokąt"
                        null -> "Nic nie wybrano"
                    },
                    color = ink,
                    fontSize = 13.sp,
                )
                if (selection is Selection.Edge) {
                    val selected = selection as Selection.Edge
                    val geometry = scene.edgeGeometry(selected.edgeId)!!
                    val isBezier = geometry.isBezier
                    Text(when (geometry.shape) {
                        EdgeShape.Line -> "Typ: odcinek"
                        is EdgeShape.CubicBezier -> "Typ: Bézier"
                        is EdgeShape.CircularArc -> "Typ: łuk okręgu · promień ${"%.2f".format(java.util.Locale.US, geometry.arc!!.radius)}"
                    }, fontSize = 12.sp, color = ink)
                    Button(onClick = {
                        when (val result = scene.toggleBezier(selected.edgeId)) {
                            is ConstraintUpdate.Applied -> {
                                scene = result.scene
                                message = if (isBezier) "Zmieniono segment Béziera na odcinek." else "Zmieniono odcinek na segment Béziera."
                            }
                            is ConstraintUpdate.Rejected -> message = result.reason
                        }
                    }, modifier = Modifier.fillMaxWidth()) { Text(if (isBezier) "Zmień na odcinek" else "Zmień na Béziera") }
                    Button(onClick = {
                        when (val result = scene.toggleArc(selected.edgeId)) {
                            is ConstraintUpdate.Applied -> {
                                scene = result.scene
                                message = if (geometry.isArc) "Zmieniono łuk na odcinek." else "Utworzono łuk. Wybierz jego koniec, aby ustawić G0 lub G1."
                            }
                            is ConstraintUpdate.Rejected -> message = result.reason
                        }
                    }, modifier = Modifier.fillMaxWidth()) { Text(if (geometry.isArc) "Łuk → odcinek" else "Zmień na łuk") }
                    Button(onClick = {
                        val updated = scene.insertAtEdgeMidpoint(selected.edgeId)
                        if (updated !== scene) {
                            scene = updated
                            selection = Selection.Vertex(selected.polygonId, updated.nextId - 2)
                            selectedEdgeIds = emptyList()
                            message = "Dodano wierzchołek w środku krawędzi."
                        } else message = "Nie można podzielić krawędzi przy obecnych ograniczeniach."
                    }, modifier = Modifier.fillMaxWidth()) { Text("Dodaj wierzchołek") }
                }
                if (selection is Selection.Vertex) {
                    val selected = selection as Selection.Vertex
                    scene.continuityAt(selected.vertexId)?.let { active ->
                        Text("Ciągłość przy wierzchołku", fontSize = 12.sp, color = ink)
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            scene.availableContinuities(selected.vertexId).forEach { type ->
                                Button(onClick = {
                                    when (val result = scene.setContinuity(selected.vertexId, type)) {
                                        is ConstraintUpdate.Applied -> { scene = result.scene; message = "Ustawiono ciągłość ${type.name}." }
                                        is ConstraintUpdate.Rejected -> message = result.reason
                                    }
                                }, enabled = scene.canSetContinuity(selected.vertexId, type),
                                    colors = ButtonDefaults.buttonColors(backgroundColor = if (type == active) orange else Color(0xFFE8EDF5))) {
                                    Text(type.name, fontSize = 11.sp)
                                }
                            }
                        }
                        if (scene.availableContinuities(selected.vertexId).size == 2) {
                            Text("Łuk: G1 może być ustawione tylko na jednym końcu. Przy G0 na obu końcach powstaje półokrąg.", fontSize = 12.sp, color = Color.DarkGray)
                        }
                    }
                    Button(onClick = {
                        val updated = scene.removeVertex(selected.vertexId)
                        if (updated === scene) {
                            message = "Nie można usunąć wierzchołka: wymagane są co najmniej 3 wierzchołki i zgodne ograniczenia."
                        } else {
                            scene = updated
                            selection = Selection.PolygonBody(selected.polygonId)
                            selectedEdgeIds = selectedEdgeIds.filter { updated.edgeGeometry(it) != null }
                            message = "Usunięto wierzchołek i połączono sąsiednie krawędzie."
                        }
                    }, modifier = Modifier.fillMaxWidth()) { Text("Usuń wierzchołek") }
                }
                Divider()
                Text("OGRANICZENIA", fontSize = 11.sp, color = Color.Gray)
                Text(
                    if (selectedEdgeIds.isEmpty()) "Wybierz jedną lub dwie krawędzie na scenie."
                    else "Wybrane krawędzie: ${selectedEdgeIds.joinToString(", ") { "#$it" }}",
                    fontSize = 12.sp,
                    color = ink,
                )
                if (selectedEdgeIds.size == 1) {
                    Button(onClick = {
                        val edgeId = selectedEdgeIds.single()
                        val length = scene.edgeGeometry(edgeId)?.length ?: return@Button
                        lengthText = "%.2f".format(java.util.Locale.US, length)
                        lengthDialogEdgeId = edgeId
                    }, modifier = Modifier.fillMaxWidth()) { Text("Ustaw długość") }
                }
                if (selectedEdgeIds.size == 2) {
                    Button(onClick = {
                        when (val result = scene.addPerpendicularConstraint(selectedEdgeIds[0], selectedEdgeIds[1])) {
                            is ConstraintUpdate.Applied -> {
                                scene = result.scene
                                message = "Dodano prostopadłość między wybranymi krawędziami."
                            }
                            is ConstraintUpdate.Rejected -> message = result.reason
                        }
                    }, modifier = Modifier.fillMaxWidth()) { Text("Ustaw prostopadłość") }
                }
                if (selectedEdgeIds.isNotEmpty()) {
                    Button(onClick = { selectedEdgeIds = emptyList() }, modifier = Modifier.fillMaxWidth()) {
                        Text("Wyczyść wybór krawędzi")
                    }
                }
                if (scene.constraints.isEmpty()) {
                    Text("Brak aktywnych ograniczeń.", fontSize = 12.sp, color = Color.DarkGray)
                } else {
                    scene.constraints.forEach { constraint ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(constraintDescription(constraint), modifier = Modifier.weight(1f), fontSize = 12.sp, color = ink)
                            Button(onClick = {
                                scene = scene.removeConstraint(constraint.id)
                                message = "Usunięto ograniczenie."
                            }) { Text("Usuń", fontSize = 11.sp) }
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text("Przeciągnij wierzchołek lub punkt kontrolny, aby zmienić kształt. Przeciągnij krawędź lub wnętrze, aby przesunąć cały wielokąt.", fontSize = 12.sp, color = Color.DarkGray)
                Text("Samoprzecięcia są dozwolone. Scenę można zmienić w pliku DemoScene.kt.", fontSize = 12.sp, color = Color.DarkGray)
                Spacer(Modifier.height(8.dp))
                Button(onClick = {
                    scene = demoScene()
                    selection = null
                    selectedEdgeIds = emptyList()
                    dragGesture = null
                    message = "Przywrócono scenę początkową."
                }, modifier = Modifier.fillMaxWidth()) { Text("Przywróć scenę") }
            }

            Column(Modifier.weight(1f).fillMaxHeight().padding(18.dp)) {
                Surface(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    elevation = 2.dp,
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Box(Modifier.fillMaxSize().border(1.dp, Color(0xFFDDE4EE))) {
                        Canvas(
                            Modifier.fillMaxSize()
                                .focusRequester(editorFocus).focusable()
                                .onPointerEvent(PointerEventType.Press) { event ->
                                    if (!event.buttons.isPrimaryPressed) return@onPointerEvent
                                    val change = event.changes.firstOrNull() ?: return@onPointerEvent
                                    if (!change.pressed) return@onPointerEvent
                                    editorFocus.requestFocus()
                                    val view = viewport(size.width.toFloat(), size.height.toFloat())
                                    val pointer = view.scenePoint(change.position)
                                    selection = hitTest(scene, pointer, 10.0 / view.scale)
                                    if (selection is Selection.Edge) {
                                        val edgeId = (selection as Selection.Edge).edgeId
                                        selectedEdgeIds = if (edgeId in selectedEdgeIds) {
                                            selectedEdgeIds - edgeId
                                        } else {
                                            (selectedEdgeIds + edgeId).takeLast(2)
                                        }
                                    }
                                    val target = when (val selected = selection) {
                                        is Selection.Vertex -> DragTarget.Vertex(selected.vertexId)
                                        is Selection.ControlPoint -> DragTarget.Control(selected.edgeId, selected.controlIndex)
                                        is Selection.Edge, is Selection.PolygonBody -> DragTarget.Polygon(selected.polygonId)
                                        null -> null
                                    }
                                    dragGesture = target?.let { DragGesture.begin(scene, it, pointer) }
                                    message = when (selection) {
                                        is Selection.Vertex -> "Przeciągnij wierzchołek."
                                        is Selection.ControlPoint -> "Przeciągnij punkt kontrolny."
                                        is Selection.Edge, is Selection.PolygonBody -> "Przeciągnij cały wielokąt."
                                        null -> "Kliknij wierzchołek, krawędź lub wnętrze wielokąta."
                                    }
                                }
                                .onPointerEvent(PointerEventType.Move) { event ->
                                    val change = event.changes.firstOrNull() ?: return@onPointerEvent
                                    val view = viewport(size.width.toFloat(), size.height.toFloat())
                                    val pointer = view.scenePoint(change.position)
                                    if (!event.buttons.isPrimaryPressed) { dragGesture = null; return@onPointerEvent }
                                    val gesture = dragGesture ?: return@onPointerEvent
                                    when (val result = gesture.move(scene, pointer)) {
                                        is ConstraintUpdate.Applied -> scene = result.scene
                                        is ConstraintUpdate.Rejected -> message = result.reason
                                    }
                                }
                                .onPointerEvent(PointerEventType.Release) { event ->
                                    if (!event.buttons.isPrimaryPressed) dragGesture = null
                                },
                        ) {
                            val view = viewport(size.width, size.height)
                            drawRect(Color.White)
                            drawPolygonScene(
                                scene = scene,
                                viewport = view,
                                selection = RenderSelection(
                                    polygonId = selection?.polygonId,
                                    vertexId = (selection as? Selection.Vertex)?.vertexId,
                                    controlEdgeId = (selection as? Selection.ControlPoint)?.edgeId,
                                    controlIndex = (selection as? Selection.ControlPoint)?.controlIndex,
                                    edgeIds = selectedEdgeIds,
                                ),
                                lineAlgorithm = lineAlgorithm,
                                palette = palette,
                                highlight = orange,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(message, fontSize = 13.sp, color = ink)
            }
        }
    }

    if (showHelp) HelpDialog(onClose = { showHelp = false; editorFocus.requestFocus() })

    lengthDialogEdgeId?.let { edgeId ->
        AlertDialog(
            onDismissRequest = { lengthDialogEdgeId = null },
            title = { Text("Zadana długość krawędzi") },
            text = {
                OutlinedTextField(
                    value = lengthText,
                    onValueChange = { lengthText = it },
                    label = { Text("Długość") },
                    singleLine = true,
                )
            },
            confirmButton = {
                Button(onClick = {
                    val length = lengthText.replace(',', '.').toDoubleOrNull()
                    if (length == null) {
                        message = "Podaj poprawną liczbę."
                    } else {
                        when (val result = scene.addLengthConstraint(edgeId, length)) {
                            is ConstraintUpdate.Applied -> {
                                scene = result.scene
                                message = "Ustawiono długość krawędzi #$edgeId na ${"%.2f".format(java.util.Locale.US, length)}."
                                lengthDialogEdgeId = null
                            }
                            is ConstraintUpdate.Rejected -> message = result.reason
                        }
                    }
                }) { Text("Zastosuj") }
            },
            dismissButton = {
                Button(onClick = { lengthDialogEdgeId = null }) { Text("Anuluj") }
            },
        )
    }
}

private fun constraintDescription(constraint: GeometricConstraint): String = when (constraint) {
    is LengthConstraint -> "Długość #${constraint.edgeId} = ${"%.2f".format(java.util.Locale.US, constraint.desiredLength)}"
    is PerpendicularConstraint -> "#${constraint.firstEdgeId} ⟂ #${constraint.secondEdgeId}"
}

private fun distanceToEdge(point: Point, edge: EdgeGeometry): Double {
    if (edge.shape is EdgeShape.Line) return distanceToSegment(point, edge.start, edge.end)
    var result = Double.POSITIVE_INFINITY
    var previous = edge.start
    for (index in 1..40) {
        val current = edge.pointAt(index / 40.0)
        result = minOf(result, distanceToSegment(point, previous, current))
        previous = current
    }
    return result
}

