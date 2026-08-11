package com.example.sportapp.viewmodel

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.sportapp.core.network.AgentChatRequest
import com.example.sportapp.core.network.AgentChatResponse
import com.example.sportapp.core.network.ApiAgentService
import com.example.sportapp.core.network.RetrofitInstance
import com.example.sportapp.feature.conversations.viewmodel.ConversationsViewModel
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import retrofit2.HttpException
import retrofit2.Response

/**
 * Tests JVM du comportement observable de [ConversationsViewModel] — le coeur
 * client du Cas C (agent IA in-app, Phase 2 MCP). Le VM est une machine a etats
 * de chat : ajout optimiste de la bulle user, garde `isSending`, POST de
 * l'historique COMPLET (serveur stateless, decision 2026-05-31), mapping des
 * erreurs HTTP, et reset via clearConversation.
 *
 * Frontiere reseau mockee a la maniere preconisee dans build.gradle.kts
 * (ligne 170) : on stubbe [RetrofitInstance.agentService] (singleton object)
 * avec un faux [ApiAgentService] controlable. Le VM capture ce service a la
 * construction (`private val api = RetrofitInstance.agentService`), donc le
 * mock doit etre pose AVANT d'instancier le VM. Le `Context` est un vrai
 * Context Robolectric -> les messages d'erreur assertes sont les vraies
 * ressources strings.xml (politique 18), pas des litteraux dupliques ici.
 *
 * UnconfinedTestDispatcher par defaut (la coroutine sendMessage s'execute des
 * son lancement) ; le test du garde re-entrant utilise un StandardTestDispatcher
 * + une reponse suspendue pour figer un envoi "en vol".
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class ConversationsViewModelTest {

    private lateinit var context: Context

    /** Faux service : on scripte la reponse (ou l'exception) et on capture les
     *  requetes recues pour verifier le contenu envoye au serveur. */
    private class FakeAgentService : ApiAgentService {
        val received = mutableListOf<AgentChatRequest>()
        var reply: String = "ok"
        var toThrow: Throwable? = null
        /** Si pose, chat() suspend jusqu'a ce que ce Deferred soit complete
         *  (permet de figer un envoi "en vol" pour tester le garde isSending). */
        var gate: CompletableDeferred<Unit>? = null

        override suspend fun chat(body: AgentChatRequest): AgentChatResponse {
            received.add(body)
            gate?.await()
            toThrow?.let { throw it }
            return AgentChatResponse(reply = reply)
        }
    }

    private val fakeApi = FakeAgentService()

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        // Le VM lit RetrofitInstance.agentService a la construction : stub avant
        // d'instancier (sinon il capterait le vrai Retrofit -> touche BASE_URL).
        mockkObject(RetrofitInstance)
        every { RetrofitInstance.agentService } returns fakeApi
    }

    @After
    fun teardown() {
        unmockkObject(RetrofitInstance)
        Dispatchers.resetMain()
    }

    private fun httpException(code: Int): HttpException =
        HttpException(
            Response.error<Any>(
                code,
                "{}".toResponseBody("application/json".toMediaTypeOrNull()),
            )
        )

    // ---- Happy path : echange complet ----

    @Test
    fun `sendMessage shows user bubble then assistant reply and clears isSending`() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        fakeApi.reply = "Tu as 3 seances cette semaine."
        val vm = ConversationsViewModel(context)

        vm.sendMessage("combien de seances cette semaine ?")

        val bubbles = vm.messages.value
        assertEquals(2, bubbles.size)
        assertEquals(ConversationsViewModel.ChatBubble.Role.USER, bubbles[0].role)
        assertEquals("combien de seances cette semaine ?", bubbles[0].text)
        assertEquals(ConversationsViewModel.ChatBubble.Role.ASSISTANT, bubbles[1].role)
        assertEquals("Tu as 3 seances cette semaine.", bubbles[1].text)
        assertFalse("isSending doit retomber a false apres la reponse", vm.isSending.value)
    }

    // ---- Serveur stateless : l'historique complet est renvoye a chaque tour ----

    @Test
    fun `second send posts the full conversation history`() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        fakeApi.reply = "R1"
        val vm = ConversationsViewModel(context)

        vm.sendMessage("Q1")
        fakeApi.reply = "R2"
        vm.sendMessage("Q2")

        // 2e requete = historique complet (Q1, R1, Q2), le serveur ne stocke rien.
        assertEquals(2, fakeApi.received.size)
        val secondPayload = fakeApi.received[1].messages
        assertEquals(3, secondPayload.size)
        assertEquals(listOf("user", "assistant", "user"), secondPayload.map { it.role })
        assertEquals(listOf("Q1", "R1", "Q2"), secondPayload.map { it.content })
    }

    // ---- Edge cases : input vide / blanc, garde re-entrant ----

    @Test
    fun `blank or empty message is ignored`() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val vm = ConversationsViewModel(context)

        vm.sendMessage("")
        vm.sendMessage("   ")

        assertTrue(vm.messages.value.isEmpty())
        assertTrue("aucun appel reseau pour une saisie vide", fakeApi.received.isEmpty())
    }

    @Test
    fun `send while a request is in flight is ignored`() {
        // StandardTestDispatcher : la coroutine ne demarre pas tant qu'on n'avance
        // pas le scheduler -> on peut figer un envoi "en vol".
        val dispatcher = StandardTestDispatcher()
        Dispatchers.setMain(dispatcher)
        runTest(dispatcher.scheduler) {
            val gate = CompletableDeferred<Unit>()
            fakeApi.gate = gate
            val vm = ConversationsViewModel(context)

            vm.sendMessage("premier")
            runCurrent() // la coroutine atteint gate.await() et y reste bloquee
            assertTrue("isSending doit etre vrai pendant l'envoi", vm.isSending.value)

            // Tentative pendant l'envoi : doit etre ignoree (pas de 2e requete).
            vm.sendMessage("deuxieme")
            runCurrent()
            assertEquals(1, fakeApi.received.size)
            // La bulle "deuxieme" n'a pas ete ajoutee.
            assertEquals(1, vm.messages.value.size)

            // Debloque l'envoi en cours et laisse finir.
            gate.complete(Unit)
            advanceUntilIdle()
            assertFalse(vm.isSending.value)
        }
    }

    // ---- Mapping des erreurs HTTP -> messages de ressources reels ----

    @Test
    fun `http 503 maps to unavailable message and resets isSending`() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        fakeApi.toThrow = httpException(503)
        val vm = ConversationsViewModel(context)

        vm.sendMessage("hello")

        val last = vm.messages.value.last()
        assertEquals(ConversationsViewModel.ChatBubble.Role.ASSISTANT, last.role)
        assertEquals(context.getString(com.example.sportapp.R.string.agent_error_unavailable), last.text)
        assertFalse(vm.isSending.value)
    }

    @Test
    fun `http 429 maps to rate-limited message`() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        fakeApi.toThrow = httpException(429)
        val vm = ConversationsViewModel(context)

        vm.sendMessage("hello")

        assertEquals(
            context.getString(com.example.sportapp.R.string.agent_error_rate_limited),
            vm.messages.value.last().text,
        )
    }

    @Test
    fun `other http error maps to generic message with the code`() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        fakeApi.toThrow = httpException(500)
        val vm = ConversationsViewModel(context)

        vm.sendMessage("hello")

        assertEquals(
            context.getString(com.example.sportapp.R.string.agent_error_generic, 500),
            vm.messages.value.last().text,
        )
    }

    @Test
    fun `non-http exception maps to network message`() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        fakeApi.toThrow = RuntimeException("socket boom")
        val vm = ConversationsViewModel(context)

        vm.sendMessage("hello")

        assertEquals(
            context.getString(com.example.sportapp.R.string.agent_error_network),
            vm.messages.value.last().text,
        )
        assertFalse(vm.isSending.value)
    }

    // ---- clearConversation ----

    @Test
    fun `clearConversation empties the thread`() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val vm = ConversationsViewModel(context)
        vm.sendMessage("Q1")
        assertEquals(2, vm.messages.value.size)

        vm.clearConversation()

        assertTrue(vm.messages.value.isEmpty())
    }

    @Test
    fun `clearConversation is ignored while a request is in flight`() {
        val dispatcher = StandardTestDispatcher()
        Dispatchers.setMain(dispatcher)
        runTest(dispatcher.scheduler) {
            val gate = CompletableDeferred<Unit>()
            fakeApi.gate = gate
            val vm = ConversationsViewModel(context)

            vm.sendMessage("premier")
            runCurrent()
            assertTrue(vm.isSending.value)

            // Tentative de reset pendant l'envoi : ignoree (la bulle user reste).
            vm.clearConversation()
            runCurrent()
            assertEquals(1, vm.messages.value.size)

            gate.complete(Unit)
            advanceUntilIdle()
        }
    }
}
