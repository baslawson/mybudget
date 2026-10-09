package com.mybudget.app;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.*;

/** The alarms MyBudget has set and not yet seen go off (see MissedReminders). Kept outside Android, which forgets them at a reboot. */
final class AlarmLedger {
    private final SharedPreferences prefs;
    AlarmLedger(Context c){prefs=c.getSharedPreferences("pending_alarms",Context.MODE_PRIVATE);}
    void set(String key,long trigger){if(prefs.getLong(key,0L)!=trigger)prefs.edit().putLong(key,trigger).apply();}
    void remove(String key){if(prefs.contains(key))prefs.edit().remove(key).apply();}
    // A snooze's own alarm firing leaves a later, still pending alarm of the same reminder in place.
    void fired(String key,long now){if(prefs.getLong(key,Long.MAX_VALUE)<=now)prefs.edit().remove(key).commit();}
    boolean has(String key){return prefs.contains(key);}
    Map<String,Long> all(){Map<String,Long> m=new HashMap<>();for(Map.Entry<String,?> e:prefs.getAll().entrySet())if(e.getValue() instanceof Long)m.put(e.getKey(),(Long)e.getValue());return m;}
    void keepOnly(Map<String,Long> remaining){SharedPreferences.Editor edit=prefs.edit();for(String k:all().keySet())if(!remaining.containsKey(k))edit.remove(k);edit.commit();}
}

/**
 * The snoozes and on-time deliveries of reminders, on this phone (as Planner's DeliveredAlarms and ReminderDeliveries). A
 * snoozed reminder rang at a fixed time, so after the clock is set back it is ahead again and rescheduling would ring it twice:
 * its time is recorded once it has rung. An on-time one is recorded by its local date and offset (Reminders.deliveryKey), which
 * a time zone change doesn't move.
 */
final class Deliveries {
    private final SharedPreferences snoozed,onTime;
    Deliveries(Context c){snoozed=c.getSharedPreferences("delivered_alarms",Context.MODE_PRIVATE);onTime=c.getSharedPreferences("reminder_deliveries",Context.MODE_PRIVATE);}
    void record(String key,long trigger){if(snoozed.getLong(key,0L)!=trigger)snoozed.edit().putLong(key,trigger).apply();}
    boolean delivered(String key,long trigger){return snoozed.contains(key)&&snoozed.getLong(key,0L)==trigger;}
    void recordOnTime(long id,String deliveryKey){String k=Long.toString(id);if(!deliveryKey.equals(onTime.getString(k,null)))onTime.edit().putString(k,deliveryKey).commit();}
    boolean deliveredOnTime(long id,String deliveryKey){return deliveryKey.equals(onTime.getString(Long.toString(id),null));}
    /** Its reminder changed or went: neither record means anything now. */
    void forget(long id){String key=Reminders.Found.key(id);if(snoozed.contains(key))snoozed.edit().remove(key).apply();String k=Long.toString(id);if(onTime.contains(k))onTime.edit().remove(k).apply();}
}

/**
 * Snoozed reminders (SnoozeActivity): reminder id → when it goes off again, with the reminder's schedule then
 * (Reminders.snoozeToken). On this phone only, as its alarm is; a change to the reminder or its transaction ends the snooze.
 */
final class Snoozes {
    private final SharedPreferences prefs;
    Snoozes(Context c){prefs=c.getSharedPreferences("reminder_snoozes",Context.MODE_PRIVATE);}
    /** When [f] is snoozed until, or 0: none, or one made for its schedule before a change. */
    long until(Reminders.Found f){String v=prefs.getString(Long.toString(f.reminder.id),null);if(v==null)return 0;int bar=v.indexOf('|');
        if(bar<0||!v.substring(bar+1).equals(Reminders.snoozeToken(f)))return 0;try{return Long.parseLong(v.substring(0,bar));}catch(NumberFormatException e){return 0;}}
    void put(long id,long until,String token){prefs.edit().putString(Long.toString(id),until+"|"+token).commit();}
    void remove(long id){String k=Long.toString(id);if(prefs.contains(k))prefs.edit().remove(k).commit();}
    Set<Long> ids(){Set<Long> s=new HashSet<>();for(String k:prefs.getAll().keySet())try{s.add(Long.parseLong(k));}catch(NumberFormatException e){}return s;}
    /** When [f]'s alarm goes off now: its snooze, or its offset from the transaction's date. */
    long trigger(Reminders.Found f){long s=until(f);return s>0?s:f.onTime();}
}

/**
 * Android 12 and later lets an app hold at most 500 alarms at once and refuses any more. So MyBudget keeps only the [LIMIT]
 * nearest reminders armed; the later ones wait and are armed as the window moves on: whenever one rings, when the app opens,
 * after a reboot or time change, and after a save. A waiting reminder has no alarm and is not in the AlarmLedger, so it is
 * never shown as "missed".
 */
final class AlarmWindow {
    private AlarmWindow(){}
    static final int LIMIT=400;
    static final class Selection {
        final Long horizon;final Set<String> atHorizon;
        Selection(Long horizon,Set<String> atHorizon){this.horizon=horizon;this.atHorizon=atHorizon;}
        boolean arms(String key,long trigger){return horizon==null||trigger<horizon||trigger==horizon&&atHorizon.contains(key);}
    }
    /** A bounded window, with a fixed choice among alarms at the same time. */
    static Selection select(Map<String,Long> triggers,long now,int limit){
        List<Map.Entry<String,Long>> ahead=new ArrayList<>();for(Map.Entry<String,Long> e:triggers.entrySet())if(e.getValue()>now)ahead.add(e);
        ahead.sort(Comparator.<Map.Entry<String,Long>>comparingLong(Map.Entry::getValue).thenComparing(Map.Entry::getKey));
        if(ahead.size()<=limit)return new Selection(null,Collections.emptySet());
        long horizon=ahead.get(limit-1).getValue();Set<String> at=new HashSet<>();for(int i=0;i<limit;i++)if(ahead.get(i).getValue()==horizon)at.add(ahead.get(i).getKey());
        return new Selection(horizon,at);}
    /** Newly waiting future alarms, plus already-waiting alarms now due that still match the stored reminder. */
    static Map<String,Long> deferred(Map<String,Long> previous,Map<String,Long> triggers,Selection selection,long now){Map<String,Long> m=new HashMap<>();
        for(Map.Entry<String,Long> e:triggers.entrySet()){long t=e.getValue();if(t>now&&!selection.arms(e.getKey(),t)||t<=now&&Long.valueOf(t).equals(previous.get(e.getKey())))m.put(e.getKey(),t);}return m;}
}

/**
 * A reboot clears Android's alarms, and so does a force stop (some phones do one when an app is swiped away). MyBudget keeps
 * its own list of the alarms it set (AlarmLedger); after a boot, the ones that fell due while the phone was off are shown once
 * as "missed"; when the app opens, the ones over [GRACE_MS] overdue ([INEXACT_GRACE_MS] without exact alarms). Only alarms that
 * were really set count, so a reminder saved with a time already past is never "missed", and the list is emptied of everything
 * due, so a second reboot shows nothing again.
 */
final class MissedReminders {
    private MissedReminders(){}
    static final long WINDOW_MS=24*3_600_000L;
    static final int MAX_SHOWN=10;
    // When the app opens, a reminder this late has lost its alarm.
    static final long GRACE_MS=10*60_000L;
    // Without exact alarms Android may deliver one up to an hour late, and Doze can add some minutes more.
    static final long INEXACT_GRACE_MS=90*60_000L;
    // How long the app waits after opening before it looks: an alarm released as the phone wakes up reaches ReminderReceiver first.
    static final long SETTLE_MS=5_000L;
    static long openGraceMs(boolean exact){return exact?GRACE_MS:INEXACT_GRACE_MS;}
    /** Alarms that fell due at most 24 hours ago, and at least [graceMs] ago. */
    static Map<String,Long> due(Map<String,Long> pending,long now,long graceMs){Map<String,Long> m=new HashMap<>();
        for(Map.Entry<String,Long> e:pending.entrySet())if(e.getValue()>now-WINDOW_MS&&e.getValue()<=now-graceMs)m.put(e.getKey(),e.getValue());return m;}
    /** What is left once they were handled: only alarms still ahead or less than [graceMs] late. */
    static Map<String,Long> remaining(Map<String,Long> pending,long now,long graceMs){Map<String,Long> m=new HashMap<>();
        for(Map.Entry<String,Long> e:pending.entrySet())if(e.getValue()>now-graceMs)m.put(e.getKey(),e.getValue());return m;}
    static final class Missed { final Reminders.Found found;final long due; Missed(Reminders.Found found,long due){this.found=found;this.due=due;} }
    /** The [due] alarms still worth showing, newest first: the reminder is still there at that time and wasn't delivered. */
    static List<Missed> select(Map<String,Long> due,Budget b,Snoozes snoozes,Deliveries deliveries){List<Missed> list=new ArrayList<>();
        for(Map.Entry<String,Long> e:due.entrySet()){Reminders.Found f=Reminders.find(b,Reminders.id(e.getKey()));if(f==null)continue;
            boolean snoozed=snoozes.until(f)>0;long expected=snoozes.trigger(f);
            // An exact match only: a reminder changed since (or moved by a time-zone change while off) isn't the one that was missed.
            if(e.getValue()!=expected||deliveries.delivered(f.key(),expected)||!snoozed&&deliveries.deliveredOnTime(f.reminder.id,Reminders.deliveryKey(f)))continue;
            list.add(new Missed(f,e.getValue()));}
        list.sort((x,y)->Long.compare(y.due,x.due));return list;}
}
