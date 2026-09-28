package com.projeto.marvel.ui.login

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LoginValidationTest {

    @Test
    fun `valida e-mail e tamanho da senha antes de ir ao Firebase`() {
        assertEquals("Informe o e-mail", validateCredentials("", "123456"))
        assertEquals("E-mail inválido", validateCredentials("peter.parker", "123456"))
        val email = "peter@dailybugle.com"
        assertEquals("A senha precisa ter pelo menos 6 caracteres", validateCredentials(email, "12345"))
        assertNull(validateCredentials(email, "123456"))
    }
}
