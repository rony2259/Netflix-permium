package com.personal.wabackup.ui.model

data class Movie(
    val title: String,
    val year: String,
    val posterPath: String,
    val match: String = "97% Match",
    val genre: String = "Drama"
)

data class MovieSection(
    val title: String,
    val movies: List<Movie>
)
