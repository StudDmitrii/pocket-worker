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
}
