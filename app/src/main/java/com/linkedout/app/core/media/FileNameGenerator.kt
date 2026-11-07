package com.linkedout.app.core.media

import java.security.NoSuchAlgorithmException
import java.text.Normalizer

/**
 * Generates valid filenames for a given string.
 *
 * Ported from MTGA, which took it from AntennaPod (GPL-3.0, credited in the README):
 * https://github.com/213YaZ786/MTGA/blob/main/app/src/main/java/com/mtga/app/core/media/FileNameGenerator.kt#FileNameGenerator
 * https://github.com/AntennaPod/AntennaPod/blob/develop/net/download/service-interface/src/main/java/de/danoeh/antennapod/net/download/serviceinterface/FileNameGenerator.java
 */
object FileNameGenerator {
    const val MAX_FILENAME_LENGTH = 242 // limited by CircleCI
    private const val MD5_HEX_LENGTH = 32

    private val validChars: CharArray =
        ("abcdefghijklmnopqrstuvwxyz" +
            "ABCDEFGHIJKLMNOPQRSTUVWXYZ" +
            "0123456789" +
            " _-").toCharArray()

    /**
     * This method will return a new string that doesn't contain any illegal
     * characters of the given string.
     *
     * commons-lang3 is not in this build: StringUtils.stripAccents is the
     * Normalizer's NFD form with the combining marks removed.
     * https://github.com/AntennaPod/AntennaPod/blob/develop/net/download/service-interface/src/main/java/de/danoeh/antennapod/net/download/serviceinterface/FileNameGenerator.java#generateFileName
     */
    fun generateFileName(string: String): String {
        val stripped = Normalizer.normalize(string, Normalizer.Form.NFD)
            .replace(COMBINING_MARKS, "")
        val buf = StringBuilder()
        for (i in stripped.indices) {
            val c = stripped[i]
            if (Character.isSpaceChar(c) &&
                (buf.isEmpty() || Character.isSpaceChar(buf[buf.length - 1]))
            ) {
                continue
            }
            if (validChars.contains(c)) {
                buf.append(c)
            }
        }
        val filename = buf.toString().trim()
        return if (filename.isEmpty()) {
            randomString(8)
        } else if (filename.length >= MAX_FILENAME_LENGTH) {
            filename.substring(0, MAX_FILENAME_LENGTH - MD5_HEX_LENGTH - 1) + "_" + md5(filename)
        } else {
            filename
        }
    }

    private val COMBINING_MARKS = Regex("\\p{InCombiningDiacriticalMarks}+")

    private fun randomString(length: Int): String {
        val sb = StringBuilder(length)
        for (i in 0 until length) {
            sb.append(validChars[(Math.random() * validChars.size).toInt()])
        }
        return sb.toString()
    }

    private fun md5(md5: String): String? {
        return try {
            val md = java.security.MessageDigest.getInstance("MD5")
            val array = md.digest(md5.toByteArray(Charsets.UTF_8))
            val sb = StringBuilder()
            for (b in array) {
                sb.append(Integer.toHexString((b.toInt() and 0xFF) or 0x100).substring(1, 3))
            }
            sb.toString()
        } catch (e: NoSuchAlgorithmException) {
            null
        }
    }
}
