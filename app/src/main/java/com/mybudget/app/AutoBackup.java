package com.mybudget.app;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Automatic backup: once a day, a backup file (the same as Back up budget) goes to a folder the user picked once,
 * as MyBudget-auto-YYYY-MM-DD.json, keeping the newest 7. It runs as a daily job and when MyBudget opens.
 * Settings live in the "budget" preferences: auto_backup_tree (the folder), auto_backup_last (date), auto_backup_error.
 */
public class AutoBackup extends JobService {
    static final String PREFIX="MyBudget-auto-";static final int KEEP=7,JOB=1;
    @Override public boolean onStartJob(JobParameters params){new Thread(()->{run(getApplicationContext(),false);jobFinished(params,false);}).start();return true;}
    @Override public boolean onStopJob(JobParameters params){return false;}

    static void schedule(Context context){
        JobScheduler jobs=context.getSystemService(JobScheduler.class);if(jobs==null)return;
        if(context.getSharedPreferences("budget",0).getString("auto_backup_tree",null)==null){jobs.cancel(JOB);return;}
        if(jobs.getPendingJob(JOB)!=null)return;
        jobs.schedule(new JobInfo.Builder(JOB,new ComponentName(context,AutoBackup.class)).setPeriodic(24L*60*60*1000).build());
    }
    /** Writes today's backup unless there is one already ([force]: write anyway). Returns an error message or null. */
    static synchronized String run(Context context,boolean force){
        SharedPreferences prefs=context.getSharedPreferences("budget",0);String tree=prefs.getString("auto_backup_tree",null),raw=prefs.getString("data",null);
        if(tree==null||raw==null)return null;String today=LocalDate.now().toString();if(!force&&today.equals(prefs.getString("auto_backup_last","")))return null;
        try{
            Uri treeUri=Uri.parse(tree),folder=DocumentsContract.buildDocumentUriUsingTree(treeUri,DocumentsContract.getTreeDocumentId(treeUri));
            String name=PREFIX+today+".json";Map<String,String> existing=children(context,treeUri);
            if(existing.containsKey(name))DocumentsContract.deleteDocument(context.getContentResolver(),DocumentsContract.buildDocumentUriUsingTree(treeUri,existing.remove(name)));
            Uri file=DocumentsContract.createDocument(context.getContentResolver(),folder,"application/json",name);if(file==null)throw new IllegalStateException("The folder didn't accept a new file.");
            String text=BudgetStore.backup(BudgetStore.decode(raw),LocalDateTime.now());
            try(OutputStream out=context.getContentResolver().openOutputStream(file,"w")){if(out==null)throw new IllegalStateException("The file couldn't be written.");out.write(text.getBytes(StandardCharsets.UTF_8));}
            List<String> old=new ArrayList<>(existing.keySet());old.removeIf(n->!n.startsWith(PREFIX));Collections.sort(old,Collections.reverseOrder());
            for(int i=KEEP-1;i<old.size();i++)DocumentsContract.deleteDocument(context.getContentResolver(),DocumentsContract.buildDocumentUriUsingTree(treeUri,existing.get(old.get(i))));
            prefs.edit().putString("auto_backup_last",today).remove("auto_backup_error").apply();return null;
        }catch(Exception e){String m="Automatic backup failed: "+(e.getMessage()==null?"the folder can't be reached.":e.getMessage());prefs.edit().putString("auto_backup_error",m).apply();return m;}
    }
    /** Display name -> document id of the files in the picked folder. */
    private static Map<String,String> children(Context context,Uri treeUri){
        Map<String,String> map=new HashMap<>();Uri kids=DocumentsContract.buildChildDocumentsUriUsingTree(treeUri,DocumentsContract.getTreeDocumentId(treeUri));
        try(Cursor c=context.getContentResolver().query(kids,new String[]{DocumentsContract.Document.COLUMN_DISPLAY_NAME,DocumentsContract.Document.COLUMN_DOCUMENT_ID},null,null,null)){if(c!=null)while(c.moveToNext())map.put(c.getString(0),c.getString(1));}
        return map;
    }
}
