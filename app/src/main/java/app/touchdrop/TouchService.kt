package app.touchdrop

import android.nfc.cardemulation.HostApduService
import android.os.Bundle
import android.os.SystemClock

object TouchSession {
    @Volatile var token:ByteArray?=null
    @Volatile var expires=0L
    @Volatile var confirm:(()->Unit)?=null
    fun clear(){token=null;confirm=null;expires=0}
    fun valid()=SystemClock.elapsedRealtime()<expires&&token!=null
}
class TouchService:HostApduService(){
    override fun processCommandApdu(command:ByteArray?,extras:Bundle?):ByteArray{
        val c=command?:return byteArrayOf(0x69,0x85.toByte())
        if(!TouchSession.valid())return byteArrayOf(0x69,0x85.toByte())
        if(c.contentEquals(SELECT))return TouchSession.token!!+OK
        if(c.size==37&&c.take(5).toByteArray().contentEquals(byteArrayOf(0x80.toByte(),0x10,0,0,32))&&
            java.security.MessageDigest.isEqual(c.copyOfRange(5,37),TouchSession.token)){
            val callback=TouchSession.confirm;TouchSession.clear();callback?.invoke();return OK
        }
        return byteArrayOf(0x69,0x85.toByte())
    }
    override fun onDeactivated(reason:Int){}
    companion object{
        val SELECT=byteArrayOf(0,0xA4.toByte(),4,0,7,0xF0.toByte(),0x54,0x44,0x52,0x4F,0x50,2,0)
        val OK=byteArrayOf(0x90.toByte(),0)
    }
}
