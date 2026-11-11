package com.example.data.backup

import android.content.Context
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Result data of safe ZIP archive extraction.
 */
data class ExtractionSummary(
  val extractedFiles: Set<String>,
  val totalUncompressedBytes: Long,
  val entryCount: Int
)

/**
 * Handles low-level archive I/O, streaming, staging sandbox lifecycle,
 * and security constraints against archive exploits (ZipSlip, ZipBomb, Duplicates).
 */
class BackupStorage(private val context: Context) {

  companion object {
    const val MAX_ENTRIES: Int = 5_000
    const val MAX_SINGLE_FILE_BYTES: Long = 50 * 1024 * 1024L // 50 MB
    const val MAX_TOTAL_UNCOMPRESSED_BYTES: Long = 250 * 1024 * 1024L // 250 MB
    const val BUFFER_SIZE: Int = 8192
  }

  /**
   * Calculates the SHA-256 hash of a byte array, returned as a lowercase hex string.
   */
  fun calculateSha256(bytes: ByteArray): String {
    val digest = MessageDigest.getInstance("SHA-256")
    val hash = digest.digest(bytes)
    return hash.joinToString("") { "%02x".format(it) }
  }

  /**
   * Calculates the SHA-256 hash of a file on disk, streaming through it in chunks.
   */
  fun calculateSha256(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    val buffer = ByteArray(BUFFER_SIZE)
    FileInputStream(file).use { input ->
      var bytesRead: Int
      while (input.read(buffer).also { bytesRead = it } != -1) {
        digest.update(buffer, 0, bytesRead)
      }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
  }

  /**
   * Creates an isolated staging directory inside the application's cache directory.
   */
  fun createStagingDirectory(prefix: String = "backup_staging"): File {
    val stagingDir = File(context.cacheDir, "${prefix}_${System.currentTimeMillis()}")
    if (stagingDir.exists()) {
      cleanDirectory(stagingDir)
    }
    stagingDir.mkdirs()
    return stagingDir
  }

  /**
   * Safely and recursively removes a directory and all of its contents.
   */
  fun cleanDirectory(dir: File?) {
    if (dir == null || !dir.exists()) return
    try {
      dir.deleteRecursively()
    } catch (_: Exception) {
      // Ignored during cleanup
    }
  }

  /**
   * Writes the backup archive to the provided OutputStream.
   *
   * @param outputStream Target stream supplied by caller (e.g. SAF Document URI stream).
   * @param byteEntries In-memory payload entries: relativePath -> fileBytes.
   * @param fileEntries On-disk payload entries: relativePath -> sourceFile.
   * @return Total uncompressed bytes written to archive.
   */
  fun writeZip(
    outputStream: OutputStream,
    byteEntries: Map<String, ByteArray>,
    fileEntries: List<Pair<String, File>>
  ): Long {
    var totalBytesWritten = 0L
    ZipOutputStream(BufferedOutputStream(outputStream)).use { zipOut ->
      // 1. Write in-memory byte entries (JSON files)
      for ((relativePath, bytes) in byteEntries) {
        val entry = ZipEntry(relativePath)
        zipOut.putNextEntry(entry)
        zipOut.write(bytes)
        zipOut.closeEntry()
        totalBytesWritten += bytes.size
      }

      // 2. Write file entries (Audio files)
      val buffer = ByteArray(BUFFER_SIZE)
      for ((relativePath, file) in fileEntries) {
        if (!file.exists() || !file.isFile || !file.canRead()) continue

        val entry = ZipEntry(relativePath)
        zipOut.putNextEntry(entry)
        FileInputStream(file).use { fileIn ->
          var count: Int
          while (fileIn.read(buffer).also { count = it } != -1) {
            zipOut.write(buffer, 0, count)
            totalBytesWritten += count
          }
        }
        zipOut.closeEntry()
      }
      zipOut.flush()
    }
    return totalBytesWritten
  }

  /**
   * Extracts an archive from an InputStream into an isolated staging directory,
   * enforcing ZipSlip, ZipBomb, entry count, and duplicate entry checks.
   */
  @Throws(Exception::class)
  fun extractZipToStaging(inputStream: InputStream, stagingDir: File): ExtractionSummary {
    val seenEntries = mutableSetOf<String>()
    var totalUncompressedBytes = 0L
    val canonicalStagingPath = stagingDir.canonicalPath

    ZipInputStream(BufferedInputStream(inputStream)).use { zipIn ->
      var entry: ZipEntry? = zipIn.nextEntry
      while (entry != null) {
        val entryName = entry.name

        // Check 1: Duplicate entry detection
        if (!seenEntries.add(entryName)) {
          throw SecurityException("Arsip mengandung entri duplikat: $entryName")
        }

        // Check 2: Entry count limit
        if (seenEntries.size > MAX_ENTRIES) {
          throw SecurityException("Arsip melebihi batas jumlah entri yang diizinkan ($MAX_ENTRIES entri).")
        }

        // Check 3: Path Traversal (ZipSlip) detection
        if (entryName.contains("..") || entryName.startsWith("/") || entryName.startsWith("\\") || entryName.contains(":")) {
          throw SecurityException("Potensi eksploitasi ZipSlip terdeteksi pada path entri: $entryName")
        }

        val targetFile = File(stagingDir, entryName)
        val targetCanonical = targetFile.canonicalPath
        if (!targetCanonical.startsWith(canonicalStagingPath + File.separator) && targetCanonical != canonicalStagingPath) {
          throw SecurityException("Path entri mengarah ke luar direktori staging: $entryName")
        }

        if (entry.isDirectory) {
          targetFile.mkdirs()
        } else {
          // Ensure parent directory exists
          targetFile.parentFile?.mkdirs()

          var entryBytesRead = 0L
          val buffer = ByteArray(BUFFER_SIZE)
          FileOutputStream(targetFile).use { fileOut ->
            var count: Int
            while (zipIn.read(buffer).also { count = it } != -1) {
              entryBytesRead += count
              totalUncompressedBytes += count

              // Check 4: Single file size limit
              if (entryBytesRead > MAX_SINGLE_FILE_BYTES) {
                throw SecurityException("Ukuran file tunggal melebihi batas 50 MB: $entryName")
              }

              // Check 5: Total uncompressed size limit (Zip Bomb prevention)
              if (totalUncompressedBytes > MAX_TOTAL_UNCOMPRESSED_BYTES) {
                throw SecurityException("Total ukuran ekstraksi melebihi batas aman 250 MB (potensi Zip Bomb).")
              }

              fileOut.write(buffer, 0, count)
            }
            fileOut.flush()
          }
        }

        zipIn.closeEntry()
        entry = zipIn.nextEntry
      }
    }

    if (seenEntries.isEmpty()) {
      throw IllegalArgumentException("Arsip ZIP kosong.")
    }

    return ExtractionSummary(
      extractedFiles = seenEntries,
      totalUncompressedBytes = totalUncompressedBytes,
      entryCount = seenEntries.size
    )
  }
}
