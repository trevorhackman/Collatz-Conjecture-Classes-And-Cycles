package hackman.trevor.collatz.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import collatzconjecture.ui.generated.resources.Res
import collatzconjecture.ui.generated.resources.icon
import java.awt.Taskbar
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO
import org.jetbrains.compose.resources.painterResource

fun main() {
    CrashLog.install()
    application {
        val icon = painterResource(Res.drawable.icon)
        // macOS draws the Dock icon from the process, not from Window.icon.
        LaunchedEffect(Unit) { installDockIcon() }
        Window(
            onCloseRequest = ::exitApplication,
            title = "Collatz",
            icon = icon,
            state = rememberWindowState(width = 1100.dp, height = 820.dp),
        ) {
            App()
        }
    }
}

private suspend fun installDockIcon() {
    try {
        if (!Taskbar.isTaskbarSupported()) return
        val taskbar = Taskbar.getTaskbar()
        if (!taskbar.isSupported(Taskbar.Feature.ICON_IMAGE)) return
        val bytes = Res.readBytes("drawable/icon.png")
        val image = ImageIO.read(ByteArrayInputStream(bytes)) ?: return
        taskbar.iconImage = image
    } catch (_: Exception) {
        // The window still opens if this platform has no taskbar icon.
    }
}

@Composable
private fun App() {
    MaterialTheme(colorScheme = darkColorScheme()) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                var tab by remember { mutableIntStateOf(0) }
                PrimaryTabRow(selectedTabIndex = tab) {
                    Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Classes") })
                    Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Sequence") })
                    Tab(selected = tab == 2, onClick = { tab = 2 }, text = { Text("Cycles") })
                }
                Column(Modifier.weight(1f).padding(20.dp)) {
                    when (tab) {
                        0 -> {
                            Text(
                                "The probe splits only classes still open. 0 mod 2 settles half of all integers; 1 mod 4 brings the proved share to three quarters.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            CoverageProbePanel(Modifier.weight(1f).padding(top = 12.dp))
                        }
                        1 -> TrajectoryPanel(Modifier.fillMaxSize())
                        else -> {
                            Text(
                                "An accelerated cycle of length n returns to its start after n steps of (3x+1)/2^k. The probe solves that equation for n = 1, 2, 3, …",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            CycleProbePanel(Modifier.weight(1f).padding(top = 12.dp))
                        }
                    }
                }
            }
        }
    }
}
