package app.touchdrop
import java.security.MessageDigest

/** Reject duplicates, holes, excess bytes and wrong digests before gallery publication. */
class TransferGate(private val expected:Long,private val sha:String) {
    var received=0L; private set
    private val hash=MessageDigest.getInstance("SHA-256")
    private var closed=false
    init { require(Limits.validSize(expected));require(sha.matches(Regex("[0-9a-f]{64}"))) }
    fun accept(position:Long,bytes:ByteArray) {
        require(!closed&&position==received&&bytes.isNotEmpty()&&bytes.size<=Limits.CHUNK&&received+bytes.size<=expected)
        hash.update(bytes);received+=bytes.size
    }
    fun finish() {
        require(!closed&&received==expected);closed=true
        require(hash.digest().joinToString(""){"%02x".format(it)}==sha){"Empreinte différente"}
    }
}
