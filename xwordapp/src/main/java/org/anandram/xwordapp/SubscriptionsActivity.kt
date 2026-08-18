package org.anandram.xwordapp

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import org.jsoup.Jsoup
import java.io.ByteArrayInputStream

class SubscriptionsActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "Subscriptions"
    }

    private lateinit var subscriptions: MutableList<Subscription>
    private lateinit var downloadButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_subscriptions)

        title = getString(R.string.subscriptions)

        SubscriptionManager.init(this)
        PuzzleManager.init(this)

        subscriptions = SubscriptionManager.getSubscriptions().toMutableList()

        val listView = findViewById<ListView>(R.id.subscription_list)
        listView.adapter = SubscriptionAdapter(this, subscriptions)

        downloadButton = findViewById(R.id.btn_download)
        downloadButton.setOnClickListener { download() }
    }

    override fun onPause() {
        super.onPause()
        SubscriptionManager.saveSubscriptions(subscriptions)
    }

    private fun download() {
        val enabled = subscriptions.filter { it.enabled }
        if (enabled.isEmpty()) {
            Toast.makeText(this, R.string.no_subscriptions_enabled, Toast.LENGTH_SHORT).show()
            return
        }

        downloadButton.isEnabled = false
        downloadButton.text = getString(R.string.downloading)

        Thread {
            var count = 0
            for (subscription in enabled) {
                count += downloadFromSubscription(subscription)
            }

            runOnUiThread {
                downloadButton.isEnabled = true
                downloadButton.text = getString(R.string.download)
                val message = if (count > 0) {
                    getString(R.string.downloaded_puzzles, count)
                } else {
                    getString(R.string.no_puzzles_found)
                }
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
            }
        }.start()
    }

    private fun downloadFromSubscription(subscription: Subscription): Int {
        return try {
            val document = Jsoup.connect(subscription.url).get()
            var count = 0
            for (link in document.select("a[href]")) {
                val href = link.attr("href")
                if (href.endsWith(".puz", ignoreCase = true)) {
                    val absUrl = link.absUrl("href")
                    if (absUrl.isNotEmpty() && !PuzzleManager.hasPuzzleByUrl(absUrl)
                            && addPuzzleIfNew(absUrl, subscription.name)) {
                        count++
                    }
                }
            }
            count
        } catch (e: Exception) {
            Log.e(TAG, "Failed to download from ${subscription.name}", e)
            0
        }
    }

    private fun addPuzzleIfNew(url: String, sourceName: String): Boolean {
        return try {
            val bytes = Jsoup.connect(url)
                    .ignoreContentType(true)
                    .timeout(30_000)
                    .execute()
                    .bodyAsBytes()
            PuzzleManager.addPuzzleIfNew(ByteArrayInputStream(bytes),
                    sourceName = sourceName, downloadUrl = url) != null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to download $url", e)
            false
        }
    }

    private class SubscriptionAdapter(
            context: Context,
            private val items: MutableList<Subscription>) : BaseAdapter() {

        private val inflater = LayoutInflater.from(context)

        override fun getCount(): Int = items.size

        override fun getItem(position: Int): Any = items[position]

        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = convertView ?: inflater
                    .inflate(R.layout.item_subscription, parent, false)

            val subscription = items[position]
            val checkbox = view.findViewById<CheckBox>(R.id.subscription_enabled)
            checkbox.setOnCheckedChangeListener(null)
            checkbox.isChecked = subscription.enabled
            checkbox.setOnCheckedChangeListener { _, isChecked ->
                items[position] = subscription.copy(enabled = isChecked)
            }

            view.findViewById<TextView>(R.id.subscription_name).text = subscription.name
            view.findViewById<TextView>(R.id.subscription_url).text = subscription.url

            return view
        }
    }
}