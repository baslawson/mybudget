package com.mybudget.app;

import android.content.Context;
import android.graphics.*;
import android.view.MotionEvent;
import android.view.View;
import java.util.function.IntConsumer;

/** Reports' spending charts, drawn like CashFlowChart. Each has a list under it with every value in text (its table view). */
final class SpendingCharts {
    private SpendingCharts(){}
    /** The spending breakdown as a donut: slices clockwise from the top, biggest first, 2dp gaps; the total in the middle. */
    static final class Donut extends View {
        private final long[] values;private final int[] colors;private final String centre,caption;private final int textColor,mutedColor;
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);private final RectF box=new RectF();
        Donut(Context context,long[] values,int[] colors,String centre,String caption,int textColor,int mutedColor){super(context);this.values=values;this.colors=colors;this.centre=centre;this.caption=caption;this.textColor=textColor;this.mutedColor=mutedColor;}
        private float dp(float n){return n*getResources().getDisplayMetrics().density;}
        @Override protected void onMeasure(int w,int h){setMeasuredDimension(MeasureSpec.getSize(w),(int)dp(200));}
        @Override protected void onDraw(Canvas canvas){
            float ring=dp(28),size=Math.min(getWidth(),getHeight())-ring-dp(4),cx=getWidth()/2f,cy=getHeight()/2f;box.set(cx-size/2,cy-size/2,cx+size/2,cy+size/2);
            long total=0;for(long v:values)total+=v;paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(ring);paint.setStrokeCap(Paint.Cap.BUTT);
            if(total>0){float start=-90,gap=values.length>1?(float)Math.toDegrees(dp(2)/(size/2)):0; // a 2dp gap between slices
                for(int i=0;i<values.length;i++){float sweep=360f*values[i]/total;paint.setColor(colors[i]);if(sweep>gap)canvas.drawArc(box,start+gap/2,sweep-gap,false,paint);start+=sweep;}}
            else{paint.setColor(mutedColor);paint.setAlpha(60);canvas.drawArc(box,0,360,false,paint);paint.setAlpha(255);}
            paint.setStyle(Paint.Style.FILL);paint.setTextAlign(Paint.Align.CENTER);paint.setColor(textColor);paint.setFakeBoldText(true);paint.setTextSize(dp(18));canvas.drawText(centre,cx,cy+dp(2),paint);
            paint.setFakeBoldText(false);paint.setColor(mutedColor);paint.setTextSize(dp(12));canvas.drawText(caption,cx,cy+dp(20),paint);
        }
    }
    /** One category's (or group's) spending per month: thin bars from zero with 4dp rounded tops, a dashed average line; a tap selects a month. */
    static final class Trend extends View {
        private final long[] values;private final String[] months;private final long average;private final int barColor,textColor,lineColor,highlight;
        private int selected;private final IntConsumer onSelect;private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        Trend(Context context,long[] values,String[] months,long average,int barColor,int textColor,int lineColor,int highlight,int selected,IntConsumer onSelect){
            super(context);this.values=values;this.months=months;this.average=average;this.barColor=barColor;this.textColor=textColor;this.lineColor=lineColor;this.highlight=highlight;this.selected=selected;this.onSelect=onSelect;setFocusable(true);setClickable(true);}
        private float dp(float n){return n*getResources().getDisplayMetrics().density;}
        @Override protected void onMeasure(int w,int h){setMeasuredDimension(MeasureSpec.getSize(w),(int)dp(150));}
        @Override protected void onDraw(Canvas canvas){
            float w=getWidth(),top=dp(8),bottom=getHeight()-dp(24),group=w/months.length;long max=Math.max(1,average);for(long v:values)max=Math.max(max,v);
            float bar=Math.min(dp(14),group-dp(8)),radius=Math.min(dp(4),bar/2);
            for(int i=0;i<months.length;i++){float centre=group*i+group/2;
                if(i==selected){paint.setColor(highlight);canvas.drawRoundRect(group*i+dp(2),top-dp(4),group*(i+1)-dp(2),getHeight()-dp(2),dp(10),dp(10),paint);}
                if(values[i]>0){float height=Math.max(dp(2),(bottom-top)*values[i]/max);paint.setColor(barColor);canvas.drawRoundRect(centre-bar/2,bottom-height,centre+bar/2,bottom,radius,radius,paint);if(height>radius)canvas.drawRect(centre-bar/2,bottom-Math.min(radius,height),centre+bar/2,bottom,paint);}
                paint.setColor(textColor);paint.setTextSize(dp(months.length>6?9:11));paint.setTextAlign(Paint.Align.CENTER);paint.setFakeBoldText(i==selected);canvas.drawText(months[i],centre,getHeight()-dp(8),paint);}
            paint.setFakeBoldText(false);paint.setColor(lineColor);canvas.drawRect(0,bottom,w,bottom+dp(1),paint);
            if(average>0){float y=bottom-(bottom-top)*average/max;paint.setStrokeWidth(dp(1.5f));paint.setColor(textColor);for(float x=0;x<w;x+=dp(8))canvas.drawLine(x,y,Math.min(w,x+dp(4)),y,paint);} // the average, dashed
        }
        @Override public boolean onTouchEvent(MotionEvent e){
            if(e.getAction()==MotionEvent.ACTION_UP){int i=(int)Math.max(0,Math.min(months.length-1,e.getX()/(getWidth()/(float)months.length)));selected=i;onSelect.accept(i);invalidate();performClick();}
            return true;
        }
        @Override public boolean performClick(){return super.performClick();}
        // TalkBack: the description (set by Reports) reads every month's amounts; scrolling forward or back (or the arrow keys)
        // picks the next or previous month, as a tap does, and says its amounts ([spoken]).
        private String[] spoken;
        void spoken(String[] spoken){this.spoken=spoken;}
        @Override public void onInitializeAccessibilityNodeInfo(android.view.accessibility.AccessibilityNodeInfo info){super.onInitializeAccessibilityNodeInfo(info);
            if(selected>0)info.addAction(android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD);
            if(selected<months.length-1)info.addAction(android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD);}
        @Override public boolean performAccessibilityAction(int action,android.os.Bundle args){
            if(action==android.view.accessibility.AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)return pick(selected+1);
            if(action==android.view.accessibility.AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)return pick(selected-1);return super.performAccessibilityAction(action,args);}
        @Override public boolean onKeyDown(int code,android.view.KeyEvent e){
            if(code==android.view.KeyEvent.KEYCODE_DPAD_RIGHT&&pick(selected+1)||code==android.view.KeyEvent.KEYCODE_DPAD_LEFT&&pick(selected-1))return true;return super.onKeyDown(code,e);}
        private boolean pick(int i){if(i<0||i>=months.length)return false;selected=i;onSelect.accept(i);invalidate();if(spoken!=null)announceForAccessibility(spoken[i]);return true;}
        }
}
