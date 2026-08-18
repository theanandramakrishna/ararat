package org.anandram.xwordapp

import android.app.Activity
import android.content.Context
import android.content.Intent
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

import java.text.DateFormat
import java.util.Date

class PuzzleListActivity : AppCompatActivity() {

    companion object {
        private const val RC_PICK_PUZZLE = 9002
    }

    private lateinit var listView: ListView
    private lateinit var adapter: PuzzleListAdapter
    private lateinit var driveManager: DriveManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_puzzle_list)

        PuzzleManager.init(this)
        title = getString(R.string.app_name)

        driveManager = DriveManager(this)
        driveManager.setupSignIn()

        listView = findViewById(R.id.puzzle_list)
        adapter = PuzzleListAdapter(this, PuzzleManager.getPuzzles())
        listView.adapter = adapter
        listView.setOnItemClickListener { _, _, position, _ ->
            val entry = adapter.getItem(position) ?: return@setOnItemClickListener
            val intent = Intent(this, MainActivity::class.java)
                    .putExtra(MainActivity.EXTRA_PUZZLE_ID, entry.id)
            startActivity(intent)
        }
    }

    override fun onResume() {
        super.onResume()
        refreshList()
    }

    private fun refreshList() {
        val puzzles = PuzzleManager.getPuzzles()
        adapter.clear()
        adapter.addAll(puzzles)
        adapter.notifyDataSetChanged()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.activity_puzzle_list, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
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
                        val added = contentResolver.openInputStream(uri)?.let { input ->
                            PuzzleManager.addPuzzle(input)
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

            return view
        }
    }
}