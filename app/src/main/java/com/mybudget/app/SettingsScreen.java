package com.mybudget.app;

import android.app.*;
import android.content.Intent;
import android.net.Uri;
import java.io.*;
import android.text.*;
import android.widget.*;
import java.time.*;
import java.util.*;

/** Settings: theme, currency, backup and restore, automatic backup, CSV import and export, flags, payees and import rules. */
final class SettingsScreen extends Ui {
    SettingsScreen(MainActivity main){super(main);}
    boolean showBackup; // open scrolled to Backup (Home's backup reminder)
    void settings(){
        main.content.addView(heading("Appearance",18,main.blue));LinearLayout appearance=card();
        appearance.addView(heading("Theme",20,main.ink));
        appearance.addView(label("Choose Light, Dark, or follow your device automatically.",14,main.muted,false));
        appearance.addView(button("Theme: "+main.themeMode,this::chooseTheme));
        // The budget's one currency (Budget.currency): how money is shown, and which of Planner's bills come in.
        LinearLayout money=card();money.addView(heading("Currency",20,main.ink));
        money.addView(label("Amounts are shown in this currency, in your phone's number style. Planner's bills are added only when they're in it.",14,main.muted,false));
        money.addView(button("Currency: "+code(),this::chooseCurrency));
        reminders();
        TextView backupTitle=heading("Backup",18,main.blue);main.content.addView(backupTitle);LinearLayout backup=card();
        if(showBackup){showBackup=false;main.content.post(()->((ScrollView)main.content.getParent()).smoothScrollTo(0,backupTitle.getTop()));} // from Home's backup reminder
        backup.addView(heading("Back up and restore",20,main.ink));
        backup.addView(label("Your budget is saved only on this device. Save a backup file somewhere safe, such as Drive or a computer, to restore it after a reinstall or on a new phone.",14,main.muted,false));
        String lastBackup=AutoBackup.lastBackup(main);backup.addView(label(lastBackup==null?"No backup yet":"Last backup: "+pretty(lastBackup),14,lastBackup==null?main.amber:main.ink,true));
        backup.addView(button("Back up budget",()->main.pick(new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("application/json")
            .putExtra(Intent.EXTRA_TITLE,"MyBudget-backup-"+LocalDate.now()+".json"),MainActivity.BACKUP)));
        backup.addView(button("Restore from backup",()->main.pick(new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*"),MainActivity.RESTORE)));
        String restoredAt=main.prefs().getString("before_restore_at",null);
        if(restoredAt!=null){backup.addView(label("Restored from a backup on "+when(restoredAt)+". Undo puts back the budget you had before.",13,main.muted,false));
            backup.addView(button("Undo restore",this::undoRestore));}
        int photos=main.photoCount();
        if(photos>0)backup.addView(label(count(photos,"photo stays","photos stay")+" on this phone: backups hold the budget, not photos.",13,main.muted,false));
        // Automatic backup to a folder picked once (Drive's folder works too, through the system picker).
        LinearLayout auto=card();auto.addView(heading("Automatic backup",20,main.ink));String tree=main.prefs().getString("auto_backup_tree",null);
        if(tree==null){auto.addView(label("Once a day, MyBudget can save a backup to a folder you choose, keeping the last 7.",14,main.muted,false));
            auto.addView(button("Choose a folder and turn on",()->{Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
                try{main.startActivityForResult(i,MainActivity.AUTO);}catch(android.content.ActivityNotFoundException e){toast("No app on this device can choose a folder.");}}));}
        else{String last=main.prefs().getString("auto_backup_last",null),error=main.prefs().getString("auto_backup_error",null);
            auto.addView(label("On: a backup a day to "+folderName(tree)+", keeping the last 7."+(last==null?"":" Last: "+pretty(last)+"."),14,main.muted,false));
            if(error!=null)auto.addView(label(error,13,main.red,true));
            auto.addView(button("Back up now",()->new Thread(()->{String e=AutoBackup.run(main,true);
                main.runOnUiThread(()->{toast(e==null?"Backed up to "+folderName(tree)+".":e);
                    if(main.tab.equals("Settings"))main.render();});}).start()));
            auto.addView(button("Turn off",()->{try{main.getContentResolver().releasePersistableUriPermission(Uri.parse(tree),Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION);}catch(Exception ignored){}
                main.prefs().edit().remove("auto_backup_tree").remove("auto_backup_last").remove("auto_backup_error").apply();
                AutoBackup.schedule(main);main.render();toast("Automatic backup is off. Backups already saved stay in the folder.");}));}
        main.content.addView(heading("Import",18,main.blue));LinearLayout imports=card();
        imports.addView(heading("Import a bank statement",20,main.ink));
        imports.addView(label("Pick a statement file from your bank: CSV (match its columns once), OFX, QFX or QIF. Rows already in the account are skipped, rows you entered yourself (or from Planner) are matched to them, and new payees go to "+CsvImport.TO_CATEGORIZE+" until you choose their category.",14,main.muted,false));
        imports.addView(button("Import transactions (CSV, OFX, QIF)",()->main.pick(new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*"),MainActivity.IMPORT)));
        LinearLayout export=card();export.addView(heading("Export transactions",20,main.ink));
        export.addView(label("A CSV file of every transaction for a spreadsheet. It can't be restored; use a backup for that.",14,main.muted,false));
        export.addView(button("Export transactions (CSV)",()->main.pick(new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("text/csv")
            .putExtra(Intent.EXTRA_TITLE,"MyBudget-transactions-"+LocalDate.now()+".csv"),MainActivity.EXPORT)));
        main.content.addView(heading("Transactions",18,main.blue));LinearLayout flags=card();flags.addView(heading("Flags",20,main.ink));
        StringBuilder named=new StringBuilder();
        for(int i=1;i<Budget.FLAGS.length;i++)if(!main.budget.flagNames[i].isEmpty())named.append(named.length()>0?", ":"").append(main.budget.flagLabel(i));
        flags.addView(label("Mark transactions with a coloured flag and filter by it. Give a colour a name once, such as green for Tax."+(named.length()>0?" Named: "+named+".":""),14,main.muted,false));
        flags.addView(button("Name your flags",this::nameFlags));
        LinearLayout payees=card();payees.addView(heading("Payees",20,main.ink));
        payees.addView(label("Rename or merge payees, hide old ones from suggestions, and set import rules that rename statement payees and choose their category.",14,main.muted,false));
        payees.addView(button("Payees and import rules",this::payees));
    }
    private void nameFlags(){
        LinearLayout f=form();
        f.addView(label("An optional name for each colour, shown wherever the flag is. Leave it empty for just the colour.",13,main.muted,false));
        EditText[] names=new EditText[Budget.FLAGS.length];
        for(int i=1;i<names.length;i++){TextView colour=label("● "+Budget.FLAGS[i],13,FLAG_COLORS[i],true);colour.setContentDescription(Budget.FLAGS[i]+" flag");f.addView(colour);
            names[i]=field(f,Budget.FLAGS[i]+" means… (optional)",false);names[i].setText(main.budget.flagNames[i]);
            names[i].setFilters(new InputFilter[]{new InputFilter.LengthFilter(30)});}
        dialog("Flag names",f,()->{for(int i=1;i<names.length;i++)main.budget.flagNames[i]=names[i].getText().toString().trim();});
    }
    // Payees: rename, merge, hide from suggestions; import rules for statements.
    void payees(){
        List<String> all=main.budget.allPayees();Map<String,Integer> uses=new HashMap<>();
        for(Budget.Entry e:main.budget.entries)if(!e.transfer())uses.merge(e.payee.trim().toLowerCase(Locale.ROOT),1,Integer::sum);
        List<MoveMenu.Choice> rows=new ArrayList<>();
        for(String p:all){int n=uses.getOrDefault(p.toLowerCase(Locale.ROOT),0);
            rows.add(MoveMenu.Choice.pick(p,()->payeeActions(p)).sub(count(n,"transaction","transactions")+(main.budget.hiddenPayee(p)?" · hidden from suggestions":"")));}
        List<MoveMenu.Choice> more=Collections.singletonList(new MoveMenu.Choice(MoveMenu.LIST,"Import rules ("+main.budget.rules.size()+")",true,this::rules));
        MoveMenu.sheet(this,all.isEmpty()?"No payees yet":"Payees",all.isEmpty()?"Payees appear here once you have transactions. Import rules can be set up now.":"Tap one to rename, merge, hide or set its category.",
            Arrays.asList(rows,more),null);
    }
    /** What a payee's category suggestion is, in words: "automatic (Groceries)", "always Groceries" or "none". */
    private String suggestionText(String payee){String set=main.budget.payeeCategories.get(payee.trim().toLowerCase(Locale.ROOT));Budget.Category fixed=set==null||set.isEmpty()?null:main.budget.category(set);
        if(set!=null&&set.isEmpty())return "none";if(fixed!=null)return "always "+fixed.name;Budget.Category usual=main.budget.category(main.budget.usualCategory(payee));
        return "automatic"+(usual==null?"":" ("+usual.name+")");}
    /** A payee's category suggestion: automatic (changes once two of its last three agree), always one category, or none. */
    private void payeeCategory(String payee){
        List<Budget.Category> cats=new ArrayList<>();for(Budget.Category c:main.budget.categories)if(!c.payment()&&!c.hidden)cats.add(c);
        String set=main.budget.payeeCategories.get(payee.trim().toLowerCase(Locale.ROOT)); // null automatic, "" none, else the category
        java.util.function.Consumer<String> choose=id->{if(main.change(()->main.budget.setPayeeCategory(payee,id)))toast(payee+": category "+suggestionText(payee)+".");};
        List<MoveMenu.Choice> ways=new ArrayList<>(),always=new ArrayList<>();
        ways.add(new MoveMenu.Choice(MoveMenu.RESET,"Automatic",false,()->choose.accept(null)).sub("The category it usually has: one odd purchase doesn't change it; two of its last three do").selected(set==null));
        ways.add(new MoveMenu.Choice(MoveMenu.HIDE,"Don't suggest a category",false,()->choose.accept("")).selected(set!=null&&set.isEmpty()));
        for(Budget.Category c:cats){String id=c.id;always.add(MoveMenu.Choice.pick("Always "+c.name,()->choose.accept(id)).sub(c.group).selected(id.equals(set)));
            always.get(always.size()-1).badge=c.name.isEmpty()?"·":new String(Character.toChars(c.name.codePointAt(0))).toUpperCase(Locale.ROOT);}
        MoveMenu.sheet(this,payee,"Category suggested for this payee",Arrays.asList(ways,always),null);
    }
    private void payeeActions(String payee){
        boolean hidden=main.budget.hiddenPayee(payee);
        java.util.function.IntConsumer act=n->{ // 0 rename, 1 merge, 2 hide or show, 3 category
            if(n==3){payeeCategory(payee);return;}
            if(n==0){LinearLayout f=form();
                f.addView(label("Every transaction and upcoming transaction with this payee gets the new name.",13,main.muted,false));
                EditText name=field(f,"New name",false);name.setText(payee);
                dialog("Rename payee",f,()->{if(main.budget.renamePayee(payee,name.getText().toString())==0)throw new IllegalArgumentException("That payee no longer has transactions.");});}
            else if(n==1){List<String> others=new ArrayList<>(main.budget.allPayees());others.removeIf(o->o.equalsIgnoreCase(payee));
                if(others.isEmpty()){toast("There's no other payee to merge with.");return;}
                List<MoveMenu.Choice> keeps=new ArrayList<>();
                for(String keep:others)keeps.add(MoveMenu.Choice.pick(keep,()->new AlertDialog.Builder(main).setTitle("Merge into "+keep+"?")
                        .setMessage("Transactions with "+payee+" become "+keep+". "+keep+" is the one kept.").setNegativeButton("Cancel",null)
                        .setPositiveButton("Merge",(d3,w)->{if(main.change(()->main.budget.mergePayees(Collections.singletonList(payee),keep)))toast("Merged into "+keep+".");}).show()));
                MoveMenu.sheet(this,"Merge "+payee+" into…","The payee you pick is the one kept",Collections.singletonList(keeps),null);}
            else if(main.change(()->main.budget.hidePayee(payee,!hidden)))toast(hidden?"Suggested again.":"Hidden from suggestions. Its transactions stay.");
        };
        List<MoveMenu.Choice> name=new ArrayList<>(),use=new ArrayList<>();
        name.add(new MoveMenu.Choice(MoveMenu.EDIT,"Rename",false,()->act.accept(0)));
        name.add(new MoveMenu.Choice(MoveMenu.MERGE,"Merge into another payee",false,()->act.accept(1)));
        use.add(new MoveMenu.Choice(MoveMenu.TAG,"Category: "+suggestionText(payee),false,()->act.accept(3)));
        use.add(new MoveMenu.Choice(hidden?MoveMenu.SHOW:MoveMenu.HIDE,hidden?"Show in suggestions":"Hide from suggestions",false,()->act.accept(2)));
        MoveMenu.sheet(this,payee,hidden?"Payee · hidden from suggestions":"Payee",Arrays.asList(name,use),null);
    }
    private String ruleText(Budget.Rule r){Budget.Category c=main.budget.category(r.category);
        return "Contains \""+r.contains+"\" → "+(r.rename.isEmpty()?"":"rename to "+r.rename)+(r.rename.isEmpty()||c==null?"":", ")+(c==null?"":"category "+c.name);}
    private void rules(){
        List<MoveMenu.Choice> rows=new ArrayList<>();
        for(int i=0;i<main.budget.rules.size();i++){int n=i;rows.add(new MoveMenu.Choice(MoveMenu.TAG,ruleText(main.budget.rules.get(i)),false,()->editRule(n)));}
        List<MoveMenu.Choice> add=Collections.singletonList(new MoveMenu.Choice(MoveMenu.COVER,"Add a rule",false,()->editRule(-1)));
        MoveMenu.sheet(this,"Import rules",rows.isEmpty()?"When a bank statement's payee contains some text, a rule renames it and/or gives it a category. Rules are checked in order, ignoring capitals; the first match wins. Without a match, the payee's last category is used as before."
            :"Checked in order, ignoring capitals; the first match wins. Tap one to change it.",Arrays.asList(rows,add),null);
    }
    private void editRule(int index){
        Budget.Rule old=index>=0&&index<main.budget.rules.size()?main.budget.rules.get(index):null;
        List<Budget.Category> cats=main.visibleCategories(old==null?null:main.budget.category(old.category));
        String[] names=new String[cats.size()+1];names[0]="Keep the usual guess";for(int i=0;i<cats.size();i++)names[i+1]=cats.get(i).name;
        LinearLayout f=form();
        f.addView(label("For bank statement imports. Capitals don't matter; the first matching rule wins.",13,main.muted,false));
        EditText contains=field(f,"Payee contains (e.g. WOOLWORTHS)",false);EditText rename=field(f,"Rename to (optional)",false);
        Spinner cat=spinner(f,"Category (optional)",names,old==null?0:cats.indexOf(main.budget.category(old.category))+1);
        if(old!=null){contains.setText(old.contains);rename.setText(old.rename);String text=old.contains;
            f.addView(button("Remove rule",()->{if(main.change(()->main.budget.rules.removeIf(r->r.contains.equals(text))))for(AlertDialog editor:new ArrayList<>(main.editors))editor.dismiss();}));}
        dialog(old==null?"New import rule":"Edit import rule",f,()->{int c=cat.getSelectedItemPosition();
            Budget.Rule r=new Budget.Rule(contains.getText().toString(),rename.getText().toString(),c==0?"":cats.get(c-1).id);
            int i=-1;if(old!=null){for(int k=0;k<main.budget.rules.size()&&i<0;k++)if(main.budget.rules.get(k).contains.equals(old.contains))i=k;
                if(i<0)throw new IllegalArgumentException("That rule was removed meanwhile.");}
            main.budget.validate(r,i<0?null:main.budget.rules.get(i));
            if(i<0)main.budget.rules.add(r);else main.budget.rules.set(i,r);}); // by its text: a failed save reads the budget in afresh
    }
    private String folderName(String tree){try{String id=android.provider.DocumentsContract.getTreeDocumentId(Uri.parse(tree));
            int c=id.lastIndexOf(':');String n=c>=0?id.substring(c+1):id;
            return n.isEmpty()?"the folder you chose":n;}catch(Exception e){return "the folder you chose";}}
    /** Matches a statement's columns (remembered by header name for next time), then imports into one account. */
    void importDialog(List<List<String>> rows){
        List<Budget.Account> accounts=main.openAccounts();accounts.removeIf(Budget.Account::tracking);
        if(accounts.isEmpty()){toast("Add an account first.");return;} // statements go into budget accounts
        List<String> first=rows.get(0);int columns=0;for(List<String> r:rows)columns=Math.max(columns,r.size());
        boolean header=CsvImport.looksLikeHeader(first);
        String[] names=new String[columns],withNone=new String[columns+1];withNone[0]="None: one signed amount column";
        for(int i=0;i<columns;i++){String sample=rows.size()>(header?1:0)&&i<rows.get(header?1:0).size()?rows.get(header?1:0).get(i):"";
            names[i]=(header&&i<first.size()&&!first.get(i).isEmpty()?first.get(i):"Column "+(i+1))+(sample.isEmpty()?"":"  (e.g. "+(sample.length()>24?sample.substring(0,24)+"…":sample)+")");withNone[i+1]=names[i];}
        // Guess from header words, or from what was used last time with the same headers.
        int date=guess(first,header,new String[]{"date"},0),payee=guess(first,header,new String[]{"description","payee","narrative","details","merchant","memo"},Math.min(1,columns-1)),amount=guess(first,header,new String[]{"amount","credit"},Math.min(2,columns-1)),out=-1;
        // Hunt 25 B4: with a debit column and a credit column ("Debit Amount", "Credit Amount"), money in is the credit one (not the
        // first "amount"), so spending isn't read as income.
        if(header){int debit=guess(first,true,new String[]{"debit","out","withdrawal"},-1),credit=guess(first,true,new String[]{"credit","deposit"},-1);
            if(debit>=0&&credit>=0&&credit!=debit){amount=credit;out=debit;}else if(debit>=0&&debit!=amount)out=debit;}
        String saved=main.prefs().getString("import_columns",null);if(saved!=null&&header){String[] p=saved.split("\u0001");
            if(p.length==5&&p[0].equals(String.join("\u0002",first))){date=Integer.parseInt(p[1]);payee=Integer.parseInt(p[2]);
                amount=Integer.parseInt(p[3]);out=Integer.parseInt(p[4]);}}
        LinearLayout f=form();f.addView(label(count(rows.size()-(header?1:0),"row","rows")+" in the file.",14,main.ink,true));
        CheckBox hasHeader=new CheckBox(main);hasHeader.setText("The first row is column names");hasHeader.setChecked(header);
        hasHeader.setMinHeight(dp(48));f.addView(hasHeader);
        Spinner dateCol=spinner(f,"Date",names,date),payeeCol=spinner(f,"Payee or description",names,payee),amountCol=spinner(f,"Amount (or money in)",names,amount),outCol=spinner(f,"Money out (if it's a separate column)",withNone,out+1),account=spinner(f,"Into account",accounts.stream().map(a->a.name).toArray(String[]::new),0);
        f.addView(label("Imported rows are marked cleared and wait for you to review them. Import rules (Settings > Payees) apply first. A row with the same amount as a transaction already here, dated within a week, is matched to it instead of added. Dates in the future or before the account opened are skipped.",12,main.muted,false));
        ScrollView scroll=new ScrollView(main);scroll.addView(f);AlertDialog d=new AlertDialog.Builder(main).setTitle("Import transactions")
            .setView(scroll).setNegativeButton("Cancel",null).setPositiveButton("Import",null).create();
        d.setOnShowListener(v->d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(w->{
            boolean h=hasHeader.isChecked();
            int dc=dateCol.getSelectedItemPosition(),pc=payeeCol.getSelectedItemPosition(),ac=amountCol.getSelectedItemPosition(),oc=outCol.getSelectedItemPosition()-1;
            String format=CsvImport.detectDateFormat(rows,dc,h);
            if(format==null){toast("MyBudget can't read the dates in that column. Check the Date column.");return;}
            Budget.Account a=accounts.get(account.getSelectedItemPosition());CsvImport.Result[] r=new CsvImport.Result[1];
            if(!main.change(()->r[0]=CsvImport.run(main.budget,rows,h,dc,pc,ac,oc,format,main.accountById(a.id))))return;
            if(h)main.prefs().edit().putString("import_columns",String.join("\u0002",first)+"\u0001"+dc+"\u0001"+pc+"\u0001"+ac+"\u0001"+oc).apply();
            d.dismiss();CsvImport.Result x=r[0];List<String> skipped=new ArrayList<>();if(x.duplicates>0)skipped.add(x.duplicates+" already there");
            if(x.future>0)skipped.add(x.future+" in the future");if(x.beforeOpening>0)skipped.add(x.beforeOpening+" before the account opened");
            if(x.unreadable>0)skipped.add(x.unreadable+" unreadable");
            String sorting=null;for(Budget.Entry e:x.entries){Budget.Category c=main.budget.category(e.category);
                if(c!=null&&c.name.equals(CsvImport.TO_CATEGORIZE))sorting=c.id;}
            // Matched rows: each was already here (entered by hand, from Planner, or upcoming); listed so a wrong match can be spotted.
            StringBuilder matched=new StringBuilder();if(x.matched>0){matched.append("\n\n").append(count(x.matched,"row was","rows were")).append(" already here, entered by you, from Planner or upcoming: matched and cleared, dated as on the statement:");
                for(Budget.Entry e:x.matches.subList(0,Math.min(8,x.matches.size())))matched.append("\n• ").append(e.payee).append(" ").append(money(e.amount)).append(", ").append(pretty(e.date));
                if(x.matches.size()>8)matched.append("\n• and ").append(x.matches.size()-8).append(" more");}
            new AlertDialog.Builder(main).setTitle(count(x.added,"transaction","transactions")+" imported")
                .setMessage((skipped.isEmpty()?"Nothing was skipped.":"Skipped: "+String.join(", ",skipped)+".")+matched+(x.matchedRules>0?" "+count(x.matchedRules,"matched rule","matched rules")+".":"")+(x.added>0?"\n\nThey're marked to review: check them in Transactions and approve them.":"")+(sorting!=null?"\n\nSome are in "+CsvImport.TO_CATEGORIZE+": open them in Transactions to choose their category.":""))
                .setPositiveButton("OK",null).show();
            // Transactions shows the statement's dates (not just the month on screen), so the new rows are all there.
            if(x.added>0){String from=null,to=null;for(Budget.Entry e:x.entries){if(from==null||e.date.compareTo(from)<0)from=e.date;
                    if(to==null||e.date.compareTo(to)>0)to=e.date;}main.clearFilters();main.fromFilter=from;main.toFilter=to;
                if(sorting!=null)main.categoryFilter=sorting;main.tab="Spending";main.render();}
        }));d.show();
    }
    private static int guess(List<String> header,boolean has,String[] words,int fallback){if(has)for(String w:words)for(int i=0;i<header.size();i++)if(header.get(i).toLowerCase(Locale.ROOT).contains(w))return i;return fallback;}
    void confirmRestore(BudgetStore.Backup backup){
        Budget b=backup.budget;int missing=0;
        for(Budget.Entry e:b.entries)if(!e.photo.isEmpty()&&!new File(main.photoDir(),e.photo).isFile()){e.photo="";
            missing++;} // photos aren't in backups
        new AlertDialog.Builder(main).setTitle("Restore this backup?")
            .setMessage("Backup made "+when(backup.created)+"\n\n"+count(b.accounts.size(),"account","accounts")+", "+count(b.categories.size(),"category","categories")+", "+count(b.entries.size(),"transaction","transactions")+"."+(missing>0?" "+count(missing,"photo isn't","photos aren't")+" on this phone (backups don't hold photos).":"")+"\n\nThis replaces the budget on this device. You can undo it afterwards in Settings.")
            .setNegativeButton("Cancel",null).setPositiveButton("Restore",(d,w)->{
                // The budget being replaced is kept (empty: there was none) for Undo restore.
                String current=main.prefs().getString("data",null);
                try{String raw=BudgetStore.encode(b);
                    if(!main.prefs().edit().putString("data",raw).putString("before_restore",current==null?"":current).putString("before_restore_at",LocalDateTime.now().withNano(0).toString()).remove("before_reset").remove("before_reset_at").commit())throw new IllegalStateException();main.loaded=raw;BudgetWidget.refresh(main);}
                catch(Exception e){toast("Could not save the restored budget. Nothing was changed.");return;}
                main.budget=b;for(AlertDialog editor:new ArrayList<>(main.editors))editor.dismiss();main.render();toast("Budget restored.");
            }).show();
    }
    private void undoRestore(){
        new AlertDialog.Builder(main).setTitle("Undo restore?")
            .setMessage("Puts back the budget you had before restoring on "+when(main.prefs().getString("before_restore_at",""))+". Changes made since the restore are lost.")
            .setNegativeButton("Cancel",null).setPositiveButton("Undo restore",(d,w)->{
                String before=main.prefs().getString("before_restore",null);if(before==null){main.render();return;}
                Budget previous;
                try{previous=before.isEmpty()?null:BudgetStore.decode(before);}catch(Exception e){toast("The budget from before the restore can't be read. Nothing was changed.");return;}
                android.content.SharedPreferences.Editor edit=main.prefs().edit().remove("before_restore").remove("before_restore_at").remove("before_reset").remove("before_reset_at");
                if(previous==null)edit.remove("data");else edit.putString("data",before);
                if(!edit.commit()){toast("Could not save to device storage.");return;}BudgetWidget.refresh(main);
                if(previous==null){main.budget=new Budget();main.load();}else{main.budget=previous;main.loaded=before;}
                for(AlertDialog editor:new ArrayList<>(main.editors))editor.dismiss();main.render();toast("Restore undone.");
            }).show();
    }
    // Common currencies first, then the rest A to Z. Amounts stay as they are (no conversion), so the change asks first.
    private void chooseCurrency(){
        List<String> codes=Budget.currencyChoices();List<MoveMenu.Choice> rows=new ArrayList<>();
        for(String chosen:codes){Currency cur=Currency.getInstance(chosen);MoveMenu.Choice row=MoveMenu.Choice.pick(cur.getDisplayName(Locale.getDefault()),()->{
            if(chosen.equals(code()))return;
            new AlertDialog.Builder(main).setTitle("Show amounts in "+chosen+"?")
                .setMessage("Amounts aren't converted, only shown in "+chosen+": "+Budget.money(10000,main.money())+" becomes "+Budget.money(10000,Budget.moneyFormat(chosen,Locale.getDefault()))+". Planner's bills in other currencies won't be added or planned for.")
                .setNegativeButton("Cancel",null).setPositiveButton("Change to "+chosen,(d,w)->{
                    try{main.commit(()->main.budget.currency=chosen);main.render();toast("Amounts are now shown in "+chosen+".");}catch(Exception e){toast(e.getMessage());}}).show();
            }).sub(chosen).selected(chosen.equals(code()));
            // The circle: a short symbol as it is ("A$", "€"), a long one's sign ("US$" → "$"), else the code's start ("AED" → "AE").
            String symbol=cur.getSymbol(Locale.getDefault()),sign=symbol.replaceAll("^\\p{L}+(?=\\P{L})","");
            row.badge=symbol.matches(".*\\P{L}.*")&&sign.length()<=2?(symbol.length()<=2?symbol:sign):chosen.substring(0,2);
            rows.add(row);}
        MoveMenu.sheet(this,"Currency","Amounts are shown in it; they aren't converted",Collections.singletonList(rows),null);
    }
    /**
     * Reminders, as Planner's Settings › Notifications: whether notifications are on, "Reminder sound" (what a reminder left at
     * Default does), a test notification and a test alarm, and while exact alarms are off, the way to allow them.
     */
    private void reminders(){
        main.content.addView(heading("Reminders",18,main.blue));LinearLayout card=card();card.addView(heading("Reminders",20,main.ink));
        card.addView(label("Add reminders to a transaction or an upcoming one in its form. They count from "+Reminders.time(Reminders.START,main)+" on its date.",14,main.muted,false));
        if(!ReminderNotifications.notificationsEnabled(main)){card.addView(label("Notifications are off, so reminders won't show.",14,main.red,false));
            card.addView(button("Turn on notifications",()->main.askNotifications(main::render)));}
        ReminderSound sound=ReminderSound.setting(main);
        card.addView(button("Reminder sound: "+sound.label,()->{List<MoveMenu.Choice> rows=new ArrayList<>();
            for(ReminderSound s:ReminderSound.values()){if(s==ReminderSound.DEFAULT)continue;
                rows.add(new MoveMenu.Choice(s.alarmSeconds()==null?MoveMenu.HIDE:MoveMenu.SNOOZE,s.label,false,()->{
                    if(!ReminderSound.write(main,s)){toast("Could not save that setting.");return;}ReminderScheduler.request(main);main.render();})
                    .sub(s==ReminderSound.NOTIFICATION?"Silent and vibrate mode mute it":s.ring?"Rings as an alarm until you stop it":"Rings as an alarm, then stops by itself").selected(s==sound));}
            MoveMenu.sheet(this,"Reminder sound","For reminders left at Default",Collections.singletonList(rows),null);}));
        card.addView(label("A ringing reminder sounds like an alarm, also in silent and vibrate mode, and shows over the lock screen.",12,main.muted,false));
        Button note=button("Test notification",()->{if(!ReminderNotifications.sendTest(main))toast("Notifications are off. Turn them on first.");});
        Button alarm=button("Test alarm",()->{if(!AlarmService.startTest(main))toast("Notifications are off, so an alarm couldn't be stopped. Turn them on first.");});
        pair(card,note,alarm);
        if(!ReminderNotifications.exactAlarmsAllowed(main)){card.addView(label("Exact alarms are off: reminders may come late, and can't ring, only notify.",13,main.amber,false));
            card.addView(button("Allow Alarms & reminders",()->{try{main.startActivity(new android.content.Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                android.net.Uri.fromParts("package",main.getPackageName(),null)));}catch(Exception e){ReminderNotifications.openAppSettings(main);}}));}
        card.addView(button("Notification settings",()->{try{ReminderNotifications.openNotificationSettings(main);}catch(Exception e){toast("Open Android's settings for MyBudget.");}}));
    }
    private void chooseTheme(){
        String[] modes={"Light","Dark","Auto"},names={"Light","Dark","Auto"},about={"Light background, dark text","Dark background, easy on the eyes at night","Follows your phone's dark theme setting"};
        int[] icons={MoveMenu.SUN,MoveMenu.MOON,MoveMenu.AUTO};List<MoveMenu.Choice> rows=new ArrayList<>();
        for(int i=0;i<modes.length;i++){String chosen=modes[i];rows.add(new MoveMenu.Choice(icons[i],names[i],false,()->{
            if(chosen.equals(main.themeMode))return;
            if(!main.getSharedPreferences("appearance",0).edit().putString("theme",chosen).commit()){toast("Could not save your theme. Try again.");return;}
            main.recreate();}).sub(about[i]).selected(chosen.equals(main.themeMode)));}
        MoveMenu.sheet(this,"Appearance",null,Collections.singletonList(rows),null);
    }
}
