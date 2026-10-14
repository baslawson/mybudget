package com.mybudget.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

/**
 * The balance forecast's line (Reports, Budget.forecast): the cash in the budget's accounts day by day, from today on the left
 * to the last day on the right, with the zero line, and the part below zero drawn in [below]. Its words are the card's.
 */
public class ForecastChart extends View {
    private final long[] daily;private final int line,below,axis;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    public ForecastChart(Context context,long[] daily,int line,int below,int axis){
        super(context);this.daily=daily;this.line=line;this.below=below;this.axis=axis;
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
    private float dp(float n){return n*getResources().getDisplayMetrics().density;}
    @Override protected void onMeasure(int w,int h){setMeasuredDimension(MeasureSpec.getSize(w),(int)dp(120));}
    @Override protected void onDraw(Canvas canvas){
        if(daily.length<2)return;long highest=0,lowest=0;for(long v:daily){highest=Math.max(highest,v);lowest=Math.min(lowest,v);}
        final long min=lowest,span=Math.max(1,highest-lowest);
        float w=getWidth(),top=dp(6),bottom=getHeight()-dp(6),step=w/(daily.length-1);
        java.util.function.LongFunction<Float> y=v->bottom-(bottom-top)*(v-min)/(float)span;
        paint.setStyle(Paint.Style.FILL);paint.setColor(axis);float zero=y.apply(0);canvas.drawRect(0,zero,w,zero+dp(1),paint);
        Path path=new Path();for(int i=0;i<daily.length;i++){float x=step*i,v=y.apply(daily[i]);if(i==0)path.moveTo(x,v);else path.lineTo(x,v);}
        paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(2.5f));paint.setStrokeJoin(Paint.Join.ROUND);paint.setColor(line);canvas.drawPath(path,paint);
        // Below zero: the same line again, clipped to under the zero line.
        if(min<0){canvas.save();canvas.clipRect(0,zero,w,getHeight());paint.setColor(below);canvas.drawPath(path,paint);canvas.restore();}
    }
}
