package com.mybudget.app;

import android.app.*;
import android.os.Bundle;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.Drawable;
import android.content.res.ColorStateList;
import android.content.Intent;
import android.net.Uri;
import android.graphics.BitmapFactory;
import java.io.*;
import java.nio.charset.StandardCharsets;
import android.text.*;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import java.text.NumberFormat;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class MainActivity extends Activity {
    private Budget budget=new Budget();
    private int ink,blue,muted,red,amber,green,canvas,surface,buttonSurface,primary;
    private String themeMode;
    private LinearLayout root,content;
    private String tab="Home",search="",accountFilter="";
    private String previousTab="Home";
    private YearMonth month=YearMonth.now();
    private boolean storageReadable=true,showHidden=false;
    private final NumberFormat currency=NumberFormat.getCurrencyInstance(Locale.forLanguageTag("en-AU"));
    @Override public void onCreate(Bundle state){
        themeMode=getSharedPreferences("appearance",0).getString("theme","Dark");hideAmounts=getSharedPreferences("appearance",0).getBoolean("hideAmounts",false);
        boolean dark=themeMode.equals("Dark")||(themeMode.equals("Auto")&&(getResources().getConfiguration().uiMode&Configuration.UI_MODE_NIGHT_MASK)==Configuration.UI_MODE_NIGHT_YES);
        setTheme(dark?R.style.AppTheme:R.style.AppTheme_Light);darkTheme=dark;
        super.onCreate(state);
        if(dark){ink=Color.rgb(237,241,250);blue=Color.rgb(179,191,255);muted=Color.rgb(171,181,201);red=Color.rgb(255,142,150);amber=Color.rgb(245,198,110);green=Color.rgb(117,219,177);canvas=Color.rgb(17,21,31);surface=Color.rgb(32,38,53);buttonSurface=Color.rgb(43,52,78);primary=Color.rgb(65,80,159);}
        else{ink=Color.rgb(27,39,62);blue=Color.rgb(57,77,165);muted=Color.rgb(111,121,140);red=Color.rgb(178,51,55);amber=Color.rgb(159,104,12);green=Color.rgb(32,115,85);canvas=Color.rgb(243,245,250);surface=Color.WHITE;buttonSurface=Color.rgb(231,235,249);primary=blue;}
        getWindow().getDecorView().setSystemUiVisibility(dark?0:View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        if(state!=null){tab=state.getString("tab","Home");previousTab=state.getString("previousTab","Home");search=state.getString("search","");accountFilter=state.getString("accountFilter","");month=YearMonth.parse(state.getString("month",YearMonth.now().toString()));}
        load();if(storageReadable){render();cleanupPhotos();}
    }
    // AddExpenseActivity may have saved an expense from another app meanwhile: read it in, so the next save keeps it.
    // Not while picking a photo for an open transaction form: reloading would close the form the photo is for.
    @Override protected void onRestart(){super.onRestart();if(pickingPhoto)return;String raw=getSharedPreferences("budget",0).getString("data",null);if(raw==null||!storageReadable)return;try{budget=BudgetStore.decode(raw);for(AlertDialog editor:new ArrayList<>(editors))editor.dismiss();render();}catch(Exception e){toast("Could not reload your budget.");}}
    private boolean pickingPhoto;private java.util.function.Consumer<Uri> photoTarget;
    // Automatic backup: today's, if it hasn't run yet (the daily job may not have had a chance).
    @Override protected void onResume(){super.onResume();if(!storageReadable)return;AutoBackup.schedule(this);if(prefs().getString("auto_backup_tree",null)!=null)new Thread(()->AutoBackup.run(getApplicationContext(),false)).start();}
    @Override protected void onSaveInstanceState(Bundle state){state.putString("tab",tab);state.putString("previousTab",previousTab);state.putString("search",search);state.putString("accountFilter",accountFilter);state.putString("month",month.toString());super.onSaveInstanceState(state);}
    private void options(View anchor){
        PopupMenu menu=new PopupMenu(this,anchor);menu.getMenu().add("Settings");menu.getMenu().add(hideAmounts?"Show amounts":"Hide amounts");menu.getMenu().add("Plan reset");
        menu.setOnMenuItemClickListener(item->{String t=item.getTitle().toString();
            if(t.equals("Plan reset"))planReset();
            else if(t.endsWith("amounts")){if(!getSharedPreferences("appearance",0).edit().putBoolean("hideAmounts",!hideAmounts).commit()){toast("Could not save that setting.");return true;}hideAmounts=!hideAmounts;render();}
            else{if(!tab.equals("Settings"))previousTab=tab;tab="Settings";render();}return true;});menu.show();
    }
    /** YNAB's Plan Reset: every category's money in this month goes back to Ready to Assign. The budget before is kept for Undo. */
    private void planReset(){
        if(!storageReadable)return;if(month.isAfter(YearMonth.now())){toast("Reset this month or an earlier one.");return;}
        long total=0;int n=0;for(Budget.Category c:budget.categories){long a=budget.available(c,month);if(a>0){total+=a;n++;}}
        if(n==0){toast("No category has money to return this month.");return;}
        String monthName=month.format(DateTimeFormatter.ofPattern("MMMM yyyy"));
        new AlertDialog.Builder(this).setTitle("Plan reset").setMessage("Return "+money(total)+" from "+count(n,"category","categories")+" to Ready to Assign in "+monthName+", then assign it again by today's priorities?\n\nTargets and transactions stay. You can undo this on Plan.")
            .setNegativeButton("Cancel",null).setPositiveButton("Reset",(d,w)->{String before=prefs().getString("data",null);
                if(change(()->budget.planReset(month))){if(!prefs().edit().putString("before_reset",before==null?"":before).putString("before_reset_at",LocalDateTime.now().withNano(0).toString()).commit())toast("Reset done, but Undo couldn't be saved.");else{tab="Plan";render();}}}).show();
    }
    private void undoPlanReset(){
        new AlertDialog.Builder(this).setTitle("Undo plan reset?").setMessage("Puts back the plan you had before the reset on "+when(prefs().getString("before_reset_at",""))+". Changes made since are lost.").setNegativeButton("Cancel",null).setPositiveButton("Undo reset",(d,w)->{
            String before=prefs().getString("before_reset",null);if(before==null){render();return;}
            Budget previous;try{previous=before.isEmpty()?null:BudgetStore.decode(before);}catch(Exception e){toast("The plan from before the reset can't be read. Nothing was changed.");return;}
            android.content.SharedPreferences.Editor edit=prefs().edit().remove("before_reset").remove("before_reset_at");if(previous==null)edit.remove("data");else edit.putString("data",before);
            if(!edit.commit()){toast("Could not save to device storage.");return;}
            if(previous==null){budget=new Budget();load();}else budget=previous;render();toast("Plan reset undone.");}).show();
    }
    private void closeSettings(){tab=previousTab;render();}
    @Override public void onBackPressed(){if(tab.equals("Settings"))closeSettings();else super.onBackPressed();}
    private void settings(){
        content.addView(label("Appearance",18,blue,true));LinearLayout appearance=card();
        appearance.addView(label("Theme",20,ink,true));appearance.addView(label("Choose Light, Dark, or follow your device automatically.",14,muted,false));
        appearance.addView(button("Theme: "+themeMode,this::chooseTheme));
        content.addView(label("Backup",18,blue,true));LinearLayout backup=card();
        backup.addView(label("Back up and restore",20,ink,true));backup.addView(label("Your budget is saved only on this device. Save a backup file somewhere safe, such as Drive or a computer, to restore it after a reinstall or on a new phone.",14,muted,false));
        backup.addView(button("Back up budget",()->pick(new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("application/json").putExtra(Intent.EXTRA_TITLE,"MyBudget-backup-"+LocalDate.now()+".json"),BACKUP)));
        backup.addView(button("Restore from backup",()->pick(new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*"),RESTORE)));
        String restoredAt=prefs().getString("before_restore_at",null);
        if(restoredAt!=null){backup.addView(label("Restored from a backup on "+when(restoredAt)+". Undo puts back the budget you had before.",13,muted,false));backup.addView(button("Undo restore",this::undoRestore));}
        int photos=photoCount();if(photos>0)backup.addView(label(count(photos,"photo stays","photos stay")+" on this phone: backups hold the budget, not photos.",13,muted,false));
        // Automatic backup to a folder picked once (Drive's folder works too, through the system picker).
        LinearLayout auto=card();auto.addView(label("Automatic backup",20,ink,true));String tree=prefs().getString("auto_backup_tree",null);
        if(tree==null){auto.addView(label("Once a day, MyBudget can save a backup to a folder you choose, keeping the last 7.",14,muted,false));auto.addView(button("Choose a folder and turn on",()->{Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);try{startActivityForResult(i,AUTO);}catch(android.content.ActivityNotFoundException e){toast("No app on this device can choose a folder.");}}));}
        else{String last=prefs().getString("auto_backup_last",null),error=prefs().getString("auto_backup_error",null);
            auto.addView(label("On: a backup a day to "+folderName(tree)+", keeping the last 7."+(last==null?"":" Last: "+pretty(last)+"."),14,muted,false));if(error!=null)auto.addView(label(error,13,red,true));
            auto.addView(button("Back up now",()->new Thread(()->{String e=AutoBackup.run(this,true);runOnUiThread(()->{toast(e==null?"Backed up to "+folderName(tree)+".":e);if(tab.equals("Settings"))render();});}).start()));
            auto.addView(button("Turn off",()->{try{getContentResolver().releasePersistableUriPermission(Uri.parse(tree),Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION);}catch(Exception ignored){}prefs().edit().remove("auto_backup_tree").remove("auto_backup_last").remove("auto_backup_error").apply();AutoBackup.schedule(this);render();toast("Automatic backup is off. Backups already saved stay in the folder.");}));}
        content.addView(label("Import",18,blue,true));LinearLayout imports=card();imports.addView(label("Import a bank statement",20,ink,true));
        imports.addView(label("Pick a CSV from your bank and match its columns once. Rows already in the account are skipped; new payees go to "+CsvImport.TO_CATEGORIZE+" until you choose their category.",14,muted,false));
        imports.addView(button("Import transactions (CSV)",()->pick(new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*"),IMPORT)));
        LinearLayout export=card();export.addView(label("Export transactions",20,ink,true));export.addView(label("A CSV file of every transaction for a spreadsheet. It can't be restored; use a backup for that.",14,muted,false));
        export.addView(button("Export transactions (CSV)",()->pick(new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("text/csv").putExtra(Intent.EXTRA_TITLE,"MyBudget-transactions-"+LocalDate.now()+".csv"),EXPORT)));
    }
    // Backup, restore and export go through Android's file picker, so MyBudget needs no storage permission.
    private static final int BACKUP=1,RESTORE=2,EXPORT=3,IMPORT=4,AUTO=5,PHOTO=6;
    private String folderName(String tree){try{String id=android.provider.DocumentsContract.getTreeDocumentId(Uri.parse(tree));int c=id.lastIndexOf(':');String n=c>=0?id.substring(c+1):id;return n.isEmpty()?"the folder you chose":n;}catch(Exception e){return "the folder you chose";}}
    private android.content.SharedPreferences prefs(){return getSharedPreferences("budget",0);}
    private String when(String iso){try{return LocalDateTime.parse(iso).format(DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a",Locale.forLanguageTag("en-AU")));}catch(Exception e){return "an unknown date";}}
    private void pick(Intent intent,int request){intent.addCategory(Intent.CATEGORY_OPENABLE);try{startActivityForResult(intent,request);}catch(android.content.ActivityNotFoundException e){toast("No app on this device can save or open files.");}}
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);Uri uri=data==null?null:data.getData();if(request==PHOTO){pickingPhoto=false;java.util.function.Consumer<Uri> target=photoTarget;photoTarget=null;if(result==RESULT_OK&&uri!=null&&target!=null)target.accept(uri);return;}if(result!=RESULT_OK||uri==null||!storageReadable)return;
        if(request==AUTO){try{getContentResolver().takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION);}catch(Exception e){toast("MyBudget couldn't keep access to that folder. Choose another.");return;}prefs().edit().putString("auto_backup_tree",uri.toString()).remove("auto_backup_last").remove("auto_backup_error").apply();AutoBackup.schedule(this);String tree=uri.toString();new Thread(()->{String e=AutoBackup.run(this,true);runOnUiThread(()->{toast(e==null?"Automatic backup is on. First backup saved.":e);render();});}).start();return;}
        if(request==IMPORT){List<List<String>> rows;try{rows=CsvImport.parse(read(uri));}catch(Exception e){toast(e instanceof IOException&&e.getMessage()!=null?e.getMessage():"Could not read that file.");return;}if(rows.isEmpty()){toast("That file has no rows.");return;}importDialog(rows);return;}
        if(request==RESTORE){BudgetStore.Backup backup;try{backup=BudgetStore.readBackup(read(uri));}catch(Exception e){String m=e.getMessage();toast((e instanceof org.json.JSONException||e instanceof IOException)&&m!=null?m:"Could not read that file.");return;}confirmRestore(backup);return;}
        try{write(uri,request==BACKUP?BudgetStore.backup(budget,LocalDateTime.now()):"﻿"+budget.csv());toast(request==BACKUP?(photoCount()>0?"Budget backed up. Photos stay on this phone.":"Budget backed up."):"Transactions exported.");}
        catch(Exception e){try{android.provider.DocumentsContract.deleteDocument(getContentResolver(),uri);}catch(Exception ignored){}toast(request==BACKUP?"Could not save the backup.":"Could not save the export.");}
    }
    /** Matches a statement's columns (remembered by header name for next time), then imports into one account. */
    private void importDialog(List<List<String>> rows){
        List<Budget.Account> accounts=openAccounts();if(accounts.isEmpty()){toast("Add an account first.");return;}
        List<String> first=rows.get(0);int columns=0;for(List<String> r:rows)columns=Math.max(columns,r.size());boolean header=CsvImport.looksLikeHeader(first);
        String[] names=new String[columns],withNone=new String[columns+1];withNone[0]="None: one signed amount column";
        for(int i=0;i<columns;i++){String sample=rows.size()>(header?1:0)&&i<rows.get(header?1:0).size()?rows.get(header?1:0).get(i):"";names[i]=(header&&i<first.size()&&!first.get(i).isEmpty()?first.get(i):"Column "+(i+1))+(sample.isEmpty()?"":"  (e.g. "+(sample.length()>24?sample.substring(0,24)+"…":sample)+")");withNone[i+1]=names[i];}
        // Guess from header words, or from what was used last time with the same headers.
        int date=guess(first,header,new String[]{"date"},0),payee=guess(first,header,new String[]{"description","payee","narrative","details","merchant","memo"},Math.min(1,columns-1)),amount=guess(first,header,new String[]{"amount","credit"},Math.min(2,columns-1)),out=-1;
        if(header){int debit=guess(first,true,new String[]{"debit","out","withdrawal"},-1);if(debit>=0&&debit!=amount)out=debit;}
        String saved=prefs().getString("import_columns",null);if(saved!=null&&header){String[] p=saved.split("\u0001");if(p.length==5&&p[0].equals(String.join("\u0002",first))){date=Integer.parseInt(p[1]);payee=Integer.parseInt(p[2]);amount=Integer.parseInt(p[3]);out=Integer.parseInt(p[4]);}}
        LinearLayout f=form();f.addView(label(count(rows.size()-(header?1:0),"row","rows")+" in the file.",14,ink,true));CheckBox hasHeader=new CheckBox(this);hasHeader.setText("The first row is column names");hasHeader.setChecked(header);hasHeader.setMinHeight(dp(48));f.addView(hasHeader);
        Spinner dateCol=spinner(f,"Date",names,date),payeeCol=spinner(f,"Payee or description",names,payee),amountCol=spinner(f,"Amount (or money in)",names,amount),outCol=spinner(f,"Money out (if it's a separate column)",withNone,out+1),account=spinner(f,"Into account",accounts.stream().map(a->a.name).toArray(String[]::new),0);
        f.addView(label("Imported rows are marked cleared. Dates in the future or before the account opened are skipped.",12,muted,false));
        ScrollView scroll=new ScrollView(this);scroll.addView(f);AlertDialog d=new AlertDialog.Builder(this).setTitle("Import transactions").setView(scroll).setNegativeButton("Cancel",null).setPositiveButton("Import",null).create();
        d.setOnShowListener(v->d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(w->{
            boolean h=hasHeader.isChecked();int dc=dateCol.getSelectedItemPosition(),pc=payeeCol.getSelectedItemPosition(),ac=amountCol.getSelectedItemPosition(),oc=outCol.getSelectedItemPosition()-1;
            String format=CsvImport.detectDateFormat(rows,dc,h);if(format==null){toast("MyBudget can't read the dates in that column. Check the Date column.");return;}
            Budget.Account a=accounts.get(account.getSelectedItemPosition());CsvImport.Result[] r=new CsvImport.Result[1];
            if(!change(()->r[0]=CsvImport.run(budget,rows,h,dc,pc,ac,oc,format,accountById(a.id))))return;
            if(h)prefs().edit().putString("import_columns",String.join("\u0002",first)+"\u0001"+dc+"\u0001"+pc+"\u0001"+ac+"\u0001"+oc).apply();
            d.dismiss();CsvImport.Result x=r[0];List<String> skipped=new ArrayList<>();if(x.duplicates>0)skipped.add(x.duplicates+" already there");if(x.future>0)skipped.add(x.future+" in the future");if(x.beforeOpening>0)skipped.add(x.beforeOpening+" before the account opened");if(x.unreadable>0)skipped.add(x.unreadable+" unreadable");
            boolean sorting=false;for(Budget.Entry e:x.entries){Budget.Category c=budget.category(e.category);if(c!=null&&c.name.equals(CsvImport.TO_CATEGORIZE))sorting=true;}
            new AlertDialog.Builder(this).setTitle(count(x.added,"transaction","transactions")+" imported").setMessage((skipped.isEmpty()?"Nothing was skipped.":"Skipped: "+String.join(", ",skipped)+".")+(sorting?"\n\nSome are in "+CsvImport.TO_CATEGORIZE+": open them in Spending to choose their category.":"")).setPositiveButton("OK",null).show();
            if(sorting){tab="Spending";search=CsvImport.TO_CATEGORIZE;accountFilter="";render();}
        }));d.show();
    }
    // Photos: JPEGs in files/photos, at most 1600 px on the long side, turned upright from the camera's EXIF.
    private File photoDir(){File d=new File(getFilesDir(),"photos");d.mkdirs();return d;}
    private String copyPhoto(Uri uri)throws IOException{
        BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;try(InputStream in=getContentResolver().openInputStream(uri)){BitmapFactory.decodeStream(in,null,bounds);}
        if(bounds.outWidth<=0)throw new IOException("Not an image.");int sample=1;while(Math.max(bounds.outWidth,bounds.outHeight)/(sample*2)>=1600)sample*=2;
        BitmapFactory.Options o=new BitmapFactory.Options();o.inSampleSize=sample;android.graphics.Bitmap bm;try(InputStream in=getContentResolver().openInputStream(uri)){bm=BitmapFactory.decodeStream(in,null,o);}if(bm==null)throw new IOException("Not an image.");
        int rotate=0;try(InputStream in=getContentResolver().openInputStream(uri)){if(in!=null){int t=new android.media.ExifInterface(in).getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION,1);rotate=t==6?90:t==3?180:t==8?270:0;}}catch(Exception ignored){}
        float scale=Math.min(1f,1600f/Math.max(bm.getWidth(),bm.getHeight()));android.graphics.Matrix m=new android.graphics.Matrix();m.postScale(scale,scale);m.postRotate(rotate);android.graphics.Bitmap out=android.graphics.Bitmap.createBitmap(bm,0,0,bm.getWidth(),bm.getHeight(),m,true);
        String name=UUID.randomUUID()+".jpg";try(OutputStream os=new FileOutputStream(new File(photoDir(),name))){if(!out.compress(android.graphics.Bitmap.CompressFormat.JPEG,85,os))throw new IOException("Could not save the photo.");}return name;
    }
    private android.graphics.Bitmap photoBitmap(String name,int max){File file=new File(photoDir(),name);if(name.isEmpty()||!file.isFile())return null;BitmapFactory.Options b=new BitmapFactory.Options();b.inJustDecodeBounds=true;BitmapFactory.decodeFile(file.getPath(),b);int s=1;while(Math.max(b.outWidth,b.outHeight)/(s*2)>=max)s*=2;BitmapFactory.Options o=new BitmapFactory.Options();o.inSampleSize=s;return BitmapFactory.decodeFile(file.getPath(),o);}
    private void viewPhoto(String name){android.graphics.Bitmap bm=photoBitmap(name,1600);if(bm==null){toast("The photo isn't on this phone.");return;}ImageView img=new ImageView(this);img.setImageBitmap(bm);img.setAdjustViewBounds(true);img.setContentDescription("Photo of this transaction");new AlertDialog.Builder(this).setView(img).setPositiveButton("Close",null).show();}
    private int photoCount(){int n=0;for(Budget.Entry e:budget.entries)if(!e.photo.isEmpty())n++;return n;}
    /** Deletes photo files no transaction uses (a form cancelled after adding one, a deleted transaction); keeps those Undo restore / Undo plan reset could bring back. */
    private void cleanupPhotos(){Set<String> used=new HashSet<>();for(Budget.Entry e:budget.entries)used.add(e.photo);for(String key:new String[]{"before_restore","before_reset"}){java.util.regex.Matcher m=java.util.regex.Pattern.compile("\"photo\":\"([^\"]+)\"").matcher(prefs().getString(key,""));while(m.find())used.add(m.group(1));}File[] files=new File(getDataDir(),"files/photos").listFiles(); /* listing only: getFilesDir() would create the folder */if(files!=null)for(File file:files)if(!used.contains(file.getName()))file.delete();}
    private static int guess(List<String> header,boolean has,String[] words,int fallback){if(has)for(String w:words)for(int i=0;i<header.size();i++)if(header.get(i).toLowerCase(Locale.ROOT).contains(w))return i;return fallback;}
    private void write(Uri uri,String text)throws IOException{
        OutputStream out;try{out=getContentResolver().openOutputStream(uri,"wt");}catch(FileNotFoundException|IllegalArgumentException|UnsupportedOperationException e){out=getContentResolver().openOutputStream(uri,"w");}
        if(out==null)throw new IOException();try(OutputStream o=out){o.write(text.getBytes(StandardCharsets.UTF_8));}
    }
    private String read(Uri uri)throws IOException{
        try(InputStream in=getContentResolver().openInputStream(uri)){if(in==null)throw new IOException();ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buffer=new byte[8192];int n;
            while((n=in.read(buffer))>0){out.write(buffer,0,n);if(out.size()>10_000_000)throw new IOException("This file is too large to be a MyBudget backup.");}
            String text=new String(out.toByteArray(),StandardCharsets.UTF_8);return text.startsWith("﻿")?text.substring(1):text;}
    }
    private static String count(int n,String one,String many){return n+" "+(n==1?one:many);}
    private void confirmRestore(BudgetStore.Backup backup){
        Budget b=backup.budget;int missing=0;for(Budget.Entry e:b.entries)if(!e.photo.isEmpty()&&!new File(photoDir(),e.photo).isFile()){e.photo="";missing++;} // photos aren't in backups
        new AlertDialog.Builder(this).setTitle("Restore this backup?").setMessage("Backup made "+when(backup.created)+"\n\n"+count(b.accounts.size(),"account","accounts")+", "+count(b.categories.size(),"category","categories")+", "+count(b.entries.size(),"transaction","transactions")+"."+(missing>0?" "+count(missing,"photo isn't","photos aren't")+" on this phone (backups don't hold photos).":"")+"\n\nThis replaces the budget on this device. You can undo it afterwards in Settings.")
            .setNegativeButton("Cancel",null).setPositiveButton("Restore",(d,w)->{
                // The budget being replaced is kept (empty: there was none) for Undo restore.
                String current=prefs().getString("data",null);
                try{if(!prefs().edit().putString("data",BudgetStore.encode(b)).putString("before_restore",current==null?"":current).putString("before_restore_at",LocalDateTime.now().withNano(0).toString()).remove("before_reset").remove("before_reset_at").commit())throw new IllegalStateException();}
                catch(Exception e){toast("Could not save the restored budget. Nothing was changed.");return;}
                budget=b;for(AlertDialog editor:new ArrayList<>(editors))editor.dismiss();render();toast("Budget restored.");
            }).show();
    }
    private void undoRestore(){
        new AlertDialog.Builder(this).setTitle("Undo restore?").setMessage("Puts back the budget you had before restoring on "+when(prefs().getString("before_restore_at",""))+". Changes made since the restore are lost.")
            .setNegativeButton("Cancel",null).setPositiveButton("Undo restore",(d,w)->{
                String before=prefs().getString("before_restore",null);if(before==null){render();return;}
                Budget previous;try{previous=before.isEmpty()?null:BudgetStore.decode(before);}catch(Exception e){toast("The budget from before the restore can't be read. Nothing was changed.");return;}
                android.content.SharedPreferences.Editor edit=prefs().edit().remove("before_restore").remove("before_restore_at").remove("before_reset").remove("before_reset_at");if(previous==null)edit.remove("data");else edit.putString("data",before);
                if(!edit.commit()){toast("Could not save to device storage.");return;}
                if(previous==null){budget=new Budget();load();}else budget=previous;for(AlertDialog editor:new ArrayList<>(editors))editor.dismiss();render();toast("Restore undone.");
            }).show();
    }
    private void chooseTheme(){
        String[] modes={"Light","Dark","Auto"};int selected=Arrays.asList(modes).indexOf(themeMode);
        new AlertDialog.Builder(this).setTitle("Appearance").setSingleChoiceItems(new String[]{"Light","Dark","Auto (follow device)"},selected,(dialog,which)->{
            String chosen=modes[which];if(chosen.equals(themeMode)){dialog.dismiss();return;}
            if(!getSharedPreferences("appearance",0).edit().putString("theme",chosen).commit()){toast("Could not save your theme preference.");return;}
            dialog.dismiss();recreate();
        }).setNegativeButton("Cancel",null).show();
    }
    private int dp(int n){return(int)(n*getResources().getDisplayMetrics().density);}
    // Hide amounts (⋮ menu) shows dots instead of money everywhere on screen, for showing the plan to someone.
    private boolean hideAmounts,darkTheme;
    private String money(long cents){return hideAmounts?"$•••":currency.format(java.math.BigDecimal.valueOf(cents,2));}
    private String decimal(long cents){return java.math.BigDecimal.valueOf(cents,2).toPlainString();}
    private LinearLayout column(){LinearLayout v=new LinearLayout(this);v.setOrientation(LinearLayout.VERTICAL);return v;}
    private TextView label(String text,int size,int color,boolean bold){TextView v=new TextView(this);v.setText(text);v.setTextSize(size);v.setTextColor(color);v.setPadding(0,dp(4),0,dp(4));if(bold)v.setTypeface(null,Typeface.BOLD);return v;}
    private GradientDrawable bg(int color){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(16));return d;}
    private LinearLayout card(){LinearLayout v=column();v.setPadding(dp(14),dp(10),dp(14),dp(10));v.setBackground(bg(surface));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,dp(4),0,dp(4));content.addView(v,p);return v;}
    private Button button(String text,Runnable action){Button b=new Button(this);b.setText(text);b.setAllCaps(false);b.setTextSize(13);b.setTextColor(blue);b.setBackground(bg(buttonSurface));b.setStateListAnimator(null);b.setElevation(0);b.setMinHeight(dp(48));b.setMinimumHeight(dp(48));b.setMinWidth(0);b.setMinimumWidth(0);b.setPadding(dp(12),dp(8),dp(12),dp(8));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,dp(4),0,dp(4));b.setLayoutParams(p);b.setOnClickListener(v->action.run());return b;}
    private void progress(LinearLayout parent,long funded,long goal,int color){ProgressBar bar=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);bar.setMax(100);bar.setProgress((int)Math.max(0,Math.min(100,funded*100/Math.max(1,goal))));bar.setProgressTintList(ColorStateList.valueOf(color));bar.setProgressBackgroundTintList(ColorStateList.valueOf(buttonSurface));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(6));p.setMargins(0,dp(5),0,dp(5));parent.addView(bar,p);}
    static String ordinal(int d){return d+(d%100>=11&&d%100<=13?"th":d%10==1?"st":d%10==2?"nd":d%10==3?"rd":"th");}
    private String targetDescription(Budget.Category c){String by=c.dueDay>0?" by the "+ordinal(c.dueDay):"";if(c.targetType.equals("Monthly"))return "Set aside "+money(c.target)+by+" each month";if(c.targetType.equals("Balance"))return "Save to "+money(c.target)+(c.due.isEmpty()?"":" by "+YearMonth.parse(c.due).format(DateTimeFormatter.ofPattern("MMM yyyy")));return "Refill to "+money(c.target)+by+" each month";}
    private void render(){
        root=column();root.setBackgroundColor(canvas);root.setPadding(dp(16),dp(12),dp(16),dp(8));setContentView(root);
        root.setOnApplyWindowInsetsListener((v,i)->{root.setPadding(dp(16),i.getSystemWindowInsetTop()+dp(8),dp(16),i.getSystemWindowInsetBottom()+dp(4));return i;});
        LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);
        ImageView logo=new ImageView(this);logo.setImageResource(R.drawable.brand_mark);logo.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);LinearLayout.LayoutParams logoSize=new LinearLayout.LayoutParams(dp(40),dp(40));logoSize.setMargins(0,0,dp(12),0);header.addView(logo,logoSize);
        LinearLayout brand=column();TextView wordmark=label("MyBudget",21,ink,true);wordmark.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));wordmark.setLetterSpacing(0.01f);wordmark.setPadding(0,0,0,0);
        SpannableString brandName=new SpannableString("MyBudget");brandName.setSpan(new android.text.style.ForegroundColorSpan(blue),0,2,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);wordmark.setText(brandName);brand.addView(wordmark);
        TextView tagline=label("Your money. Your plan.",11,muted,false);tagline.setPadding(0,dp(2),0,0);brand.addView(tagline);header.addView(brand,new LinearLayout.LayoutParams(0,-2,1));
        Button overflow=button("\u22ee",()->{});overflow.setContentDescription("More options");overflow.setTextSize(26);overflow.setMinWidth(0);overflow.setMinimumWidth(0);overflow.setPadding(0,0,0,0);overflow.setBackground(bg(Color.TRANSPARENT));overflow.setOnClickListener(v->options(v));header.addView(overflow,new LinearLayout.LayoutParams(dp(48),dp(48)));root.addView(header);root.addView(label(tab,28,ink,true));
        LinearLayout months=new LinearLayout(this);months.setGravity(Gravity.CENTER_VERTICAL);Button previous=button("\u2039",()->{month=month.minusMonths(1);render();});previous.setTextSize(26);previous.setContentDescription("Previous month");previous.setBackground(bg(Color.TRANSPARENT));months.addView(previous,new LinearLayout.LayoutParams(dp(48),dp(48)));TextView title=label(month.format(DateTimeFormatter.ofPattern("MMMM yyyy")),16,ink,true);title.setGravity(Gravity.CENTER);months.addView(title,new LinearLayout.LayoutParams(0,-2,1));Button next=button("\u203a",()->{month=month.plusMonths(1);render();});next.setTextSize(26);next.setContentDescription("Next month");next.setBackground(bg(Color.TRANSPARENT));months.addView(next,new LinearLayout.LayoutParams(dp(48),dp(48)));if(!tab.equals("Settings"))root.addView(months);
        ScrollView scroll=new ScrollView(this);content=column();scroll.addView(content);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        switch(tab){case"Settings":settings();break;case"Home":home();break;case"Plan":plan();break;case"Spending":spending();break;case"Accounts":accounts();break;default:reflect();}
        if(tab.equals("Settings")){root.addView(button("Back",this::closeSettings));return;}
        LinearLayout nav=new LinearLayout(this);nav.setPadding(0,dp(6),0,0);String[] names={"Home","Plan","Spending","Accounts","Reflect"};int[] icons={R.drawable.nav_home,R.drawable.nav_plan,R.drawable.nav_spending,R.drawable.nav_accounts,R.drawable.nav_reflect};for(int i=0;i<names.length;i++){String name=names[i];boolean selected=tab.equals(name);Button b=button(name,()->{tab=name;accountFilter="";render();});b.setTextSize(10);b.setPadding(dp(2),dp(7),dp(2),dp(5));b.setBackground(bg(selected?primary:Color.TRANSPARENT));b.setTextColor(selected?Color.WHITE:muted);b.setSelected(selected);b.setContentDescription(name+(selected?", selected":""));if(selected)b.setTypeface(null,Typeface.BOLD);Drawable icon=getDrawable(icons[i]).mutate();icon.setTint(selected?Color.WHITE:muted);icon.setBounds(0,0,dp(20),dp(20));b.setCompoundDrawables(null,icon,null,null);b.setCompoundDrawablePadding(dp(4));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(60),1);p.setMargins(dp(1),0,dp(1),0);nav.addView(b,p);}root.addView(nav);
    }
    private void readyCard(){LinearLayout c=card();c.setBackground(bg(primary));c.addView(label("READY TO ASSIGN",11,Color.WHITE,true));c.addView(label(money(budget.spendable(month)),30,Color.WHITE,true));long future=budget.futureAssigned(month);c.addView(label(future>0?money(future)+" reserved in future months":"Give the money you have a purpose.",12,Color.WHITE,false));}
    private int overspent(){int n=0;for(Budget.Category c:budget.categories)if(budget.available(c,month)<0)n++;return n;}
    private void home(){
        readyCard();content.addView(button("+ Add transaction",()->transaction(null)));
        int due=budget.due(LocalDate.now()).size();if(due>0){LinearLayout c=card();c.addView(label(count(due,"upcoming transaction is","upcoming transactions are")+" due",19,amber,true));c.addView(label("Enter them so your plan matches your accounts, or skip any that didn't happen.",14,muted,false));c.addView(button("Review",()->{tab="Spending";render();}));}
        if(budget.accounts.isEmpty()){LinearLayout c=card();c.addView(label("Start with the money you have",21,ink,true));c.addView(label("Add your bank, savings or cash account and its current balance. Then assign that money in your plan.",15,muted,false));c.addView(button("Add your first account",this::addAccount));}
        if(overspent()>0){LinearLayout c=card();c.addView(label("Cover "+count(overspent(),"overspent category","overspent categories"),19,red,true));c.addView(label("Move money to cover spending before trusting other available balances.",14,muted,false));c.addView(button("Review plan",()->{tab="Plan";render();}));}
        long need=0;for(Budget.Category c:budget.categories)if(!c.hidden)need+=budget.fundNeed(c,month);
        LinearLayout progress=card();progress.addView(label("Your funding progress",19,ink,true));progress.addView(label(money(need)+" still needed this month",16,need>0?amber:green,true));progress.addView(label("Targets tell you what to fund. They do not create money.",14,muted,false));
        content.addView(label("Your priorities",20,ink,true));int count=0;for(Budget.Category c:budget.categories)if(c.target>0&&!c.hidden){count++;categoryCard(c);}if(count==0)content.addView(label("Add targets in Plan for bills, everyday spending and future goals.",15,muted,false));
    }
    private void categoryCard(Budget.Category c){
        LinearLayout row=card();long available=budget.available(c,month),need=budget.needed(c,month);int status=available<0?red:need>0?amber:green;
        LinearLayout heading=new LinearLayout(this);heading.setGravity(Gravity.CENTER_VERTICAL);TextView name=label(c.name,16,ink,true);name.setPadding(0,0,dp(8),0);heading.addView(name,new LinearLayout.LayoutParams(0,-2,1));LinearLayout balance=column();TextView caption=label("Available",10,muted,false);caption.setGravity(Gravity.END);caption.setPadding(0,0,0,0);balance.addView(caption);TextView value=label(money(available),20,status,true);value.setGravity(Gravity.END);value.setPadding(0,0,0,0);value.setAutoSizeTextTypeUniformWithConfiguration(12,20,1,android.util.TypedValue.COMPLEX_UNIT_SP);balance.addView(value,new LinearLayout.LayoutParams(-1,dp(27)));heading.addView(balance,new LinearLayout.LayoutParams(dp(128),-2));row.addView(heading);
        LinearLayout details=new LinearLayout(this);TextView assigned=label("Assigned  "+money(budget.assigned(c,month)),11,muted,false),activity=label("Activity  "+money(budget.activity(c,month)),11,muted,false);details.addView(assigned,new LinearLayout.LayoutParams(0,-2,1));activity.setGravity(Gravity.END);details.addView(activity,new LinearLayout.LayoutParams(0,-2,1));row.addView(details);
        if(c.target>0){long base=c.targetType.equals("Monthly")?budget.assigned(c,month):c.targetType.equals("Balance")?available:c.target-need;progress(row,base,c.target,status);row.addView(label(targetDescription(c),11,muted,false));row.addView(c.snoozed.equals(month.toString())?label("Target snoozed this month",12,muted,true):label(need==0?"Funded for this month":money(need)+" left to fund this month",12,need>0?amber:green,true));}
        if(!c.note.isEmpty())row.addView(label(c.note,12,muted,false));
        long upcoming=budget.upcoming(c,month);if(upcoming>0)row.addView(label("Upcoming bills this month: "+money(upcoming),12,muted,false));
        long onCredit=budget.creditOverspent(c,month);
        if(available<0&&onCredit>=-available)row.addView(label("Overspent on a credit card by "+money(-available)+": it becomes card debt unless you cover it",12,amber,true));
        else if(available<0)row.addView(label("Overspent by "+money(-available)+" - tap to cover",12,red,true));
        if(c.payment()){Budget.Account card=budget.account(c.cardAccount);if(card!=null){long owed=-budget.balance(card,false);row.addView(label("Pays "+card.name+(owed>0?" · owed "+money(owed):" · paid off"),12,muted,false));}}
        row.setOnClickListener(v->categoryDetails(c));
    }
    private void plan(){
        readyCard();
        String resetAt=prefs().getString("before_reset_at",null);
        if(resetAt!=null){LinearLayout r=card();r.addView(label("Plan reset on "+when(resetAt)+". Assign your money again by today's priorities.",13,muted,false));LinearLayout buttons=new LinearLayout(this);Button undo=button("Undo plan reset",this::undoPlanReset),keep=button("Keep",()->{prefs().edit().remove("before_reset").remove("before_reset_at").apply();render();});buttons.addView(undo,new LinearLayout.LayoutParams(0,-2,2));LinearLayout.LayoutParams kp=new LinearLayout.LayoutParams(0,-2,1);kp.setMargins(dp(8),0,0,0);buttons.addView(keep,kp);r.addView(buttons);}
        if(overspent()>0)content.addView(label(count(overspent(),"category","categories")+" overspent - tap to cover",14,red,true));
        content.addView(button("Fund targets",this::autoAssign));LinkedHashSet<String> groups=new LinkedHashSet<>();for(Budget.Category c:budget.categories)if(!c.hidden)groups.add(c.group);
        for(String group:groups){content.addView(label(group,18,blue,true));for(Budget.Category c:budget.categories)if(!c.hidden&&c.group.equals(group))categoryCard(c);}
        content.addView(button("+ Add category",()->editCategory(null)));content.addView(button("Move money",this::move));
        List<Budget.Category> hidden=new ArrayList<>();long held=0;for(Budget.Category c:budget.categories)if(c.hidden){hidden.add(c);held+=budget.available(c,month);}
        if(!hidden.isEmpty()){content.addView(button((showHidden?"Collapse hidden categories (":"Show hidden categories (")+hidden.size()+")"+(held!=0?" · holds "+money(held):""),()->{showHidden=!showHidden;render();}));if(showHidden)for(Budget.Category c:hidden)categoryCard(c);}
    }
    private void categoryDetails(Budget.Category c){
        List<String> names=new ArrayList<>();List<Runnable> actions=new ArrayList<>();String id=c.id;
        if(budget.available(c,month)<0){names.add("Cover overspending");actions.add(()->cover(id));}
        names.add("Assign or return money");actions.add(()->assign(c));names.add("Move money");actions.add(this::move);names.add("Edit category and target");actions.add(()->editCategory(c));
        names.add("View transactions");actions.add(()->{tab="Spending";search=c.name;accountFilter="";render();});
        names.add("Move up");actions.add(()->reorder(id,-1));names.add("Move down");actions.add(()->reorder(id,1));
        if(c.target>0){boolean snoozed=c.snoozed.equals(month.toString());String m=month.toString();names.add(snoozed?"Unsnooze target":"Snooze target this month");actions.add(()->change(()->categoryById(id).snoozed=snoozed?"":m));}
        names.add(c.hidden?"Unhide":"Hide");actions.add(()->hide(id,!c.hidden));if(!c.payment()){names.add("Delete category");actions.add(()->deleteCategory(id));}
        new AlertDialog.Builder(this).setTitle(c.name).setItems(names.toArray(new String[0]),(d,n)->actions.get(n).run()).show();
    }
    // Changes from a menu: the category is looked up again by id, in case the budget was reloaded meanwhile.
    private boolean change(Runnable action){try{commit(action);render();return true;}catch(Exception e){toast(e.getMessage());return false;}}
    private Budget.Category categoryById(String id){Budget.Category c=budget.category(id);if(c==null)throw new IllegalArgumentException("That category no longer exists.");return c;}
    private void reorder(String id,int direction){change(()->{if(!budget.reorder(categoryById(id),direction))throw new IllegalArgumentException(direction<0?"Already first in its group.":"Already last in its group.");});}
    private void hide(String id,boolean hidden){if(change(()->categoryById(id).hidden=hidden))toast(hidden?"Hidden. It's at the bottom of Plan; its money still counts.":"Back in your plan.");}
    private void deleteCategory(String id){
        Budget.Category c=budget.category(id);if(c==null)return;
        if(!budget.used(c)){new AlertDialog.Builder(this).setTitle("Delete "+c.name+"?").setMessage("It has no transactions or assigned money.").setNegativeButton("Cancel",null).setPositiveButton("Delete",(d,w)->change(()->budget.deleteCategory(categoryById(id),null))).show();return;}
        List<Budget.Category> others=new ArrayList<>();for(Budget.Category o:budget.categories)if(o!=c&&!o.payment())others.add(o);
        if(others.isEmpty()){toast("Add another category first, to take its transactions and money.");return;}
        String[] labels=others.stream().map(o->o.name+(o.hidden?" (hidden)":"")).toArray(String[]::new);
        new AlertDialog.Builder(this).setTitle("Move "+c.name+" to…").setItems(labels,(d,n)->{String into=others.get(n).id;int count=budget.entriesIn(c);
            new AlertDialog.Builder(this).setTitle("Delete "+c.name+"?").setMessage("Its "+count(count,"transaction","transactions")+" and the money assigned to it in every month move to "+others.get(n).name+". Bills from Planner then suggest "+others.get(n).name+" too.")
                .setNegativeButton("Cancel",null).setPositiveButton("Move and delete",(d2,w)->change(()->budget.deleteCategory(categoryById(id),categoryById(into)))).show();}).show();
    }
    /** YNAB-style cover: pick where the money comes from (categories with money, or Ready to Assign). */
    private void cover(String id){
        Budget.Category c=budget.category(id);if(c==null)return;long missing=-budget.available(c,month);if(missing<=0)return;
        List<Budget.Category> sources=new ArrayList<>();for(Budget.Category o:budget.categories)if(o!=c&&budget.available(o,month)>0)sources.add(o);sources.sort((a,b)->Long.compare(budget.available(b,month),budget.available(a,month)));
        long ready=budget.spendable(month);List<String> labels=new ArrayList<>();if(ready>0)labels.add("Ready to Assign ("+money(ready)+")");for(Budget.Category o:sources)labels.add(o.name+" ("+money(budget.available(o,month))+")");
        if(labels.isEmpty()){toast("No category has money to move. Record income or assign money first.");return;}
        new AlertDialog.Builder(this).setTitle("Cover "+money(missing)+" for "+c.name).setItems(labels.toArray(new String[0]),(d,n)->{
            boolean fromReady=ready>0&&n==0;Budget.Category from=fromReady?null:sources.get(n-(ready>0?1:0));long amount=Math.min(missing,fromReady?ready:budget.available(from,month));String fromId=fromReady?null:from.id;
            new AlertDialog.Builder(this).setTitle("Cover overspending").setMessage("Move "+money(amount)+" from "+(fromReady?"Ready to Assign":from.name)+" to "+c.name+"?"+(amount<missing?"\n\nThat covers part of it; "+money(missing-amount)+" stays overspent.":""))
                .setNegativeButton("Cancel",null).setPositiveButton("Cover",(d2,w)->change(()->{if(fromId==null)budget.assign(categoryById(id),month,amount);else budget.move(categoryById(fromId),categoryById(id),month,amount);})).show();
        }).show();
    }
    /** Spending's Upcoming section: scheduled transactions by date; due ones first and marked. */
    private void upcomingList(){
        if(budget.scheduled.isEmpty())return;List<Budget.Scheduled> list=new ArrayList<>(budget.scheduled);list.sort(Comparator.comparing(s->s.next));
        content.addView(label("Upcoming",18,blue,true));
        for(Budget.Scheduled s:list){if(!accountFilter.isEmpty()&&!s.account.equals(accountFilter))continue;boolean isDue=!LocalDate.parse(s.next).isAfter(LocalDate.now());Budget.Category c=budget.category(s.category);Budget.Account a=budget.account(s.account);
            LinearLayout row=card();row.addView(label(s.payee,17,ink,true));row.addView(label((s.category.isEmpty()?"Ready to Assign":c==null?"":c.name)+" / "+(a==null?"":a.name)+" / "+pretty(s.next)+" · "+repeatLabel(s),12,muted,false));
            row.addView(label(money(s.amount),17,s.amount>0?green:ink,true));if(isDue)row.addView(label("Due - tap to enter or skip",12,amber,true));String id=s.id;row.setOnClickListener(v->dueActions(id));}
    }
    private void spending(){
        content.addView(button("+ Add transaction",()->transaction(null)));upcomingList();if(!accountFilter.isEmpty())content.addView(label("Account: "+budget.account(accountFilter).name,14,blue,true));
        EditText query=field(content,"Search payee, category or memo",false);query.setText(search);LinearLayout list=column();content.addView(list);fillEntries(list);
        query.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int f){}public void onTextChanged(CharSequence s,int a,int b,int c){search=s.toString();fillEntries(list);}public void afterTextChanged(Editable e){}});
    }
    // Money not given to a category goes to Ready to Assign (income and reconcile adjustments), as YNAB labels it.
    private String categoryName(Budget.Entry e){if(e.split()){StringBuilder s=new StringBuilder("Split:");for(Budget.Split p:e.splits){Budget.Category c=budget.category(p.category);s.append(" ").append(c==null?"Ready to Assign":c.name).append(",");}return s.substring(0,s.length()-1);}return e.transfer()?"Transfer":e.category.isEmpty()?"Ready to Assign":budget.category(e.category).name;}
    private void fillEntries(LinearLayout list){
        list.removeAllViews();List<Budget.Entry> ordered=new ArrayList<>(budget.entries);ordered.sort((a,b)->b.date.compareTo(a.date));int n=0;
        for(Budget.Entry e:ordered){String text=e.payee+" "+categoryName(e)+" "+e.memo+" "+budget.account(e.account).name;if(!e.date.startsWith(month.toString())||!text.toLowerCase(Locale.ROOT).contains(search.toLowerCase(Locale.ROOT))||(!accountFilter.isEmpty()&&!e.account.equals(accountFilter)&&!e.destination.equals(accountFilter)))continue;n++;LinearLayout row=column();row.setPadding(dp(14),dp(10),dp(14),dp(10));row.setBackground(bg(surface));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,dp(5),0,dp(5));list.addView(row,p);row.addView(label(e.payee,17,ink,true));row.addView(label(categoryName(e)+" / "+budget.account(e.account).name+" / "+pretty(e.date),12,muted,false));row.addView(label(money(e.amount)+(e.cleared?"  Cleared":"  Uncleared"),17,e.amount>0?green:ink,true));if(!e.memo.isEmpty())row.addView(label(e.memo,12,muted,false));if(!e.photo.isEmpty())row.addView(label("Photo attached",12,blue,false));row.setOnClickListener(v->transaction(e));}
        if(n==0)list.addView(label("No matching transactions this month.",15,muted,false));
    }
    private void accounts(){
        for(Budget.Account a:budget.accounts){if(a.closed)continue;LinearLayout c=card();c.addView(label(a.name+(a.credit()?" · credit card":""),20,ink,true));if(a.credit()){long owed=-budget.balance(a,false);Budget.Category p=budget.paymentCategory(a);long ready=p==null?0:budget.available(p,month);c.addView(label(owed>0?"Owed "+money(owed):owed<0?"In credit "+money(-owed):"Paid off",28,ink,true));c.addView(label("Set aside for the payment: "+money(ready)+(owed>ready&&owed>0?" ("+money(owed-ready)+" not covered yet)":""),13,owed>ready&&owed>0?amber:green,true));c.addView(button("Make a payment",()->payCard(a.id)));}else c.addView(label(money(budget.balance(a,false)),28,ink,true));c.addView(label("Cleared "+money(budget.balance(a,true))+" / Uncleared "+money(budget.balance(a,false)-budget.balance(a,true)),13,muted,false));if(!a.reconciled.isEmpty())c.addView(label("Last reconciled "+pretty(a.reconciled),12,green,false));c.addView(button("View transactions",()->{accountFilter=a.id;search="";tab="Spending";render();}));c.addView(button("Reconcile",()->reconcile(a)));c.addView(button("Edit account",()->editAccount(a.id)));}
        content.addView(button("+ Add account",this::addAccount));if(openAccounts().size()>1)content.addView(button("Transfer between accounts",this::transfer));
        boolean anyClosed=false;for(Budget.Account a:budget.accounts)if(a.closed){if(!anyClosed)content.addView(label("Closed accounts",18,blue,true));anyClosed=true;LinearLayout c=card();c.addView(label(a.name,17,muted,true));c.addView(label("Closed. Its transactions stay in your history.",13,muted,false));c.addView(button("View transactions",()->{accountFilter=a.id;search="";tab="Spending";render();}));c.addView(button("Reopen account",()->change(()->accountById(a.id).closed=false)));}content.addView(label("Checking, savings and cash accounts are pooled for your plan. Transfers change where money lives, not its purpose. Spending on a credit card moves the category's money to the card's payment category, ready to pay it.",14,muted,false));
    }
    private void reflect(){
        LinearLayout totals=card();totals.addView(label("This month's cash flow",20,ink,true));totals.addView(label("Income "+money(budget.income(month)),21,green,true));totals.addView(label("Spending "+money(budget.spending(month)),21,ink,true));totals.addView(label("Difference "+money(budget.income(month)-budget.spending(month)),17,blue,true));
        content.addView(label("Spending by category",20,ink,true));long total=budget.spending(month);for(Budget.Category c:budget.categories){long spent=Math.max(0,-budget.activity(c,month));if(spent==0)continue;LinearLayout r=card();r.addView(label(c.name+"  "+money(spent),16,ink,true));ProgressBar bar=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);bar.setProgress((int)(spent*100/Math.max(1,total)));r.addView(bar);}
        // Income vs spending, six months to this one: one axis from zero; the list below is the table view.
        LinearLayout flow=card();flow.addView(label("Income and spending",20,ink,true));
        int income=darkTheme?Color.parseColor("#3987E5"):Color.parseColor("#2A78D6"),spend=darkTheme?Color.parseColor("#D95926"):Color.parseColor("#EB6834");
        LinearLayout legend=new LinearLayout(this);legend.setGravity(Gravity.CENTER_VERTICAL);for(int k=0;k<2;k++){View swatch=new View(this);GradientDrawable s=new GradientDrawable();s.setColor(k==0?income:spend);s.setCornerRadius(dp(2));swatch.setBackground(s);LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(dp(10),dp(10));sp.setMargins(k==0?0:dp(14),0,dp(6),0);legend.addView(swatch,sp);legend.addView(label(k==0?"Income":"Spending",12,ink,false));}flow.addView(legend);
        long[] in=new long[6],out=new long[6];String[] names=new String[6];YearMonth[] ms=new YearMonth[6];for(int i=0;i<6;i++){YearMonth m=month.minusMonths(5-i);ms[i]=m;in[i]=budget.income(m);out[i]=budget.spending(m);names[i]=m.format(DateTimeFormatter.ofPattern("MMM"));}
        TextView picked=label("",13,ink,true);flow.addView(picked);java.util.function.IntConsumer show=i->picked.setText(ms[i].format(DateTimeFormatter.ofPattern("MMMM yyyy"))+":  in "+money(in[i])+"  ·  out "+money(out[i]));show.accept(5);
        CashFlowChart chart=new CashFlowChart(this,in,out,names,income,spend,muted,muted,buttonSurface,5,show);chart.setContentDescription("Income and spending chart for the last six months. The list below has the amounts.");flow.addView(chart,new LinearLayout.LayoutParams(-1,-2));
        // Net worth and Age of Money.
        LinearLayout worth=card();long now=budget.netWorth(month),change=now-budget.netWorth(month.minusMonths(1));worth.addView(label("Net worth",20,ink,true));worth.addView(label(money(now),26,ink,true));worth.addView(label((change>=0?"Up ":"Down ")+money(Math.abs(change))+" since the end of last month",13,muted,false));
        LinearLayout age=card();LocalDate until=month.isBefore(YearMonth.now())?month.atEndOfMonth():LocalDate.now();int days=budget.ageOfMoney(until);age.addView(label("Age of Money",20,ink,true));
        age.addView(label(days<0?"Not enough spending yet":count(days,"day","days"),26,days>=30?green:ink,true));age.addView(label("How old your money is when you spend it, over your last 10 outflows. 30 days or more means you're spending last month's income.",13,muted,false));
        content.addView(label("Last six months",20,ink,true));for(int i=5;i>=0;i--){YearMonth m=month.minusMonths(i);content.addView(label(m.format(DateTimeFormatter.ofPattern("MMM yyyy"))+"   In "+money(budget.income(m))+"   Out "+money(budget.spending(m)),13,muted,false));}
        content.addView(label("AUD / Saved on this device. Back up or export it in Settings. Bank sync is not included.",12,muted,false));
    }
    private LinearLayout form(){LinearLayout f=column();f.setPadding(dp(20),dp(8),dp(20),dp(8));return f;}
    private EditText field(LinearLayout f,String hint,boolean numeric){EditText e=new EditText(this);e.setHint(hint);e.setSingleLine(true);e.setTextColor(ink);if(numeric)e.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL|InputType.TYPE_NUMBER_FLAG_SIGNED);f.addView(e,new LinearLayout.LayoutParams(-1,-2));return e;}
    private Spinner spinner(LinearLayout f,String title,String[] names,int selection){f.addView(label(title,12,muted,true));Spinner s=new Spinner(this);s.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,names));if(names.length>0)s.setSelection(Math.max(0,selection));f.addView(s);return s;}
    private String required(EditText e){String s=e.getText().toString().trim();if(s.isEmpty())throw new IllegalArgumentException("Please enter a name.");return s;}
    private String date(EditText e){LocalDate d=LocalDate.parse((String)e.getTag());if(d.isAfter(LocalDate.now())||d.getYear()<1900)throw new IllegalArgumentException("Use a date between 1900 and today.");return d.toString();}
    static String pretty(String iso){try{return LocalDate.parse(iso).format(DateTimeFormatter.ofPattern("d MMM yyyy",Locale.forLanguageTag("en-AU")));}catch(Exception e){return iso;}}
    /** A date shown as "7 Oct 2026" that opens the date picker (1900 to today); the ISO date is kept in its tag. */
    private EditText dateField(LinearLayout f,String iso){return dateField(f,iso,false);}
    /** [future]: dates after today allowed (up to five years), for upcoming transactions. */
    private EditText dateField(LinearLayout f,String iso,boolean future){
        EditText e=new EditText(this);e.setTag(iso);e.setText(pretty(iso));e.setTextColor(ink);e.setFocusable(false);e.setCursorVisible(false);e.setContentDescription("Date, "+pretty(iso)+". Double tap to change.");
        e.setOnClickListener(v->{LocalDate d=LocalDate.parse((String)e.getTag());DatePickerDialog picker=new DatePickerDialog(this,(p,y,m,day)->{String chosen=LocalDate.of(y,m+1,day).toString();e.setTag(chosen);e.setText(pretty(chosen));e.setContentDescription("Date, "+pretty(chosen)+". Double tap to change.");},d.getYear(),d.getMonthValue()-1,d.getDayOfMonth());
            picker.getDatePicker().setMaxDate(future?LocalDate.now().plusYears(5).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli():System.currentTimeMillis());picker.getDatePicker().setMinDate(LocalDate.of(1900,1,1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli());picker.show();});
        f.addView(e,new LinearLayout.LayoutParams(-1,-2));return e;
    }
    private void onText(EditText e,Runnable changed){e.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int f){}public void onTextChanged(CharSequence s,int a,int b,int c){changed.run();}public void afterTextChanged(Editable x){}});}
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
    private void commit(Runnable action){
        if(!storageReadable)throw new IllegalStateException("Saved data could not be read.");
        Budget before;try{before=BudgetStore.decode(BudgetStore.encode(budget));}catch(Exception e){throw new IllegalStateException("Could not prepare save.");}
        try{action.run();String raw=BudgetStore.encode(budget);if(!getSharedPreferences("budget",0).edit().putString("data",raw).commit())throw new IllegalStateException("Could not save to device storage.");}
        catch(Exception e){budget=before;throw new IllegalArgumentException(e.getMessage()==null?"Check your entry.":e.getMessage());}
    }
    private void dialog(String title,LinearLayout f,Runnable action){
        ScrollView scroll=new ScrollView(this);scroll.addView(f);AlertDialog d=new AlertDialog.Builder(this).setTitle(title).setView(scroll).setNegativeButton("Cancel",null).setPositiveButton("Save",null).create();
        editors.add(d);d.setOnDismissListener(v->editors.remove(d));d.setOnShowListener(v->d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(w->{try{commit(action);render();d.dismiss();}catch(Exception e){toast(e.getMessage());}}));d.show();
    }
    private void assign(Budget.Category c){
        LinearLayout f=form();f.addView(label(money(budget.spendable(month))+" ready to assign",16,blue,true));long now=budget.assigned(c,month);f.addView(label("Assigned this month: "+money(now)+". A positive amount adds money; a negative one returns it.",13,muted,false));
        EditText amount=field(f,"Amount (AUD)",true);TextView result=label("",13,blue,true);f.addView(result);onText(amount,()->{try{result.setText("Assigned becomes "+money(now+Budget.parse(amount.getText().toString())));}catch(Exception e){result.setText("");}});
        // Quick amounts (as in YNAB): each fills in the change to this month's Assigned.
        f.addView(label("Quick amounts",12,muted,true));YearMonth last=month.minusMonths(1);long lastAssigned=budget.assigned(c,last),spentLast=budget.spent(c,last),average=budget.averageSpent(c,month);
        List<String> names=new ArrayList<>();List<Long> changes=new ArrayList<>();
        long needed=budget.needed(c,month);if(c.target>0&&needed>0){names.add("Needed for target: "+money(needed));changes.add(needed);}
        names.add("Assigned last month: "+money(lastAssigned));changes.add(lastAssigned-now);names.add("Spent last month: "+money(spentLast));changes.add(spentLast-now);names.add("Average spent, last 3 months: "+money(average));changes.add(average-now);
        long reset=budget.resetChange(c,month);if(reset!=0){names.add(reset==-now?"Reset to $0":"Return what's left: "+money(-reset));changes.add(reset);}
        for(int i=0;i<names.size();i++){long change=changes.get(i);Button b=button(names.get(i),()->amount.setText(decimal(change)));b.setTextSize(12);b.setMinHeight(dp(40));b.setMinimumHeight(dp(40));f.addView(b);}
        dialog("Assign to "+c.name,f,()->budget.assign(categoryById(c.id),month,Budget.parse(amount.getText().toString())));
    }
    private String[] availableNames(){return budget.categories.stream().map(c->c.name+" ("+money(budget.available(c,month))+")").toArray(String[]::new);}
    private void move(){if(budget.categories.size()<2){toast("Create two categories first.");return;}LinearLayout f=form();Spinner from=spinner(f,"From",availableNames(),0),to=spinner(f,"To",availableNames(),1);EditText amount=field(f,"Amount (AUD)",true);dialog("Move money",f,()->budget.move(budget.categories.get(from.getSelectedItemPosition()),budget.categories.get(to.getSelectedItemPosition()),month,Budget.cents(amount.getText().toString())));}
    private void autoAssign(){long remaining=Math.max(0,budget.spendable(month));long total=0;for(Budget.Category c:budget.categories)if(!c.hidden)total+=budget.fundNeed(c,month);long fund=Math.min(remaining,total);if(fund==0){toast("No available money or underfunded targets.");return;}new AlertDialog.Builder(this).setTitle("Fund targets").setMessage("Assign "+money(fund)+" to underfunded targets and upcoming bills, earliest due first?").setNegativeButton("Cancel",null).setPositiveButton("Fund",(d,w)->{try{commit(()->{long left=Math.max(0,budget.spendable(month));for(Budget.Category c:budget.fundOrder(month)){if(c.hidden)continue;long n=Math.min(left,budget.fundNeed(c,month));if(n>0){budget.assign(c,month,n);left-=n;}}});render();}catch(Exception e){toast(e.getMessage());}}).show();}
    private void editCategory(Budget.Category existing){
        LinearLayout f=form();EditText name=field(f,"Category name",false),group=field(f,"Group (Bills, Everyday, Savings...)",false);String[] types={"Refill each month","Set aside each month","Save toward a balance"};Spinner type=spinner(f,"Target behavior",types,existing==null?0:existing.targetType.equals("Monthly")?1:existing.targetType.equals("Balance")?2:0);TextView explanation=label("",13,muted,false);f.addView(explanation);EditText amount=field(f,"Target amount (0 for none)",true);LinearLayout deadline=column();f.addView(deadline);EditText due=field(deadline,"Due month (YYYY-MM, optional)",false);
        LinearLayout dayRow=column();f.addView(dayRow);EditText dueDay=field(dayRow,"Due day of the month (1-31, optional)",false);dueDay.setInputType(InputType.TYPE_CLASS_NUMBER);dayRow.addView(label("Fund targets funds the earliest due first.",12,muted,false));
        EditText note=field(f,"Note (optional)",false);
        if(existing!=null){name.setText(existing.name);group.setText(existing.group);amount.setText(decimal(existing.target));due.setText(existing.due);dueDay.setText(existing.dueDay>0?String.valueOf(existing.dueDay):"");note.setText(existing.note);}else group.setText("Everyday");
        Runnable describe=()->{int selected=type.getSelectedItemPosition();deadline.setVisibility(selected==2?View.VISIBLE:View.GONE);dayRow.setVisibility(selected==2?View.GONE:View.VISIBLE);explanation.setText(new String[]{"Top up what remained from last month. Example: a $500 target with $100 left asks for $400. Spending this month does not restart the target.","Add a fresh amount every month. Example: set aside $100 for repairs, even if $300 remains from earlier months.","Build up to a total balance. Example: a $1,200 goal with $300 saved and 3 months remaining asks for $300 this month. A due month is optional."}[selected]);};describe.run();type.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onItemSelected(AdapterView<?> p,View v,int position,long id){describe.run();}public void onNothingSelected(AdapterView<?> p){}});
        dialog(existing==null?"New category":"Edit category & target",f,()->{String n=required(name),g=required(group);for(Budget.Category c:budget.categories)if((existing==null||!c.id.equals(existing.id))&&c.name.equalsIgnoreCase(n))throw new IllegalArgumentException("That category already exists.");long target=amount.getText().toString().trim().isEmpty()?0:Budget.parse(amount.getText().toString());if(target<0)throw new IllegalArgumentException("Target cannot be negative.");String dueMonth=type.getSelectedItemPosition()==2?due.getText().toString().trim():"";if(!dueMonth.isEmpty()){YearMonth m=YearMonth.parse(dueMonth);if(m.getYear()<1900||m.getYear()>2100)throw new IllegalArgumentException("Choose a due year between 1900 and 2100.");}Budget.Category c=existing==null?new Budget.Category(n):budget.category(existing.id);c.name=n;c.group=g;c.target=target;c.targetType=new String[]{"Refill","Monthly","Balance"}[type.getSelectedItemPosition()];c.due=dueMonth;String day=dueDay.getText().toString().trim();int d=0;if(type.getSelectedItemPosition()!=2&&!day.isEmpty()){try{d=Integer.parseInt(day);}catch(NumberFormatException e){d=-1;}if(d<1||d>31)throw new IllegalArgumentException("Enter a due day from 1 to 31, or leave it empty.");}c.dueDay=d;c.note=note.getText().toString().trim();if(existing==null)budget.categories.add(c);});
    }
    private void addAccount(){
        LinearLayout f=form();Spinner type=spinner(f,"Type",new String[]{"Cash, checking or savings","Credit card"},0);EditText name=field(f,"Account name",false),opening=field(f,"Current cash balance (AUD)",true);f.addView(label("Opening date",12,muted,true));EditText day=dateField(f,LocalDate.now().toString());TextView help=label("",13,muted,false);f.addView(help);
        Runnable adapt=()->{boolean card=type.getSelectedItemPosition()==1;opening.setHint(card?"Amount owed now (AUD, 0 if paid off)":"Current cash balance (AUD)");help.setText(card?"What you owe now is old debt: it gets a payment category with nothing set aside, so assign money to that category to pay it down. New spending on the card moves the category's money there for you.":"Enter transactions from the opening date onward.");};
        adapt.run();type.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onItemSelected(AdapterView<?> p,View v,int position,long id){adapt.run();}public void onNothingSelected(AdapterView<?> p){}});
        dialog("Add account",f,()->{String n=required(name);for(Budget.Account a:budget.accounts)if(a.name.equalsIgnoreCase(n))throw new IllegalArgumentException("That account already exists.");long balance=Budget.parse(opening.getText().toString().trim().isEmpty()?"0":opening.getText().toString());
            if(balance<0)throw new IllegalArgumentException(type.getSelectedItemPosition()==1?"Enter what you owe as a positive amount.":"Use a nonnegative cash opening balance.");
            if(type.getSelectedItemPosition()==1){for(Budget.Category c:budget.categories)if(c.name.equalsIgnoreCase(n))throw new IllegalArgumentException("A category already has that name. Choose another name for the card.");budget.addCard(n,date(day),balance);}
            else budget.accounts.add(new Budget.Account(n,date(day),balance));});
    }
    private static final String[] REPEAT_LABELS={"Doesn't repeat","Weekly","Every 2 weeks","Monthly","Every 3 months","Yearly"};
    private void transaction(Budget.Entry old){transaction(old,null);}
    /**
     * Adds or edits a transaction ([old]) or an upcoming one ([sched]). A new one with a future date or a repeat
     * becomes upcoming: it waits in Spending until its day, when the user enters or skips it.
     */
    private void transaction(Budget.Entry old,Budget.Scheduled sched){
        if(budget.accounts.isEmpty()){toast("Add an account first.");addAccount();return;}if(old!=null&&old.transfer()){editTransfer(old);return;}
        String keepAccount=old!=null?old.account:sched!=null?sched.account:null,keepCategory=old!=null?old.category:sched!=null?sched.category:"";long oldAmount=old!=null?old.amount:sched!=null?sched.amount:-1;
        List<Budget.Account> accounts=keepAccount==null?openAccounts():openAccounts(budget.account(keepAccount));List<Budget.Category> categories=visibleCategories(budget.category(keepCategory));
        if(accounts.isEmpty()){toast("All your accounts are closed. Reopen one in Accounts first.");return;}
        LinearLayout f=form();Spinner kind=spinner(f,"Type",new String[]{"Expense","Income","Category refund"},oldAmount<0?0:keepCategory.isEmpty()?1:2);TextView guidance=label("",12,muted,false);f.addView(guidance);
        // Payees used before are suggested; picking one on a new transaction fills in the category it had last time.
        AutoCompleteTextView payee=new AutoCompleteTextView(this);payee.setSingleLine(true);payee.setTextColor(ink);payee.setThreshold(1);payee.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_dropdown_item_1line,budget.payees()));f.addView(payee,new LinearLayout.LayoutParams(-1,-2));
        f.addView(label("Amount (AUD)",12,muted,true));EditText amount=field(f,"0.00",true);amount.setTextSize(24);f.addView(label("Date",12,muted,true));EditText day=dateField(f,old!=null?old.date:sched!=null?sched.next:LocalDate.now().toString(),old==null);Spinner account=spinner(f,"Account",accounts.stream().map(a->a.name).toArray(String[]::new),keepAccount==null?0:accounts.indexOf(budget.account(keepAccount)));LinearLayout categoryFields=column();f.addView(categoryFields);Spinner category=spinner(categoryFields,"Category",categories.stream().map(c->c.name).toArray(String[]::new),keepCategory.isEmpty()?0:categories.indexOf(budget.category(keepCategory)));
        // Split: the parts (positive amounts while editing) replace the category; the amount becomes their total.
        List<Budget.Split> parts=new ArrayList<>();if(old!=null)for(Budget.Split p:old.splits){Budget.Split c=new Budget.Split(p.category,Math.abs(p.amount));c.memo=p.memo;parts.add(c);}
        TextView splitSummary=label("",13,ink,false);categoryFields.addView(splitSummary);Button splitButton=button("Split into categories",()->{});categoryFields.addView(splitButton);
        Runnable showSplit=()->{boolean on=!parts.isEmpty();category.setVisibility(on?View.GONE:View.VISIBLE);splitSummary.setVisibility(on?View.VISIBLE:View.GONE);splitButton.setText(on?"Edit split":"Split into categories");amount.setEnabled(!on);
            if(on){long sum=0;StringBuilder s=new StringBuilder("Split: ");for(int i=0;i<parts.size();i++){Budget.Split p=parts.get(i);Budget.Category c=budget.category(p.category);sum+=p.amount;s.append(i>0?", ":"").append(c==null?"Ready to Assign":c.name).append(" ").append(money(p.amount));}splitSummary.setText(s);amount.setText(decimal(sum));}};
        splitButton.setOnClickListener(v->{long total;try{total=Budget.cents(amount.getText().toString());}catch(Exception e){total=0;}editSplit(parts,categories,categories.isEmpty()?null:categories.get(Math.max(0,category.getSelectedItemPosition())),total,showSplit);});
        showSplit.run();
        boolean[] categoryChosen={old!=null||sched!=null};category.setOnTouchListener((v,ev)->{categoryChosen[0]=true;return false;});
        payee.setOnItemClickListener((p,v,position,id)->{Budget.Entry last=budget.lastForPayee(payee.getText().toString());if(old!=null||categoryChosen[0]||last==null)return;
            if(last.category.isEmpty()){kind.setSelection(1);return;}int i=categories.indexOf(budget.category(last.category));if(i<0)return;category.setSelection(i);if(kind.getSelectedItemPosition()==1)kind.setSelection(last.amount<0?0:2);});
        LinearLayout noteFields=column();EditText memo=field(noteFields,"Note (optional)",false);Button note=button(old!=null&&!old.memo.isEmpty()?"Hide note":"+ Add a note",()->{});f.addView(note);f.addView(noteFields);noteFields.setVisibility(old!=null&&!old.memo.isEmpty()?View.VISIBLE:View.GONE);note.setOnClickListener(v->{boolean show=noteFields.getVisibility()!=View.VISIBLE;noteFields.setVisibility(show?View.VISIBLE:View.GONE);note.setText(show?"Hide note":"+ Add a note");});CheckBox cleared=new CheckBox(this);cleared.setText("Cleared at the bank");cleared.setMinHeight(dp(48));f.addView(cleared);
        // A photo (e.g. a receipt), kept on this phone. Picking one leaves this form open (see onRestart).
        String[] photo={old!=null?old.photo:""};LinearLayout photoBox=column();if(sched==null)f.addView(photoBox);Runnable[] showPhotoBox=new Runnable[1];
        showPhotoBox[0]=()->{photoBox.removeAllViews();
            if(photo[0].isEmpty())photoBox.addView(button("+ Add a photo",()->{photoTarget=u->{try{photo[0]=copyPhoto(u);}catch(Exception e){toast("Could not add that photo.");}showPhotoBox[0].run();};pickingPhoto=true;
                try{startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE),PHOTO);}catch(Exception e){pickingPhoto=false;photoTarget=null;toast("No app on this device can pick a photo.");}}));
            else{android.graphics.Bitmap bm=photoBitmap(photo[0],480);if(bm==null)photoBox.addView(label("The photo isn't on this phone.",13,muted,false));else{ImageView img=new ImageView(this);img.setImageBitmap(bm);img.setAdjustViewBounds(true);img.setScaleType(ImageView.ScaleType.FIT_START);img.setContentDescription("Photo of this transaction. Double tap to view it larger.");img.setOnClickListener(v->viewPhoto(photo[0]));photoBox.addView(img,new LinearLayout.LayoutParams(-1,dp(160)));}
                photoBox.addView(button("Remove photo",()->{photo[0]="";showPhotoBox[0].run();}));}};
        showPhotoBox[0].run();
        Spinner repeat=null;if(old==null){repeat=spinner(f,"Repeat",REPEAT_LABELS,sched==null?0:Arrays.asList(Budget.Scheduled.REPEATS).indexOf(sched.repeat));f.addView(label("A future date or a repeat makes it upcoming: it waits in Spending, and you enter it when the day comes.",12,muted,false));}
        if(sched!=null){cleared.setVisibility(View.GONE);payee.setText(sched.payee,false);amount.setText(decimal(Math.abs(sched.amount)));memo.setText(sched.memo);if(!sched.memo.isEmpty()){noteFields.setVisibility(View.VISIBLE);note.setText("Hide note");}f.addView(button("Delete upcoming transaction",()->deleteScheduled(sched.id)));}
        if(old!=null){payee.setText(old.payee,false);amount.setText(decimal(Math.abs(old.amount)));memo.setText(old.memo);cleared.setChecked(old.cleared);f.addView(button("Delete transaction",()->delete(old)));}
        Spinner repeatField=repeat;
        Runnable adapt=()->{int selected=kind.getSelectedItemPosition();boolean income=selected==1;categoryFields.setVisibility(income?View.GONE:View.VISIBLE);payee.setHint(income?"Income source":selected==2?"Refund from":"Payee");guidance.setText(income?"Adds money to Ready to Assign.":selected==2?"Returns money to the original spending category.":"Reduces the available money in your category.");};adapt.run();kind.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onItemSelected(AdapterView<?> p,View v,int position,long id){adapt.run();}public void onNothingSelected(AdapterView<?> p){}});
        dialog(sched!=null?"Edit upcoming transaction":old==null?"Add transaction":"Edit transaction",f,()->{int k=kind.getSelectedItemPosition();if(k!=1&&categories.isEmpty())throw new IllegalArgumentException("Add a category first.");
            boolean isSplit=k!=1&&!parts.isEmpty();
            String p=required(payee),cat=k==1?"":isSplit?Budget.SPLIT:categories.get(category.getSelectedItemPosition()).id,acc=accounts.get(account.getSelectedItemPosition()).id,memoText=memo.getText().toString().trim();long cents=Budget.cents(amount.getText().toString())*(k==0?-1:1);
            String rep=repeatField==null?"Never":Budget.Scheduled.REPEATS[repeatField.getSelectedItemPosition()];LocalDate when=LocalDate.parse((String)day.getTag());
            if(old==null&&(sched!=null||when.isAfter(LocalDate.now())||!rep.equals("Never"))){
                if(isSplit)throw new IllegalArgumentException("A split can't be upcoming yet. Save it on its day, or use one category.");
                Budget.Scheduled s=new Budget.Scheduled(p,cat,acc,when.toString(),cents,rep);s.memo=memoText;if(sched!=null){s.id=sched.id;s.billKey=sched.billKey;}
                if(sched==null&&!when.isAfter(LocalDate.now())){budget.validate(s);budget.enter(s);if(!rep.equals("Never"))budget.scheduled.add(s);return;} // today or earlier: entered now, the repeat continues
                budget.validate(s);budget.scheduled.removeIf(t->t.id.equals(s.id));budget.scheduled.add(s);return;
            }
            Budget.Entry e=new Budget.Entry(p,cat,acc,date(day),cents);e.memo=memoText;e.photo=photo[0];if(isSplit)for(Budget.Split part:parts){Budget.Split s=new Budget.Split(part.category,part.amount*(k==0?-1:1));s.memo=part.memo;e.splits.add(s);}e.cleared=cleared.isChecked();budget.validate(e);if(old!=null){e.id=old.id;e.externalId=old.externalId;e.billKey=old.billKey;budget.entries.removeIf(t->t.id.equals(old.id));}budget.entries.add(0,e);});
    }
    /** Edits [parts] (category + positive amount per row); at least two parts. Remove split empties them. */
    private void editSplit(List<Budget.Split> parts,List<Budget.Category> categories,Budget.Category first,long total,Runnable done){
        if(categories.isEmpty()){toast("Add a category first.");return;}
        String[] names=new String[categories.size()+1];for(int i=0;i<categories.size();i++)names[i]=categories.get(i).name;names[categories.size()]="Ready to Assign";
        LinearLayout f=form(),rows=column();f.addView(label("Each part comes out of its own category.",13,muted,false));f.addView(rows);TextView sum=label("",14,blue,true);
        List<Spinner> cats=new ArrayList<>();List<EditText> amounts=new ArrayList<>();
        Runnable total2=()->{long n=0;for(EditText a:amounts){try{n+=Budget.parse(a.getText().toString().isEmpty()?"0":a.getText().toString());}catch(Exception e){}}sum.setText("Total "+money(n));};
        java.util.function.BiConsumer<String,Long> addRow=(category,cents)->{LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);Spinner s=new Spinner(this);s.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,names));
            int i=category==null?0:category.isEmpty()?categories.size():Math.max(0,categories.indexOf(budget.category(category)));s.setSelection(i);row.addView(s,new LinearLayout.LayoutParams(0,-2,1));
            EditText a=new EditText(this);a.setHint("0.00");a.setTextColor(ink);a.setSingleLine(true);a.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);if(cents!=null&&cents>0)a.setText(decimal(cents));onText(a,total2);row.addView(a,new LinearLayout.LayoutParams(dp(110),-2));
            Button x=button("✕",()->{});x.setContentDescription("Remove this part");x.setBackground(bg(Color.TRANSPARENT));x.setOnClickListener(v->{rows.removeView(row);cats.remove(s);amounts.remove(a);total2.run();});row.addView(x,new LinearLayout.LayoutParams(dp(48),dp(48)));
            rows.addView(row);cats.add(s);amounts.add(a);};
        if(parts.isEmpty()){addRow.accept(first==null?null:first.id,total);addRow.accept(null,null);}else for(Budget.Split p:parts)addRow.accept(p.category,p.amount);
        f.addView(button("+ Add a part",()->{addRow.accept(null,null);total2.run();}));f.addView(sum);total2.run();
        ScrollView scroll=new ScrollView(this);scroll.addView(f);AlertDialog d=new AlertDialog.Builder(this).setTitle("Split").setView(scroll).setNegativeButton("Cancel",null).setPositiveButton("Done",null).setNeutralButton(parts.isEmpty()?null:"Remove split",(x,w)->{parts.clear();done.run();}).create();
        d.setOnShowListener(v->d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(w->{
            List<Budget.Split> result=new ArrayList<>();
            for(int i=0;i<cats.size();i++){long cents;try{cents=Budget.cents(amounts.get(i).getText().toString());}catch(Exception e){toast("Give every part an amount above $0.");return;}int c=cats.get(i).getSelectedItemPosition();result.add(new Budget.Split(c==categories.size()?"":categories.get(c).id,cents));}
            if(result.size()<2){toast("A split needs at least two parts. Use Remove split for one category.");return;}
            parts.clear();parts.addAll(result);done.run();d.dismiss();}));d.show();
    }
    private Budget.Scheduled scheduledById(String id){for(Budget.Scheduled s:budget.scheduled)if(s.id.equals(id))return s;throw new IllegalArgumentException("That upcoming transaction no longer exists.");}
    private void deleteScheduled(String id){new AlertDialog.Builder(this).setTitle("Delete upcoming transaction?").setMessage("It and its repeats are removed. Transactions already entered stay.").setNegativeButton("Cancel",null).setPositiveButton("Delete",(d,w)->{if(change(()->budget.scheduled.remove(scheduledById(id))))for(AlertDialog editor:new ArrayList<>(editors))editor.dismiss();}).show();}
    /** A due upcoming transaction: enter it (it becomes money), skip this date, or edit it. */
    private void dueActions(String id){
        Budget.Scheduled s;try{s=scheduledById(id);}catch(Exception e){return;}
        boolean isDue=!LocalDate.parse(s.next).isAfter(LocalDate.now());
        List<String> names=new ArrayList<>();List<Runnable> actions=new ArrayList<>();
        if(isDue){names.add("Enter it now");actions.add(()->{if(change(()->budget.enter(scheduledById(id))))toast("Entered "+s.payee+".");});names.add(s.repeat.equals("Never")?"Skip it (delete)":"Skip this one");actions.add(()->change(()->budget.advance(scheduledById(id))));}
        names.add("Edit");actions.add(()->transaction(null,s));
        new AlertDialog.Builder(this).setTitle(s.payee+" · "+money(s.amount)).setItems(names.toArray(new String[0]),(d,n)->actions.get(n).run()).show();
    }
    private String repeatLabel(Budget.Scheduled s){int i=Arrays.asList(Budget.Scheduled.REPEATS).indexOf(s.repeat);return i<=0?"Once":REPEAT_LABELS[i];}
    private void delete(Budget.Entry e){new AlertDialog.Builder(this).setTitle("Delete transaction?").setMessage("Account and category balances will be recalculated.").setNegativeButton("Cancel",null).setPositiveButton("Delete",(d,w)->{try{commit(()->budget.entries.removeIf(t->t.id.equals(e.id)));render();for(AlertDialog editor:new ArrayList<>(editors))editor.dismiss();}catch(Exception ex){toast(ex.getMessage());}}).show();}
    private final List<AlertDialog> editors=new ArrayList<>();
    private void transfer(){editTransfer(null,null,0);}
    /** A card payment: a transfer from a cash account to the card, for what's set aside (or what's owed, if less). */
    private void payCard(String id){Budget.Account card=budget.account(id);if(card==null)return;Budget.Category p=budget.paymentCategory(card);long owed=-budget.balance(card,false),ready=p==null?0:Math.max(0,budget.available(p,month));editTransfer(null,id,Math.max(0,Math.min(owed,ready)));}
    private void editTransfer(Budget.Entry old){editTransfer(old,null,0);}
    private void editTransfer(Budget.Entry old,String toId,long preset){
        List<Budget.Account> accounts=old==null?openAccounts():openAccounts(budget.account(old.account),budget.account(old.destination));String[] names=accounts.stream().map(a->a.name).toArray(String[]::new);
        if(accounts.size()<2){toast("Add two open accounts first.");return;}LinearLayout f=form();int toIndex=toId==null?-1:accounts.indexOf(budget.account(toId)),fromIndex=0;if(toIndex>=0)for(int i=0;i<accounts.size();i++)if(!accounts.get(i).credit()){fromIndex=i;break;}
        Spinner from=spinner(f,"From account",names,old!=null?accounts.indexOf(budget.account(old.account)):fromIndex),to=spinner(f,"To account",names,old!=null?accounts.indexOf(budget.account(old.destination)):toIndex>=0?toIndex:1);EditText amount=field(f,"Amount (AUD)",true);if(preset>0)amount.setText(decimal(preset));f.addView(label("Date",12,muted,true));EditText day=dateField(f,old==null?LocalDate.now().toString():old.date);CheckBox cleared=new CheckBox(this);cleared.setText("Cleared in both accounts");f.addView(cleared);if(old!=null){amount.setText(decimal(-old.amount));cleared.setChecked(old.cleared);f.addView(button("Delete transfer",()->delete(old)));}
        dialog(old!=null?"Edit transfer":toIndex>=0?"Pay "+budget.account(toId).name:"Transfer money",f,()->{Budget.Account a=accounts.get(from.getSelectedItemPosition()),b=accounts.get(to.getSelectedItemPosition());Budget.Entry e=new Budget.Entry("Transfer to "+b.name,"",a.id,date(day),-Budget.cents(amount.getText().toString()));e.destination=b.id;e.cleared=cleared.isChecked();budget.validate(e);if(old!=null){e.id=old.id;budget.entries.removeIf(t->t.id.equals(old.id));}budget.entries.add(0,e);});
    }
    // A difference can be settled with an adjustment to Ready to Assign (as YNAB does) after the user confirms.
    private void reconcile(Budget.Account a){
        LinearLayout f=form();f.addView(label("Cleared balance: "+money(budget.balance(a,true)),18,ink,true));f.addView(label("Compare with your bank's cleared balance, excluding pending transactions. Mark transactions cleared in Spending first.",14,muted,false));EditText value=field(f,"Bank's cleared balance (AUD)",true);
        ScrollView scroll=new ScrollView(this);scroll.addView(f);AlertDialog d=new AlertDialog.Builder(this).setTitle("Reconcile "+a.name).setView(scroll).setNegativeButton("Cancel",null).setPositiveButton("Reconcile",null).create();
        editors.add(d);d.setOnDismissListener(v->editors.remove(d));d.setOnShowListener(v->d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(w->{
            long bank;try{bank=Budget.parse(value.getText().toString());}catch(Exception e){toast(e.getMessage());return;}
            String today=LocalDate.now().toString();Budget.Entry adjust=budget.adjustment(accountById(a.id),bank,today);
            if(adjust==null){if(change(()->accountById(a.id).reconciled=today)){d.dismiss();toast("Reconciled.");}return;}
            new AlertDialog.Builder(this).setTitle("Balances differ by "+money(adjust.amount)).setMessage("Check for missing or uncleared transactions first. Or add a cleared adjustment of "+money(adjust.amount)+" to Ready to Assign so "+a.name+" matches your bank.")
                .setNegativeButton("Check first",null).setPositiveButton("Add adjustment",(d2,x)->{if(change(()->{Budget.Account acc=accountById(a.id);Budget.Entry e=budget.adjustment(acc,bank,today);if(e!=null){budget.validate(e);budget.entries.add(0,e);}acc.reconciled=today;})){d.dismiss();toast("Adjustment added and reconciled.");}}).show();
        }));d.show();
    }
    private Budget.Account accountById(String id){Budget.Account a=budget.account(id);if(a==null)throw new IllegalArgumentException("That account no longer exists.");return a;}
    /** Open accounts, plus [keep] (an old transaction's accounts) even when closed. */
    private List<Budget.Account> openAccounts(Budget.Account... keep){List<Budget.Account> list=new ArrayList<>();for(Budget.Account a:budget.accounts)if(!a.closed||Arrays.asList(keep).contains(a))list.add(a);return list;}
    /** Categories to spend from (not hidden, not a card payment), plus [keep] (an old transaction's category) even when hidden. */
    private List<Budget.Category> visibleCategories(Budget.Category keep){List<Budget.Category> list=new ArrayList<>();for(Budget.Category c:budget.categories)if((!c.hidden&&!c.payment())||c==keep)list.add(c);return list;}
    private void editAccount(String id){
        Budget.Account a=budget.account(id);if(a==null)return;LinearLayout f=form();f.addView(label("Name",12,muted,true));EditText name=field(f,"Account name",false);name.setText(a.name);
        f.addView(label("Opened "+pretty(a.date)+" with "+money(a.opening)+". These stay fixed so past months don't change.",13,muted,false));
        long balance=budget.balance(a,false);
        if(balance==0)f.addView(button("Close account",()->new AlertDialog.Builder(this).setTitle("Close "+a.name+"?").setMessage("It moves to Closed accounts and isn't offered for new transactions. Its history stays, and you can reopen it.").setNegativeButton("Cancel",null).setPositiveButton("Close account",(d,w)->{if(change(()->budget.close(accountById(id))))for(AlertDialog editor:new ArrayList<>(editors))editor.dismiss();}).show()));
        else f.addView(label("To close it, first move its "+money(balance)+" to another account: an account closes at $0.",13,muted,false));
        if(!budget.usedAccount(a))f.addView(button("Delete account",()->new AlertDialog.Builder(this).setTitle("Delete "+a.name+"?").setMessage("It has no transactions. Its opening balance of "+money(a.opening)+" leaves your plan.").setNegativeButton("Cancel",null).setPositiveButton("Delete",(d,w)->{if(change(()->budget.deleteAccount(accountById(id))))for(AlertDialog editor:new ArrayList<>(editors))editor.dismiss();}).show()));
        dialog("Edit account",f,()->{String n=required(name);for(Budget.Account o:budget.accounts)if(!o.id.equals(id)&&o.name.equalsIgnoreCase(n))throw new IllegalArgumentException("That account already exists.");budget.rename(accountById(id),n);});
    }
    private void load(){
        String raw=getSharedPreferences("budget",0).getString("data",null);if(raw==null){for(String[] item:new String[][]{{"Rent","Bills"},{"Utilities","Bills"},{"Groceries","Everyday"},{"Transport","Everyday"},{"Dining out","Everyday"},{"Annual insurance","True expenses"},{"Car repairs","True expenses"},{"Emergency fund","Savings"}}){Budget.Category c=new Budget.Category(item[0]);c.group=item[1];budget.categories.add(c);}return;}
        try{budget=BudgetStore.decode(raw);if(!raw.contains("\"version\"")){String updated=BudgetStore.encode(budget);if(!getSharedPreferences("budget",0).edit().putString("legacy_backup",raw).putString("data",updated).commit())throw new IllegalStateException("Migration could not be saved.");toast("Budget upgraded. Existing balances preserved; monthly assignments begin this month.");}}
        catch(Exception e){storageReadable=false;new AlertDialog.Builder(this).setTitle("Unable to load budget").setMessage("Your saved data has been preserved. Close the app to avoid changes.").setPositiveButton("Close",(d,w)->finish()).setCancelable(false).show();}
    }
}
