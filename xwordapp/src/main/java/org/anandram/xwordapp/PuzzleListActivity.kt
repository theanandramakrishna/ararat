package org.anandram.xwordapp

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
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

import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.tasks.Task
import com.google.api.client.http.ByteArrayContent
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.api.services.drive.model.File as DriveFile
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.gson.Gson

class PuzzleListActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "PuzzleList"
        private const val RC_SIGN_IN = 9001
        private const val RC_PICK_PUZZLE = 9002
        private const val LIST_FILE_NAME = "puzzles.json"
    }

    private lateinit var listView: ListView
    private lateinit var adapter: PuzzleListAdapter

    // Google Drive
    private lateinit var googleSignInClient: GoogleSignInClient
    private var driveService: Drive? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_puzzle_list)

        PuzzleManager.init(this)
        title = getString(R.string.app_name)

        setupSignIn()

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
            R.id.menu_sign_in_drive -> signInToDrive()
            R.id.menu_save_drive -> saveToDrive()
            R.id.menu_load_drive -> loadFromDrive()
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
            RC_SIGN_IN -> {
                val task = GoogleSignIn.getSignedInAccountFromIntent(data)
                handleSignInResult(task)
            }
        }
    }

    // Google Drive sign-in
    private fun setupSignIn() {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestScopes(com.google.android.gms.common.api.Scope(DriveScopes.DRIVE_APPDATA))
                .requestScopes(com.google.android.gms.common.api.Scope(DriveScopes.DRIVE_FILE))
                .requestEmail()
                .build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)

        val account = GoogleSignIn.getLastSignedInAccount(this)
        if (account != null) {
            setupDriveService(account)
        }
    }

    private fun signInToDrive() {
        val signInIntent = googleSignInClient.signInIntent
        startActivityForResult(signInIntent, RC_SIGN_IN)
    }

    private fun setupDriveService(account: GoogleSignInAccount) {
        val credential = GoogleAccountCredential.usingOAuth2(
                this, listOf(DriveScopes.DRIVE_FILE, DriveScopes.DRIVE_APPDATA))
        credential.selectedAccount = account.account
        driveService = Drive.Builder(
                NetHttpTransport(),
                GsonFactory(),
                credential)
                .setApplicationName("Crossword App")
                .build()
    }

    private fun handleSignInResult(completedTask: Task<GoogleSignInAccount>) {
        try {
            val account = completedTask.getResult(ApiException::class.java)
            setupDriveService(account)
            Toast.makeText(this,
                    getString(R.string.signed_in_as, account.email),
                    Toast.LENGTH_SHORT).show()
        } catch (e: ApiException) {
            Toast.makeText(this, R.string.sign_in_failed, Toast.LENGTH_SHORT).show()
            FirebaseCrashlytics.getInstance().recordException(e)
        }
    }

    // Google Drive sync of the puzzle list, .puz contents and progress states
    private fun saveToDrive() {
        if (driveService == null) {
            Toast.makeText(this, R.string.please_sign_in, Toast.LENGTH_SHORT).show()
            return
        }

        val service = driveService!!

        Thread {
            try {
                val entries = PuzzleManager.getPuzzles()

                // Upload the list metadata
                uploadFile(service, LIST_FILE_NAME,
                        ByteArrayContent("application/json", PuzzleManager.listJson().toByteArray()))

                // Upload each puzzle's .puz and, if present, its progress state
                for (entry in entries) {
                    uploadFile(service, entry.fileName,
                            ByteArrayContent("application/octet-stream",
                                    PuzzleManager.puzFile(entry.id).readBytes()))
                    val stateFile = PuzzleManager.stateFile(entry.id)
                    if (stateFile.exists()) {
                        uploadFile(service, stateFile.name,
                                ByteArrayContent("application/octet-stream", stateFile.readBytes()))
                    }
                }

                runOnUiThread {
                    Toast.makeText(this, R.string.saved_to_drive, Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Save to Drive failed", e)
                runOnUiThread {
                    Toast.makeText(this, R.string.drive_save_failed, Toast.LENGTH_SHORT).show()
                    FirebaseCrashlytics.getInstance().recordException(e)
                }
            }
        }.start()
    }

    private fun loadFromDrive() {
        if (driveService == null) {
            Toast.makeText(this, R.string.please_sign_in, Toast.LENGTH_SHORT).show()
            return
        }

        val service = driveService!!

        Thread {
            try {
                val listContent = downloadFile(service, LIST_FILE_NAME)
                        ?: run {
                            runOnUiThread {
                                Toast.makeText(this, R.string.no_list_on_drive,
                                        Toast.LENGTH_SHORT).show()
                            }
                            return@Thread
                        }

                val entries = Gson().fromJson(
                        String(listContent), Array<PuzzleEntry>::class.java)
                        ?.toList()
                        ?: emptyList()

                for (entry in entries) {
                    val puz = downloadFile(service, entry.fileName) ?: continue
                    PuzzleManager.writePuz(entry.id, puz)

                    val state = downloadFile(service, "${entry.id}.state")
                    if (state != null) {
                        PuzzleManager.writeState(entry.id, state)
                    }
                }

                PuzzleManager.saveList(entries)

                runOnUiThread {
                    refreshList()
                    Toast.makeText(this, R.string.loaded_from_drive, Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Load from Drive failed", e)
                runOnUiThread {
                    Toast.makeText(this, R.string.drive_load_failed, Toast.LENGTH_SHORT).show()
                    FirebaseCrashlytics.getInstance().recordException(e)
                }
            }
        }.start()
    }

    private fun uploadFile(service: Drive, name: String, content: ByteArrayContent) {
        val query = "name='$name' and trashed=false"
        val fileList = service.files().list()
                .setSpaces("appDataFolder")
                .setQ(query)
                .execute()

        val fileId = if (fileList.files.isNotEmpty()) {
            fileList.files[0].id
        } else {
            val metadata = DriveFile().setName(name).setParents(listOf("appDataFolder"))
            service.files().create(metadata).execute().id
        }

        service.files().update(fileId, DriveFile(), content).execute()
    }

    private fun downloadFile(service: Drive, name: String): ByteArray? {
        val fileList = service.files().list()
                .setSpaces("appDataFolder")
                .setQ("name='$name' and trashed=false")
                .execute()
        if (fileList.files.isEmpty()) return null

        val fileId = fileList.files[0].id
        return service.files().get(fileId).executeMediaAsInputStream().use { it.readBytes() }
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