package com.personal.wabackup.ui

import android.content.Intent
import android.os.Bundle
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import com.personal.wabackup.R

/**
 * Profile selection screen — "Who's watching?"
 * Any profile click navigates to HomeActivity.
 */
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val goHome = { _: Any ->
            startActivity(Intent(this, HomeActivity::class.java))
            // No finish() so back button returns to profile selection
        }

        findViewById<LinearLayout>(R.id.profile_1).setOnClickListener(goHome)
        findViewById<LinearLayout>(R.id.profile_2).setOnClickListener(goHome)
        findViewById<LinearLayout>(R.id.profile_3).setOnClickListener(goHome)
        findViewById<LinearLayout>(R.id.profile_4).setOnClickListener(goHome)
    }
}
