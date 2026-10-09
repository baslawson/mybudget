package com.mybudget.app;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ServiceInfo;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.media.ToneGenerator;
import android.net.Uri;
import android.os.*;
import android.util.AtomicFile;
import android.util.Log;
import android.widget.Toast;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Rings like an alarm clock, as Planner's AlarmService: looping alarm sound and vibration until the user taps Stop or Snooze.
 * It gives up after [MAX_RING_MINUTES] and leaves a "missed" notification, so a forgotten phone stays quiet. A reminder set to
 * ring for a few seconds ([EXTRA_RING_FOR]) rings the same way, then stops by itself and leaves its normal notification (not a
 * missed one), as its Stop button does.
 */
public class AlarmService extends Service {
    private MediaPlayer player;private PowerManager.WakeLock wakeLock;private Bundle ringing;
    // The start that brought the ringing alarm. Android keeps every start to deliver again after it ends the process, until that
    // start is stopped by its id: so one that gave way or gave up is never rung again, and stopping it leaves the starts after it
    // (another alarm) to run.
    private int ringingStart,lastStart,ignoredStart;
    private final Handler handler=new Handler(Looper.getMainLooper());
    // Its time is up: a timed ring ends as its normal notification, one until stopped as missed.
    private final Runnable giveUp=()->{if(timed(ringing))onRangOut();else onGiveUp();};
    @Override public IBinder onBind(Intent intent){return null;}
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        lastStart=Math.max(lastStart,startId);String action=intent==null?null:intent.getAction();
        if(ACTION_STOP.equals(action)||ACTION_SNOOZE.equals(action)){
            if(!RingToken.matches(stopToken,intent.getStringExtra(EXTRA_STOP_ALARM))){
                // A stale button must neither stop this ring nor change its process-restart policy.
                if(ringing==null){stopSelf(startId);return START_NOT_STICKY;}
                ignoredStart=Math.max(ignoredStart,startId);return START_REDELIVER_INTENT;}}
        if(ACTION_STOP.equals(action)){
            // A timed ring's Stop: it stops the sound, the reminder stays to act on (only one that still stands, checked and
            // posted under the lock that cancelling takes, and without sounding again).
            if(intent.getBooleanExtra(EXTRA_KEEP_REMINDER,false)&&ringing!=null){Bundle extras=ringing;
                synchronized(OwnedAlarmStarts.class){if(OwnedAlarmStarts.isCurrent(this,extras))showAsNotification(extras,false,true);}}
            stopRinging(null);
        }else if(ACTION_SNOOZE.equals(action)){
            if(ringing!=null){ReminderScheduler.snoozeTest(this,ringing,SNOOZE_MINUTES);Toast.makeText(this,"Snoozed for "+SNOOZE_MINUTES+" minutes",Toast.LENGTH_SHORT).show();}
            stopRinging(null);
        }else{
            Bundle extras=intent==null?null:intent.getExtras();boolean redelivered=(flags&START_FLAG_REDELIVERY)!=0;
            boolean accepted=OwnedAlarmStarts.start(this,extras,()->startRinging(extras,startId,redelivered));
            // Turned quiet on its way: its reminder is shown as a normal notification instead (with its buttons); delivered again
            // after a restart, it has had its sound.
            if(!accepted)synchronized(OwnedAlarmStarts.class){if(OwnedAlarmStarts.takeQuiet(this,extras)&&extras!=null)showAsNotification(extras,false,redelivered);}
            if(!accepted&&ringing!=null)ignoredStart=Math.max(ignoredStart,startId);
            if(!accepted&&ringing==null){
                // Fulfil the foreground-start deadline, then end this cancelled start without playing anything. Refused after a
                // restart from the background (Android 12+), it has no deadline to meet.
                Notification.Builder cancelled=new Notification.Builder(this,ReminderNotifications.ALARM_CHANNEL).setSmallIcon(R.drawable.ic_notification)
                    .setContentTitle("Reminder cancelled").setCategory(Notification.CATEGORY_STATUS);ReminderNotifications.silent(cancelled);
                if(goForeground(cancelled.build()))stopForeground(STOP_FOREGROUND_REMOVE);
                stopSelf(startId);}
            // Android ending the process (low memory, seen right after an unlock) must not end the alarm without anyone stopping
            // it: the start is delivered again and it rings on (Stop and Snooze end it for good).
            return START_REDELIVER_INTENT;
        }
        return START_NOT_STICKY;
    }
    private void startRinging(Bundle extras,int startId,boolean redelivered){
        // A timed ring never takes over one ringing until stopped (a wake-up alarm): it gives way, as its normal notification with
        // its sound, and the alarm rings on. Its start is set aside, done with when the alarm ends.
        if(extras!=null&&timed(extras)&&ringing!=null&&!timed(ringing)){showAsNotification(extras,false,redelivered);OwnedAlarmStarts.finish(this,extras);
            ignoredStart=Math.max(ignoredStart,startId);return;}
        // A second alarm can arrive while one is ringing; keep the first one as a normal notification, quietly (it has rung).
        Bundle previous=ringing;int previousStart=ringingStart;
        if(previous!=null&&extras!=null&&OwnedAlarmStarts.isCurrent(this,previous))showAsNotification(previous,false,true);
        if(!Arrays.equals(OwnedAlarmStarts.owner(previous),OwnedAlarmStarts.owner(extras)))OwnedAlarmStarts.finish(this,previous);
        ringing=extras;ringingStart=startId;
        currentReminderId=extras==null?null:extras.getLong(ReminderScheduler.EXTRA_REMINDER_ID);
        ringingSince=SystemClock.elapsedRealtime();stopToken=RingToken.create();
        // Read before the notification is built (an unlock is for good), so an unlock landing in between still gets its rebuild.
        boolean builtLocked=!DirectBoot.isUnlocked(this);
        // After a restart it rings for what is left of its time from the reminder's own time (at least a minute), and an alarm long
        // past that is left as missed. A timed one rings only what is left of its seconds; with none left it is its normal
        // notification. Worked out first, so one with nothing left goes into the foreground silently.
        Long ringFor=extras==null?null:AlarmRestart.ringFor(redelivered,extras.getLong(ReminderScheduler.EXTRA_TRIGGER,0L),System.currentTimeMillis(),extras.getInt(EXTRA_RING_FOR,0));
        // Must be called promptly after startForegroundService, even if there is nothing to ring for. Android may refuse it after
        // it restarted the service in the background (Android 12+): rather than crash, the reminder is left as its notification.
        if(!goForeground(notification(extras,false,extras!=null&&ringFor==null))){
            if(extras!=null)showAsNotification(extras,redelivered&&!timed(extras),redelivered&&timed(extras));stopRinging(startId);return;}
        // The one that gave way is a notification now: its start is done with, not to be delivered again.
        if(previous!=null&&extras!=null)stopSelfResult(previousStart);
        if(extras==null){stopRinging(null);return;}
        // Started before the first unlock, Enter it now may be left out (addDataAction, Android 11 and lower). Once unlocked, the
        // notification is built again with it.
        if(builtLocked)rebuildAtUnlock();
        if(ringFor==null){if(timed(extras))onRangOut();else onGiveUp();return;}
        startSound();startVibration();handler.removeCallbacks(giveUp);handler.postDelayed(giveUp,ringFor);
    }
    // False when Android won't let it run in the foreground now (ForegroundServiceStartNotAllowedException).
    private boolean goForeground(Notification shown){
        try{if(Build.VERSION.SDK_INT>=29)startForeground(NOTIFICATION_ID,shown,ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);else startForeground(NOTIFICATION_ID,shown);return true;}
        catch(IllegalStateException e){Log.w("AlarmService","Couldn't ring in the foreground",e);return false;}}
    // The ringing notification for [extras]. [quiet]: built again, without popping up a second time.
    private Notification notification(Bundle extras,boolean quiet,boolean silent){
        ReminderNotifications.Content content=ReminderNotifications.Content.of(extras);boolean timed=timed(extras);String title=content==null?"Alarm":content.title;
        Notification.Builder b=new Notification.Builder(this,ReminderNotifications.ALARM_CHANNEL).setSmallIcon(R.drawable.ic_notification).setContentTitle(title)
            // What it is for, as the reminder's own notification says it: the note's first line after the day, all of it when
            // opened. None of it on the lock screen (the public version below: payee and day).
            .setContentText(content==null?"":ReminderNotifications.withDetails(content.text,content.details))
            .setVisibility(Notification.VISIBILITY_PRIVATE).setPublicVersion(lockScreen(title,content==null?"":content.whenText,timed))
            .setSubText(content==null?"":content.subText).setCategory(Notification.CATEGORY_ALARM).setOngoing(true).setOnlyAlertOnce(quiet)
            .setContentIntent(openAndStop(stopToken))
            // Android 14+ lets the user swipe even this ongoing notification away (not on the lock screen). Swiping it is the only
            // thing left to do, so it counts as Stop: otherwise it would ring on with nothing to stop it.
            .setDeleteIntent(serviceAction(ACTION_STOP,false));
        if(content!=null&&!content.details.isEmpty())b.setStyle(new Notification.BigTextStyle().bigText(content.text+"\n"+content.details));
        if(silent)ReminderNotifications.silent(b);
        long id=extras==null?0:extras.getLong(ReminderScheduler.EXTRA_REMINDER_ID);
        if(id>0)ReminderNotifications.addReminderActions(b,this,extras,true);
        else b.addAction(new Notification.Action.Builder(null,"Snooze "+SNOOZE_MINUTES+" min",serviceAction(ACTION_SNOOZE,false)).build());
        // A timed ring's Stop leaves the reminder's normal notification, as its end does; a swipe (delete intent) or a tap (opens
        // MyBudget) is done with it, as with a normal reminder.
        b.addAction(new Notification.Action.Builder(null,"Stop",serviceAction(ACTION_STOP,timed)).build());
        return b.build();}
    // What the lock screen shows while it rings, where the phone hides sensitive content: [title] and [text] (its day), no
    // amount or note, and its Stop, so it can still be stopped without unlocking.
    private Notification lockScreen(String title,String text,boolean timed){Notification.Builder b=new Notification.Builder(this,ReminderNotifications.ALARM_CHANNEL)
        .setSmallIcon(R.drawable.ic_notification).setContentTitle(title).setCategory(Notification.CATEGORY_ALARM)
        .addAction(new Notification.Action.Builder(null,"Stop",serviceAction(ACTION_STOP,timed)).build());if(!text.isEmpty())b.setContentText(text);return b.build();}
    // Rings for a few seconds ([EXTRA_RING_FOR] above 0), not until stopped.
    private static boolean timed(Bundle extras){return extras!=null&&extras.getInt(EXTRA_RING_FOR,0)>0;}
    /** [extras]' reminder as its normal notification. [missed]: it rang unanswered ("Missed alarm: …"). [silent]: it has just rung. */
    private void showAsNotification(Bundle extras,boolean missed,boolean silent){ReminderNotifications.post(this,extras,false,false,silent,missed);}
    // A timed ring's seconds are up (or were, before Android restarted the service): the sound stops and the reminder stays as its
    // normal notification, quietly, with its buttons. Like a give-up, it ends its own start only.
    private void onRangOut(){int start=ringingStart;
        // An alarm stopped from elsewhere (stopIfRinging) just now is no longer this one's: nothing to post.
        synchronized(OwnedAlarmStarts.class){if(ringing!=null&&currentReminderId!=null)showAsNotification(ringing,false,true);}
        stopRinging(start);}
    private BroadcastReceiver unlockWatch;
    private void rebuildAtUnlock(){if(unlockWatch!=null)return;
        BroadcastReceiver watch=new BroadcastReceiver(){@Override public void onReceive(Context c,Intent i){stopUnlockWatch();Bundle extras=ringing;if(extras==null)return;
            try{getSystemService(NotificationManager.class).notify(NOTIFICATION_ID,notification(extras,true,false));}catch(Exception e){Log.w("AlarmService","Couldn't show the alarm's buttons again",e);}}};
        unlockWatch=watch;IntentFilter filter=new IntentFilter(Intent.ACTION_USER_UNLOCKED);
        if(Build.VERSION.SDK_INT>=33)registerReceiver(watch,filter,Context.RECEIVER_NOT_EXPORTED);else registerReceiver(watch,filter);
        // Unlocked in the meantime: the broadcast may have gone before the receiver was there.
        if(DirectBoot.isUnlocked(this))watch.onReceive(this,new Intent(Intent.ACTION_USER_UNLOCKED));}
    private void stopUnlockWatch(){if(unlockWatch!=null)try{unregisterReceiver(unlockWatch);}catch(Exception e){}unlockWatch=null;}
    private List<Uri> soundChoices=new ArrayList<>();
    // Each alarm's sounds are a run of their own: a late error from the previous alarm's player changes nothing.
    private int soundRun;
    private void startSound(){releasePlayer();
        // The first sound that plays, of these (AlarmSound); vibration goes on whatever happens to the sound.
        boolean unlocked=DirectBoot.isUnlocked(this);Uri chosen=null;if(unlocked)try{chosen=RingtoneManager.getActualDefaultRingtoneUri(this,RingtoneManager.TYPE_ALARM);}catch(Exception e){}
        soundChoices=AlarmSound.choices(unlocked,chosen,RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE),
            Uri.parse("android.resource://"+getPackageName()+"/"+R.raw.alarm_fallback));
        soundRun++;play(0);
        // Keeps the CPU awake while the screen is off.
        if(wakeLock!=null&&wakeLock.isHeld())wakeLock.release();
        wakeLock=getSystemService(PowerManager.class).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"mybudget:alarm");wakeLock.acquire(MAX_RING_MINUTES*60_000L+5_000L);}
    // Plays [soundChoices] from [index]; a sound that can't be read or played hands over to the next one. If none plays,
    // Android's own beeps (ToneGenerator), so a ringing alarm is never silent.
    private void play(int index){releasePlayer();if(index>=soundChoices.size()){startBeeps();return;}Uri uri=soundChoices.get(index);int run=soundRun;
        java.util.function.Consumer<String> next=why->{Log.w("AlarmService","Couldn't play the alarm sound "+uri+" ("+why+"), trying the next one");
            handler.post(()->{if(ringing!=null&&run==soundRun)play(index+1);});};
        try{MediaPlayer mp=new MediaPlayer();player=mp;
            // Alarm usage keeps it audible in silent mode and lets it through default Do Not Disturb.
            mp.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build());
            mp.setOnErrorListener((m,what,extra)->{if(player==m)next.accept("error "+what+"/"+extra);return true;});
            mp.setOnPreparedListener(m->{if(player==m)try{m.start();}catch(Exception e){next.accept(e.toString());}});
            mp.setDataSource(this,uri);mp.setLooping(true);mp.prepareAsync();}
        catch(Exception e){releasePlayer();next.accept(e.toString());}}
    private ToneGenerator tones;
    private final Runnable beep=new Runnable(){@Override public void run(){if(tones!=null)tones.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD,1_000);handler.postDelayed(this,1_600L);}};
    private void startBeeps(){try{tones=new ToneGenerator(AudioManager.STREAM_ALARM,ToneGenerator.MAX_VOLUME);}catch(Exception e){Log.w("AlarmService","No alarm sound could play",e);return;}handler.post(beep);}
    private Vibrator vibrator(){if(Build.VERSION.SDK_INT>=31){VibratorManager m=getSystemService(VibratorManager.class);return m==null?null:m.getDefaultVibrator();}return getSystemService(Vibrator.class);}
    private void startVibration(){Vibrator v=vibrator();if(v==null)return;
        // Alarm attributes stop Android from silencing the vibration while the app is in the background.
        VibrationEffect effect=VibrationEffect.createWaveform(new long[]{0,800,600},0);
        if(Build.VERSION.SDK_INT>=33)v.vibrate(effect,new VibrationAttributes.Builder().setUsage(VibrationAttributes.USAGE_ALARM).build());
        else v.vibrate(effect,new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build());}
    // Given up on, it ends its own start only: an alarm started after it still rings. An alarm stopped from elsewhere just now
    // (entered, deleted, snoozed) is no longer this one's, so no "Missed alarm" goes into the slot its notification was just taken from.
    private void onGiveUp(){int start=ringingStart;
        synchronized(OwnedAlarmStarts.class){if(ringing!=null&&currentReminderId!=null&&OwnedAlarmStarts.isCurrent(this,ringing))showAsNotification(ringing,true,false);}
        stopRinging(start);}
    // [startId]: only that start (and the ones before it) is done with; without it, all of them (Stop, Snooze).
    private void stopRinging(Integer startId){handler.removeCallbacks(giveUp);releasePlayer();Vibrator v=vibrator();if(v!=null)v.cancel();
        if(wakeLock!=null&&wakeLock.isHeld())wakeLock.release();wakeLock=null;OwnedAlarmStarts.finish(this,ringing);
        ringing=null;currentReminderId=null;stopToken=null;stopUnlockWatch();stopForeground(STOP_FOREGROUND_REMOVE);
        if(startId==null)stopSelf(lastStart);else stopSelf(Math.max(startId,ignoredStart));}
    private void releasePlayer(){if(player!=null){MediaPlayer p=player;player=null;try{p.stop();}catch(Exception e){}p.release();}
        handler.removeCallbacks(beep);if(tones!=null){try{tones.stopTone();}catch(Exception e){}tones.release();}tones=null;}
    @Override public void onDestroy(){OwnedAlarmStarts.finish(this,ringing);currentReminderId=null;stopToken=null;stopUnlockWatch();handler.removeCallbacks(giveUp);releasePlayer();
        Vibrator v=vibrator();if(v!=null)v.cancel();if(wakeLock!=null&&wakeLock.isHeld())wakeLock.release();super.onDestroy();}
    // [keepReminder]: a Stop that leaves the reminder's notification (a timed ring's button). Its own request code and data, so it
    // doesn't replace the swipe's plain Stop, the same action.
    private PendingIntent serviceAction(String action,boolean keepReminder){return PendingIntent.getService(this,action.hashCode()+(keepReminder?1:0),
        alarmAction(this,action,stopToken,keepReminder).putExtra(EXTRA_KEEP_REMINDER,keepReminder),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);}
    // Tapping the notification counts as acknowledging: MainActivity stops the alarm when it sees the extra, this ring's own
    // [token], so another app starting MyBudget (it's exported) can't stop a ringing alarm.
    private PendingIntent openAndStop(String token){return PendingIntent.getActivity(this,NOTIFICATION_ID,ReminderNotifications.openIntent(this).putExtra(EXTRA_STOP_ALARM,token),
        PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);}

    private static volatile Long currentReminderId;private static volatile long ringingSince;private static volatile String stopToken;
    /** Reminder [id] was deleted, entered, skipped or snoozed: its ring stops, and a start on its way is refused. */
    static void stopIfRinging(Context c,long id){synchronized(OwnedAlarmStarts.class){
        try{OwnedAlarmStarts.cancel(c,OwnedAlarmStarts.EVENT,Long.toString(id),null);}catch(Exception e){Log.w("AlarmService","Couldn't cancel ringing ownership",e);}
        // No longer this alarm's, at once, so its time running out meanwhile (onRangOut) doesn't post the notification that was just taken away.
        Long current=currentReminderId;if(current!=null&&current==id){currentReminderId=null;requestStop(c,stopToken,false);}}}
    /** A reminder's sound changed to one that doesn't ring: it stops ringing, its notification stays; a start on its way shows the notification instead. */
    static void quietIfRinging(Context c,long id){synchronized(OwnedAlarmStarts.class){
        try{OwnedAlarmStarts.quiet(c,OwnedAlarmStarts.EVENT,Long.toString(id));}catch(Exception e){Log.w("AlarmService","Couldn't quiet ringing ownership",e);}
        Long current=currentReminderId;if(current!=null&&current==id)requestStop(c,stopToken,true);}}
    // MyBudget opened while an alarm rings without its notification (swiped away on Android 14+ and the delete intent didn't get
    // through): nothing else could stop it, so opening the app does. The first seconds are left alone, while Android may not list
    // the just-posted notification yet.
    static void stopIfUnseen(Context c){String token=stopToken;if(currentReminderId==null||SystemClock.elapsedRealtime()-ringingSince<3_000L)return;
        boolean shown=true;try{shown=false;for(android.service.notification.StatusBarNotification n:c.getSystemService(NotificationManager.class).getActiveNotifications())if(n.getId()==NOTIFICATION_ID)shown=true;}catch(Exception e){shown=true;}
        if(!shown)requestStop(c,token,false);}
    /** A tap on the ringing notification ([EXTRA_STOP_ALARM]): stops it only with the ringing alarm's own token. */
    static void stopFromTap(Context c,String token){if(RingToken.matches(stopToken,token))requestStop(c,token,false);}
    private static Intent alarmAction(Context c,String action,String token,boolean keepReminder){Uri.Builder data=new Uri.Builder().scheme("mybudget").authority("alarm-action").appendPath(action).appendPath(token==null?"":token);
        if(keepReminder)data.appendPath("keep");return new Intent(c,AlarmService.class).setAction(action).setData(data.build()).putExtra(EXTRA_STOP_ALARM,token);}
    private static void requestStop(Context c,String token,boolean keepReminder){if(token!=null)c.startService(alarmAction(c,ACTION_STOP,token,false).putExtra(EXTRA_KEEP_REMINDER,keepReminder));}
    static final String ACTION_STOP="com.mybudget.app.alarm.STOP",ACTION_SNOOZE="com.mybudget.app.alarm.SNOOZE",EXTRA_STOP_ALARM="stop_alarm",EXTRA_KEEP_REMINDER="keep_reminder";
    static final long SNOOZE_MINUTES=10,MAX_RING_MINUTES=10;
    // Seconds a reminder rings (ReminderSound.ringSecondsNow): above 0 a timed ring, 0 (or none) until stopped.
    static final String EXTRA_RING_FOR="ring_for";
    private static final int NOTIFICATION_ID=1_000_000_001;
    /** Rings a sample alarm so the sound, vibration and buttons can be checked from Settings. False when it can't ring: its notification (the only way to stop it) couldn't show. */
    static boolean startTest(Context c){if(!ReminderNotifications.ringingAlarmsEnabled(c))return false;
        c.startForegroundService(new Intent(c,AlarmService.class).putExtra(ReminderScheduler.EXTRA_REMINDER_ID,0L).putExtra(ReminderScheduler.EXTRA_TITLE,"Test alarm")
            .putExtra(ReminderScheduler.EXTRA_TEXT,Reminders.moment(System.currentTimeMillis(),c)).putExtra(ReminderScheduler.EXTRA_SUB,"Test")
            .putExtra(ReminderScheduler.EXTRA_RING,true).putExtra(EXTRA_RING_FOR,0).putExtra(ReminderScheduler.EXTRA_TRIGGER,System.currentTimeMillis()));return true;}
}

/**
 * How long an alarm rings: its full time when it starts; after Android restarted the service, what is left counted from the
 * reminder's time (at least a minute), or null (missed) once that is long gone. A trigger of 0 (not known) rings fully.
 * [seconds]: a timed ring's length (above 0): after a restart only what is left of it, and null (its normal notification, not
 * missed) once none is left.
 */
final class AlarmRestart {
    private AlarmRestart(){}
    static Long ringFor(boolean redelivered,long trigger,long now,int seconds){
        if(seconds>0){long timed=seconds*1_000L;if(!redelivered||trigger<=0L)return timed;long left=trigger+timed-now;return left>0?Math.min(left,timed):null;}
        long full=AlarmService.MAX_RING_MINUTES*60_000L;if(!redelivered||trigger<=0L)return full;long left=trigger+full-now;
        return left<-5*60_000L?null:Math.max(60_000L,Math.min(left,full));}
}
/** What a tap on the ringing notification must carry to stop it, new for each ring, so no other app can guess it. */
final class RingToken {
    private RingToken(){}
    static String create(){return UUID.randomUUID().toString();}
    static boolean matches(String current,String given){return current!=null&&current.equals(given);}
}
/** The sounds a ringing alarm tries, in order: the chosen alarm sound (only once unlocked: before, it can't be read), the default alarm and ringtone, MyBudget's own. */
final class AlarmSound {
    private AlarmSound(){}
    static <T> List<T> choices(boolean unlocked,T chosen,T defaultAlarm,T defaultRingtone,T bundled){List<T> list=new ArrayList<>();
        for(T t:Arrays.asList(unlocked?chosen:null,defaultAlarm,defaultRingtone,bundled))if(t!=null&&!list.contains(t))list.add(t);return list;}
}
/**
 * A reminder's ringing start, reserved as "event:<reminder id>" (ReminderReceiver) on this phone, surviving the process ending,
 * and readable before the first unlock. A start Android delivers again after ending the process is refused once the reminder
 * was dealt with meanwhile (cancel). An alarm without one (the Settings test, one that couldn't reserve) rings as before.
 */
final class OwnedAlarmStarts {
    private OwnedAlarmStarts(){}
    static final String EXTRA_EVENT_START="alarm_event_start",EVENT="event";
    private static final String QUIET="quiet:";
    private static AtomicFile file(Context c){return new AtomicFile(new File(c.createDeviceProtectedStorageContext().getNoBackupFilesDir(),"ring-starts"));}
    // readFully (not a check of the file itself) so AtomicFile brings back its backup after a write cut short. A file that still
    // can't be read counts as empty: its starts are refused, and the next reservation writes it afresh.
    private static JSONObject read(Context c){byte[] bytes;try{bytes=file(c).readFully();}catch(java.io.IOException e){return new JSONObject();}
        try{return new JSONObject(new String(bytes,StandardCharsets.UTF_8));}catch(JSONException e){Log.w("OwnedAlarmStarts","Unreadable ringing ownership, starting again",e);return new JSONObject();}}
    private static void write(Context c,JSONObject value)throws java.io.IOException{AtomicFile f=file(c);f.getBaseFile().getParentFile().mkdirs();FileOutputStream out=f.startWrite();
        try{out.write(value.toString().getBytes(StandardCharsets.UTF_8));f.finishWrite(out);}catch(java.io.IOException|RuntimeException e){f.failWrite(out);throw e;}}
    static synchronized String reserve(Context c,String kind,String id)throws Exception{String token=RingToken.create();write(c,read(c).put(kind+":"+id,token));return token;}
    static synchronized void cancel(Context c,String kind,String id,String token)throws Exception{JSONObject v=read(c);String key=kind+":"+id;
        if(v.has(key)&&(token==null||v.optString(key).equals(token)||v.optString(key).equals(QUIET+token))){v.remove(key);write(c,v);}}
    /** Ringing turned off while this alarm is on its way or ringing: its start is refused as a cancelled one is, but leaves the reminder's notification ([takeQuiet]). */
    static synchronized void quiet(Context c,String kind,String id)throws Exception{JSONObject v=read(c);String key=kind+":"+id;if(!v.has(key))return;String token=v.optString(key);
        if(!token.startsWith(QUIET))write(c,v.put(key,QUIET+token));}
    /** True once, for a start refused because its ringing was turned off ([quiet]): it is then done with. */
    static synchronized boolean takeQuiet(Context c,Bundle extras){String[] o=owner(extras);if(o==null)return false;JSONObject v;try{v=read(c);}catch(Exception e){return false;}
        String key=o[0]+":"+o[1];if(!(QUIET+o[2]).equals(v.has(key)?v.optString(key):null))return false;v.remove(key);try{write(c,v);}catch(Exception e){}return true;}
    /** [extras]' reminder still stands, ringing or turned quiet; an alarm without a reservation always does. */
    static synchronized boolean isCurrent(Context c,Bundle extras){String[] o=owner(extras);if(o==null)return true;
        try{JSONObject v=read(c);String key=o[0]+":"+o[1];String now=v.has(key)?v.optString(key):null;return now!=null&&(now.equals(o[2])||now.equals(QUIET+o[2]));}catch(Exception e){return false;}}
    static synchronized void finish(Context c,Bundle extras){String[] o=owner(extras);if(o==null)return;try{cancel(c,o[0],o[1],o[2]);}catch(Exception e){Log.w("OwnedAlarmStarts","Couldn't finish ringing ownership",e);}}
    /** The reservation [extras] rings under ({kind, reminder id, token}), or null for an alarm without one. */
    static String[] owner(Bundle extras){String token=extras==null?null:extras.getString(EXTRA_EVENT_START);if(token==null)return null;
        return new String[]{EVENT,Long.toString(extras.getLong(ReminderScheduler.EXTRA_REMINDER_ID)),token};}
    /** Hold ownership through startup so cancellation cannot land between validation and playback. */
    static synchronized boolean start(Context c,Bundle extras,Runnable play){
        if(extras!=null&&extras.getString(EXTRA_EVENT_START)!=null){String[] o=owner(extras);String current;
            try{JSONObject v=read(c);String key=o[0]+":"+o[1];current=v.has(key)?v.optString(key):null;}catch(Exception e){Log.w("OwnedAlarmStarts","Couldn't validate ringing owner",e);return false;}
            if(!RingToken.matches(current,o[2]))return false;}
        play.run();return true;}
}
