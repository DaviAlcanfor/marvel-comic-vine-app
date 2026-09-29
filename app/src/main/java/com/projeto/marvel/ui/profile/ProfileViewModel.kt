package com.projeto.marvel.ui.profile

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.projeto.marvel.data.AchievementStore
import com.projeto.marvel.data.AuthRepository
import com.projeto.marvel.data.BattleRecord
import com.projeto.marvel.data.BattleRecordStore
import com.projeto.marvel.data.Favorite
import com.projeto.marvel.data.MissionEvent
import com.projeto.marvel.data.mission
import com.projeto.marvel.data.ProfilePreferences
import com.projeto.marvel.data.Progress
import com.projeto.marvel.data.RatedMovie
import com.projeto.marvel.data.ReadComic
import com.projeto.marvel.data.ReadingStatus
import com.projeto.marvel.data.ReadingStore
import com.projeto.marvel.data.UserProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Tudo vem do aparelho (Firebase Auth em cache + SharedPreferences): não há Loading/Error. */
data class ProfileUiState(
    val user: UserProfile?,
    val shelf: List<ReadComic>,
    val record: BattleRecord,
    val preferences: ProfilePreferences,
    val filter: ReadingStatus,
    val movies: List<RatedMovie> = emptyList(),
    val progress: Progress = Progress()
) {
    val read get() = shelf.filter { it.status == ReadingStatus.READ }

    val filtered get() = shelf.filter { it.status == filter }

    /** Média só das HQs lidas com nota (0 = sem nota); null se nenhuma tem nota. */
    val averageRating: Double?
        get() = read.filter { it.rating > 0 }.map { it.rating }.average().takeIf { !it.isNaN() }
}

class ProfileViewModel @JvmOverloads constructor(
    application: Application,
    private val auth: AuthRepository = AuthRepository(),
    private val store: ReadingStore = ReadingStore(application, auth.currentUser?.uid),
    private val records: BattleRecordStore = BattleRecordStore(application),
    private val achievements: AchievementStore = AchievementStore(application)
) : AndroidViewModel(application) {

    private var filter = ReadingStatus.READ
    private val _state = MutableStateFlow(current())
    val state: StateFlow<ProfileUiState> = _state.asStateFlow()

    /** A estante e o placar mudam em outras telas (busca de HQs, Batalha): relê ao voltar. */
    fun refresh() {
        _state.value = current()
    }

    fun showOnly(status: ReadingStatus) {
        filter = status
        refresh()
    }

    fun save(comic: ReadComic) {
        store.save(comic)
        if (!comic.review.isNullOrBlank()) getApplication<Application>().mission(MissionEvent.REVIEW)
        refresh()
    }

    fun remove(id: Int) {
        store.remove(id)
        refresh()
    }

    fun setHero(hero: Favorite) = updatePreferences { it.copy(hero = hero) }

    fun setSeries(series: Favorite) = updatePreferences { it.copy(series = series) }

    fun setGenres(genres: List<String>) = updatePreferences { it.copy(genres = genres) }

    fun signOut() = auth.signOut()

    private fun updatePreferences(change: (ProfilePreferences) -> ProfilePreferences) {
        store.savePreferences(change(store.preferences()))
        refresh()
    }

    private fun current(): ProfileUiState {
        val record = records.get()
        return ProfileUiState(
            user = auth.currentUser,
            shelf = store.get(),
            record = record,
            preferences = store.preferences(),
            filter = filter,
            movies = store.movies(),
            progress = achievements.progress(record, store)
        )
    }
}
