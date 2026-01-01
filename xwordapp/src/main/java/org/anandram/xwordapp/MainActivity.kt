// Copyright (c) Anand Ramakrishna
//
// Permission is hereby granted, free of charge, to any person obtaining a copy
// of this software and associated documentation files (the "Software"), to deal
// in the Software without restriction, including without limitation the rights
// to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
// copies of the Software, and to permit persons to whom the Software is
// furnished to do so, subject to the following conditions:
//
// The above copyright notice and this permission notice shall be included in all
// copies or substantial portions of the Software.
//
// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
// IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
// FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
// AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
// LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
// OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
// SOFTWARE.

package org.anandram.xwordapp

import android.content.Intent
import androidx.annotation.RawRes
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.TextView
import android.widget.Toast

import org.akop.ararat.core.Crossword
import org.akop.ararat.core.buildCrossword
import org.akop.ararat.io.PuzFormatter
import org.akop.ararat.view.CrosswordView

// Google Drive imports
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.tasks.Task
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.api.services.drive.model.File as DriveFile
import org.akop.ararat.core.CrosswordStateWriter
import org.akop.ararat.core.CrosswordStateReader
import java.io.ByteArrayOutputStream
import com.google.api.client.http.ByteArrayContent


// Crossword: Double-A's by Ben Tausig
// http://www.inkwellxwords.com/iwxpuzzles.html
class MainActivity : AppCompatActivity(), CrosswordView.OnLongPressListener, CrosswordView.OnStateChangeListener, CrosswordView.OnSelectionChangeListener {

    private lateinit var crosswordView: CrosswordView
    private var hint: TextView? = null

    // Google Drive
    private lateinit var googleSignInClient: GoogleSignInClient
    private var driveService: Drive? = null
    private val RC_SIGN_IN = 9001
    private val STATE_FILE_NAME = "crossword_state.dat"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

        // Initialize Google Sign-In
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestScopes(com.google.android.gms.common.api.Scope(DriveScopes.DRIVE_FILE))
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)

        // Check if already signed in
        val account = GoogleSignIn.getLastSignedInAccount(this)
        if (account != null) {
            setupDriveService(account)
        }

        crosswordView = findViewById(R.id.crossword)
        hint = findViewById(R.id.hint)

        val puzzle = readPuzzle(R.raw.puzzle)

        title = getString(R.string.title_by_author,
                puzzle.title, puzzle.author)

        with (crosswordView) {
            crossword = puzzle
            onLongPressListener = this@MainActivity
            onStateChangeListener = this@MainActivity
            onSelectionChangeListener = this@MainActivity
            inputValidator = { ch -> !ch.first().isISOControl() }
            undoMode = CrosswordView.UNDO_NONE
            markerDisplayMode = CrosswordView.MARKER_CHEAT
        }
        onSelectionChanged(crosswordView,
                crosswordView.selectedWord,
                crosswordView.selectedCell)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)

        crosswordView.restoreState(savedInstanceState.getParcelable("state")!!)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        outState.putParcelable("state", crosswordView.state)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.activity_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_restart -> crosswordView.reset()
            R.id.menu_solve_cell -> crosswordView.solveChar(
                    crosswordView.selectedWord!!,
                    crosswordView.selectedCell)
            R.id.menu_solve_word -> crosswordView.solveWord(
                    crosswordView.selectedWord!!)
            R.id.menu_solve_puzzle -> crosswordView.solveCrossword()
            R.id.menu_sign_in_drive -> signInToDrive()
            R.id.menu_save_drive -> saveStateToDrive()
            R.id.menu_load_drive -> loadStateFromDrive()
            else -> return super.onOptionsItemSelected(item)
        }

        return true
    }

    override fun onCellLongPressed(view: CrosswordView,
                                   word: Crossword.Word, cell: Int) {
        Toast.makeText(this, "Show popup menu for " + word.hint!!,
                Toast.LENGTH_SHORT).show()
    }

    override fun onCrosswordChanged(view: CrosswordView) {}

    override fun onCrosswordSolved(view: CrosswordView) {
        Toast.makeText(this, R.string.youve_solved_the_puzzle,
                Toast.LENGTH_SHORT).show()
    }

    override fun onCrosswordUnsolved(view: CrosswordView) { }

    private fun readPuzzle(@RawRes resourceId: Int): Crossword =
            resources.openRawResource(resourceId).use { s ->
                buildCrossword { PuzFormatter().read(this, s) }
            }

    override fun onSelectionChanged(view: CrosswordView,
                                    word: Crossword.Word?, position: Int) {
        hint!!.text = when (word?.direction) {
            Crossword.Word.DIR_ACROSS -> getString(R.string.across, word.number, word.hint)
            Crossword.Word.DIR_DOWN -> getString(R.string.down, word.number, word.hint)
            else -> ""
        }
    }

    // Google Drive methods
    private fun signInToDrive() {
        val signInIntent = googleSignInClient.signInIntent
        startActivityForResult(signInIntent, RC_SIGN_IN)
    }

    private fun setupDriveService(account: GoogleSignInAccount) {
        val credential = GoogleAccountCredential.usingOAuth2(
            this, listOf(DriveScopes.DRIVE_FILE)
        )
        credential.selectedAccount = account.account
        driveService = Drive.Builder(
            NetHttpTransport(),
            GsonFactory(),
            credential
        ).setApplicationName("Crossword App").build()
    }

    private fun saveStateToDrive() {
        if (driveService == null) {
            Toast.makeText(this, "Please sign in to Google Drive first", Toast.LENGTH_SHORT).show()
            return
        }

        // Serialize state
        val state = crosswordView.state
        if (state == null) {
            Toast.makeText(this, "No state to save", Toast.LENGTH_SHORT).show()
            return
        }
        val baos = ByteArrayOutputStream()
        val writer = CrosswordStateWriter(baos)
        writer.write(state)
        writer.close()
        val data = baos.toByteArray()

        // Find existing file or create new
        Thread {
            try {
                val fileList = driveService!!.files().list()
                    .setQ("name='$STATE_FILE_NAME' and trashed=false")
                    .execute()
                val fileId = if (fileList.files.isNotEmpty()) {
                    fileList.files[0].id
                } else {
                    // Create new file
                    val fileMetadata = DriveFile().setName(STATE_FILE_NAME)
                    driveService!!.files().create(fileMetadata).execute().id
                }

                // Update file content
                val content = ByteArrayContent(null, data)
                driveService!!.files().update(fileId, DriveFile(), content).execute()
                runOnUiThread {
                    Toast.makeText(this, "State saved to Drive", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this, "Failed to save: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    private fun loadStateFromDrive() {
        if (driveService == null) {
            Toast.makeText(this, "Please sign in to Google Drive first", Toast.LENGTH_SHORT).show()
            return
        }

        Thread {
            try {
                val fileList = driveService!!.files().list()
                    .setQ("name='$STATE_FILE_NAME' and trashed=false")
                    .execute()
                if (fileList.files.isEmpty()) {
                    runOnUiThread {
                        Toast.makeText(this, "No saved state found", Toast.LENGTH_SHORT).show()
                    }
                    return@Thread
                }

                val fileId = fileList.files[0].id
                val inputStream = driveService!!.files().get(fileId).executeMediaAsInputStream()
                if (inputStream == null) {
                    runOnUiThread {
                        Toast.makeText(this, "Failed to get input stream", Toast.LENGTH_SHORT).show()
                    }
                    return@Thread
                }
                val reader = CrosswordStateReader(inputStream)
                val state = reader.read()
                reader.close()

                runOnUiThread {
                    crosswordView.restoreState(state)
                    Toast.makeText(this, "State loaded from Drive", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this, "Failed to load: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == RC_SIGN_IN) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            handleSignInResult(task)
        }
    }

    private fun handleSignInResult(completedTask: Task<GoogleSignInAccount>) {
        try {
            val account = completedTask.getResult(ApiException::class.java)
            setupDriveService(account)
            Toast.makeText(this, "Signed in as ${account.email}", Toast.LENGTH_SHORT).show()
        } catch (e: ApiException) {
            Toast.makeText(this, "Sign-in failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
