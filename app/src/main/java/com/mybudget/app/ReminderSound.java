package com.mybudget.app;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * How a reminder sounds, as in Planner: the plain notification sound, which silent and vibrate mode mute, or a ring through
 * AlarmService (the alarm sound, through silent and vibrate mode, shown over the lock screen) for 10 s, 30 s, 1 min, or until
 * it is stopped. Settings › Reminders › "Reminder sound" says what [DEFAULT] means; each reminder can choose its own.
 *
 * Stored per reminder as two fields (Budget.Reminder): `ring` is "Until I stop it", and `ringSeconds` holds the rest (0 = the
 * default, -1 = notification sound only, 10/30/60 = rings that long; always 0 with `ring` on).
 */
enum ReminderSound {
    DEFAULT(false,0,"Default","Default"),
    NOTIFICATION(false,-1,"Notification sound only","notification sound only"),
    SECONDS_10(false,10,"10 seconds","10 s"),
    SECONDS_30(false,30,"30 seconds","30 s"),
    MINUTE(false,60,"1 minute","1 min"),
    UNTIL_STOPPED(true,0,"Until I stop it","until I stop it");
    final boolean ring;final int seconds;final String label,shortLabel;
    ReminderSound(boolean ring,int seconds,String label,String shortLabel){this.ring=ring;this.seconds=seconds;this.label=label;this.shortLabel=shortLabel;}
    /** The Settings choice on a new install. */
    static final ReminderSound SETTING_DEFAULT=SECONDS_10;
    /**
     * How long it rings through AlarmService, in seconds (0: until stopped, giving up after AlarmService.MAX_RING_MINUTES);
     * null: no ring, the notification sound only. [DEFAULT] has none of its own: resolve it first ([resolve]).
     */
    Integer alarmSeconds(){switch(this){case DEFAULT:case NOTIFICATION:return null;case UNTIL_STOPPED:return 0;default:return seconds;}}
    /** What a reminder's own list shows for this choice, [def] being the Settings choice: "Default (10 s)". */
    String choiceLabel(ReminderSound def){return this==DEFAULT?"Default ("+(def==DEFAULT?SETTING_DEFAULT:def).shortLabel+")":label;}
    /** A reminder's choice from its two stored fields. A length no build offers (a later version's, say) reads as Default. */
    static ReminderSound of(boolean ring,int seconds){if(ring)return UNTIL_STOPPED;for(ReminderSound s:values())if(!s.ring&&s.seconds==seconds)return s;return DEFAULT;}
    /** `ringSeconds` as it is stored: only a length on offer, and 0 with "Until I stop it". */
    static int cleanSeconds(boolean ring,int seconds){return ring?0:of(false,seconds).seconds;}
    /** What a reminder with these fields does now, with [def] the Settings choice: never [DEFAULT]. */
    static ReminderSound resolve(boolean ring,int seconds,ReminderSound def){ReminderSound own=of(ring,seconds);
        return own!=DEFAULT?own:def!=DEFAULT?def:SETTING_DEFAULT;}
    /** The Settings choice from what is stored ([name], null when never set). */
    static ReminderSound fromSetting(String name){for(ReminderSound s:values())if(s!=DEFAULT&&s.name().equals(name))return s;return SETTING_DEFAULT;}

    /**
     * Settings › Reminders › "Reminder sound", in the app's settings, and a copy in device-protected storage: a reminder that
     * rings before the first unlock after a reboot can't read the app's settings, and would otherwise ring for the new-install
     * default instead of the user's choice.
     */
    static final String PREF="reminder_sound";
    private static SharedPreferences settings(Context c){return c.getSharedPreferences("reminders",Context.MODE_PRIVATE);}
    private static SharedPreferences locked(Context c){return c.createDeviceProtectedStorageContext().getSharedPreferences("reminder_sound",Context.MODE_PRIVATE);}
    static ReminderSound setting(Context c){return fromSetting(settings(c).getString(PREF,null));}
    static boolean write(Context c,ReminderSound sound){boolean ok=settings(c).edit().putString(PREF,sound.name()).commit();mirror(c,sound);return ok;}
    /** Copies [sound] for a ring before the first unlock (also at start, so a restart before any reminder rings has it). */
    static void mirror(Context c,ReminderSound sound){
        try{SharedPreferences p=locked(c);if(!sound.name().equals(p.getString(PREF,null)))p.edit().putString(PREF,sound.name()).apply();}
        catch(Exception e){android.util.Log.w("ReminderSound","Couldn't keep the reminder sound for a locked restart",e);}}
    /** The choice now, as a reminder ringing reads it: the setting once unlocked (copied for a locked restart); before the first unlock, that copy. */
    static ReminderSound current(Context c){
        if(DirectBoot.isUnlocked(c)){ReminderSound s;try{s=setting(c);}catch(Exception e){s=SETTING_DEFAULT;}mirror(c,s);return s;}
        try{return fromSetting(locked(c).getString(PREF,null));}catch(Exception e){return SETTING_DEFAULT;}}
    /**
     * How long a reminder due now rings through AlarmService, from its own choice: seconds, 0 until stopped, or null for the
     * notification sound only (no ring). A timed ring is the very same ring as "Until I stop it", only it stops by itself.
     */
    static Integer ringSecondsNow(Context c,boolean ring,int seconds){return resolve(ring,seconds,current(c)).alarmSeconds();}
}
