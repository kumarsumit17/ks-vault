package com.ks.vault.ui

import androidx.activity.compose.BackHandler
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
import com.ks.vault.notes.ItemDetailScreen
import com.ks.vault.notes.ItemEditScreen
import com.ks.vault.notes.ItemListScreen
import com.ks.vault.settings.ChangeSecretScreen
import com.ks.vault.settings.SettingsScreen

/** The screens of an open vault. Kept in memory only (never in saved state), and dropped on lock. */
sealed interface Route {
    data object List : Route
    data class Detail(val id: Long) : Route
    data class Edit(val id: Long?, val kind: ItemKind) : Route
    data class Viewer(val attachmentId: Long) : Route
    data object Settings : Route
    data object ChangeSecret : Route
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
            when (val r = nav.current) {
                Route.List -> ItemListScreen(s.vault, nav, session)
                is Route.Detail -> ItemDetailScreen(s.vault, nav, session, r.id)
                is Route.Edit -> ItemEditScreen(s.vault, nav, r.id, r.kind)
                is Route.Viewer -> AttachmentViewer(s.vault, nav, r.attachmentId)
                Route.Settings -> SettingsScreen(session, settings, nav)
                Route.ChangeSecret -> ChangeSecretScreen(session, nav, s.kind)
            }
        }
    }
}
