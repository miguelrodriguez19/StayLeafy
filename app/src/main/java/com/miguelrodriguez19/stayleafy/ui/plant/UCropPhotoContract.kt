package com.miguelrodriguez19.stayleafy.ui.plant

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.activity.result.contract.ActivityResultContract
import com.miguelrodriguez19.stayleafy.data.photo.PendingPhotoCrop
import com.miguelrodriguez19.stayleafy.data.photo.PhotoCropResult
import com.yalantis.ucrop.UCrop

/** Launches the non-exported, local-only uCrop activity for an app-owned source and destination. */
class UCropPhotoContract : ActivityResultContract<PendingPhotoCrop, PhotoCropResult>() {
    override fun createIntent(context: Context, input: PendingPhotoCrop): Intent {
        val options = UCrop.Options().apply {
            setCompressionFormat(Bitmap.CompressFormat.JPEG)
            setCompressionQuality(input.outputQuality)
            setFreeStyleCropEnabled(input.freeStyleCropEnabled)
            setHideBottomControls(true)
        }
        return UCrop.of(input.sourceUri, input.destinationUri)
            .withAspectRatio(input.aspectRatioX, input.aspectRatioY)
            .withMaxResultSize(input.maxWidth, input.maxHeight)
            .withOptions(options)
            .getIntent(context)
    }

    override fun parseResult(resultCode: Int, intent: Intent?): PhotoCropResult = when {
        resultCode == Activity.RESULT_OK && intent?.let(UCrop::getOutput) != null -> {
            PhotoCropResult.Success
        }
        resultCode == UCrop.RESULT_ERROR -> PhotoCropResult.Failed
        else -> PhotoCropResult.Cancelled
    }
}
