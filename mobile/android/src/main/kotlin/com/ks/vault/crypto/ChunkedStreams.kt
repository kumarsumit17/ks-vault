package com.ks.vault.crypto

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer

/**
 * A large stream encrypted with AES-256-GCM in 64 KiB chunks, so a backup of hundreds of megabytes
 * never has to fit in memory. Each chunk is `flag (1 byte) || length (4 bytes) || sealed chunk`; its
 * associated data is `header || chunk number || flag`, and the flag marks the last chunk. So chunks
 * cannot be reordered, swapped between files, or cut off without the reader noticing (the STREAM
 * construction).
 */
private const val CHUNK = 64 * 1024

private fun aad(header: ByteArray, index: Long, last: Boolean): ByteArray =
    ByteBuffer.allocate(header.size + 9).put(header).putLong(index).put(if (last) 1 else 0).array()

class SealingOutputStream(out: OutputStream, private val key: ByteArray, private val header: ByteArray) : OutputStream() {
    private val out = DataOutputStream(out)
    private val buf = ByteArray(CHUNK)
    private var n = 0
    private var index = 0L
    private var closed = false

    override fun write(b: Int) {
        if (n == CHUNK) flushChunk(last = false)
        buf[n++] = b.toByte()
    }

    override fun write(b: ByteArray, off: Int, len: Int) {
        var o = off
        var left = len
        while (left > 0) {
            if (n == CHUNK) flushChunk(last = false)
            val take = minOf(left, CHUNK - n)
            System.arraycopy(b, o, buf, n, take)
            n += take
            o += take
            left -= take
        }
    }

    private fun flushChunk(last: Boolean) {
        val sealed = Aead.seal(key, buf.copyOf(n), aad(header, index, last))
        out.writeByte(if (last) 1 else 0)
        out.writeInt(sealed.size)
        out.write(sealed)
        buf.fill(0, 0, n)
        n = 0
        index++
    }

    override fun close() {
        if (closed) return
        closed = true
        flushChunk(last = true)
        out.close()
    }
}

/** The file was cut off, changed, or is not this kind of file. */
class DamagedStreamException(message: String) : IOException(message)

class OpeningInputStream(input: InputStream, private val key: ByteArray, private val header: ByteArray) : InputStream() {
    private val input = DataInputStream(input)
    private var chunk = ByteArray(0)
    private var pos = 0
    private var index = 0L
    private var sawLast = false

    /** Reads the next chunk; false at the proper end. The first chunk failing means a wrong password. */
    private fun next(): Boolean {
        if (sawLast) {
            if (input.read() != -1) throw DamagedStreamException("Data after the end")
            return false
        }
        val flag = try {
            input.readByte().toInt()
        } catch (_: EOFException) {
            throw DamagedStreamException("The file is cut off")
        }
        if (flag != 0 && flag != 1) throw DamagedStreamException("Not a chunk")
        val len = input.readInt()
        if (len < 28 || len > CHUNK + 28) throw DamagedStreamException("Bad chunk length")
        val sealed = ByteArray(len).also(input::readFully)
        chunk.fill(0)
        chunk = Aead.open(key, sealed, aad(header, index, flag == 1)) // AEADBadTagException when wrong
        pos = 0
        index++
        sawLast = flag == 1
        return true
    }

    override fun read(): Int {
        while (pos >= chunk.size) if (!next()) return -1
        return chunk[pos++].toInt() and 0xff
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (len == 0) return 0
        while (pos >= chunk.size) if (!next()) return -1
        val take = minOf(len, chunk.size - pos)
        System.arraycopy(chunk, pos, b, off, take)
        pos += take
        return take
    }

    override fun close() {
        chunk.fill(0)
        input.close()
    }
}
