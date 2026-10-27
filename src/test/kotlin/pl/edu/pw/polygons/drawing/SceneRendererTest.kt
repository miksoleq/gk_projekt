package pl.edu.pw.polygons.drawing

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import org.jetbrains.skia.Image
import pl.edu.pw.polygons.model.Point
import pl.edu.pw.polygons.model.demoScene
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SceneRendererTest {
    @Test
    fun `rasterizer paints exactly the selected pixels without a library line underneath`() {
        val bitmap = ImageBitmap(24, 16)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bitmap), Size(24f, 16f)) {
            drawBresenhamPolyline(listOf(Point(2.0, 3.0), Point(18.0, 9.0)), Color.Black, 1f)
        }
        val expected = mutableSetOf<Pixel>()
        bresenhamLine(Pixel(2, 3), Pixel(18, 9)) { expected += it }
        val map = bitmap.toPixelMap()
        for (x in 0 until 24) for (y in 0 until 16) {
            assertEquals(if (Pixel(x, y) in expected) 1f else 0f, map[x, y].alpha, 1e-6f, "Unexpected coverage at $x, $y")
        }
    }

    @Test
    fun `both algorithms render the full scene including the selected Bezier and arc`() {
        val scene = demoScene()
        val selection = RenderSelection(
            polygonId = scene.polygons[1].id,
            vertexId = null, controlEdgeId = null, controlIndex = null,
            edgeIds = listOf(scene.polygons[1].edges[1].id, scene.polygons[2].edges[2].id),
        )
        val images = LineAlgorithm.entries.map { algorithm ->
            val bitmap = ImageBitmap(900, 470)
            CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bitmap), Size(900f, 470f)) {
                drawRect(Color.White)
                drawPolygonScene(scene, Viewport(1f, 0f, 0f), selection, algorithm,
                    listOf(Color(0xFF567DDA), Color(0xFF45A894), Color(0xFFD9875C)), Color(0xFFED8D5A))
            }
            val file = File("build/reports/rendering/${algorithm.name.lowercase()}.png")
            file.parentFile.mkdirs()
            Image.makeFromBitmap(bitmap.asSkiaBitmap()).use { image ->
                image.encodeToData()!!.use { file.writeBytes(it.bytes) }
            }
            bitmap.toPixelMap()
        }
        var differences = 0
        for (x in 0 until 900) for (y in 0 until 470) if (images[0][x, y] != images[1][x, y]) differences++
        assertTrue(differences > 100, "Switching algorithms should change the actual rasterized image")
        assertTrue(images.all { it[180, 225] == Color.White }, "The center of the star must remain unfilled in both modes")
    }
}
