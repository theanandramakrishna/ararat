package org.anandram.xwordapp

import android.content.Intent
import android.view.View
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Smoke test: the bundled puzzle opens on a real device and its crossword
 * grid renders. Uses the bundled entry so no setup or cleanup is needed,
 * and it carries no CWF binding so no network is touched.
 *
 * Assertions run via [ActivityScenario.onActivity] (UI thread, no event
 * injection): Espresso cannot run on this device's API level because its
 * hidden-API access to `InputManager.getInstance()` was removed.
 */
@RunWith(AndroidJUnit4::class)
class MainActivityDeviceTest {

    @Test
    fun bundledPuzzleOpensAndGridRenders() {
        val intent = Intent(
                ApplicationProvider.getApplicationContext(),
                MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_PUZZLE_ID, PuzzleManager.getBundledId())
        ActivityScenario.launch<MainActivity>(intent).use { scenario ->
            // launch() returns before resume; the window isn't attached (and
            // isShown() is false) until RESUMED, so wait for it explicitly.
            scenario.moveToState(Lifecycle.State.RESUMED)
            scenario.onActivity { activity ->
                val grid = activity.findViewById<View>(R.id.crossword)
                assertNotNull("crossword view missing", grid)
                assertTrue("crossword view not shown", grid.isShown)
            }
        }
    }
}
