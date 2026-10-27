package pl.edu.pw.polygons.ui

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Button
import androidx.compose.material.Divider
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@Composable
fun HelpDialog(onClose: () -> Unit) {
    val sections = remember {
        checkNotNull(object {}.javaClass.getResource("/help.md")) { "Missing bundled help.md" }
            .readText(Charsets.UTF_8).split("# ").filter { it.isNotBlank() }
    }
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.widthIn(max = 760.dp).fillMaxWidth(0.9f).fillMaxHeight(0.85f),
            shape = MaterialTheme.shapes.medium,
            elevation = 8.dp,
        ) {
            Column(Modifier.fillMaxSize()) {
                Text("Pomoc — edytor wielokątów", style = MaterialTheme.typography.h6, modifier = Modifier.padding(20.dp))
                Divider()
                val scroll = rememberScrollState()
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    SelectionContainer {
                        Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(20.dp).padding(end = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            sections.forEach { section ->
                                Text(section.substringBefore('\n'), style = MaterialTheme.typography.subtitle1)
                                Text(section.substringAfter('\n').trim(), style = MaterialTheme.typography.body2)
                            }
                        }
                    }
                    VerticalScrollbar(rememberScrollbarAdapter(scroll), Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(4.dp))
                }
                Divider()
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), horizontalArrangement = Arrangement.End) {
                    Button(onClick = onClose) { Text("Zamknij") }
                }
            }
        }
    }
}
