package app.touchdrop

import android.content.Context
import android.graphics.*
import android.view.View
import kotlin.math.*
import java.util.Random

/** A bounded particle field. Transfer progress determines dissolve/reassembly. */
class SandView(context:Context):View(context){
    var photo:Bitmap?=null
        set(value){
            field=value
            grains.forEachIndexed{i,g->colors[i]=value?.getPixel((g[0]*(value.width-1)).toInt(),(g[1]*(value.height-1)).toInt())?:Color.rgb(238,183,75)}
            invalidate()
        }
    var outgoing=true
    var amount=0f
        set(value){field=value.coerceIn(0f,1f);invalidate()}
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    private val random=Random(71)
    private val grains=Array(8200){floatArrayOf(random.nextFloat(),random.nextFloat(),random.nextFloat(),random.nextFloat())}
    private val colors=IntArray(8200){Color.rgb(238,183,75)}
    private val started=System.nanoTime()
    private var smooth=0f
    override fun onDraw(c:Canvas){
        super.onDraw(c)
        smooth+=(amount-smooth)*.12f
        if(abs(smooth-amount)<.001f)smooth=amount
        val w=width.toFloat();val h=height.toFloat()
        paint.shader=LinearGradient(0f,0f,w,h,intArrayOf(Color.rgb(12,15,26),Color.rgb(32,26,16)),null,Shader.TileMode.CLAMP)
        c.drawRoundRect(0f,0f,w,h,32f,32f,paint);paint.shader=null
        val t=(System.nanoTime()-started)/1e9
        val left=w*.045f;val right=w*.955f;val top=h*.15f;val bottom=h*.78f
        val frame=RectF(left,top,right,bottom)
        val image=photo
        val dissolve=if(outgoing)smooth else 1f-smooth
        if(image!=null){
            c.save();c.clipRect(left,top+(bottom-top)*dissolve,right,bottom)
            paint.alpha=255;c.drawBitmap(image,null,frame,paint);c.restore()
        }else{
            paint.color=Color.rgb(70,60,47);paint.alpha=180;c.drawRoundRect(frame,18f,18f,paint)
        }
        grains.forEachIndexed{grainIndex,g->
            val x=left+(right-left)*g[0];val y=top+(bottom-top)*g[1]
            val shift=((dissolve-g[1])/.38f).coerceIn(0f,1f)
            if(shift>0f&&shift<1f){
                val depth=.62f+g[2]*.72f
                val px=x+sin(t*1.8+g[2]*12+shift*5).toFloat()*w*.1f*shift+(g[2]-.5f)*w*.45f*shift
                val py=y-shift*(h+g[3]*h*.4f)
                val base=colors[grainIndex]
                paint.color=if(g[3]>.45f)Color.rgb(255,202,98) else base
                paint.alpha=((1f-shift)*(145f+depth*100f)).toInt()
                val radius=(.75f+g[2]*3.6f)*depth
                if(g[3]>.72f){
                    paint.alpha=(paint.alpha*.16f).toInt();c.drawCircle(px,py,radius*4.8f,paint)
                }
                paint.alpha=((1f-shift)*(145f+depth*100f)).toInt();c.drawCircle(px,py,radius,paint)
                if(g[3]>.82f){paint.alpha=65;paint.strokeWidth=maxOf(1f,radius*.42f);c.drawLine(px,py,px-5-radius,py+14*shift,paint)}
            }
        }
        paint.alpha=255;paint.color=Color.rgb(243,204,124);paint.textSize=13*resources.displayMetrics.scaledDensity
        c.drawText(if(amount>=1f)"ORIGINAL VÉRIFIÉ" else if(outgoing)"LE VENT EMPORTE VOTRE IMAGE" else "LES GRAINS RECOMPOSENT L’IMAGE",w*.055f,h*.12f,paint)
        paint.color=Color.rgb(173,161,139);paint.textSize=11*resources.displayMetrics.scaledDensity
        c.drawText("TOUCHDROP • GOLDEN WIND",w*.055f,h*.81f,paint)
        if(isShown&&windowVisibility==VISIBLE)postInvalidateOnAnimation()
    }
}
