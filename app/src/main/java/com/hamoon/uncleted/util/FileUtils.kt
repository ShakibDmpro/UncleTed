package com.hamoon.uncleted.util

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import java.io.File

object FileUtils {

    private const val TAG = "FileUtils"

    /**
     * Saves a given image file to the public "Pictures" directory using MediaStore.
     * This is the modern, correct way to save media files for public access.
     *
     * @param context The application context.
     * @param sourceFile The private file (e.g., from CameraHandler) to be copied.
     * @return The public URI of the saved image, or null if saving failed.
     */
    fun saveImageToPictures(context: Context, sourceFile: File): Uri? {
        val contentResolver = context.contentResolver
        val imageDetails = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, sourceFile.name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Specify the Pictures directory for organization.
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/UncleTed")
                put(MediaStore.Images.Media.IS_PENDING, 1) // Mark as pending until write is complete
            }
        }

        val imageUri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, imageDetails)

        if (imageUri == null) {
            Log.e(TAG, "Failed to create new MediaStore entry for the image.")
            return null
        }

        try {
            contentResolver.openOutputStream(imageUri).use { outputStream ->
                if (outputStream == null) {
                    throw Exception("Failed to get output stream.")
                }
                sourceFile.inputStream().use { inputStream ->
                    inputStream.copyTo(outputStream)
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                imageDetails.clear()
                imageDetails.put(MediaStore.Images.Media.IS_PENDING, 0)
                contentResolver.update(imageUri, imageDetails, null, null)
            }
            Log.i(TAG, "Image saved successfully to public gallery: $imageUri")
            return imageUri

        } catch (e: Exception) {
            Log.e(TAG, "Failed to save image to Pictures directory", e)
            // If something went wrong, clean up the pending entry
            contentResolver.delete(imageUri, null, null)
            return null
        }
    }
}