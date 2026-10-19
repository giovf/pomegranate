package com.gankdat.pomegranate.ui.onboarding

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DestinationFoldersTest {
    @Test
    fun `internal storage subfolder`() {
        val info = DestinationFolders.describe("primary:Pictures/Pomegranate")
        assertEquals("primary", info.volume)
        assertEquals("Pictures/Pomegranate", info.path)
        assertFalse(info.isRemovable)
        assertTrue(info.isBackupFolder)
        assertEquals("Internal › Pictures › Pomegranate", info.label("Internal", "SD card"))
    }

    @Test
    fun `removable volume root`() {
        val info = DestinationFolders.describe("1234-ABCD:")
        assertEquals("1234-ABCD", info.volume)
        assertEquals("", info.path)
        assertTrue(info.isRemovable)
        assertFalse(info.isBackupFolder)
        assertEquals("SD card", info.label("Internal", "SD card"))
    }

    @Test
    fun `backup folder detection is case-insensitive and only looks at the first segment`() {
        assertTrue(DestinationFolders.describe("primary:dcim/Camera").isBackupFolder)
        assertTrue(DestinationFolders.describe("primary:Download").isBackupFolder)
        assertFalse(DestinationFolders.describe("primary:Pomegranate/DCIM").isBackupFolder)
        assertFalse(DestinationFolders.describe("primary:Pomegranate").isBackupFolder)
    }

    @Test
    fun `ids without a colon are treated as a volume root`() {
        val info = DestinationFolders.describe("weird")
        assertEquals("weird", info.volume)
        assertEquals("", info.path)
    }
}
