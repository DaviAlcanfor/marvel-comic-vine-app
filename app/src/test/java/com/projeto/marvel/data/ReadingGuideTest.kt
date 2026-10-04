package com.projeto.marvel.data

import com.projeto.marvel.data.remote.Publisher
import com.projeto.marvel.data.remote.VolumeCard
import org.junit.Assert.assertEquals
import org.junit.Test

class ReadingGuideTest {

    private fun volume(id: Int, publisher: String?, issues: Int?) =
        VolumeCard(id, publisher?.let { Publisher(it) }, issues = issues)

    @Test
    fun `so Marvel, com edicoes, as maiores primeiro`() {
        val picks = starterCandidates(
            listOf(
                volume(1, "Marvel", 12),
                volume(2, "DC Comics", 500),
                volume(3, "Marvel", 0),
                volume(4, null, 40),
                volume(5, "Marvel", 189)
            )
        )
        assertEquals(listOf(5, 1), picks.map { it.id })
    }
}
