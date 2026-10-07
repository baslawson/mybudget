package com.mybudget.app;

import android.content.Context;
import android.graphics.*;
import android.view.MotionEvent;
import android.view.View;
import java.util.function.IntConsumer;

/**
 * Reflect's income vs spending bars, one pair per month on one axis from zero. Bars are thin with 4dp rounded tops,
 * 2dp apart; month names under them; a tap selects a month (its amounts are shown above the chart by the caller).
 */
public class CashFlowChart extends View {
    private final long[] income,spending;private final String[] months;
    private final int incomeColor,spendingColor,textColor,lineColor,highlight;
    private int selected;private final IntConsumer onSelect;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    public CashFlowChart(Context context,long[] income,long[] spending,String[] months,int incomeColor,int spendingColor,int textColor,int lineColor,int highlight,int selected,IntConsumer onSelect){
        super(context);this.income=income;this.spending=spending;this.months=months;this.incomeColor=incomeColor;this.spendingColor=spendingColor;this.textColor=textColor;this.lineColor=lineColor;this.highlight=highlight;this.selected=selected;this.onSelect=onSelect;
        setFocusable(true);setClickable(true);
    }
    private float dp(float n){return n*getResources().getDisplayMetrics().density;}
    @Override protected void onMeasure(int w,int h){setMeasuredDimension(MeasureSpec.getSize(w),(int)dp(168));}
    @Override protected void onDraw(Canvas canvas){
        float w=getWidth(),top=dp(8),bottom=getHeight()-dp(24),group=w/months.length;long max=1;
        for(int i=0;i<months.length;i++)max=Math.max(max,Math.max(income[i],spending[i]));
        float bar=Math.min(dp(14),(group-dp(14))/2),gap=dp(2),radius=dp(4);
        for(int i=0;i<months.length;i++){
            float centre=group*i+group/2;
            if(i==selected){paint.setColor(highlight);canvas.drawRoundRect(group*i+dp(3),top-dp(4),group*(i+1)-dp(3),getHeight()-dp(2),dp(10),dp(10),paint);}
            drawBar(canvas,centre-gap/2-bar,bar,income[i],max,top,bottom,radius,incomeColor);
            drawBar(canvas,centre+gap/2,bar,spending[i],max,top,bottom,radius,spendingColor);
            paint.setColor(textColor);paint.setTextSize(dp(11));paint.setTextAlign(Paint.Align.CENTER);paint.setFakeBoldText(i==selected);canvas.drawText(months[i],centre,getHeight()-dp(8),paint);
        }
        paint.setFakeBoldText(false);paint.setColor(lineColor);canvas.drawRect(0,bottom,w,bottom+dp(1),paint);
    }
    // Rounded at the top, square on the baseline.
    private void drawBar(Canvas canvas,float left,float width,long value,long max,float top,float bottom,float radius,int color){
        if(value<=0)return;float height=Math.max(dp(2),(bottom-top)*value/max);paint.setColor(color);
        canvas.drawRoundRect(left,bottom-height,left+width,bottom,radius,radius,paint);if(height>radius)canvas.drawRect(left,bottom-Math.min(radius,height),left+width,bottom,paint);
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
