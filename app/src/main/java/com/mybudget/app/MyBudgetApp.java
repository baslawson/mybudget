package com.mybudget.app;

import android.app.Application;
import android.content.SharedPreferences;

/**
 * Sets up reminders for the whole app: their notification channels, and a check of every reminder after each save of the
 * budget (by any screen, Planner's expenses, a restore, a reminder's own buttons) or a change to Hide amounts, which the
 * reminders' text follows (ReminderScheduler.request).
 */
public class MyBudgetApp extends Application {
    // Held here: SharedPreferences keeps its listeners only weakly.
    private final SharedPreferences.OnSharedPreferenceChangeListener changed=(prefs,key)->{
        if(key==null||key.equals("data")||key.equals("hideAmounts"))ReminderScheduler.request(this);};
    @Override public void onCreate(){super.onCreate();
        try{ReminderNotifications.createChannels(this);}catch(Exception e){android.util.Log.w("MyBudgetApp","Couldn't create the reminder channels",e);}
        // Before the first unlock after a reboot (a reminder ringing from the locked snapshot) the app's own storage can't be read.
        if(!DirectBoot.isUnlocked(this))return;
        ReminderSound.mirror(this,ReminderSound.setting(this));
        getSharedPreferences("budget",MODE_PRIVATE).registerOnSharedPreferenceChangeListener(changed);
        getSharedPreferences("appearance",MODE_PRIVATE).registerOnSharedPreferenceChangeListener(changed);}
}
