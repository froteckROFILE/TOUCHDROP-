package app.touchdrop

import android.content.Context
import android.graphics.*
import android.os.SystemClock
import android.view.View
import kotlin.math.*
import java.util.Random

class IntroView(context:Context):View(context){
    companion object{const val DURATION_MS=12000L}
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    private var start=0L
    private val random=Random()
    private data class Grain(val x:Float,val y:Float,val originX:Float,val originY:Float,val phase:Float,val begin:Float,val leave:Float,val speed:Float,val radius:Float)
    private var grains=emptyList<Grain>()
    init{isClickable=true;isFocusable=true;contentDescription="TOUCHDROP. BNET COMPANY. ENGINEERING BY LABED ABDNOUR."}
    override fun onSizeChanged(w:Int,h:Int,oldw:Int,oldh:Int){
        if(w<=0||h<=0)return
        val scale=minOf(1f,640f/w)
        val mw=(w*scale).toInt();val mh=(h*scale).toInt()
        val bitmap=Bitmap.createBitmap(mw,mh,Bitmap.Config.ARGB_8888)
        val mask=Canvas(bitmap);mask.scale(scale,scale);val list=ArrayList<Grain>()
        val step=2
        fun collect(begin:Float){
            for(y in 0 until mh step step)for(x in 0 until mw step step){
                if(Color.alpha(bitmap.getPixel(x,y))>90){
                    repeat(3){
                    val gx=(x+random.nextFloat()*step)/scale;val gy=(y+random.nextFloat()*step)/scale
                    list.add(Grain(gx,gy,gx-w*(.12f+random.nextFloat()*.25f),gy+(random.nextFloat()-.5f)*h*.18f,
                        random.nextFloat()*6.283f,begin+random.nextFloat()*.5f,6.8f+random.nextFloat()*1.3f,.12f+random.nextFloat()*.22f,
                        step/scale*(.18f+random.nextFloat()*.23f)))
                    }
                }
            }
            bitmap.eraseColor(Color.TRANSPARENT)
        }
        paint.typeface=Typeface.create("sans-serif-black",Typeface.BOLD)
        paint.textSize=w*.14f;paint.textSize*=w*.56f/paint.measureText("TOUCHDROP")
        val sizes="TOUCHDROP".map{paint.measureText(it.toString())}
        var x=(w-sizes.sum())/2
        "TOUCHDROP".forEachIndexed{i,ch->
            paint.color=Color.WHITE;paint.alpha=255
            mask.drawText(ch.toString(),x,h*.5f-(paint.ascent()+paint.descent())/2,paint);collect(.1f+i*.18f);x+=sizes[i]
        }
        val markHeight=w*.065f;val markLeft=(w-markHeight*3.4f)/2
        repeat(4){i->paint.color=Color.WHITE;BnetMark.letter(mask,paint,i,markLeft+i*markHeight*.88f,h*.60f,markHeight);collect(2.3f+i*.18f)}
        grains=list;bitmap.recycle();start=SystemClock.uptimeMillis()
    }
    override fun onDraw(c:Canvas){
        if(start==0L)return
        val t=((SystemClock.uptimeMillis()-start)/1000f).coerceAtMost(12f)
        val w=width.toFloat();val h=height.toFloat()
        if(w<=0||h<=0)return
        paint.alpha=255
        if (!SandShader.draw(c,paint,w,h,t)) {
            paint.shader=RadialGradient(w*.5f,h*.5f,maxOf(w,h)*.8f,intArrayOf(Color.rgb(35,27,17),Color.rgb(6,8,12)),null,Shader.TileMode.CLAMP)
            c.drawRect(0f,0f,w,h,paint);paint.shader=null
        }
        grains.forEach{g->
            if(t<g.begin)return@forEach
            val arrival=((t-g.begin)/2.8f).coerceIn(0f,1f)
            val ease=arrival*arrival*(3-2*arrival)
            val departure=((t-g.leave)/3.3f).coerceIn(0f,1f)
            val turbulence=sin(t*.8f+g.phase)
            val x=g.originX+(g.x-g.originX)*ease+(1-ease)*sin(arrival*3+g.phase)*w*.028f+
                departure*w*g.speed+sin(departure*3+g.phase)*departure*w*.045f
            val y=g.originY+(g.y-g.originY)*ease+(1-ease)*cos(arrival*3+g.phase)*h*.025f-
                departure*departure*h*.09f+cos(departure*3+g.phase)*departure*h*.035f
            paint.color=if(g.phase>4f)Color.rgb(255,237,185) else if(g.phase>2f)Color.rgb(230,179,84) else Color.rgb(180,126,46)
            paint.alpha=(255*minOf(arrival*4,1f)*(1-departure)).toInt().coerceIn(0,255)
            val grainAlpha=paint.alpha
            if(g.phase>4.8f){
                paint.alpha=(grainAlpha*.075f).toInt()
                c.drawCircle(x,y,g.radius*4.5f,paint)
            }
            paint.alpha=grainAlpha
            c.drawCircle(x,y,g.radius,paint)
            if(g.phase>5.2f&&(arrival<.9f||departure>.05f)){
                paint.alpha=(paint.alpha*.24f).toInt();paint.strokeWidth=g.radius*.5f
                c.drawLine(x,y,x-((1-ease)+departure)*w*.008f,y+turbulence*2,paint)
            }
        }
        drawQuill(c,t,w,h)
        paint.typeface=Typeface.create("sans-serif-medium",Typeface.NORMAL)
        paint.color=Color.rgb(236,214,170)
        paint.alpha=(((t-4.6f)/1.4f).coerceIn(0f,1f)*(1-((t-8.6f)/2.4f).coerceIn(0f,1f))*255).toInt()
        listOf("C O M P A N Y","ENGINEERING BY LABED ABDNOUR").forEachIndexed{i,line->
            paint.textSize=w*.028f
            if(paint.measureText(line)>w*.86f)paint.textSize*=w*.86f/paint.measureText(line)
            c.drawText(line,(w-paint.measureText(line))/2,h*(.70f+i*.032f),paint)
        }
        paint.alpha=255
        if(t<12f&&isShown)postInvalidateOnAnimation()
    }

    private fun drawQuill(c:Canvas,t:Float,w:Float,h:Float){
        val progress=((t-.15f)/6.4f).coerceIn(0f,1f)
        if(progress<=0f||progress>=1f)return
        val text="TOUCHDROP"
        paint.typeface=Typeface.create("sans-serif-black",Typeface.BOLD)
        paint.textSize=w*.14f;paint.textSize*=w*.56f/paint.measureText(text)
        val full=paint.measureText(text)
        val x=(w-full)/2f+full*progress
        val y=h*.5f
        val angle=-0.42f
        c.save();c.rotate(angle*57.2958f,x,y)
        paint.style=Paint.Style.STROKE;paint.strokeWidth=maxOf(2f,w*.006f)
        paint.color=Color.rgb(255,220,132);paint.alpha=230
        c.drawLine(x,y,x-w*.08f,y+h*.12f,paint)
        paint.style=Paint.Style.FILL
        val feather=Path().apply{
            moveTo(x-w*.08f,y+h*.12f)
            quadTo(x-w*.18f,y+h*.03f,x-w*.13f,y-h*.06f)
            quadTo(x-w*.06f,y-h*.015f,x,y)
            close()
        }
        paint.shader=LinearGradient(x-w*.17f,y-h*.08f,x,y+h*.1f,Color.rgb(255,236,184),Color.rgb(172,105,31),Shader.TileMode.CLAMP)
        c.drawPath(feather,paint);paint.shader=null
        paint.color=Color.rgb(255,233,166);paint.alpha=90
        c.drawCircle(x,y, w*.024f,paint)
        paint.alpha=255;c.restore()
    }
}
