package com.gankdat.pomegranate.reconcile

import com.gankdat.pomegranate.data.db.entity.MediaGroupKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SiblingGrouperTest {
    private fun files(vararg names: String) = names.mapIndexed { i, n -> SiblingFile(i, n) }

    private fun kinds(vararg names: String) = SiblingGrouper.group(files(*names)).map { g -> g.kind to g.members.map { it.filename } }

    @Test
    fun `raw plus jpeg`() {
        assertEquals(listOf(MediaGroupKind.RAW_PAIR to listOf("DSC_0001.JPG", "DSC_0001.NEF")), kinds("DSC_0001.NEF", "DSC_0001.JPG", "DSC_0002.NEF"))
    }

    @Test
    fun `motion photo sidecar and MVIMG`() {
        assertEquals(listOf(MediaGroupKind.MOTION_PHOTO to listOf("PXL_1.MP.jpg", "PXL_1.mp4")), kinds("PXL_1.MP.jpg", "PXL_1.mp4"))
        assertEquals(listOf(MediaGroupKind.MOTION_PHOTO to listOf("MVIMG_2.jpg", "IMG_2.mp4")), kinds("MVIMG_2.jpg", "IMG_2.mp4"))
    }

    @Test
    fun `live photo`() {
        assertEquals(listOf(MediaGroupKind.LIVE_PHOTO to listOf("IMG_3.HEIC", "IMG_3.MOV")), kinds("IMG_3.HEIC", "IMG_3.MOV", "IMG_4.HEIC"))
    }

    @Test
    fun `original and edited`() {
        assertEquals(listOf(MediaGroupKind.EDIT_PAIR to listOf("IMG_5.jpg", "IMG_5_edited.jpg")), kinds("IMG_5.jpg", "IMG_5_edited.jpg"))
        assertEquals(listOf(MediaGroupKind.EDIT_PAIR to listOf("beach.jpg", "VSCO_beach.jpg")), kinds("beach.jpg", "VSCO_beach.jpg"))
        assertEquals(listOf(MediaGroupKind.EDIT_PAIR to listOf("IMG_6.jpg", "IMG_6~2.jpg")), kinds("IMG_6.jpg", "IMG_6~2.jpg"))
    }

    @Test
    fun `a file joins at most one group and unrelated files stay single`() {
        val groups = SiblingGrouper.group(files("A.dng", "A.jpg", "A.mp4", "B.jpg", "C.png"))
        assertEquals(1, groups.size)
        assertEquals(MediaGroupKind.RAW_PAIR, groups.single().kind)
        assertTrue(SiblingGrouper.group(files("x.jpg", "y.jpg")).isEmpty())
    }
}
