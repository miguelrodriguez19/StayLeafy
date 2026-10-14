package com.miguelrodriguez19.stayleafy.ui.plant

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.miguelrodriguez19.stayleafy.R
import com.miguelrodriguez19.stayleafy.data.photo.PLANT_PHOTO_DIRECTORY
import com.miguelrodriguez19.stayleafy.ui.theme.leafyLayoutTokens
import java.io.File

@Composable
internal fun PlantPhotoBox(
    relativePath: String?,
    stagedFile: File? = null,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
) {
    val context = LocalContext.current
    val isPreview = LocalInspectionMode.current
    val file = stagedFile?.takeIf(File::isFile)
        ?: relativePath?.let { resolvePlantPhotoFile(context.filesDir, it) }
    var decodeFailed by remember(file) { mutableStateOf(false) }
    val clippedModifier = modifier.clip(RoundedCornerShape(leafyLayoutTokens.radiusMd))
    when {
        isPreview && relativePath != null -> PlantPhotoPlaceholder(
            modifier = clippedModifier,
            hasPhoto = true,
        )
        file == null || decodeFailed -> PlantPhotoPlaceholder(
            modifier = clippedModifier,
            hasPhoto = false,
        )
        else -> Box(
            modifier = clippedModifier.background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            AsyncImage(
                model = file,
                contentDescription = stringResource(R.string.plant_photo),
                modifier = Modifier.matchParentSize(),
                contentScale = contentScale,
                onError = { decodeFailed = true },
            )
        }
    }
}

@Composable
private fun PlantPhotoPlaceholder(
    modifier: Modifier,
    hasPhoto: Boolean,
) {
    val background = if (hasPhoto) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    Box(
        modifier = modifier.background(background),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_leaf),
            contentDescription = stringResource(
                if (hasPhoto) R.string.plant_photo else R.string.no_plant_photo,
            ),
            tint = if (hasPhoto) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

private fun resolvePlantPhotoFile(filesDirectory: File, relativePath: String): File? = runCatching {
    require(relativePath.startsWith("$PLANT_PHOTO_DIRECTORY/"))
    val name = relativePath.removePrefix("$PLANT_PHOTO_DIRECTORY/")
    require(SAFE_PHOTO_FILE.matches(name))
    val managedDirectory = File(filesDirectory, PLANT_PHOTO_DIRECTORY).canonicalFile
    val file = File(managedDirectory, name).canonicalFile
    require(file.parentFile == managedDirectory)
    file.takeIf(File::isFile)
}.getOrNull()

private val SAFE_PHOTO_FILE = Regex("[A-Za-z0-9-]{1,128}\\.[a-z0-9]{1,16}")
