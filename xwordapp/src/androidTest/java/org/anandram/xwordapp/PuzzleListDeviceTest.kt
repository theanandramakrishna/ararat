package org.anandram.xwordapp

import android.view.View
import android.widget.ListView
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Smoke test: the puzzle list launches on a real device and renders its
 * list view through the full [PuzzleListActivity.onCreate] path (manager
 * init, Drive sign-in setup, tabs, live-game probe with no live games).
 *
 * Assertions run via [ActivityScenario.onActivity] (UI thread, no event
 * injection): Espresso cannot run on this device's API level because its
 * hidden-API access to `InputManager.getInstance()` was removed.
 */
@RunWith(AndroidJUnit4::class)
class PuzzleListDeviceTest {

    @Test
    fun listLaunchesAndRenders() {
        ActivityScenario.launch(PuzzleListActivity::class.java).use { scenario ->
            // launch() returns before resume; the window isn't attached (and
            // isShown() is false) until RESUMED, so wait for it explicitly.
            scenario.moveToState(Lifecycle.State.RESUMED)
            scenario.onActivity { activity ->
                val list = activity.findViewById<ListView>(R.id.puzzle_list)
                assertNotNull("puzzle_list missing", list)
                assertTrue("puzzle_list not shown", list.isShown)
                assertTrue("puzzle_list has no adapter", list.adapter != null)
            }
        }
    }
}
