package com.projeto.marvel.data

import org.junit.Assert.assertEquals
import org.junit.Test

class RecentSearchStoreTest {

    @Test
    fun `busca nova vai para o topo`() {
        assertEquals(listOf("thor", "hulk"), listOf("hulk").withRecent("thor"))
    }

    @Test
    fun `busca repetida sobe sem duplicar, mesmo com outra caixa`() {
        assertEquals(listOf("Hulk", "thor"), listOf("thor", "hulk").withRecent("Hulk"))
    }

    @Test
    fun `pedacos digitados antes saem do historico`() {
        assertEquals(listOf("spider", "hulk"), listOf("spid", "spi", "hulk").withRecent("spider"))
    }

    @Test
    fun `guarda so as cinco mais recentes`() {
        assertEquals(listOf("f", "a", "b", "c", "d"), listOf("a", "b", "c", "d", "e").withRecent("f"))
    }
}
