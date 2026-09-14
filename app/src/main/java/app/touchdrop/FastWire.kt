package app.touchdrop

import java.io.*
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Independently authenticated 256 KiB records: bounded memory and no per-record round-trip. */
object FastWire {
    const val BLOCK=256*1024
    fun transfer(input:InputStream, output:OutputStream,size:Long,key:ByteArray,aad:String,progress:(Long)->Unit){
        require(key.size==32&&Limits.validSize(size))
        val out=DataOutputStream(BufferedOutputStream(output,1024*1024))
        val salt=ByteArray(8).also{SecureRandom().nextBytes(it)};out.write(salt)
        var done=0L;var sequence=0
        while(done<size){
            val n=minOf(BLOCK.toLong(),size-done).toInt();val plain=ByteArray(n);DataInputStream(input).readFully(plain)
            val encrypted=cipher(Cipher.ENCRYPT_MODE,key,salt,sequence,aad).doFinal(plain)
            out.writeInt(encrypted.size);out.write(encrypted);done+=n;sequence++;progress(done)
        }
        out.flush()
    }
    fun receive(input:InputStream,output:OutputStream,size:Long,key:ByteArray,aad:String,sha:String,progress:(Long)->Unit){
        require(key.size==32&&Limits.validSize(size))
        val stream=DataInputStream(BufferedInputStream(input,1024*1024));val salt=ByteArray(8);stream.readFully(salt)
        val hash=MessageDigest.getInstance("SHA-256");var done=0L;var sequence=0
        while(done<size){
            val n=stream.readInt();require(n==minOf(BLOCK.toLong(),size-done).toInt()+16){"Bloc Wi-Fi invalide"}
            val data=ByteArray(n);stream.readFully(data)
            val plain=cipher(Cipher.DECRYPT_MODE,key,salt,sequence,aad).doFinal(data)
            output.write(plain);hash.update(plain);done+=plain.size;sequence++;progress(done)
        }
        require(stream.read()==-1){"Données en excès"}
        require(hash.digest().joinToString(""){"%02x".format(it)}==sha){"Empreinte différente"}
        output.flush()
    }
    private fun cipher(mode:Int,key:ByteArray,salt:ByteArray,sequence:Int,aad:String):Cipher{
        val nonce=java.nio.ByteBuffer.allocate(12).put(salt).putInt(sequence).array()
        return Cipher.getInstance("AES/GCM/NoPadding").apply{
            init(mode,SecretKeySpec(key,"AES"),GCMParameterSpec(128,nonce))
            updateAAD((aad+":"+sequence).toByteArray(Charsets.UTF_8))
        }
    }
}
