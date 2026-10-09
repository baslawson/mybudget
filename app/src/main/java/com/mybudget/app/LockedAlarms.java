package com.mybudget.app;

import android.app.AlarmManager;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.UserManager;
import android.util.AtomicFile;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * One reminder's alarm and what its notification shows, as Planner's LockedAlarm.Event. It travels in the alarm's intent (each
 * save sets it again with fresh values) and in the snapshot for a locked reboot: after a reboot Android clears every alarm, and
 * MyBudget's budget (credential-encrypted storage) can't be read until the phone is first unlocked. So the nearest alarms are
 * also kept in device-protected storage (LockedAlarmStore); at LOCKED_BOOT_COMPLETED they are set again from it (BootReceiver),
 * and while the phone is still locked ReminderReceiver shows them from their intent; once unlocked BOOT_COMPLETED sets every
 * alarm from the budget with the same request codes, replacing these. The transaction's note isn't kept there.
 */
final class LockedAlarm {
    final long reminderId,trigger;final String title,text,whenText,subText,snoozeToken,enterToken;final boolean repeats,ring;final int ringSeconds;
    LockedAlarm(long reminderId,long trigger,String title,String text,String whenText,String subText,boolean ring,int ringSeconds,String snoozeToken,String enterToken,boolean repeats){
        this.reminderId=reminderId;this.trigger=trigger;this.title=title;this.text=text;this.whenText=whenText;this.subText=subText;this.ring=ring;
        this.ringSeconds=ringSeconds;this.snoozeToken=snoozeToken;this.enterToken=enterToken;this.repeats=repeats;}
    String key(){return Reminders.Found.key(reminderId);}
    /**
     * [f]'s alarm at [trigger], saying what MyBudget's lists do: the payee; the day ("Due Tue 7 Oct" for an upcoming one), the
     * amount (unless amounts are hidden) and the account; the reminder's own time ("1 day before at 9:00") under it.
     */
    static LockedAlarm of(Context c,Budget b,Reminders.Found f,long trigger,boolean hideAmounts){
        String payee=f.entry!=null?f.entry.payee:f.upcoming.payee,account=f.entry!=null?f.entry.account:f.upcoming.account;long amount=f.entry!=null?f.entry.amount:f.upcoming.amount;
        Budget.Account a=b.account(account);String day=Reminders.day(java.time.LocalDate.parse(f.date()));
        String when=(f.upcoming!=null?"Due ":"")+day;
        java.text.NumberFormat format=Budget.moneyFormat(b.currency,Locale.getDefault()); // dots with Hide amounts on, as MyBudget shows them
        String money=hideAmounts?format.getCurrency().getSymbol(Locale.getDefault())+"•••":Budget.money(amount,format);
        StringBuilder text=new StringBuilder(when);if(!money.isEmpty())text.append(" · ").append(money);if(a!=null)text.append(" · ").append(a.name);
        return new LockedAlarm(f.reminder.id,trigger,payee.trim().isEmpty()?"Transaction":payee.trim(),text.toString(),when,Reminders.label(f.reminder,c),
            f.reminder.ring,f.reminder.ringSeconds,Reminders.snoozeToken(f),Reminders.enterToken(f,trigger),f.upcoming!=null&&!f.upcoming.repeat.equals("Never"));}
    /** Its alarm's intent: everything its notification shows ([details]: the transaction's note, never in the snapshot). */
    Intent intent(Context c,String details){return new Intent(c,ReminderReceiver.class)
        .putExtra(ReminderScheduler.EXTRA_REMINDER_ID,reminderId).putExtra(ReminderScheduler.EXTRA_TRIGGER,trigger)
        .putExtra(ReminderScheduler.EXTRA_TITLE,title).putExtra(ReminderScheduler.EXTRA_TEXT,text).putExtra(ReminderScheduler.EXTRA_WHEN,whenText)
        .putExtra(ReminderScheduler.EXTRA_SUB,subText).putExtra(ReminderScheduler.EXTRA_DETAILS,details==null?"":details)
        .putExtra(ReminderScheduler.EXTRA_RING,ring).putExtra(ReminderScheduler.EXTRA_RING_SECONDS,ringSeconds)
        .putExtra(ReminderScheduler.EXTRA_SNOOZE_TOKEN,snoozeToken).putExtra(ReminderScheduler.EXTRA_ENTER_TOKEN,enterToken)
        .putExtra(ReminderScheduler.EXTRA_REPEATS,repeats);}
    /** As LockedAlarmCodec writes it: also what tells a changed alarm (ReminderScheduler sets only those again). */
    String line(){return LockedAlarmCodec.line("r",Long.toString(reminderId),Long.toString(trigger),title,text,whenText,subText,
        ring?"1":"0",Integer.toString(ringSeconds),snoozeToken,enterToken,repeats?"1":"0");}
    @Override public boolean equals(Object o){return o instanceof LockedAlarm&&((LockedAlarm)o).line().equals(line());}
    @Override public int hashCode(){return line().hashCode();}
}

/** The snapshot: the alarms; [rewriteAt], when a snapshot that left alarms out is to be written again; [fullAt], when it was written. */
final class LockedSnapshot {
    final List<LockedAlarm> alarms;final Long rewriteAt,fullAt;
    LockedSnapshot(List<LockedAlarm> alarms,Long rewriteAt,Long fullAt){this.alarms=alarms;this.rewriteAt=rewriteAt;this.fullAt=fullAt;}
}
/** An alarm that rang before the first unlock: the ledger and delivery records are brought up to date once it's unlocked. */
final class LockedFired {
    final String key;final long trigger,at;
    LockedFired(String key,long trigger,long at){this.key=key;this.trigger=trigger;this.at=at;}
}

final class LockedAlarmSelection {
    private LockedAlarmSelection(){}
    /** Only the next two weeks are kept: a phone stays locked after a reboot for hours, not weeks. */
    static final long HORIZON_MS=14*24*3_600_000L;
    /** ...and at most this many of them, the nearest. */
    static final int MAX=64;
    /** ...but always at least this many of the nearest, however far: sparse reminders (a monthly bill) are kept too. */
    static final int MIN_KEPT=5;
    /** A snapshot that left later alarms out is written again this long after, a day before its two weeks run out. */
    static final long REWRITE_AFTER_MS=HORIZON_MS-24*3_600_000L;
    /** A reminder ringing rewrites the snapshot from every alarm once it is this old, so it keeps the next two weeks however long MyBudget goes unopened. */
    static final long REFRESH_MS=12*3_600_000L;
    static boolean stale(Long writtenAt,long now){return writtenAt==null||writtenAt>now||now-writtenAt>=REFRESH_MS;}
    /** The alarms still ahead of [now], within [HORIZON_MS] or among the [MIN_KEPT] nearest, nearest first, at most [MAX]. */
    static List<LockedAlarm> select(Collection<LockedAlarm> alarms,long now){List<LockedAlarm> ahead=new ArrayList<>();for(LockedAlarm a:alarms)if(a.trigger>now)ahead.add(a);
        ahead.sort(Comparator.comparingLong(a->a.trigger));List<LockedAlarm> kept=new ArrayList<>();
        for(int i=0;i<ahead.size()&&kept.size()<MAX;i++)if(i<MIN_KEPT||ahead.get(i).trigger<=now+HORIZON_MS)kept.add(ahead.get(i));return kept;}
    /** When a snapshot written at [now] from [alarms] is to be written again: null when it kept every alarm ahead. */
    static Long rewriteAt(Collection<LockedAlarm> alarms,long now){List<LockedAlarm> kept=select(alarms,now);int ahead=0;for(LockedAlarm a:alarms)if(a.trigger>now)ahead++;
        if(kept.size()>=ahead)return null;return kept.size()==MAX?Math.min(now+REWRITE_AFTER_MS,kept.get(kept.size()-1).trigger):now+REWRITE_AFTER_MS;}
}

/**
 * Plain text, one alarm a line, fields split by tabs. A field's backslashes, tabs and line breaks are escaped; `\0` is null.
 * Lines it doesn't know are skipped, so a damaged line costs one alarm.
 */
final class LockedAlarmCodec {
    private LockedAlarmCodec(){}
    private static final String HEADER="mybudget-locked-alarms",VERSION="1",NULL="\\0";
    static String encode(LockedSnapshot s){StringBuilder o=new StringBuilder(line(HEADER,VERSION,s.rewriteAt==null?null:s.rewriteAt.toString(),s.fullAt==null?null:s.fullAt.toString()));
        for(LockedAlarm a:s.alarms)o.append(a.line());return o.toString();}
    /** Null when [text] isn't a snapshot of this version. */
    static LockedSnapshot decode(String text){String[] lines=text.split("\n");if(lines.length==0)return null;List<String> h=fields(lines[0]);
        if(h.size()<2||!HEADER.equals(h.get(0))||!VERSION.equals(h.get(1)))return null;List<LockedAlarm> alarms=new ArrayList<>();
        for(int i=1;i<lines.length;i++){if(lines[i].isEmpty())continue;List<String> f=fields(lines[i]);
            try{if(!"r".equals(f.get(0)))continue;alarms.add(new LockedAlarm(Long.parseLong(f.get(1)),Long.parseLong(f.get(2)),Objects.requireNonNull(f.get(3)),
                Objects.requireNonNull(f.get(4)),Objects.requireNonNull(f.get(5)),Objects.requireNonNull(f.get(6)),"1".equals(f.get(7)),Integer.parseInt(f.get(8)),f.get(9),f.get(10),"1".equals(f.get(11))));}
            catch(RuntimeException e){/* a damaged line: that alarm only */}}
        return new LockedSnapshot(alarms,num(h,2),num(h,3));}
    private static Long num(List<String> f,int i){try{return f.size()>i&&f.get(i)!=null?Long.valueOf(f.get(i)):null;}catch(NumberFormatException e){return null;}}
    static String encodeFired(List<LockedFired> fired){StringBuilder o=new StringBuilder();for(LockedFired f:fired)o.append(line(f.key,Long.toString(f.trigger),Long.toString(f.at)));return o.toString();}
    static List<LockedFired> decodeFired(String text){List<LockedFired> list=new ArrayList<>();for(String l:text.split("\n")){if(l.isEmpty())continue;List<String> f=fields(l);
        try{list.add(new LockedFired(Objects.requireNonNull(f.get(0)),Long.parseLong(f.get(1)),Long.parseLong(f.get(2))));}catch(RuntimeException e){}}return list;}
    static String line(String... values){StringBuilder o=new StringBuilder();for(int i=0;i<values.length;i++){if(i>0)o.append('\t');o.append(escape(values[i]));}return o.append('\n').toString();}
    static String escape(String v){return v==null?NULL:v.replace("\\","\\\\").replace("\t","\\t").replace("\n","\\n").replace("\r","\\r");}
    private static List<String> fields(String line){List<String> out=new ArrayList<>();for(String f:line.split("\t",-1))out.add(unescape(f));return out;}
    static String unescape(String field){if(field.equals(NULL))return null;if(field.indexOf('\\')<0)return field;StringBuilder out=new StringBuilder();
        for(int i=0;i<field.length();i++){char c=field.charAt(i);if(c=='\\'&&i+1<field.length()){char n=field.charAt(++i);out.append(n=='t'?'\t':n=='n'?'\n':n=='r'?'\r':n);}else out.append(c);}
        return out.toString();}
}

/**
 * The snapshot and the alarms rung while locked, in device-protected storage (they belong to this phone, as the AlarmLedger
 * does). Each is written whole through an AtomicFile, so a crash leaves the old or the new one.
 */
final class LockedAlarmStore {
    private final File dir,snapshotPath;private final AtomicFile snapshotFile,firedFile;
    LockedAlarmStore(Context c){this(new File(c.createDeviceProtectedStorageContext().getNoBackupFilesDir(),"locked-alarms"));}
    LockedAlarmStore(File dir){this.dir=dir;snapshotPath=new File(dir,"snapshot");snapshotFile=new AtomicFile(snapshotPath);firedFile=new AtomicFile(new File(dir,"fired"));}
    synchronized LockedSnapshot read(){String t=text(snapshotFile);return t==null?null:LockedAlarmCodec.decode(t);}
    synchronized void write(LockedSnapshot s)throws java.io.IOException{put(snapshotFile,LockedAlarmCodec.encode(s));}
    synchronized List<LockedFired> fired(){String t=text(firedFile);return t==null?new ArrayList<>():LockedAlarmCodec.decodeFired(t);}
    synchronized void recordFired(LockedFired f)throws java.io.IOException{List<LockedFired> all=fired();all.add(f);put(firedFile,LockedAlarmCodec.encodeFired(all));}
    synchronized void clearFired(){firedFile.delete();}
    private static String text(AtomicFile f){try{return new String(f.readFully(),StandardCharsets.UTF_8);}catch(Exception e){return null;}}
    private void put(AtomicFile f,String text)throws java.io.IOException{dir.mkdirs();FileOutputStream out=f.startWrite();
        try{out.write(text.getBytes(StandardCharsets.UTF_8));f.finishWrite(out);}catch(java.io.IOException|RuntimeException e){f.failWrite(out);throw e;}}
}

/** Before the first unlock after a reboot only device-protected storage can be read; these are the pieces that work then. */
final class DirectBoot {
    private DirectBoot(){}
    private static LockedAlarmStore shared;
    /** Whether the phone has been unlocked since it started, so the budget and the app's settings can be read. */
    static boolean isUnlocked(Context c){UserManager u=c.getSystemService(UserManager.class);return u==null||u.isUserUnlocked();}
    // One per process, so its writes don't overlap.
    static synchronized LockedAlarmStore store(Context c){if(shared==null)shared=new LockedAlarmStore(c.getApplicationContext()!=null?c.getApplicationContext():c);return shared;}
    /** At LOCKED_BOOT_COMPLETED: the snapshot's alarms still ahead are set. The number set. */
    static int armFromSnapshot(Context c){LockedSnapshot s=store(c).read();if(s==null)return 0;AlarmManager am=c.getSystemService(AlarmManager.class);
        List<LockedAlarm> due=LockedAlarmSelection.select(s.alarms,System.currentTimeMillis());
        for(LockedAlarm a:due)try{ReminderScheduler.setReminderAlarm(am,a.trigger,ReminderScheduler.pending(c,a.reminderId,a.intent(c,""),PendingIntent.FLAG_UPDATE_CURRENT));}
            catch(Exception e){android.util.Log.w("DirectBoot","Couldn't set "+a.key(),e);}
        return due.size();}
    /** A receiver ran an alarm while locked: noted, for [replayFired] once the phone is unlocked. */
    static void fired(Context c,String key,long trigger){try{store(c).recordFired(new LockedFired(key,trigger,System.currentTimeMillis()));}
        catch(Exception e){android.util.Log.w("DirectBoot","Couldn't note "+key,e);}}
    /**
     * Once unlocked, before missed reminders are looked for: the alarms that rang while locked are taken off the ledger (they
     * aren't missed) and recorded as delivered, as ringing unlocked would have done. On Android 11 and lower, Enter it now was
     * left out while locked (addDataAction), so a notification still there is shown again with its buttons, quietly.
     */
    static void replayFired(Context c,Budget b){LockedAlarmStore store=store(c);List<LockedFired> fired=store.fired();if(fired.isEmpty())return;
        AlarmLedger ledger=new AlarmLedger(c);Deliveries deliveries=new Deliveries(c);Snoozes snoozes=new Snoozes(c);
        for(LockedFired f:fired)try{ledger.fired(f.key,f.at);Reminders.Found found=b==null?null:Reminders.find(b,Reminders.id(f.key));if(found==null)continue;
            boolean snoozed=snoozes.until(found)>0;long expected=snoozes.trigger(found);if(!Reminders.accepts(f.trigger,expected,snoozed,f.at))continue;
            if(snoozed)deliveries.record(found.key(),expected);else deliveries.recordOnTime(found.reminder.id,Reminders.deliveryKey(found));
            if(Build.VERSION.SDK_INT<31&&notificationShown(c,null,(int)found.reminder.id))ReminderNotifications.showAgain(c,b,found,f.trigger);}
        catch(Exception e){android.util.Log.w("DirectBoot","Couldn't record "+f.key,e);}
        store.clearFired();}
    /** Whether MyBudget's notification [tag]/[id] is still shown. */
    static boolean notificationShown(Context c,String tag,int id){try{for(android.service.notification.StatusBarNotification n:c.getSystemService(NotificationManager.class).getActiveNotifications())
        if(Objects.equals(n.getTag(),tag)&&n.getId()==id)return true;}catch(Exception e){}return false;}
}
