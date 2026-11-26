package com.pocketworker.app.core.storage

import com.pocketworker.app.core.git.GitCredentials
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CredentialStoreTest {

    @Test
    fun `resolveForRepo prefers repo credentials, then project, then default`() {
        val store = InMemoryCredentialStore()
        val defaultCreds = GitCredentials("def", "tok-def")
        val projectCreds = GitCredentials("proj", "tok-proj")
        val repoCreds = GitCredentials("repo", "tok-repo")

        assertNull(store.resolveForRepo("p1", "r1"))

        store.saveDefaultCredentials(defaultCreds)
        assertEquals(defaultCreds, store.resolveForRepo("p1", "r1"))

        store.saveProjectCredentials("p1", projectCreds)
        assertEquals(projectCreds, store.resolveForRepo("p1", "r1"))

        store.saveRepoCredentials("r1", repoCreds)
        assertEquals(repoCreds, store.resolveForRepo("p1", "r1"))

        store.removeRepoCredentials("r1")
        assertEquals(projectCreds, store.resolveForRepo("p1", "r1"))
    }

    @Test
    fun `ai api keys are isolated per provider`() {
        val store = InMemoryCredentialStore()
        store.saveAiApiKey(AiProvider.GEMINI, "AIza-test")

        assertEquals("AIza-test", store.getAiApiKey(AiProvider.GEMINI))
        assertNull(store.getAiApiKey(AiProvider.ANTHROPIC))
        assertNull(store.getAiApiKey(AiProvider.OPENAI))
        assertEquals(mapOf(AiProvider.GEMINI to "AIza-test"), store.getAllAiApiKeys())

        store.saveAiApiKey(AiProvider.OPENAI, "sk-openai-test")
        assertEquals("AIza-test", store.getAiApiKey(AiProvider.GEMINI))
        assertEquals("sk-openai-test", store.getAiApiKey(AiProvider.OPENAI))
        assertNull(store.getAiApiKey(AiProvider.ANTHROPIC))
    }
}
