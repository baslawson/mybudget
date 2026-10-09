package com.mybudget.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/** A reminder's alarm went off: it rings (AlarmService) or shows its notification, as Planner's ReminderReceiver. */
public class ReminderReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context,Intent intent){
        long id=intent.getLongExtra(ReminderScheduler.EXTRA_REMINDER_ID,0L);
        // The test alarm's snooze uses id zero and has no reminder in the budget.
        if(id==0L){show(context,intent);return;}
        String key=Reminders.Found.key(id);long trigger=intent.getLongExtra(ReminderScheduler.EXTRA_TRIGGER,0L);
        // Before the first unlock after a reboot the budget can't be read. This alarm was set from the locked snapshot
        // (BootReceiver), and its intent carries what the notification shows; once unlocked it's noted as rung.
        if(!DirectBoot.isUnlocked(context)){DirectBoot.fired(context,key,trigger);show(context,intent);return;}
        new AlarmLedger(context).fired(key,System.currentTimeMillis());
        PendingResult pending=goAsync();Context app=context.getApplicationContext();
        ReminderScheduler.WORK.execute(()->{
            try{deliver(app,id,trigger);}catch(Exception e){Log.w("ReminderReceiver","Couldn't deliver reminder",e);}
            finally{pending.finish();
                // One alarm fewer: a reminder waiting for one gets it (AlarmWindow); and the locked-reboot snapshot is kept fresh.
                ReminderScheduler.afterRing(app);}});
    }
    /** Checked against the budget as it is now: still there, at this time, not rung already; then shown, and recorded as rung. */
    static void deliver(Context c,long id,long trigger)throws Exception{
        Budget b=ReminderScheduler.readBudget(c);if(b==null)return;Reminders.Found f=Reminders.find(b,id);if(f==null)return;
        Snoozes snoozes=new Snoozes(c);Deliveries deliveries=new Deliveries(c);boolean snoozed=snoozes.until(f)>0;long expected=snoozes.trigger(f);
        if(snoozed?deliveries.delivered(f.key(),expected):deliveries.deliveredOnTime(f.reminder.id,Reminders.deliveryKey(f)))return;
        if(!Reminders.accepts(trigger,expected,snoozed,System.currentTimeMillis()))return;
        show(c,LockedAlarm.of(c,b,f,trigger,ReminderScheduler.hideAmounts(c)).intent(c,f.entry!=null?f.entry.memo:f.upcoming.memo));
        if(snoozed)deliveries.record(f.key(),expected);else deliveries.recordOnTime(f.reminder.id,Reminders.deliveryKey(f));
    }
    static void show(Context c,Intent intent){
        long id=intent.getLongExtra(ReminderScheduler.EXTRA_REMINDER_ID,0L);boolean couldNotRing=false;
        // Its sound (ReminderSound): rings for a few seconds or until stopped, through AlarmService; or the notification only.
        Integer ringFor=ReminderSound.ringSecondsNow(c,intent.getBooleanExtra(ReminderScheduler.EXTRA_RING,false),intent.getIntExtra(ReminderScheduler.EXTRA_RING_SECONDS,0));
        if(ringFor!=null&&ReminderNotifications.ringingAlarmsEnabled(c)){
            // Reserved, so a start Android delivers again after ending the process is refused once the reminder was deleted,
            // entered, skipped, snoozed or moved (ReminderScheduler.cancel). On device-protected storage, so this works before the
            // first unlock too. One that can't be reserved rings as before.
            String token=null;if(id!=0L)try{token=OwnedAlarmStarts.reserve(c,OwnedAlarmStarts.EVENT,Long.toString(id));}catch(Exception e){Log.w("ReminderReceiver","Couldn't reserve the ringing start",e);}
            try{Intent start=new Intent(c,AlarmService.class).putExtras(intent).putExtra(AlarmService.EXTRA_RING_FOR,ringFor);
                // Not an earlier ring's (a snooze set from the ringing alarm carries its extras).
                if(token!=null)start.putExtra(OwnedAlarmStarts.EXTRA_EVENT_START,token);else start.removeExtra(OwnedAlarmStarts.EXTRA_EVENT_START);
                c.startForegroundService(start);return;}
            catch(Exception e){if(token!=null)try{OwnedAlarmStarts.cancel(c,OwnedAlarmStarts.EVENT,Long.toString(id),token);}catch(Exception x){}
                // Android 12+ lets a background app start the ringing service from an exact alarm only. Without "Alarms & reminders"
                // this alarm came inexact, and nothing else allowed then can ring. So the notification itself keeps sounding until
                // it is seen, and says why it didn't ring. With exact alarms allowed the cause is elsewhere, battery use say.
                Log.w("ReminderReceiver","Couldn't start the ringing alarm",e);couldNotRing=true;}}
        ReminderNotifications.post(c,intent.getExtras(),couldNotRing,false,false,false);
    }
}
