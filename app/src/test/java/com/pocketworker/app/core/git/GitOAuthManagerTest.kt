package com.pocketworker.app.core.git

import com.pocketworker.app.core.storage.InMemoryCredentialStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Base64

@OptIn(ExperimentalCoroutinesApi::class)
class GitOAuthManagerTest {

    private class FakeAuthorStore(
        var customName: String? = null,
        var customEmail: String? = null
    ) : GlobalGitAuthorStore {
        override fun hasCustomAuthor(): Boolean =
            !customName.isNullOrBlank() || !customEmail.isNullOrBlank()

        override fun saveAuthor(name: String, email: String) {
            customName = name
            customEmail = email
        }
    }

    private class RecordingOAuthHttpClient(
        private val onGet: (url: String, headers: Map<String, String>) -> OAuthHttpResponse = { _, _ ->
            OAuthHttpResponse(404, "{}")
        },
        private val onPost: (url: String, params: Map<String, String>, headers: Map<String, String>) -> OAuthHttpResponse = { _, _, _ ->
            OAuthHttpResponse(404, "{}")
        }
    ) : OAuthHttpClient {
        val getCalls = mutableListOf<Pair<String, Map<String, String>>>()
        val postCalls = mutableListOf<Triple<String, Map<String, String>, Map<String, String>>>()

        override fun get(url: String, headers: Map<String, String>): OAuthHttpResponse {
            getCalls.add(url to headers)
            return onGet(url, headers)
        }

        override fun postForm(
            url: String,
            formParams: Map<String, String>,
            headers: Map<String, String>
        ): OAuthHttpResponse {
            postCalls.add(Triple(url, formParams, headers))
            return onPost(url, formParams, headers)
        }
    }

    @Test
    fun `extractHostFromGitUrl and normalizeGitBaseUrl handle cloud, self-hosted ports, and ssh`() {
        assertEquals("github.com", extractHostFromGitUrl("https://github.com/octocat/hello.git"))
        assertEquals("gitlab.com", extractHostFromGitUrl("https://gitlab.com/group/sub/repo"))
        assertEquals("gitlab.company.com:8443", extractHostFromGitUrl("https://gitlab.company.com:8443/team/api.git"))
        assertEquals("gitlab.company.com", extractHostFromGitUrl("https://gitlab.company.com:443/team/api.git"))
        assertEquals("192.168.1.50:8080", extractHostFromGitUrl("http://192.168.1.50:8080/org/app.git"))
        assertEquals("github.com", extractHostFromGitUrl("git@github.com:octocat/hello.git"))
        assertNull(extractHostFromGitUrl("   "))

        assertEquals("https://gitlab.company.com", normalizeGitBaseUrl("gitlab.company.com/"))
        assertEquals("https://gitlab.company.com:8443", normalizeGitBaseUrl("https://GitLab.Company.com:8443/"))
        assertEquals("http://192.168.1.50:8080", normalizeGitBaseUrl("http://192.168.1.50:8080/"))
    }

    @Test
    fun `github device flow polls pending then slow_down, fetches primary email, and auto-fills global author only when unset`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val credStore = InMemoryCredentialStore()
        val authorStore = FakeAuthorStore()
        var pollStep = 0

        val http = RecordingOAuthHttpClient(
            onPost = { url, params, _ ->
                when (url) {
                    "https://github.com/login/device/code" -> {
                        assertEquals("gh-client-123", params["client_id"])
                        OAuthHttpResponse(
                            200,
                            """{"device_code":"dev-xyz","user_code":"ABCD-1234","verification_uri":"https://github.com/login/device","expires_in":900,"interval":5}"""
                        )
                    }
                    "https://github.com/login/oauth/access_token" -> {
                        assertEquals("dev-xyz", params["device_code"])
                        when (pollStep++) {
                            0 -> OAuthHttpResponse(200, """{"error":"authorization_pending"}""")
                            1 -> OAuthHttpResponse(200, """{"error":"slow_down","interval":10}""")
                            else -> OAuthHttpResponse(200, """{"access_token":"gho_secret_token_1","token_type":"bearer"}""")
                        }
                    }
                    else -> OAuthHttpResponse(404, "{}")
                }
            },
            onGet = { url, headers ->
                assertEquals("Bearer gho_secret_token_1", headers["Authorization"])
                when (url) {
                    "https://api.github.com/user" -> OAuthHttpResponse(
                        200,
                        """{"id":42,"login":"octodev","name":"Octo Dev","email":null}"""
                    )
                    "https://api.github.com/user/emails" -> OAuthHttpResponse(
                        200,
                        """[{"email":"secondary@example.com","primary":false,"verified":true},{"email":"octo@example.com","primary":true,"verified":true}]"""
                    )
                    else -> OAuthHttpResponse(404, "{}")
                }
            }
        )

        var fakeNow = 1_000_000L
        val delays = mutableListOf<Long>()
        val manager = GitOAuthManager(
            credentialStore = credStore,
            httpClient = http,
            globalAuthorStore = authorStore,
            ioDispatcher = dispatcher,
            scope = TestScope(dispatcher),
            delayFn = { ms ->
                delays.add(ms)
                fakeNow += ms
            },
            nowProvider = { fakeNow }
        )

        manager.startGitHubDeviceFlow("gh-client-123")
        advanceUntilIdle()

        val completed = manager.flowState.value as? GitOAuthFlowState.Completed
        assertNotNull(completed)
        assertEquals("octodev", completed!!.account.username)
        assertEquals("Octo Dev", completed.account.displayName)
        assertEquals("octo@example.com", completed.account.email)
        assertEquals("gho_secret_token_1", completed.account.accessToken)
        assertTrue(completed.autoFilledAuthor)
        assertEquals("Octo Dev", authorStore.customName)
        assertEquals("octo@example.com", authorStore.customEmail)
        // Verify polling backed off after slow_down (5s -> 5s -> 10s)
        assertEquals(listOf(5_000L, 5_000L, 10_000L), delays)

        // Second login when global author is ALREADY set must NOT overwrite it
        pollStep = 2
        authorStore.customName = "My Custom Name"
        authorStore.customEmail = "custom@work.com"
        manager.startGitHubDeviceFlow("gh-client-123")
        advanceUntilIdle()

        val second = manager.flowState.value as? GitOAuthFlowState.Completed
        assertNotNull(second)
        assertFalse(second!!.autoFilledAuthor)
        assertEquals("My Custom Name", authorStore.customName)
        assertEquals("custom@work.com", authorStore.customEmail)
        // Re-login updates existing account in place without creating duplicates
        assertEquals(1, credStore.getOAuthAccounts().size)
    }

    @Test
    fun `gitlab PKCE works for self-hosted server, verifies state, uses oauth2 user, and refreshes expired tokens`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val credStore = InMemoryCredentialStore()
        val authorStore = FakeAuthorStore()
        var fakeNow = 10_000_000L
        var capturedVerifier = ""

        val http = RecordingOAuthHttpClient(
            onPost = { url, params, _ ->
                assertEquals("https://gitlab.company.com:8443/oauth/token", url)
                when (params["grant_type"]) {
                    "authorization_code" -> {
                        capturedVerifier = params["code_verifier"].orEmpty()
                        assertEquals("gl-self-client", params["client_id"])
                        assertEquals("auth-code-99", params["code"])
                        assertEquals(GitOAuthDefaults.REDIRECT_URI_GITLAB, params["redirect_uri"])
                        OAuthHttpResponse(
                            200,
                            """{"access_token":"gl_access_v1","refresh_token":"gl_refresh_v1","expires_in":7200}"""
                        )
                    }
                    "refresh_token" -> {
                        assertEquals("gl-self-client", params["client_id"])
                        assertEquals("gl_refresh_v1", params["refresh_token"])
                        OAuthHttpResponse(
                            200,
                            """{"access_token":"gl_access_v2","refresh_token":"gl_refresh_v2","expires_in":7200}"""
                        )
                    }
                    else -> OAuthHttpResponse(400, "{}")
                }
            },
            onGet = { url, headers ->
                assertEquals("https://gitlab.company.com:8443/api/v4/user", url)
                assertEquals("Bearer gl_access_v1", headers["Authorization"])
                OAuthHttpResponse(
                    200,
                    """{"username":"ivan.dev","name":"Ivan Dev","commit_email":"ivan@company.com"}"""
                )
            }
        )

        val manager = GitOAuthManager(
            credentialStore = credStore,
            httpClient = http,
            globalAuthorStore = authorStore,
            ioDispatcher = dispatcher,
            scope = TestScope(dispatcher),
            nowProvider = { fakeNow }
        )

        val authUrl = manager.prepareGitLabAuthorization("gitlab.company.com:8443", "gl-self-client")
        assertNotNull(authUrl)
        val uri = URI(authUrl!!)
        val query = uri.rawQuery.split('&').associate {
            val parts = it.split('=', limit = 2)
            parts[0] to URLDecoder.decode(parts.getOrElse(1) { "" }, "UTF-8")
        }
        assertEquals("gl-self-client", query["client_id"])
        assertEquals("S256", query["code_challenge_method"])
        val state = query["state"]!!
        val sentChallenge = query["code_challenge"]!!

        // Wrong state must be rejected cleanly
        val badManager = GitOAuthManager(credStore, http, authorStore, dispatcher, TestScope(dispatcher))
        badManager.prepareGitLabAuthorization("gitlab.company.com:8443", "gl-self-client")
        assertTrue(badManager.handleRedirectUrl("pocketworker://oauth/gitlab?code=abc&state=wrong_state"))
        assertTrue(badManager.flowState.value is GitOAuthFlowState.Error)

        // Valid redirect completes login
        assertTrue(manager.handleRedirectUrl("pocketworker://oauth/gitlab?code=auth-code-99&state=$state"))
        advanceUntilIdle()

        // Confirm PKCE S256 math matches RFC 7636
        val expectedChallenge = Base64.getUrlEncoder().withoutPadding().encodeToString(
            MessageDigest.getInstance("SHA-256").digest(capturedVerifier.toByteArray(StandardCharsets.US_ASCII))
        )
        assertEquals(expectedChallenge, sentChallenge)

        val completed = manager.flowState.value as? GitOAuthFlowState.Completed
        assertNotNull(completed)
        val account = completed!!.account
        assertEquals("gitlab.company.com:8443", account.normalizedHost)
        assertEquals("ivan.dev", account.username)
        assertEquals("ivan@company.com", account.email)
        assertTrue(account.isSelfHosted)
        assertTrue(completed.autoFilledAuthor)
        assertEquals("Ivan Dev", authorStore.customName)
        assertEquals("ivan@company.com", authorStore.customEmail)
        assertEquals("gl-self-client", credStore.getOAuthClientId(GitOAuthProvider.GITLAB, "gitlab.company.com:8443"))

        // While token is fresh, resolveFreshCredentials returns gl_access_v1 without refreshing
        val freshCreds = manager.resolveFreshCredentials(
            projectId = "proj-1",
            repoId = "repo-1",
            remoteUrl = "https://gitlab.company.com:8443/team/service.git"
        )
        assertEquals(GitCredentials("oauth2", "gl_access_v1", isOAuthToken = true), freshCreds)
        assertEquals(1, http.postCalls.size)

        // Advance time past 2-hour expiry -> resolveFreshCredentials automatically refreshes token!
        fakeNow += 7_200_000L
        val refreshedCreds = manager.resolveFreshCredentials(
            projectId = "proj-1",
            repoId = "repo-1",
            remoteUrl = "https://gitlab.company.com:8443/team/service.git"
        )
        assertEquals(GitCredentials("oauth2", "gl_access_v2", isOAuthToken = true), refreshedCreds)
        assertEquals(2, http.postCalls.size)
        assertEquals("gl_refresh_v2", credStore.getOAuthAccount(account.id)?.refreshToken)
    }

    @Test
    fun `two-layer credential resolution handles multiple hosts, host defaults, project and repo overrides, and cleanup`() {
        val store = InMemoryCredentialStore()
        val ghPersonal = store.saveOAuthAccount(
            GitOAuthAccount(
                id = "gh-personal",
                provider = GitOAuthProvider.GITHUB,
                host = "github.com",
                baseUrl = "https://github.com",
                username = "personal",
                displayName = "Personal",
                accessToken = "gho_personal"
            )
        )
        val ghWork = store.saveOAuthAccount(
            GitOAuthAccount(
                id = "gh-work",
                provider = GitOAuthProvider.GITHUB,
                host = "github.com",
                baseUrl = "https://github.com",
                username = "work",
                displayName = "Work",
                accessToken = "gho_work"
            )
        )
        val glCloud = store.saveOAuthAccount(
            GitOAuthAccount(
                id = "gl-cloud",
                provider = GitOAuthProvider.GITLAB,
                host = "gitlab.com",
                baseUrl = "https://gitlab.com",
                username = "devgl",
                displayName = "Dev GL",
                accessToken = "gl_cloud_tok"
            )
        )
        store.saveDefaultCredentials(GitCredentials("manual-user", "manual-default-pat"))

        // 1. Host-based default auto-selects GitHub vs GitLab vs fallback manual PAT simultaneously
        assertEquals(
            GitCredentials("personal", "gho_personal", isOAuthToken = true),
            store.resolveForRepo("p1", "r1", "https://github.com/org/repo.git")
        )
        assertEquals(
            GitCredentials("oauth2", "gl_cloud_tok", isOAuthToken = true),
            store.resolveForRepo("p1", "r1", "https://gitlab.com/group/repo.git")
        )
        assertEquals(
            GitCredentials("manual-user", "manual-default-pat"),
            store.resolveForRepo("p1", "r1", "https://gitea.local/team/repo.git")
        )

        // 2. Switching default account for github.com
        store.setDefaultOAuthAccountForHost("github.com", ghWork.id)
        assertEquals(
            GitCredentials("work", "gho_work", isOAuthToken = true),
            store.resolveForRepo("p1", "r1", "https://github.com/org/repo.git")
        )

        // 3. Project OAuth binding overrides host default
        store.setProjectOAuthAccountId("p1", ghPersonal.id)
        assertEquals(
            GitCredentials("personal", "gho_personal", isOAuthToken = true),
            store.resolveForRepo("p1", "r1", "https://github.com/org/repo.git")
        )

        // 4. Repo OAuth binding overrides project binding
        store.setRepoOAuthAccountId("r1", glCloud.id)
        assertEquals(
            GitCredentials("oauth2", "gl_cloud_tok", isOAuthToken = true),
            store.resolveForRepo("p1", "r1", "https://gitlab.com/group/repo.git")
        )

        // 5. Manual repo PAT replaces repo OAuth binding (mutual exclusion)
        store.saveRepoCredentials("r1", GitCredentials("deploy", "repo-pat"))
        assertNull(store.getRepoOAuthAccountId("r1"))
        assertEquals(
            GitCredentials("deploy", "repo-pat"),
            store.resolveForRepo("p1", "r1", "https://github.com/org/repo.git")
        )

        // 6. Removing an OAuth account cleans up project binding and reassigns host default
        store.removeRepoCredentials("r1")
        store.removeOAuthAccount(ghPersonal.id)
        assertNull(store.getProjectOAuthAccountId("p1"))
        assertEquals(
            GitCredentials("work", "gho_work", isOAuthToken = true),
            store.resolveForRepo("p1", "r1", "https://github.com/org/repo.git")
        )
    }

    @Test
    fun `gitlab group discovery uses Bearer header for oauth2 tokens and PRIVATE-TOKEN for PATs`() {
        val capturedHeaders = mutableListOf<Map<String, String>>()
        val fakeClient = object : HttpTextClient {
            override fun get(url: String, headers: Map<String, String>): HttpTextResponse {
                capturedHeaders.add(headers)
                return HttpTextResponse(200, "[]")
            }
        }
        val discovery = HttpRepoGroupDiscoveryService(fakeClient)

        discovery.discoverRepositories(
            groupUrl = "https://gitlab.company.com/my-group",
            credentials = GitCredentials("oauth2", "gl_oauth_tok", isOAuthToken = true)
        )
        assertEquals("Bearer gl_oauth_tok", capturedHeaders.last()["Authorization"])
        assertNull(capturedHeaders.last()["PRIVATE-TOKEN"])

        discovery.discoverRepositories(
            groupUrl = "https://gitlab.company.com/my-group",
            credentials = GitCredentials("oauth2", "glpat-123", isOAuthToken = false)
        )
        assertEquals("glpat-123", capturedHeaders.last()["PRIVATE-TOKEN"])
        assertNull(capturedHeaders.last()["Authorization"])
    }
}
