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
    private String tab="Home",search="",accountFilter="",categoryFilter="",fromFilter="",toFilter=""; // Transactions filters (see Budget.Filter)
    private int flagFilter=-1,clearedFilter=-1;
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
        if(state!=null){tab=state.getString("tab","Home");previousTab=state.getString("previousTab","Home");search=state.getString("search","");accountFilter=state.getString("accountFilter","");categoryFilter=state.getString("categoryFilter","");fromFilter=state.getString("fromFilter","");toFilter=state.getString("toFilter","");flagFilter=state.getInt("flagFilter",-1);clearedFilter=state.getInt("clearedFilter",-1);month=YearMonth.parse(state.getString("month",YearMonth.now().toString()));period=Math.max(0,Math.min(PERIODS.length-1,state.getInt("period",0)));byGroup=state.getBoolean("byGroup",false);trendKey=state.getString("trendKey","");trendMonths=state.getInt("trendMonths",6)==12?12:6;}
        load();if(storageReadable){render();cleanupPhotos();}
    }
    // The saved data as this screen last read or wrote it. AddExpenseActivity may save an expense from another app
    // meanwhile: when the saved data differs, it's read in (open forms close, as they show the old budget), so the next save keeps it.
    private String loaded;
    private boolean reloadIfChanged(){String raw=prefs().getString("data",null);if(raw==null||raw.equals(loaded)||!storageReadable)return false;Budget fresh;try{fresh=BudgetStore.decode(raw);}catch(Exception e){throw new IllegalStateException("Could not reload your budget.");}fresh.fromPlanner.addAll(budget.fromPlanner);budget=fresh;loaded=raw;return true;}
    private boolean pickingPhoto;private java.util.function.Consumer<Uri> photoTarget;private AlertDialog photoForm;
    /**
     * Back from the photo picker: data saved meanwhile is read in, but the form the photo is for stays open (with what was
     * typed and the photo). Its save looks records up by id, so it saves on the latest data; any other open form closes.
     */
    private void photoReturned(){AlertDialog form=photoForm;photoForm=null;try{if(reloadIfChanged()){for(AlertDialog editor:new ArrayList<>(editors))if(editor!=form)editor.dismiss();render();}}catch(IllegalStateException e){toast(e.getMessage());}}
    // Automatic backup: today's, if it hasn't run yet (the daily job may not have had a chance).
    // Not reloaded on return from the photo picker: photoReturned did that, keeping the form the photo is for open.
    @Override protected void onResume(){super.onResume();if(!storageReadable)return;if(pickingPhoto)pickingPhoto=false;else try{if(reloadIfChanged()){for(AlertDialog editor:new ArrayList<>(editors))editor.dismiss();render();}}catch(IllegalStateException e){toast(e.getMessage());}
        AutoBackup.schedule(this);if(prefs().getString("auto_backup_tree",null)!=null)new Thread(()->AutoBackup.run(getApplicationContext(),false)).start();}
    @Override protected void onSaveInstanceState(Bundle state){state.putString("tab",tab);state.putString("previousTab",previousTab);state.putString("search",search);state.putString("accountFilter",accountFilter);state.putString("categoryFilter",categoryFilter);state.putString("fromFilter",fromFilter);state.putString("toFilter",toFilter);state.putInt("flagFilter",flagFilter);state.putInt("clearedFilter",clearedFilter);state.putString("month",month.toString());state.putInt("period",period);state.putBoolean("byGroup",byGroup);state.putString("trendKey",trendKey);state.putInt("trendMonths",trendMonths);super.onSaveInstanceState(state);}
    private void options(View anchor){
        PopupMenu menu=new PopupMenu(this,anchor);menu.getMenu().add("Settings");menu.getMenu().add(hideAmounts?"Show amounts":"Hide amounts");menu.getMenu().add("Budget reset");
        menu.setOnMenuItemClickListener(item->{String t=item.getTitle().toString();
            if(t.equals("Budget reset"))planReset();
            else if(t.endsWith("amounts")){if(!getSharedPreferences("appearance",0).edit().putBoolean("hideAmounts",!hideAmounts).commit()){toast("Could not save that setting.");return true;}hideAmounts=!hideAmounts;render();}
            else{if(!tab.equals("Settings"))previousTab=tab;tab="Settings";render();}return true;});menu.show();
    }
    /** Budget reset: every category's money in this month goes back into To budget, to start the plan afresh. The budget before is kept for Undo. */
    private void planReset(){
        if(!storageReadable)return;if(month.isAfter(YearMonth.now())){toast("Reset this month or an earlier one.");return;}
        Map<Budget.Category,Long> back=budget.resetAmounts(month);long total=0;for(long a:back.values())total+=a;int n=back.size(); // exactly what the reset returns
        if(n==0){toast("No category has money to return this month.");return;}
        String monthName=month.format(DateTimeFormatter.ofPattern("MMMM yyyy"));
        new AlertDialog.Builder(this).setTitle("Budget reset").setMessage("Return "+money(total)+" from "+count(n,"category","categories")+" into To budget in "+monthName+", then assign it again by today's priorities?\n\nTargets and transactions stay. You can undo this on Budget.")
            .setNegativeButton("Cancel",null).setPositiveButton("Reset",(d,w)->{String before=prefs().getString("data",null);
                if(change(()->budget.planReset(month))){if(!prefs().edit().putString("before_reset",before==null?"":before).putString("before_reset_at",LocalDateTime.now().withNano(0).toString()).commit())toast("Reset done, but Undo couldn't be saved.");else{tab="Plan";render();}}}).show();
    }
    private void undoPlanReset(){
        new AlertDialog.Builder(this).setTitle("Undo budget reset?").setMessage("Puts back the plan you had before the reset on "+when(prefs().getString("before_reset_at",""))+". Changes made since are lost.").setNegativeButton("Cancel",null).setPositiveButton("Undo reset",(d,w)->{
            String before=prefs().getString("before_reset",null);if(before==null){render();return;}
            Budget previous;try{previous=before.isEmpty()?null:BudgetStore.decode(before);}catch(Exception e){toast("The plan from before the reset can't be read. Nothing was changed.");return;}
            android.content.SharedPreferences.Editor edit=prefs().edit().remove("before_reset").remove("before_reset_at");if(previous==null)edit.remove("data");else edit.putString("data",before);
            if(!edit.commit()){toast("Could not save to device storage.");return;}
            if(previous==null){budget=new Budget();load();}else{budget=previous;loaded=before;}render();toast("Budget reset undone.");}).show();
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
        content.addView(label("Transactions",18,blue,true));LinearLayout flags=card();flags.addView(label("Flags",20,ink,true));
        StringBuilder named=new StringBuilder();for(int i=1;i<Budget.FLAGS.length;i++)if(!budget.flagNames[i].isEmpty())named.append(named.length()>0?", ":"").append(budget.flagLabel(i));
        flags.addView(label("Mark transactions with a coloured flag and filter by it. Give a colour a name once, such as green for Tax."+(named.length()>0?" Named: "+named+".":""),14,muted,false));flags.addView(button("Name your flags",this::nameFlags));
        LinearLayout payees=card();payees.addView(label("Payees",20,ink,true));payees.addView(label("Rename or merge payees, hide old ones from suggestions, and set import rules that rename statement payees and choose their category.",14,muted,false));payees.addView(button("Payees and import rules",this::payees));
    }
    // Flags: a colour per transaction (Budget.FLAGS), shown as a dot on its row.
    private static final int[] FLAG_COLORS={0,Color.rgb(229,72,77),Color.rgb(247,107,21),Color.rgb(226,178,3),Color.rgb(48,164,108),Color.rgb(62,99,221),Color.rgb(142,78,198)};
    private String[] flagChoices(){String[] names=new String[Budget.FLAGS.length];names[0]="No flag";for(int i=1;i<names.length;i++)names[i]=budget.flagLabel(i);return names;}
    private void nameFlags(){
        LinearLayout f=form();f.addView(label("An optional name for each colour, shown wherever the flag is. Leave it empty for just the colour.",13,muted,false));EditText[] names=new EditText[Budget.FLAGS.length];
        for(int i=1;i<names.length;i++){TextView colour=label("● "+Budget.FLAGS[i],13,FLAG_COLORS[i],true);f.addView(colour);names[i]=field(f,Budget.FLAGS[i]+" means… (optional)",false);names[i].setText(budget.flagNames[i]);names[i].setFilters(new InputFilter[]{new InputFilter.LengthFilter(30)});}
        dialog("Flag names",f,()->{for(int i=1;i<names.length;i++)budget.flagNames[i]=names[i].getText().toString().trim();});
    }
    // Payees: rename, merge, hide from suggestions; import rules for statements.
    private void payees(){
        List<String> all=budget.allPayees();Map<String,Integer> uses=new HashMap<>();for(Budget.Entry e:budget.entries)if(!e.transfer())uses.merge(e.payee.trim().toLowerCase(Locale.ROOT),1,Integer::sum);
        String[] rows=all.stream().map(p->p+" ("+uses.getOrDefault(p.toLowerCase(Locale.ROOT),0)+")"+(budget.hiddenPayee(p)?" · hidden":"")).toArray(String[]::new);
        AlertDialog.Builder d=new AlertDialog.Builder(this).setTitle(all.isEmpty()?"No payees yet":"Payees").setNegativeButton("Close",null).setNeutralButton("Import rules ("+budget.rules.size()+")",(x,w)->rules());
        if(all.isEmpty())d.setMessage("Payees appear here once you have transactions. Import rules can be set up now.");else d.setItems(rows,(x,n)->payeeActions(all.get(n)));d.show();
    }
    private void payeeActions(String payee){
        boolean hidden=budget.hiddenPayee(payee);
        new AlertDialog.Builder(this).setTitle(payee).setItems(new String[]{"Rename","Merge into another payee",hidden?"Show in suggestions":"Hide from suggestions"},(d,n)->{
            if(n==0){LinearLayout f=form();f.addView(label("Every transaction and upcoming transaction with this payee gets the new name.",13,muted,false));EditText name=field(f,"New name",false);name.setText(payee);dialog("Rename payee",f,()->{if(budget.renamePayee(payee,name.getText().toString())==0)throw new IllegalArgumentException("That payee no longer has transactions.");});}
            else if(n==1){List<String> others=new ArrayList<>(budget.allPayees());others.removeIf(o->o.equalsIgnoreCase(payee));if(others.isEmpty()){toast("There's no other payee to merge with.");return;}
                new AlertDialog.Builder(this).setTitle("Merge "+payee+" into…").setItems(others.toArray(new String[0]),(d2,k)->{String keep=others.get(k);
                    new AlertDialog.Builder(this).setTitle("Merge into "+keep+"?").setMessage("Transactions with "+payee+" become "+keep+". "+keep+" is the one kept.").setNegativeButton("Cancel",null).setPositiveButton("Merge",(d3,w)->{if(change(()->budget.mergePayees(Collections.singletonList(payee),keep)))toast("Merged into "+keep+".");}).show();}).show();}
            else if(change(()->budget.hidePayee(payee,!hidden)))toast(hidden?"Suggested again.":"Hidden from suggestions. Its transactions stay.");
        }).show();
    }
    private String ruleText(Budget.Rule r){Budget.Category c=budget.category(r.category);return "Contains \""+r.contains+"\" → "+(r.rename.isEmpty()?"":"rename to "+r.rename)+(r.rename.isEmpty()||c==null?"":", ")+(c==null?"":"category "+c.name);}
    private void rules(){
        String[] rows=budget.rules.stream().map(this::ruleText).toArray(String[]::new);
        AlertDialog.Builder d=new AlertDialog.Builder(this).setTitle("Import rules").setNegativeButton("Close",null).setPositiveButton("+ Add rule",(x,w)->editRule(-1));
        if(rows.length==0)d.setMessage("When a bank statement's payee contains some text, a rule renames it and/or gives it a category. Rules are checked in order, ignoring capitals; the first match wins. Without a match, the payee's last category is used as before.");
        else d.setItems(rows,(x,n)->editRule(n));d.show();
    }
    private void editRule(int index){
        Budget.Rule old=index>=0&&index<budget.rules.size()?budget.rules.get(index):null;List<Budget.Category> cats=visibleCategories(old==null?null:budget.category(old.category));
        String[] names=new String[cats.size()+1];names[0]="Keep the usual guess";for(int i=0;i<cats.size();i++)names[i+1]=cats.get(i).name;
        LinearLayout f=form();f.addView(label("For bank statement imports. Capitals don't matter; the first matching rule wins.",13,muted,false));EditText contains=field(f,"Payee contains (e.g. WOOLWORTHS)",false);EditText rename=field(f,"Rename to (optional)",false);Spinner cat=spinner(f,"Category (optional)",names,old==null?0:cats.indexOf(budget.category(old.category))+1);
        if(old!=null){contains.setText(old.contains);rename.setText(old.rename);String text=old.contains;f.addView(button("Remove rule",()->{if(change(()->budget.rules.removeIf(r->r.contains.equals(text))))for(AlertDialog editor:new ArrayList<>(editors))editor.dismiss();}));}
        dialog(old==null?"New import rule":"Edit import rule",f,()->{int c=cat.getSelectedItemPosition();Budget.Rule r=new Budget.Rule(contains.getText().toString(),rename.getText().toString(),c==0?"":cats.get(c-1).id);
            int i=-1;if(old!=null){for(int k=0;k<budget.rules.size()&&i<0;k++)if(budget.rules.get(k).contains.equals(old.contains))i=k;if(i<0)throw new IllegalArgumentException("That rule was removed meanwhile.");}
            budget.validate(r,i<0?null:budget.rules.get(i));if(i<0)budget.rules.add(r);else budget.rules.set(i,r);}); // by its text: a failed save reads the budget in afresh
    }
    // Backup, restore and export go through Android's file picker, so MyBudget needs no storage permission.
    private static final int BACKUP=1,RESTORE=2,EXPORT=3,IMPORT=4,AUTO=5,PHOTO=6;
    private String folderName(String tree){try{String id=android.provider.DocumentsContract.getTreeDocumentId(Uri.parse(tree));int c=id.lastIndexOf(':');String n=c>=0?id.substring(c+1):id;return n.isEmpty()?"the folder you chose":n;}catch(Exception e){return "the folder you chose";}}
    private android.content.SharedPreferences prefs(){return getSharedPreferences("budget",0);}
    private String when(String iso){try{return LocalDateTime.parse(iso).format(DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a",Locale.forLanguageTag("en-AU")));}catch(Exception e){return "an unknown date";}}
    private void pick(Intent intent,int request){intent.addCategory(Intent.CATEGORY_OPENABLE);try{startActivityForResult(intent,request);}catch(android.content.ActivityNotFoundException e){toast("No app on this device can save or open files.");}}
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);Uri uri=data==null?null:data.getData();if(request==PHOTO){java.util.function.Consumer<Uri> target=photoTarget;photoTarget=null;if(result==RESULT_OK&&uri!=null&&target!=null)target.accept(uri);photoReturned();return;}if(result!=RESULT_OK||uri==null||!storageReadable)return;
        if(request==AUTO){try{getContentResolver().takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION);}catch(Exception e){toast("MyBudget couldn't keep access to that folder. Choose another.");return;}prefs().edit().putString("auto_backup_tree",uri.toString()).remove("auto_backup_last").remove("auto_backup_error").apply();AutoBackup.schedule(this);String tree=uri.toString();new Thread(()->{String e=AutoBackup.run(this,true);runOnUiThread(()->{toast(e==null?"Automatic backup is on. First backup saved.":e);render();});}).start();return;}
        if(request==IMPORT){List<List<String>> rows;try{rows=CsvImport.parse(read(uri));}catch(Exception e){toast(e instanceof IOException&&e.getMessage()!=null?e.getMessage():"Could not read that file.");return;}if(rows.isEmpty()){toast("That file has no rows.");return;}importDialog(rows);return;}
        if(request==RESTORE){BudgetStore.Backup backup;try{backup=BudgetStore.readBackup(read(uri));}catch(Exception e){String m=e.getMessage();toast((e instanceof org.json.JSONException||e instanceof IOException)&&m!=null?m:"Could not read that file.");return;}confirmRestore(backup);return;}
        try{write(uri,request==BACKUP?BudgetStore.backup(budget,LocalDateTime.now()):"﻿"+budget.csv());toast(request==BACKUP?(photoCount()>0?"Budget backed up. Photos stay on this phone.":"Budget backed up."):"Transactions exported.");}
        catch(Exception e){try{android.provider.DocumentsContract.deleteDocument(getContentResolver(),uri);}catch(Exception ignored){}toast(request==BACKUP?"Could not save the backup.":"Could not save the export.");}
    }
    /** Matches a statement's columns (remembered by header name for next time), then imports into one account. */
    private void importDialog(List<List<String>> rows){
        List<Budget.Account> accounts=openAccounts();accounts.removeIf(Budget.Account::tracking);if(accounts.isEmpty()){toast("Add an account first.");return;} // statements go into budget accounts
        List<String> first=rows.get(0);int columns=0;for(List<String> r:rows)columns=Math.max(columns,r.size());boolean header=CsvImport.looksLikeHeader(first);
        String[] names=new String[columns],withNone=new String[columns+1];withNone[0]="None: one signed amount column";
        for(int i=0;i<columns;i++){String sample=rows.size()>(header?1:0)&&i<rows.get(header?1:0).size()?rows.get(header?1:0).get(i):"";names[i]=(header&&i<first.size()&&!first.get(i).isEmpty()?first.get(i):"Column "+(i+1))+(sample.isEmpty()?"":"  (e.g. "+(sample.length()>24?sample.substring(0,24)+"…":sample)+")");withNone[i+1]=names[i];}
        // Guess from header words, or from what was used last time with the same headers.
        int date=guess(first,header,new String[]{"date"},0),payee=guess(first,header,new String[]{"description","payee","narrative","details","merchant","memo"},Math.min(1,columns-1)),amount=guess(first,header,new String[]{"amount","credit"},Math.min(2,columns-1)),out=-1;
        if(header){int debit=guess(first,true,new String[]{"debit","out","withdrawal"},-1);if(debit>=0&&debit!=amount)out=debit;}
        String saved=prefs().getString("import_columns",null);if(saved!=null&&header){String[] p=saved.split("\u0001");if(p.length==5&&p[0].equals(String.join("\u0002",first))){date=Integer.parseInt(p[1]);payee=Integer.parseInt(p[2]);amount=Integer.parseInt(p[3]);out=Integer.parseInt(p[4]);}}
        LinearLayout f=form();f.addView(label(count(rows.size()-(header?1:0),"row","rows")+" in the file.",14,ink,true));CheckBox hasHeader=new CheckBox(this);hasHeader.setText("The first row is column names");hasHeader.setChecked(header);hasHeader.setMinHeight(dp(48));f.addView(hasHeader);
        Spinner dateCol=spinner(f,"Date",names,date),payeeCol=spinner(f,"Payee or description",names,payee),amountCol=spinner(f,"Amount (or money in)",names,amount),outCol=spinner(f,"Money out (if it's a separate column)",withNone,out+1),account=spinner(f,"Into account",accounts.stream().map(a->a.name).toArray(String[]::new),0);
        f.addView(label("Imported rows are marked cleared and wait for you to review them. Import rules (Settings > Payees) apply first. Dates in the future or before the account opened are skipped.",12,muted,false));
        ScrollView scroll=new ScrollView(this);scroll.addView(f);AlertDialog d=new AlertDialog.Builder(this).setTitle("Import transactions").setView(scroll).setNegativeButton("Cancel",null).setPositiveButton("Import",null).create();
        d.setOnShowListener(v->d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(w->{
            boolean h=hasHeader.isChecked();int dc=dateCol.getSelectedItemPosition(),pc=payeeCol.getSelectedItemPosition(),ac=amountCol.getSelectedItemPosition(),oc=outCol.getSelectedItemPosition()-1;
            String format=CsvImport.detectDateFormat(rows,dc,h);if(format==null){toast("MyBudget can't read the dates in that column. Check the Date column.");return;}
            Budget.Account a=accounts.get(account.getSelectedItemPosition());CsvImport.Result[] r=new CsvImport.Result[1];
            if(!change(()->r[0]=CsvImport.run(budget,rows,h,dc,pc,ac,oc,format,accountById(a.id))))return;
            if(h)prefs().edit().putString("import_columns",String.join("\u0002",first)+"\u0001"+dc+"\u0001"+pc+"\u0001"+ac+"\u0001"+oc).apply();
            d.dismiss();CsvImport.Result x=r[0];List<String> skipped=new ArrayList<>();if(x.duplicates>0)skipped.add(x.duplicates+" already there");if(x.future>0)skipped.add(x.future+" in the future");if(x.beforeOpening>0)skipped.add(x.beforeOpening+" before the account opened");if(x.unreadable>0)skipped.add(x.unreadable+" unreadable");
            String sorting=null;for(Budget.Entry e:x.entries){Budget.Category c=budget.category(e.category);if(c!=null&&c.name.equals(CsvImport.TO_CATEGORIZE))sorting=c.id;}
            new AlertDialog.Builder(this).setTitle(count(x.added,"transaction","transactions")+" imported").setMessage((skipped.isEmpty()?"Nothing was skipped.":"Skipped: "+String.join(", ",skipped)+".")+(x.matchedRules>0?" "+count(x.matchedRules,"matched rule","matched rules")+".":"")+(x.added>0?"\n\nThey're marked to review: check them in Transactions and approve them.":"")+(sorting!=null?"\n\nSome are in "+CsvImport.TO_CATEGORIZE+": open them in Transactions to choose their category.":"")).setPositiveButton("OK",null).show();
            // Transactions shows the statement's dates (not just the month on screen), so the new rows are all there.
            if(x.added>0){String from=null,to=null;for(Budget.Entry e:x.entries){if(from==null||e.date.compareTo(from)<0)from=e.date;if(to==null||e.date.compareTo(to)>0)to=e.date;}clearFilters();fromFilter=from;toFilter=to;if(sorting!=null)categoryFilter=sorting;tab="Spending";render();}
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
    /** Deletes photo files no transaction uses (a form cancelled after adding one, a deleted transaction); keeps those Undo restore / Undo budget reset could bring back. */
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
                try{String raw=BudgetStore.encode(b);if(!prefs().edit().putString("data",raw).putString("before_restore",current==null?"":current).putString("before_restore_at",LocalDateTime.now().withNano(0).toString()).remove("before_reset").remove("before_reset_at").commit())throw new IllegalStateException();loaded=raw;}
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
                if(previous==null){budget=new Budget();load();}else{budget=previous;loaded=before;}for(AlertDialog editor:new ArrayList<>(editors))editor.dismiss();render();toast("Restore undone.");
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
    private String targetDescription(Budget.Category c){String by=c.dueDay>0?" by the "+ordinal(c.dueDay):"";if(c.targetType.equals("Monthly"))return "Set aside "+money(c.target)+by+" each month";if(c.targetType.equals("Balance"))return "Save to "+money(c.target)+(c.due.isEmpty()?"":" by "+YearMonth.parse(c.due).format(DateTimeFormatter.ofPattern("MMM yyyy")));
        if(c.targetType.equals("Debt"))return "Pay "+money(c.target)+by+" on this debt each month";
        if(c.targetType.equals("Weekly")){int n=Budget.weekdaysIn(month,c.weekday);return (c.weeklyRefill?"Refill to ":"Set aside ")+money(c.target)+" every "+dayName(c.weekday)+" · "+n+" this month = "+money(c.target*n);} // e.g. "$40 every Monday · 5 this month = $200"
        if(c.targetType.equals("ByDate")){LocalDate d=Budget.dueFor(c,month);String every=c.repeatMonths>0?", then every "+c.repeatMonths+" months":"";return "Save "+money(c.target)+" by "+pretty(d==null?c.dueDate:d.toString())+every;}
        return "Refill to "+money(c.target)+by+" each month";}
    static String dayName(int weekday){return DayOfWeek.of(weekday).getDisplayName(java.time.format.TextStyle.FULL,Locale.forLanguageTag("en-AU"));}
    /** What a tab is called on screen (MyBudget's own names; the keys stay as saved in older sessions). */
    static String tabTitle(String tab){switch(tab){case"Plan":return "Budget";case"Spending":return "Transactions";case"Reflect":return "Reports";default:return tab;}}
    private void render(){
        budget.fromPlanner.clear();budget.fromPlanner.addAll(PlannerBills.read(this,budget)); // Planner may have sent a new list meanwhile
        root=column();root.setBackgroundColor(canvas);root.setPadding(dp(16),dp(12),dp(16),dp(8));setContentView(root);
        root.setOnApplyWindowInsetsListener((v,i)->{root.setPadding(dp(16),i.getSystemWindowInsetTop()+dp(8),dp(16),i.getSystemWindowInsetBottom()+dp(4));return i;});
        LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);
        ImageView logo=new ImageView(this);logo.setImageResource(R.drawable.brand_mark);logo.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);LinearLayout.LayoutParams logoSize=new LinearLayout.LayoutParams(dp(40),dp(40));logoSize.setMargins(0,0,dp(12),0);header.addView(logo,logoSize);
        LinearLayout brand=column();TextView wordmark=label("MyBudget",21,ink,true);wordmark.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));wordmark.setLetterSpacing(0.01f);wordmark.setPadding(0,0,0,0);
        SpannableString brandName=new SpannableString("MyBudget");brandName.setSpan(new android.text.style.ForegroundColorSpan(blue),0,2,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);wordmark.setText(brandName);brand.addView(wordmark);
        TextView tagline=label("Your money. Your plan.",11,muted,false);tagline.setPadding(0,dp(2),0,0);brand.addView(tagline);header.addView(brand,new LinearLayout.LayoutParams(0,-2,1));
        Button overflow=button("\u22ee",()->{});overflow.setContentDescription("More options");overflow.setTextSize(26);overflow.setMinWidth(0);overflow.setMinimumWidth(0);overflow.setPadding(0,0,0,0);overflow.setBackground(bg(Color.TRANSPARENT));overflow.setOnClickListener(v->options(v));header.addView(overflow,new LinearLayout.LayoutParams(dp(48),dp(48)));root.addView(header);root.addView(label(tabTitle(tab),28,ink,true));
        LinearLayout months=new LinearLayout(this);months.setGravity(Gravity.CENTER_VERTICAL);Button previous=button("\u2039",()->{month=month.minusMonths(1);render();});previous.setTextSize(26);previous.setContentDescription("Previous month");previous.setBackground(bg(Color.TRANSPARENT));months.addView(previous,new LinearLayout.LayoutParams(dp(48),dp(48)));TextView title=label(month.format(DateTimeFormatter.ofPattern("MMMM yyyy")),16,ink,true);title.setGravity(Gravity.CENTER);months.addView(title,new LinearLayout.LayoutParams(0,-2,1));Button next=button("\u203a",()->{month=month.plusMonths(1);render();});next.setTextSize(26);next.setContentDescription("Next month");next.setBackground(bg(Color.TRANSPARENT));months.addView(next,new LinearLayout.LayoutParams(dp(48),dp(48)));if(!tab.equals("Settings"))root.addView(months);
        ScrollView scroll=new ScrollView(this);content=column();scroll.addView(content);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        switch(tab){case"Settings":settings();break;case"Home":home();break;case"Plan":plan();break;case"Spending":spending();break;case"Accounts":accounts();break;default:reflect();}
        if(tab.equals("Settings")){root.addView(button("Back",this::closeSettings));return;}
        LinearLayout nav=new LinearLayout(this);nav.setPadding(0,dp(6),0,0);String[] names={"Home","Plan","Spending","Accounts","Reflect"};int[] icons={R.drawable.nav_home,R.drawable.nav_plan,R.drawable.nav_spending,R.drawable.nav_accounts,R.drawable.nav_reflect};for(int i=0;i<names.length;i++){String name=names[i];boolean selected=tab.equals(name);Button b=button(tabTitle(name),()->{tab=name;accountFilter="";render();});b.setTextSize(10);b.setPadding(dp(2),dp(7),dp(2),dp(5));b.setBackground(bg(selected?primary:Color.TRANSPARENT));b.setTextColor(selected?Color.WHITE:muted);b.setSelected(selected);b.setContentDescription(tabTitle(name)+(selected?", selected":""));if(selected)b.setTypeface(null,Typeface.BOLD);Drawable icon=getDrawable(icons[i]).mutate();icon.setTint(selected?Color.WHITE:muted);icon.setBounds(0,0,dp(20),dp(20));b.setCompoundDrawables(null,icon,null,null);b.setCompoundDrawablePadding(dp(4));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(60),1);p.setMargins(dp(1),0,dp(1),0);nav.addView(b,p);}root.addView(nav);
    }
    private void readyCard(){LinearLayout c=card();c.setBackground(bg(primary));c.addView(label("TO BUDGET",11,Color.WHITE,true));c.addView(label(money(budget.spendable(month)),30,Color.WHITE,true));long future=budget.futureAssigned(month);c.addView(label(future>0?money(future)+" reserved in future months":"Give the money you have a purpose.",12,Color.WHITE,false));}
    private int overspent(){int n=0;for(Budget.Category c:budget.categories)if(budget.toCover(c,month)>0)n++;return n;}
    private void home(){
        readyCard();content.addView(button("+ Add transaction",()->transaction(null)));
        if(budget.accounts.isEmpty()){LinearLayout c=card();c.addView(label("Start with the money you have",21,ink,true));c.addView(label("Add your bank, savings or cash account and its current balance. Then assign that money in your plan.",15,muted,false));c.addView(button("Add your first account",this::addAccount));}
        // Needs attention: each alert opens where it's dealt with.
        content.addView(label("Needs attention",20,ink,true));int alerts=0;
        for(Budget.Category c:budget.categories){long cover=budget.toCover(c,month);if(cover<=0)continue;alerts++;String id=c.id;LinearLayout a=alert(c.name+" is overspent by "+money(cover),red,"Cover it with money from another category or To budget.",()->categoryDetails(budget.category(id)));a.addView(button("Cover",()->cover(id)));}
        long ready=budget.spendable(month);
        if(ready>0){alerts++;alert(money(ready)+" in To budget is waiting to be assigned",blue,"Give it a job in Budget, or use Fund targets.",()->{tab="Plan";render();});}
        else if(ready<0){alerts++;alert("To budget is below $0 by "+money(-ready),red,"More is assigned than you have. Return money from a category in Budget.",()->{tab="Plan";render();});}
        LocalDate today=LocalDate.now();List<Budget.Scheduled> soon=budget.dueWithin(today,7);
        for(Budget.Scheduled s:soon.subList(0,Math.min(5,soon.size()))){alerts++;LocalDate d=LocalDate.parse(s.next);boolean planner=budget.fromPlanner.contains(s);String id=s.id;
            alert(s.payee+" · "+(planner&&s.amount==0?"no amount yet":money(s.amount)),d.isBefore(today)?amber:ink,(d.isBefore(today)?"Overdue since "+pretty(s.next):d.equals(today)?"Due today":"Due "+pretty(s.next))+(planner?" · a bill in Planner":" · tap to enter, skip or edit"),planner?()->{clearFilters();tab="Spending";render();}:()->dueActions(id));}
        if(soon.size()>5)content.addView(button("All "+soon.size()+" due this week in Transactions",()->{clearFilters();tab="Spending";render();}));
        int review=budget.toReview().size();if(review>0){alerts++;alert(count(review,"imported transaction","imported transactions")+" to review",amber,"Check each one's payee and category, then approve it.",this::review);}
        if(alerts==0)content.addView(label("All set: nothing needs your attention.",15,green,true));
        long need=0;for(Budget.Category c:budget.categories)if(!c.hidden)need+=budget.fundNeed(c,month);
        LinearLayout progress=card();progress.addView(label("Your funding progress",19,ink,true));progress.addView(label(money(need)+" still needed this month",16,need>0?amber:green,true));progress.addView(label("Targets tell you what to fund. They do not create money.",14,muted,false));
        // Priority categories: the ones pinned from their menu in Budget.
        content.addView(label("Priority categories",20,ink,true));List<Budget.Category> pinned=budget.pinned();for(Budget.Category c:pinned)categoryCard(c);
        if(pinned.isEmpty())content.addView(label("Pin up to "+Budget.PINS+" categories to keep an eye on them here: tap a category in Budget, then Pin to Home.",14,muted,false));
    }
    /** A Home alert: a card with a coloured title and a line of detail; tapping it opens where it's dealt with. */
    private LinearLayout alert(String title,int color,String detail,Runnable open){LinearLayout c=card();c.addView(label(title,16,color,true));c.addView(label(detail,13,muted,false));c.setOnClickListener(v->open.run());return c;}
    private void categoryCard(Budget.Category c){
        LinearLayout row=card();long available=budget.available(c,month),need=budget.needed(c,month),cover=budget.toCover(c,month);int status=cover>0?red:need>0||available<0?amber:green;
        LinearLayout heading=new LinearLayout(this);heading.setGravity(Gravity.CENTER_VERTICAL);TextView name=label(c.name,16,ink,true);name.setPadding(0,0,dp(8),0);heading.addView(name,new LinearLayout.LayoutParams(0,-2,1));LinearLayout balance=column();TextView caption=label("Available",10,muted,false);caption.setGravity(Gravity.END);caption.setPadding(0,0,0,0);balance.addView(caption);TextView value=label(money(available),20,status,true);value.setGravity(Gravity.END);value.setPadding(0,0,0,0);value.setAutoSizeTextTypeUniformWithConfiguration(12,20,1,android.util.TypedValue.COMPLEX_UNIT_SP);balance.addView(value,new LinearLayout.LayoutParams(-1,dp(27)));heading.addView(balance,new LinearLayout.LayoutParams(dp(128),-2));row.addView(heading);
        LinearLayout details=new LinearLayout(this);TextView assigned=label("Assigned  "+money(budget.assigned(c,month)),11,muted,false),activity=label("Activity  "+money(budget.activity(c,month)),11,muted,false);details.addView(assigned,new LinearLayout.LayoutParams(0,-2,1));activity.setGravity(Gravity.END);details.addView(activity,new LinearLayout.LayoutParams(0,-2,1));row.addView(details);
        // Progress: Set aside and debt payments by this month's Assigned; a balance by Available; by date toward the whole amount; refills (and weekly) toward this month's amount.
        if(c.target>0){String t=c.targetType;long goal=t.equals("Weekly")?Budget.weeklyGoal(c,month):c.target,base=t.equals("Monthly")||t.equals("Debt")?budget.assigned(c,month):t.equals("Balance")?available:t.equals("ByDate")?budget.carried(c,month)+budget.assigned(c,month):goal-need;progress(row,base,goal,status);row.addView(label(targetDescription(c),11,muted,false));
            boolean passed=t.equals("ByDate")&&Budget.dueFor(c,month)==null;row.addView(c.snoozed.equals(month.toString())?label("Target snoozed this month",12,muted,true):passed?label("Due date passed: it asks for nothing more",12,muted,true):label(need==0?"Funded for this month":money(need)+" left to fund this month",12,need>0?amber:green,true));}
        Budget.Pace pace=budget.pace(c,month,LocalDate.now());if(pace!=null)row.addView(label("Spending faster than the month: "+pace.spent+"% spent, "+pace.elapsed+"% of the month gone",12,amber,false)); // the current month only
        if(!c.note.isEmpty())row.addView(label(c.note,12,muted,false));
        long upcoming=budget.upcoming(c,month);if(upcoming>0)row.addView(label("Upcoming bills this month: "+money(upcoming),12,muted,false));
        long onCredit=budget.creditOverspent(c,month);
        if(available<0&&onCredit>=-available)row.addView(label("Overspent on a credit card by "+money(-available)+": it becomes card debt unless you cover it",12,amber,true));
        else if(cover>0)row.addView(label("Overspent by "+money(cover)+" - tap to cover",12,red,true));
        else if(available<0)row.addView(label("Below $0 by a refund or credit on the card: it carries on, with nothing to cover",12,amber,false));
        if(c.payment()){Budget.Account card=budget.account(c.cardAccount);if(card!=null){long owed=-budget.balance(card,false);row.addView(label("Pays "+card.name+(owed>0?" · owed "+money(owed):" · paid off"),12,muted,false));}}
        row.setOnClickListener(v->categoryDetails(c));
    }
    private void plan(){
        monthNoteRow();readyCard();
        String resetAt=prefs().getString("before_reset_at",null);
        if(resetAt!=null){LinearLayout r=card();r.addView(label("Budget reset on "+when(resetAt)+". Assign your money again by today's priorities.",13,muted,false));LinearLayout buttons=new LinearLayout(this);Button undo=button("Undo budget reset",this::undoPlanReset),keep=button("Keep",()->{prefs().edit().remove("before_reset").remove("before_reset_at").apply();render();});buttons.addView(undo,new LinearLayout.LayoutParams(0,-2,2));LinearLayout.LayoutParams kp=new LinearLayout.LayoutParams(0,-2,1);kp.setMargins(dp(8),0,0,0);buttons.addView(keep,kp);r.addView(buttons);}
        if(overspent()>0)content.addView(label(count(overspent(),"category","categories")+" overspent - tap to cover",14,red,true));
        content.addView(button("Fund targets",this::autoAssign));LinkedHashSet<String> groups=new LinkedHashSet<>();for(Budget.Category c:budget.categories)if(!c.hidden)groups.add(c.group);
        for(String group:groups){content.addView(label(group,18,blue,true));for(Budget.Category c:budget.categories)if(!c.hidden&&c.group.equals(group))categoryCard(c);}
        content.addView(button("+ Add category",()->editCategory(null)));content.addView(button("Move money",this::move));
        List<Budget.Category> hidden=new ArrayList<>();long held=0;for(Budget.Category c:budget.categories)if(c.hidden){hidden.add(c);held+=budget.available(c,month);}
        if(!hidden.isEmpty()){content.addView(button((showHidden?"Collapse hidden categories (":"Show hidden categories (")+hidden.size()+")"+(held!=0?" · holds "+money(held):""),()->{showHidden=!showHidden;render();}));if(showHidden)for(Budget.Category c:hidden)categoryCard(c);}
    }
    /** The month's note (top of Budget): the text with a small Edit, or a small "+ Note" when there's none. */
    private void monthNoteRow(){
        String note=budget.monthNote(month);LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(note.isEmpty()?new View(this):label(note,13,ink,false),new LinearLayout.LayoutParams(0,-2,1));
        Button edit=button(note.isEmpty()?"+ Note for "+month.format(DateTimeFormatter.ofPattern("MMMM")):"Edit",this::editMonthNote);edit.setTextSize(12);edit.setMinHeight(dp(40));edit.setMinimumHeight(dp(40));edit.setBackground(bg(Color.TRANSPARENT));edit.setContentDescription(note.isEmpty()?"Add a note for this month":"Edit this month's note");row.addView(edit,new LinearLayout.LayoutParams(-2,-2));content.addView(row);
    }
    private void editMonthNote(){
        YearMonth m=month;LinearLayout f=form();f.addView(label("A reminder for "+m.format(DateTimeFormatter.ofPattern("MMMM yyyy"))+", shown at the top of Budget. Up to "+Budget.MONTH_NOTE_MAX+" characters; leave it empty to remove it.",13,muted,false));
        EditText text=field(f,"Note for this month",false);text.setSingleLine(false);text.setMaxLines(5);text.setFilters(new InputFilter[]{new InputFilter.LengthFilter(Budget.MONTH_NOTE_MAX)});text.setText(budget.monthNote(m));
        dialog("Month note",f,()->budget.setMonthNote(m,text.getText().toString()));
    }
    private void categoryDetails(Budget.Category c){
        List<String> names=new ArrayList<>();List<Runnable> actions=new ArrayList<>();String id=c.id;
        if(budget.toCover(c,month)>0){names.add("Cover overspending");actions.add(()->cover(id));}
        names.add("Assign or return money");actions.add(()->assign(c));names.add("Move money");actions.add(this::move);names.add("Edit category and target");actions.add(()->editCategory(c));
        names.add("View transactions");actions.add(()->{clearFilters();tab="Spending";categoryFilter=id;render();});
        names.add("Move up");actions.add(()->reorder(id,-1));names.add("Move down");actions.add(()->reorder(id,1));
        if(c.target>0){boolean snoozed=c.snoozed.equals(month.toString());String m=month.toString();names.add(snoozed?"Unsnooze target":"Snooze target this month");actions.add(()->change(()->categoryById(id).snoozed=snoozed?"":m));}
        boolean pinned=c.pinned;names.add(pinned?"Unpin from Home":"Pin to Home");actions.add(()->{if(change(()->budget.pin(categoryById(id),!pinned)))toast(pinned?"Unpinned from Home.":"Pinned to Home: it shows under Priority categories.");});
        names.add(c.hidden?"Unhide":"Hide");actions.add(()->hide(id,!c.hidden));if(!c.payment()){names.add("Delete category");actions.add(()->deleteCategory(id));}
        new AlertDialog.Builder(this).setTitle(c.name).setItems(names.toArray(new String[0]),(d,n)->actions.get(n).run()).show();
    }
    // Changes from a menu: the category is looked up again by id, in case the budget was reloaded meanwhile.
    private boolean change(Runnable action){try{commit(action);render();return true;}catch(Exception e){toast(e.getMessage());return false;}}
    private Budget.Category categoryById(String id){Budget.Category c=budget.category(id);if(c==null)throw new IllegalArgumentException("That category no longer exists.");return c;}
    private void reorder(String id,int direction){change(()->{if(!budget.reorder(categoryById(id),direction))throw new IllegalArgumentException(direction<0?"Already first in its group.":"Already last in its group.");});}
    private void hide(String id,boolean hidden){if(change(()->categoryById(id).hidden=hidden))toast(hidden?"Hidden. It's at the bottom of Budget; its money still counts.":"Back in your plan.");}
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
    /** Cover overspending: pick the envelope the money comes from (categories with money, or To budget). */
    private void cover(String id){
        Budget.Category c=budget.category(id);if(c==null)return;long missing=budget.toCover(c,month);if(missing<=0)return;
        List<Budget.Category> sources=new ArrayList<>();for(Budget.Category o:budget.categories)if(o!=c&&budget.available(o,month)>0)sources.add(o);sources.sort((a,b)->Long.compare(budget.available(b,month),budget.available(a,month)));
        long ready=budget.spendable(month);List<String> labels=new ArrayList<>();if(ready>0)labels.add("To budget ("+money(ready)+")");for(Budget.Category o:sources)labels.add(o.name+" ("+money(budget.available(o,month))+")");
        if(labels.isEmpty()){toast("No category has money to move. Record income or assign money first.");return;}
        new AlertDialog.Builder(this).setTitle("Cover "+money(missing)+" for "+c.name).setItems(labels.toArray(new String[0]),(d,n)->{
            boolean fromReady=ready>0&&n==0;Budget.Category from=fromReady?null:sources.get(n-(ready>0?1:0));long amount=Math.min(missing,fromReady?ready:budget.available(from,month));String fromId=fromReady?null:from.id;
            new AlertDialog.Builder(this).setTitle("Cover overspending").setMessage("Move "+money(amount)+" from "+(fromReady?"To budget":from.name)+" to "+c.name+"?"+(amount<missing?"\n\nThat covers part of it; "+money(missing-amount)+" stays overspent.":""))
                .setNegativeButton("Cancel",null).setPositiveButton("Cover",(d2,w)->change(()->{if(fromId==null)budget.assign(categoryById(id),month,amount);else budget.move(categoryById(fromId),categoryById(id),month,amount);})).show();
        }).show();
    }
    /** Spending's Upcoming section: scheduled transactions by date; due ones first and marked. */
    private void upcomingList(){
        if(budget.scheduled.isEmpty())return;List<Budget.Scheduled> list=new ArrayList<>(budget.scheduled);list.sort(Comparator.comparing(s->s.next));
        content.addView(label("Upcoming",18,blue,true));
        for(Budget.Scheduled s:list){if(!accountFilter.isEmpty()&&!s.account.equals(accountFilter))continue;boolean isDue=!LocalDate.parse(s.next).isAfter(LocalDate.now());Budget.Category c=budget.category(s.category);Budget.Account a=budget.account(s.account);
            LinearLayout row=card();row.addView(label(s.payee,17,ink,true));row.addView(label((s.category.isEmpty()?"To budget":c==null?"":c.name)+" / "+(a==null?"":a.name)+" / "+pretty(s.next)+" · "+repeatLabel(s),12,muted,false));
            row.addView(label(money(s.amount),17,s.amount>0?green:ink,true));if(isDue)row.addView(label("Due - tap to enter or skip",12,amber,true));String id=s.id;row.setOnClickListener(v->dueActions(id));}
    }
    /** Planner's upcoming bills: what's coming, with the category each is planned from (tap to choose or change it). */
    private void plannerList(){
        if(!accountFilter.isEmpty())return;
        if(PlannerBills.stale(this)&&!prefs().getString("planner_bills","[]").equals("[]")){content.addView(label("Planner's upcoming bills are from "+when(prefs().getString("planner_bills_at",""))+", so they aren't planned for. Open Planner to send them again.",12,muted,false));return;}
        if(budget.fromPlanner.isEmpty())return;
        content.addView(label("Coming up in Planner",18,blue,true));
        String at=prefs().getString("planner_bills_at",null);content.addView(label("Sent by Planner"+(at==null?"":" on "+when(at))+". They're added here when you mark them paid in Planner.",12,muted,false));
        for(Budget.Scheduled s:budget.fromPlanner){Budget.Category c=budget.category(s.category);LinearLayout row=card();row.addView(label(s.payee,17,ink,true));
            boolean overdue=LocalDate.parse(s.next).isBefore(LocalDate.now());row.addView(label((overdue?"Overdue since ":"Due ")+pretty(s.next)+" · "+(c==null?"no category yet":c.name),12,overdue?amber:muted,false));
            row.addView(label(s.amount==0?"No amount in Planner":money(s.amount),17,ink,true));if(c==null)row.addView(label("Choose a category to plan for it",12,amber,true));
            String key=s.billKey,payee=s.payee;row.setOnClickListener(v->chooseBillCategory(key,payee));}
    }
    private void chooseBillCategory(String billKey,String payee){
        List<Budget.Category> cats=visibleCategories(null);if(cats.isEmpty()){toast("Add a category first.");return;}
        new AlertDialog.Builder(this).setTitle("Plan "+payee+" from").setItems(cats.stream().map(c->c.name+" ("+money(budget.available(c,month))+")").toArray(String[]::new),(d,n)->{String id=cats.get(n).id;if(change(()->budget.billCategories.put(billKey,id)))toast("Planned from "+cats.get(n).name+". MyBudget suggests it when the bill is paid, too.");}).show();
    }
    private void spending(){
        content.addView(button("+ Add transaction",()->transaction(null)));
        int review=budget.toReview().size();if(review>0){LinearLayout c=card();c.addView(label(count(review,"imported transaction","imported transactions")+" to review",17,amber,true));c.addView(label("Check each one's payee and category, then approve it.",13,muted,false));c.addView(button("Review "+review+" imported",this::review));}
        upcomingList();plannerList();
        LinearLayout tools=new LinearLayout(this);Button filters=button(filtered()?"Filters (on)":"Filters",this::filters),payees=button("Payees",this::payees);tools.addView(filters,new LinearLayout.LayoutParams(0,-2,1));LinearLayout.LayoutParams pp=new LinearLayout.LayoutParams(0,-2,1);pp.setMargins(dp(8),0,0,0);tools.addView(payees,pp);content.addView(tools);
        EditText query=field(content,"Search payee, category or memo",false);query.setText(search);TextView summary=label("",13,blue,true);content.addView(summary);Button clear=button("Clear filters",()->{clearFilters();render();});content.addView(clear);LinearLayout list=column();content.addView(list);
        Runnable refresh=()->{summary.setText(filterSummary());summary.setVisibility(filtered()||!search.trim().isEmpty()?View.VISIBLE:View.GONE);clear.setVisibility(summary.getVisibility());fillEntries(list);};refresh.run();
        query.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int f){}public void onTextChanged(CharSequence s,int a,int b,int c){search=s.toString();refresh.run();}public void afterTextChanged(Editable e){}});
    }
    // Transactions filters: they combine (Budget.matches). Without a date range the list shows the month on screen.
    private boolean filtered(){return !accountFilter.isEmpty()||!categoryFilter.isEmpty()||flagFilter>=0||clearedFilter>=0||!fromFilter.isEmpty()||!toFilter.isEmpty();}
    private void clearFilters(){search="";accountFilter="";categoryFilter="";fromFilter="";toFilter="";flagFilter=-1;clearedFilter=-1;}
    private Budget.Filter currentFilter(){
        Budget.Filter f=new Budget.Filter();f.text=search;f.account=budget.account(accountFilter)==null?"":accountFilter;f.category=budget.category(categoryFilter)==null?"":categoryFilter;f.flag=flagFilter;f.cleared=clearedFilter;f.from=fromFilter;f.to=toFilter;
        if(f.from.isEmpty()&&f.to.isEmpty()){f.from=month.atDay(1).toString();f.to=month.atEndOfMonth().toString();}return f;
    }
    /** The current filter as one line: "Showing: Everyday · Groceries · Green flag · Uncleared · 1 Sep 2026 to 30 Sep 2026 · "coles"". */
    private String filterSummary(){
        List<String> parts=new ArrayList<>();Budget.Account a=budget.account(accountFilter);Budget.Category c=budget.category(categoryFilter);if(a!=null)parts.add(a.name);if(c!=null)parts.add(c.name);
        if(flagFilter==0)parts.add("No flag");else if(flagFilter>0)parts.add(budget.flagLabel(flagFilter)+" flag");if(clearedFilter>=0)parts.add(clearedFilter==1?"Cleared":"Uncleared");
        if(!fromFilter.isEmpty()&&!toFilter.isEmpty())parts.add(pretty(fromFilter)+" to "+pretty(toFilter));else if(!fromFilter.isEmpty())parts.add("From "+pretty(fromFilter));else if(!toFilter.isEmpty())parts.add("Up to "+pretty(toFilter));else parts.add(month.format(DateTimeFormatter.ofPattern("MMMM yyyy")));
        if(!search.trim().isEmpty())parts.add("\""+search.trim()+"\"");return "Showing: "+String.join(" · ",parts);
    }
    private void filters(){
        List<Budget.Account> accs=new ArrayList<>(budget.accounts);List<Budget.Category> cats=new ArrayList<>(budget.categories);
        String[] accNames=new String[accs.size()+1],catNames=new String[cats.size()+1],flags=new String[Budget.FLAGS.length+1];accNames[0]="Any account";catNames[0]="Any category";flags[0]="Any flag";
        for(int i=0;i<accs.size();i++)accNames[i+1]=accs.get(i).name+(accs.get(i).closed?" (closed)":"");for(int i=0;i<cats.size();i++)catNames[i+1]=cats.get(i).name;String[] choices=flagChoices();for(int i=0;i<choices.length;i++)flags[i+1]=choices[i];
        LinearLayout f=form();Spinner account=spinner(f,"Account",accNames,accs.indexOf(budget.account(accountFilter))+1),category=spinner(f,"Category",catNames,cats.indexOf(budget.category(categoryFilter))+1),flag=spinner(f,"Flag",flags,flagFilter+1),cleared=spinner(f,"Cleared",new String[]{"Cleared or not","Cleared","Uncleared"},clearedFilter<0?0:clearedFilter==1?1:2);
        f.addView(label("Dates (without them, the month on screen)",12,muted,true));CheckBox useFrom=new CheckBox(this);useFrom.setText("From");useFrom.setChecked(!fromFilter.isEmpty());f.addView(useFrom);EditText from=dateField(f,fromFilter.isEmpty()?month.atDay(1).toString():fromFilter);
        CheckBox useTo=new CheckBox(this);useTo.setText("To");useTo.setChecked(!toFilter.isEmpty());f.addView(useTo);EditText to=dateField(f,toFilter.isEmpty()?LocalDate.now().toString():toFilter);
        ScrollView scroll=new ScrollView(this);scroll.addView(f);AlertDialog d=new AlertDialog.Builder(this).setTitle("Filter transactions").setView(scroll).setNegativeButton("Cancel",null).setNeutralButton("Clear all",(x,w)->{clearFilters();render();}).setPositiveButton("Apply",null).create();
        d.setOnShowListener(v->d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(w->{
            String fromDay=useFrom.isChecked()?(String)from.getTag():"",toDay=useTo.isChecked()?(String)to.getTag():"";if(!fromDay.isEmpty()&&!toDay.isEmpty()&&fromDay.compareTo(toDay)>0){toast("The From date is after the To date.");return;}
            int a=account.getSelectedItemPosition(),c=category.getSelectedItemPosition(),cl=cleared.getSelectedItemPosition();accountFilter=a==0?"":accs.get(a-1).id;categoryFilter=c==0?"":cats.get(c-1).id;flagFilter=flag.getSelectedItemPosition()-1;clearedFilter=cl==0?-1:cl==1?1:0;fromFilter=fromDay;toFilter=toDay;d.dismiss();render();}));d.show();
    }
    /** Imported transactions waiting for review: approve each (or edit it, which approves it too), or all at once. */
    private void review(){
        List<Budget.Entry> list=budget.toReview();if(list.isEmpty()){render();return;}LinearLayout f=form();f.addView(label("Imported from a bank statement. Check the payee and category; editing one approves it.",13,muted,false));AlertDialog[] shown={null};
        for(Budget.Entry e:list.subList(0,Math.min(100,list.size()))){LinearLayout row=column();row.setPadding(0,dp(6),0,dp(6));row.addView(label(e.payee+"  "+money(e.amount),15,e.amount>0?green:ink,true));Budget.Account a=budget.account(e.account);row.addView(label(categoryName(e)+" / "+(a==null?"":a.name)+" / "+pretty(e.date),12,muted,false));
            LinearLayout buttons=new LinearLayout(this);String id=e.id;Button approve=button("Approve",()->{if(change(()->budget.approve(entryById(id)))){shown[0].dismiss();review();}}),edit=button("Edit",()->{shown[0].dismiss();Budget.Entry t=budget.entries.stream().filter(x->x.id.equals(id)).findFirst().orElse(null);if(t!=null)transaction(t);});
            buttons.addView(approve,new LinearLayout.LayoutParams(0,-2,1));LinearLayout.LayoutParams ep=new LinearLayout.LayoutParams(0,-2,1);ep.setMargins(dp(8),0,0,0);buttons.addView(edit,ep);row.addView(buttons);f.addView(row);}
        if(list.size()>100)f.addView(label("And "+(list.size()-100)+" more: Approve all includes them.",12,muted,false));
        ScrollView scroll=new ScrollView(this);scroll.addView(f);shown[0]=new AlertDialog.Builder(this).setTitle(count(list.size(),"transaction","transactions")+" to review").setView(scroll).setNegativeButton("Close",null).setPositiveButton("Approve all",(x,w)->{int[] n={0};if(change(()->n[0]=budget.approveAll()))toast(count(n[0],"transaction","transactions")+" approved.");}).show();
    }
    private Budget.Entry entryById(String id){for(Budget.Entry e:budget.entries)if(e.id.equals(id))return e;throw new IllegalArgumentException("That transaction no longer exists.");}
    // Money not given to a category goes into To budget (income and reconcile adjustments).
    private String categoryName(Budget.Entry e){if(e.split()){StringBuilder s=new StringBuilder("Split:");for(Budget.Split p:e.splits){Budget.Category c=budget.category(p.category);s.append(" ").append(c==null?"To budget":c.name).append(",");}return s.substring(0,s.length()-1);}Budget.Category c=budget.category(e.category);
        if(e.transfer())return c!=null?"Transfer · "+c.name:budget.crossing(e)?"Transfer · To budget":"Transfer"; // in or out of the budget, to or from a tracking account
        Budget.Account a=budget.account(e.account);return a!=null&&a.tracking()?"Tracking account":c==null?"To budget":c.name;}
    private void fillEntries(LinearLayout list){
        list.removeAllViews();int n=0;
        Budget.Account shown=accountFilter.isEmpty()?null:budget.account(accountFilter);Map<String,Long> running=shown==null?null:budget.runningBalances(shown); // one account's list: its balance after each transaction
        for(Budget.Entry e:budget.filter(currentFilter())){n++;LinearLayout row=column();row.setPadding(dp(14),dp(10),dp(14),dp(10));row.setBackground(bg(surface));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,dp(5),0,dp(5));list.addView(row,p);
            TextView payee=label(e.payee,17,ink,true);if(e.flag>0&&e.flag<FLAG_COLORS.length){SpannableString s=new SpannableString("● "+e.payee);s.setSpan(new android.text.style.ForegroundColorSpan(FLAG_COLORS[e.flag]),0,1,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);payee.setText(s);payee.setContentDescription(e.payee+", "+budget.flagLabel(e.flag)+" flag");}row.addView(payee);
            if(!e.approved)row.addView(label("To review · imported",12,amber,true));row.addView(label(categoryName(e)+" / "+budget.account(e.account).name+" / "+pretty(e.date),12,muted,false));row.addView(label(money(e.amount)+(e.cleared?"  Cleared":"  Uncleared"),17,e.amount>0?green:ink,true));if(running!=null&&running.containsKey(e.id))row.addView(label("Balance "+money(running.get(e.id)),12,muted,false));if(!e.memo.isEmpty())row.addView(label(e.memo,12,muted,false));if(!e.photo.isEmpty())row.addView(label("Photo attached",12,blue,false));row.setOnClickListener(v->transaction(e));}
        if(n==0)list.addView(label(fromFilter.isEmpty()&&toFilter.isEmpty()?"No matching transactions this month.":"No matching transactions in these dates.",15,muted,false));
    }
    private void accounts(){
        for(Budget.Account a:budget.accounts){if(a.closed||a.tracking())continue;LinearLayout c=card();c.addView(label(a.name+(a.credit()?" · credit card":""),20,ink,true));if(a.credit()){long owed=-budget.balance(a,false);Budget.Category p=budget.paymentCategory(a);long ready=p==null?0:Math.max(0,budget.available(p,month));c.addView(label(owed>0?"Owed "+money(owed):owed<0?"In credit "+money(-owed):"Paid off",28,ink,true));c.addView(label("Set aside for the payment: "+money(ready)+(owed>ready&&owed>0?" ("+money(owed-ready)+" not covered yet)":""),13,owed>ready&&owed>0?amber:green,true));c.addView(button("Make a payment",()->payCard(a.id)));}else c.addView(label(money(budget.balance(a,false)),28,ink,true));c.addView(label("Cleared "+money(budget.balance(a,true))+" / Uncleared "+money(budget.balance(a,false)-budget.balance(a,true)),13,muted,false));if(!a.reconciled.isEmpty())c.addView(label("Last reconciled "+pretty(a.reconciled),12,green,false));c.addView(button("View transactions",()->{clearFilters();accountFilter=a.id;tab="Spending";render();}));c.addView(button("Reconcile",()->reconcile(a)));c.addView(button("Edit account",()->editAccount(a.id)));}
        // Tracking accounts: off budget, in Net worth only.
        boolean anyTracking=false;for(Budget.Account a:budget.accounts){if(a.closed||!a.tracking())continue;if(!anyTracking){content.addView(label("Tracking accounts",18,blue,true));content.addView(label("Off budget: they count in Net worth only. Update their balance now and then.",13,muted,false));}anyTracking=true;
            LinearLayout c=card();c.addView(label(a.name+(a.liability?" · loan or debt":" · asset"),20,ink,true));long balance=budget.balance(a,false);c.addView(label(a.liability?(balance<0?"Owed "+money(-balance):"Paid off"):money(balance),28,ink,true));
            if(a.liability&&(a.rate>0||a.payment>0))c.addView(label(a.ratePercent().stripTrailingZeros().toPlainString()+"% a year · "+money(a.payment)+" "+a.frequency.toLowerCase(Locale.ROOT),13,muted,false));
            String id=a.id;c.addView(button("Update balance",()->updateBalance(id)));if(a.liability)c.addView(button("Payoff planner",()->payoffPlanner(id)));c.addView(button("View transactions",()->{clearFilters();accountFilter=id;tab="Spending";render();}));c.addView(button("Edit account",()->editAccount(id)));}
        content.addView(button("+ Add account",this::addAccount));if(openAccounts().size()>1)content.addView(button("Transfer between accounts",this::transfer));
        boolean anyClosed=false;for(Budget.Account a:budget.accounts)if(a.closed){if(!anyClosed)content.addView(label("Closed accounts",18,blue,true));anyClosed=true;LinearLayout c=card();c.addView(label(a.name,17,muted,true));c.addView(label("Closed. Its transactions stay in your history.",13,muted,false));c.addView(button("View transactions",()->{clearFilters();accountFilter=a.id;tab="Spending";render();}));c.addView(button("Reopen account",()->change(()->accountById(a.id).closed=false)));}content.addView(label("Checking, savings and cash accounts are pooled for your plan. Transfers change where money lives, not its purpose. Spending on a credit card moves the category's money to the card's payment category, ready to pay it. Moving money from your budget to a tracking account (an extra loan payment, an investment) is spending from a category; money from one into your budget is income to To budget.",14,muted,false));
    }
    private void reflect(){
        LinearLayout totals=card();totals.addView(label("This month's cash flow",20,ink,true));totals.addView(label("Income "+money(budget.income(month)),21,green,true));totals.addView(label("Spending "+money(budget.spending(month)),21,ink,true));totals.addView(label("Difference "+money(budget.income(month)-budget.spending(month)),17,blue,true));
        breakdownCard();trendsCard(); // card payments, transfers and tracking accounts aren't spending
        // Income vs spending, six months to this one: one axis from zero; the list below is the table view.
        LinearLayout flow=card();flow.addView(label("Income and spending",20,ink,true));
        int income=darkTheme?Color.parseColor("#3987E5"):Color.parseColor("#2A78D6"),spend=darkTheme?Color.parseColor("#D95926"):Color.parseColor("#EB6834");
        LinearLayout legend=new LinearLayout(this);legend.setGravity(Gravity.CENTER_VERTICAL);for(int k=0;k<2;k++){View swatch=new View(this);GradientDrawable s=new GradientDrawable();s.setColor(k==0?income:spend);s.setCornerRadius(dp(2));swatch.setBackground(s);LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(dp(10),dp(10));sp.setMargins(k==0?0:dp(14),0,dp(6),0);legend.addView(swatch,sp);legend.addView(label(k==0?"Income":"Spending",12,ink,false));}flow.addView(legend);
        long[] in=new long[6],out=new long[6];String[] names=new String[6];YearMonth[] ms=new YearMonth[6];for(int i=0;i<6;i++){YearMonth m=month.minusMonths(5-i);ms[i]=m;in[i]=budget.income(m);out[i]=budget.spending(m);names[i]=m.format(DateTimeFormatter.ofPattern("MMM"));}
        TextView picked=label("",13,ink,true);flow.addView(picked);java.util.function.IntConsumer show=i->picked.setText(ms[i].format(DateTimeFormatter.ofPattern("MMMM yyyy"))+":  in "+money(in[i])+"  ·  out "+money(out[i]));show.accept(5);
        CashFlowChart chart=new CashFlowChart(this,in,out,names,income,spend,muted,muted,buttonSurface,5,show);chart.setContentDescription("Income and spending chart for the last six months. The list below has the amounts.");flow.addView(chart,new LinearLayout.LayoutParams(-1,-2));
        incomeExpenseTable();
        // Net worth and Money age.
        LinearLayout worth=card();long now=budget.netWorth(month),change=now-budget.netWorth(month.minusMonths(1));worth.addView(label("Net worth",20,ink,true));worth.addView(label(money(now),26,ink,true));worth.addView(label((change>=0?"Up ":"Down ")+money(Math.abs(change))+" since the end of last month",13,muted,false));
        LinearLayout age=card();LocalDate until=month.isBefore(YearMonth.now())?month.atEndOfMonth():LocalDate.now();int days=budget.ageOfMoney(until);age.addView(label("Money age",20,ink,true));
        age.addView(label(days<0?"Not enough spending yet":count(days,"day","days"),26,days>=30?green:ink,true));age.addView(label("How old your money is when you spend it, over your last 10 outflows. 30 days or more means you're spending last month's income.",13,muted,false));
        content.addView(label("Last six months",20,ink,true));for(int i=5;i>=0;i--){YearMonth m=month.minusMonths(i);content.addView(label(m.format(DateTimeFormatter.ofPattern("MMM yyyy"))+"   In "+money(budget.income(m))+"   Out "+money(budget.spending(m)),13,muted,false));}
        content.addView(label("AUD / Saved on this device. Back up or export it in Settings. Bank sync is not included.",12,muted,false));
    }
    // Reports: spending breakdown (period, by category or group), spending trends, the income and expense table. Periods count back from the month on screen.
    private int period,trendMonths=6;private boolean byGroup;private String trendKey="";
    private static final String[] PERIODS={"This month","Last month","Last 3 months","This year"};
    // Categorical colours in fixed order (the validated chart palette, light and dark steps); Other is grey.
    private static final int[] SLICE_LIGHT={0xFF2A78D6,0xFFEB6834,0xFF1BAF7A,0xFFEDA100,0xFFE87BA4,0xFF008300,0xFF4A3AA7},SLICE_DARK={0xFF3987E5,0xFFD95926,0xFF199E70,0xFFC98500,0xFFD55181,0xFF008300,0xFF9085E9};
    private YearMonth[] periodRange(int p){return p==1?new YearMonth[]{month.minusMonths(1),month.minusMonths(1)}:p==2?new YearMonth[]{month.minusMonths(2),month}:p==3?new YearMonth[]{month.withMonth(1),month}:new YearMonth[]{month,month};}
    private String rangeText(YearMonth[] r){DateTimeFormatter f=DateTimeFormatter.ofPattern("MMMM yyyy");return r[0].equals(r[1])?r[0].format(f):r[0].format(f)+" to "+r[1].format(f);}
    private static String percent(int tenths){return tenths/10+"."+tenths%10+"%";}
    private Spinner choice(String[] names,int selection,String description){Spinner s=new Spinner(this);s.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,names));s.setSelection(Math.max(0,selection));s.setContentDescription(description);return s;}
    private void breakdownCard(){
        LinearLayout card=card();card.addView(label("Spending breakdown",20,ink,true));LinearLayout pickers=new LinearLayout(this);Spinner when=choice(PERIODS,period,"Period"),by=choice(new String[]{"By category","By group"},byGroup?1:0,"Show by category or by group");
        pickers.addView(when,new LinearLayout.LayoutParams(0,-2,1));pickers.addView(by,new LinearLayout.LayoutParams(0,-2,1));card.addView(pickers);LinearLayout body=column();card.addView(body);fillBreakdown(body);
        onPick(when,()->{if(period!=when.getSelectedItemPosition()){period=when.getSelectedItemPosition();fillBreakdown(body);}});onPick(by,()->{if(byGroup!=(by.getSelectedItemPosition()==1)){byGroup=!byGroup;fillBreakdown(body);}});
    }
    private void fillBreakdown(LinearLayout body){
        body.removeAllViews();YearMonth[] r=periodRange(period);List<Budget.Slice> slices=budget.breakdown(r[0],r[1],byGroup);long total=Budget.total(slices);body.addView(label(rangeText(r),12,muted,false));
        if(slices.isEmpty()){body.addView(label("No spending in this period.",14,muted,false));return;}
        long[] values=new long[slices.size()];int[] colors=new int[slices.size()];for(int i=0;i<values.length;i++){values[i]=slices.get(i).amount;colors[i]=slices.get(i).other?muted:(darkTheme?SLICE_DARK:SLICE_LIGHT)[i];}
        SpendingCharts.Donut donut=new SpendingCharts.Donut(this,values,colors,money(total),"spent",ink,muted);donut.setContentDescription("Spending breakdown chart: "+money(total)+" spent. The list below has each amount and share.");body.addView(donut,new LinearLayout.LayoutParams(-1,-2));
        // The table view: every slice with its amount and share, biggest first; tap for its transactions.
        for(int i=0;i<values.length;i++){Budget.Slice s=slices.get(i);LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setMinimumHeight(dp(44));
            View swatch=new View(this);GradientDrawable d=new GradientDrawable();d.setColor(colors[i]);d.setCornerRadius(dp(2));swatch.setBackground(d);LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(dp(10),dp(10));sp.setMargins(0,0,dp(8),0);row.addView(swatch,sp);
            row.addView(label(s.name,14,ink,false),new LinearLayout.LayoutParams(0,-2,1));TextView amount=label(money(s.amount)+"  ·  "+percent(s.tenths),14,ink,true);amount.setGravity(Gravity.END);row.addView(amount);
            row.setContentDescription(s.name+", "+money(s.amount)+", "+percent(s.tenths)+". Double tap for its transactions.");row.setOnClickListener(v->sliceTransactions(s,r));body.addView(row);}
        // Both views leave out categories that got back more than they spent: their refunds come off the total here, as in Spending.
        long refunds=budget.refunds(r[0],r[1]);if(refunds>0)body.addView(label("Refunds beyond spending (categories that got more back than they spent): "+money(refunds)+". Net spending: "+money(total-refunds)+".",12,muted,false));
        body.addView(label((byGroup?"Groups":"Categories")+" beyond the biggest "+Budget.SLICES+" are in Other. Tap a row for its transactions.",11,muted,false));
    }
    /** A slice's transactions in the period (Transactions with a category and date filter); a group or Other asks which category first. */
    private void sliceTransactions(Budget.Slice s,YearMonth[] r){
        if(s.ids.size()==1){showTransactions(s.ids.get(0),r);return;}Map<String,Long> spent=budget.spentBy(r[0],r[1]);List<String> ids=new ArrayList<>(s.ids);ids.sort((a,b)->Long.compare(spent.getOrDefault(b,0L),spent.getOrDefault(a,0L)));
        String[] names=ids.stream().map(id->{Budget.Category c=budget.category(id);return(c==null?"":c.name)+" ("+money(spent.getOrDefault(id,0L))+")";}).toArray(String[]::new);
        new AlertDialog.Builder(this).setTitle(s.name+": which category?").setItems(names,(d,n)->showTransactions(ids.get(n),r)).setNegativeButton("Cancel",null).show();
    }
    private void showTransactions(String categoryId,YearMonth[] r){clearFilters();categoryFilter=categoryId;fromFilter=r[0].atDay(1).toString();toFilter=r[1].atEndOfMonth().toString();tab="Spending";render();}
    private void trendsCard(){
        LinearLayout card=card();card.addView(label("Spending trends",20,ink,true));List<String> keys=new ArrayList<>(),names=new ArrayList<>();
        for(Budget.Category c:budget.categories)if(!c.payment()){keys.add("c:"+c.id);names.add(c.name);}for(String g:budget.groups()){keys.add("g:"+g);names.add("Group: "+g);}
        if(keys.isEmpty()){card.addView(label("Add a category to see its spending month by month.",14,muted,false));return;}
        if(!keys.contains(trendKey)){List<Budget.Slice> top=budget.breakdown(month,month,false);trendKey=top.isEmpty()||top.get(0).other?keys.get(0):"c:"+top.get(0).ids.get(0);} // the month's biggest spending first
        Spinner what=choice(names.toArray(new String[0]),keys.indexOf(trendKey),"Category or group"),span=choice(new String[]{"Last 6 months","Last 12 months"},trendMonths==12?1:0,"Months shown");
        card.addView(what);card.addView(span);LinearLayout body=column();card.addView(body);fillTrend(body);
        onPick(what,()->{String k=keys.get(what.getSelectedItemPosition());if(!k.equals(trendKey)){trendKey=k;fillTrend(body);}});onPick(span,()->{int m=span.getSelectedItemPosition()==1?12:6;if(m!=trendMonths){trendMonths=m;fillTrend(body);}});
    }
    private void fillTrend(LinearLayout body){
        body.removeAllViews();List<String> ids=trendKey.startsWith("g:")?budget.groupIds(trendKey.substring(2)):Collections.singletonList(trendKey.substring(2));int n=trendMonths;long[] v=budget.trend(ids,month,n);long average=Budget.average(v);
        String[] labels=new String[n];YearMonth[] ms=new YearMonth[n];for(int i=0;i<n;i++){ms[i]=month.minusMonths(n-1-i);labels[i]=ms[i].format(DateTimeFormatter.ofPattern(n>6?"MMMMM":"MMM"));}
        body.addView(label("Average "+money(average)+" a month",15,ink,true));TextView picked=label("",13,ink,true);body.addView(picked);java.util.function.IntConsumer show=i->picked.setText(ms[i].format(DateTimeFormatter.ofPattern("MMMM yyyy"))+":  "+money(v[i]));show.accept(n-1);
        SpendingCharts.Trend chart=new SpendingCharts.Trend(this,v,labels,average,(darkTheme?SLICE_DARK:SLICE_LIGHT)[0],muted,muted,buttonSurface,n-1,show);chart.setContentDescription("Spending per month for the last "+n+" months, averaging "+money(average)+". Show as a list has each month's amount.");body.addView(chart,new LinearLayout.LayoutParams(-1,-2));
        body.addView(label("Dashed line: the average. Tap a month for its amount.",11,muted,false));
        LinearLayout list=column();list.setVisibility(View.GONE);for(int i=n-1;i>=0;i--)list.addView(label(ms[i].format(DateTimeFormatter.ofPattern("MMM yyyy"))+"   "+money(v[i]),13,muted,false));
        Button toggle=button("Show as a list",()->{});toggle.setOnClickListener(x->{boolean open=list.getVisibility()!=View.VISIBLE;list.setVisibility(open?View.VISIBLE:View.GONE);toggle.setText(open?"Hide the list":"Show as a list");});body.addView(toggle);body.addView(list);
    }
    /** Income by payee and expenses by group and category over six months to the month on screen, with average and total columns (scroll sideways). */
    private void incomeExpenseTable(){
        LinearLayout card=card();card.addView(label("Income and expenses",20,ink,true));Budget.Table t=budget.incomeExpense(month.minusMonths(5),6);
        if(t.income.isEmpty()&&t.expenses.isEmpty()){card.addView(label("No income or spending in the six months to "+month.format(DateTimeFormatter.ofPattern("MMMM yyyy"))+".",14,muted,false));return;}
        card.addView(label("Six months to "+month.format(DateTimeFormatter.ofPattern("MMMM yyyy"))+". Scroll sideways for every month, the average and the total.",12,muted,false));
        String[] heads=new String[t.months.length+2];for(int i=0;i<t.months.length;i++)heads[i]=t.months[i].format(DateTimeFormatter.ofPattern("MMM yyyy"));heads[heads.length-2]="Average";heads[heads.length-1]="Total";
        LinearLayout table=new LinearLayout(this),names=column(),cols=column();HorizontalScrollView scroll=new HorizontalScrollView(this);scroll.addView(cols);table.addView(names,new LinearLayout.LayoutParams(dp(118),-2));table.addView(scroll,new LinearLayout.LayoutParams(0,-2,1));card.addView(table);scroll.post(()->scroll.fullScroll(View.FOCUS_RIGHT)); // opens on the newest month, its average and total
        tableRow(names,cols,"",heads,null,muted,true,0);
        tableRow(names,cols,"Income",null,null,blue,true,0);for(Budget.Row r:t.income)tableRow(names,cols,r.name,heads,r,ink,false,8);tableRow(names,cols,t.incomeTotal.name,heads,t.incomeTotal,ink,true,0);
        tableRow(names,cols,"Expenses",null,null,blue,true,0);for(Budget.Row r:t.expenses)tableRow(names,cols,r.name,heads,r,r.group?ink:muted,r.group,r.group?8:16);tableRow(names,cols,t.expenseTotal.name,heads,t.expenseTotal,ink,true,0);
        tableRow(names,cols,"Net (income − expenses)",heads,t.net,ink,true,0);
    }
    /** One table row: its name on the left, its months, average and total in the scrolling part ([r] null: [heads] themselves, or nothing for a section title). */
    private void tableRow(LinearLayout names,LinearLayout cols,String name,String[] heads,Budget.Row r,int color,boolean bold,int indent){
        TextView n=label(name,12,color,bold);n.setSingleLine(true);n.setEllipsize(TextUtils.TruncateAt.END);n.setGravity(Gravity.CENTER_VERTICAL);n.setPadding(dp(indent),0,dp(4),0);names.addView(n,new LinearLayout.LayoutParams(-1,dp(30)));
        LinearLayout line=new LinearLayout(this);int cells=heads==null?0:heads.length;
        for(int i=0;i<cells;i++){String text=r==null?heads[i]:money(i<r.amounts.length?r.amounts[i]:i==r.amounts.length?r.average():r.total());TextView c=label(text,12,r==null?muted:color,bold||i>=cells-2);c.setSingleLine(true);c.setGravity(Gravity.END|Gravity.CENTER_VERTICAL);c.setPadding(dp(4),0,dp(4),0);if(r!=null)c.setContentDescription(name+", "+heads[i]+", "+text);line.addView(c,new LinearLayout.LayoutParams(dp(96),dp(30)));}
        if(cells==0)line.addView(new View(this),new LinearLayout.LayoutParams(dp(1),dp(30)));cols.addView(line);
    }
    private LinearLayout form(){LinearLayout f=column();f.setPadding(dp(20),dp(8),dp(20),dp(8));return f;}
    // Amount boxes take quick maths ("45+12.50", see Budget.evaluate): the phone keypad has digits, . + - * / and brackets.
    static final int AMOUNT_INPUT=InputType.TYPE_CLASS_PHONE;
    private EditText field(LinearLayout f,String hint,boolean numeric){EditText e=new EditText(this);e.setHint(hint);e.setSingleLine(true);e.setTextColor(ink);if(numeric)e.setInputType(AMOUNT_INPUT);f.addView(e,new LinearLayout.LayoutParams(-1,-2));return e;}
    private AutoCompleteTextView suggestField(LinearLayout f,String hint,java.util.function.Supplier<List<String>> options){AutoCompleteTextView e=Suggest.box(this,f,hint,options);e.setTextColor(ink);return e;}
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
        // Saved meanwhile (split screen: an expense from Planner): open forms hold the old budget, so they close and nothing
        // is saved over the new data; the change is made again on what is there now.
        if(reloadIfChanged()){for(AlertDialog editor:new ArrayList<>(editors))editor.dismiss();render();throw new IllegalArgumentException("MyBudget changed meanwhile (an expense from Planner came in). Make your change again.");}
        Budget before;try{before=BudgetStore.decode(BudgetStore.encode(budget));before.fromPlanner.addAll(budget.fromPlanner);}catch(Exception e){throw new IllegalStateException("Could not prepare save.");}
        try{action.run();String raw=BudgetStore.encode(budget);if(!getSharedPreferences("budget",0).edit().putString("data",raw).commit())throw new IllegalStateException("Could not save to device storage.");loaded=raw;}
        catch(Exception e){budget=before;throw new IllegalArgumentException(e.getMessage()==null?"Check your entry.":e.getMessage());}
    }
    private void dialog(String title,LinearLayout f,Runnable action){
        ScrollView scroll=new ScrollView(this);scroll.addView(f);AlertDialog d=new AlertDialog.Builder(this).setTitle(title).setView(scroll).setNegativeButton("Cancel",null).setPositiveButton("Save",null).create();
        editors.add(d);d.setOnDismissListener(v->editors.remove(d));d.setOnShowListener(v->d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(w->{try{commit(action);render();d.dismiss();}catch(Exception e){toast(e.getMessage());}}));d.show();
    }
    private void assign(Budget.Category c){
        LinearLayout f=form();f.addView(label(money(budget.spendable(month))+" to budget",16,blue,true));long now=budget.assigned(c,month);f.addView(label("Assigned this month: "+money(now)+". A positive amount adds money; a negative one returns it.",13,muted,false));
        EditText amount=field(f,"Amount (AUD)",true);TextView result=label("",13,blue,true);f.addView(result);onText(amount,()->{try{result.setText("Assigned becomes "+money(now+Budget.parse(amount.getText().toString())));}catch(Exception e){result.setText("");}});
        // Quick amounts: each fills in the change to this month's Assigned.
        f.addView(label("Quick amounts",12,muted,true));YearMonth last=month.minusMonths(1);long lastAssigned=budget.assigned(c,last),spentLast=budget.spent(c,last),average=budget.averageSpent(c,month),averageAssigned=budget.averageAssigned(c,month);
        List<String> names=new ArrayList<>();List<Long> changes=new ArrayList<>();
        long underfunded=budget.fundNeed(c,month);if(underfunded>0){names.add("Underfunded: "+money(underfunded));changes.add(underfunded);} // what Fund targets would give it
        names.add("Assigned last month: "+money(lastAssigned));changes.add(lastAssigned-now);names.add("Average assigned, last 3 months: "+money(averageAssigned));changes.add(averageAssigned-now);
        if(!c.payment()){names.add("Spent last month: "+money(spentLast));changes.add(spentLast-now);names.add("Average spent, last 3 months: "+money(average));changes.add(average-now);} // a card payment isn't spending
        long available=budget.available(c,month),toZero=budget.resetAvailableChange(c,month);if(toZero!=0){names.add("Reset available to $0 (now "+money(available)+")");changes.add(toZero);} // not offered in a future month when carried money would have to go
        long reset=budget.resetChange(c,month);if(reset!=0){names.add(reset==-now?"Reset assigned to $0":"Reset assigned: return what's left, "+money(-reset));changes.add(reset);}
        for(int i=0;i<names.size();i++){long change=changes.get(i);Button b=button(names.get(i),()->amount.setText(decimal(change)));b.setTextSize(12);b.setMinHeight(dp(40));b.setMinimumHeight(dp(40));f.addView(b);}
        dialog("Assign to "+c.name,f,()->budget.assign(categoryById(c.id),month,Budget.parse(amount.getText().toString())));
    }
    private String[] availableNames(){return budget.categories.stream().map(c->c.name+" ("+money(budget.available(c,month))+")").toArray(String[]::new);}
    private void move(){if(budget.categories.size()<2){toast("Create two categories first.");return;}LinearLayout f=form();Spinner from=spinner(f,"From",availableNames(),0),to=spinner(f,"To",availableNames(),1);EditText amount=field(f,"Amount (AUD)",true);dialog("Move money",f,()->budget.move(budget.categories.get(from.getSelectedItemPosition()),budget.categories.get(to.getSelectedItemPosition()),month,Budget.cents(amount.getText().toString())));}
    private void autoAssign(){long remaining=Math.max(0,budget.spendable(month));long total=0;for(Budget.Category c:budget.categories)if(!c.hidden)total+=budget.fundNeed(c,month);long fund=Math.min(remaining,total);if(fund==0){toast("No available money or underfunded targets.");return;}new AlertDialog.Builder(this).setTitle("Fund targets").setMessage("Assign "+money(fund)+" to underfunded targets and upcoming bills, earliest due first?").setNegativeButton("Cancel",null).setPositiveButton("Fund",(d,w)->{try{commit(()->{long left=Math.max(0,budget.spendable(month));for(Budget.Category c:budget.fundOrder(month)){if(c.hidden)continue;long n=Math.min(left,budget.fundNeed(c,month));if(n>0){budget.assign(c,month,n);left-=n;}}});render();}catch(Exception e){toast(e.getMessage());}}).show();}
    // Target kinds as saved (targetType), in the form's order.
    private static final String[] TARGET_TYPES={"Refill","Monthly","Balance","Weekly","ByDate","Debt"};
    private static final int[] REPEAT_MONTHS={0,3,6,12};
    private void editCategory(Budget.Category existing){
        LinearLayout f=form();EditText name=field(f,"Category name",false);AutoCompleteTextView group=suggestField(f,"Group (Bills, Everyday, Savings...)",()->budget.groups());String[] types={"Refill each month","Set aside each month","Save toward a balance","Weekly amount","Save for spending by a date","Monthly debt payment"};Spinner type=spinner(f,"Target behavior",types,existing==null?0:Math.max(0,Arrays.asList(TARGET_TYPES).indexOf(existing.targetType)));TextView explanation=label("",13,muted,false);f.addView(explanation);EditText amount=field(f,"Target amount (0 for none)",true);LinearLayout deadline=column();f.addView(deadline);EditText due=field(deadline,"Due month (YYYY-MM, optional)",false);
        // Weekly: the weekday and whether each week tops up or adds a fresh amount. By date: the date and an optional repeat.
        LinearLayout weekly=column();f.addView(weekly);String[] days=new String[7];for(int i=0;i<7;i++)days[i]=dayName(i+1);Spinner weekday=spinner(weekly,"Every",days,existing==null?0:existing.weekday-1);Spinner weeklyMode=spinner(weekly,"Each week",new String[]{"Refill up to the amount","Set aside another amount"},existing==null||existing.weeklyRefill?0:1);
        LinearLayout byDate=column();f.addView(byDate);byDate.addView(label("Due date",12,muted,true));EditText dueDate=dateField(byDate,existing!=null&&!existing.dueDate.isEmpty()?existing.dueDate:LocalDate.now().plusMonths(3).toString(),true);int repeatAt=0;for(int i=0;i<REPEAT_MONTHS.length;i++)if(existing!=null&&existing.repeatMonths==REPEAT_MONTHS[i])repeatAt=i;Spinner repeat=spinner(byDate,"After the date",new String[]{"Stop asking","Repeat every 3 months","Repeat every 6 months","Repeat every 12 months"},repeatAt);
        LinearLayout dayRow=column();f.addView(dayRow);EditText dueDay=field(dayRow,"Due day of the month (1-31, optional)",false);dueDay.setInputType(InputType.TYPE_CLASS_NUMBER);dayRow.addView(label("Fund targets funds the earliest due first.",12,muted,false));
        EditText note=field(f,"Note (optional)",false);
        if(existing!=null){name.setText(existing.name);group.setText(existing.group,false);amount.setText(decimal(existing.target));due.setText(existing.due);dueDay.setText(existing.dueDay>0?String.valueOf(existing.dueDay):"");note.setText(existing.note);}else group.setText("Everyday",false);
        Runnable describe=()->{String t=TARGET_TYPES[type.getSelectedItemPosition()];deadline.setVisibility(t.equals("Balance")?View.VISIBLE:View.GONE);weekly.setVisibility(t.equals("Weekly")?View.VISIBLE:View.GONE);byDate.setVisibility(t.equals("ByDate")?View.VISIBLE:View.GONE);dayRow.setVisibility(t.equals("Refill")||t.equals("Monthly")||t.equals("Debt")?View.VISIBLE:View.GONE);
            amount.setHint(t.equals("Weekly")?"Amount each week (0 for none)":t.equals("ByDate")?"Amount needed by the date (0 for none)":t.equals("Debt")?"Payment each month (0 for none)":"Target amount (0 for none)");
            explanation.setText(new String[]{"Top up what remained from last month. Example: a $500 target with $100 left asks for $400. Spending this month does not restart the target.","Add a fresh amount every month. Example: set aside $100 for repairs, even if $300 remains from earlier months.","Build up to a total balance. Example: a $1,200 goal with $300 saved and 3 months remaining asks for $300 this month. A due month is optional.",
                "An amount every week on the day you choose. Example: $40 every Monday asks for $200 in a month with 5 Mondays, $160 with 4. Refill up to counts what's left from last month; Set aside another adds the full amount each week.","Save money to spend by a date, such as a $250 bill due 15 January. What's still needed is spread evenly over the months up to and including the due month. After the date it stops asking, or starts again for the next date if it repeats.","A fixed payment toward a debt every month, such as $300 on a loan. It asks for the full amount each month, like Set aside, even if money remains from earlier months."}[type.getSelectedItemPosition()]);};
        describe.run();type.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onItemSelected(AdapterView<?> p,View v,int position,long id){describe.run();}public void onNothingSelected(AdapterView<?> p){}});
        // Groups already in the plan are suggested; a group typed in other capitals is saved in its existing spelling ("bills" -> "Bills").
        // The target amount takes quick maths; starting with + adds to the current target.
        dialog(existing==null?"New category":"Edit category & target",f,()->{String n=required(name),g=budget.existingGroup(required(group),existing==null?null:budget.category(existing.id));for(Budget.Category c:budget.categories)if((existing==null||!c.id.equals(existing.id))&&c.name.equalsIgnoreCase(n))throw new IllegalArgumentException("That category already exists.");String t=TARGET_TYPES[type.getSelectedItemPosition()];
            long target=amount.getText().toString().trim().isEmpty()?0:Budget.adjust(amount.getText().toString(),existing==null?0:existing.target);if(target<0)throw new IllegalArgumentException("Target cannot be negative.");String dueMonth=t.equals("Balance")?due.getText().toString().trim():"";if(!dueMonth.isEmpty()){YearMonth m=YearMonth.parse(dueMonth);if(m.getYear()<1900||m.getYear()>2100)throw new IllegalArgumentException("Choose a due year between 1900 and 2100.");}
            Budget.Category c=existing==null?new Budget.Category(n):budget.category(existing.id);c.name=n;c.group=g;c.target=target;c.targetType=t;c.due=dueMonth;String day=dueDay.getText().toString().trim();int d=0;if(dayRow.getVisibility()==View.VISIBLE&&!day.isEmpty()){try{d=Integer.parseInt(day);}catch(NumberFormatException e){d=-1;}if(d<1||d>31)throw new IllegalArgumentException("Enter a due day from 1 to 31, or leave it empty.");}c.dueDay=d;
            c.weekday=weekday.getSelectedItemPosition()+1;c.weeklyRefill=weeklyMode.getSelectedItemPosition()==0;c.dueDate=t.equals("ByDate")?LocalDate.parse((String)dueDate.getTag()).toString():"";c.repeatMonths=t.equals("ByDate")?REPEAT_MONTHS[repeat.getSelectedItemPosition()]:0;
            c.note=note.getText().toString().trim();if(existing==null)budget.categories.add(c);});
    }
    private void addAccount(){
        LinearLayout f=form();Spinner type=spinner(f,"Type",new String[]{"Cash, checking or savings","Credit card","Tracking: an asset (savings elsewhere, investments, house, super)","Tracking: a loan or debt (mortgage, car loan)"},0);EditText name=field(f,"Account name",false),opening=field(f,"Current cash balance (AUD)",true);f.addView(label("Opening date",12,muted,true));EditText day=dateField(f,LocalDate.now().toString());TextView help=label("",13,muted,false);f.addView(help);
        Runnable adapt=()->{int t=type.getSelectedItemPosition();boolean card=t==1;opening.setHint(card?"Amount owed now (AUD, 0 if paid off)":t==2?"What it's worth now (AUD)":t==3?"Amount owed now (AUD)":"Current cash balance (AUD)");
            help.setText(card?"What you owe now is old debt: it gets a payment category with nothing set aside, so assign money to that category to pay it down. New spending on the card moves the category's money there for you.":t>=2?"Off budget: it doesn't change To budget or your categories, only Net worth. Update its balance now and then."+(t==3?" Add the interest rate and payment in Edit account for the payoff planner.":""):"Enter transactions from the opening date onward.");};
        adapt.run();onPick(type,adapt);
        dialog("Add account",f,()->{String n=required(name);for(Budget.Account a:budget.accounts)if(a.name.equalsIgnoreCase(n))throw new IllegalArgumentException("That account already exists.");long balance=Budget.parse(opening.getText().toString().trim().isEmpty()?"0":opening.getText().toString());int t=type.getSelectedItemPosition();
            if(balance<0)throw new IllegalArgumentException(t==1||t==3?"Enter what you owe as a positive amount.":t==2?"Enter what it's worth as a positive amount.":"Use a nonnegative cash opening balance.");
            if(t>=2){budget.addTracking(n,date(day),balance,t==3);return;}
            if(t==1){for(Budget.Category c:budget.categories)if(c.name.equalsIgnoreCase(n))throw new IllegalArgumentException("A category already has that name. Choose another name for the card.");budget.addCard(n,date(day),balance);}
            else budget.accounts.add(new Budget.Account(n,date(day),balance));});
    }
    private static final String[] REPEAT_LABELS={"Doesn't repeat","Weekly","Every 2 weeks","Monthly","Every 3 months","Yearly"};
    private void transaction(Budget.Entry old){transaction(old,null);}
    /**
     * Adds or edits a transaction ([old]) or an upcoming one ([sched]). A new one with a future date or a repeat
     * becomes upcoming: it waits in Transactions until its day, when the user enters or skips it.
     */
    private void transaction(Budget.Entry old,Budget.Scheduled sched){
        if(budget.accounts.isEmpty()){toast("Add an account first.");addAccount();return;}if(old!=null&&old.transfer()){editTransfer(old);return;}if(old!=null&&budget.account(old.account)!=null&&budget.account(old.account).tracking()){trackingEntry(old.account,old);return;}
        String keepAccount=old!=null?old.account:sched!=null?sched.account:null,keepCategory=old!=null?old.category:sched!=null?sched.category:"";long oldAmount=old!=null?old.amount:sched!=null?sched.amount:-1;
        List<Budget.Account> accounts=keepAccount==null?openAccounts():openAccounts(budget.account(keepAccount));accounts.removeIf(Budget.Account::tracking);List<Budget.Category> categories=visibleCategories(budget.category(keepCategory)); // tracking accounts: Update balance, or a transfer
        if(accounts.isEmpty()){boolean onlyTracking=true;for(Budget.Account a:budget.accounts)if(!a.tracking())onlyTracking=false;toast(onlyTracking?"Add a bank, cash or card account first. Tracking accounts change with Update balance.":"All your accounts are closed. Reopen one in Accounts first.");return;}
        LinearLayout f=form();Spinner kind=spinner(f,"Type",new String[]{"Expense","Income","Category refund"},oldAmount<0?0:keepCategory.isEmpty()?1:2);TextView guidance=label("",12,muted,false);f.addView(guidance);
        // Payees used before are suggested; picking one on a new transaction fills in the category it had last time.
        AutoCompleteTextView payee=suggestField(f,"Payee",()->budget.payees());
        f.addView(label("Amount (AUD)",12,muted,true));EditText amount=field(f,"0.00",true);amount.setTextSize(24);f.addView(label("Date",12,muted,true));EditText day=dateField(f,old!=null?old.date:sched!=null?sched.next:LocalDate.now().toString(),old==null);Spinner account=spinner(f,"Account",accounts.stream().map(a->a.name).toArray(String[]::new),keepAccount==null?0:accounts.indexOf(budget.account(keepAccount)));LinearLayout categoryFields=column();f.addView(categoryFields);Spinner category=spinner(categoryFields,"Category",categories.stream().map(c->c.name).toArray(String[]::new),keepCategory.isEmpty()?0:categories.indexOf(budget.category(keepCategory)));
        // Split: the parts (positive amounts while editing) replace the category; the amount becomes their total.
        List<Budget.Split> parts=new ArrayList<>();if(old!=null)for(Budget.Split p:old.splits){Budget.Split c=new Budget.Split(p.category,Math.abs(p.amount));c.memo=p.memo;parts.add(c);}
        TextView splitSummary=label("",13,ink,false);categoryFields.addView(splitSummary);Button splitButton=button("Split into categories",()->{});categoryFields.addView(splitButton);
        Runnable showSplit=()->{boolean on=!parts.isEmpty();category.setVisibility(on?View.GONE:View.VISIBLE);splitSummary.setVisibility(on?View.VISIBLE:View.GONE);splitButton.setText(on?"Edit split":"Split into categories");amount.setEnabled(!on);
            if(on){long sum=0;StringBuilder s=new StringBuilder("Split: ");for(int i=0;i<parts.size();i++){Budget.Split p=parts.get(i);Budget.Category c=budget.category(p.category);sum+=p.amount;s.append(i>0?", ":"").append(c==null?"To budget":c.name).append(" ").append(money(p.amount));}splitSummary.setText(s);amount.setText(decimal(sum));}};
        splitButton.setOnClickListener(v->{long total;try{total=Budget.cents(amount.getText().toString());}catch(Exception e){total=0;}editSplit(parts,categories,categories.isEmpty()?null:categories.get(Math.max(0,category.getSelectedItemPosition())),total,showSplit);});
        showSplit.run();
        boolean[] categoryChosen={old!=null||sched!=null};category.setOnTouchListener((v,ev)->{categoryChosen[0]=true;return false;});
        payee.setOnItemClickListener((p,v,position,id)->{Budget.Entry last=budget.lastForPayee(payee.getText().toString());if(old!=null||categoryChosen[0]||last==null)return;
            if(last.category.isEmpty()){kind.setSelection(1);return;}int i=categories.indexOf(budget.category(last.category));if(i<0)return;category.setSelection(i);if(kind.getSelectedItemPosition()==1)kind.setSelection(last.amount<0?0:2);});
        // Notes used before are suggested, those with this payee first.
        LinearLayout noteFields=column();AutoCompleteTextView memo=suggestField(noteFields,"Note (optional)",()->budget.memos(payee.getText().toString()));Button note=button(old!=null&&!old.memo.isEmpty()?"Hide note":"+ Add a note",()->{});f.addView(note);f.addView(noteFields);noteFields.setVisibility(old!=null&&!old.memo.isEmpty()?View.VISIBLE:View.GONE);note.setOnClickListener(v->{boolean show=noteFields.getVisibility()!=View.VISIBLE;noteFields.setVisibility(show?View.VISIBLE:View.GONE);note.setText(show?"Hide note":"+ Add a note");});CheckBox cleared=new CheckBox(this);cleared.setText("Cleared at the bank");cleared.setMinHeight(dp(48));f.addView(cleared);LinearLayout flagBox=column();f.addView(flagBox);Spinner flag=spinner(flagBox,"Flag",flagChoices(),old!=null?old.flag:0);if(sched!=null)flagBox.setVisibility(View.GONE); // upcoming transactions have no flag
        // A photo (e.g. a receipt), kept on this phone. Picking one leaves this form open (see onRestart).
        String[] photo={old!=null?old.photo:""};LinearLayout photoBox=column();if(sched==null)f.addView(photoBox);Runnable[] showPhotoBox=new Runnable[1];
        showPhotoBox[0]=()->{photoBox.removeAllViews();
            if(photo[0].isEmpty())photoBox.addView(button("+ Add a photo",()->{photoTarget=u->{try{photo[0]=copyPhoto(u);}catch(Exception e){toast("Could not add that photo.");}showPhotoBox[0].run();};pickingPhoto=true;photoForm=editors.isEmpty()?null:editors.get(editors.size()-1); // this form: the newest open one
                try{startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE),PHOTO);}catch(Exception e){pickingPhoto=false;photoTarget=null;photoForm=null;toast("No app on this device can pick a photo.");}}));
            else{android.graphics.Bitmap bm=photoBitmap(photo[0],480);if(bm==null)photoBox.addView(label("The photo isn't on this phone.",13,muted,false));else{ImageView img=new ImageView(this);img.setImageBitmap(bm);img.setAdjustViewBounds(true);img.setScaleType(ImageView.ScaleType.FIT_START);img.setContentDescription("Photo of this transaction. Double tap to view it larger.");img.setOnClickListener(v->viewPhoto(photo[0]));photoBox.addView(img,new LinearLayout.LayoutParams(-1,dp(160)));}
                photoBox.addView(button("Remove photo",()->{photo[0]="";showPhotoBox[0].run();}));}};
        showPhotoBox[0].run();
        Spinner repeat=null;if(old==null){repeat=spinner(f,"Repeat",REPEAT_LABELS,sched==null?0:Arrays.asList(Budget.Scheduled.REPEATS).indexOf(sched.repeat));f.addView(label("A future date or a repeat makes it upcoming: it waits in Transactions, and you enter it when the day comes.",12,muted,false));}
        if(sched!=null){cleared.setVisibility(View.GONE);payee.setText(sched.payee,false);amount.setText(decimal(Math.abs(sched.amount)));memo.setText(sched.memo,false);if(!sched.memo.isEmpty()){noteFields.setVisibility(View.VISIBLE);note.setText("Hide note");}f.addView(button("Delete upcoming transaction",()->deleteScheduled(sched.id)));}
        if(old!=null){payee.setText(old.payee,false);amount.setText(decimal(Math.abs(old.amount)));memo.setText(old.memo,false);cleared.setChecked(old.cleared);f.addView(button("Delete transaction",()->delete(old)));}
        Spinner repeatField=repeat;
        Runnable adapt=()->{int selected=kind.getSelectedItemPosition();boolean income=selected==1;categoryFields.setVisibility(income?View.GONE:View.VISIBLE);payee.setHint(income?"Income source":selected==2?"Refund from":"Payee");guidance.setText(income?"Adds money into To budget.":selected==2?"Returns money to the original spending category.":"Reduces the available money in your category.");};adapt.run();kind.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onItemSelected(AdapterView<?> p,View v,int position,long id){adapt.run();}public void onNothingSelected(AdapterView<?> p){}});
        dialog(sched!=null?"Edit upcoming transaction":old==null?"Add transaction":"Edit transaction",f,()->{int k=kind.getSelectedItemPosition();if(k!=1&&categories.isEmpty())throw new IllegalArgumentException("Add a category first.");
            boolean isSplit=k!=1&&!parts.isEmpty();if(old!=null&&budget.entries.stream().noneMatch(t->t.id.equals(old.id)))throw new IllegalArgumentException("This transaction was removed meanwhile."); // the data may have been read in again since the form opened
            String p=required(payee),cat=k==1?"":isSplit?Budget.SPLIT:categories.get(category.getSelectedItemPosition()).id,acc=accounts.get(account.getSelectedItemPosition()).id,memoText=memo.getText().toString().trim();long cents=Budget.cents(amount.getText().toString())*(k==0?-1:1);
            String rep=repeatField==null?"Never":Budget.Scheduled.REPEATS[repeatField.getSelectedItemPosition()];LocalDate when=LocalDate.parse((String)day.getTag());
            if(old==null&&(sched!=null||when.isAfter(LocalDate.now())||!rep.equals("Never"))){
                if(isSplit)throw new IllegalArgumentException("A split can't be upcoming yet. Save it on its day, or use one category.");
                Budget.Scheduled s=new Budget.Scheduled(p,cat,acc,when.toString(),cents,rep);s.memo=memoText;if(sched!=null){s.id=sched.id;s.billKey=sched.billKey;}
                if(sched==null&&!when.isAfter(LocalDate.now())){budget.validate(s);budget.enter(s,photo[0],cleared.isChecked()).flag=flag.getSelectedItemPosition();if(!rep.equals("Never"))budget.scheduled.add(s);return;} // today or earlier: entered now (with its photo and Cleared tick), the repeat continues
                budget.validate(s);budget.scheduled.removeIf(t->t.id.equals(s.id));budget.scheduled.add(s);return;
            }
            Budget.Entry e=new Budget.Entry(p,cat,acc,date(day),cents);e.memo=memoText;e.photo=photo[0];if(isSplit)for(Budget.Split part:parts){Budget.Split s=new Budget.Split(part.category,part.amount*(k==0?-1:1));s.memo=part.memo;e.splits.add(s);}e.cleared=cleared.isChecked();e.flag=flag.getSelectedItemPosition();budget.validate(e);if(old!=null){e.id=old.id;e.externalId=old.externalId;e.billKey=old.billKey;e.bankPayee=old.bankPayee;budget.entries.removeIf(t->t.id.equals(old.id));}budget.entries.add(0,e);});
    }
    /** Edits [parts] (category + positive amount per row); at least two parts. Remove split empties them. */
    private void editSplit(List<Budget.Split> parts,List<Budget.Category> categories,Budget.Category first,long total,Runnable done){
        if(categories.isEmpty()){toast("Add a category first.");return;}
        String[] names=new String[categories.size()+1];for(int i=0;i<categories.size();i++)names[i]=categories.get(i).name;names[categories.size()]="To budget";
        LinearLayout f=form(),rows=column();f.addView(label("Each part comes out of its own category.",13,muted,false));f.addView(rows);TextView sum=label("",14,blue,true);
        List<Spinner> cats=new ArrayList<>();List<EditText> amounts=new ArrayList<>();EditText[] current={null}; // the part last typed in (Fill remaining fills it)
        Runnable total2=()->{long n=0;for(EditText a:amounts){try{n+=Budget.parse(a.getText().toString().isEmpty()?"0":a.getText().toString());}catch(Exception e){}}sum.setText("Total "+money(n));};
        java.util.function.BiConsumer<String,Long> addRow=(category,cents)->{LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);Spinner s=new Spinner(this);s.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,names));
            int i=category==null?0:category.isEmpty()?categories.size():Math.max(0,categories.indexOf(budget.category(category)));s.setSelection(i);row.addView(s,new LinearLayout.LayoutParams(0,-2,1));
            EditText a=new EditText(this);a.setHint("0.00");a.setTextColor(ink);a.setSingleLine(true);a.setInputType(AMOUNT_INPUT);if(cents!=null&&cents>0)a.setText(decimal(cents));onText(a,total2);a.setOnFocusChangeListener((v,has)->{if(has)current[0]=a;});row.addView(a,new LinearLayout.LayoutParams(dp(110),-2));
            Button x=button("✕",()->{});x.setContentDescription("Remove this part");x.setBackground(bg(Color.TRANSPARENT));x.setOnClickListener(v->{rows.removeView(row);cats.remove(s);amounts.remove(a);if(current[0]==a)current[0]=null;total2.run();});row.addView(x,new LinearLayout.LayoutParams(dp(48),dp(48)));
            rows.addView(row);cats.add(s);amounts.add(a);};
        if(parts.isEmpty()){addRow.accept(first==null?null:first.id,total);addRow.accept(null,null);}else for(Budget.Split p:parts)addRow.accept(p.category,p.amount);
        f.addView(button("+ Add a part",()->{addRow.accept(null,null);total2.run();}));
        // Helpers over the transaction's amount: Split evenly (leftover cents on the first parts); Fill remaining puts what's left into the part last typed in, else the last empty one.
        LinearLayout helpers=new LinearLayout(this);Button even=button("Split evenly",()->{if(amounts.isEmpty())return;if(total<=0){toast("Enter the transaction's amount first.");return;}long[] shares=Budget.splitEvenly(total,amounts.size());for(int i=0;i<amounts.size();i++)amounts.get(i).setText(decimal(shares[i]));});
        Button fill=button("Fill remaining",()->{if(amounts.isEmpty())return;if(total<=0){toast("Enter the transaction's amount first.");return;}EditText into=amounts.contains(current[0])?current[0]:null;if(into==null)for(EditText a:amounts)if(a.getText().toString().trim().isEmpty())into=a;if(into==null)into=amounts.get(amounts.size()-1);
            long[] others=new long[amounts.size()-1];int k=0;for(EditText a:amounts){if(a==into)continue;String v=a.getText().toString().trim();try{others[k++]=v.isEmpty()?0:Budget.parse(v);}catch(Exception e){toast(e.getMessage());return;}}
            long left=Budget.remaining(total,others);if(left<=0){toast("Nothing is left of the "+money(total)+" total.");return;}into.setText(decimal(left));});
        helpers.addView(even,new LinearLayout.LayoutParams(0,-2,1));LinearLayout.LayoutParams fp=new LinearLayout.LayoutParams(0,-2,1);fp.setMargins(dp(8),0,0,0);helpers.addView(fill,fp);f.addView(helpers);
        f.addView(sum);total2.run();
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
    private void payCard(String id){Budget.Account card=budget.account(id);if(card==null)return;editTransfer(null,id,budget.toPay(card));} // what's set aside now, whatever month is on screen
    private void editTransfer(Budget.Entry old){editTransfer(old,null,0);}
    private void editTransfer(Budget.Entry old,String toId,long preset){
        List<Budget.Account> accounts=old==null?openAccounts():openAccounts(budget.account(old.account),budget.account(old.destination));String[] names=accounts.stream().map(a->a.name).toArray(String[]::new);
        if(accounts.size()<2){toast("Add two open accounts first.");return;}LinearLayout f=form();int toIndex=toId==null?-1:accounts.indexOf(budget.account(toId)),fromIndex=0;if(toIndex>=0)for(int i=0;i<accounts.size();i++)if(accounts.get(i).cash()){fromIndex=i;break;}
        Spinner from=spinner(f,"From account",names,old!=null?accounts.indexOf(budget.account(old.account)):fromIndex),to=spinner(f,"To account",names,old!=null?accounts.indexOf(budget.account(old.destination)):toIndex>=0?toIndex:1);
        // Out of the budget to a tracking account (an extra loan payment, an investment): spending, so it comes from a category. In from one: income to To budget.
        List<Budget.Category> cats=visibleCategories(old==null?null:budget.category(old.category));LinearLayout categoryBox=column();f.addView(categoryBox);Spinner category=spinner(categoryBox,"Category it comes from",cats.stream().map(c->c.name+" ("+money(budget.available(c,month))+")").toArray(String[]::new),old==null?0:cats.indexOf(budget.category(old.category)));TextView crossing=label("",12,muted,false);f.addView(crossing);
        Runnable adapt=()->{Budget.Account a=accounts.get(Math.max(0,from.getSelectedItemPosition())),b=accounts.get(Math.max(0,to.getSelectedItemPosition()));boolean out=!a.tracking()&&b.tracking(),in=a.tracking()&&!b.tracking();categoryBox.setVisibility(out?View.VISIBLE:View.GONE);crossing.setText(out?"It leaves your budget: it's spending from this category.":in?"It comes into your budget: it's income to To budget.":"");crossing.setVisibility(out||in?View.VISIBLE:View.GONE);};adapt.run();onPick(from,adapt);onPick(to,adapt);
        EditText amount=field(f,"Amount (AUD)",true);if(preset>0)amount.setText(decimal(preset));f.addView(label("Date",12,muted,true));EditText day=dateField(f,old==null?LocalDate.now().toString():old.date);CheckBox cleared=new CheckBox(this);cleared.setText("Cleared in both accounts");f.addView(cleared);if(old!=null){amount.setText(decimal(-old.amount));cleared.setChecked(old.cleared);f.addView(button("Delete transfer",()->delete(old)));}
        dialog(old!=null?"Edit transfer":toIndex>=0?"Pay "+budget.account(toId).name:"Transfer money",f,()->{Budget.Account a=accounts.get(from.getSelectedItemPosition()),b=accounts.get(to.getSelectedItemPosition());boolean out=!a.tracking()&&b.tracking();if(out&&cats.isEmpty())throw new IllegalArgumentException("Add a category first.");
            Budget.Entry e=new Budget.Entry("Transfer to "+b.name,out?cats.get(Math.max(0,category.getSelectedItemPosition())).id:"",a.id,date(day),-Budget.cents(amount.getText().toString()));e.destination=b.id;e.cleared=cleared.isChecked();if(old!=null){e.memo=old.memo;e.flag=old.flag;e.bankPayee=old.bankPayee;}budget.validate(e);if(old!=null){e.id=old.id;budget.entries.removeIf(t->t.id.equals(old.id));}budget.entries.add(0,e);});
    }
    // A difference can be settled with an adjustment into To budget after the user confirms.
    private void reconcile(Budget.Account a){
        LinearLayout f=form();f.addView(label("Cleared balance: "+money(budget.balance(a,true)),18,ink,true));f.addView(label("Compare with your bank's cleared balance, excluding pending transactions. Mark transactions cleared in Transactions first.",14,muted,false));EditText value=field(f,"Bank's cleared balance (AUD)",true);
        ScrollView scroll=new ScrollView(this);scroll.addView(f);AlertDialog d=new AlertDialog.Builder(this).setTitle("Reconcile "+a.name).setView(scroll).setNegativeButton("Cancel",null).setPositiveButton("Reconcile",null).create();
        editors.add(d);d.setOnDismissListener(v->editors.remove(d));d.setOnShowListener(v->d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(w->{
            long bank;try{bank=Budget.parse(value.getText().toString());}catch(Exception e){toast(e.getMessage());return;}
            String today=LocalDate.now().toString();Budget.Entry adjust=budget.adjustment(accountById(a.id),bank,today);
            if(adjust==null){if(change(()->accountById(a.id).reconciled=today)){d.dismiss();toast("Reconciled.");}return;}
            new AlertDialog.Builder(this).setTitle("Balances differ by "+money(adjust.amount)).setMessage("Check for missing or uncleared transactions first. Or add a cleared adjustment of "+money(adjust.amount)+" into To budget so "+a.name+" matches your bank.")
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
        // A loan's terms, for the payoff planner.
        LinearLayout terms=column();if(a.liability)f.addView(terms);terms.addView(label("Loan terms (for the payoff planner)",12,muted,true));EditText rate=field(terms,"Interest rate (% a year, e.g. 6.25)",false);rate.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);EditText payment=field(terms,"Regular payment (AUD)",true);Spinner often=spinner(terms,"Paid",Budget.FREQUENCIES,Arrays.asList(Budget.FREQUENCIES).indexOf(a.frequency));
        if(a.rate>0)rate.setText(a.ratePercent().stripTrailingZeros().toPlainString());if(a.payment>0){if(hideAmounts)payment.setHint("Regular payment: $••• (empty keeps it)");else payment.setText(decimal(a.payment));} // Hide amounts: the payment isn't shown
        dialog("Edit account",f,()->{String n=required(name);for(Budget.Account o:budget.accounts)if(!o.id.equals(id)&&o.name.equalsIgnoreCase(n))throw new IllegalArgumentException("That account already exists.");
            long r=0,p=0;if(a.liability){String rt=rate.getText().toString().trim();try{r=rt.isEmpty()?0:new java.math.BigDecimal(rt).movePointRight(3).longValueExact();}catch(RuntimeException e){r=-1;}if(r<0||r>100_000)throw new IllegalArgumentException("Enter an interest rate from 0 to 100%, with up to three decimals.");
                String pt=payment.getText().toString().trim();p=pt.isEmpty()?(hideAmounts?accountById(id).payment:0):Budget.parse(pt);if(p<0)throw new IllegalArgumentException("Enter the payment as a positive amount.");}
            Budget.Account acc=accountById(id);budget.rename(acc,n);if(acc.liability){acc.rate=r;acc.payment=p;acc.frequency=Budget.FREQUENCIES[often.getSelectedItemPosition()];}});
    }
    private void onPick(Spinner s,Runnable changed){s.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onItemSelected(AdapterView<?> p,View v,int position,long id){changed.run();}public void onNothingSelected(AdapterView<?> p){}});}
    /** A tracking account's value update: the new balance (what's owed, for a debt) as a cleared transaction for the difference. */
    private void updateBalance(String id){
        Budget.Account a=budget.account(id);if(a==null)return;long now=budget.balance(a,false);LinearLayout f=form();f.addView(label((a.liability?"Owed now: "+money(-now):"Balance now: "+money(now))+". Enter the figure from your statement for the date below; the difference from its balance on that date is recorded as a balance update. It doesn't touch your budget.",13,muted,false));
        EditText value=field(f,a.liability?"Amount owed (AUD)":"What it's worth (AUD)",true);f.addView(label("Date",12,muted,true));EditText day=dateField(f,LocalDate.now().toString());
        dialog("Update "+a.name,f,()->{long v=Budget.parse(value.getText().toString());if(v<0)throw new IllegalArgumentException("Enter the amount as a positive number.");Budget.Entry e=budget.valueUpdate(accountById(id),v,date(day));if(e==null)throw new IllegalArgumentException("That's already its balance.");budget.validate(e);budget.entries.add(0,e);});
    }
    /** Edits a tracking account's transaction: no category (it's off budget). */
    private void trackingEntry(String accountId,Budget.Entry old){
        Budget.Account a=budget.account(accountId);if(a==null)return;LinearLayout f=form();f.addView(label(a.name+" is a tracking account: off budget, no category. A positive amount raises its balance"+(a.liability?" (less owed)":"")+"; a negative one lowers it.",13,muted,false));
        AutoCompleteTextView payee=suggestField(f,"Payee or description",()->budget.payees());EditText amount=field(f,"Amount (AUD, + or -)",true);f.addView(label("Date",12,muted,true));EditText day=dateField(f,old.date);EditText memo=field(f,"Note (optional)",false);
        CheckBox cleared=new CheckBox(this);cleared.setText("Cleared");f.addView(cleared);Spinner flag=spinner(f,"Flag",flagChoices(),old.flag);payee.setText(old.payee,false);amount.setText(decimal(old.amount));memo.setText(old.memo);cleared.setChecked(old.cleared);f.addView(button("Delete transaction",()->delete(old)));
        dialog("Edit transaction",f,()->{Budget.Entry e=new Budget.Entry(required(payee),"",accountId,date(day),Budget.parse(amount.getText().toString()));e.id=old.id;e.memo=memo.getText().toString().trim();e.cleared=cleared.isChecked();e.flag=flag.getSelectedItemPosition();e.photo=old.photo;e.bankPayee=old.bankPayee;budget.validate(e);
            if(!budget.entries.removeIf(t->t.id.equals(old.id)))throw new IllegalArgumentException("This transaction was removed meanwhile.");budget.entries.add(0,e);});
    }
    /** A loan's payoff date and interest at its payment, and how much sooner (and cheaper) an extra amount each payment makes it. */
    private void payoffPlanner(String id){
        Budget.Account a=budget.account(id);if(a==null)return;long owed=-budget.balance(a,false);LinearLayout f=form();
        if(owed<=0){toast(a.name+" is paid off.");return;}if(a.payment<=0){toast("Add the interest rate and regular payment in Edit account first.");editAccount(id);return;}
        long monthly=Budget.perMonth(a.payment,a.frequency);java.math.BigDecimal rate=a.ratePercent();f.addView(label("Owed "+money(owed)+" at "+rate.stripTrailingZeros().toPlainString()+"% a year, paying "+money(a.payment)+" "+a.frequency.toLowerCase(Locale.ROOT)+(a.frequency.equals("Monthly")?"":" (about "+money(monthly)+" a month)")+".",14,ink,false));
        Budget.Payoff base=Budget.payoff(owed,rate,monthly,0);f.addView(label(payoffText(base),17,base.finished?green:red,true));
        f.addView(label("Pay extra each payment",12,muted,true));EditText extra=field(f,"Extra (AUD)",true);TextView result=label("",15,blue,true);f.addView(result);
        f.addView(label("Interest is worked out monthly on what's owed, rounded to the cent; a weekly or fortnightly payment counts as its monthly average. Your lender's figures may differ a little.",12,muted,false));
        onText(extra,()->{long x;try{String t=extra.getText().toString().trim();x=t.isEmpty()?0:Budget.parse(t);}catch(Exception e){result.setText("");return;}if(x<=0){result.setText("");return;}
            Budget.Payoff faster=Budget.payoff(owed,rate,monthly,Budget.perMonth(x,a.frequency));if(!faster.finished){result.setText(payoffText(faster));return;}
            result.setText(money(x)+" extra: "+payoffText(faster)+(base.finished?"\n"+Budget.duration(base.months-faster.months)+" sooner, "+money(base.interest-faster.interest)+" less interest":""));});
        ScrollView scroll=new ScrollView(this);scroll.addView(f);new AlertDialog.Builder(this).setTitle("Payoff planner: "+a.name).setView(scroll).setPositiveButton("Close",null).show();
    }
    private String payoffText(Budget.Payoff p){if(!p.covers)return "This payment doesn't cover the interest: the loan would never be paid off.";if(!p.finished)return "At this payment it takes over 100 years to pay off.";
        return "Paid off by "+YearMonth.now().plusMonths(p.months).format(DateTimeFormatter.ofPattern("MMMM yyyy"))+" ("+Budget.duration(p.months)+"), with "+money(p.interest)+" interest";}
    private void load(){
        String raw=getSharedPreferences("budget",0).getString("data",null);loaded=raw;if(raw==null){for(String[] item:new String[][]{{"Rent","Bills"},{"Utilities","Bills"},{"Groceries","Everyday"},{"Transport","Everyday"},{"Dining out","Everyday"},{"Annual insurance","True expenses"},{"Car repairs","True expenses"},{"Emergency fund","Savings"}}){Budget.Category c=new Budget.Category(item[0]);c.group=item[1];budget.categories.add(c);}return;}
        try{budget=BudgetStore.decode(raw);if(!raw.contains("\"version\"")){String updated=BudgetStore.encode(budget);if(!getSharedPreferences("budget",0).edit().putString("legacy_backup",raw).putString("data",updated).commit())throw new IllegalStateException("Migration could not be saved.");loaded=updated;toast("Budget upgraded. Existing balances preserved; monthly assignments begin this month.");}}
        catch(Exception e){storageReadable=false;new AlertDialog.Builder(this).setTitle("Unable to load budget").setMessage("Your saved data has been preserved. Close the app to avoid changes.").setPositiveButton("Close",(d,w)->finish()).setCancelable(false).show();}
    }
}
