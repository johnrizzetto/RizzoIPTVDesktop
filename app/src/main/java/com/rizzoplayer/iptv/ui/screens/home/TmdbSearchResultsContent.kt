package com.rizzoplayer.iptv.ui.screens.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.focusGroup
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rizzoplayer.iptv.AppConfig
import com.rizzoplayer.iptv.data.model.Favorite
import com.rizzoplayer.iptv.data.model.TmdbMovie
import com.rizzoplayer.iptv.data.model.TmdbShow
import com.rizzoplayer.iptv.ui.theme.TextMuted
import com.rizzoplayer.iptv.ui.theme.TextPrimary
import com.rizzoplayer.iptv.ui.viewmodel.BrowseContent
import com.rizzoplayer.iptv.ui.viewmodel.MainViewModel

@Composable
private fun SearchMovieRow(
    movies: List<TmdbMovie>,
    favorites: Map<String, Favorite>,
    onSelect: (TmdbMovie) -> Unit,
    onToggleFavorite: (TmdbMovie) -> Unit
) {
    val firstFocus = remember { FocusRequester() }

    Column {
        Text(
            "Movies",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextMuted,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
        )
        Box(modifier = Modifier.fillMaxWidth().focusGroup()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(movies.size) { idx ->
                    val movie = movies[idx]
                    TmdbPosterCard(
                        title = movie.title,
                        posterPath = movie.posterPath,
                        rating = movie.rating,
                        year = movie.releaseDate.take(4),
                        overview = movie.overview,
                        isFavorite = favorites.containsKey(movie.id.toString()),
                        onClick = { onSelect(movie) },
                        onLongClick = { onToggleFavorite(movie) },
                        modifier = if (idx == 0) Modifier.focusRequester(firstFocus) else Modifier
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchShowRow(
    shows: List<TmdbShow>,
    favorites: Map<String, Favorite>,
    onSelect: (TmdbShow) -> Unit,
    onToggleFavorite: (TmdbShow) -> Unit
) {
    val firstFocus = remember { FocusRequester() }

    Column {
        Text(
            "TV Shows",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextMuted,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
        )
        Box(modifier = Modifier.fillMaxWidth().focusGroup()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(shows.size) { idx ->
                    val show = shows[idx]
                    TmdbPosterCard(
                        title = show.name,
                        posterPath = show.posterPath,
                        rating = show.rating,
                        year = show.firstAirDate.take(4),
                        overview = show.overview,
                        isFavorite = favorites.containsKey(show.id.toString()),
                        onClick = { onSelect(show) },
                        onLongClick = { onToggleFavorite(show) },
                        modifier = if (idx == 0) Modifier.focusRequester(firstFocus) else Modifier
                    )
                }
            }
        }
    }
}

@Composable
fun EmptyHint(message: String = "Select a category") {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(message, fontSize = 14.sp, color = TextMuted)
    }
}

/**
 * Standalone search results view for use in Movies / Shows spaces.
 * filter = "movies"  -> show only movies row
 * filter = "shows"   -> show only shows row
 * filter = "all"      -> show both rows (used by ContentArea / global search)
 */
@Composable
fun TmdbSearchResultsContent(
    content: BrowseContent.TmdbSearchResults,
    favorites: Map<String, Favorite>,
    viewModel: MainViewModel,
    filter: String = "all"
) {
    val showMovies = filter != "shows" && content.movies.isNotEmpty()
    val showShows = filter != "movies" && content.shows.isNotEmpty()

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            "Results for \"${content.query}\"",
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextPrimary,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
        )

        if (showMovies) {
            SearchMovieRow(
                movies = content.movies,
                favorites = favorites,
                onSelect = viewModel::selectTmdbMovie,
                onToggleFavorite = { movie ->
                    viewModel.toggleFavorite(
                        id   = movie.id.toString(),
                        name = movie.title,
                        type = "tmdb_movie",
                        icon = movie.posterPath?.let { "${AppConfig.TMDB_IMAGE_BASE}/${AppConfig.TMDB_POSTER_SIZE}$it" }
                    )
                }
            )
        }

        if (showShows) {
            SearchShowRow(
                shows = content.shows,
                favorites = favorites,
                onSelect = viewModel::selectTmdbShow,
                onToggleFavorite = { show ->
                    viewModel.toggleFavorite(
                        id   = show.id.toString(),
                        name = show.name,
                        type = "tmdb_show",
                        icon = show.posterPath?.let { "${AppConfig.TMDB_IMAGE_BASE}/${AppConfig.TMDB_POSTER_SIZE}$it" }
                    )
                }
            )
        }

        if (!showMovies && !showShows) {
            EmptyHint("No results found")
        }
    }
}
