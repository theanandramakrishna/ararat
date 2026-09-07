package org.anandram.xwordapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val GID = "46db7850-5806-46ec-8baf-85b7ee60232d"

/**
 * Guards the strict share-to-join URL validation:
 * only http(s) links to a Cross With Friends host carrying a
 * `/beta/game/<gid>` room yield a room id; everything else is rejected.
 * Also guards our own app links (`lexikattam://crosswithfriends/game/<gid>`)
 * and their round-trip through [CrossWithFriendsSubscription.appGameUrl].
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class CrossWithFriendsShareTest {

    @Test
    fun validGameUrl() {
        assertEquals(GID, CrossWithFriendsSubscription.gidFromShareUrl(
                "https://www.crosswithfriends.com/beta/game/$GID"))
    }

    @Test
    fun validWithoutWww() {
        assertEquals(GID, CrossWithFriendsSubscription.gidFromShareUrl(
                "https://crosswithfriends.com/beta/game/$GID"))
    }

    @Test
    fun validDashlessHexGid() {
        assertEquals(GID, CrossWithFriendsSubscription.gidFromShareUrl(
                "https://www.crosswithfriends.com/beta/game/46db7850580646ec8baf85b7ee60232d"))
    }

    @Test
    fun validUppercaseGid() {
        assertEquals(GID, CrossWithFriendsSubscription.gidFromShareUrl(
                "https://www.crosswithfriends.com/beta/game/${GID.uppercase()}"))
    }

    @Test
    fun acceptShortSlugGid() {
        assertEquals("102650123-cesk", CrossWithFriendsSubscription.gidFromShareUrl(
                "https://www.crosswithfriends.com/beta/game/102650123-cesk"))
    }

    @Test
    fun validTrailingSlash() {
        assertEquals(GID, CrossWithFriendsSubscription.gidFromShareUrl(
                "https://www.crosswithfriends.com/beta/game/$GID/"))
    }

    @Test
    fun validQueryString() {
        assertEquals(GID, CrossWithFriendsSubscription.gidFromShareUrl(
                "https://www.crosswithfriends.com/beta/game/$GID?source=share"))
    }

    @Test
    fun validPlainHttp() {
        assertEquals(GID, CrossWithFriendsSubscription.gidFromShareUrl(
                "http://www.crosswithfriends.com/beta/game/$GID"))
    }

    @Test
    fun rejectForeignHost() {
        assertNull(CrossWithFriendsSubscription.gidFromShareUrl(
                "https://twitter.com/beta/game/$GID"))
    }

    @Test
    fun rejectLookalikeHost() {
        assertNull(CrossWithFriendsSubscription.gidFromShareUrl(
                "https://crosswithfriends.com.evil.io/beta/game/$GID"))
    }

    @Test
    fun rejectNoRoom() {
        assertNull(CrossWithFriendsSubscription.gidFromShareUrl(
                "https://www.crosswithfriends.com/"))
    }

    @Test
    fun rejectWrongPath() {
        assertNull(CrossWithFriendsSubscription.gidFromShareUrl(
                "https://www.crosswithfriends.com/beta/games/$GID"))
    }

    @Test
    fun rejectSlugWithSlash() {
        assertNull(CrossWithFriendsSubscription.gidFromShareUrl(
                "https://www.crosswithfriends.com/beta/game/102650123-cesk/extra"))
    }

    @Test
    fun rejectBareGid() {
        assertNull(CrossWithFriendsSubscription.gidFromShareUrl(GID))
    }

    @Test
    fun rejectNonUrlText() {
        assertNull(CrossWithFriendsSubscription.gidFromShareUrl(
                "join my cross with friends game"))
    }

    @Test
    fun gidFromGameUrlStillNormalizesDashless() {
        assertEquals(GID, CrossWithFriendsSubscription.gidFromGameUrl(
                "46db7850580646ec8baf85b7ee60232d"))
    }

    @Test
    fun appLinkValid() {
        assertEquals(GID, CrossWithFriendsSubscription.gidFromAppUrl(
                "lexikattam://crosswithfriends/game/$GID"))
    }

    @Test
    fun appLinkDashlessNormalized() {
        assertEquals(GID, CrossWithFriendsSubscription.gidFromAppUrl(
                "lexikattam://crosswithfriends/game/46db7850580646ec8baf85b7ee60232d"))
    }

    @Test
    fun appLinkRoundTrip() {
        assertEquals(GID, CrossWithFriendsSubscription.gidFromAppUrl(
                CrossWithFriendsSubscription.appGameUrl(GID)))
    }

    @Test
    fun appLinkRejectWrongHost() {
        assertNull(CrossWithFriendsSubscription.gidFromAppUrl(
                "lexikattam://other/game/$GID"))
    }

    @Test
    fun appLinkRejectWrongScheme() {
        assertNull(CrossWithFriendsSubscription.gidFromAppUrl(
                "https://crosswithfriends/game/$GID"))
    }

    @Test
    fun appLinkRejectWrongPath() {
        assertNull(CrossWithFriendsSubscription.gidFromAppUrl(
                "lexikattam://crosswithfriends/beta/game/$GID"))
    }

    @Test
    fun appLinkRejectHttpsGameUrl() {
        assertNull(CrossWithFriendsSubscription.gidFromAppUrl(
                "https://www.crosswithfriends.com/beta/game/$GID"))
    }

    @Test
    fun shareUrlRejectsAppLink() {
        assertNull(CrossWithFriendsSubscription.gidFromShareUrl(
                "lexikattam://crosswithfriends/game/$GID"))
    }
}