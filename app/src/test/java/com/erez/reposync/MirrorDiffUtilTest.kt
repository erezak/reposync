package com.erez.reposync

import com.erez.reposync.data.repo.MirrorDiffUtil
import org.junit.Assert.assertEquals
import org.junit.Test

class MirrorDiffUtilTest {
    @Test
    fun detectsAddedModifiedDeleted() {
        val previous = mapOf(
            "a.txt" to MirrorDiffUtil.Snapshot(10, 1000),
            "b.txt" to MirrorDiffUtil.Snapshot(20, 2000)
        )
        val current = mapOf(
            "a.txt" to MirrorDiffUtil.Snapshot(10, 1000),
            "b.txt" to MirrorDiffUtil.Snapshot(25, 3000),
            "c.txt" to MirrorDiffUtil.Snapshot(5, 1500)
        )
        val diff = MirrorDiffUtil.diff(previous, current)
        assertEquals(listOf("c.txt"), diff.added.sorted())
        assertEquals(listOf("b.txt"), diff.modified.sorted())
        assertEquals(emptyList<String>(), diff.deleted.sorted())
    }

    @Test
    fun detectsDeleted() {
        val previous = mapOf(
            "a.txt" to MirrorDiffUtil.Snapshot(10, 1000)
        )
        val current = emptyMap<String, MirrorDiffUtil.Snapshot>()
        val diff = MirrorDiffUtil.diff(previous, current)
        assertEquals(emptyList<String>(), diff.added)
        assertEquals(emptyList<String>(), diff.modified)
        assertEquals(listOf("a.txt"), diff.deleted)
    }
}
