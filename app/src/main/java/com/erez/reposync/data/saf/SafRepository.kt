package com.erez.reposync.data.saf

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile

class SafRepository(private val context: Context) {
    fun persistTreePermission(treeUri: Uri) {
        val flags =
            IntentFlags.FLAG_GRANT_READ or IntentFlags.FLAG_GRANT_WRITE
        runCatching {
            context.contentResolver.takePersistableUriPermission(treeUri, flags)
        }
    }

    fun getTreeDocument(treeUri: Uri): DocumentFile? {
        return DocumentFile.fromTreeUri(context, treeUri)
    }

    fun listChildren(tree: DocumentFile): List<DocumentFile> {
        return tree.listFiles().toList()
    }

    fun openInput(uri: Uri) = context.contentResolver.openInputStream(uri)

    fun openOutput(uri: Uri) = context.contentResolver.openOutputStream(uri)

    fun getContentResolver(): ContentResolver = context.contentResolver
}

object IntentFlags {
    const val FLAG_GRANT_READ = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
    const val FLAG_GRANT_WRITE = android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
}
