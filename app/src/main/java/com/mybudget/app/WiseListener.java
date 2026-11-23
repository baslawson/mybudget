package com.mybudget.app;

import android.app.Notification;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.provider.Settings;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Reads Wise's card payment notifications (Settings › Wise card payments; off until turned on there and Android's
 * notification access is allowed). Only Wise's notifications are looked at; nothing else is read or kept. What a payment
 * becomes: WiseNotifications.add.
 *
 * Keys in prefs "budget" (this phone only, not in backups): wise_on, wise_since (when it was turned on: older notifications are
 * left alone, as their payments may be in already), wise_account (the account's id), wise_seen (one line per notification
 * handled: its day, a tab, its key (Android's key, title and text), a tab, the transaction it became, if any, a tab, its time;
 * kept KEEP_DAYS, longer than Wise's notifications stay (72 hours), and older ones are never read), wise_status (the last
 * outcome, for Settings), wise_unread (card payment notifications that couldn't be read).
 *
 * Android calls this on the main thread, as MyBudget's screens run, so a save here can't happen in the middle of one of theirs.
 * While a MyBudget form or Planner's add-expense dialog is open the notification waits (saving now would make that form's save
 * refuse, "MyBudget changed meanwhile", or add Planner's expense a second time); it's looked at again when they close and when
 * MyBudget opens (catchUp), as long as the notification is still there. The same goes for one that came before the phone was
 * first unlocked, and for one refused because the Wise account was gone (choosing it again picks the payment up).
 */
public class WiseListener extends NotificationListenerService {
    private static volatile WiseListener live;
    static final int KEEP_DAYS=14;
    static final int SAME_DAYS=3; // as long as Wise's notifications stay (72 hours): the same key and words within it are one payment

    @Override public void onListenerConnected(){live=this;catchUp(this);}
    @Override public void onListenerDisconnected(){if(live==this)live=null;}
    @Override public void onDestroy(){if(live==this)live=null;super.onDestroy();}
    @Override public void onNotificationPosted(StatusBarNotification sbn){try{handle(this,sbn);}catch(RuntimeException e){android.util.Log.w("WiseListener","Couldn't add a Wise payment",e);}}

    /** Wise's notifications still showing, handled now (those already handled are skipped). */
    static void catchUp(Context c){WiseListener l=live;if(l==null||!on(c))return;
        StatusBarNotification[] all;try{all=l.getActiveNotifications();}catch(RuntimeException e){return;}
        if(all==null)return;List<StatusBarNotification> wise=new ArrayList<>();for(StatusBarNotification s:all)if(WiseNotifications.PACKAGE.equals(s.getPackageName()))wise.add(s);
        wise.sort((a,b)->Long.compare(when(a),when(b))); // oldest first, as they came
        for(StatusBarNotification s:wise)try{handle(c,s);}catch(RuntimeException e){android.util.Log.w("WiseListener","Couldn't add a Wise payment",e);}}
    /** catchUp once what's running now has finished (a window closing). */
    static void catchUpSoon(Context c){Context app=c.getApplicationContext();new android.os.Handler(android.os.Looper.getMainLooper()).post(()->catchUp(app));}

    static boolean on(Context c){return DirectBoot.isUnlocked(c)&&prefs(c).getBoolean("wise_on",false);}
    private static SharedPreferences prefs(Context c){return c.getSharedPreferences("budget",Context.MODE_PRIVATE);}
    private static long when(StatusBarNotification s){long w=s.getNotification().when;return w>0?w:s.getPostTime();}

    static void handle(Context c,StatusBarNotification sbn){ // package-private for BudgetInstrumentation
        if(!WiseNotifications.PACKAGE.equals(sbn.getPackageName())||!on(c))return;
        Notification n=sbn.getNotification();if((n.flags&Notification.FLAG_GROUP_SUMMARY)!=0)return; // a group's summary repeats one of its notifications
        Bundle x=n.extras;if(x==null)return;
        String title=text(x.getCharSequence(Notification.EXTRA_TITLE)),body=text(x.getCharSequence(Notification.EXTRA_TEXT));if(body==null)body=text(x.getCharSequence(Notification.EXTRA_BIG_TEXT));
        if(title==null||!title.trim().equalsIgnoreCase(WiseNotifications.TITLE))return; // not a card payment: left alone
        LocalDate date=Instant.ofEpochMilli(when(sbn)).atZone(ZoneId.systemDefault()).toLocalDate(),today=LocalDate.now();
        if(date.isBefore(today.minusDays(KEEP_DAYS-1)))return; // older than what's remembered: never read
        if(when(sbn)<prefs(c).getLong("wise_since",0))return; // before it was turned on
        if(MainActivity.formOpen())return; // later (class comment)
        // Known by Android's key with its title and text, and its time, or a day within SAME_DAYS: one posted again (a new time, on
        // later days too) or read in another time zone is the same; tabs and line breaks are spaces, as kept.
        SharedPreferences prefs=prefs(c);String key=(sbn.getKey()+"|"+title.trim()+"|"+(body==null?"":body.trim())).replace('\t',' ').replace('\n',' ').replace('\r',' ');
        List<String> seen=new ArrayList<>();Set<String> claimed=new HashSet<>();long oldest=today.minusDays(KEEP_DAYS).toEpochDay();
        for(String line:prefs.getString("wise_seen","").split("\n")){String[] f=line.split("\t",-1);if(f.length!=4)continue;
            long day;try{day=Long.parseLong(f[0]);}catch(NumberFormatException e){continue;}if(day<oldest)continue;
            if(f[1].equals(key)&&(f[3].equals(String.valueOf(when(sbn)))||Math.abs(day-date.toEpochDay())<=SAME_DAYS))return;seen.add(line);if(!f[2].isEmpty())claimed.add(f[2]);}
        SharedPreferences.Editor edit=prefs.edit();String mark=date.toEpochDay()+"\t"+key+"\t",time="\t"+when(sbn);
        WiseNotifications.Payment p=WiseNotifications.read(title,body);
        if(p==null){seen.add(mark+time);edit.putInt("wise_unread",prefs.getInt("wise_unread",0)+1).putString("wise_status","A Wise card payment couldn't be read, so it wasn't added. Add it by hand, or import Wise's statement.");
            edit.putString("wise_seen",String.join("\n",seen)).commit();return;}
        String raw=prefs.getString("data",null);Budget budget;
        try{budget=raw==null?new Budget():BudgetStore.decode(raw);}catch(Exception e){return;} // MyBudget itself says the data can't be read
        WiseNotifications.Result r=WiseNotifications.add(budget,prefs.getString("wise_account",""),p,date,claimed);
        edit.putString("wise_status",WiseNotifications.describe(r,p,Ui.pretty(date.toString())));
        if(r.outcome!=WiseNotifications.Outcome.NO_ACCOUNT){seen.add(mark+(r.entry==null?"":r.entry.id)+time);edit.putString("wise_seen",String.join("\n",seen));} // no account: again once one is chosen
        if(r.changed())try{edit.putString("data",BudgetStore.encode(budget));}catch(Exception e){return;}
        if(!edit.commit())return;
        if(r.changed()){BudgetWidget.refresh(c);MainActivity.dataChangedElsewhere();}
    }
    private static String text(CharSequence s){return s==null?null:s.toString();}

    /** Whether Android lets MyBudget read notifications (Settings › Notification access). */
    static boolean allowed(Context c){String all=Settings.Secure.getString(c.getContentResolver(),"enabled_notification_listeners");
        if(all==null)return false;ComponentName me=new ComponentName(c,WiseListener.class);
        for(String s:all.split(":")){ComponentName n=ComponentName.unflattenFromString(s);if(me.equals(n))return true;}return false;}
    /** Android's screen to allow it: this app's own page where there is one (Android 11+), else the list. */
    static android.content.Intent accessSettings(Context c){
        if(android.os.Build.VERSION.SDK_INT>=30)return new android.content.Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
            .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,new ComponentName(c,WiseListener.class).flattenToString());
        return new android.content.Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS);}
}
