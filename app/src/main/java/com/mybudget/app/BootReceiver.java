package com.mybudget.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import java.util.*;

/**
 * Alarms are cleared by a reboot and by app updates, so every future reminder is put back, and after a reboot the missed ones
 * shown, as Planner's BootReceiver. Planner hands the work to WorkManager when its database is busy; MyBudget's budget is one
 * saved text that is never held, so it is done here, within the seconds Android gives (goAsync).
 */
public class BootReceiver extends BroadcastReceiver {
    // MyBudget's own: the locked-boot snapshot is due to be written again (ReminderScheduler).
    static final String ACTION_REFRESH_LOCKED="com.mybudget.app.REFRESH_LOCKED_ALARMS";
    // "Alarms & reminders" turned back on (Android 12/12L; from 13 USE_EXACT_ALARM keeps it on). The alarms set inexact meanwhile are set again exact.
    static final String ACTION_EXACT_ALARMS_ALLOWED="android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED";
    private static final Set<String> ACTIONS=new HashSet<>(Arrays.asList(Intent.ACTION_LOCKED_BOOT_COMPLETED,Intent.ACTION_BOOT_COMPLETED,Intent.ACTION_MY_PACKAGE_REPLACED,
        Intent.ACTION_TIMEZONE_CHANGED,Intent.ACTION_TIME_CHANGED,ACTION_EXACT_ALARMS_ALLOWED,ACTION_REFRESH_LOCKED));
    @Override public void onReceive(Context context,Intent intent){String action=intent.getAction();if(!ACTIONS.contains(action))return;
        // directBootAware, so this also runs before the first unlock, when the budget can't be read. Then the nearest alarms are
        // set from the locked snapshot; BOOT_COMPLETED, which Android sends once the phone is unlocked, sets them all from the
        // budget (the same request codes: each replaces its snapshot one). A time change while locked leaves them: they are set
        // for fixed instants, and the unlock puts the times right.
        if(!DirectBoot.isUnlocked(context)){if(Intent.ACTION_LOCKED_BOOT_COMPLETED.equals(action))DirectBoot.armFromSnapshot(context);return;}
        // Unlocked already (no screen lock, say): BOOT_COMPLETED follows and does it all.
        if(Intent.ACTION_LOCKED_BOOT_COMPLETED.equals(action))return;
        Context app=context.getApplicationContext();PendingResult pending=goAsync();
        ReminderScheduler.WORK.execute(()->{
            try{Budget b=ReminderScheduler.readBudget(app);
                // Only a reboot leaves due alarms unseen for long: a time change fires past ones late, an update takes seconds.
                if(Intent.ACTION_BOOT_COMPLETED.equals(action))showMissed(app,b,true);
                ReminderScheduler.armAll(app,b,!ACTION_REFRESH_LOCKED.equals(action));}
            // A failure here (storage full, say) must not crash MyBudget in the background; the next boot, update, ring or open tries again.
            catch(Exception e){Log.w("BootReceiver","Couldn't set the reminders again ("+action+")",e);}
            finally{pending.finish();}});
    }
    /**
     * After a reboot ([afterBoot]), the reminders due while the phone was off; when the app opens, the ones whose alarm Android
     * dropped without ringing (a force stop, or the exact-alarm permission turned off, clears an app's alarms). The ones that
     * rang before the first unlock aren't missed. Runs on ReminderScheduler.WORK.
     */
    static void showMissed(Context c,Budget b,boolean afterBoot){
        try{DirectBoot.replayFired(c,b);}catch(Exception e){Log.w("MissedReminders","Couldn't record the reminders rung while locked",e);}
        if(b==null)return;long now=System.currentTimeMillis();Snoozes snoozes=new Snoozes(c);Deliveries deliveries=new Deliveries(c);
        try{// Ones that waited for a free alarm (AlarmWindow) and are due now.
            Map<String,Long> waiting=ReminderScheduler.deferredReminders(c),due=new HashMap<>();for(Map.Entry<String,Long> e:waiting.entrySet())if(e.getValue()<=now)due.put(e.getKey(),e.getValue());
            if(!due.isEmpty()){List<MissedReminders.Missed> list=MissedReminders.select(MissedReminders.due(due,now,0),b,snoozes,deliveries);
                // Remove only the captured due version: a later edit to the same reminder must stay waiting.
                ReminderScheduler.forgetDeferred(c,due);record(c,list);ReminderNotifications.postMissed(c,b,list,now,afterBoot,true);}
            AlarmLedger ledger=new AlarmLedger(c);long grace=afterBoot?0:MissedReminders.openGraceMs(ReminderScheduler.canScheduleExact(c));
            Map<String,Long> pending=ledger.all();List<MissedReminders.Missed> missed=MissedReminders.select(MissedReminders.due(pending,now,grace),b,snoozes,deliveries);
            // Forgotten before they are shown, so a crash can lose a missed note but never repeat it at the next boot. A late alarm
            // that is still set is disarmed, so it can't show again.
            Map<String,Long> remaining=MissedReminders.remaining(pending,now,grace);for(String key:pending.keySet())if(!remaining.containsKey(key))ReminderScheduler.disarm(c,key);
            ledger.keepOnly(remaining);record(c,missed);ReminderNotifications.postMissed(c,b,missed,now,afterBoot,false);}
        catch(Exception e){Log.w("MissedReminders","Couldn't show missed reminders",e);}
    }
    // Shown as missed: recorded as delivered, so they aren't set or shown again.
    private static void record(Context c,List<MissedReminders.Missed> list){Snoozes snoozes=new Snoozes(c);Deliveries d=new Deliveries(c);
        for(MissedReminders.Missed m:list){if(snoozes.until(m.found)>0)d.record(m.found.key(),m.due);else d.recordOnTime(m.found.reminder.id,Reminders.deliveryKey(m.found));}}
    /**
     * When MyBudget opens: after a few seconds (an alarm Android releases as the phone wakes up reaches ReminderReceiver first),
     * the missed reminders, then every alarm set again, as Planner does on each open.
     */
    static void onAppOpen(Context c){Context app=c.getApplicationContext();
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(()->ReminderScheduler.WORK.execute(()->{
            try{Budget b=ReminderScheduler.readBudget(app);showMissed(app,b,false);ReminderScheduler.armAll(app,b,true);}
            catch(Exception e){Log.w("BootReceiver","Couldn't check the reminders",e);}}),MissedReminders.SETTLE_MS);}
}
