package com.jarvis.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GithubTest {
    @Test
    fun repoNameParses() {
        assertEquals("jarvis", repoName("jarvis"))
        assertEquals("o/r-name_2.0", repoName("o/r-name_2.0"))
        assertEquals("jarvis", repoName("  jarvis? "))
        assertNull(repoName(""))
        assertNull(repoName("the jarvis app"))
        assertNull(repoName("a/b/c"))
    }

    @Test
    fun resolveMatchRanks() {
        val all = listOf("me/jarvis-app", "me/notes", "you/jarvis")
        assertEquals("me/jarvis-app", resolveMatch(all, "me/jarvis-app"))
        assertEquals("you/jarvis", resolveMatch(all, "jarvis"))
        assertEquals("me/jarvis-app", resolveMatch(all, "jarvis-"))
        assertEquals("me/notes", resolveMatch(all, "note"))
        assertNull(resolveMatch(all, "zzz"))
    }

    @Test
    fun agoFormats() {
        val now = 1_779_278_400_000L
        assertEquals("30s", agoShort("2026-05-20T11:59:30Z", now))
        assertEquals("1m", agoShort("2026-05-20T11:59:00Z", now))
        assertEquals("3h", agoShort("2026-05-20T09:00:00Z", now))
        assertEquals("5d", agoShort("2026-05-15T12:00:00Z", now))
        assertEquals("—", agoShort("garbage", now))
    }

    @Test
    fun formatsReadWell() {
        val brief = RepoBrief("me/j", "desc", 7, 2, 3, "Kotlin", "main", "2h")
        val t = formatRepoBrief(brief)
        assertTrue(t.contains("me/j") && t.contains("★ 7") && t.contains("3 open"))
        assertTrue(formatRuns("me/j", emptyList()).contains("no workflow runs"))
        assertTrue(formatRuns("me/j", listOf(RunBrief("CI", "main", "success", "5m"))).contains("passing ✓"))
        assertTrue(formatRuns("me/j", listOf(RunBrief("CI", "main", "failure", "5m"))).contains("FAILING"))
        assertTrue(formatRuns("me/j", listOf(RunBrief("CI", "main", "in_progress", "1m"))).contains("building"))
        assertTrue(formatIssues("me/j", emptyList()).contains("No open issues"))
        assertTrue(formatFile("me/j", "a.kt", "", false).contains("empty file"))
        assertTrue(formatFile("me/j", "a.kt", "x".repeat(2000), true).contains("trimmed"))
        assertTrue(formatRepoList(emptyList()).contains("No repos"))
    }

    @Test
    fun routerRoutesGithub() {
        assertEquals("github_repos", Router.detect("my repos")?.tool)
        assertEquals("github_repos", Router.detect("list repos")?.tool)
        assertEquals("github_repo", Router.detect("repo new-pa-block-")?.tool)
        assertEquals("new-pa-block-", Router.detect("repo new-pa-block-")?.arg)
        assertEquals("github_repo", Router.detect("open jarvis repo")?.tool)
        assertEquals("github_repo", Router.detect("jarvis repo status")?.tool)
        assertEquals("github_builds", Router.detect("check builds for jarvis")?.tool)
        assertEquals("github_builds", Router.detect("jarvis build status")?.tool)
        assertEquals("github_builds", Router.detect("is jarvis building")?.tool)
        assertEquals("github_builds", Router.detect("build status")?.tool)
        assertEquals("github_issues", Router.detect("show issues of jarvis")?.tool)
        assertEquals("github_issues", Router.detect("jarvis repo issues")?.tool)
        assertEquals("github_read", Router.detect("read README from jarvis")?.tool)
        assertEquals("github_read", Router.detect("read MainActivity.kt from jarvis")?.tool)
        assertEquals("github_read", Router.detect("show readme of jarvis")?.tool)
    }

    @Test
    fun unobscureRoundTrip() {
        val token = "github_pat_test123"
        val obf = java.util.Base64.getEncoder().encodeToString(token.reversed().toByteArray())
        assertEquals(token, unobscureKey(obf))
        assertEquals("", unobscureKey(""))
        assertEquals("", unobscureKey("!!!not-base64!!!"))
    }

    @Test
    fun githubDoesNotHijack() {
        assertNull(Router.detect("trust issues"))
        assertNull(Router.detect("read the news from BBC"))
        assertNull(Router.detect("check builds for the jarvis app"))
        assertNull(Router.detect("report the news"))
        assertEquals("device", Router.detect("open jarvis app")?.tool)
        assertEquals("notifs", Router.detect("read my notifications")?.tool)
    }
}
