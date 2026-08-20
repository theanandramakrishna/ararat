package org.anandram.xwordapp

import android.app.Activity
import android.os.Build
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

fun Activity.applySystemBarInsets() {
    val content = findViewById<View>(android.R.id.content)
    ViewCompat.setOnApplyWindowInsetsListener(content) { v, insets ->
        val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
        var top = systemBars.top
        if (top == 0 && Build.VERSION.SDK_INT >= 35) {
            // On Android 15+ the decor action bar consumes the status-bar inset,
            // so read it from the root window insets to keep content below the action bar.
            top = WindowInsetsCompat.toWindowInsetsCompat(v.rootWindowInsets)
                    .getInsets(WindowInsetsCompat.Type.systemBars()).top
        }
        v.setPadding(0, top, 0, systemBars.bottom)
        insets
    }
}