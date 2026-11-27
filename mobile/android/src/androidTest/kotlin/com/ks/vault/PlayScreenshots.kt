package com.ks.vault

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.Image
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ks.vault.crypto.Aead
import com.ks.vault.crypto.FileCipher
import com.ks.vault.crypto.SecretKind
import com.ks.vault.data.IdType
import com.ks.vault.data.Item
import com.ks.vault.data.ItemKind
import com.ks.vault.data.TicketType
import com.ks.vault.data.VaultDatabase
import com.ks.vault.data.VaultRepository
import com.ks.vault.notes.ItemDetailScreen
import com.ks.vault.notes.ItemListScreen
import com.ks.vault.auth.UnlockScreen
import com.ks.vault.ui.Navigator
import com.ks.vault.ui.VaultTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate

/**
 * Google Play images (specs/google-play.md), made from the real screens with sample data in a test
 * window, because the app's own window is FLAG_SECURE. Runs only with `-e screenshots true`; the
 * images go to the app's external files folder, `screenshots/`, to be pulled with adb.
 */
@RunWith(AndroidJUnit4::class)
class PlayScreenshots {
    @get:Rule val rule = createComposeRule()

    private val context get() = ApplicationProvider.getApplicationContext<VaultApp>()
    private val out by lazy { File(context.getExternalFilesDir(null), "screenshots").apply { mkdirs() } }

    @Before fun onlyWhenAsked() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("screenshots") == "true")
    }

    private fun save(name: String) {
        val bmp = rule.onRoot().captureToImage().asAndroidBitmap()
        File(out, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun sampleVault(): Pair<VaultRepository, Long> = runBlocking {
        context.deleteDatabase("screens.db")
        val repo = VaultRepository(VaultDatabase.open(context, Aead.randomKey(), "screens.db"), FileCipher(File(context.cacheDir, "screens")))
        val day = { y: Int, m: Int, d: Int -> LocalDate.of(y, m, d).toEpochDay() }
        repo.save(Item(kind = ItemKind.TICKET, title = "Delhi to Pune", ticketType = TicketType.TRAIN, fromPlace = "New Delhi", toPlace = "Pune", travelDate = day(2026, 12, 18), travelTime = "16:55", bookingRef = "4521367890", seat = "B4 32"))
        repo.save(Item(kind = ItemKind.NOTE, title = "Locker and Wi-Fi codes", notes = "Office locker: 4471\nHome Wi-Fi: on the router label"))
        val pan = repo.save(Item(kind = ItemKind.ID_CARD, title = "PAN card", idType = IdType.PAN, idNumber = "ABCDE1234F", idName = "Asha Verma", idIssued = day(2019, 4, 2), pinned = true))
        repo.save(Item(kind = ItemKind.ID_CARD, title = "Passport", idType = IdType.PASSPORT, idNumber = "Z1234567", idName = "Asha Verma", idIssued = day(2021, 6, 15), idExpires = day(2031, 6, 14)))
        repo.save(Item(kind = ItemKind.TICKET, title = "Concert, 9 January", ticketType = TicketType.EVENT, travelDate = day(2027, 1, 9), seat = "Row F, 12"))
        repo to pan
    }

    @Test fun listDetailAndLock() {
        val (repo, pan) = sampleVault()
        val session = context.session
        val screen = androidx.compose.runtime.mutableIntStateOf(0)
        rule.setContent {
            VaultTheme {
                when (screen.intValue) {
                    0 -> ItemListScreen(repo, Navigator(), session)
                    1 -> ItemDetailScreen(repo, Navigator(), session, pan)
                    else -> UnlockScreen(session, SecretKind.PATTERN)
                }
            }
        }
        listOf("1-list", "2-id-card", "3-locked").forEachIndexed { i, name ->
            screen.intValue = i
            rule.waitForIdle(); Thread.sleep(800); rule.waitForIdle()
            save(name)
        }
        repo.close()
    }

    /** The 512 x 512 store icon: the launcher foreground on the icon colour. */
    @Test fun icon() {
        val bmp = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(ContextCompat.getColor(context, R.color.icon_background))
        ContextCompat.getDrawable(context, R.drawable.ic_launcher_foreground)!!.apply { setBounds(-64, -64, 576, 576); draw(canvas) }
        File(out, "icon-512.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    /** The 1024 x 500 feature graphic. */
    @Test fun featureGraphic() {
        rule.setContent {
            val d = LocalDensity.current
            with(d) { FeatureGraphic(Modifier.size(1024.toDp(), 500.toDp())) }
        }
        rule.waitForIdle()
        save("feature-1024x500")
    }

    @Composable
    private fun FeatureGraphic(modifier: Modifier) {
        val scale = LocalDensity.current.run { 1.toDp().value } // px-independent sizes
        Box(
            modifier.background(Brush.linearGradient(listOf(Color(0xFF3B2A8C), Color(0xFF9C3E78)))),
            contentAlignment = Alignment.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy((40 * scale).dp)) {
                Image(painterResource(R.drawable.ic_launcher_foreground), contentDescription = null, modifier = Modifier.size((260 * scale).dp))
                Column(Modifier.width((560 * scale).dp)) {
                    Text("KS Vault", color = Color.White, fontSize = (84 * scale).sp, fontWeight = FontWeight.Bold)
                    Text("Notes, ID cards and tickets.\nEncrypted. Offline. Yours.", color = Color.White, fontSize = (38 * scale).sp, lineHeight = (50 * scale).sp)
                }
            }
        }
    }
}
