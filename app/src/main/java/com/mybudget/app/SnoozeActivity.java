package com.mybudget.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;
import java.time.ZonedDateTime;

/**
 * A reminder's Snooze, as Planner's: an activity the notification opens directly (also on Android 12+), in a task of its own
 * (see the manifest), so MyBudget's own screen never comes up under it. 10 minutes, 1 hour or tomorrow at 9 am.
 */
public class SnoozeActivity extends Activity {
    static final String[] CHOICES={"10 minutes","1 hour","Tomorrow at 9 am"};
    static long until(int choice,ZonedDateTime now){return (choice==0?now.plusMinutes(10):choice==1?now.plusHours(1):now.toLocalDate().plusDays(1).atTime(9,0).atZone(now.getZone())).toInstant().toEpochMilli();}
    @Override protected void onCreate(Bundle state){
        String mode=getSharedPreferences("appearance",0).getString("theme","Dark");
        boolean dark=mode.equals("Dark")||(mode.equals("Auto")&&(getResources().getConfiguration().uiMode&Configuration.UI_MODE_NIGHT_MASK)==Configuration.UI_MODE_NIGHT_YES);
        setTheme(dark?R.style.AppTheme_Overlay:R.style.AppTheme_Light_Overlay);
        super.onCreate(state);
        long id=getIntent().getLongExtra(ReminderScheduler.EXTRA_REMINDER_ID,0);String token=getIntent().getStringExtra(ReminderScheduler.EXTRA_SNOOZE_TOKEN);
        if(id<=0||token==null){finish();return;}
        AlertDialog d=new AlertDialog.Builder(this).setTitle("Snooze reminder").setItems(CHOICES,(x,which)->{
                String done;try{done=snooze(this,id,token,until(which,ZonedDateTime.now()))?"Snoozed: "+CHOICES[which]:"This reminder is no longer active";}
                catch(Exception e){done="Couldn't snooze. Please try again.";}
                Toast.makeText(this,done,Toast.LENGTH_SHORT).show();finish();})
            .setNegativeButton("Cancel",(x,w)->finish()).create();
        d.setOnCancelListener(x->finish());d.show();
    }
    /**
     * Snoozes reminder [id] until [until], as Planner's snoozeReminder: only while it is still the reminder [token] was made for
     * (not deleted, moved or entered meanwhile). Its notification and any ring go; its alarm is set for the new time.
     */
    static boolean snooze(Context c,long id,String token,long until)throws Exception{
        if(until<=System.currentTimeMillis())throw new IllegalArgumentException("A snooze is for later.");
        Budget b=ReminderScheduler.readBudget(c);Reminders.Found f=b==null?null:Reminders.find(b,id);if(f==null||!Reminders.snoozeToken(f).equals(token))return false;
        new Snoozes(c).put(id,until,token);new Deliveries(c).forget(id);
        try{c.getSystemService(android.app.NotificationManager.class).cancel((int)id);}catch(Exception e){}
        AlarmService.stopIfRinging(c,id);ReminderScheduler.request(c);return true;}
    static PendingIntent action(Context c,long id,String token){return PendingIntent.getActivity(c,(int)id,new Intent(c,SnoozeActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        .setData(Uri.parse("mybudget://reminder-snooze/"+id+"/"+token)).putExtra(ReminderScheduler.EXTRA_REMINDER_ID,id).putExtra(ReminderScheduler.EXTRA_SNOOZE_TOKEN,token),
        PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);}
}
