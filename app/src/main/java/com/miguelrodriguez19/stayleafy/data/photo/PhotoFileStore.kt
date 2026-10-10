package com.miguelrodriguez19.stayleafy.data.photo

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import com.miguelrodriguez19.stayleafy.data.UuidProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

const val PLANT_PHOTO_DIRECTORY = "plant_photos"
const val PENDING_PLANT_PHOTO_DIRECTORY = "pending_plant_photos"

data class PendingPhotoCapture(
    val token: String,
    val pendingPath: String,
    val contentUri: Uri,
)

data class StoredPhoto(
    val relativePath: String,
    val file: File,
)

data class PhotoOrphanReconciliation(
    val deletedPaths: Set<String>,
    val failedPaths: Set<String>,
)

interface PhotoInputSource {
    val extensionHint: String?

    fun openStream(): InputStream
}

fun interface PhotoContentUriFactory {
    fun create(file: File): Uri
}

interface PhotoFileStore {
    fun prepareCameraCapture(): PendingPhotoCapture

    suspend fun completeCameraCapture(token: String): StoredPhoto

    suspend fun discardCameraCapture(token: String): Boolean

    suspend fun importImage(source: PhotoInputSource): StoredPhoto

    suspend fun remove(relativePath: String): Boolean

    fun resolve(relativePath: String): File?

    suspend fun reconcileOrphans(referencedPaths: Set<String>): PhotoOrphanReconciliation
}

open class PhotoFileStoreException(message: String, cause: Throwable? = null) : IOException(message, cause)

class PendingPhotoNotFoundException(token: String) : PhotoFileStoreException(
    "Pending photo capture does not exist: $token",
)

class PhotoSourceUnavailableException(cause: Throwable? = null) : PhotoFileStoreException(
    "Unable to read the selected photo",
    cause,
)

class FilePhotoFileStore(
    filesDirectory: File,
    cacheDirectory: File,
    private val uuidProvider: UuidProvider,
    private val contentUriFactory: PhotoContentUriFactory,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : PhotoFileStore {

    private val stableDirectory = File(filesDirectory, PLANT_PHOTO_DIRECTORY)
    private val pendingDirectory = File(cacheDirectory, PENDING_PLANT_PHOTO_DIRECTORY)

    override fun prepareCameraCapture(): PendingPhotoCapture {
        requireDirectory(stable = false)
        val token = safeStem(uuidProvider.newUuid())
        val pendingFile = safePendingFile(token)
        if (!pendingFile.createNewFile()) {
            throw PhotoFileStoreException("Unable to reserve pending photo capture")
        }

        return try {
            PendingPhotoCapture(
                token = token,
                pendingPath = "$PENDING_PLANT_PHOTO_DIRECTORY/$token.jpg",
                contentUri = contentUriFactory.create(pendingFile),
            )
        } catch (error: Throwable) {
            pendingFile.delete()
            throw error
        }
    }

    override suspend fun completeCameraCapture(token: String): StoredPhoto = withContext(ioDispatcher) {
        val pendingFile = safePendingFile(token)
        if (!pendingFile.isFile || pendingFile.length() == 0L) {
            pendingFile.delete()
            throw PendingPhotoNotFoundException(token)
        }

        try {
            val stored = copyIntoStable(
                extensionHint = "jpg",
                openSource = { FileInputStream(pendingFile) },
            )
            if (!pendingFile.delete()) {
                removeQuietly(stored.file)
                throw PhotoFileStoreException("Unable to discard completed pending capture: $token")
            }
            stored
        } catch (error: Throwable) {
            removeQuietly(pendingFile)
            throw error
        }
    }

    override suspend fun discardCameraCapture(token: String): Boolean = withContext(ioDispatcher) {
        val pendingFile = safePendingFile(token)
        if (!pendingFile.exists()) return@withContext false
        if (!pendingFile.delete()) {
            throw PhotoFileStoreException("Unable to discard pending photo capture: $token")
        }
        true
    }

    override suspend fun importImage(source: PhotoInputSource): StoredPhoto = withContext(ioDispatcher) {
        copyIntoStable(
            extensionHint = source.extensionHint,
            openSource = {
                try {
                    source.openStream()
                } catch (error: Throwable) {
                    if (error is CancellationException) throw error
                    throw PhotoSourceUnavailableException(error)
                }
            },
        )
    }

    override suspend fun remove(relativePath: String): Boolean = withContext(ioDispatcher) {
        val file = resolveExisting(relativePath) ?: return@withContext false
        if (!file.delete()) {
            throw PhotoFileStoreException("Unable to remove photo: $relativePath")
        }
        true
    }

    override fun resolve(relativePath: String): File? =
        resolveExisting(relativePath)

    override suspend fun reconcileOrphans(
        referencedPaths: Set<String>,
    ): PhotoOrphanReconciliation = withContext(ioDispatcher) {
        if (!stableDirectory.exists()) {
            return@withContext PhotoOrphanReconciliation(emptySet(), emptySet())
        }

        val referencedFiles = referencedPaths.mapNotNull { path ->
            runCatching { resolveCanonical(path) }.getOrNull()
        }.toSet()
        val deleted = mutableSetOf<String>()
        val failed = mutableSetOf<String>()
        stableDirectory.listFiles().orEmpty()
            .filter { it.isFile }
            .forEach { file ->
                val canonical = runCatching { file.canonicalFile }.getOrNull() ?: return@forEach
                if (canonical in referencedFiles) return@forEach

                val relativePath = "$PLANT_PHOTO_DIRECTORY/${file.name}"
                if (file.delete()) deleted += relativePath else failed += relativePath
            }
        PhotoOrphanReconciliation(deletedPaths = deleted, failedPaths = failed)
    }

    private fun copyIntoStable(
        extensionHint: String?,
        openSource: () -> InputStream,
    ): StoredPhoto {
        requireDirectory(stable = true)
        val extension = safeExtension(extensionHint)
        val stem = safeStem(uuidProvider.newUuid())
        val target = safeStableFile("$stem.$extension")
        val temporary = canonicalDirectChild(stableDirectory, ".$stem.$extension.part")

        if (!temporary.createNewFile()) {
            throw PhotoFileStoreException("Unable to reserve stable photo file")
        }

        return try {
            openSource().use { input ->
                FileOutputStream(temporary).use { output ->
                    input.copyTo(output)
                    output.fd.sync()
                }
            }
            if (!temporary.renameTo(target)) {
                throw PhotoFileStoreException("Unable to finalize stable photo file")
            }
            StoredPhoto(
                relativePath = "$PLANT_PHOTO_DIRECTORY/${target.name}",
                file = target,
            )
        } catch (error: Throwable) {
            removeQuietly(temporary)
            removeQuietly(target)
            if (error is CancellationException) throw error
            if (error is PhotoFileStoreException) throw error
            throw PhotoFileStoreException("Unable to store photo", error)
        }
    }

    private fun requireDirectory(stable: Boolean) {
        val directory = if (stable) stableDirectory else pendingDirectory
        if (!directory.exists() && !directory.mkdirs()) {
            throw PhotoFileStoreException("Unable to create photo directory: ${directory.path}")
        }
        if (!directory.isDirectory) {
            throw PhotoFileStoreException("Photo path is not a directory: ${directory.path}")
        }
    }

    private fun safePendingFile(token: String): File {
        val normalizedToken = safeStem(token)
        require(normalizedToken == token) { "Invalid pending photo token" }
        requireDirectory(stable = false)
        return canonicalDirectChild(pendingDirectory, "$normalizedToken.jpg")
    }

    private fun safeStableFile(name: String): File {
        require(SAFE_STABLE_FILE.matches(name)) { "Invalid stable photo name" }
        requireDirectory(stable = true)
        return canonicalDirectChild(stableDirectory, name)
    }

    private fun resolveExisting(relativePath: String): File? {
        val file = resolveCanonical(relativePath)
        return file.takeIf { it.isFile }
    }

    private fun resolveCanonical(relativePath: String): File {
        require(relativePath.startsWith("$PLANT_PHOTO_DIRECTORY/")) {
            "Photo path must be relative to $PLANT_PHOTO_DIRECTORY"
        }
        val name = relativePath.removePrefix("$PLANT_PHOTO_DIRECTORY/")
        require(SAFE_STABLE_FILE.matches(name)) { "Invalid stable photo path" }
        return safeStableFile(name)
    }

    private fun canonicalDirectChild(directory: File, name: String): File {
        val root = directory.canonicalFile
        val child = File(root, name).canonicalFile
        require(child.parentFile == root) { "Photo path escapes its managed directory" }
        return child
    }

    private fun safeStem(value: String): String {
        require(SAFE_STEM.matches(value)) { "Invalid photo identifier" }
        return value
    }

    private fun safeExtension(value: String?): String {
        val extension = value.orEmpty()
            .trim()
            .removePrefix(".")
            .lowercase(Locale.ROOT)
        return extension.takeIf(SAFE_EXTENSION::matches) ?: "bin"
    }

    private fun removeQuietly(file: File) {
        if (file.exists()) file.delete()
    }

    private companion object {
        val SAFE_STEM = Regex("[A-Za-z0-9-]{1,128}")
        val SAFE_EXTENSION = Regex("[a-z0-9]{1,16}")
        val SAFE_STABLE_FILE = Regex("[A-Za-z0-9-]{1,128}\\.[a-z0-9]{1,16}")
    }
}

@Singleton
class AndroidPhotoFileStore @Inject constructor(
    @ApplicationContext context: Context,
    uuidProvider: UuidProvider,
) : PhotoFileStore {
    private val delegate = FilePhotoFileStore(
        filesDirectory = context.filesDir,
        cacheDirectory = context.cacheDir,
        uuidProvider = uuidProvider,
        contentUriFactory = FileProviderPhotoContentUriFactory(context),
    )

    override fun prepareCameraCapture(): PendingPhotoCapture = delegate.prepareCameraCapture()

    override suspend fun completeCameraCapture(token: String): StoredPhoto =
        delegate.completeCameraCapture(token)

    override suspend fun discardCameraCapture(token: String): Boolean =
        delegate.discardCameraCapture(token)

    override suspend fun importImage(source: PhotoInputSource): StoredPhoto =
        delegate.importImage(source)

    override suspend fun remove(relativePath: String): Boolean = delegate.remove(relativePath)

    override fun resolve(relativePath: String): File? = delegate.resolve(relativePath)

    override suspend fun reconcileOrphans(
        referencedPaths: Set<String>,
    ): PhotoOrphanReconciliation = delegate.reconcileOrphans(referencedPaths)
}

class FileProviderPhotoContentUriFactory(
    private val context: Context,
) : PhotoContentUriFactory {
    override fun create(file: File): Uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file,
    )
}

fun interface PhotoInputResolver {
    fun resolve(uri: Uri): PhotoInputSource
}

@Singleton
class ContentResolverPhotoInputResolver @Inject constructor(
    @ApplicationContext context: Context,
) : PhotoInputResolver {
    private val contentResolver: ContentResolver = context.contentResolver

    override fun resolve(uri: Uri): PhotoInputSource {
        val extension = MimeTypeMap.getSingleton()
            .getExtensionFromMimeType(contentResolver.getType(uri))
            ?: MimeTypeMap.getFileExtensionFromUrl(uri.toString())

        return object : PhotoInputSource {
            override val extensionHint: String? = extension

            override fun openStream(): InputStream = contentResolver.openInputStream(uri)
                ?: throw PhotoSourceUnavailableException()
        }
    }
}
