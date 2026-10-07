package com.mybudget.app;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Data safety rules, kept free of Android so tests/BudgetTest.java can check them: when Home asks for a backup,
 * when Undo of a delete may still put the budget back, and which day's automatic backup to write and which to prune.
 * Dates are ISO (YYYY-MM-DD) strings as kept in preferences; a missing or unreadable one counts as none.
 */
public final class DataSafety {
    private DataSafety(){}
    public static final int REMIND_DAYS=14,SNOOZE_DAYS=7,KEEP=7;
    public static final String PREFIX="MyBudget-auto-";
    private static LocalDate day(String iso){if(iso==null||iso.isEmpty())return null;try{return LocalDate.parse(iso);}catch(RuntimeException e){return null;}}
    /** The later of two backup dates (manual and automatic), or null when neither is readable. */
    public static String latest(String a,String b){LocalDate x=day(a),y=day(b);LocalDate d=x==null?y:y==null||x.isAfter(y)?x:y;return d==null?null:d.toString();}
    /** Whole days since the last backup (0 for a date after today: the clock moved back), or -1 for never. */
    public static long daysSince(String last,LocalDate today){LocalDate d=day(last);return d==null?-1:Math.max(0,ChronoUnit.DAYS.between(d,today));}
    /**
     * Home's backup alert: a budget with at least one account, with no backup for REMIND_DAYS days (or never),
     * unless "Remind me in a week" was tapped less than a week ago (a snooze further ahead than that is ignored).
     */
    public static boolean backupReminderDue(boolean hasAccounts,String lastBackup,String snoozedUntil,LocalDate today){
        if(!hasAccounts)return false;LocalDate until=day(snoozedUntil);
        if(until!=null&&today.isBefore(until)&&!until.isAfter(today.plusDays(SNOOZE_DAYS)))return false;
        long days=daysSince(lastBackup,today);return days<0||days>=REMIND_DAYS;}
    public static String snoozeUntil(LocalDate today){return today.plusDays(SNOOZE_DAYS).toString();}
    /**
     * Undo of a delete puts back the budget saved before it, only while the saved data is still exactly what the delete
     * saved and what the screen holds: anything saved since (an expense from Planner, a restore) is never overwritten.
     */
    public static boolean undoAllowed(String stored,String savedByDelete,String onScreen){
        return savedByDelete!=null&&savedByDelete.equals(stored)&&savedByDelete.equals(onScreen);}
    /** Automatic backup: one file a day, so it's due unless the last one was written today. */
    public static boolean autoBackupDue(LocalDate today,String last){return !today.toString().equals(last);}
    public static String autoBackupName(LocalDate day){return PREFIX+day+".json";}
    /** After writing [written], the older automatic backups to delete so the newest KEEP stay; other files are left alone. */
    public static List<String> autoBackupsToDelete(Collection<String> names,String written){
        List<String> old=new ArrayList<>();for(String n:names)if(n.startsWith(PREFIX)&&!n.equals(written))old.add(n);
        Collections.sort(old,Collections.reverseOrder());return old.size()<KEEP?new ArrayList<>():new ArrayList<>(old.subList(KEEP-1,old.size()));}
}
