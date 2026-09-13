package com.jarvis.app

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

// ---------- secure token storage (never in plain prefs, never logged) ----------

private const val SECURE_FILE = "jarvis_secure"

private fun securePrefs(ctx: Context) = EncryptedSharedPreferences.create(
    ctx.applicationContext, SECURE_FILE,
    MasterKey.Builder(ctx.applicationContext).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
)

fun loadGithubToken(ctx: Context): String = try {
    securePrefs(ctx).getString("gh", "") ?: ""
} catch (_: Exception) {
    ""
}

fun saveGithubToken(ctx: Context, token: String) {
    try {
        securePrefs(ctx).edit().putString("gh", token.trim().take(200)).apply()
    } catch (_: Exception) {
    }
}

// ---------- repo-name parsing (pure, tested) ----------

/**
 * Extract owner/repo or a bare repo name from a Router arg.
 * Rejects multi-word phrases (no slash) so normal chat falls through. Pure, tested.
 */
fun repoName(arg: String): String? {
    val s = arg.trim().trimEnd('?', '.', '!').trim()
    if (s.isEmpty()) return null
    if (s.contains(" ") && !s.contains("/")) return null
    if (!Regex("""^[A-Za-z0-9_.-]+(/[A-Za-z0-9_.-]+)?$""").matches(s)) return null
    return s
}

/** Best match for a query against full repo names. Pure, tested. */
fun resolveMatch(fullNames: List<String>, query: String): String? {
    val q = query.lowercase()
    fullNames.firstOrNull { it.lowercase() == q }?.let { return it }
    if (!q.contains("/")) {
        fullNames.firstOrNull { it.substringAfter("/").lowercase() == q }?.let { return it }
        fullNames.firstOrNull { it.substringAfter("/").lowercase().startsWith(q) }?.let { return it }
    }
    return fullNames.firstOrNull { q in it.lowercase() }
}

/** "2026-09-12T10:00:00Z" -> "3h". Pure, tested. */
fun agoShort(iso: String, nowMs: Long = System.currentTimeMillis()): String {
    return try {
        val then = java.time.Instant.parse(iso).toEpochMilli()
        val s = ((nowMs - then) / 1000).coerceAtLeast(0)
        when {
            s < 60 -> "${s}s"
            s < 3600 -> "${s / 60}m"
            s < 172800 -> "${s / 3600}h"
            else -> "${s / 86400}d"
        }
    } catch (_: Exception) {
        "—"
    }
}

// ---------- read-only GitHub API ----------

data class RepoInfo(val full: String, val desc: String, val stars: Int, val lang: String, val pushedAgo: String)
data class RepoBrief(
    val full: String, val desc: String, val stars: Int, val forks: Int,
    val openIssues: Int, val lang: String, val branch: String, val pushedAgo: String
)
data class RunBrief(val name: String, val branch: String, val state: String, val ago: String)
data class IssueBrief(val num: Int, val title: String)

object GithubApi {
    private val client by lazy { OkHttpClient.Builder().callTimeout(20, TimeUnit.SECONDS).build() }

    private suspend fun getRaw(token: String, path: String): String = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url("https://api.github.com$path")
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .build()
        client.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw IllegalStateException("HTTP ${resp.code}")
            body
        }
    }

    suspend fun listRepos(token: String): List<RepoInfo> {
        val arr = JSONArray(getRaw(token, "/user/repos?per_page=100&sort=pushed"))
        return List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            RepoInfo(
                o.optString("full_name"), o.optString("description"),
                o.optInt("stargazers_count"), o.optString("language"),
                agoShort(o.optString("pushed_at"))
            )
        }
    }

    suspend fun repoBrief(token: String, full: String): RepoBrief {
        val o = JSONObject(getRaw(token, "/repos/$full"))
        return RepoBrief(
            o.optString("full_name"), o.optString("description"),
            o.optInt("stargazers_count"), o.optInt("forks_count"),
            o.optInt("open_issues_count"), o.optString("language"),
            o.optString("default_branch"), agoShort(o.optString("pushed_at"))
        )
    }

    suspend fun runs(token: String, full: String): List<RunBrief> {
        val arr = JSONObject(getRaw(token, "/repos/$full/actions/runs?per_page=3"))
            .optJSONArray("workflow_runs") ?: return emptyList()
        return List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            RunBrief(
                o.optString("name"), o.optString("head_branch"),
                o.optString("conclusion").ifBlank { o.optString("status") },
                agoShort(o.optString("created_at"))
            )
        }
    }

    suspend fun issues(token: String, full: String): List<IssueBrief> {
        val arr = JSONArray(getRaw(token, "/repos/$full/issues?state=open&per_page=5"))
        return List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            val pr = if (o.has("pull_request")) " [PR]" else ""
            IssueBrief(o.optInt("number"), o.optString("title") + pr)
        }
    }

    /** Returns (text, truncated). Throws on dirs, huge blobs, missing files. */
    suspend fun fileText(token: String, full: String, path: String): Pair<String, Boolean> {
        val safe = path.replace(" ", "%20")
        val o = JSONObject(getRaw(token, "/repos/$full/contents/$safe"))
        if (o.optString("type") != "file") throw IllegalStateException("not a file")
        if (o.optLong("size") > 60000) throw IllegalStateException("too large")
        val raw = o.optString("content").replace("\\s".toRegex(), "")
        val text = android.util.Base64.decode(raw, android.util.Base64.DEFAULT).toString(Charsets.UTF_8)
        return if (text.length > 1500) text.take(1500) to true else text to false
    }
}

// ---------- reply formatting (pure, tested) ----------

fun formatRepoList(repos: List<RepoInfo>): String {
    if (repos.isEmpty()) return "No repos found on that account."
    return "Your repos:\n" + repos.take(10).joinToString("\n") { "• ${it.full} ★${it.stars}" } +
        if (repos.size > 10) "\n…and ${repos.size - 10} more." else ""
}

fun formatRepoBrief(b: RepoBrief): String {
    val sb = StringBuilder("\uD83D\uDC26 ${b.full}")
    if (b.desc.isNotBlank()) sb.append("\n${b.desc.take(140)}")
    sb.append("\n★ ${b.stars} ⑂ ${b.forks} ◉ ${b.openIssues} open")
    if (b.lang.isNotBlank()) sb.append(" · ${b.lang}")
    sb.append("\nBranch ${b.branch} · pushed ${b.pushedAgo} ago")
    return sb.toString()
}

fun formatRuns(full: String, runs: List<RunBrief>): String {
    if (runs.isEmpty()) return "$full has no workflow runs yet."
    val r = runs[0]
    val verdict = when (r.state.lowercase()) {
        "success" -> "passing ✓"
        "failure", "timed_out" -> "FAILING ✗"
        "cancelled" -> "cancelled"
        "in_progress" -> "building…"
        "queued", "requested", "waiting" -> "queued…"
        else -> r.state
    }
    val sb = StringBuilder("$full: $verdict — ${r.name} on ${r.branch} (${r.ago} ago)")
    runs.drop(1).take(2).forEach { sb.append("\n• ${it.name}: ${it.state} (${it.ago} ago)") }
    return sb.toString()
}

fun formatIssues(full: String, issues: List<IssueBrief>): String {
    if (issues.isEmpty()) return "No open issues on $full. ✓"
    return "Open issues on $full:\n" + issues.joinToString("\n") { "• #${it.num} ${it.title.take(80)}" }
}

fun formatFile(full: String, path: String, text: String, truncated: Boolean): String {
    if (text.isBlank()) return "\uD83D\uDCC4 $path on $full:\n(empty file)"
    return "\uD83D\uDCC4 $path on $full:\n$text" + if (truncated) "\n…(trimmed)" else ""
}
