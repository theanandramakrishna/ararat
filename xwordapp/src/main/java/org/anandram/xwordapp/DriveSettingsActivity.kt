package org.anandram.xwordapp

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class DriveSettingsActivity : AppCompatActivity() {

    private lateinit var driveManager: DriveManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_drive_settings)
        applySystemBarInsets()

        title = getString(R.string.drive_settings)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        driveManager = DriveManager(this)
        driveManager.setupSignIn()

        val listView = findViewById<ListView>(R.id.drive_settings_list)
        listView.adapter = ArrayAdapter(
                this,
                android.R.layout.simple_list_item_1,
                listOf(
                        getString(R.string.save_drive),
                        getString(R.string.load_drive),
                        getString(R.string.delete_from_drive)))

        listView.setOnItemClickListener { _, _, position, _ ->
            when (position) {
                0 -> driveManager.saveToDrive { message ->
                    Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
                }
                1 -> driveManager.loadFromDrive { message ->
                    Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
                }
                2 -> driveManager.deleteFromDrive { message ->
                    Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        driveManager.handleSignInResult(requestCode, data)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}