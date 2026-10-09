package com.mybudget.app;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;
import android.widget.Toast;
import java.util.*;

/**
 * An upcoming transaction's reminder buttons, as Planner's Mark paid (BillPaymentReceiver): "Enter it now" enters it (it becomes
 * money, and a repeat moves on to its next date), "Skip" skips this date. Only while it is still what the button was made for
 * (Reminders.enterToken). Undo is offered for a few seconds, from its own notification. Not directBootAware: before the first
 * unlock the budget can't be read, and the buttons ask for the unlock first (ReminderNotifications.addDataAction).
 */
public class ReminderActionReceiver extends BroadcastReceiver {
    static final String ENTER="com.mybudget.app.reminder.ENTER",SKIP="com.mybudget.app.reminder.SKIP",UNDO="com.mybudget.app.reminder.UNDO";
    private static final String UNDO_TAG="reminder-undo",UNDO_TOKEN="reminder_undo_token";
    static final long UNDO_MS=10_000L;
    // The saved data from just before an Enter or Skip and just after it, while its Undo lasts (in this process: a restart ends it).
    private static final Map<String,String[]> undos=new HashMap<>();private static final Map<String,Long> undoUntil=new HashMap<>();
    // Runs on the main thread, as MainActivity's saves do, so neither saves over the other.
    @Override public void onReceive(Context c,Intent intent){
        long id=intent.getLongExtra(ReminderScheduler.EXTRA_REMINDER_ID,0);String token=intent.getStringExtra(ReminderScheduler.EXTRA_ENTER_TOKEN);
        if(id<=0||token==null)return;String action=intent.getAction();
        try{if(UNDO.equals(action)){Toast.makeText(c,undo(c,token)?"Undone":"Undo expired or MyBudget changed since. Open MyBudget to check.",Toast.LENGTH_SHORT).show();
                c.getSystemService(NotificationManager.class).cancel(UNDO_TAG,(int)id);return;}
            String done=act(c,id,token,ENTER.equals(action));
            Toast.makeText(c,done!=null?done:"This upcoming transaction changed or was entered already. Open MyBudget to check.",Toast.LENGTH_SHORT).show();}
        catch(Exception e){Log.w("ReminderActionReceiver","Couldn't change the upcoming transaction",e);
            Toast.makeText(c,e instanceof IllegalArgumentException&&e.getMessage()!=null?e.getMessage():"Couldn't do that. Please try in MyBudget.",Toast.LENGTH_LONG).show();}
    }
    /** Enters ([enter]) or skips reminder [id]'s upcoming transaction; what was done, or null when it isn't what [token] was made for. */
    static String act(Context c,long id,String token,boolean enter)throws Exception{
        android.content.SharedPreferences prefs=c.getSharedPreferences("budget",0);String before=prefs.getString("data",null);if(before==null)return null;
        Budget b=BudgetStore.decode(before);Reminders.Found f=Reminders.find(b,id);if(f==null||f.upcoming==null)return null;
        long trigger=new Snoozes(c).trigger(f);if(!token.equals(Reminders.enterToken(f,trigger))&&!token.equals(Reminders.enterToken(f,f.onTime())))return null;
        Budget.Scheduled s=f.upcoming;String what=s.payee.trim();
        if(enter)b.enter(s);else b.advance(s);
        String after=BudgetStore.encode(b);if(!prefs.edit().putString("data",after).commit())throw new IllegalStateException("Could not save to device storage.");
        BudgetWidget.refresh(c);
        // The reminder moved on with it (or went): its notification and ring go with the next run (ReminderScheduler.cancel).
        ReminderScheduler.request(c);
        String undoToken=UUID.randomUUID().toString();synchronized(undos){undos.put(undoToken,new String[]{before,after});undoUntil.put(undoToken,System.currentTimeMillis()+UNDO_MS);}
        String done=enter?"Entered "+what:"Skipped "+what;showUndo(c,id,undoToken,done);return done+".";
    }
    /** Puts back the data from before, only while the saved data is still what the Enter or Skip saved (nothing saved since is lost). */
    static boolean undo(Context c,String undoToken)throws Exception{String[] pair;synchronized(undos){pair=undos.remove(undoToken);Long until=undoUntil.remove(undoToken);
            if(pair==null||until==null||until<System.currentTimeMillis())return false;}
        android.content.SharedPreferences prefs=c.getSharedPreferences("budget",0);if(!pair[1].equals(prefs.getString("data",null)))return false;
        if(!prefs.edit().putString("data",pair[0]).commit())throw new IllegalStateException("Could not save to device storage.");
        BudgetWidget.refresh(c);ReminderScheduler.request(c);return true;}
    // Its own slot: in the reminder's, the reminder's cancelling (the next run, as it moved on) would remove it at once.
    private static void showUndo(Context c,long id,String undoToken,String done){
        PendingIntent undo=PendingIntent.getBroadcast(c,(int)id,new Intent(c,ReminderActionReceiver.class).setAction(UNDO).setData(Uri.parse("mybudget://reminder-undo/"+undoToken))
            .putExtra(ReminderScheduler.EXTRA_REMINDER_ID,id).putExtra(ReminderScheduler.EXTRA_ENTER_TOKEN,undoToken),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder n=new Notification.Builder(c,ReminderNotifications.REMINDER_CHANNEL).setSmallIcon(R.drawable.ic_notification).setContentTitle(done)
            .setContentText("Saved. Undo is available briefly.").setTimeoutAfter(UNDO_MS).setAutoCancel(true)
            .addAction(new Notification.Action.Builder(null,"Undo",undo).build());ReminderNotifications.silent(n);
        try{c.getSystemService(NotificationManager.class).notify(UNDO_TAG,(int)id,n.build());}catch(SecurityException e){/* saved all the same */}}
    static PendingIntent action(Context c,String action,long id,String token){return PendingIntent.getBroadcast(c,(int)id,new Intent(c,ReminderActionReceiver.class).setAction(action)
        .setData(Uri.parse("mybudget://reminder-action/"+id+"/"+token)).putExtra(ReminderScheduler.EXTRA_REMINDER_ID,id).putExtra(ReminderScheduler.EXTRA_ENTER_TOKEN,token),
        PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);}
}
