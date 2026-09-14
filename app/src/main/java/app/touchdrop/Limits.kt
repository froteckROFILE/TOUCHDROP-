package app.touchdrop

object Limits {
    const val MAX_FILES = 50
    const val MAX_FILE = 1024L * 1024 * 1024
    const val MAX_BATCH = 2L * 1024 * 1024 * 1024
    const val CHUNK = 16 * 1024
    val mimeTypes = setOf("image/jpeg", "image/png", "image/webp", "image/gif")
    fun validSize(size: Long) = size in 1..MAX_FILE
    fun cleanName(name: String) = name.replace(Regex("[^\\p{L}\\p{N}._ -]"), "_").take(100).ifBlank { "photo" }
    fun validMime(value:String)=value.length<=100 && value.matches(Regex("[a-zA-Z0-9][a-zA-Z0-9!#$&^_.+-]*/[a-zA-Z0-9][a-zA-Z0-9!#$&^_.+-]*"))
    fun signature(b: ByteArray): String? {
        if (b.size >= 3 && b[0] == 0xff.toByte() && b[1] == 0xd8.toByte() && b[2] == 0xff.toByte()) return "image/jpeg"
        if (b.size >= 8 && b.take(8).toByteArray().contentEquals(byteArrayOf(0x89.toByte(),80,78,71,13,10,26,10))) return "image/png"
        if (b.size >= 6 && String(b,0,6,Charsets.US_ASCII) in setOf("GIF87a","GIF89a")) return "image/gif"
        if (b.size >= 12 && String(b,0,4,Charsets.US_ASCII)=="RIFF" && String(b,8,4,Charsets.US_ASCII)=="WEBP") return "image/webp"
        return null
    }
}
