package com.astramesh.services.audio

import android.content.Context
import com.astramesh.common.AstraLog
import java.io.File
import java.io.FileOutputStream

/**
 * Utility to extract or locate ONNX models on local disk for direct mmap execution in ONNX Runtime.
 * Avoids loading multi-hundred-megabyte byte arrays into the Android ART Java Heap.
 */
object ModelAssetLoader {
    private const val TAG = "ModelAssetLoader"

    @Synchronized
    fun getOrExtractAssetFile(context: Context, assetPath: String): File {
        val targetFile = File(context.filesDir, assetPath)
        if (targetFile.exists() && targetFile.length() > 0) {
            return targetFile
        }

        targetFile.parentFile?.mkdirs()
        val tempFile = File(context.filesDir, "$assetPath.tmp")
        tempFile.parentFile?.mkdirs()

        AstraLog.i(TAG, "Extracting asset '$assetPath' to disk for native mmap loading...")
        context.assets.open(assetPath).use { input ->
            FileOutputStream(tempFile).use { output ->
                input.copyTo(output, bufferSize = 64 * 1024)
            }
        }

        if (!tempFile.renameTo(targetFile)) {
            tempFile.copyTo(targetFile, overwrite = true)
            tempFile.delete()
        }
        AstraLog.i(TAG, "Asset '$assetPath' ready at '${targetFile.absolutePath}' (${targetFile.length()} bytes)")
        return targetFile
    }

    fun assetExists(context: Context, assetPath: String): Boolean {
        return try {
            val targetFile = File(context.filesDir, assetPath)
            if (targetFile.exists() && targetFile.length() > 0) return true
            context.assets.open(assetPath).use { }
            true
        } catch (_: Exception) {
            false
        }
    }
}
