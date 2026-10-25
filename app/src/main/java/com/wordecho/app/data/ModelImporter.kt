package com.wordecho.app.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

/**
 * 离线模型导入器。
 *
 * 手机上最烦的一步就是「把 42MB 的模型目录塞进 assets 再重新编译」，
 * 所以这里支持直接选一个 zip 导入到 App 私有目录 —— 电脑下载完传手机，一次搞定。
 *
 * - Vosk 语音模型：zip 里有 `am/` 或 `conf/` 即认，会自动剥掉外面那层文件夹
 */
class ModelImporter(private val context: Context) {

    data class Result(val ok: Boolean, val message: String)

    private fun voskDir(lang: String) = File(context.filesDir, "vosk-$lang")

    fun voskReady(lang: String): Boolean {
        val d = voskDir(lang)
        return File(d, "am").exists() || File(d, "conf").exists()
    }

    // ---------------- Vosk ----------------

    suspend fun importVoskModel(uri: Uri, lang: String): Result = withContext(Dispatchers.IO) {
        val tmp = File(context.cacheDir, "vosk_import_${System.currentTimeMillis()}")
        try {
            val unzipped = unzip(uri, tmp)
            if (!unzipped) return@withContext Result(false, "不是有效的 zip 压缩包")

            // 剥掉外层文件夹：如果临时目录里只有一个子目录且没有散文件，就用它当根
            val children = tmp.listFiles().orEmpty()
            val root = if (children.size == 1 && children[0].isDirectory) children[0] else tmp

            if (!File(root, "am").exists() && !File(root, "conf").exists()) {
                return@withContext Result(false, "这不是 Vosk 模型包：缺少 am/ 或 conf/ 目录")
            }

            val target = voskDir(lang)
            target.deleteRecursively()
            target.mkdirs()
            root.copyRecursively(target, overwrite = true)
            Result(true, "语音模型导入完成（${folderSizeMb(target)} MB）")
        } catch (e: Throwable) {
            Result(false, e.message ?: "导入失败")
        } finally {
            tmp.deleteRecursively()
        }
    }

    // ---------------- 工具 ----------------

    private fun unzip(uri: Uri, dest: File): Boolean {
        dest.mkdirs()
        var any = false
        context.contentResolver.openInputStream(uri)?.use { raw ->
            ZipInputStream(BufferedInputStream(raw)).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    val name = entry.name.replace('\\', '/')
                    if (name.contains("..")) {
                        zis.closeEntry()
                        entry = zis.nextEntry
                        continue
                    }
                    val out = File(dest, name)
                    if (entry.isDirectory) {
                        out.mkdirs()
                    } else {
                        out.parentFile?.mkdirs()
                        FileOutputStream(out).use { zis.copyTo(it, 64 * 1024) }
                        any = true
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
        }
        return any
    }

    private fun folderSizeMb(dir: File): Long =
        dir.walkTopDown().filter { it.isFile }.sumOf { it.length() } / 1024 / 1024
}
