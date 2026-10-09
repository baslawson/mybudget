package com.mybudget.app;

import android.content.Context;
import java.security.MessageDigest;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Reminder times and what they say, as Planner's for an all-day event: a transaction has a date and no time, so its reminders
 * count from [START] (9:00) on that date. Days keep that clock time; hours and minutes are elapsed time across a clock change.
 */
final class Reminders {
    private Reminders(){}
    static final LocalTime START=LocalTime.of(9,0);
    /** The furthest a reminder may be from its date (the Custom box takes four digits of days). */
    static final long MAX_OFFSET_MINUTES=9999L*1440;
    /** The presets of "Add reminder" (user, 9 Oct: 9:00 on the day, a day before, a week before): amount, unit, menu text. */
    static final Object[][] PRESETS={{0,"minutes","On the day at 9:00"},{1,"days","1 day before at 9:00"},{7,"days","1 week before at 9:00"}};

    /** When [r] goes off for a transaction dated [date]. */
    static ZonedDateTime trigger(LocalDate date,Budget.Reminder r,ZoneId zone){LocalDateTime start=date.atTime(START);
        return r.unit.equals("days")?start.minusDays(r.amount).atZone(zone):start.atZone(zone).minusMinutes(r.offsetMinutes());}
    static long triggerMillis(String date,Budget.Reminder r){return trigger(LocalDate.parse(date),r,ZoneId.systemDefault()).toInstant().toEpochMilli();}
    /**
     * A reminder at the date and time [at] ("Pick date and time"), as the offset from 9:00 on [date] that reminders keep, so it
     * moves with the transaction: whole days when [at] is at 9:00, else hours or minutes of elapsed time; after the date it is
     * negative. {amount, unit index} or null when too far off to store.
     */
    static Object[] at(LocalDate date,LocalDateTime at,ZoneId zone){LocalDateTime start=date.atTime(START);
        if(at.toLocalTime().equals(START)){long days=ChronoUnit.DAYS.between(at.toLocalDate(),date);
            if(Math.abs(days)*1440>MAX_OFFSET_MINUTES)return null;return days==0?new Object[]{0,"minutes"}:new Object[]{(int)days,"days"};}
        long minutes=Duration.between(at.atZone(zone),start.atZone(zone)).toMinutes();if(Math.abs(minutes)>MAX_OFFSET_MINUTES)return null;
        return minutes%60==0?new Object[]{(int)(minutes/60),"hours"}:new Object[]{(int)minutes,"minutes"};}

    /** "On the day at 9:00", "1 day before at 9:00", "1 week before at 9:00", "2 hours before 9:00", "1 day, 3 hours and 15 minutes after 9:00". */
    static String label(Budget.Reminder r,Context c){
        String nine=time(START,c),side=r.amount<0?"after":"before";long n=Math.abs((long)r.amount);
        if(r.amount==0)return "On the day at "+nine;
        if(r.unit.equals("days")){if(n%7==0)return count(n/7,"week")+" "+side+" at "+nine;return count(n,"day")+" "+side+" at "+nine;}
        long minutes=Math.abs(r.offsetMinutes());List<String> parts=new ArrayList<>();
        if(minutes>=1440&&minutes/1440>0)parts.add(count(minutes/1440,"day"));if(minutes%1440/60>0)parts.add(count(minutes%1440/60,"hour"));if(minutes%60>0)parts.add(count(minutes%60,"minute"));
        String joined=parts.size()==1?parts.get(0):String.join(", ",parts.subList(0,parts.size()-1))+" and "+parts.get(parts.size()-1);
        return joined+" "+side+" "+nine;}
    private static String count(long n,String unit){return n+" "+unit+(n==1?"":"s");}
    /** A clock time as the phone shows them (24-hour or am/pm). */
    static String time(LocalTime t,Context c){boolean h24=c!=null&&android.text.format.DateFormat.is24HourFormat(c);
        return t.format(DateTimeFormatter.ofPattern(h24?"H:mm":"h:mm a",Locale.getDefault()));}
    /** "Tue 7 Oct". */
    static String day(LocalDate d){return d.format(DateTimeFormatter.ofPattern("EEE d MMM",Locale.getDefault()));}
    /** A moment as reminders say it: "Tue 7 Oct, 9:00". */
    static String moment(long at,Context c){ZonedDateTime z=Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault());return day(z.toLocalDate())+", "+time(z.toLocalTime(),c);}

    /** The reminder [id] with what it belongs to: a transaction ([entry]) or an upcoming one ([upcoming]). */
    static final class Found {
        final Budget.Reminder reminder;final Budget.Entry entry;final Budget.Scheduled upcoming;
        Found(Budget.Reminder reminder,Budget.Entry entry,Budget.Scheduled upcoming){this.reminder=reminder;this.entry=entry;this.upcoming=upcoming;}
        String date(){return entry!=null?entry.date:upcoming.next;}
        /** When it goes off now, its snooze aside. */
        long onTime(){return triggerMillis(date(),reminder);}
        String key(){return key(reminder.id);}
        static String key(long id){return "r:"+id;}
    }
    static Found find(Budget b,long id){
        for(Budget.Scheduled s:b.scheduled)for(Budget.Reminder r:s.reminders)if(r.id==id)return new Found(r,null,s);
        for(Budget.Entry e:b.entries)for(Budget.Reminder r:e.reminders)if(r.id==id)return new Found(r,e,null);return null;}
    static List<Found> all(Budget b){List<Found> list=new ArrayList<>();
        for(Budget.Scheduled s:b.scheduled)for(Budget.Reminder r:s.reminders)list.add(new Found(r,null,s));
        for(Budget.Entry e:b.entries)for(Budget.Reminder r:e.reminders)list.add(new Found(r,e,null));return list;}
    static long id(String key){if(key==null||!key.startsWith("r:"))return 0;try{return Long.parseLong(key.substring(2));}catch(NumberFormatException e){return 0;}}

    /**
     * Its schedule (as Planner's eventReminderToken): what it belongs to, the date it counts from and the reminder itself, but not
     * the payee or notes, so unrelated edits keep an active Snooze usable. A snooze is kept with it (Snoozes): a change ends it.
     */
    static String snoozeToken(Found f){return sha(f.date()+"|"+(f.entry!=null?"e:"+f.entry.id:"s:"+f.upcoming.id)+"|"+when(f.reminder));}
    /**
     * Its "Enter it now" and "Skip" (as Planner's billReminderToken): the whole upcoming transaction as it is, so a button from
     * before an edit can't enter something else. Null when it has none: a transaction, or a reminder before the upcoming day.
     */
    static String enterToken(Found f,long trigger){if(f.upcoming==null)return null;
        LocalDate on=Instant.ofEpochMilli(trigger).atZone(ZoneId.systemDefault()).toLocalDate();if(on.isBefore(LocalDate.parse(f.upcoming.next)))return null;
        Budget.Scheduled s=f.upcoming;StringBuilder parts=new StringBuilder();for(Budget.Split p:s.splits)parts.append(p.category).append(':').append(p.amount).append(':').append(p.memo).append(';');
        return sha(s.id+"|"+s.payee+"|"+s.category+"|"+s.account+"|"+s.next+"|"+s.repeat+"|"+s.day+"|"+s.amount+"|"+s.memo+"|"+parts+"|"+when(f.reminder));}
    /** An on-time delivery's record (ReminderDeliveries): its local date and offset, which a time zone change doesn't move. */
    static String deliveryKey(Found f){return f.date()+"|"+f.reminder.amount+"|"+f.reminder.unit;}
    /** A reminder's time, without its sound: a sound changed while it rings quietens it but keeps it (ReminderScheduler). */
    static String when(Budget.Reminder r){return r.id+"|"+r.amount+"|"+r.unit;}
    static String sha(String text){try{byte[] d=MessageDigest.getInstance("SHA-256").digest(text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        StringBuilder s=new StringBuilder();for(byte b:d)s.append(String.format("%02x",b));return s.toString();}catch(Exception e){throw new IllegalStateException(e);}}

    // Time zones span UTC-12 to UTC+14.
    static final long MAX_ZONE_SHIFT_MS=26*3_600_000L;
    /**
     * Whether an alarm set for [trigger] may still show ([expected]: when it is due now). Besides an exact match, an on-time
     * alarm set before the phone moved east is accepted: its recomputed time is earlier and already past, and scheduling
     * leaves the old alarm in place rather than drop the reminder. (A move west re-times the still-future alarm instead.)
     */
    static boolean accepts(long trigger,long expected,boolean snoozed,long now){
        return trigger==expected||!snoozed&&(trigger==0L||trigger>expected&&trigger<=Math.min(now,expected+MAX_ZONE_SHIFT_MS));}
}
