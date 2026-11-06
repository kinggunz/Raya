package com.ayaraya.voicegunz

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import java.io.File
import java.io.FileNotFoundException

/** Membagikan file rekaman ke aplikasi lain (WA dll) tanpa library tambahan. */
class ShareProvider : ContentProvider() {

    companion object {
        const val AUTHORITY = "com.ayaraya.voicegunz.share"

        fun dir(c: Context): File = File(c.cacheDir, "rec").apply { mkdirs() }

        fun uriFor(name: String): Uri = Uri.parse("content://$AUTHORITY/rec/$name")
    }

    override fun onCreate(): Boolean = true

    private fun fileFor(uri: Uri): File? {
        val c = context ?: return null
        val name = uri.lastPathSegment ?: return null
        val base = dir(c)
        val f = File(base, name)
        val ok = f.canonicalPath.startsWith(base.canonicalPath + File.separator) && f.exists()
        return if (ok) f else null
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        val f = fileFor(uri) ?: throw FileNotFoundException(uri.toString())
        return ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun query(
        uri: Uri,
        projection: Array<String>?,
        selection: String?,
        selectionArgs: Array<String>?,
        sortOrder: String?
    ): Cursor? {
        val f = fileFor(uri) ?: return null
        val cols = projection ?: arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        val row = arrayOfNulls<Any>(cols.size)
        for (i in cols.indices) {
            row[i] = when (cols[i]) {
                OpenableColumns.DISPLAY_NAME -> f.name
                OpenableColumns.SIZE -> f.length()
                else -> null
            }
        }
        val cursor = MatrixCursor(cols)
        cursor.addRow(row)
        return cursor
    }

    override fun getType(uri: Uri): String = "audio/mp4"

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<String>?
    ): Int = 0
}
