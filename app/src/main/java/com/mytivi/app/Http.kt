package com.mytivi.app

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.BufferedInputStream
import java.io.File
import java.io.InputStream
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream

object Http {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private fun request(url: String): Response {
        val req = Request.Builder().url(url).header("User-Agent", "MyTivi/1.0").build()
        val resp = client.newCall(req).execute()
        if (!resp.isSuccessful) {
            val code = resp.code
            resp.close()
            throw IllegalStateException("HTTP $code")
        }
        return resp
    }

    /** Nếu dữ liệu là gzip (nhiều nguồn EPG nén .gz) thì tự giải nén. */
    private fun maybeGunzip(raw: InputStream): InputStream {
        val s = BufferedInputStream(raw)
        s.mark(2)
        val b1 = s.read()
        val b2 = s.read()
        s.reset()
        return if (b1 == 0x1f && b2 == 0x8b) GZIPInputStream(s) else s
    }

    fun open(url: String): InputStream = maybeGunzip(request(url).body!!.byteStream())

    fun getString(url: String): String = open(url).bufferedReader().use { it.readText() }

    /** Tải nguyên bản (giữ nguyên .gz nếu có) về file để lưu cache. */
    fun download(url: String, dest: File) {
        request(url).use { resp ->
            resp.body!!.byteStream().use { input ->
                dest.outputStream().use { out -> input.copyTo(out) }
            }
        }
    }

    fun openFile(file: File): InputStream = maybeGunzip(file.inputStream())
}
