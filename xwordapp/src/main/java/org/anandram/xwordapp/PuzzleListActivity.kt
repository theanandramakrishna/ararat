package org.anandram.xwordapp

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.tabs.TabLayout

import java.text.DateFormat
import java.util.Date

class PuzzleListActivity : AppCompatActivity() {

    companion object {
        private const val RC_PICK_PUZZLE = 9002

        private const val TAB_ALL = 0
        private const val TAB_UNSOLVED = 1
        private const val TAB_SOLVED = 2
        private const val TAB_BY_SOURCE = 3
    }

    private lateinit var listView: ListView
    private lateinit var tabLayout: TabLayout
    private lateinit var puzzleAdapter: PuzzleListAdapter
    private lateinit var subscriptionAdapter: ArrayAdapter<Subscription>
    private lateinit var driveManager: DriveManager

    private var currentSource: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_puzzle_list)

        PuzzleManager.init(this)
        SubscriptionManager.init(this)
        title = getString(R.string.app_name)

        driveManager = DriveManager(this)
        driveManager.setupSignIn()

        listView = findViewById(R.id.puzzle_list)
        tabLayout = findViewById(R.id.tabs)

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
    }

    override fun onResume() {
        super.onResume()
        refreshList()
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
            subscriptionAdapter.clear()
            subscriptionAdapter.addAll(SubscriptionManager.getSubscriptions())
            listView.adapter = subscriptionAdapter
        } else {
            showPuzzles(PuzzleManager.getPuzzles().filter { it.source == source })
        }
    }

    private fun showPuzzles(puzzles: List<PuzzleEntry>) {
        puzzleAdapter.clear()
        puzzleAdapter.addAll(puzzles)
        listView.adapter = puzzleAdapter
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.activity_puzzle_list, menu)
        return true
    }

    private fun updateActionBar() {
        val source = currentSource
        supportActionBar?.setDisplayHomeAsUpEnabled(source != null)
        title = source ?: getString(R.string.app_name)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            if (currentSource != null) {
                currentSource = null
                renderBySource()
                updateActionBar()
            } else {
                finish()
            }
            return true
        }

        when (item.itemId) {
            R.id.menu_add_puzzle -> pickPuzzleFile()
            R.id.menu_sign_in_drive -> driveManager.signIn()
            R.id.menu_settings -> startActivity(Intent(this, SettingsActivity::class.java))
            else -> return super.onOptionsItemSelected(item)
        }

        return true
    }

    private fun pickPuzzleFile() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }
        startActivityForResult(intent, RC_PICK_PUZZLE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        when (requestCode) {
            RC_PICK_PUZZLE -> {
                if (resultCode == Activity.RESULT_OK) {
                    data?.data?.let { uri ->
                        val fileName = displayName(uri)
                        val added = contentResolver.openInputStream(uri)?.let { input ->
                            PuzzleManager.addPuzzle(input, downloadUrl = "file:$fileName")
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
                    ?: PuzzleManager.puzFile(entry.id).lastModified()
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

            return view
        }
    }
}