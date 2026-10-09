package com.mybudget.app;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Sets a reminder's alarm, as Planner's ReminderScheduler does: exact when Android allows it, the [AlarmWindow.LIMIT] nearest
 * armed and the rest waiting, a ledger of what was set (missed reminders), and a snapshot for a locked reboot. MyBudget keeps its
 * budget as one saved text, so after every save the reminders are compared with what was set before ([armAll]): a reminder
 * gone or changed is cancelled (alarm, snooze, delivery records, notification, a ring), and only alarms that changed are set
 * again, except when Android may have dropped them all ([force]: a boot, an update, a time change, opening the app).
 *
 * All of it runs on one background thread ([WORK]), in order, as Planner's runs under its repository lock.
 */
final class ReminderScheduler {
    private ReminderScheduler(){}
    static final String EXTRA_REMINDER_ID="reminder_id",EXTRA_TRIGGER="trigger_at",EXTRA_TITLE="title",EXTRA_TEXT="text",EXTRA_WHEN="when",
        EXTRA_SUB="sub",EXTRA_DETAILS="details",EXTRA_SNOOZE_TOKEN="snooze_token",EXTRA_ENTER_TOKEN="enter_token",EXTRA_REPEATS="repeats",
        // "Until I stop it" and the length otherwise, as stored: the receiver resolves Default with the setting as it is when it rings.
        EXTRA_RING="ring",EXTRA_RING_SECONDS="ring_seconds";
    static final ExecutorService WORK=Executors.newSingleThreadExecutor();
    private static final AtomicBoolean queued=new AtomicBoolean(),forced=new AtomicBoolean();

    /** After a save, a ring or a setting change: every reminder checked again, soon, on [WORK] (several requests share one run). */
    static void request(Context c){request(c,false);}
    static void request(Context c,boolean force){Context app=c.getApplicationContext();if(force)forced.set(true);
        if(!queued.compareAndSet(false,true))return;
        WORK.execute(()->{queued.set(false);boolean f=forced.getAndSet(false);try{armAll(app,readBudget(app),f);}catch(Exception e){Log.w("ReminderScheduler","Couldn't set the reminders",e);}});}
    /** The saved budget, or null when nothing is saved yet. Throws if it can't be read. */
    static Budget readBudget(Context c)throws Exception{String raw=c.getSharedPreferences("budget",0).getString("data",null);return raw==null?null:BudgetStore.decode(raw);}
    static boolean hideAmounts(Context c){return c.getSharedPreferences("appearance",0).getBoolean("hideAmounts",false);}

    /** Exact alarms can be switched off by the user on Android 12+; reminders then arrive a little late. */
    static boolean canScheduleExact(Context c){return Build.VERSION.SDK_INT<31||c.getSystemService(AlarmManager.class).canScheduleExactAlarms();}

    /** Every reminder in [b] (null: none) as it should be now. Runs on [WORK]. */
    static synchronized void armAll(Context c,Budget b,boolean force){
        long now=System.currentTimeMillis();AlarmManager am=c.getSystemService(AlarmManager.class);
        AlarmLedger ledger=new AlarmLedger(c);Deliveries deliveries=new Deliveries(c);Snoozes snoozes=new Snoozes(c);
        SharedPreferences tokens=c.getSharedPreferences("reminder_tokens",0),signatures=c.getSharedPreferences("alarm_signatures",0),window=c.getSharedPreferences("alarm_window",0);
        List<Reminders.Found> all=b==null?new ArrayList<>():Reminders.all(b);Map<Long,Reminders.Found> byId=new HashMap<>();for(Reminders.Found f:all)byId.put(f.reminder.id,f);
        // 1. Reminders gone or changed since the last run (deleted, edited, entered, skipped): cancelled, as Planner cancels on an edit.
        SharedPreferences.Editor tokenEdit=tokens.edit();
        for(Map.Entry<String,?> e:tokens.getAll().entrySet()){long id;try{id=Long.parseLong(e.getKey());}catch(NumberFormatException x){tokenEdit.remove(e.getKey());continue;}
            Reminders.Found f=byId.get(id);String was=String.valueOf(e.getValue());
            if(f==null||!was.startsWith(Reminders.snoozeToken(f)+"#")){cancel(c,id);if(f==null)tokenEdit.remove(e.getKey());}
            // Only its sound changed, to one that doesn't ring: a ring (or one on its way) goes quiet, the reminder and its notification stay.
            else if(!was.equals(stamp(f))&&ReminderSound.ringSecondsNow(c,f.reminder.ring,f.reminder.ringSeconds)==null)AlarmService.quietIfRinging(c,id);}
        for(Reminders.Found f:all)tokenEdit.putString(Long.toString(f.reminder.id),stamp(f));tokenEdit.commit();
        for(long id:snoozes.ids())if(!byId.containsKey(id))snoozes.remove(id);
        // 2. When each goes off now, leaving out the ones that rang already (also after the clock went back).
        boolean hide=hideAmounts(c);Map<String,Long> triggers=new HashMap<>();Map<String,Reminders.Found> byKey=new HashMap<>();
        for(Reminders.Found f:all){boolean snoozed=snoozes.until(f)>0;long t=snoozes.trigger(f);
            if(snoozed?deliveries.delivered(f.key(),t):deliveries.deliveredOnTime(f.reminder.id,Reminders.deliveryKey(f)))continue;
            triggers.put(f.key(),t);byKey.put(f.key(),f);}
        // 3. The nearest [LIMIT] are armed, the rest wait.
        AlarmWindow.Selection selection=AlarmWindow.select(triggers,now,AlarmWindow.LIMIT);
        Map<String,Long> previous=deferred(window),waiting=AlarmWindow.deferred(previous,triggers,selection,now);
        SharedPreferences.Editor w=window.edit().clear();for(Map.Entry<String,Long> e:waiting.entrySet())w.putLong("deferred:"+e.getKey(),e.getValue());w.commit();
        // Alarms set before and no longer wanted (excluded from the window, or gone): freed first.
        for(Map.Entry<String,Long> e:ledger.all().entrySet()){Long t=triggers.get(e.getKey());
            if(e.getValue()>now&&(t==null||!selection.arms(e.getKey(),t))){cancelCode(c,Reminders.id(e.getKey()));ledger.remove(e.getKey());signatures.edit().remove(e.getKey()).apply();}}
        List<LockedAlarm> armed=new ArrayList<>();
        for(Map.Entry<String,Long> e:triggers.entrySet()){String key=e.getKey();long t=e.getValue();Reminders.Found f=byKey.get(key);
            // Past: an alarm Android hasn't delivered yet (also one set before an eastward time zone change moved it into the past) is left alone.
            if(t<=now)continue;
            if(!selection.arms(key,t))continue;
            LockedAlarm shown=LockedAlarm.of(c,b,f,t,hide);armed.add(shown);String line=shown.line();
            if(!force&&ledger.has(key)&&line.equals(signatures.getString(key,null)))continue;
            setReminderAlarm(am,t,pending(c,f.reminder.id,shown.intent(c,f.entry!=null?f.entry.memo:f.upcoming.memo),PendingIntent.FLAG_UPDATE_CURRENT));
            ledger.set(key,t);signatures.edit().putString(key,line).apply();}
        for(String key:signatures.getAll().keySet())if(!triggers.containsKey(key))signatures.edit().remove(key).apply();
        // 4. The snapshot for a locked reboot.
        saveLocked(c,armed,now);
    }
    // What a reminder was at the last run: its schedule (Reminders.snoozeToken), then its sound.
    private static String stamp(Reminders.Found f){return Reminders.snoozeToken(f)+"#"+f.reminder.ring+","+f.reminder.ringSeconds;}
    private static Map<String,Long> deferred(SharedPreferences window){Map<String,Long> m=new HashMap<>();
        for(Map.Entry<String,?> e:window.getAll().entrySet())if(e.getKey().startsWith("deferred:")&&e.getValue() instanceof Long)m.put(e.getKey().substring(9),(Long)e.getValue());return m;}
    static Map<String,Long> deferredReminders(Context c){return deferred(c.getSharedPreferences("alarm_window",0));}
    static void forgetDeferred(Context c,Map<String,Long> done){SharedPreferences w=c.getSharedPreferences("alarm_window",0);SharedPreferences.Editor e=w.edit();
        for(Map.Entry<String,Long> d:done.entrySet())if(w.getLong("deferred:"+d.getKey(),Long.MIN_VALUE)==d.getValue())e.remove("deferred:"+d.getKey());e.commit();}

    /** Writes the snapshot of [armed] when it changed or is half a day old, and sets its refresh alarm when it left alarms out. */
    private static void saveLocked(Context c,List<LockedAlarm> armed,long now){
        LockedAlarmStore store=DirectBoot.store(c);LockedSnapshot last=store.read();List<LockedAlarm> kept=LockedAlarmSelection.select(armed,now);
        Long rewrite=LockedAlarmSelection.rewriteAt(armed,now);
        boolean same=last!=null&&last.alarms.equals(kept)&&Objects.equals(last.rewriteAt,rewrite);
        if(same&&!LockedAlarmSelection.stale(last.fullAt,now))return;
        try{store.write(new LockedSnapshot(kept,rewrite,now));}catch(Exception e){Log.w("ReminderScheduler","Couldn't keep the alarms for a locked restart",e);return;}
        // A snapshot that left later alarms out is written again before its two weeks run out, even if no reminder rings and
        // MyBudget isn't opened by then. Inexact (a day to spare), and cleared when nothing was left out.
        Intent refresh=new Intent(c,BootReceiver.class).setAction(BootReceiver.ACTION_REFRESH_LOCKED);AlarmManager am=c.getSystemService(AlarmManager.class);
        if(rewrite==null){PendingIntent p=PendingIntent.getBroadcast(c,0,refresh,PendingIntent.FLAG_NO_CREATE|PendingIntent.FLAG_IMMUTABLE);if(p!=null){am.cancel(p);p.cancel();}return;}
        try{am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,rewrite,PendingIntent.getBroadcast(c,0,refresh,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE));}
        catch(Exception e){Log.w("ReminderScheduler","Couldn't set the locked-boot refresh",e);}
    }
    /** After a reminder rang: one alarm fewer, so a waiting one may get it, and the snapshot is kept fresh (both by [armAll]). */
    static void afterRing(Context c){request(c);}

    /**
     * Reminder [id] is gone or changed: its alarm, snooze, delivery records, notification and any ring go, as Planner's cancel().
     * Its new version (if any) is set again by the rest of [armAll].
     */
    static void cancel(Context c,long id){String key=Reminders.Found.key(id);
        cancelCode(c,id);new AlarmLedger(c).remove(key);new Deliveries(c).forget(id);new Snoozes(c).remove(id);
        c.getSharedPreferences("alarm_signatures",0).edit().remove(key).apply();
        try{c.getSystemService(android.app.NotificationManager.class).cancel((int)id);}catch(Exception e){}
        AlarmService.stopIfRinging(c,id);}
    /** Its request code: the reminder id (from 1); Android's alarms and the notification both use it. */
    static PendingIntent pending(Context c,long id,Intent intent,int flags){return PendingIntent.getBroadcast(c,(int)id,intent,flags|PendingIntent.FLAG_IMMUTABLE);}
    private static void cancelCode(Context c,long id){
        // Extras don't take part in matching, so a bare intent finds the alarm that was set.
        PendingIntent p=pending(c,id,new Intent(c,ReminderReceiver.class),PendingIntent.FLAG_NO_CREATE);if(p==null)return;
        c.getSystemService(AlarmManager.class).cancel(p);p.cancel();}
    /** A late alarm already shown as missed (MissedReminders); its notification stays. */
    static void disarm(Context c,String key){cancelCode(c,Reminders.id(key));}

    /** The test alarm's Snooze (AlarmService): the same alarm again in [minutes], as Planner's snooze(extras). */
    static void snoozeTest(Context c,android.os.Bundle extras,long minutes){long at=System.currentTimeMillis()+minutes*60_000L;
        Intent intent=new Intent(c,ReminderReceiver.class).putExtras(extras).putExtra(EXTRA_TRIGGER,at);
        setReminderAlarm(c.getSystemService(AlarmManager.class),at,pending(c,0,intent,PendingIntent.FLAG_UPDATE_CURRENT));}

    /** Exact when Android allows it; otherwise a little late (see [canScheduleExact]). */
    static void setReminderAlarm(AlarmManager am,long triggerAt,PendingIntent pending){
        try{if(Build.VERSION.SDK_INT<31||am.canScheduleExactAlarms())am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,triggerAt,pending);
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,triggerAt,pending);}
        catch(SecurityException e){ // permission was revoked between the check and the call
            try{am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,triggerAt,pending);}catch(IllegalStateException x){tooManyAlarms(x);}}
        catch(IllegalStateException e){tooManyAlarms(e);}}
    // Over Android's 500 alarms (AlarmWindow keeps MyBudget well under it). It stays in the ledger, so the next run sets it,
    // or the app shows it as missed if that comes too late.
    private static void tooManyAlarms(IllegalStateException e){Log.w("ReminderScheduler","Android refused another alarm",e);}
}
