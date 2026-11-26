package com.pocketworker.app.core.git

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GitHostGroupDiscoveryTest {

    @Test
    fun `parseGroupUrl handles GitHub orgs users and rejects single repo`() {
        val org = HttpRepoGroupDiscoveryService.parseGroupUrl("https://github.com/orgs/google/repositories")
        assertTrue(org is ParsedGroupUrl.GitHub)
        org as ParsedGroupUrl.GitHub
        assertEquals("google", org.owner)
        assertTrue(org.isExplicitOrg)

        val user = HttpRepoGroupDiscoveryService.parseGroupUrl("github.com/torvalds?tab=repositories")
        assertTrue(user is ParsedGroupUrl.GitHub)
        user as ParsedGroupUrl.GitHub
        assertEquals("torvalds", user.owner)
        assertFalse(user.isExplicitOrg)

        val singleRepoOutcome = runCatching {
            HttpRepoGroupDiscoveryService.parseGroupUrl("https://github.com/google/guava")
        }
        assertTrue(singleRepoOutcome.isFailure)
    }

    @Test
    fun `parseGroupUrl handles GitLab groups nested subgroups and self hosted instances`() {
        val glGroup = HttpRepoGroupDiscoveryService.parseGroupUrl("https://gitlab.com/groups/my-org/backend/services/-/children")
        assertTrue(glGroup is ParsedGroupUrl.GitLab)
        glGroup as ParsedGroupUrl.GitLab
        assertEquals("https://gitlab.com", glGroup.hostBaseUrl)
        assertEquals("my-org/backend/services", glGroup.groupPath)

        val selfHosted = HttpRepoGroupDiscoveryService.parseGroupUrl("http://git.company.local:8080/mobile-team")
        assertTrue(selfHosted is ParsedGroupUrl.GitLab)
        selfHosted as ParsedGroupUrl.GitLab
        assertEquals("http://git.company.local:8080", selfHosted.hostBaseUrl)
        assertEquals("mobile-team", selfHosted.groupPath)
    }

    @Test
    fun `discoverRepositories fetches GitHub org and self private user repos`() {
        val requestedUrls = mutableListOf<String>()
        val authHeaders = mutableListOf<String?>()

        val fakeClient = object : HttpTextClient {
            override fun get(url: String, headers: Map<String, String>): HttpTextResponse {
                requestedUrls += url
                authHeaders += headers["Authorization"]
                return when {
                    url == "https://api.github.com/users/my-org" -> HttpTextResponse(
                        statusCode = 200,
                        body = """{"login":"my-org","type":"Organization"}"""
                    )
                    url.startsWith("https://api.github.com/orgs/my-org/repos") -> HttpTextResponse(
                        statusCode = 200,
                        body = """[
                            {"id":1,"name":"client","full_name":"my-org/client","clone_url":"https://github.com/my-org/client.git","default_branch":"main","description":"Android app","private":false},
                            {"id":2,"name":"api","full_name":"my-org/api","clone_url":"https://github.com/my-org/api.git","default_branch":"dev","description":null,"private":true}
                        ]"""
                    )
                    url == "https://api.github.com/users/alice" -> HttpTextResponse(
                        statusCode = 200,
                        body = """{"login":"alice","type":"User"}"""
                    )
                    url == "https://api.github.com/user" -> HttpTextResponse(
                        statusCode = 200,
                        body = """{"login":"alice"}"""
                    )
                    url.startsWith("https://api.github.com/user/repos") -> HttpTextResponse(
                        statusCode = 200,
                        body = """[
                            {"id":10,"name":"secret-notes","full_name":"alice/secret-notes","clone_url":"https://github.com/alice/secret-notes.git","default_branch":"main","description":"","private":true}
                        ]"""
                    )
                    else -> HttpTextResponse(statusCode = 404, body = """{"message":"Not Found"}""")
                }
            }
        }

        val service = HttpRepoGroupDiscoveryService(fakeClient)

        val orgRepos = service.discoverRepositories(
            "https://github.com/my-org",
            GitCredentials("x-access-token", "ghp_secret")
        )
        assertEquals(2, orgRepos.size)
        assertEquals("client", orgRepos[0].name)
        assertEquals("api", orgRepos[1].name)
        assertTrue(orgRepos[1].isPrivate)
        assertEquals("", orgRepos[1].description)
        assertTrue(authHeaders.all { it == "Bearer ghp_secret" })

        val userRepos = service.discoverRepositories(
            "https://github.com/alice",
            GitCredentials("alice", "ghp_secret")
        )
        assertEquals(1, userRepos.size)
        assertEquals("secret-notes", userRepos.first().name)
        assertTrue(requestedUrls.any { it.contains("/user/repos") })
    }

    @Test
    fun `discoverRepositories fetches GitLab group with pagination and falls back to user on 404`() {
        val requestedUrls = mutableListOf<String>()
        val fakeClient = object : HttpTextClient {
            override fun get(url: String, headers: Map<String, String>): HttpTextResponse {
                requestedUrls += url
                assertEquals("glpat-123", headers["PRIVATE-TOKEN"])
                return when {
                    url.contains("/api/v4/groups/org%2Fsub/projects") -> HttpTextResponse(
                        statusCode = 200,
                        body = """[
                            {"id":101,"name":"Core Service","path":"core-service","path_with_namespace":"org/sub/core-service","http_url_to_repo":"https://gitlab.com/org/sub/core-service.git","default_branch":"main","description":"Core","visibility":"private"}
                        ]""",
                        headers = emptyMap()
                    )
                    url.contains("/api/v4/groups/bob/projects") -> HttpTextResponse(
                        statusCode = 404,
                        body = """{"message":"404 Group Not Found"}"""
                    )
                    url.contains("/api/v4/users/bob/projects") -> HttpTextResponse(
                        statusCode = 200,
                        body = """[
                            {"id":202,"name":"Personal App","path":"personal-app","path_with_namespace":"bob/personal-app","http_url_to_repo":"https://gitlab.com/bob/personal-app.git","default_branch":"main","description":"","visibility":"public"}
                        ]"""
                    )
                    else -> HttpTextResponse(statusCode = 404, body = "{}")
                }
            }
        }

        val service = HttpRepoGroupDiscoveryService(fakeClient)

        val groupRepos = service.discoverRepositories(
            "https://gitlab.com/org/sub",
            GitCredentials("oauth2", "glpat-123")
        )
        assertEquals(1, groupRepos.size)
        assertEquals("core-service", groupRepos[0].name)
        assertEquals("org/sub/core-service", groupRepos[0].fullName)
        assertTrue(groupRepos[0].isPrivate)

        val userRepos = service.discoverRepositories(
            "https://gitlab.com/bob",
            GitCredentials("oauth2", "glpat-123")
        )
        assertEquals(1, userRepos.size)
        assertEquals("personal-app", userRepos[0].name)
        assertTrue(requestedUrls.any { it.contains("/api/v4/users/bob/projects") })
    }
}
