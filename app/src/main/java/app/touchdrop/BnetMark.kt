package app.touchdrop

import android.content.Context
import android.graphics.*
import android.view.View

/** Geometric BNET monogram, drawn as angular gold strokes (no font dependency). */
object BnetMark {
    /** Draw the compact angular monogram used by the intro and the app footer. */
    fun drawLogo(canvas:Canvas,paint:Paint,cx:Float,cy:Float,size:Float){
        canvas.save();canvas.translate(cx-size*.58f,cy-size*.5f);canvas.scale(size/100f,size/100f)
        paint.style=Paint.Style.STROKE;paint.strokeWidth=9f;paint.strokeJoin=Paint.Join.BEVEL;paint.strokeCap=Paint.Cap.SQUARE
        val b=Path().apply{
            moveTo(6f,8f);lineTo(43f,8f);lineTo(61f,22f);lineTo(43f,39f);lineTo(6f,39f)
            moveTo(6f,39f);lineTo(43f,39f);lineTo(63f,57f);lineTo(48f,92f);lineTo(6f,92f);close()
        }
        canvas.drawPath(b,paint)
        val e=Path().apply{
            moveTo(68f,8f);lineTo(112f,8f);moveTo(68f,8f);lineTo(68f,92f)
            moveTo(68f,49f);lineTo(106f,49f);moveTo(68f,92f);lineTo(112f,92f)
        }
        canvas.drawPath(e,paint)
        val nt=Path().apply{
            moveTo(52f,58f);lineTo(52f,98f);moveTo(52f,58f);lineTo(91f,98f)
            moveTo(86f,58f);lineTo(114f,58f);moveTo(100f,58f);lineTo(100f,98f)
        }
        canvas.drawPath(nt,paint)
        paint.style=Paint.Style.FILL
        canvas.drawRect(117f,86f,124f,93f,paint)
        paint.style=Paint.Style.FILL;canvas.restore()
    }

    fun letter(canvas:Canvas,paint:Paint,index:Int,x:Float,y:Float,height:Float){
        canvas.save();canvas.translate(x,y);canvas.scale(height/100f,height/100f)
        paint.style=Paint.Style.STROKE;paint.strokeWidth=8f;paint.strokeJoin=Paint.Join.BEVEL;paint.strokeCap=Paint.Cap.SQUARE
        val p=Path()
        when(index){
            0->{p.moveTo(8f,8f);p.lineTo(48f,8f);p.lineTo(65f,24f);p.lineTo(48f,48f);p.lineTo(8f,48f);p.moveTo(48f,48f);p.lineTo(68f,66f);p.lineTo(50f,92f);p.lineTo(8f,92f);p.lineTo(8f,8f)}
            1->{p.moveTo(8f,92f);p.lineTo(8f,8f);p.lineTo(65f,92f);p.lineTo(65f,8f)}
            2->{p.moveTo(65f,8f);p.lineTo(8f,8f);p.lineTo(8f,92f);p.lineTo(65f,92f);p.moveTo(8f,48f);p.lineTo(52f,48f)}
            3->{p.moveTo(4f,8f);p.lineTo(70f,8f);p.moveTo(37f,8f);p.lineTo(37f,92f)}
        }
        canvas.drawPath(p,paint);paint.style=Paint.Style.FILL;canvas.restore()
    }
    fun draw(canvas:Canvas,paint:Paint,x:Float,y:Float,height:Float){
        drawLogo(canvas,paint,x+height*1.7f,y+height*.5f,height)
    }
}
class BnetBrandView(context:Context):View(context){
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    init{contentDescription="BNET COMPANY"}
    override fun onDraw(c:Canvas){
        val h=height*.92f
        paint.color=Color.rgb(232,192,112)
        BnetMark.drawLogo(c,paint,width*.5f,height*.5f,h)
    }
}
