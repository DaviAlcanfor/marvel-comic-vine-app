package com.projeto.marvel.data

import android.content.Context
import androidx.core.content.edit

/** O que conta para as conquistas, juntado das várias estantes/placares. */
data class Progress(
    val wins: Int = 0,
    val ultimates: Int = 0,
    val gauntletTeams: Set<String> = emptySet(),
    val comicsRead: Int = 0,
    val reviews: Int = 0,
    val moviesWatched: Int = 0,
    val quizDone: Boolean = false,
    val photos: Int = 0,
    val bestGuessStreak: Int = 0,
    val stickers: Int = 0
)

/** Conquista: desbloqueia quando [value] do progresso chega a [goal]. Nomes/ícones ficam na UI. */
@Suppress("MagicNumber") // as metas são a própria tabela de regras das conquistas
enum class Achievement(val goal: Int, val value: (Progress) -> Int) {
    FIRST_WIN(1, { it.wins }),
    VETERAN(10, { it.wins }),
    PATHFINDER(1, { it.gauntletTeams.size }),
    AVENGER(1, { p -> if (p.gauntletTeams.any { it.equals(AVENGERS, ignoreCase = true) }) 1 else 0 }),
    MAX_POWER(5, { it.ultimates }),
    BOOKWORM(10, { it.comicsRead }),
    CRITIC(3, { it.reviews }),
    CINEPHILE(5, { it.moviesWatched }),
    SELF_KNOWLEDGE(1, { p -> if (p.quizDone) 1 else 0 }),
    FANBOY(1, { it.photos }),
    DETECTIVE(5, { it.bestGuessStreak }),
    COLLECTOR(30, { it.stickers });

    fun unlocked(progress: Progress) = value(progress) >= goal

    /** Progresso para a barra/texto "3/10", sem passar da meta. */
    fun current(progress: Progress) = value(progress).coerceAtMost(goal)
}

private const val AVENGERS = "Avengers"

/** Conquistas que estavam bloqueadas em [before] e estão desbloqueadas em [after]. */
fun newlyUnlocked(before: Progress, after: Progress) =
    Achievement.entries.filter { !it.unlocked(before) && it.unlocked(after) }

/**
 * Contadores que só as conquistas usam (o resto vem das estantes e do placar), no aparelho via
 * SharedPreferences, como o placar de batalhas.
 */
class AchievementStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val stickers = StickerStore(context)

    fun addUltimate() = prefs.edit { putInt(KEY_ULTIMATES, prefs.getInt(KEY_ULTIMATES, 0) + 1) }

    fun addGauntlet(team: String) = prefs.edit { putStringSet(KEY_GAUNTLETS, gauntlets() + team) }

    fun markQuiz() = prefs.edit { putBoolean(KEY_QUIZ, true) }

    fun addPhoto() = prefs.edit { putInt(KEY_PHOTOS, prefs.getInt(KEY_PHOTOS, 0) + 1) }

    fun bestGuessStreak() = prefs.getInt(KEY_GUESS_STREAK, 0)

    /** Guarda só o recorde da sequência do "Quem é esse herói?". */
    fun recordGuessStreak(streak: Int) {
        if (streak > bestGuessStreak()) prefs.edit { putInt(KEY_GUESS_STREAK, streak) }
    }

    private fun gauntlets(): Set<String> = prefs.getStringSet(KEY_GAUNTLETS, null).orEmpty().toSet()

    /** Progresso completo, juntando este arquivo com o placar e as estantes. */
    fun progress(record: BattleRecord, reading: ReadingStore): Progress {
        val comics = reading.get()
        return Progress(
            wins = record.wins,
            ultimates = prefs.getInt(KEY_ULTIMATES, 0),
            gauntletTeams = gauntlets(),
            comicsRead = comics.count { it.status == ReadingStatus.READ },
            reviews = comics.count { !it.review.isNullOrBlank() } +
                reading.movies().count { !it.review.isNullOrBlank() },
            moviesWatched = reading.movies().count { it.watched },
            quizDone = prefs.getBoolean(KEY_QUIZ, false),
            photos = prefs.getInt(KEY_PHOTOS, 0),
            bestGuessStreak = bestGuessStreak(),
            stickers = stickers.counts().size
        )
    }

    private companion object {
        const val PREFS_NAME = "achievements"
        const val KEY_ULTIMATES = "ultimates"
        const val KEY_GAUNTLETS = "gauntlets"
        const val KEY_QUIZ = "quiz"
        const val KEY_PHOTOS = "photos"
        const val KEY_GUESS_STREAK = "guess_streak"
    }
}
