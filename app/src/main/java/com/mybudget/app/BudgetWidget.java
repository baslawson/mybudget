package com.mybudget.app;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.RemoteViews;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * The home-screen widget: this month's To budget and the categories pinned to Home (up to 5) with their Available, in the
 * budget's currency, dots with Hide amounts on. "+" opens the Add transaction form, anywhere else opens Home. It reads the
 * saved budget, so every save calls refresh(); the system also updates it daily (a new month) and when it's added.
 * Only the system's widget broadcasts are acted on: the receiver is exported for APPWIDGET_UPDATE, and an update from
 * anywhere only redraws MyBudget's own widgets.
 */
public class BudgetWidget extends AppWidgetProvider {
    static final int ROWS=5;
    private static final int[] ROW={R.id.widget_row1,R.id.widget_row2,R.id.widget_row3,R.id.widget_row4,R.id.widget_row5},
        NAME={R.id.widget_name1,R.id.widget_name2,R.id.widget_name3,R.id.widget_name4,R.id.widget_name5},
        AMOUNT={R.id.widget_amount1,R.id.widget_amount2,R.id.widget_amount3,R.id.widget_amount4,R.id.widget_amount5};
    @Override public void onReceive(Context context,Intent intent){String a=intent.getAction();if(a!=null&&a.startsWith("android.appwidget.action."))super.onReceive(context,intent);}
    @Override public void onUpdate(Context context,AppWidgetManager manager,int[] ids){refresh(context);} // ours only, whatever ids were sent
    @Override public void onAppWidgetOptionsChanged(Context context,AppWidgetManager manager,int id,Bundle options){refresh(context);}
    /** Redraws every MyBudget widget from the saved budget. Called after each save, Hide amounts and on resume; never fails a save. */
    static void refresh(Context context){
        try{AppWidgetManager manager=AppWidgetManager.getInstance(context);if(manager==null)return;
            int[] ids=manager.getAppWidgetIds(new ComponentName(context,BudgetWidget.class));if(ids==null||ids.length==0)return;
            android.content.SharedPreferences prefs=context.getSharedPreferences("budget",0);String raw=prefs.getString("data",null);
            boolean hide=context.getSharedPreferences("appearance",0).getBoolean("hideAmounts",false);
            for(int id:ids){int h=manager.getAppWidgetOptions(id).getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT,0);
                manager.updateAppWidget(id,views(context,raw,hide,h<=0?ROWS:(h-76)/24));}} // the header takes about 76dp, each row 24dp
        catch(RuntimeException ignored){}
    }
    /**
     * The widget for the saved budget [raw] (null: none yet), [hide]: Hide amounts, at most [rows] pinned categories (as many as
     * fit its height). Unreadable data shows "Open MyBudget", as the app itself refuses to change it then.
     */
    static RemoteViews views(Context context,String raw,boolean hide,int rows){
        RemoteViews v=new RemoteViews(context.getPackageName(),R.layout.widget);YearMonth month=YearMonth.now();
        v.setOnClickPendingIntent(R.id.widget_root,open(context,MainActivity.OPEN_HOME,0));v.setOnClickPendingIntent(R.id.widget_add,open(context,MainActivity.OPEN_ADD,1));
        Budget b;try{b=raw==null?new Budget():BudgetStore.decode(raw);}catch(Exception e){b=null;}
        if(b==null){v.setTextViewText(R.id.widget_title,"MyBudget can't read its budget");v.setTextViewText(R.id.widget_ready,"Open MyBudget");
            color(context,v,R.id.widget_ready,R.color.widget_ink);v.setViewVisibility(R.id.widget_add,View.GONE);for(int r:ROW)v.setViewVisibility(r,View.GONE);
            v.setViewVisibility(R.id.widget_empty,View.GONE);return v;}
        java.text.NumberFormat format=Budget.moneyFormat(b.currency,Locale.getDefault());
        String dots=format.getCurrency().getSymbol(Locale.getDefault())+"•••"; // as Ui.money with Hide amounts
        long ready=b.spendable(month);v.setTextViewText(R.id.widget_title,"To budget · "+month.format(DateTimeFormatter.ofPattern("MMMM")));
        v.setTextViewText(R.id.widget_ready,hide?dots:Budget.money(ready,format));color(context,v,R.id.widget_ready,ready<0&&!hide?R.color.widget_red:R.color.widget_ink);
        v.setContentDescription(R.id.widget_ready,hide?"To budget, amount hidden":"To budget "+Budget.money(ready,format));
        List<Budget.Category> pinned=b.pinned();int shown=Math.max(0,Math.min(Math.min(rows,ROWS),pinned.size()));
        for(int i=0;i<ROWS;i++){if(i>=shown){v.setViewVisibility(ROW[i],View.GONE);continue;}Budget.Category c=pinned.get(i);long available=b.available(c,month);
            v.setViewVisibility(ROW[i],View.VISIBLE);v.setTextViewText(NAME[i],c.name);v.setTextViewText(AMOUNT[i],hide?dots:Budget.money(available,format));
            color(context,v,AMOUNT[i],available<0&&!hide?R.color.widget_red:available>0&&!hide?R.color.widget_green:R.color.widget_ink);}
        v.setViewVisibility(R.id.widget_empty,pinned.isEmpty()&&rows>0?View.VISIBLE:View.GONE);
        return v;
    }
    // From Android 12 the colour is a resource, so it follows the phone's light or dark theme as it changes; before that it's fixed when drawn.
    private static void color(Context context,RemoteViews v,int view,int color){
        if(Build.VERSION.SDK_INT>=31)v.setColorStateList(view,"setTextColor",color);else v.setTextColor(view,context.getColor(color));}
    private static PendingIntent open(Context context,String what,int request){
        Intent i=new Intent(context,MainActivity.class).putExtra(MainActivity.OPEN,what).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return PendingIntent.getActivity(context,request,i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);}
}
