package dev.cernoh.quotes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The update check parses a GitHub release and compares version numbers. Both
 * decide whether the app offers an update, so both are worth a test: the live
 * call needs a token for a private repository.
 */
class UpdatesTest {
    private val payload = """
        {
          "tag_name": "v0.2",
          "name": "Quotes v0.2",
          "body": "Second release.",
          "draft": false,
          "prerelease": false,
          "assets": [
            {
              "id": 128849019,
              "name": "quotes-0.2-debug.apk",
              "browser_download_url": "https://github.com/cernoh/quotes/releases/download/v0.2/quotes-0.2-debug.apk"
            },
            {
              "id": 128849020,
              "name": "source.tar.gz",
              "browser_download_url": "https://github.com/cernoh/quotes/archive/v0.2.tar.gz"
            }
          ]
        }
    """.trimIndent()

    @Test
    fun parsesARelease() {
        val release = Updates.parseRelease(payload)
        assertNotNull(release)
        assertEquals("v0.2", release!!.tag)
        assertEquals("Quotes v0.2", release.name)
        assertEquals("Second release.", release.notes)
        assertEquals(128849019L, release.apkId)
    }

    @Test
    fun downloadsThroughTheApiAssetEndpoint() {
        // The browser address of a private repository needs a signed-in web
        // session. The API address works with the token.
        assertEquals(
            "https://api.github.com/repos/cernoh/quotes/releases/assets/128849019",
            Updates.assetUrl(128849019L),
        )
    }

    @Test
    fun parsesAReleaseWithoutAnApk() {
        val release = Updates.parseRelease(
            """{"tag_name": "v0.2", "assets": [{"id": 7, "name": "source.zip"}]}""",
        )
        assertNotNull(release)
        assertNull(release!!.apkId)
    }

    @Test
    fun refusesAnAssetWithoutAnId() {
        // An asset with no id cannot be fetched from the API endpoint.
        val release = Updates.parseRelease(
            """{"tag_name": "v0.2", "assets": [{"name": "quotes-0.2.apk"}]}""",
        )
        assertNotNull(release)
        assertNull(release!!.apkId)
    }

    @Test
    fun refusesRubbish() {
        assertNull(Updates.parseRelease("not json at all"))
        assertNull(Updates.parseRelease(""))
    }

    @Test
    fun comparesVersions() {
        assertTrue(Updates.newer("v0.2", "0.1.0"))
        assertTrue(Updates.newer("0.2.0", "0.1.0"))
        assertTrue(Updates.newer("v1.0", "0.9.9"))
        // Ten is later than nine, which a text compare would get wrong.
        assertTrue(Updates.newer("v0.1.10", "0.1.9"))
        assertFalse(Updates.newer("v0.1", "0.1.0"))
        assertFalse(Updates.newer("0.1.0", "v0.1"))
        assertFalse(Updates.newer("v0.1.9", "0.1.10"))
        // A tag the app cannot read must not look like an update.
        assertFalse(Updates.newer("nightly", "0.1.0"))
    }
}
