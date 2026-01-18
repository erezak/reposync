package com.erez.reposync.data.repo

import com.erez.reposync.data.model.MirrorDiff

object MirrorDiffUtil {
    data class Snapshot(val sizeBytes: Long, val modifiedTimeEpochMillis: Long?)

    fun diff(previous: Map<String, Snapshot>, current: Map<String, Snapshot>): MirrorDiff {
        val added = current.keys - previous.keys
        val deleted = previous.keys - current.keys
        val modified = current.keys.intersect(previous.keys).filter { path ->
            val prev = previous[path]
            val curr = current[path]
            prev != null && curr != null && (prev.sizeBytes != curr.sizeBytes || prev.modifiedTimeEpochMillis != curr.modifiedTimeEpochMillis)
        }
        return MirrorDiff(
            added = added.toList(),
            modified = modified.toList(),
            deleted = deleted.toList()
        )
    }
}
