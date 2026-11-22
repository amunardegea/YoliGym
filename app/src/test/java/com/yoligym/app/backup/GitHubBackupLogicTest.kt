package com.yoligym.app.backup

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.util.Base64
import java.util.Calendar
import java.util.TimeZone

class GitHubBackupLogicTest {

    @Test
    fun `sanitizeUserId normalizes accents spaces symbols length and empty inputs`() {
        assertEquals("yoli", GitHubBackupNaming.sanitizeUserId("Yoli"))
        assertEquals("andres-m", GitHubBackupNaming.sanitizeUserId("  Andrés M.  "))
        assertEquals("pepe-garcia-2", GitHubBackupNaming.sanitizeUserId("Pepe__García!! #2"))
        assertEquals("", GitHubBackupNaming.sanitizeUserId("   ---___   "))

        val longInput = "usuario-con-nombre-demasiado-largo-para-ruta-12345"
        val sanitizedLong = GitHubBackupNaming.sanitizeUserId(longInput)
        assertTrue(sanitizedLong.length <= 32)
        assertFalse(sanitizedLong.endsWith("-"))
        assertEquals("usuario-con-nombre-demasiado-lar", sanitizedLong)
    }

    @Test
    fun `parseOwnerRepo accepts shorthand and GitHub URLs and rejects invalid values`() {
        assertEquals("amunardegea" to "yoligym-backups", GitHubBackupNaming.parseOwnerRepo("amunardegea/yoligym-backups"))
        assertEquals("amunardegea" to "yoligym.backups", GitHubBackupNaming.parseOwnerRepo("https://github.com/amunardegea/yoligym.backups"))
        assertEquals("amunardegea" to "yoligym-backups", GitHubBackupNaming.parseOwnerRepo("https://github.com/amunardegea/yoligym-backups.git"))
        assertEquals("amunardegea" to "yoligym-backups", GitHubBackupNaming.parseOwnerRepo("http://github.com/amunardegea/yoligym-backups/"))
        assertEquals("amunardegea" to "yoligym-backups", GitHubBackupNaming.parseOwnerRepo("github.com/amunardegea/yoligym-backups/"))

        assertNull(GitHubBackupNaming.parseOwnerRepo(""))
        assertNull(GitHubBackupNaming.parseOwnerRepo("solo-repo"))
        assertNull(GitHubBackupNaming.parseOwnerRepo("owner/repo/extra"))
        assertNull(GitHubBackupNaming.parseOwnerRepo("owner/.."))
        assertNull(GitHubBackupNaming.parseOwnerRepo("owner/repo with spaces"))
    }

    @Test
    fun `daily file paths and user isolation prevent prefix collisions`() {
        val date = LocalDate.of(2026, 11, 22)
        assertEquals("yoligym_backup_yoli_2026-11-22.json", GitHubBackupNaming.buildDailyFileName("Yoli", date))
        assertEquals("backups/yoli/yoligym_backup_yoli_2026-11-22.json", GitHubBackupNaming.buildDailyFilePath("Yoli", date))

        assertTrue(GitHubBackupNaming.matchesUserDailyFile("yoligym_backup_yoli_2026-11-22.json", "yoli"))
        assertFalse(GitHubBackupNaming.matchesUserDailyFile("yoligym_backup_yoli-2_2026-11-22.json", "yoli"))
        assertFalse(GitHubBackupNaming.matchesUserDailyFile("yoligym_backup_andres_2026-11-22.json", "yoli"))
        assertFalse(GitHubBackupNaming.matchesUserDailyFile("yoligym_backup_yoli_20261122.json", "yoli"))
        assertFalse(GitHubBackupNaming.matchesUserDailyFile("README.md", "yoli"))

        assertEquals("2026-11-22", GitHubBackupNaming.extractDateLabel("yoligym_backup_yoli-2_2026-11-22.json"))
        assertNull(GitHubBackupNaming.extractDateLabel("README.md"))
    }

    @Test
    fun `selectFilesToDelete only prunes target user backups beyond 30 days`() {
        fun makeFile(user: String, date: LocalDate): GitHubBackupFile {
            val fileName = GitHubBackupNaming.buildDailyFileName(user, date)
            return GitHubBackupFile(
                name = fileName,
                path = "${GitHubBackupNaming.buildUserFolderPath(user)}/$fileName",
                sha = "sha_$fileName",
                sizeBytes = 2048L
            )
        }

        val baseDate = LocalDate.of(2026, 10, 1)
        val yoliFiles = (0 until 45).map { offset -> makeFile("yoli", baseDate.plusDays(offset.toLong())) }
        val andresFiles = (0 until 5).map { offset -> makeFile("andres", baseDate.plusDays(offset.toLong())) }
        val yoli2Files = listOf(makeFile("yoli-2", baseDate))
        val unrelated = listOf(
            GitHubBackupFile("README.md", "backups/yoli/README.md", "sha_readme", 120L),
            GitHubBackupFile("manual.json", "backups/yoli/manual.json", "sha_manual", 512L)
        )

        val mixed = (yoliFiles + andresFiles + yoli2Files + unrelated).shuffled()
        val toDelete = GitHubBackupNaming.selectFilesToDelete(mixed, userId = "Yoli", maxKeep = 30)

        assertEquals(15, toDelete.size)
        val deletedNames = toDelete.map { it.name }.toSet()

        for (offset in 0 until 15) {
            val expectedOld = GitHubBackupNaming.buildDailyFileName("yoli", baseDate.plusDays(offset.toLong()))
            assertTrue("Expected $expectedOld to be deleted", expectedOld in deletedNames)
        }
        for (offset in 15 until 45) {
            val expectedKept = GitHubBackupNaming.buildDailyFileName("yoli", baseDate.plusDays(offset.toLong()))
            assertFalse("Expected $expectedKept to be kept", expectedKept in deletedNames)
        }
        andresFiles.forEach { assertFalse(it.name in deletedNames) }
        yoli2Files.forEach { assertFalse(it.name in deletedNames) }
        unrelated.forEach { assertFalse(it.name in deletedNames) }
    }

    @Test
    fun `computeDelayToNextHourMillis isBackupDueToday and maskToken behave correctly`() {
        val utc = TimeZone.getTimeZone("UTC")
        fun utcMillis(year: Int, monthZeroBased: Int, day: Int, hour: Int, minute: Int, second: Int): Long {
            return Calendar.getInstance(utc).apply {
                set(year, monthZeroBased, day, hour, minute, second)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
        }

        val beforeNow = utcMillis(2026, Calendar.NOVEMBER, 22, 1, 30, 0)
        assertEquals(
            90 * 60_000L,
            GitHubBackupNaming.computeDelayToNextHourMillis(beforeNow, targetHour = 3, timeZone = utc)
        )

        val afterNow = utcMillis(2026, Calendar.NOVEMBER, 22, 10, 0, 0)
        assertEquals(
            17 * 3600_000L,
            GitHubBackupNaming.computeDelayToNextHourMillis(afterNow, targetHour = 3, timeZone = utc)
        )

        val atTargetNow = utcMillis(2026, Calendar.NOVEMBER, 22, 3, 0, 0)
        assertEquals(
            24 * 3600_000L,
            GitHubBackupNaming.computeDelayToNextHourMillis(atTargetNow, targetHour = 3, timeZone = utc)
        )

        val today = LocalDate.of(2026, 11, 22)
        assertTrue(GitHubBackupNaming.isBackupDueToday(null, today))
        assertTrue(GitHubBackupNaming.isBackupDueToday("  ", today))
        assertTrue(GitHubBackupNaming.isBackupDueToday("2026-11-21 22:00:00", today))
        assertTrue(GitHubBackupNaming.isBackupDueToday("bad-ts", today))
        assertFalse(GitHubBackupNaming.isBackupDueToday("2026-11-22 03:15:00", today))

        assertEquals("", GitHubBackupNaming.maskToken(null))
        assertEquals("", GitHubBackupNaming.maskToken("   "))
        assertEquals("••••", GitHubBackupNaming.maskToken("12345678"))
        assertEquals("gith••••wxyz", GitHubBackupNaming.maskToken("github_pat_12345wxyz"))
    }

    @Test
    fun `GitHubRestClient parses repo listing sha and builds UTF8 base64 payloads`() {
        val repoInfo = GitHubRestClient.parseRepoInfo(
            """
            {
              "name": "yoligym-backups",
              "private": true,
              "default_branch": "main",
              "owner": { "login": "amunardegea" },
              "permissions": { "pull": true, "push": true }
            }
            """.trimIndent()
        )
        assertEquals("amunardegea", repoInfo.owner)
        assertEquals("yoligym-backups", repoInfo.name)
        assertEquals("main", repoInfo.defaultBranch)
        assertTrue(repoInfo.isPrivate)
        assertTrue(repoInfo.canPush)

        assertEquals("abc123sha", GitHubRestClient.parseFileSha("""{"sha":"abc123sha"}"""))
        assertNull(GitHubRestClient.parseFileSha("""{}"""))

        val listingJson = """
            [
              {
                "type": "file",
                "name": "yoligym_backup_yoli_2026-11-22.json",
                "path": "backups/yoli/yoligym_backup_yoli_2026-11-22.json",
                "sha": "sha111",
                "size": 4096
              },
              {
                "type": "dir",
                "name": "subdir",
                "path": "backups/yoli/subdir",
                "sha": "sha_dir",
                "size": 0
              }
            ]
        """.trimIndent()
        val files = GitHubRestClient.parseDirectoryListing(listingJson)
        assertEquals(1, files.size)
        assertEquals("yoligym_backup_yoli_2026-11-22.json", files[0].name)
        assertEquals("backups/yoli/yoligym_backup_yoli_2026-11-22.json", files[0].path)
        assertEquals("sha111", files[0].sha)
        assertEquals(4096L, files[0].sizeBytes)

        val sampleJson = """{"app":"YoliGym","note":"Press de banca inclinación 30º · año"}"""
        val putCreate = JSONObject(
            GitHubRestClient.buildPutContentPayload(
                message = "YoliGym backup (yoli) 2026-11-22",
                utf8JsonContent = sampleJson,
                branch = "main",
                existingSha = null
            )
        )
        assertFalse(putCreate.has("sha"))
        assertEquals("main", putCreate.getString("branch"))
        val decoded = String(Base64.getDecoder().decode(putCreate.getString("content")), Charsets.UTF_8)
        assertEquals(sampleJson, decoded)

        val putUpdate = JSONObject(
            GitHubRestClient.buildPutContentPayload(
                message = "YoliGym backup (yoli) 2026-11-22",
                utf8JsonContent = sampleJson,
                branch = "main",
                existingSha = "oldsha999"
            )
        )
        assertEquals("oldsha999", putUpdate.getString("sha"))

        val deletePayload = JSONObject(
            GitHubRestClient.buildDeletePayload(
                message = "Prune old backup",
                sha = "oldsha999",
                branch = "main"
            )
        )
        assertEquals("Prune old backup", deletePayload.getString("message"))
        assertEquals("oldsha999", deletePayload.getString("sha"))
        assertEquals("main", deletePayload.getString("branch"))
    }
}
