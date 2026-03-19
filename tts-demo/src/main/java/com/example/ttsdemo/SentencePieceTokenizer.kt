package com.example.ttsdemo

import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Minimal SentencePiece unigram tokenizer.
 * Reads a .model protobuf file and performs Viterbi segmentation.
 */
class SentencePieceTokenizer private constructor(
    private val pieces: List<SentencePiece>
) {
    private val pieceToId: Map<String, Int> = pieces.withIndex()
        .associate { (i, p) -> p.piece to i }

    // Build trie for efficient prefix matching
    private val trie = Trie().apply {
        for ((i, p) in pieces.withIndex()) {
            if (p.type == PieceType.NORMAL || p.type == PieceType.USER_DEFINED) {
                insert(p.piece, i)
            }
        }
    }

    data class SentencePiece(
        val piece: String,
        val score: Float,
        val type: PieceType
    )

    enum class PieceType(val value: Int) {
        NORMAL(1),
        UNKNOWN(2),
        CONTROL(3),
        USER_DEFINED(4),
        UNUSED(5),
        BYTE(6);

        companion object {
            fun fromValue(v: Int): PieceType = entries.firstOrNull { it.value == v } ?: NORMAL
        }
    }

    /**
     * Encode text to token IDs using Viterbi segmentation.
     * SentencePiece uses U+2581 (▁) as word boundary marker.
     */
    fun encode(text: String): IntArray {
        // SentencePiece normalizes: replace spaces with ▁, prepend ▁
        val normalized = "\u2581" + text.replace(" ", "\u2581")
        return viterbiEncode(normalized)
    }

    private fun viterbiEncode(text: String): IntArray {
        val n = text.length
        // best[i] = (score, pieceId, prevPos) for best segmentation ending at position i
        val best = Array(n + 1) { Triple(Float.NEGATIVE_INFINITY, -1, -1) }
        best[0] = Triple(0f, -1, -1)

        for (i in 0 until n) {
            if (best[i].first == Float.NEGATIVE_INFINITY) continue

            // Find all pieces starting at position i using trie
            val matches = trie.findPrefixes(text, i)
            for ((pieceId, endPos) in matches) {
                val score = best[i].first + pieces[pieceId].score
                if (score > best[endPos].first) {
                    best[endPos] = Triple(score, pieceId, i)
                }
            }

            // Fallback: single character as unknown (byte fallback)
            val nextPos = i + 1
            if (nextPos <= n) {
                val fallbackScore = best[i].first - 100f // heavy penalty
                if (fallbackScore > best[nextPos].first) {
                    // Try to find the byte piece
                    val ch = text[i]
                    val bytePieceId = findBytePiece(ch)
                    if (bytePieceId >= 0) {
                        val byteScore = best[i].first + pieces[bytePieceId].score
                        if (byteScore > best[nextPos].first) {
                            best[nextPos] = Triple(byteScore, bytePieceId, i)
                        }
                    } else if (fallbackScore > best[nextPos].first) {
                        // Use unknown token
                        val unkId = pieces.indexOfFirst { it.type == PieceType.UNKNOWN }
                        best[nextPos] = Triple(fallbackScore, if (unkId >= 0) unkId else 0, i)
                    }
                }
            }
        }

        // Backtrack to find the best segmentation
        val result = mutableListOf<Int>()
        var pos = n
        while (pos > 0) {
            val (_, pieceId, prevPos) = best[pos]
            if (pieceId >= 0) {
                result.add(pieceId)
            }
            pos = prevPos
        }
        result.reverse()
        return result.toIntArray()
    }

    private fun findBytePiece(ch: Char): Int {
        // For byte fallback, SentencePiece uses <0xHH> format
        val bytes = ch.toString().toByteArray(Charsets.UTF_8)
        if (bytes.size == 1) {
            val hex = String.format("<0x%02X>", bytes[0].toInt() and 0xFF)
            return pieceToId[hex] ?: -1
        }
        // Multi-byte: return first byte piece, caller handles rest
        return -1
    }

    private class Trie {
        private class Node {
            val children = HashMap<Char, Node>()
            var pieceId: Int = -1
        }

        private val root = Node()

        fun insert(piece: String, id: Int) {
            var node = root
            for (ch in piece) {
                node = node.children.getOrPut(ch) { Node() }
            }
            node.pieceId = id
        }

        /** Returns list of (pieceId, endPosition) for all prefixes starting at startPos */
        fun findPrefixes(text: String, startPos: Int): List<Pair<Int, Int>> {
            val results = mutableListOf<Pair<Int, Int>>()
            var node = root
            for (i in startPos until text.length) {
                node = node.children[text[i]] ?: break
                if (node.pieceId >= 0) {
                    results.add(node.pieceId to (i + 1))
                }
            }
            return results
        }
    }

    companion object {
        /**
         * Load a SentencePiece .model file from an InputStream.
         * Parses the protobuf manually (no protobuf dependency needed).
         */
        fun load(input: InputStream): SentencePieceTokenizer {
            val data = input.readBytes()
            val pieces = parseModelProto(data)
            return SentencePieceTokenizer(pieces)
        }

        private fun parseModelProto(data: ByteArray): List<SentencePiece> {
            val pieces = mutableListOf<SentencePiece>()
            val buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
            var pos = 0

            while (pos < data.size) {
                val (fieldNum, wireType, newPos) = readTag(data, pos)
                pos = newPos

                when {
                    // Field 1 = pieces (repeated message)
                    fieldNum == 1 && wireType == 2 -> {
                        val (msgBytes, endPos) = readLengthDelimited(data, pos)
                        pos = endPos
                        pieces.add(parseSentencePiece(msgBytes))
                    }
                    // Skip other fields
                    wireType == 0 -> {
                        // Varint
                        val (_, endPos) = readVarint(data, pos)
                        pos = endPos
                    }
                    wireType == 1 -> {
                        // 64-bit
                        pos += 8
                    }
                    wireType == 2 -> {
                        // Length-delimited
                        val (_, endPos) = readLengthDelimited(data, pos)
                        pos = endPos
                    }
                    wireType == 5 -> {
                        // 32-bit
                        pos += 4
                    }
                    else -> {
                        // Unknown wire type, try to skip
                        pos++
                    }
                }
            }

            return pieces
        }

        private fun parseSentencePiece(data: ByteArray): SentencePiece {
            var piece = ""
            var score = 0f
            var type = PieceType.NORMAL
            var pos = 0

            while (pos < data.size) {
                val (fieldNum, wireType, newPos) = readTag(data, pos)
                pos = newPos

                when {
                    fieldNum == 1 && wireType == 2 -> {
                        val (bytes, endPos) = readLengthDelimited(data, pos)
                        piece = String(bytes, Charsets.UTF_8)
                        pos = endPos
                    }
                    fieldNum == 2 && wireType == 5 -> {
                        score = ByteBuffer.wrap(data, pos, 4)
                            .order(ByteOrder.LITTLE_ENDIAN).float
                        pos += 4
                    }
                    fieldNum == 3 && wireType == 0 -> {
                        val (value, endPos) = readVarint(data, pos)
                        type = PieceType.fromValue(value.toInt())
                        pos = endPos
                    }
                    wireType == 0 -> {
                        val (_, endPos) = readVarint(data, pos)
                        pos = endPos
                    }
                    wireType == 2 -> {
                        val (_, endPos) = readLengthDelimited(data, pos)
                        pos = endPos
                    }
                    wireType == 5 -> pos += 4
                    wireType == 1 -> pos += 8
                    else -> pos++
                }
            }

            return SentencePiece(piece, score, type)
        }

        private fun readTag(data: ByteArray, pos: Int): Triple<Int, Int, Int> {
            val (value, newPos) = readVarint(data, pos)
            val fieldNum = (value shr 3).toInt()
            val wireType = (value and 0x7).toInt()
            return Triple(fieldNum, wireType, newPos)
        }

        private fun readVarint(data: ByteArray, startPos: Int): Pair<Long, Int> {
            var result = 0L
            var shift = 0
            var pos = startPos
            while (pos < data.size) {
                val b = data[pos].toInt() and 0xFF
                result = result or ((b and 0x7F).toLong() shl shift)
                pos++
                if (b and 0x80 == 0) break
                shift += 7
            }
            return result to pos
        }

        private fun readLengthDelimited(data: ByteArray, pos: Int): Pair<ByteArray, Int> {
            val (length, dataStart) = readVarint(data, pos)
            val len = length.toInt()
            val bytes = data.copyOfRange(dataStart, dataStart + len)
            return bytes to (dataStart + len)
        }
    }
}
