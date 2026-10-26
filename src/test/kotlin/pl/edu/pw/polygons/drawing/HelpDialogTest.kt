package pl.edu.pw.polygons.drawing

import androidx.compose.material.MaterialTheme
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.unit.Density
import org.jetbrains.skia.Bitmap
import pl.edu.pw.polygons.ui.HelpDialog
import java.io.File
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalComposeUiApi::class)
class HelpDialogTest {
    @Test
    fun `help has a visible fixed heading and footer while its document scrolls`() {
        SwingUtilities.invokeAndWait {
            for ((width, height) in listOf(1000 to 800, 600 to 480)) {
                val scene = ImageComposeScene(width, height, density = Density(1f)) {
                    MaterialTheme { HelpDialog(onClose = {}) }
                }
                try {
                    scene.render().close()
                    val initial = scene.render()
                    val file = File("build/reports/rendering/help-$width.png")
                    file.parentFile.mkdirs()
                    initial.encodeToData()!!.use { file.writeBytes(it.bytes) }
                    val before = Bitmap.makeFromImage(initial)
                    initial.close()
                    try {
                        // The panel occupies 85% of the available height. A title
                        // must appear at the top, not beyond the scrolling document.
                        val headerTop = (height * 0.075).toInt()
                        val headerBottom = headerTop + 60
                        fun darkPixels(top: Int, bottom: Int): Int {
                            var count = 0
                            for (y in top until bottom) for (x in width / 4 until width * 3 / 4) {
                                val color = before.getColor(x, y)
                                val alpha = color ushr 24
                                val red = (color ushr 16) and 255
                                val green = (color ushr 8) and 255
                                val blue = color and 255
                                if (alpha > 200 && red < 140 && green < 140 && blue < 140) count++
                            }
                            return count
                        }
                        assertTrue(darkPixels(headerTop, headerBottom) > 100, "Missing heading at $width x $height")
                        scene.sendPointerEvent(PointerEventType.Scroll, Offset(width * 0.5f, height * 0.5f), scrollDelta = Offset(0f, 12f))
                        scene.render().close()
                        scene.render().use { scrolled ->
                            Bitmap.makeFromImage(scrolled).use { after ->
                                var changedBodyPixels = 0
                                for (y in headerTop until (height * 0.925).toInt()) for (x in width / 4 until width * 3 / 4) {
                                    val changed = before.getColor(x, y) != after.getColor(x, y)
                                    if (y < headerBottom || y >= (height * 0.925).toInt() - 65) {
                                        assertTrue(!changed, "Heading or footer moved while scrolling")
                                    } else if (changed) changedBodyPixels++
                                }
                                assertTrue(changedBodyPixels > 100, "Document did not scroll")
                            }
                        }
                    } finally { before.close() }
                } finally { scene.close() }
            }
        }
    }
}
