package org.anandram.xwordapp

import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.ListView
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        title = getString(R.string.settings)

        val listView = findViewById<ListView>(R.id.settings_list)
        listView.adapter = ArrayAdapter(
                this,
                android.R.layout.simple_list_item_1,
                listOf(getString(R.string.subscriptions)))

        listView.setOnItemClickListener { _, _, _, _ ->
            startActivity(Intent(this, SubscriptionsActivity::class.java))
        }
    }
}