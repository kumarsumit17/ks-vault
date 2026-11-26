package com.ks.vault.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ks.vault.R
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ks.vault.auth.SetupScreen
import com.ks.vault.auth.UnlockScreen
import com.ks.vault.auth.VaultSession
import com.ks.vault.auth.VaultSettings
import com.ks.vault.data.ItemKind
import com.ks.vault.documents.AttachmentViewer
import com.ks.vault.documents.CameraScreen
import com.ks.vault.documents.SaveSharedScreen
import com.ks.vault.notes.ItemDetailScreen
import com.ks.vault.notes.ItemEditScreen
import com.ks.vault.notes.ItemListScreen
import com.ks.vault.settings.BackupScreen
import com.ks.vault.settings.ChangeSecretScreen
import com.ks.vault.settings.EnableBiometricScreen
import com.ks.vault.settings.RestoreScreen
import com.ks.vault.settings.SettingsScreen

/** The screens of an open vault. Kept in memory only (never in saved state), and dropped on lock. */
sealed interface Route {
    data object List : Route
    data class Detail(val id: Long) : Route
    data class Edit(val id: Long?, val kind: ItemKind) : Route
    data class Viewer(val attachmentId: Long) : Route
    data class Camera(val itemId: Long) : Route
    data object SaveShared : Route
    data object Settings : Route
    data object ChangeSecret : Route
    data object Backup : Route
    data object EnableBiometric : Route
    data object Restore : Route
}

class Navigator {
    val stack = mutableStateListOf<Route>(Route.List)
    val current: Route get() = stack.last()
    fun go(route: Route) { stack.add(route) }
    fun back() { if (stack.size > 1) stack.removeAt(stack.lastIndex) }
    /** Replaces the current screen (after saving a new item, its detail replaces the editor). */
    fun replace(route: Route) { stack[stack.lastIndex] = route }
}

@Composable
fun VaultRoot(session: VaultSession, settings: VaultSettings) {
    val state by session.state.collectAsStateWithLifecycle()
    when (val s = state) {
        VaultSession.State.NeedsSetup -> SetupScreen(session)
        is VaultSession.State.Locked -> UnlockScreen(session, s.kind)
        is VaultSession.State.Unlocked -> {
            // A new navigator for every unlock: the vault always opens on the list.
            val nav = remember(s.vault) { Navigator() }
            BackHandler(enabled = nav.stack.size > 1) { nav.back() }
            // Files shared from another app open the save screen once the vault is unlocked.
            val shared by session.shared.collectAsStateWithLifecycle()
            LaunchedEffect(shared) {
                if (shared.isNotEmpty() && nav.current != Route.SaveShared) nav.go(Route.SaveShared)
            }
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val r = nav.current
                // Wide screens (landscape, foldables): the list and the chosen item side by side.
                if (maxWidth >= WIDE && (r == Route.List || r is Route.Detail)) {
                    val selected = (r as? Route.Detail)?.id
                    Row(Modifier.fillMaxSize()) {
                        Box(Modifier.weight(0.42f)) {
                            ItemListScreen(s.vault, nav, session, selected) { id ->
                                if (nav.current is Route.Detail) nav.replace(Route.Detail(id)) else nav.go(Route.Detail(id))
                            }
                        }
                        VerticalDivider()
                        Box(Modifier.weight(0.58f), contentAlignment = Alignment.Center) {
                            if (selected != null) {
                                key(selected) { ItemDetailScreen(s.vault, nav, session, selected) }
                            } else {
                                Text(
                                    stringResource(R.string.list_choose_item),
                                    style = MaterialTheme.typography.bodyLarge,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(32.dp),
                                )
                            }
                        }
                    }
                } else {
                    Screen(r, s, nav, session, settings)
                }
            }
        }
    }
}

private val WIDE = 720.dp

@Composable
private fun Screen(r: Route, s: VaultSession.State.Unlocked, nav: Navigator, session: VaultSession, settings: VaultSettings) {
            when (r) {
                Route.List -> ItemListScreen(s.vault, nav, session)
                is Route.Detail -> ItemDetailScreen(s.vault, nav, session, r.id)
                is Route.Edit -> ItemEditScreen(s.vault, nav, r.id, r.kind)
                is Route.Viewer -> AttachmentViewer(s.vault, nav, r.attachmentId)
                is Route.Camera -> CameraScreen(s.vault, nav, r.itemId)
                Route.SaveShared -> SaveSharedScreen(s.vault, nav, session)
                Route.Settings -> SettingsScreen(session, settings, nav)
                Route.ChangeSecret -> ChangeSecretScreen(session, nav, s.kind)
                Route.Backup -> BackupScreen(s.vault, session, nav)
                Route.EnableBiometric -> EnableBiometricScreen(session, nav, s.kind)
                Route.Restore -> RestoreScreen(s.vault, session, nav)
            }
}
