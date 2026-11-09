package com.example.util

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.documentfile.provider.DocumentFile
import com.example.model.StatusMedia
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream

object StorageHelper {
    private const val TAG = "StorageHelper"
    private const val PREFS_NAME = "status_saver_prefs"
    private const val KEY_WHATSAPP_URI = "key_whatsapp_tree_uri"
    private const val KEY_WA_BUSINESS_URI = "key_wa_business_tree_uri"

    const val PACKAGE_WHATSAPP = "com.whatsapp"
    const val PACKAGE_WA_BUSINESS = "com.whatsapp.w4b"

    // Relative paths
    private const val PATH_WA_ANDROID_11 = "Android/media/com.whatsapp/WhatsApp/Media/.Statuses"
    private const val PATH_WA_LEGACY = "WhatsApp/Media/.Statuses"

    private const val PATH_BUSINESS_ANDROID_11 = "Android/media/com.whatsapp.w4b/WhatsApp Business/Media/.Statuses"
    private const val PATH_BUSINESS_LEGACY = "WhatsApp Business/Media/.Statuses"

    fun getRelativeStatusPath(isBusiness: Boolean): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (isBusiness) PATH_BUSINESS_ANDROID_11 else PATH_WA_ANDROID_11
        } else {
            if (isBusiness) PATH_BUSINESS_LEGACY else PATH_WA_LEGACY
        }
    }

    fun isPermissionGranted(context: Context, isBusiness: Boolean): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            // Android 10 and below - check if legacy folder exists and can be read
            val legacyPath = File(Environment.getExternalStorageDirectory(), getRelativeStatusPath(isBusiness))
            if (legacyPath.exists() && legacyPath.canRead()) {
                return true
            }
        }

        // Check SAF Persisted URI permissions
        val savedUriStr = getSavedUriString(context, isBusiness) ?: return false
        val savedUri = Uri.parse(savedUriStr)

        val persisted = context.contentResolver.persistedUriPermissions
        val hasPermission = persisted.any { it.uri == savedUri && it.isReadPermission }
        if (!hasPermission) return false

        return try {
            val docFile = DocumentFile.fromTreeUri(context, savedUri)
            docFile != null && docFile.exists() && docFile.canRead()
        } catch (e: Exception) {
            Log.e(TAG, "Error checking permission for $savedUri", e)
            false
        }
    }

    fun getSavedUriString(context: Context, isBusiness: Boolean): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val key = if (isBusiness) KEY_WA_BUSINESS_URI else KEY_WHATSAPP_URI
        return prefs.getString(key, null)
    }

    fun savePersistedUri(context: Context, isBusiness: Boolean, uri: Uri) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val key = if (isBusiness) KEY_WA_BUSINESS_URI else KEY_WHATSAPP_URI
        prefs.edit().putString(key, uri.toString()).apply()
    }

    fun getOpenDocumentTreeIntent(context: Context, isBusiness: Boolean): Intent {
        val intent: Intent
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val sm = context.getSystemService(Context.STORAGE_SERVICE) as? StorageManager
            val primaryVolume = sm?.primaryStorageVolume
            intent = primaryVolume?.createOpenDocumentTreeIntent() ?: Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
        } else {
            intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val relPath = getRelativeStatusPath(isBusiness)
            // Encode the path to point directly to the exact folder
            val encodedPath = Uri.encode("primary:$relPath")
            val initialUri = Uri.parse("content://com.android.externalstorage.documents/document/$encodedPath")
            intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, initialUri)
        }

        intent.addFlags(
            Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or
                    Intent.FLAG_GRANT_PREFIX_URI_PERMISSION
        )
        return intent
    }

    fun takePersistablePermission(context: Context, uri: Uri, intentData: Intent?, isBusiness: Boolean) {
        val flags = (intentData?.flags ?: 0) and
                (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        val takeFlags = if (flags != 0) flags else Intent.FLAG_GRANT_READ_URI_PERMISSION

        try {
            context.contentResolver.takePersistableUriPermission(uri, takeFlags)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to take persistable URI permission", e)
        }
        savePersistedUri(context, isBusiness, uri)
    }

    suspend fun loadStatuses(
        context: Context,
        isBusiness: Boolean,
        filterVideosOnly: Boolean? = null
    ): List<StatusMedia> = withContext(Dispatchers.IO) {
        val resultList = mutableListOf<StatusMedia>()

        // 1. Try DocumentFile via SAF
        val savedUriStr = getSavedUriString(context, isBusiness)
        if (savedUriStr != null) {
            try {
                val treeUri = Uri.parse(savedUriStr)
                val documentFile = DocumentFile.fromTreeUri(context, treeUri)
                if (documentFile != null && documentFile.exists()) {
                    val files = documentFile.listFiles()
                    for (file in files) {
                        val name = file.name ?: continue
                        if (name.equals(".nomedia", ignoreCase = true)) continue

                        val isVideo = name.endsWith(".mp4", ignoreCase = true)
                        val isImage = name.endsWith(".jpg", ignoreCase = true) ||
                                name.endsWith(".jpeg", ignoreCase = true) ||
                                name.endsWith(".png", ignoreCase = true)

                        if (!isVideo && !isImage) continue

                        if (filterVideosOnly != null) {
                            if (filterVideosOnly && !isVideo) continue
                            if (!filterVideosOnly && isVideo) continue
                        }

                        val media = StatusMedia(
                            uri = file.uri,
                            file = null,
                            name = name,
                            path = file.uri.toString(),
                            size = file.length(),
                            dateModified = file.lastModified(),
                            isVideo = isVideo,
                            isSaved = isStatusSaved(context, name)
                        )
                        resultList.add(media)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error reading SAF DocumentFile", e)
            }
        }

        // 2. Fallback to direct legacy File access if list is empty or Android <= 10
        if (resultList.isEmpty()) {
            try {
                val relPath = getRelativeStatusPath(isBusiness)
                val legacyDir = File(Environment.getExternalStorageDirectory(), relPath)
                if (legacyDir.exists() && legacyDir.isDirectory) {
                    val files = legacyDir.listFiles()
                    files?.forEach { file ->
                        val name = file.name
                        if (!name.equals(".nomedia", ignoreCase = true)) {
                            val isVideo = name.endsWith(".mp4", ignoreCase = true)
                            val isImage = name.endsWith(".jpg", ignoreCase = true) ||
                                    name.endsWith(".jpeg", ignoreCase = true) ||
                                    name.endsWith(".png", ignoreCase = true)

                            if (isVideo || isImage) {
                                if (filterVideosOnly == null || (filterVideosOnly && isVideo) || (!filterVideosOnly && !isVideo)) {
                                    val uri = Uri.fromFile(file)
                                    val media = StatusMedia(
                                        uri = uri,
                                        file = file,
                                        name = name,
                                        path = file.absolutePath,
                                        size = file.length(),
                                        dateModified = file.lastModified(),
                                        isVideo = isVideo,
                                        isSaved = isStatusSaved(context, name)
                                    )
                                    resultList.add(media)
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error reading legacy status files", e)
            }
        }

        // Sort descending by date modified (newest statuses first)
        resultList.sortedByDescending { it.dateModified }
    }

    suspend fun loadSavedStatuses(
        context: Context,
        filterVideosOnly: Boolean? = null
    ): List<StatusMedia> = withContext(Dispatchers.IO) {
        val resultList = mutableListOf<StatusMedia>()

        // 1. Query MediaStore for Pictures/StatusSaver and Movies/StatusSaver
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Images
            if (filterVideosOnly == null || !filterVideosOnly) {
                val projection = arrayOf(
                    MediaStore.Images.Media._ID,
                    MediaStore.Images.Media.DISPLAY_NAME,
                    MediaStore.Images.Media.SIZE,
                    MediaStore.Images.Media.DATE_MODIFIED
                )
                val selection = "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ?"
                val selectionArgs = arrayOf("%Pictures/StatusSaver%")

                try {
                    context.contentResolver.query(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        projection,
                        selection,
                        selectionArgs,
                        "${MediaStore.Images.Media.DATE_MODIFIED} DESC"
                    )?.use { cursor ->
                        val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                        val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                        val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
                        val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_MODIFIED)

                        while (cursor.moveToNext()) {
                            val id = cursor.getLong(idColumn)
                            val name = cursor.getString(nameColumn) ?: "saved_image.jpg"
                            val size = cursor.getLong(sizeColumn)
                            val date = cursor.getLong(dateColumn) * 1000L
                            val uri = Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id.toString())

                            resultList.add(
                                StatusMedia(
                                    uri = uri,
                                    file = null,
                                    name = name,
                                    path = uri.toString(),
                                    size = size,
                                    dateModified = date,
                                    isVideo = false,
                                    isSaved = true
                                )
                            )
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error querying MediaStore images", e)
                }
            }

            // Videos
            if (filterVideosOnly == null || filterVideosOnly) {
                val projection = arrayOf(
                    MediaStore.Video.Media._ID,
                    MediaStore.Video.Media.DISPLAY_NAME,
                    MediaStore.Video.Media.SIZE,
                    MediaStore.Video.Media.DATE_MODIFIED
                )
                val selection = "${MediaStore.Video.Media.RELATIVE_PATH} LIKE ?"
                val selectionArgs = arrayOf("%Movies/StatusSaver%")

                try {
                    context.contentResolver.query(
                        MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                        projection,
                        selection,
                        selectionArgs,
                        "${MediaStore.Video.Media.DATE_MODIFIED} DESC"
                    )?.use { cursor ->
                        val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                        val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                        val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
                        val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_MODIFIED)

                        while (cursor.moveToNext()) {
                            val id = cursor.getLong(idColumn)
                            val name = cursor.getString(nameColumn) ?: "saved_video.mp4"
                            val size = cursor.getLong(sizeColumn)
                            val date = cursor.getLong(dateColumn) * 1000L
                            val uri = Uri.withAppendedPath(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id.toString())

                            resultList.add(
                                StatusMedia(
                                    uri = uri,
                                    file = null,
                                    name = name,
                                    path = uri.toString(),
                                    size = size,
                                    dateModified = date,
                                    isVideo = true,
                                    isSaved = true
                                )
                            )
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error querying MediaStore videos", e)
                }
            }
        }

        // 2. Also check physical folders Pictures/StatusSaver and Movies/StatusSaver
        val picturesDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "StatusSaver")
        val moviesDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "StatusSaver")

        val scannedDirs = listOf(picturesDir, moviesDir)
        for (dir in scannedDirs) {
            if (dir.exists() && dir.isDirectory) {
                dir.listFiles()?.forEach { file ->
                    val name = file.name
                    val isVideo = name.endsWith(".mp4", ignoreCase = true)
                    val isImage = name.endsWith(".jpg", ignoreCase = true) ||
                            name.endsWith(".jpeg", ignoreCase = true) ||
                            name.endsWith(".png", ignoreCase = true)

                    if (isVideo || isImage) {
                        if (filterVideosOnly == null || (filterVideosOnly && isVideo) || (!filterVideosOnly && !isVideo)) {
                            // Check if already in list by name
                            if (resultList.none { it.name == name }) {
                                val uri = Uri.fromFile(file)
                                resultList.add(
                                    StatusMedia(
                                        uri = uri,
                                        file = file,
                                        name = name,
                                        path = file.absolutePath,
                                        size = file.length(),
                                        dateModified = file.lastModified(),
                                        isVideo = isVideo,
                                        isSaved = true
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }

        resultList.sortedByDescending { it.dateModified }
    }

    fun isStatusSaved(context: Context, fileName: String): Boolean {
        // Quick check in standard folders
        val picFile = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
            "StatusSaver/$fileName"
        )
        if (picFile.exists()) return true

        val movieFile = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
            "StatusSaver/$fileName"
        )
        if (movieFile.exists()) return true

        return false
    }

    suspend fun saveStatusToGallery(context: Context, media: StatusMedia): Boolean = withContext(Dispatchers.IO) {
        try {
            val fileName = if (media.name.startsWith("status_")) {
                media.name
            } else {
                "status_${System.currentTimeMillis()}_${media.name}"
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val collectionUri: Uri
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    if (media.isVideo) {
                        put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
                        put(MediaStore.MediaColumns.RELATIVE_PATH, "Movies/StatusSaver")
                        collectionUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                    } else {
                        val mime = if (fileName.endsWith(".png", true)) "image/png" else "image/jpeg"
                        put(MediaStore.MediaColumns.MIME_TYPE, mime)
                        put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/StatusSaver")
                        collectionUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                    }
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }

                val itemUri = context.contentResolver.insert(collectionUri, contentValues)
                    ?: return@withContext false

                context.contentResolver.openOutputStream(itemUri)?.use { outStream ->
                    openInputStream(context, media)?.use { inStream ->
                        inStream.copyTo(outStream)
                    }
                }

                contentValues.clear()
                contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                context.contentResolver.update(itemUri, contentValues, null, null)

                media.isSaved = true
                return@withContext true
            } else {
                // Legacy Android 9 and below
                val targetDir = if (media.isVideo) {
                    File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "StatusSaver")
                } else {
                    File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "StatusSaver")
                }
                if (!targetDir.exists()) {
                    targetDir.mkdirs()
                }

                val targetFile = File(targetDir, fileName)
                FileOutputStream(targetFile).use { outStream ->
                    openInputStream(context, media)?.use { inStream ->
                        inStream.copyTo(outStream)
                    }
                }

                // Trigger media scan so it shows immediately in gallery
                MediaScannerConnection.scanFile(
                    context,
                    arrayOf(targetFile.absolutePath),
                    arrayOf(if (media.isVideo) "video/mp4" else "image/jpeg"),
                    null
                )

                media.isSaved = true
                return@withContext true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save status ${media.name}", e)
            false
        }
    }

    suspend fun deleteSavedStatus(context: Context, media: StatusMedia): Boolean = withContext(Dispatchers.IO) {
        try {
            // If it's a MediaStore content URI
            if (media.uri.scheme == "content") {
                val rows = context.contentResolver.delete(media.uri, null, null)
                if (rows > 0) return@withContext true
            }

            // If it has a file or file uri
            if (media.file != null && media.file.exists()) {
                val deleted = media.file.delete()
                if (deleted) {
                    MediaScannerConnection.scanFile(context, arrayOf(media.file.absolutePath), null, null)
                    return@withContext true
                }
            }

            val picFile = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                "StatusSaver/${media.name}"
            )
            if (picFile.exists()) {
                picFile.delete()
                MediaScannerConnection.scanFile(context, arrayOf(picFile.absolutePath), null, null)
                return@withContext true
            }

            val movieFile = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
                "StatusSaver/${media.name}"
            )
            if (movieFile.exists()) {
                movieFile.delete()
                MediaScannerConnection.scanFile(context, arrayOf(movieFile.absolutePath), null, null)
                return@withContext true
            }

            false
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete saved status", e)
            false
        }
    }

    private fun openInputStream(context: Context, media: StatusMedia): InputStream? {
        return if (media.file != null && media.file.exists()) {
            FileInputStream(media.file)
        } else {
            context.contentResolver.openInputStream(media.uri)
        }
    }

    fun isAppInstalled(context: Context, packageName: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    fun openWhatsApp(context: Context, isBusiness: Boolean): Boolean {
        val packageName = if (isBusiness) PACKAGE_WA_BUSINESS else PACKAGE_WHATSAPP
        val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
        return if (launchIntent != null) {
            context.startActivity(launchIntent)
            true
        } else {
            Toast.makeText(context, "WhatsApp is not installed", Toast.LENGTH_SHORT).show()
            false
        }
    }

    fun shareStatus(context: Context, media: StatusMedia) {
        try {
            val shareUri = getShareableUri(context, media)
            val mimeType = if (media.isVideo) "video/mp4" else "image/*"

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, shareUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Share Status via"))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to share status", e)
            Toast.makeText(context, "Could not share status", Toast.LENGTH_SHORT).show()
        }
    }

    fun repostStatus(context: Context, media: StatusMedia, isBusiness: Boolean) {
        try {
            val shareUri = getShareableUri(context, media)
            val mimeType = if (media.isVideo) "video/mp4" else "image/*"
            val targetPackage = if (isBusiness) PACKAGE_WA_BUSINESS else PACKAGE_WHATSAPP

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, shareUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                if (isAppInstalled(context, targetPackage)) {
                    setPackage(targetPackage)
                }
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to repost status", e)
            // Fallback to chooser
            shareStatus(context, media)
        }
    }

    fun shareMultipleStatuses(context: Context, mediaList: List<StatusMedia>) {
        if (mediaList.isEmpty()) return
        try {
            val uriList = ArrayList<Uri>()
            var hasVideo = false
            for (media in mediaList) {
                if (media.isVideo) hasVideo = true
                uriList.add(getShareableUri(context, media))
            }

            val mimeType = if (hasVideo) "video/*" else "image/*"
            val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = mimeType
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, uriList)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Share Selected Statuses"))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to share multiple statuses", e)
            Toast.makeText(context, "Could not share statuses", Toast.LENGTH_SHORT).show()
        }
    }

    private fun getShareableUri(context: Context, media: StatusMedia): Uri {
        // If file exists, create FileProvider URI
        if (media.file != null && media.file.exists()) {
            return FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                media.file
            )
        }

        // If it's already a content URI from MediaStore or SAF, cache it into a temporary shareable file
        // to guarantee third party apps like WhatsApp can open it without permission issues
        val cacheFile = File(context.cacheDir, "shared_${System.currentTimeMillis()}_${media.name}")
        FileOutputStream(cacheFile).use { out ->
            openInputStream(context, media)?.use { it.copyTo(out) }
        }
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            cacheFile
        )
    }
}
