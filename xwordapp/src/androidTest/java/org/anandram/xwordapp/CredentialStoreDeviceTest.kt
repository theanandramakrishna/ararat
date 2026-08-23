package org.anandram.xwordapp

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Verifies the Keystore-backed credential store on a real Android system. */
@RunWith(AndroidJUnit4::class)
class CredentialStoreDeviceTest {

    @Test
    fun roundTripIsolationAndOverwrite() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        CredentialStore.init(context)

        assertFalse(CredentialStore.has("Sub A"))
        assertNull(CredentialStore.get("Sub A"))

        CredentialStore.set("Sub A", "alice", "s3cret")
        CredentialStore.set("Sub B", "bob", "hunter2")

        assertTrue(CredentialStore.has("Sub A"))
        assertEquals("alice" to "s3cret", CredentialStore.get("Sub A"))
        assertEquals("bob" to "hunter2", CredentialStore.get("Sub B"))

        // Overwriting keeps entries independent.
        CredentialStore.set("Sub A", "carol", "newpass")
        assertEquals("carol" to "newpass", CredentialStore.get("Sub A"))
        assertEquals("bob" to "hunter2", CredentialStore.get("Sub B"))
    }

    @Test
    fun credentialsPersistAcrossReinit() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        CredentialStore.init(context)
        CredentialStore.set("Persistent", "dana", "pw123")

        // A second init must not wipe or shadow existing storage.
        CredentialStore.init(context)

        assertEquals("dana" to "pw123", CredentialStore.get("Persistent"))
    }
}
