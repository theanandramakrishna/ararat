package org.anandram.xwordapp

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.navigation.NavigationView
import com.google.android.material.tabs.TabLayout

import java.io.ByteArrayInputStream
import java.text.DateFormat
import java.util.Date

class PuzzleListActivity : AppCompatActivity() {

    companion object {
        private const val RC_PICK_PUZZLE = 9002
        private const val TAG = "PuzzleListActivity"

        private const val TAB_ALL = 0
        private const val TAB_UNSOLVED = 1
        private const val TAB_SOLVED = 2
        private const val TAB_BY_SOURCE = 3

        @Volatile private var liveGameProbeDone = false

        private val URL_REGEX = Regex("https?://\\S+")
    }

    private lateinit var listView: ListView
    private lateinit var tabLayout: TabLayout
    private lateinit var toolbar: Toolbar
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var puzzleAdapter: PuzzleListAdapter
    private lateinit var subscriptionAdapter: ArrayAdapter<Subscription>
    private lateinit var driveManager: DriveManager

    private var currentSource: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_puzzle_list)
        applySystemBarInsets()

        PuzzleManager.init(this)
        SubscriptionManager.init(this)
        title = getString(R.string.app_name)

        driveManager = DriveManager(this)
        driveManager.setupSignIn()

        listView = findViewById(R.id.puzzle_list)
        tabLayout = findViewById(R.id.tabs)
        toolbar = findViewById(R.id.toolbar)
        drawerLayout = findViewById(R.id.drawer_layout)
        val navigationView = findViewById<NavigationView>(R.id.nav_view)

        setSupportActionBar(toolbar)
        toolbar.setNavigationOnClickListener {
            if (currentSource == null) {
                drawerLayout.openDrawer(GravityCompat.START)
            } else {
                currentSource = null
                renderBySource()
                updateActionBar()
            }
        }
        navigationView.setNavigationItemSelectedListener { item ->
            drawerLayout.closeDrawer(GravityCompat.START)
            when (item.itemId) {
                R.id.menu_add_puzzle -> pickPuzzleFile()
                R.id.menu_join_cwf -> showJoinGameDialog()
                R.id.menu_subscriptions ->
                    startActivity(Intent(this, SubscriptionsActivity::class.java))
                R.id.menu_sign_in_drive -> driveManager.signIn()
                R.id.menu_settings -> startActivity(Intent(this, SettingsActivity::class.java))
            }
            true
        }
        if (!BuildConfig.DEBUG) {
            navigationView.menu.findItem(R.id.menu_sign_in_drive).isVisible = false
        }

        puzzleAdapter = PuzzleListAdapter(this, mutableListOf())
        subscriptionAdapter = SubscriptionAdapter(this, mutableListOf())

        listView.setOnItemClickListener { _, _, position, _ ->
            if (listView.adapter === subscriptionAdapter) {
                val subscription = subscriptionAdapter.getItem(position)
                        ?: return@setOnItemClickListener
                currentSource = subscription.name
                renderBySource()
                updateActionBar()
            } else {
                val entry = puzzleAdapter.getItem(position) ?: return@setOnItemClickListener
                val intent = Intent(this, MainActivity::class.java)
                        .putExtra(MainActivity.EXTRA_PUZZLE_ID, entry.id)
                startActivity(intent)
            }
        }

        listOf(R.string.tab_all, R.string.tab_unsolved, R.string.tab_solved,
                R.string.tab_by_source).forEach {
            tabLayout.addTab(tabLayout.newTab().setText(it))
        }
        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) = renderTab(tab.position)
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {
                if (tab.position == TAB_BY_SOURCE) {
                    currentSource = null
                    renderTab(tab.position)
                    updateActionBar()
                }
            }
        })
        renderTab(TAB_ALL)
        updateActionBar()
        updateSessionKeys()
        handleShareIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleShareIntent(intent)
    }

    private fun handleShareIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND) return
        val text = intent.getStringExtra(Intent.EXTRA_TEXT)
                ?: intent.getStringExtra(Intent.EXTRA_TITLE) ?: return
        Log.i(TAG, "Share received: $text")
        FirebaseStats.log("share_received")
        joinGameFromShare(text)
    }

    private fun joinGameFromShare(text: String) {
        for (urlMatch in URL_REGEX.findAll(text)) {
            val url = urlMatch.value.trimEnd(')', '.', ',', '>')
            val gid = CrossWithFriendsSubscription.gidFromShareUrl(url)
            if (gid != null) {
                Log.i(TAG, "Share url=$url -> gid=$gid")
                FirebaseStats.logEvent(FirebaseStats.EVENT_JOIN_GAME_SHARE_RECEIVED,
                        mapOf("gid" to gid.take(8)))
                importCwfGame(gid, navigateOnJoin = true)
                return
            }
            Log.w(TAG, "Share url rejected: $url")
        }
        Toast.makeText(this, R.string.cwf_cannot_join, Toast.LENGTH_SHORT).show()
    }

    /** Keeps Crashlytics session keys current for the puzzle library. */
    private fun updateSessionKeys() {
        val puzzles = PuzzleManager.getPuzzles()
        FirebaseStats.setCustomKey("num_puzzles", puzzles.size.toLong())
        FirebaseStats.setCustomKey("cwf_games_joined",
                puzzles.count { it.cwfGid != null }.toLong())
    }

    override fun onResume() {
        super.onResume()
        updateSessionKeys()
        refreshList()
        probeLiveGames()
    }

    /** Once per session, verifies every joined CWF room still exists. Rooms the
     *  server reports as gone are unbound from their puzzle (the entry stops
     *  showing "Live game in progress"). Outcomes that are merely unknown
     *  (connect error/timeout) leave the binding untouched. */
    private fun probeLiveGames() {
        if (liveGameProbeDone) return
        liveGameProbeDone = true
        val live = PuzzleManager.getPuzzles().filter { it.cwfGid != null }
        Log.i(TAG, "probing ${live.size} live CWF game(s)")
        for (entry in live) {
            val gid = entry.cwfGid ?: continue
            CrossWithFriendsSubscription.verifyGameExists(gid) { exists ->
                if (exists == false) {
                    Log.w(TAG, "CWF game room gone for ${entry.title} (${gid.take(8)}); clearing binding")
                    FirebaseStats.log("cwf_probe_gone ${gid.take(8)}")
                    runOnUiThread {
                        PuzzleManager.setCwfGame(entry.id, null, null)
                        refreshList()
                    }
                }
            }
        }
    }

    private fun refreshList() {
        val position = tabLayout.selectedTabPosition
        renderTab(if (position >= 0) position else TAB_ALL)
    }

    private fun renderTab(position: Int) {
        when (position) {
            TAB_ALL -> showPuzzles(PuzzleManager.getPuzzles())
            TAB_UNSOLVED -> showPuzzles(
                    PuzzleManager.getPuzzles().filter { PuzzleManager.solvedPercent(it.id) < 100 })
            TAB_SOLVED -> showPuzzles(
                    PuzzleManager.getPuzzles().filter { PuzzleManager.solvedPercent(it.id) >= 100 })
            else -> renderBySource()
        }
    }

    private fun renderBySource() {
        val source = currentSource
        if (source == null) {
            val puzzles = PuzzleManager.getPuzzles()
            val puzzlesBySource = puzzles.groupBy { it.source }.keys
            subscriptionAdapter.clear()
            if (puzzles.any { it.source == null }) {
                subscriptionAdapter.add(
                        Subscription(name = getString(R.string.manually_added)))
            }
            subscriptionAdapter.addAll(SubscriptionManager.getSubscriptions()
                    .filter { it.name in puzzlesBySource })
            listView.adapter = subscriptionAdapter
        } else if (source == getString(R.string.manually_added)) {
            showPuzzles(PuzzleManager.getPuzzles().filter { it.source == null })
        } else {
            showPuzzles(PuzzleManager.getPuzzles().filter { it.source == source })
        }
    }

    private fun showPuzzles(puzzles: List<PuzzleEntry>) {
        puzzleAdapter.clear()
        puzzleAdapter.addAll(puzzles)
        listView.adapter = puzzleAdapter
    }

    private fun updateActionBar() {
        val source = currentSource
        if (source == null) {
            toolbar.setNavigationIcon(R.drawable.ic_menu)
            drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED)
        } else {
            toolbar.setNavigationIcon(R.drawable.ic_arrow_back)
            drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
        }
        title = source ?: getString(R.string.app_name)
    }

    private fun pickPuzzleFile() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }
        startActivityForResult(intent, RC_PICK_PUZZLE)
    }

    /**
     * Asks for a Cross With Friends game URL (pre-filled with the game
     * prefix), then imports the puzzle behind that room.
     */
    private fun showJoinGameDialog() {
        val input = EditText(this)
        val padding = (16 * resources.displayMetrics.density).toInt()
        input.setPadding(padding, padding, padding, padding)
        input.setText(CrossWithFriendsSubscription.GAME_URL_PREFIX)

        AlertDialog.Builder(this)
                .setTitle(R.string.game_url)
                .setView(input)
                .setPositiveButton(R.string.ok) { _, _ ->
                    val url = input.text?.toString()?.trim().orEmpty()
                    val gid = CrossWithFriendsSubscription.gidFromGameUrl(url)
                    if (gid == null) {
                        Toast.makeText(this, R.string.cwf_invalid_game_url,
                                Toast.LENGTH_SHORT).show()
                    } else {
                        importCwfGame(gid)
                    }
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
    }

    private fun importCwfGame(gid: String, navigateOnJoin: Boolean = false) {
        Log.i(TAG, "cwf import start gid=${gid.take(8)} navigate=$navigateOnJoin")
        Toast.makeText(this, R.string.cwf_import_started, Toast.LENGTH_SHORT).show()
        FirebaseStats.logEvent(FirebaseStats.EVENT_JOIN_GAME_START,
                mapOf("gid" to gid.take(8), "from_share" to navigateOnJoin))

        val url = CrossWithFriendsSubscription.gameUrl(gid)
        CrossWithFriendsSubscription.importGame(gid, url) { entry, duplicate ->
            val outcome = when {
                entry == null -> "failed"
                duplicate -> "duplicate"
                else -> "succeeded"
            }
            Log.i(TAG, "cwf import done gid=${gid.take(8)} outcome=$outcome")
            FirebaseStats.log("cwf_import_done ${gid.take(8)} $outcome")
            FirebaseStats.logEvent(
                    when {
                        entry == null -> FirebaseStats.EVENT_JOIN_GAME_FAILED
                        duplicate -> FirebaseStats.EVENT_JOIN_GAME_DUPLICATE
                        else -> FirebaseStats.EVENT_JOIN_GAME_SUCCEEDED
                    },
                    mapOf("gid" to gid.take(8), "from_share" to navigateOnJoin))
            runOnUiThread {
                val toastRes = when {
                    entry == null ->
                        if (navigateOnJoin) R.string.cwf_cannot_join
                        else R.string.cwf_import_failed
                    duplicate -> R.string.cwf_import_duplicate
                    else -> R.string.cwf_imported
                }
                Log.i(TAG, "cwf import toast res=$toastRes")
                Toast.makeText(this, toastRes, Toast.LENGTH_SHORT).show()
                if (entry != null && navigateOnJoin) {
                    startActivity(Intent(this, MainActivity::class.java)
                            .putExtra(MainActivity.EXTRA_PUZZLE_ID, entry.id))
                } else {
                    refreshList()
                }
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        when (requestCode) {
            RC_PICK_PUZZLE -> {
                if (resultCode == Activity.RESULT_OK) {
                    data?.data?.let { uri ->
                        val fileName = displayName(uri)
                        val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        val format = bytes?.let { PuzzleManager.detectFormat(fileName, it) } ?: "puz"
                        val added = bytes?.let {
                            PuzzleManager.addPuzzle(ByteArrayInputStream(it),
                                    format = format, downloadUrl = "file:$fileName")
                        }
                        if (added == null) {
                            Toast.makeText(this, R.string.add_failed, Toast.LENGTH_SHORT).show()
                        }
                    }
                    refreshList()
                }
            }
            DriveManager.RC_SIGN_IN -> driveManager.handleSignInResult(requestCode, data)
        }
    }

    private fun displayName(uri: Uri): String {
        return try {
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index >= 0) cursor.getString(index) else ""
                } else {
                    ""
                }
            } ?: ""
        } catch (e: Exception) {
            ""
        }
    }

    private class SubscriptionAdapter(
            context: Context,
            objects: List<Subscription>) : ArrayAdapter<Subscription>(context, 0, objects) {

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = convertView ?: LayoutInflater.from(context)
                    .inflate(android.R.layout.simple_list_item_1, parent, false)
            val textView = view.findViewById<TextView>(android.R.id.text1)
            textView.text = getItem(position)?.name
            return view
        }
    }

    private class PuzzleListAdapter(
            context: Context,
            objects: List<PuzzleEntry>) : ArrayAdapter<PuzzleEntry>(context, 0, objects) {

        private val dateFormat = DateFormat.getDateInstance(DateFormat.MEDIUM)

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = convertView ?: LayoutInflater.from(context)
                    .inflate(R.layout.item_puzzle, parent, false)

            val entry = getItem(position) ?: return view
            view.findViewById<TextView>(R.id.puzzle_title).text = entry.title
            view.findViewById<TextView>(R.id.puzzle_author).text = entry.author

            val modified = entry.modified.takeIf { it > 0 }
                    ?: PuzzleManager.puzzleFile(entry.id, entry.format).lastModified()
            val modifiedText = view.findViewById<TextView>(R.id.puzzle_modified)
            modifiedText.text = if (modified > 0) {
                dateFormat.format(Date(modified))
            } else {
                ""
            }

            val progressText = view.findViewById<TextView>(R.id.puzzle_progress)
            val percent = PuzzleManager.solvedPercent(entry.id)
            progressText.text = if (percent > 0) {
                context.getString(R.string.solved_percent, percent)
            } else {
                ""
            }

            view.findViewById<TextView>(R.id.puzzle_time).text =
                    PuzzleManager.formatTime(PuzzleManager.getTimeSpent(entry.id))

            view.findViewById<TextView>(R.id.puzzle_live).visibility =
                    if (entry.cwfGid != null) View.VISIBLE else View.INVISIBLE

            return view
        }
    }
}