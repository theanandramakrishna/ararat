package org.anandram.xwordapp

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.MenuItem
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
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

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

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val listView = findViewById<ListView>(R.id.subscription_list)
        listView.adapter = SubscriptionAdapter(this, subscriptions)

        downloadButton = findViewById(R.id.btn_download)
        downloadButton.setOnClickListener { download() }
    }

    override fun onPause() {
        super.onPause()
        SubscriptionManager.saveSubscriptions(subscriptions)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun shouldSkip(subscription: Subscription, today: String): Boolean {
        val lastDate = subscription.lastDownloadDate
        if (lastDate.isEmpty()) return false

        return when (subscription.fetchFrequency) {
            "Weekly" -> lastDate >= startOfWeek(today)
            "Daily" -> lastDate == today
            else -> true
        }
    }

    private fun startOfWeek(today: String): String {
        val parts = today.split("-")
        val cal = Calendar.getInstance()
        cal.clear()
        cal.set(parts[0].toInt(), parts[1].toInt() - 1, parts[2].toInt())
        cal.firstDayOfWeek = Calendar.SUNDAY
        cal.minimalDaysInFirstWeek = 1
        cal.set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY)
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time)
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
            val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
            for (subscription in enabled) {
                if (shouldSkip(subscription, today)) continue
                SubscriptionManager.markDownloadStarted(subscription.name, today)
                val index = subscriptions.indexOfFirst { it.name == subscription.name }
                if (index >= 0) {
                    subscriptions[index] = subscriptions[index].copy(lastDownloadDate = today)
                }
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
            private val subscriptions: MutableList<Subscription>) : BaseAdapter() {

        private val inflater = LayoutInflater.from(context)
        private val rows = mutableListOf<Row>()

        private sealed class Row
        private class HeaderRow(val title: String) : Row()
        private class SubscriptionRow(val subscription: Subscription) : Row()

        init {
            rebuild()
        }

        private fun rebuild() {
            rows.clear()
            val groups = subscriptions.groupBy { it.fetchFrequency }
            val orderedKeys = listOf("One-Time", "Weekly", "Daily")
                    .filter { groups.containsKey(it) }
            val keys = orderedKeys + (groups.keys - orderedKeys.toSet())
            for (key in keys) {
                rows.add(HeaderRow(key))
                rows.addAll(groups.getValue(key).map { SubscriptionRow(it) })
            }
        }

        override fun getCount(): Int = rows.size

        override fun getItem(position: Int): Any = rows[position]

        override fun getItemId(position: Int): Long = position.toLong()

        override fun getViewTypeCount(): Int = 2

        override fun getItemViewType(position: Int): Int =
                if (rows[position] is HeaderRow) 0 else 1

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val row = rows[position]
            if (row is HeaderRow) {
                val view = convertView ?: inflater
                        .inflate(R.layout.item_section_header, parent, false)
                view.findViewById<TextView>(R.id.section_title).text = row.title
                return view
            }

            val subscription = (row as SubscriptionRow).subscription
            val view = convertView ?: inflater
                    .inflate(R.layout.item_subscription, parent, false)

            val checkbox = view.findViewById<CheckBox>(R.id.subscription_enabled)
            checkbox.setOnCheckedChangeListener(null)
            checkbox.isChecked = subscription.enabled
            checkbox.setOnCheckedChangeListener { _, isChecked ->
                val index = subscriptions.indexOfFirst { it.name == subscription.name }
                if (index >= 0) {
                    subscriptions[index] = subscription.copy(enabled = isChecked)
                    rebuild()
                    notifyDataSetChanged()
                }
            }

            view.findViewById<TextView>(R.id.subscription_name).text = subscription.name
            view.findViewById<TextView>(R.id.subscription_url).text = subscription.url

            return view
        }
    }
}