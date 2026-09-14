package app.touchdrop

import android.graphics.RuntimeShader
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Build

/** Small GPU background used by the intro on Android 13+ (API 33). */
object SandShader {
    const val SOURCE = """
        uniform float2 iResolution;
        uniform float iTime;

        float hash21(float2 p) {
            p = fract(p * float2(123.34, 456.21));
            p += dot(p, p + 45.32);
            return fract(p.x * p.y);
        }

        half4 main(float2 p) {
            float2 uv = p / iResolution;
            float2 cell = floor(uv * 220.0);
            float2 f = fract(uv * 220.0) - 0.5;
            float seed = hash21(cell);
            float drift = sin(iTime * (0.35 + seed * 0.8) + seed * 20.0) * 0.16;
            f.x += drift + iTime * 0.018;
            float grain = smoothstep(0.115, 0.0, length(f));
            float shimmer = 0.55 + 0.45 * sin(iTime * 1.7 + seed * 40.0);
            float3 dark = float3(0.018, 0.012, 0.008);
            float3 gold = float3(0.78, 0.47, 0.16) * (0.18 + 0.42 * shimmer);
            float3 color = dark + gold * grain;
            return half4(color, 1.0);
        }
    """

    /** Returns true when the GPU shader was drawn; safe on API 29+. */
    fun draw(c:Canvas, paint:Paint, width:Float, height:Float, time:Float):Boolean {
        if (Build.VERSION.SDK_INT < 33) return false
        Api33.draw(c,paint,width,height,time)
        return true
    }

    @android.annotation.TargetApi(33)
    private object Api33 {
        private val shader = RuntimeShader(SOURCE)
        fun draw(c:Canvas, paint:Paint, width:Float, height:Float, time:Float) {
            shader.setFloatUniform("iResolution",width,height)
            shader.setFloatUniform("iTime",time)
            paint.shader=shader
            c.drawRect(0f,0f,width,height,paint)
            paint.shader=null
        }
    }
}
