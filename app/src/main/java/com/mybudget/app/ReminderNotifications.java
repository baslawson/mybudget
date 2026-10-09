package com.mybudget.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import java.util.*;

/** Reminder notifications, as Planner's (Notifications.kt and MissedReminderNotifier.kt). */
final class ReminderNotifications {
    private ReminderNotifications(){}
    static final String REMINDER_CHANNEL="reminders";
    // Ringing alarms play their own looping sound from AlarmService, so the channel itself is silent.
    static final String ALARM_CHANNEL="alarms";
    // Reminder ids start at 1, so 0 can never collide with a real reminder's notification.
    static final int TEST_ID=0;
    static void createChannels(Context c){NotificationManager m=c.getSystemService(NotificationManager.class);
        NotificationChannel r=new NotificationChannel(REMINDER_CHANNEL,"Reminders",NotificationManager.IMPORTANCE_HIGH);r.setDescription("Reminders for your transactions and upcoming ones");m.createNotificationChannel(r);
        NotificationChannel a=new NotificationChannel(ALARM_CHANNEL,"Ringing alarms",NotificationManager.IMPORTANCE_HIGH);a.setDescription("Reminders while they ring, for a few seconds or until you stop them");
        a.setSound(null,null);a.enableVibration(false);m.createNotificationChannel(a);}

    /** What tapping a reminder opens: MyBudget's Transactions, in its open window (MainActivity.openFrom). */
    static Intent openIntent(Context c){return new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP)
        .putExtra(MainActivity.OPEN,"transactions");}
    static PendingIntent open(Context c,int code){return PendingIntent.getActivity(c,code,openIntent(c),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);}

    /** What a reminder says, from the extras ReminderScheduler put in its intent. [details]: the transaction's note. [whenText]: its day alone, all the lock screen shows besides the payee. */
    static final class Content {
        final String title,text,subText,details,whenText;
        Content(String title,String text,String subText,String details,String whenText){this.title=title;this.text=text;this.subText=subText;this.details=details;this.whenText=whenText;}
        static Content of(Bundle e){if(e==null||e.getString(ReminderScheduler.EXTRA_TITLE)==null)return null;String text=e.getString(ReminderScheduler.EXTRA_TEXT,"");
            return new Content(e.getString(ReminderScheduler.EXTRA_TITLE),text,e.getString(ReminderScheduler.EXTRA_SUB,""),e.getString(ReminderScheduler.EXTRA_DETAILS,"").trim(),e.getString(ReminderScheduler.EXTRA_WHEN,text));}
    }
    /** False when the permission was refused, notifications are off for the app, or the user muted the channel. */
    static boolean notificationsEnabled(Context c){NotificationManager m=c.getSystemService(NotificationManager.class);if(!m.areNotificationsEnabled())return false;
        NotificationChannel ch=m.getNotificationChannel(REMINDER_CHANNEL);return ch==null||ch.getImportance()!=NotificationManager.IMPORTANCE_NONE;}
    /**
     * A ringing alarm is stopped only from its notification, so it rings only when that notification can show: otherwise it
     * would ring for ten minutes with no way to stop it. The reminder then falls back to a normal notification.
     */
    static boolean ringsAsAlarm(boolean appNotifications,Integer alarmChannelImportance){return appNotifications&&(alarmChannelImportance==null||alarmChannelImportance!=NotificationManager.IMPORTANCE_NONE);}
    static boolean ringingAlarmsEnabled(Context c){NotificationManager m=c.getSystemService(NotificationManager.class);NotificationChannel ch=m.getNotificationChannel(ALARM_CHANNEL);
        return ringsAsAlarm(m.areNotificationsEnabled(),ch==null?null:ch.getImportance());}
    /** As ReminderScheduler.canScheduleExact (it also runs before the first unlock). */
    static boolean exactAlarmsAllowed(Context c){try{return ReminderScheduler.canScheduleExact(c);}catch(Exception e){return true;}}
    /**
     * Why a reminder set to ring didn't ring, as each notification says it. Only with exact alarms refused (Android 12, or 14+ when
     * not allowed) is "Alarms & reminders" the cause; with them allowed (always from Android 13, by USE_EXACT_ALARM) it is
     * notifications or battery use, which app settings reach.
     */
    enum CouldNotRing {
        ALLOW_ALARMS("Android didn't let this ring as an alarm. Allow Alarms & reminders for MyBudget so it can.","Couldn’t ring. Allow Alarms & reminders for MyBudget in app settings."),
        CHECK_SETTINGS("Android didn't let this ring as an alarm. Check that MyBudget's notifications are on and its battery use isn't restricted.","Couldn’t ring. Check MyBudget’s notification and battery settings.");
        final String full,shortText;
        CouldNotRing(String full,String shortText){this.full=full;this.shortText=shortText;}
        static CouldNotRing of(boolean exactAllowed){return exactAllowed?CHECK_SETTINGS:ALLOW_ALARMS;}
        static CouldNotRing now(Context c){return of(exactAlarmsAllowed(c));}
    }
    /** A reminder's line: [text], then the first line of [details] (the note) when it has one. */
    static String withDetails(String text,String details){for(String line:details.split("\n"))if(!line.trim().isEmpty())return text.isEmpty()?line.trim():text+" · "+line.trim();return text;}
    /** What the lock screen shows of a reminder: [title] and [text] (its day), no amount or note. */
    static Notification publicVersion(Context c,String channel,String title,String text){Notification.Builder b=new Notification.Builder(c,channel).setSmallIcon(R.drawable.ic_notification)
        .setContentTitle(title).setCategory(Notification.CATEGORY_REMINDER);if(!text.isEmpty())b.setContentText(text);return b.build();}

    /**
     * A button whose receiver reads the budget (Enter it now, Skip). Before the first unlock that receiver can't run (it isn't
     * directBootAware, having nothing to work with): Android 12 and later asks for the unlock first, then sends it; older
     * Android would drop the tap, so there the button is left out. Unlocked, a plain button as always.
     */
    static void addDataAction(Notification.Builder b,Context c,String title,PendingIntent intent){
        if(DirectBoot.isUnlocked(c))b.addAction(new Notification.Action.Builder(null,title,intent).build());
        else if(Build.VERSION.SDK_INT>=31)b.addAction(new Notification.Action.Builder(null,title,intent).setAuthenticationRequired(true).build());}
    /** A reminder's buttons from its alarm's extras: Enter it now and Skip (an upcoming one on or after its day), Snooze. */
    static void addReminderActions(Notification.Builder b,Context c,Bundle e,boolean ringing){
        long id=e.getLong(ReminderScheduler.EXTRA_REMINDER_ID);if(id<=0)return;String enter=e.getString(ReminderScheduler.EXTRA_ENTER_TOKEN),snooze=e.getString(ReminderScheduler.EXTRA_SNOOZE_TOKEN);
        if(enter!=null){addDataAction(b,c,"Enter it now",ReminderActionReceiver.action(c,ReminderActionReceiver.ENTER,id,enter));
            // While it rings there is room for three buttons: Enter it now, Snooze and Stop. Skip only on the notification, and only
            // for a repeating one (skipping a one-off deletes it: that is done in MyBudget).
            if(!ringing&&e.getBoolean(ReminderScheduler.EXTRA_REPEATS,false))addDataAction(b,c,"Skip",ReminderActionReceiver.action(c,ReminderActionReceiver.SKIP,id,enter));}
        if(snooze!=null)b.addAction(new Notification.Action.Builder(null,"Snooze",SnoozeActivity.action(c,id,snooze)).build());}

    /**
     * Posts a reminder's notification from its alarm's [extras]; false when it could not be shown (notifications off).
     * [couldNotRing]: set to ring, and Android didn't let it. [quiet]: shown again without popping up. [silent]: no sound at all
     * (it has just rung). [missed]: it rang unanswered ("Missed alarm: …").
     */
    static boolean post(Context c,Bundle extras,boolean couldNotRing,boolean quiet,boolean silent,boolean missed){
        Content content=Content.of(extras);if(content==null||!notificationsEnabled(c))return false;
        long id=extras.getLong(ReminderScheduler.EXTRA_REMINDER_ID);String title=(missed?"Missed alarm: ":"")+content.title;
        Notification.Builder b=new Notification.Builder(c,REMINDER_CHANNEL).setSmallIcon(R.drawable.ic_notification).setContentTitle(title)
            .setContentText(withDetails(content.text,content.details)).setSubText(content.subText).setCategory(Notification.CATEGORY_REMINDER)
            .setAutoCancel(true).setContentIntent(open(c,(int)id)).setOnlyAlertOnce(quiet).setVisibility(Notification.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion(c,REMINDER_CHANNEL,title,content.whenText));
        if(!content.details.isEmpty())b.setStyle(new Notification.BigTextStyle().bigText(content.text+"\n"+content.details));
        if(silent)silent(b);
        long trigger=extras.getLong(ReminderScheduler.EXTRA_TRIGGER,0);if(trigger>0)b.setWhen(trigger).setShowWhen(true);
        addReminderActions(b,c,extras,false);
        if(couldNotRing){CouldNotRing why=CouldNotRing.now(c);List<String> lines=new ArrayList<>();for(String s:new String[]{content.text,content.details,why.full})if(!s.isEmpty())lines.add(s);
            b.setStyle(new Notification.BigTextStyle().bigText(String.join("\n",lines)));
            // "Allow alarms" only when that is what's missing; otherwise app settings (notifications, battery).
            boolean allow=why==CouldNotRing.ALLOW_ALARMS&&Build.VERSION.SDK_INT>=31;
            Intent settings=new Intent(allow?Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM:Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.fromParts("package",c.getPackageName(),null));
            b.addAction(new Notification.Action.Builder(null,allow?"Allow alarms":"App settings",PendingIntent.getActivity(c,0,settings,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE)).build());}
        Notification n=b.build();
        // Its sound repeats until the notification is opened or dismissed: the nearest to ringing without the alarm.
        if(couldNotRing)n.flags|=Notification.FLAG_INSISTENT;
        try{c.getSystemService(NotificationManager.class).notify((int)id,n);return true;}catch(SecurityException e){return false;}
    }
    /**
     * A reminder shown before the first unlock, shown again with its buttons (Enter it now), quietly. Its title is kept ("Missed
     * alarm: …" after a ringing one gave up), and the "couldn't ring" note if it had one.
     */
    static void showAgain(Context c,Budget b,Reminders.Found f,long trigger){
        Bundle extras=LockedAlarm.of(c,b,f,trigger,ReminderScheduler.hideAmounts(c)).intent(c,f.entry!=null?f.entry.memo:f.upcoming.memo).getExtras();
        Notification shown=null;try{for(android.service.notification.StatusBarNotification n:c.getSystemService(NotificationManager.class).getActiveNotifications())
            if(n.getTag()==null&&n.getId()==(int)f.reminder.id)shown=n.getNotification();}catch(Exception e){}
        boolean missed=shown!=null&&String.valueOf(shown.extras.getCharSequence(Notification.EXTRA_TITLE)).startsWith("Missed alarm: ");
        boolean couldNotRing=shown!=null&&(shown.flags&Notification.FLAG_INSISTENT)!=0;
        post(c,extras,couldNotRing,true,false,missed);}

    /** No sound at all (it has just rung), as NotificationCompat.setSilent does it: in a group of its own whose summary alone would alert. */
    static void silent(Notification.Builder b){b.setGroup("silent").setGroupAlertBehavior(Notification.GROUP_ALERT_SUMMARY);}
    private static final String MISSED_GROUP="mybudget.missed";
    /**
     * Normal (never ringing) notifications, in the reminder's own slot so a later real one replaces it. Several go in one group
     * whose summary alone alerts, and at most [MissedReminders.MAX_SHOWN] are shown; the summary counts the rest. After a reboot
     * ([afterBoot]), the reminders due while the phone was off; when the app opens, the ones whose alarm Android dropped
     * without ringing; [waiting]: ones that waited for a free alarm (AlarmWindow) and are due now.
     */
    static void postMissed(Context c,Budget b,List<MissedReminders.Missed> missed,long now,boolean afterBoot,boolean waiting){
        if(missed.isEmpty()||!notificationsEnabled(c))return;boolean grouped=missed.size()>1;List<MissedReminders.Missed> shown=missed.subList(0,Math.min(missed.size(),MissedReminders.MAX_SHOWN));
        NotificationManager m=c.getSystemService(NotificationManager.class);boolean hide=ReminderScheduler.hideAmounts(c);
        try{for(MissedReminders.Missed x:shown){Bundle extras=LockedAlarm.of(c,b,x.found,x.due,hide).intent(c,x.found.entry!=null?x.found.entry.memo:x.found.upcoming.memo).getExtras();
                Content content=Content.of(extras);int id=(int)x.found.reminder.id;
                java.time.ZonedDateTime at=java.time.Instant.ofEpochMilli(x.due).atZone(java.time.ZoneId.systemDefault());
                String day=at.toLocalDate().equals(java.time.Instant.ofEpochMilli(now).atZone(java.time.ZoneId.systemDefault()).toLocalDate())?"":Reminders.day(at.toLocalDate())+", ";
                String due=(waiting?"Due ":"Missed · due ")+day+Reminders.time(at.toLocalTime(),c);
                Notification.Builder n=new Notification.Builder(c,REMINDER_CHANNEL).setSmallIcon(R.drawable.ic_notification).setContentTitle(content.title).setContentText(due)
                    .setSubText(content.subText).setWhen(x.due).setShowWhen(true).setCategory(Notification.CATEGORY_REMINDER).setContentIntent(open(c,id)).setAutoCancel(true)
                    .setStyle(new Notification.BigTextStyle().bigText(due+"\n"+withDetails(content.text,content.details))).setVisibility(Notification.VISIBILITY_PRIVATE)
                    .setPublicVersion(publicVersion(c,REMINDER_CHANNEL,content.title,content.whenText));
                if(grouped)n.setGroup(MISSED_GROUP).setGroupAlertBehavior(Notification.GROUP_ALERT_SUMMARY);
                addReminderActions(n,c,extras,false);m.notify(id,n.build());}
            if(grouped){int more=missed.size()-shown.size();Notification.InboxStyle style=new Notification.InboxStyle();for(MissedReminders.Missed x:shown)style.addLine(x.found.entry!=null?x.found.entry.payee:x.found.upcoming.payee);
                if(more>0)style.setSummaryText("+"+more+" more in MyBudget");
                m.notify("missed-reminders",0,new Notification.Builder(c,REMINDER_CHANNEL).setSmallIcon(R.drawable.ic_notification)
                    .setContentTitle(missed.size()+" "+(waiting?"reminders due":"missed reminders"))
                    .setContentText((waiting?"Open MyBudget to see them":afterBoot?"While your phone was off":"Android stopped their alarms")+(more>0?" · "+more+" more in MyBudget":""))
                    .setStyle(style).setCategory(Notification.CATEGORY_REMINDER).setGroup(MISSED_GROUP).setGroupSummary(true).setGroupAlertBehavior(Notification.GROUP_ALERT_SUMMARY)
                    .setContentIntent(open(c,0)).setAutoCancel(true).build());}}
        catch(SecurityException e){/* permission can be revoked after notificationsEnabled was checked */}
    }
    /** Settings › Reminders › Test notification. */
    static boolean sendTest(Context c){Bundle e=new Bundle();e.putLong(ReminderScheduler.EXTRA_REMINDER_ID,TEST_ID);e.putString(ReminderScheduler.EXTRA_TITLE,"Test notification");
        e.putString(ReminderScheduler.EXTRA_TEXT,"Your reminders will look like this.");e.putString(ReminderScheduler.EXTRA_SUB,"Just now");return post(c,e,false,false,false,false);}
    static void openNotificationSettings(Context c){c.startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,c.getPackageName()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));}
    static void openAppSettings(Context c){c.startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.fromParts("package",c.getPackageName(),null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));}
}
