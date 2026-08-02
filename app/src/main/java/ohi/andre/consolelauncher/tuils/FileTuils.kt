package ohi.andre.consolelauncher.tuils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

object FileTuils {

    private const val TUI_FOLDER = "t-ui"
    private const val FILEUPDATE_DELAY = 100L
    private var folder: File? = null

    @JvmStatic
    @Throws(Exception::class)
    fun download(`in`: InputStream, file: File): Long {
        return `in`.use { input ->
            FileOutputStream(file, false).use { output ->
                input.copyTo(output)
            }
        }
    }

    @JvmStatic
    fun inputStreamToString(`is`: InputStream): String {
        val s = java.util.Scanner(`is`).useDelimiter("\\A")
        return if (s.hasNext()) s.next() else Tuils.EMPTYSTRING
    }

    @JvmStatic
    fun insertOld(c: Context, oldFile: File?): Boolean {
        if (oldFile == null || !oldFile.exists()) return false

        val oldPath = oldFile.absolutePath

        val oldFolder = File(getFolder(c), "old")
        if (!oldFolder.exists()) oldFolder.mkdir()

        val dest = File(oldFolder, oldFile.name)
        if (dest.exists()) dest.delete()

        return oldFile.renameTo(dest) && File(oldPath).delete()
    }

    @JvmStatic
    fun getOld(c: Context, name: String): File? {
        val old = File(getFolder(c), "old")
        val file = File(old, name)

        if (file.exists()) return file
        return null
    }

    @JvmStatic
    fun deleteContentOnly(dir: File) {
        val files = dir.listFiles() ?: return

        for (f in files) {
            if (f.isDirectory) delete(f)
            f.delete()
        }
    }

    @JvmStatic
    fun delete(dir: File) {
        val files = dir.listFiles() ?: return

        for (f in files) {
            if (f.isDirectory) delete(f)
            f.delete()
        }
        dir.delete()
    }

    @JvmStatic
    fun openFile(c: Context, f: File): Intent {
        val intent = Intent(Intent.ACTION_VIEW)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        val u = buildFile(c, f)
        val mimetype = MimeTypes.getMimeType(f.absolutePath, f.isDirectory)

        intent.setDataAndType(u, mimetype)

        val flags: Int = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_PREFIX_URI_PERMISSION
        } else {
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        }

        intent.addFlags(flags)

        return intent
    }

    @JvmStatic
    fun shareFile(c: Context, f: File): Intent {
        val intent = Intent(Intent.ACTION_SEND)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        val u = buildFile(c, f)

        val mimetype = MimeTypes.getMimeType(f.absolutePath, f.isDirectory)

        intent.setDataAndType(u, mimetype)

        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        intent.putExtra(Intent.EXTRA_STREAM, u)

        return intent
    }

    private fun buildFile(context: Context, file: File): Uri {
        return if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            Uri.fromFile(file)
        } else {
            FileProvider.getUriForFile(context, GenericFileProvider.PROVIDER_NAME, file)
        }
    }

    private fun getTuiFolder(c: Context?): File? {
        return c?.getExternalFilesDir(TUI_FOLDER)
    }

    @JvmStatic
    fun getFolder(c: Context?): File? {
        if (folder != null) return folder

        var elapsedTime = 0L
        while (elapsedTime < 1000) {
            val tuiFolder = getTuiFolder(c)
            if (tuiFolder != null && ((tuiFolder.exists() && tuiFolder.isDirectory) || tuiFolder.mkdir())) {
                folder = tuiFolder
                return folder
            }

            try {
                Thread.sleep(FILEUPDATE_DELAY)
            } catch (e: InterruptedException) {
            }

            elapsedTime += FILEUPDATE_DELAY
        }

        return null
    }

    @JvmStatic
    fun nOfBytes(file: File): Int {
        return file.length().toInt()
    }
}
