package org.levimc.launcher.ui.activities

import android.content.Context
import android.content.pm.ProviderInfo
import android.content.res.AssetFileDescriptor
import android.database.Cursor
import android.database.MatrixCursor
import android.graphics.Point
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.DocumentsProvider
import android.webkit.MimeTypeMap
import java.io.File
import java.io.FileNotFoundException
import org.levimc.launcher.R

class LeviExternalDocumentsProvider : DocumentsProvider() {

    private lateinit var baseDir: File

    private companion object {
        const val ALL_MIME_TYPES = "*/*"
        private val DEFAULT_ROOT_PROJECTION = arrayOf(
            DocumentsContract.Root.COLUMN_ROOT_ID,
            DocumentsContract.Root.COLUMN_MIME_TYPES,
            DocumentsContract.Root.COLUMN_FLAGS,
            DocumentsContract.Root.COLUMN_ICON,
            DocumentsContract.Root.COLUMN_TITLE,
            DocumentsContract.Root.COLUMN_SUMMARY,
            DocumentsContract.Root.COLUMN_DOCUMENT_ID,
            DocumentsContract.Root.COLUMN_AVAILABLE_BYTES
        )
        private val DEFAULT_DOCUMENT_PROJECTION = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
            DocumentsContract.Document.COLUMN_FLAGS,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_ICON
        )
    }

    override fun attachInfo(context: Context, info: ProviderInfo) {
        try {
            super.attachInfo(context, info)
        } catch (_: SecurityException) {
        }
    }

    override fun onCreate(): Boolean {
        baseDir = context!!.getExternalFilesDir(null)!!
        if (!baseDir.exists()) baseDir.mkdirs()
        return true
    }

    override fun queryRoots(projection: Array<out String>?): Cursor {
        val result = MatrixCursor(resolveRootProjection(projection))
        val row = result.newRow()
        row.add(DocumentsContract.Root.COLUMN_ROOT_ID, getDocIdForFile(baseDir))
        row.add(DocumentsContract.Root.COLUMN_DOCUMENT_ID, getDocIdForFile(baseDir))
        row.add(DocumentsContract.Root.COLUMN_FLAGS, getRootFlags())
        row.add(DocumentsContract.Root.COLUMN_ICON, R.mipmap.ic_launcher)
        row.add(DocumentsContract.Root.COLUMN_TITLE, "LeviLauncher - External")
        row.add(DocumentsContract.Root.COLUMN_SUMMARY, "External Storage")
        row.add(DocumentsContract.Root.COLUMN_MIME_TYPES, ALL_MIME_TYPES)
        row.add(DocumentsContract.Root.COLUMN_AVAILABLE_BYTES, baseDir.freeSpace)
        return result
    }

    override fun queryChildDocuments(
        parentDocumentId: String,
        projection: Array<out String>?,
        queryArgs: Bundle?
    ): Cursor {
        val result = MatrixCursor(resolveDocumentProjection(projection))
        val parent = getFileForDocId(parentDocumentId)
        parent.listFiles()?.forEach { includeFile(result, null, it) }
        return result
    }

    override fun queryChildDocuments(
        parentDocumentId: String,
        projection: Array<out String>?,
        sortOrder: String
    ): Cursor {
        return queryChildDocuments(parentDocumentId, projection, null)
    }

    override fun queryDocument(documentId: String, projection: Array<out String>?): Cursor {
        val result = MatrixCursor(resolveDocumentProjection(projection))
        includeFile(result, documentId, null)
        return result
    }

    override fun getDocumentType(documentId: String): String {
        return getTypeForFile(getFileForDocId(documentId))
    }

    override fun openDocument(
        documentId: String,
        mode: String,
        signal: CancellationSignal?
    ): ParcelFileDescriptor {
        return ParcelFileDescriptor.open(
            getFileForDocId(documentId),
            ParcelFileDescriptor.parseMode(mode)
        )
    }

    override fun openDocumentThumbnail(
        documentId: String,
        sizeHint: Point,
        signal: CancellationSignal
    ): AssetFileDescriptor {
        val file = getFileForDocId(documentId)
        val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        return AssetFileDescriptor(pfd, 0, file.length())
    }

    override fun createDocument(
        parentDocumentId: String,
        mimeType: String,
        displayName: String
    ): String {
        val parent = getFileForDocId(parentDocumentId)
        val file = File(parent, displayName)
        if (mimeType == DocumentsContract.Document.MIME_TYPE_DIR) {
            if (!file.mkdir()) throw FileNotFoundException()
        } else {
            if (!file.createNewFile()) throw FileNotFoundException()
        }
        return getDocIdForFile(file)
    }

    override fun deleteDocument(documentId: String) {
        val file = getFileForDocId(documentId)
        if (file.isDirectory) file.deleteRecursively() else file.delete()
    }

    override fun renameDocument(documentId: String, displayName: String): String {
        val file = getFileForDocId(documentId)
        val newFile = File(file.parentFile, displayName)
        if (!file.renameTo(newFile)) throw FileNotFoundException()
        return getDocIdForFile(newFile)
    }

    override fun moveDocument(
        sourceDocumentId: String,
        sourceParentDocumentId: String,
        targetParentDocumentId: String
    ): String {
        val source = getFileForDocId(sourceDocumentId)
        val targetParent = getFileForDocId(targetParentDocumentId)
        val target = File(targetParent, source.name)
        if (!source.renameTo(target)) throw FileNotFoundException()
        return getDocIdForFile(target)
    }

    override fun copyDocument(
        sourceDocumentId: String,
        targetParentDocumentId: String
    ): String {
        val source = getFileForDocId(sourceDocumentId)
        val targetParent = getFileForDocId(targetParentDocumentId)
        val target = File(targetParent, source.name)
        source.copyTo(target, true)
        return getDocIdForFile(target)
    }

    override fun isChildDocument(parentDocumentId: String, documentId: String): Boolean {
        return documentId.startsWith(parentDocumentId)
    }

    private fun getRootFlags(): Long {
        var flags = DocumentsContract.Root.FLAG_LOCAL_ONLY.toLong()
        flags = flags or DocumentsContract.Root.FLAG_SUPPORTS_CREATE.toLong()
        flags = flags or DocumentsContract.Root.FLAG_SUPPORTS_IS_CHILD.toLong()
        return flags
    }

    private fun resolveRootProjection(projection: Array<out String>?): Array<String> {
        return projection as? Array<String> ?: DEFAULT_ROOT_PROJECTION
    }

    private fun resolveDocumentProjection(projection: Array<out String>?): Array<String> {
        return projection as? Array<String> ?: DEFAULT_DOCUMENT_PROJECTION
    }

    private fun getTypeForFile(file: File): String {
        return if (file.isDirectory)
            DocumentsContract.Document.MIME_TYPE_DIR
        else
            getTypeForName(file.name)
    }

    private fun getTypeForName(name: String): String {
        val dot = name.lastIndexOf('.')
        if (dot >= 0) {
            MimeTypeMap.getSingleton()
                .getMimeTypeFromExtension(name.substring(dot + 1))
                ?.let { return it }
        }
        return "application/octet-stream"
    }

    private fun includeFile(result: MatrixCursor, docId: String?, file: File?) {
        val id = docId ?: getDocIdForFile(file!!)
        val f = file ?: getFileForDocId(id)

        var flags = 0L
        if (f.canWrite()) {
            flags = flags or DocumentsContract.Document.FLAG_SUPPORTS_DELETE.toLong()
            flags = flags or DocumentsContract.Document.FLAG_SUPPORTS_RENAME.toLong()
            if (f.isDirectory) {
                flags = flags or DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE.toLong()
            } else {
                flags = flags or DocumentsContract.Document.FLAG_SUPPORTS_WRITE.toLong()
            }
        }

        if (getTypeForFile(f).startsWith("image/")) {
            flags = flags or DocumentsContract.Document.FLAG_SUPPORTS_THUMBNAIL.toLong()
        }

        val row = result.newRow()
        row.add(DocumentsContract.Document.COLUMN_DOCUMENT_ID, id)
        row.add(DocumentsContract.Document.COLUMN_DISPLAY_NAME, f.name)
        row.add(DocumentsContract.Document.COLUMN_SIZE, f.length())
        row.add(DocumentsContract.Document.COLUMN_MIME_TYPE, getTypeForFile(f))
        row.add(DocumentsContract.Document.COLUMN_LAST_MODIFIED, f.lastModified())
        row.add(DocumentsContract.Document.COLUMN_FLAGS, flags)
        row.add(DocumentsContract.Document.COLUMN_ICON, getFileIcon(f))
    }

    private fun getFileIcon(file: File): Int {
        return if (file.isDirectory) 0
        else if (getTypeForFile(file).startsWith("image/")) android.R.drawable.ic_menu_gallery
        else 0
    }

    private fun getDocIdForFile(file: File): String = file.absolutePath

    private fun getFileForDocId(docId: String): File {
        val file = File(docId)
        if (!file.exists()) throw FileNotFoundException()
        return file
    }
}