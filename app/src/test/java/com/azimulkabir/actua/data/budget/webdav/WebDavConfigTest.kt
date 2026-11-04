package com.azimulkabir.actua.data.budget.webdav

import com.azimulkabir.actua.data.budget.webdav.WebDavException.InvalidSettings.Reason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class WebDavConfigTest {
    private fun config(
        url: String = "https://cloud.example.com/remote.php/dav/files/alice/",
        user: String = "alice",
        password: String = "app-pass",
        folder: String = "Actua Backups",
        allowHttp: Boolean = false,
    ) = WebDavConfig(url, user, password, folder, allowHttp)

    private fun reasonOf(block: () -> Unit): Reason =
        assertThrows(WebDavException.InvalidSettings::class.java, block).reason

    @Test fun anHttpsAddressWithAFolderBuildsTheFolderUrl() {
        assertEquals(
            "https://cloud.example.com/remote.php/dav/files/alice/Actua%20Backups",
            config().folderUrl().toString(),
        )
    }

    @Test fun theAddressMayOmitItsTrailingSlash() {
        assertEquals(
            "https://nas.local:5006/home/Actua",
            config(url = "https://nas.local:5006/home", folder = "Actua").folderUrl().toString(),
        )
    }

    @Test fun nestedFoldersAreSplitAndTidied() {
        assertEquals(listOf("Backups", "Actua"), WebDavConfig.parseFolder(" /Backups//Actua/ "))
        assertEquals(emptyList<String>(), WebDavConfig.parseFolder(""))
    }

    @Test fun foldersThatCouldEscapeAreRefused() {
        listOf("..", "a/../b", ".", "a\\b", "a\u0000b").forEach { folder ->
            assertEquals(Reason.INVALID_FOLDER, reasonOf { WebDavConfig.parseFolder(folder) })
        }
    }

    @Test fun plainHttpIsRefusedUnlessExplicitlyAllowed() {
        assertEquals(Reason.INSECURE_HTTP, reasonOf { config(url = "http://nas.local:5005/").baseUrl() })
        assertEquals("http://nas.local:5005/", config(url = "http://nas.local:5005/", allowHttp = true).baseUrl().toString())
    }

    @Test fun addressesThatAreNotUsableAreRefused() {
        listOf("", "nas.local", "ftp://nas.local/", "https://nas.local/?x=1", "https://nas.local/#top").forEach { url ->
            assertEquals(Reason.INVALID_URL, reasonOf { config(url = url).baseUrl() })
        }
    }

    @Test fun missingCredentialsAreRefused() {
        assertEquals(Reason.MISSING_CREDENTIALS, reasonOf { config(user = " ").baseUrl() })
        assertEquals(Reason.MISSING_CREDENTIALS, reasonOf { config(password = "").baseUrl() })
    }
}
