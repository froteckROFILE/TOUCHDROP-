package app.touchdrop

import android.content.Context
import android.graphics.*
import android.view.View

/** Geometric vector lettering: B N E T, without font-dependent Unicode glyphs. */
object BnetMark {
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
        repeat(4){letter(canvas,paint,it,x+it*height*.88f,y,height)}
    }
}
class BnetBrandView(context:Context):View(context){
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    init{contentDescription="BNET COMPANY"}
    override fun onDraw(c:Canvas){
        val h=height*.8f
        paint.color=Color.rgb(232,192,112)
        BnetMark.draw(c,paint,(width-h*3.4f)/2,height*.1f,h)
    }
}
