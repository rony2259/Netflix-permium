package com.personal.wabackup.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.personal.wabackup.R
import com.personal.wabackup.ui.model.Movie
import com.personal.wabackup.ui.model.MovieSection

class SectionAdapter(
    private val sections: List<MovieSection>,
    private val onMovieClick: (Movie) -> Unit
) : RecyclerView.Adapter<SectionAdapter.VH>() {

    /** Shared pool so inner RVs recycle views across sections */
    private val sharedPool = RecyclerView.RecycledViewPool()

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.txt_section_title)
        val rvMovies: RecyclerView = view.findViewById(R.id.rv_movies)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_section, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val section = sections[position]
        holder.title.text = section.title

        val lm = LinearLayoutManager(holder.rvMovies.context, LinearLayoutManager.HORIZONTAL, false)
        lm.initialPrefetchItemCount = 5
        holder.rvMovies.layoutManager = lm
        holder.rvMovies.setRecycledViewPool(sharedPool)
        holder.rvMovies.adapter = MovieAdapter(section.movies, onMovieClick)
        holder.rvMovies.setHasFixedSize(true)
    }

    override fun getItemCount() = sections.size
}
