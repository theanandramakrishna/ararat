package org.anandram.xwordapp

import android.content.Intent
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.google.api.client.http.ByteArrayContent
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.api.services.drive.model.File as DriveFile
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.gson.Gson
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class DriveManager(private val activity: AppCompatActivity) {

    companion object {
        private const val TAG = "DriveManager"
        const val RC_SIGN_IN = 9001
        private const val LIST_FILE_NAME = "puzzles.json"
        private const val BACKUP_FILE_NAME = "xwordapp-backup.zip"
    }

    private lateinit var googleSignInClient: GoogleSignInClient
    private var driveService: Drive? = null
    private var pendingAction: (() -> Unit)? = null

    fun setupSignIn() {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestScopes(Scope(DriveScopes.DRIVE_APPDATA))
                .requestScopes(Scope(DriveScopes.DRIVE_FILE))
                .requestEmail()
                .build()
        googleSignInClient = GoogleSignIn.getClient(activity, gso)

        GoogleSignIn.getLastSignedInAccount(activity)?.let { setupDriveService(it) }
    }

    fun signIn() {
        activity.startActivityForResult(googleSignInClient.signInIntent, RC_SIGN_IN)
    }

    fun handleSignInResult(requestCode: Int, data: Intent?): Boolean {
        if (requestCode != RC_SIGN_IN) return false

        try {
            val account = GoogleSignIn.getSignedInAccountFromIntent(data)
                    .getResult(ApiException::class.java)
            setupDriveService(account)
            Toast.makeText(activity,
                    activity.getString(R.string.signed_in_as, account.email),
                    Toast.LENGTH_SHORT).show()

            val pending = pendingAction
            pendingAction = null
            pending?.invoke()
        } catch (e: ApiException) {
            pendingAction = null
            Toast.makeText(activity, R.string.sign_in_failed, Toast.LENGTH_SHORT).show()
            FirebaseCrashlytics.getInstance().recordException(e)
        }

        return true
    }

    fun saveToDrive(onComplete: (Int) -> Unit) {
        ensureSignedIn {
            Thread {
                try {
                    val service = driveService!!
                    uploadFile(service, BACKUP_FILE_NAME,
                            ByteArrayContent("application/zip", buildBackupZip()))

                    activity.runOnUiThread { onComplete(R.string.saved_to_drive) }
                } catch (e: Exception) {
                    Log.e(TAG, "Save to Drive failed", e)
                    activity.runOnUiThread {
                        onComplete(R.string.drive_save_failed)
                        FirebaseCrashlytics.getInstance().recordException(e)
                    }
                }
            }.start()
        }
    }

    fun loadFromDrive(onComplete: (Int) -> Unit) {
        ensureSignedIn {
            Thread {
                try {
                    val service = driveService!!
                    val zipBytes = downloadFile(service, BACKUP_FILE_NAME)
                            ?: run {
                                activity.runOnUiThread { onComplete(R.string.no_backup_on_drive) }
                                return@Thread
                            }

                    val contents = unzip(zipBytes)

                    val listContent = contents[LIST_FILE_NAME]
                            ?: run {
                                activity.runOnUiThread { onComplete(R.string.no_backup_on_drive) }
                                return@Thread
                            }

                    val entries = Gson().fromJson(
                            String(listContent), Array<PuzzleEntry>::class.java)
                            ?.toList()
                            ?: emptyList()

                    for (entry in entries) {
                        contents[entry.fileName]?.let { PuzzleManager.writePuzzle(entry.id, entry.format, it) }
                        contents["${entry.id}.state"]?.let { PuzzleManager.writeState(entry.id, it) }
                    }

                    PuzzleManager.saveList(entries)

                    activity.runOnUiThread { onComplete(R.string.loaded_from_drive) }
                } catch (e: Exception) {
                    Log.e(TAG, "Load from Drive failed", e)
                    activity.runOnUiThread {
                        onComplete(R.string.drive_load_failed)
                        FirebaseCrashlytics.getInstance().recordException(e)
                    }
                }
            }.start()
        }
    }

    fun deleteFromDrive(onComplete: (Int) -> Unit) {
        ensureSignedIn {
            Thread {
                try {
                    val service = driveService!!
                    val fileList = service.files().list()
                            .setSpaces("appDataFolder")
                            .setQ("name='$BACKUP_FILE_NAME' and trashed=false")
                            .execute()
                    for (file in fileList.files) {
                        service.files().delete(file.id).execute()
                    }

                    activity.runOnUiThread { onComplete(R.string.deleted_from_drive) }
                } catch (e: Exception) {
                    Log.e(TAG, "Delete from Drive failed", e)
                    activity.runOnUiThread {
                        onComplete(R.string.drive_delete_failed)
                        FirebaseCrashlytics.getInstance().recordException(e)
                    }
                }
            }.start()
        }
    }

    private fun buildBackupZip(): ByteArray {
        val entries = PuzzleManager.getPuzzles().filter {
            PuzzleManager.puzzleFile(it.id, it.format).exists()
        }

        val bytesOut = ByteArrayOutputStream()
        ZipOutputStream(bytesOut).use { zip ->
            zip.putNextEntry(ZipEntry(LIST_FILE_NAME))
            zip.write(Gson().toJson(entries).toByteArray())
            zip.closeEntry()

            for (entry in entries) {
                zip.putNextEntry(ZipEntry(entry.fileName))
                zip.write(PuzzleManager.puzzleFile(entry.id, entry.format).readBytes())
                zip.closeEntry()

                val stateFile = PuzzleManager.stateFile(entry.id)
                if (stateFile.exists()) {
                    zip.putNextEntry(ZipEntry("${entry.id}.state"))
                    zip.write(stateFile.readBytes())
                    zip.closeEntry()
                }
            }
        }
        return bytesOut.toByteArray()
    }

    private fun unzip(bytes: ByteArray): Map<String, ByteArray> {
        val entries = mutableMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                entries[entry.name] = zip.readBytes()
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        return entries
    }

    private fun ensureSignedIn(action: () -> Unit) {
        if (driveService == null) {
            pendingAction = action
            signIn()
        } else {
            action()
        }
    }

    private fun setupDriveService(account: GoogleSignInAccount) {
        val credential = GoogleAccountCredential.usingOAuth2(
                activity, listOf(DriveScopes.DRIVE_FILE, DriveScopes.DRIVE_APPDATA))
        credential.selectedAccount = account.account
        driveService = Drive.Builder(
                NetHttpTransport(),
                GsonFactory(),
                credential)
                .setApplicationName("Crossword App")
                .build()
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
}