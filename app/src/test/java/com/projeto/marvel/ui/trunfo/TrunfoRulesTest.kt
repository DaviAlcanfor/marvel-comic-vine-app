package com.projeto.marvel.ui.trunfo

import com.projeto.marvel.data.Stat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrunfoRulesTest {

    private fun card(id: Int, attack: Int, speed: Int = 10) =
        TrunfoCard(id, "c$id", null, mapOf(Stat.ATTACK to attack, Stat.SPEED to speed))

    @Test
    fun `maior atributo leva as duas cartas para o fim do monte`() {
        val game = TrunfoGame(player = listOf(card(1, 50), card(2, 10)), cpu = listOf(card(3, 40), card(4, 10)))
        val round = game.play(Stat.ATTACK)
        assertEquals(TrunfoSide.PLAYER, round.winner)
        assertEquals(listOf(2, 1, 3), round.next.player.map { it.id })
        assertEquals(listOf(4), round.next.cpu.map { it.id })
        assertEquals(TrunfoSide.PLAYER, round.next.chooser)
    }

    @Test
    fun `empate vai para o monte e quem vence a proxima leva tudo`() {
        val game = TrunfoGame(player = listOf(card(1, 30), card(2, 5)), cpu = listOf(card(3, 30), card(4, 50)))
        val tie = game.play(Stat.ATTACK)
        assertNull(tie.winner)
        assertEquals(2, tie.next.pot.size)
        val next = tie.next.play(Stat.ATTACK)
        assertEquals(TrunfoSide.CPU, next.winner)
        assertEquals(4, next.next.cpu.size)
        assertTrue(next.next.over)
        assertEquals(TrunfoSide.CPU, next.next.winner)
    }

    @Test
    fun `cpu escolhe o atributo mais forte`() {
        assertEquals(Stat.SPEED, card(1, attack = 20, speed = 90).bestStat())
    }

    @Test
    fun `limite de rodadas encerra e quem tem mais cartas ganha`() {
        val game = TrunfoGame(
            player = listOf(card(1, 1), card(2, 1)),
            cpu = listOf(card(3, 1)),
            round = TRUNFO_MAX_ROUNDS + 1
        )
        assertTrue(game.over)
        assertEquals(TrunfoSide.PLAYER, game.winner)
    }
}
