package com.personal.wabackup.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.personal.wabackup.R
import com.personal.wabackup.ui.data.MovieData
import com.personal.wabackup.ui.model.Movie

class MovieAdapter(
    private val movies: List<Movie>,
    private val onMovieClick: (Movie) -> Unit
) : RecyclerView.Adapter<MovieAdapter.VH>() {

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val imgPoster: ImageView = view.findViewById(R.id.img_poster)
        val txtMatch: TextView = view.findViewById(R.id.txt_match)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_poster, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val movie = movies[position]
        holder.txtMatch.text = movie.match

        Glide.with(holder.imgPoster.context)
            .load(MovieData.TMDB_BASE + movie.posterPath)
            .transition(DrawableTransitionOptions.withCrossFade(200))
            .centerCrop()
            .into(holder.imgPoster)

        holder.itemView.setOnClickListener { onMovieClick(movie) }
    }

    override fun getItemCount() = movies.size
}
