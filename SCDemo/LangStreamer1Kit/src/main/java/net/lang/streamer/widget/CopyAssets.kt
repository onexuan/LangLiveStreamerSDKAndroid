package net.lang.streamer.widget

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream

class CopyAssets {
    private val TAG = "CopyAssets"
    fun saveArAssestsDataLang(context: Context, filePath: String, name: String): String? {
        val inputStream: InputStream
        try {
            inputStream = context.resources.assets.open("$filePath/$name")

            val file = File("/sdcard/tempdir")
            if (!file.exists()) {
                file.mkdirs()
            }

            val path = "$file/$name"
            val stickerFile = File(path)
            if (stickerFile.exists())
                return stickerFile.absolutePath

            val fileOutputStream = FileOutputStream(path)
            val buffer = ByteArray(512)
            var count = inputStream.read(buffer)
            while (count > 0) {
                fileOutputStream.write(buffer, 0, count)
                count = inputStream.read(buffer)
            }

            fileOutputStream.flush()
            fileOutputStream.close()
            inputStream.close()
            return path
        } catch (e: IOException) {
            e.printStackTrace()
        }

        return null
    }
}