package com.mybudget.app;

import android.app.*;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import java.time.*;
import java.util.*;

/** The transaction, transfer and split forms, upcoming transactions' actions and photo viewing, shared by the screens. */
final class TransactionForms extends Ui {
    TransactionForms(MainActivity main){super(main);}
    private boolean reconciledOk; // the reconciled warning was answered: open the form
    private void viewPhoto(String name){android.graphics.Bitmap bm=main.photoBitmap(name,1600);
        if(bm==null){toast("The photo isn't on this phone.");return;}ImageView img=new ImageView(main);img.setImageBitmap(bm);
        img.setAdjustViewBounds(true);img.setContentDescription("Photo of this transaction");new AlertDialog.Builder(main).setView(img)
            .setPositiveButton("Close",null).show();}
    // Hunt 23: an edited transaction keeps its place in the list (the newest first), so it doesn't pass for the newest. False: not there.
    private boolean put(String id,Budget.Entry e){List<Budget.Entry> all=main.budget.entries;
        for(int i=0;i<all.size();i++)if(all.get(i).id.equals(id)){all.set(i,e);return true;}return false;}
    // Hunt 23: [c] as the budget has it now (the data may have been read in again since the form opened).
    private Budget.Category fresh(Budget.Category c){Budget.Category now=main.budget.category(c.id);return now==null?c:now;}
    private static final int CATEGORY_CHIPS=6; // the most-used categories shown as chips in Add transaction
    private static final String[] REPEAT_LABELS={"Doesn't repeat","Weekly","Every 2 weeks","Monthly","Every 3 months","Yearly"};
    static final int NOTE_CHIPS=3; // the payee's recent notes offered under the note box
    /** [payee]'s last few notes, newest first, each once (not what's typed now, nor the import and Wise markers). */
    private List<String> recentNotes(String payee,String typed){List<String> notes=new ArrayList<>();String p=payee.trim();if(p.isEmpty())return notes;
        List<Budget.Entry> theirs=new ArrayList<>();for(Budget.Entry e:main.budget.entries)if(e.payee.trim().equalsIgnoreCase(p))theirs.add(e);
        theirs.sort((a,b)->b.date.compareTo(a.date));Set<String> seen=new HashSet<>();
        for(Budget.Entry e:theirs){String m=e.memo.trim();if(m.isEmpty()||m.equals(Budget.IMPORTED)||WiseNotifications.fromWise(e)||m.equalsIgnoreCase(typed.trim())||!seen.add(m.toLowerCase(Locale.ROOT)))continue;
            notes.add(m);if(notes.size()==NOTE_CHIPS)break;}
        return notes;}
    void transaction(Budget.Entry old){transaction(old,null);}
    /**
     * Adds or edits a transaction ([old]) or an upcoming one ([sched]). A new one with a future date or a repeat
     * becomes upcoming: it waits in Transactions until its day, when the user enters or skips it.
     */
    void transaction(Budget.Entry old,Budget.Scheduled sched){
        if(old!=null&&old.reconciled&&!reconciledOk){reconciledWarning(old,()->{reconciledOk=true;try{transaction(old,sched);}finally{reconciledOk=false;}});return;}
        if(old!=null&&main.budget.cardCharge(old)){cardCharge(old.account,old);return;}
        if(main.budget.accounts.isEmpty()){toast("Add an account first.");main.accountsScreen.addAccount();return;}
        if(old!=null&&old.transfer()){editTransfer(old);return;}
        if(old!=null&&main.budget.account(old.account)!=null&&main.budget.account(old.account).tracking()){trackingEntry(old.account,old);return;}
        String keepAccount=old!=null?old.account:sched!=null?sched.account:null,keepCategory=old!=null?old.category:sched!=null?sched.category:"";
        long oldAmount=old!=null?old.amount:sched!=null?sched.amount:-1;
        List<Budget.Account> accounts=keepAccount==null?main.openAccounts():main.openAccounts(main.budget.account(keepAccount));
        accounts.removeIf(Budget.Account::tracking);
        List<Budget.Category> categories=main.visibleCategories(main.budget.category(keepCategory)); // tracking accounts: Update balance, or a transfer
        if(accounts.isEmpty()){boolean onlyTracking=true;for(Budget.Account a:main.budget.accounts)if(!a.tracking())onlyTracking=false;
            toast(onlyTracking?"Add a bank, cash or card account first. Tracking accounts change with Update balance.":"All your accounts are closed. Reopen one in Accounts first.");return;}
        boolean adding=old==null&&sched==null;
        LinearLayout f=form();
        // 1. What it is first (Expense, Income, Refund), then the amount: large, signed and coloured by where the money goes (− red
        // out, + green in). A - or + typed first picks Expense or Income and is taken off, so the box always holds the size of the amount.
        Choice kind=choice(f,new String[]{"− Expense","+ Income","↩ Refund"},new int[]{main.red,main.green,main.green},oldAmount<0?0:keepCategory.isEmpty()?1:2);
        LinearLayout amountRow=new LinearLayout(main);amountRow.setGravity(Gravity.CENTER_VERTICAL);amountRow.setPadding(0,dp(4),0,dp(4));
        TextView sign=label("−",36,main.red,true);sign.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);sign.setPadding(0,0,dp(6),0);
        amountRow.addView(sign);EditText amount=new EditText(main);amount.setHint("0.00");amount.setSingleLine(true);amount.setInputType(AMOUNT_INPUT);
        amount.setTextSize(36);amount.setTypeface(null,android.graphics.Typeface.BOLD);amount.setFontFeatureSettings("tnum");sumsHint(amount,"Amount ("+code()+")");
        amountRow.addView(amount,new LinearLayout.LayoutParams(0,-2,1));TextView currency=label(code(),15,main.muted,true);
        currency.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);amountRow.addView(currency);f.addView(amountRow);
        Runnable paint=()->{boolean out=kind.selected()==0;int c=out?main.red:main.green;sign.setText(out?"−":"+");sign.setTextColor(c);amount.setTextColor(c);};
        // 2. Who, and a note. Payees used before are suggested; picking one on a new transaction fills in the category it had last
        // time and offers last time's amount.
        AutoCompleteTextView payee=suggestField(f,"Payee",()->main.budget.payees());
        TextView lastTime=label("",13,main.blue,true);lastTime.setVisibility(View.GONE);lastTime.setMinHeight(dp(48));lastTime.setGravity(Gravity.CENTER_VERTICAL);pressable(lastTime);f.addView(lastTime);
        // The note, always in sight (it was folded away under More): notes used before are suggested as it's typed, those with this
        // payee first, and this payee's last few notes are chips under it, one tap to use.
        AutoCompleteTextView memo=suggestField(f,"Add a note (optional)",()->main.budget.memos(payee.getText().toString()));
        Chips noteChips=chips(f);
        Runnable showNotes=()->{List<String> notes=recentNotes(payee.getText().toString(),memo.getText().toString());
            noteChips.view.setVisibility(notes.isEmpty()?View.GONE:View.VISIBLE);if(notes.isEmpty())return;
            List<String> shown=new ArrayList<>();for(String m:notes)shown.add(m.length()>40?m.substring(0,39)+"…":m); // a long one shortened on its chip
            noteChips.show(shown,-1,n->{memo.setText(notes.get(n),false);memo.setSelection(memo.length());});};
        onText(payee,showNotes);onText(memo,showNotes);showNotes.run();
        LinearLayout categoryFields=column();f.addView(categoryFields);
        // Category: the 6 used most in the last 120 days as chips, then "All categories…" for the rest, wrapped onto more lines so
        // all of them are in sight. A category not among the 6 (picked from the list, or the one being edited) takes the 6th place.
        int[] cat={keepCategory.isEmpty()?-1:categories.indexOf(main.budget.category(keepCategory))};
        Map<String,Integer> uses=new HashMap<>();String since=LocalDate.now().minusDays(120).toString();
        for(Budget.Entry e:main.budget.entries)if(e.date.compareTo(since)>=0&&!e.category.isEmpty())uses.merge(e.category,1,Integer::sum);
        List<Budget.Category> often=new ArrayList<>(categories);often.sort((a,b)->uses.getOrDefault(b.id,0)-uses.getOrDefault(a.id,0));
        List<Budget.Category> top=new ArrayList<>(often.subList(0,Math.min(CATEGORY_CHIPS,often.size()))),shown=new ArrayList<>(top);
        TextView categoryTitle=label("Category",12,main.muted,true);categoryFields.addView(categoryTitle);Chips categoryChips=chips(categoryFields,true);
        LinearLayout previewRow=new LinearLayout(main);previewRow.setGravity(Gravity.END|Gravity.CENTER_VERTICAL);categoryFields.addView(previewRow,new LinearLayout.LayoutParams(-1,-2)); // Split at the end, also with no preview
        TextView preview=label("",14,main.muted,true);previewRow.addView(preview,new LinearLayout.LayoutParams(0,-2,1));
        // Split: the parts (positive amounts while editing) replace the category; the amount becomes their total.
        List<Budget.Split> parts=new ArrayList<>();
        for(Budget.Split p:old!=null?old.splits:sched!=null?sched.splits:new ArrayList<Budget.Split>()){Budget.Split c=new Budget.Split(p.category,Math.abs(p.amount));c.memo=p.memo;parts.add(c);
            Budget.Category pc=main.budget.category(p.category);if(pc!=null&&!pc.payment()&&!categories.contains(pc))categories.add(pc);} // a part's hidden category stays choosable
        TextView splitSummary=label("",13,main.ink,false);categoryFields.addView(splitSummary);
        Button splitButton=button("Split",()->{});splitButton.setBackground(bg(Color.TRANSPARENT));splitButton.setTextSize(13);
        previewRow.addView(splitButton,new LinearLayout.LayoutParams(-2,-2)); // beside the preview: it's the category's own option
        // A card transaction that is really the card's interest or a fee (an imported one, say) becomes one (cardCharge).
        Budget.Account onCard=old==null?null:main.budget.account(old.account);
        if(onCard!=null&&onCard.credit()&&!old.split()){Button charge=button("It's interest or a fee on "+onCard.name,()->{for(AlertDialog ed:new ArrayList<>(main.editors))ed.dismiss();cardCharge(old.account,old);});
            charge.setBackground(bg(Color.TRANSPARENT));charge.setTextSize(13);categoryFields.addView(charge);}
        boolean[] categoryChosen={old!=null||sched!=null};
        Runnable[] showPreview={()->{}},showCategories={null};
        showCategories[0]=()->{shown.clear();shown.addAll(top);
            if(cat[0]>=0&&!top.contains(categories.get(cat[0]))){if(shown.size()<CATEGORY_CHIPS)shown.add(categories.get(cat[0]));else shown.set(CATEGORY_CHIPS-1,categories.get(cat[0]));}
            List<String> names=new ArrayList<>();for(Budget.Category c:shown)names.add(c.name);names.add("All categories…");names.add("+ New category");
            // A new category (Budget's form): once saved it joins the list and is the one chosen; what was typed here stays.
            Runnable newCategory=()->main.budgetScreen.newCategory(c->{if(!categories.contains(c))categories.add(c);
                cat[0]=categories.indexOf(c);categoryChosen[0]=true;showCategories[0].run();showPreview[0].run();});
            categoryChips.show(names,cat[0]<0?-1:shown.indexOf(categories.get(cat[0])),n->{
                if(n<shown.size()){cat[0]=categories.indexOf(shown.get(n));categoryChosen[0]=true;showCategories[0].run();showPreview[0].run();return;}
                if(n>shown.size()){newCategory.run();return;}
                YearMonth m=YearMonth.now();List<MoveMenu.Choice> all=new ArrayList<>();
                all.add(new MoveMenu.Choice(MoveMenu.COVER,"New category…",false,newCategory).sub("Name it, pick its group, a target if you like"));
                for(int k=0;k<categories.size();k++){int i=k;Budget.Category c=categories.get(k);
                    all.add(categoryPick(c,main.budget.available(fresh(c),m),()->{cat[0]=i;categoryChosen[0]=true;showCategories[0].run();showPreview[0].run();}).selected(cat[0]==k));}
                MoveMenu.sheet(this,"Choose a category","Available this month",Collections.singletonList(all),null);});};
        showCategories[0].run();
        Runnable showSplit=()->{boolean on=!parts.isEmpty();categoryChips.view.setVisibility(on?View.GONE:View.VISIBLE);categoryTitle.setVisibility(categoryChips.view.getVisibility());
            splitSummary.setVisibility(on?View.VISIBLE:View.GONE);splitButton.setText(on?"Edit split":"Split");splitButton.setContentDescription(on?"Edit split":"Split into categories");amount.setEnabled(!on);
            if(on){long sum=0;StringBuilder s=new StringBuilder("Split: ");
                for(int i=0;i<parts.size();i++){Budget.Split p=parts.get(i);Budget.Category c=main.budget.category(p.category);sum+=p.amount;
                    s.append(i>0?", ":"").append(c==null?"To budget":c.name).append(" ").append(money(p.amount));}splitSummary.setText(s);
                amount.setText(decimal(sum));}showPreview[0].run();};
        splitButton.setOnClickListener(v->{long total;try{total=Budget.cents(amount.getText().toString());}catch(Exception e){total=0;}
            editSplit(parts,categories,categories.isEmpty()?null:categories.get(Math.max(0,cat[0])),total,showSplit,kind.selected()==0?-1:1);});
        payee.setOnItemClickListener((p,v,position,id)->{Budget.Entry last=main.budget.lastForPayee(payee.getText().toString());
            if(last!=null&&old==null&&!last.split()){long size=Math.abs(last.amount);lastTime.setText("Last time "+money(size)+" · tap to use it");
                lastTime.setOnClickListener(w->{amount.setText(decimal(size));amount.setSelection(amount.length());lastTime.setVisibility(View.GONE);});
                lastTime.setVisibility(amount.getText().toString().trim().isEmpty()?View.VISIBLE:View.GONE);}
            if(old!=null||categoryChosen[0]||last==null)return;
            // Hunt 23 M4: no category is income only for money in, outside a tracking account (a loan's payment has none either),
            // and never over a kind the user picked (or typed a sign for).
            // Hunt 24 B8: a payee set to always one category gets it even when its last transaction had none (income).
            String set=main.budget.payeeCategories.get(payee.getText().toString().trim().toLowerCase(Locale.ROOT));boolean fixed=set!=null&&!set.isEmpty();
            if(last.category.isEmpty()&&!fixed){Budget.Account la=main.budget.account(last.account);
                if(last.amount>0&&(la==null||!la.tracking())&&!kind.picked)kind.set(1);return;}
            // The payee's suggestion: its usual category (one odd purchase doesn't change it), a fixed one, or none (Settings > Payees).
            String usual=main.budget.suggestedCategory(payee.getText().toString());if(usual==null)return;int i=categories.indexOf(main.budget.category(usual));if(i<0)return;
            cat[0]=i;showCategories[0].run();if(kind.selected()==1&&!kind.picked)kind.set(last.amount<0||last.category.isEmpty()?0:2);showPreview[0].run();});
        // 3. When and where: Today, Yesterday or a picked date; the account as chips (a new one starts on the account used last).
        f.addView(label("Date",12,main.muted,true));
        LinearLayout hidden=column();EditText day=dateField(hidden,old!=null?old.date:sched!=null?sched.next:LocalDate.now().toString(),old==null);
        Chips dates=chips(f);
        Runnable[] showDates={null};showDates[0]=()->{LocalDate d=LocalDate.parse((String)day.getTag()),today=LocalDate.now();
            boolean picked=!d.equals(today)&&!d.equals(today.minusDays(1));
            dates.show(Arrays.asList("Today","Yesterday",picked?pretty(d.toString()):"Pick a date…"),d.equals(today)?0:picked?2:1,n->{
                if(n==2){day.performClick();return;}String iso=today.minusDays(n).toString();day.setTag(iso);day.setText(pretty(iso));});};
        onText(day,()->{showDates[0].run();showPreview[0].run();});showDates[0].run();
        int lastUsed=-1;String lastAccount=main.getSharedPreferences("appearance",0).getString("last_account","");
        for(int i=0;i<accounts.size();i++)if(accounts.get(i).id.equals(keepAccount!=null?keepAccount:lastAccount))lastUsed=i;
        int[] acc={Math.max(0,lastUsed)};TextView accountTitle=label("Account",12,main.muted,true);f.addView(accountTitle);Chips accountChips=chips(f);
        Runnable[] showAccounts={null};showAccounts[0]=()->{List<String> names=new ArrayList<>();for(Budget.Account a:accounts)names.add(a.name);
            accountChips.show(names,acc[0],n->{acc[0]=n;showAccounts[0].run();});};showAccounts[0].run();
        // 4. More options, folded away: repeat, pay later, reminders, photo, flag and Cleared, the things most transactions don't
        // need. Folded, a line under it says which of them this one has; it opens by itself when the one being edited has any.
        Button moreButton=button("",()->{});moreButton.setBackground(bg(Color.TRANSPARENT));moreButton.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);
        moreButton.setTextSize(15);moreButton.setPadding(0,dp(8),dp(12),dp(8));moreButton.setTypeface(null,android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams mp=new LinearLayout.LayoutParams(-1,-2);mp.setMargins(0,dp(14),0,0);f.addView(moreButton,mp);
        TextView moreSummary=label("",13,main.muted,false);moreSummary.setPadding(0,0,dp(12),dp(4));f.addView(moreSummary);
        LinearLayout more=column();f.addView(more);
        Spinner repeat=null;
        if(old==null){repeat=spinner(more,"Repeat",REPEAT_LABELS,sched==null?0:Arrays.asList(Budget.Scheduled.REPEATS).indexOf(sched.repeat));
            more.addView(label("A future date or a repeat makes it upcoming: it waits in Transactions, and you enter it when the day comes.",12,main.muted,false));}
        // Pay later (a new expense only): bought on a pay-later plan (Pay in 4, Afterpay, Zip), paid off in instalments from the
        // account chosen above (Budget.payLater). The purchase is spent in its category now; the payments are transfers.
        boolean[] later={false},firstNow={true},remindEach={false};String[] planId={""},every={"Every 2 weeks"};
        CheckBox laterBox=new CheckBox(main);laterBox.setText("Pay later: Pay in 4, Afterpay, Zip…");laterBox.setTextColor(main.ink);laterBox.setMinHeight(dp(48));
        LinearLayout laterFields=column();laterFields.setVisibility(View.GONE);if(adding){more.addView(laterBox);more.addView(laterFields);}
        laterFields.addView(label("Plan",12,main.muted,true));Chips planChips=chips(laterFields,true);
        laterFields.addView(label("Number of payments",12,main.muted,true));EditText payments=field(laterFields,"Number of payments",false);payments.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);payments.setText("4");
        laterFields.addView(label("Every",12,main.muted,true));Chips everyChips=chips(laterFields,true); // wrapped: large text fits
        CheckBox firstToday=new CheckBox(main);firstToday.setText("First payment on the purchase date");firstToday.setChecked(true);firstToday.setTextColor(main.ink);firstToday.setMinHeight(dp(48));laterFields.addView(firstToday);
        CheckBox remindBox=new CheckBox(main);remindBox.setText("Remind me the day before each payment");remindBox.setTextColor(main.ink);remindBox.setMinHeight(dp(48));laterFields.addView(remindBox);
        TextView schedule=label("",13,main.ink,false);laterFields.addView(schedule);
        Runnable[] showLater={null},showPlans={null};
        showPlans[0]=()->{List<Budget.Account> plans=new ArrayList<>();for(Budget.Account a:main.budget.accounts)if(a.payLater&&!a.closed)plans.add(a);
            if(main.budget.account(planId[0])==null&&!plans.isEmpty())planId[0]=plans.get(0).id;
            List<String> names=new ArrayList<>();int on=-1;for(int i=0;i<plans.size();i++){names.add(plans.get(i).name);if(plans.get(i).id.equals(planId[0]))on=i;}names.add("+ New plan…");
            planChips.show(names,on,n->{if(n<plans.size()){planId[0]=plans.get(n).id;showPlans[0].run();}else newPlan(name->{
                for(Budget.Account a:main.budget.accounts)if(a.payLater&&a.name.equals(name))planId[0]=a.id;showPlans[0].run();});});};
        // The payments as they'll be: the first (paid now when it's on or before today), then the rest with their dates.
        showLater[0]=()->{everyChips.show(Arrays.asList(Budget.PAY_EVERY),Arrays.asList(Budget.PAY_EVERY).indexOf(every[0]),n->{every[0]=Budget.PAY_EVERY[n];showLater[0].run();});
            accountTitle.setText(later[0]?"Payments come from":"Account");laterFields.setVisibility(later[0]?View.VISIBLE:View.GONE);
            long total;int n;try{total=Budget.cents(amount.getText().toString());n=Integer.parseInt(payments.getText().toString().trim());}catch(Exception e){schedule.setText("");return;}
            if(n<2||n>Budget.MAX_INSTALMENTS){schedule.setText("Choose 2 to "+Budget.MAX_INSTALMENTS+" payments.");schedule.setTextColor(main.amber);return;}
            LocalDate bought=LocalDate.parse((String)day.getTag()),first=firstNow[0]?bought:Budget.instalmentDate(bought,every[0],1);long[] sizes=Budget.instalments(total,n);
            StringBuilder s=new StringBuilder();for(int i=0;i<n;i++){LocalDate d=Budget.instalmentDate(first,every[0],i); // "$25.02 on 12 Oct (paid now), then $24.99 on 26 Oct, 9 Nov and 23 Nov"
                s.append(i==0?"":i==1?", then ":i==n-1?" and ":", ").append(i<=1||sizes[i]!=sizes[i-1]?money(sizes[i])+" on ":"").append(pretty(d.toString()))
                    .append(!d.isAfter(LocalDate.now())?" (paid now)":"");}
            schedule.setTextColor(main.ink);schedule.setText(s);};
        laterBox.setOnCheckedChangeListener((v,on)->{later[0]=on;if(on)showPlans[0].run();showLater[0].run();});
        firstToday.setOnCheckedChangeListener((v,on)->{firstNow[0]=on;showLater[0].run();});remindBox.setOnCheckedChangeListener((v,on)->remindEach[0]=on);
        onText(payments,()->showLater[0].run());onText(day,()->showLater[0].run());
        // The category's Available after this transaction, in the month of its date: what's left (green) or overspent by (red).
        showPreview[0]=()->{int k=kind.selected();if(k==1||!parts.isEmpty()||cat[0]<0){preview.setVisibility(View.GONE);return;}
            Budget.Category c=fresh(categories.get(cat[0]));long typed;try{typed=Math.max(0,Budget.evaluate(amount.getText().toString()));}catch(Exception e){typed=0;}
            YearMonth m=YearMonth.from(LocalDate.parse((String)day.getTag()));long now=main.budget.available(c,m);
            if(old!=null&&old.category.equals(c.id)&&YearMonth.from(LocalDate.parse(old.date)).equals(m))now-=old.amount; // editing: without its old amount
            long after=now+(k==0?-typed:typed);preview.setVisibility(View.VISIBLE);
            if(typed==0){preview.setTextColor(main.muted);preview.setText(tint(c.name+" has "+money(now)+" available",money(now),amountColour(now)));}
            else if(after>=0){preview.setText(c.name+" will have "+money(after)+" left");preview.setTextColor(amountColour(after));}
            else{preview.setText(c.name+" will be overspent by "+money(-after));preview.setTextColor(main.red);}};
        // Reminders, as Planner's: from 9:00 on the date (an upcoming one's next date, so they move on with it). None until added.
        ReminderEditor reminders=new ReminderEditor(main,old!=null?old.reminders:sched!=null?sched.reminders:new ArrayList<>(),()->(String)day.getTag());
        reminders.addTo(more);onText(day,reminders::show);
        // A photo (e.g. a receipt), kept on this phone. Picking one leaves this form open (see onRestart).
        // Bug hunt 27 (M5): a photo that came back after Android closed MyBudget (and its form) starts a new transaction.
        String[] photo={old!=null?old.photo:sched==null&&main.recoveredPhoto!=null?main.recoveredPhoto:""};if(old==null&&sched==null)main.recoveredPhoto=null;LinearLayout photoBox=column();if(sched==null)more.addView(photoBox);
        Runnable[] showPhotoBox=new Runnable[1];
        showPhotoBox[0]=()->{photoBox.removeAllViews();
            // Take a photo (the camera app) or choose one from the gallery; either is copied into photos at most 1600 px.
            if(photo[0].isEmpty()){java.util.function.IntConsumer pick=n->{main.photoTarget=u->{try{photo[0]=main.copyPhoto(u);}catch(Exception e){toast("Could not add that photo.");}
                    showPhotoBox[0].run();};main.pickingPhoto=true;
                main.photoForm=main.editors.isEmpty()?null:main.editors.get(main.editors.size()-1); // this form: the newest open one (the sheet has left the list)
                if(!(n==0?main.takePhoto():main.choosePhoto())){main.pickingPhoto=false;main.photoTarget=null;main.photoForm=null;}};
                photoBox.addView(button("+ Add a photo",()->MoveMenu.sheet(this,"Add a photo","Kept on this phone only, not in backups",
                    Collections.singletonList(Arrays.asList(new MoveMenu.Choice(MoveMenu.CAMERA,"Take a photo",false,()->pick.accept(0)),
                        new MoveMenu.Choice(MoveMenu.GALLERY,"Choose from gallery",false,()->pick.accept(1)))),null)));}
            else{android.graphics.Bitmap bm=main.photoBitmap(photo[0],480);
                if(bm==null)photoBox.addView(label("The photo isn't on this phone.",13,main.muted,false));else{ImageView img=new ImageView(main);
                    img.setImageBitmap(bm);img.setAdjustViewBounds(true);img.setScaleType(ImageView.ScaleType.FIT_START);
                    img.setContentDescription("Photo of this transaction. Double tap to view it larger.");
                    img.setOnClickListener(v->viewPhoto(photo[0]));photoBox.addView(img,new LinearLayout.LayoutParams(-1,dp(160)));}
                photoBox.addView(button("Remove photo",()->{photo[0]="";showPhotoBox[0].run();}));}};
        showPhotoBox[0].run();
        // Flag: a row of coloured dots, the chosen one ringed with a ✓ and named under them; tapping it again takes the flag off.
        int[] flag={old!=null?old.flag:0};LinearLayout flagBox=column();if(sched==null)more.addView(flagBox); // upcoming transactions have no flag
        flagBox.addView(label("Flag",12,main.muted,true));Flow dots=new Flow(main,0);flagBox.addView(dots,new LinearLayout.LayoutParams(-1,-2)); // wraps onto a second line when narrow
        TextView flagName=label("",13,main.muted,false);flagBox.addView(flagName);
        Runnable[] showFlags={null};showFlags[0]=()->{dots.removeAllViews();String[] names=flagChoices();
            for(int i=1;i<names.length;i++){int n=i;boolean on=flag[0]==i;TextView dot=new TextView(main);dot.setGravity(Gravity.CENTER);dot.setTextSize(14);
                FrameLayout target=new FrameLayout(main);target.addView(dot,new FrameLayout.LayoutParams(dp(36),dp(36),Gravity.CENTER)); // a 36dp dot in a 48dp target
                android.graphics.drawable.GradientDrawable d=new android.graphics.drawable.GradientDrawable();d.setShape(android.graphics.drawable.GradientDrawable.OVAL);
                d.setColor(FLAG_COLORS[i]);if(on){dot.setText("✓");dot.setTextColor(i==3?Color.BLACK:Color.WHITE);}
                if(on)d.setStroke(dp(3),main.ink);dot.setBackground(d);dot.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
                target.setContentDescription(names[i]+(on?", selected. Double tap to take the flag off":" flag"));target.setOnClickListener(v->{tick(v);flag[0]=flag[0]==n?0:n;showFlags[0].run();});
                target.setMinimumWidth(dp(48));target.setMinimumHeight(dp(48));dots.addView(target,new android.view.ViewGroup.LayoutParams(dp(48),dp(48)));}
            flagName.setText(flag[0]==0?"No flag":names[flag[0]]+" · tap it again to take it off");};
        showFlags[0].run();
        CheckBox cleared=new CheckBox(main);cleared.setText("Cleared at the bank");cleared.setTextColor(main.ink);
        cleared.setMinHeight(dp(48));more.addView(cleared);
        // Editing: Delete (and Someone owes part of this) at the very end, apart from the rest.
        LinearLayout endBox=column();View rule=new View(main);rule.setBackgroundColor(tint(main.ink,main.darkTheme?26:18));
        LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,dp(1));rp.setMargins(0,dp(20),0,dp(8));endBox.addView(rule,rp);
        java.util.function.Function<Button,Button> red=b->{b.setTextColor(main.red);return b;};
        if(sched!=null){cleared.setVisibility(View.GONE);payee.setText(sched.payee,false);amount.setText(decimal(Math.abs(sched.amount)));
            memo.setText(sched.memo,false);endBox.addView(red.apply(button("Delete upcoming transaction",()->deleteScheduled(sched.id))));f.addView(endBox);}
        if(old!=null){payee.setText(old.payee,false);amount.setText(decimal(Math.abs(old.amount)));memo.setText(old.memo,false);
            cleared.setChecked(old.cleared);
            // Money owed between people (Budget.Owed): part of this paid for someone else.
            if(old.amount<0&&!old.transfer())endBox.addView(button("Someone owes part of this",()->main.accountsScreen.owedFor(old)));
            endBox.addView(red.apply(button("Delete transaction",()->delete(old))));f.addView(endBox);}
        Spinner repeatShown=repeat;
        boolean used=old!=null&&(!old.photo.isEmpty()||old.flag>0||!old.reminders.isEmpty())||sched!=null&&(!sched.repeat.equals("Never")||!sched.reminders.isEmpty());
        boolean[] open={used};Runnable showMore=()->{more.setVisibility(open[0]?View.VISIBLE:View.GONE);
            List<String> set=new ArrayList<>();if(repeatShown!=null&&repeatShown.getSelectedItemPosition()>0)set.add(REPEAT_LABELS[repeatShown.getSelectedItemPosition()]);
            if(later[0])set.add("Pay later");if(!reminders.list.isEmpty())set.add(count(reminders.list.size(),"reminder","reminders"));
            if(!photo[0].isEmpty())set.add("Photo");if(flag[0]>0&&sched==null)set.add("Flag: "+flagChoices()[flag[0]]);if(cleared.isChecked()&&sched==null)set.add("Cleared");
            String inside=(old==null?"Repeat, ":"")+(adding?"pay later, ":"")+"reminders"+(sched==null?", photo, flag, cleared":"");
            inside=Character.toUpperCase(inside.charAt(0))+inside.substring(1);
            moreSummary.setVisibility(open[0]?View.GONE:View.VISIBLE);moreSummary.setTextColor(set.isEmpty()?main.muted:main.ink);
            moreSummary.setText(set.isEmpty()?inside:String.join(" · ",set));
            moreButton.setText((open[0]?"▾  ":"▸  ")+"More options");
            moreButton.setContentDescription("More options: "+(set.isEmpty()?inside:String.join(", ",set))+(open[0]?", shown":", hidden"));};
        moreButton.setOnClickListener(v->{open[0]=!open[0];tick(v);showMore.run();});showMore.run();
        showSplit.run();
        Spinner repeatField=repeat;
        Runnable adapt=()->{int selected=kind.selected();boolean income=selected==1;
            categoryFields.setVisibility(income?View.GONE:View.VISIBLE);payee.setHint(income?"Income source":selected==2?"Refund from":"Payee");
            laterBox.setVisibility(selected==0?View.VISIBLE:View.GONE);if(selected!=0&&later[0])laterBox.setChecked(false); // pay later: expenses only
            paint.run();showPreview[0].run();showMore.run();};
        adapt.run();kind.changed=adapt;
        amount.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int b,int c){}public void onTextChanged(CharSequence s,int a,int b,int c){}
            public void afterTextChanged(android.text.Editable s){
                if(s.length()>0&&(s.charAt(0)=='-'||s.charAt(0)=='−')){kind.picked=true;kind.set(0);s.delete(0,1);return;} // the change re-runs this
                if(s.length()>0&&s.charAt(0)=='+'){kind.picked=true;if(kind.selected()==0)kind.set(1);s.delete(0,1);return;}
                if(s.toString().trim().length()>0)lastTime.setVisibility(View.GONE);showPreview[0].run();showLater[0].run();}});
        long[] refund={0};String[] refundPlan={""}; // a refund just saved on a pay-later plan (see saved)
        Runnable save=()->{int k=kind.selected();
            if(k!=1&&categories.isEmpty())throw new IllegalArgumentException("Add a category first.");
            boolean isSplit=k!=1&&!parts.isEmpty();
            boolean bare=old!=null&&old.amount<0&&old.category.isEmpty()&&!old.transfer()&&k==0&&!isSplit&&cat[0]<0; // hunt 26 C1: an outflow with no category (a reconcile adjustment) keeps none
            if(k!=1&&!isSplit&&cat[0]<0&&!bare)throw new IllegalArgumentException("Choose a category.");
            if(old!=null&&main.budget.entries.stream().noneMatch(t->t.id.equals(old.id)))throw new IllegalArgumentException("This transaction was removed meanwhile."); // the data may have been read in again since the form opened
            String p=required(payee),cat2=k==1||bare?"":isSplit?Budget.SPLIT:categories.get(cat[0]).id,acc2=accounts.get(acc[0]).id,memoText=memo.getText().toString().trim();
            long cents=Budget.cents(amount.getText().toString())*(k==0?-1:1);
            String rep=repeatField==null?"Never":Budget.Scheduled.REPEATS[repeatField.getSelectedItemPosition()];
            LocalDate when=LocalDate.parse((String)day.getTag());refund[0]=0;
            if(later[0]&&adding){ // bought on a pay-later plan: the purchase, the payments made now, and the rest as upcoming transfers
                if(k!=0)throw new IllegalArgumentException("Pay later is for an expense.");
                if(when.isAfter(LocalDate.now())||!rep.equals("Never"))throw new IllegalArgumentException("A pay-later purchase is dated today or earlier and doesn't repeat.");
                Budget.Account plan=main.budget.account(planId[0]);if(plan==null||!plan.payLater||plan.closed)throw new IllegalArgumentException("Choose a pay-later plan, or add one with + New plan.");
                int n;try{n=Integer.parseInt(payments.getText().toString().trim());}catch(NumberFormatException x){throw new IllegalArgumentException("Enter the number of payments.");}
                Budget.Entry e=new Budget.Entry(p,cat2,plan.id,when.toString(),cents);e.memo=memoText;e.photo=photo[0];e.reminders.addAll(reminders.assignIds(main.budget));
                if(isSplit)for(Budget.Split part:parts){Budget.Split s=new Budget.Split(part.category,-part.amount);s.memo=part.memo;e.splits.add(s);}
                e.cleared=cleared.isChecked();e.flag=flag[0];
                for(Budget.Scheduled s:main.budget.payLater(e,acc2,n,every[0],firstNow[0]?when:Budget.instalmentDate(when,every[0],1)))
                    if(remindEach[0])s.reminders.add(new Budget.Reminder(main.budget.newReminderId(),1,"days")); // the day before, at 9:00
                return;}
            if(old==null&&(sched!=null||when.isAfter(LocalDate.now())||!rep.equals("Never"))){
                Budget.Scheduled s=new Budget.Scheduled(p,cat2,acc2,when.toString(),cents,rep);s.memo=memoText;s.reminders.addAll(reminders.assignIds(main.budget));
                if(isSplit)for(Budget.Split part:parts){Budget.Split c=new Budget.Split(part.category,part.amount*(k==0?-1:1));c.memo=part.memo;s.splits.add(c);} // entered later with the same parts
                if(sched!=null){s.id=sched.id;s.billKey=sched.billKey;
                    if(when.toString().equals(sched.next))s.day=sched.day;} // Hunt 23 M1: rent on the 31st, shown as 30 Nov, stays the 31st
                if(sched==null&&!when.isAfter(LocalDate.now())){main.budget.validate(s);
                    Budget.Entry entered=main.budget.enter(s,photo[0],cleared.isChecked());entered.flag=flag[0];
                    if(rep.equals("Never")){entered.reminders.addAll(s.reminders);s.reminders.clear();} // a one-off: its reminders go with it; a repeat's stay on the repeat
                    if(!rep.equals("Never"))main.budget.scheduled.add(s);
                    return;} // today or earlier: entered now (with its photo and Cleared tick), the repeat continues
                main.budget.validate(s);main.budget.scheduled.removeIf(t->t.id.equals(s.id));main.budget.scheduled.add(s);
                if(!photo[0].isEmpty()||flag[0]>0)toast("Saved as upcoming: add the photo and flag when it's entered."); // hunt 26 C3: upcoming ones keep neither
                return;
            }
            Budget.Entry e=new Budget.Entry(p,cat2,acc2,date(day),cents);e.memo=memoText;e.photo=photo[0];e.reminders.addAll(reminders.assignIds(main.budget));
            if(isSplit)for(Budget.Split part:parts){Budget.Split s=new Budget.Split(part.category,part.amount*(k==0?-1:1));s.memo=part.memo;
                e.splits.add(s);}e.cleared=cleared.isChecked();e.flag=flag[0];main.budget.validate(e);
            if(old!=null){e.id=old.id;e.externalId=old.externalId;e.billKey=old.billKey;e.bankPayee=Budget.statementPayee(old);e.bankDate=old.bankDate;e.bankAmount=old.bankAmount;e.reconciled=old.reconciled&&e.cleared&&e.account.equals(old.account); // unticking Cleared, or another account (hunt 24 C6), unlocks it
                if(!put(old.id,e))main.budget.entries.add(0,e);}else main.budget.entries.add(0,e);
            Budget.Account onPlan=main.budget.account(acc2); // a new refund on a pay-later plan with payments left: asked about after the save
            if(adding&&k==2&&onPlan!=null&&onPlan.payLater&&!main.budget.instalmentsLeft(onPlan).isEmpty()){refund[0]=cents;refundPlan[0]=onPlan.id;}};
        // After a save: which account to start on next time, and a short "Saved" line.
        Runnable saved=()->{if(adding)main.getSharedPreferences("appearance",0).edit().putString("last_account",accounts.get(acc[0]).id).apply();
            long size;try{size=Budget.cents(amount.getText().toString());}catch(Exception e){size=0;}
            Toast.makeText(main,"Saved: "+payee.getText().toString().trim()+" "+(kind.selected()==0?"−":"+")+money(size),Toast.LENGTH_SHORT).show();
            if(refund[0]>0){long back=refund[0];refund[0]=0;lowerAfterRefund(refundPlan[0],back);}};
        // Save and add another (new transactions only): the kind, date and account stay; everything else starts afresh.
        Runnable again=!adding?null:()->{payee.setText("",false);amount.setText("");memo.setText("",false);photo[0]="";showPhotoBox[0].run();reminders.list.clear();reminders.show();
            parts.clear();showSplit.run();flag[0]=0;showFlags[0].run();cleared.setChecked(false);if(repeatField!=null)repeatField.setSelection(0);
            cat[0]=-1;categoryChosen[0]=false;showCategories[0].run();lastTime.setVisibility(View.GONE);showPreview[0].run();showMore.run();amount.requestFocus();};
        sheet(sched!=null?"Edit upcoming transaction":old==null?"Add transaction":"Edit transaction",f,save,saved,again,adding?amount:null);
    }
    /** Edits [parts] (category + positive amount per row); at least two parts. Remove split empties them. [sign] -1: an expense, so its parts show red as typed. */
    private void editSplit(List<Budget.Split> parts,List<Budget.Category> categories,Budget.Category first,long total,Runnable done,int sign){
        if(categories.isEmpty()){toast("Add a category first.");return;}
        String[] names=new String[categories.size()+1];for(int i=0;i<categories.size();i++)names[i]=categories.get(i).name;
        names[categories.size()]="To budget";
        LinearLayout f=form(),rows=column();f.addView(label("Each part comes out of its own category.",13,main.muted,false));f.addView(rows);
        TextView sum=label("",14,main.blue,true);
        List<Spinner> cats=new ArrayList<>();List<EditText> amounts=new ArrayList<>(),memos=new ArrayList<>();
        EditText[] current={null}; // the part last typed in (Fill remaining fills it)
        Runnable total2=()->{long n=0;
            for(EditText a:amounts){try{n+=Budget.parse(a.getText().toString().isEmpty()?"0":a.getText().toString());}catch(Exception e){}}
            sum.setText("Total "+money(n));};
        // Each part: its category, amount and remove button, with its own note underneath (optional; the CSV uses it for that part's row).
        interface Row{void add(String category,Long cents,String note);}
        Row addRow=(category,cents,note)->{LinearLayout part=column(),row=new LinearLayout(main);part.addView(row);
            row.setGravity(Gravity.CENTER_VERTICAL);Spinner s=new Spinner(main);
            Ui.dropdown(s,names);
            int i=category==null?0:category.isEmpty()?categories.size():Math.max(0,categories.indexOf(main.budget.category(category)));
            s.setSelection(i);row.addView(s,new LinearLayout.LayoutParams(0,-2,1));
            EditText a=new EditText(main);a.setHint("0.00");a.setTextColor(main.ink);a.setSingleLine(true);a.setInputType(AMOUNT_INPUT);sumsHint(a,"Amount of this part");s.setContentDescription("Category of this part");
            if(cents!=null&&cents>0)a.setText(decimal(cents));signColours(a,sign);onText(a,total2);a.setOnFocusChangeListener((v,has)->{if(has)current[0]=a;});
            row.addView(a,new LinearLayout.LayoutParams(dp(110),-2));
            Button x=button("✕",()->{});x.setContentDescription("Remove this part");x.setBackground(bg(Color.TRANSPARENT));
            EditText m=new EditText(main);m.setHint("Note for this part (optional)");m.setTextColor(main.ink);m.setSingleLine(true);m.setTextSize(14);
            m.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);if(note!=null)m.setText(note);part.addView(m,new LinearLayout.LayoutParams(-1,-2));
            x.setOnClickListener(v->{rows.removeView(part);cats.remove(s);amounts.remove(a);memos.remove(m);if(current[0]==a)current[0]=null;total2.run();});
            row.addView(x,new LinearLayout.LayoutParams(dp(48),dp(48)));
            rows.addView(part);cats.add(s);amounts.add(a);memos.add(m);};
        if(parts.isEmpty()){addRow.add(first==null?null:first.id,total,null);
            addRow.add(null,null,null);}else for(Budget.Split p:parts)addRow.add(p.category,p.amount,p.memo);
        f.addView(button("+ Add a part",()->{addRow.add(null,null,null);total2.run();}));
        // Helpers over the transaction's amount: Split evenly (leftover cents on the first parts); Fill remaining puts what's left into the part last typed in, else the last empty one.
        LinearLayout helpers=new LinearLayout(main);Button even=button("Split evenly",()->{if(amounts.isEmpty())return;
            if(total<=0){toast("Enter the transaction's amount first.");return;}long[] shares=Budget.splitEvenly(total,amounts.size());
            for(int i=0;i<amounts.size();i++)amounts.get(i).setText(decimal(shares[i]));});
        Button fill=button("Fill remaining",()->{if(amounts.isEmpty())return;if(total<=0){toast("Enter the transaction's amount first.");return;}
            EditText into=amounts.contains(current[0])?current[0]:null;
            if(into==null)for(EditText a:amounts)if(a.getText().toString().trim().isEmpty())into=a;if(into==null)into=amounts.get(amounts.size()-1);
            long[] others=new long[amounts.size()-1];int k=0;for(EditText a:amounts){if(a==into)continue;String v=a.getText().toString().trim();
                try{others[k++]=v.isEmpty()?0:Budget.parse(v);}catch(Exception e){toast(e.getMessage());return;}}
            long left=Budget.remaining(total,others);if(left<=0){toast("Nothing is left of the "+money(total)+" total.");return;}
            into.setText(decimal(left));});
        helpers.addView(even,new LinearLayout.LayoutParams(0,-2,1));LinearLayout.LayoutParams fp=new LinearLayout.LayoutParams(0,-2,1);
        fp.setMargins(dp(8),0,0,0);helpers.addView(fill,fp);f.addView(helpers);
        f.addView(sum);total2.run();AlertDialog[] shown={null};
        // In the dialog, not as a third button: beside Cancel and Done the three stacked (hunt 28).
        if(!parts.isEmpty())f.addView(button("Remove split",()->{parts.clear();done.run();shown[0].dismiss();}));
        ScrollView scroll=new ScrollView(main);scroll.addView(f);AlertDialog d=new AlertDialog.Builder(main).setTitle("Split").setView(scroll)
            .setNegativeButton("Cancel",null).setPositiveButton("Done",null)
            .create();shown[0]=d;
        d.setOnShowListener(v->d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(w->{
            List<Budget.Split> result=new ArrayList<>();
            for(int i=0;i<cats.size();i++){long cents;
                try{cents=Budget.cents(amounts.get(i).getText().toString());}catch(Exception e){toast("Give every part an amount above zero.");return;}
                int c=cats.get(i).getSelectedItemPosition();Budget.Split part=new Budget.Split(c==categories.size()?"":categories.get(c).id,cents);
                part.memo=memos.get(i).getText().toString().trim();result.add(part);}
            if(result.size()<2){toast("A split needs at least two parts. Use Remove split for one category.");return;}
            parts.clear();parts.addAll(result);done.run();d.dismiss();}));d.show();
    }
    private Budget.Scheduled scheduledById(String id){for(Budget.Scheduled s:main.budget.scheduled)if(s.id.equals(id))return s;
        throw new IllegalArgumentException("That upcoming transaction no longer exists.");}
    private void deleteScheduled(String id){Ui.danger(new AlertDialog.Builder(main).setTitle("Delete upcoming transaction?")
            .setMessage("It and its repeats are removed. Transactions already entered stay.").setNegativeButton("Cancel",null)
            .setPositiveButton("Delete",(d,w)->{if(main.deleteWithUndo("Upcoming transaction deleted",()->main.budget.scheduled.remove(scheduledById(id))))for(AlertDialog editor:new ArrayList<>(main.editors))editor.dismiss();}).show());}
    /** A due upcoming transaction: enter it (it becomes money), skip this date, or edit it. */
    void dueActions(String id){
        Budget.Scheduled s;try{s=scheduledById(id);}catch(Exception e){return;}
        boolean isDue=!LocalDate.parse(s.next).isAfter(LocalDate.now());
        List<MoveMenu.Choice> choices=new ArrayList<>();
        if(isDue){choices.add(new MoveMenu.Choice(MoveMenu.ENTER,"Enter it now",false,
                ()->{if(main.change(()->main.budget.enter(scheduledById(id))))toast("Entered "+(s.transfer()&&!s.memo.isEmpty()?s.memo:s.payee)+".");}));
            Runnable skip=()->main.change(()->main.budget.advance(scheduledById(id)));
            choices.add(s.repeat.equals("Never")?MoveMenu.Choice.danger(MoveMenu.DELETE,"Skip it (delete)",skip):new MoveMenu.Choice(MoveMenu.SKIP,"Skip this one",false,skip));}
        choices.add(new MoveMenu.Choice(MoveMenu.EDIT,"Edit",false,()->{if(s.transfer())editInstalment(id);else transaction(null,s);}));
        MoveMenu.sheet(this,s.transfer()&&!s.memo.isEmpty()?s.memo:s.payee,money(s.amount)+" · "+(isDue?"due ":"next ")+pretty(s.next)+(s.repeat.equals("Never")?"":" · "+s.repeat.toLowerCase(Locale.ROOT)),
            Collections.singletonList(choices),null);
    }
    static final String[] PLAN_NAMES={"PayPal Pay in 4","Afterpay","Zip","Klarna","Humm"};
    /** A new pay-later plan (Budget.addPayLater): its name, picked from the usual ones or typed; [made] gets the name once saved. */
    void newPlan(java.util.function.Consumer<String> made){LinearLayout f=form();
        f.addView(label("A plan is like a small credit card: what you buy on it is spent from its category straight away, and the payments come out of your account over the next weeks.",13,main.muted,false));
        EditText name=field(f,"Name",false);name.setText(PLAN_NAMES[0]);Chips picks=chips(f,true);
        java.util.function.IntConsumer[] pick={null};pick[0]=n->{name.setText(PLAN_NAMES[n]);name.setSelection(name.length());picks.show(Arrays.asList(PLAN_NAMES),n,pick[0]);};
        picks.show(Arrays.asList(PLAN_NAMES),0,pick[0]);
        String[] saved={null};
        dialog("New pay-later plan",f,()->{String n=required(name);
            for(Budget.Account a:main.budget.accounts)if(a.name.equalsIgnoreCase(n))throw new IllegalArgumentException("An account already has that name.");
            for(Budget.Category c:main.budget.categories)if(c.name.equalsIgnoreCase(n))throw new IllegalArgumentException("A category already has that name. Choose another name for the plan.");
            main.budget.addPayLater(n,LocalDate.now().toString());saved[0]=n;},()->{if(saved[0]!=null)made.accept(saved[0]);});}
    /** After a refund on pay-later plan [planId]: asks whether to lower the payments still to come by [cents], the last ones first. */
    private void lowerAfterRefund(String planId,long cents){lowerLeft(planId,cents,"refund");}
    /** Asks whether to lower plan [planId]'s payments still to come by [cents] ([what]: the refund or payment it comes from). */
    private void lowerLeft(String planId,long cents,String what){Budget.Account plan=main.budget.account(planId);if(plan==null)return;
        List<Budget.Scheduled> left=main.budget.instalmentsLeft(plan);if(left.isEmpty())return;long owed=0;for(Budget.Scheduled s:left)owed-=s.amount;
        long by=Math.min(cents,owed);
        tracked(new AlertDialog.Builder(main).setTitle("Lower the payments left?")
            .setMessage(plan.name+" has "+count(left.size(),"payment","payments")+" left ("+money(owed)+"). Lower them by the "+what+", "+money(by)+"? The last payments are lowered first, as pay-later plans usually do"+(by>=owed?": that's all of them.":"."))
            .setNegativeButton("Not now",null).setPositiveButton("Lower them",(d,w)->{if(main.change(()->main.budget.lowerInstalments(main.accountById(planId),by)))toast("Payments lowered by "+money(by)+".");}));}
    /** An upcoming transfer (a pay-later payment): its amount, date and reminders, or delete it. */
    private void editInstalment(String id){Budget.Scheduled s;try{s=scheduledById(id);}catch(Exception e){toast(e.getMessage());return;}
        Budget.Account from=main.budget.account(s.account),to=main.budget.account(s.destination);LinearLayout f=form();
        f.addView(label((from==null?"":"From "+from.name)+(to==null?"":" to "+to.name)+(s.memo.isEmpty()?"":"\n"+s.memo),14,main.ink,true));
        EditText amount=field(f,"Amount ("+code()+")",true);amount.setText(decimal(-s.amount));signColours(amount,-1);
        f.addView(label("Date",12,main.muted,true));EditText day=dateField(f,s.next,true);
        ReminderEditor reminders=new ReminderEditor(main,s.reminders,()->(String)day.getTag());reminders.addTo(f);onText(day,reminders::show);
        f.addView(button("Delete this payment",()->deleteScheduled(id)));
        dialog("Edit payment",f,()->{Budget.Scheduled t=scheduledById(id);long cents=Budget.cents(amount.getText().toString());String d=(String)day.getTag(); // a future date (date() allows only up to today)
            t.amount=-cents;t.next=d;t.day=LocalDate.parse(d).getDayOfMonth();t.reminders.clear();t.reminders.addAll(reminders.assignIds(main.budget));main.budget.validate(t);});}
    String repeatLabel(Budget.Scheduled s){int i=Arrays.asList(Budget.Scheduled.REPEATS).indexOf(s.repeat);return i<=0?"Once":REPEAT_LABELS[i];}
    private void delete(Budget.Entry e){Ui.danger(new AlertDialog.Builder(main).setTitle("Delete transaction?")
            .setMessage("Account and category balances will be recalculated.").setNegativeButton("Cancel",null)
            .setPositiveButton("Delete",(d,w)->{if(main.deleteWithUndo(e.transfer()?"Transfer deleted":"Transaction deleted",()->{if(!main.budget.entries.removeIf(t->t.id.equals(e.id)))throw new IllegalArgumentException("That transaction no longer exists.");}))
                for(AlertDialog editor:new ArrayList<>(main.editors))editor.dismiss();}).show());}
    /** A reconciled transaction is part of a balance checked against the bank: ask before opening it. */
    private void reconciledWarning(Budget.Entry e,Runnable open){Budget.Account a=main.budget.account(e.account);String when=a==null||a.reconciled.isEmpty()?"":" on "+pretty(a.reconciled);
        AlertDialog warning=new AlertDialog.Builder(main).setTitle("This transaction is reconciled")
            .setMessage("It's part of the balance you checked against your bank"+when+". Changing its amount, date or account, or deleting it, changes that balance. Untick Cleared to unlock it for good.")
            .setNegativeButton("Cancel",null).setPositiveButton("Change it",(d,w)->open.run()).create();
        main.editors.add(warning);warning.setOnDismissListener(v->main.editors.remove(warning));warning.show();} // Hunt 24 C1: a reload closes it with the forms
    /**
     * Interest or a fee on card [cardId] (or its refund), in the card's payment category: more debt, nothing overspent (Budget.cardCharge).
     * [old]: one to edit, or a card transaction to turn into one (it keeps its id, Planner link and statement text).
     */
    void cardCharge(String cardId,Budget.Entry old){Budget.Account card=main.budget.account(cardId);Budget.Category pc=card==null?null:main.budget.paymentCategory(card);
        if(pc==null){toast("Only a credit card has interest and fees.");return;}
        if(card.closed&&old==null){toast(card.name+" is closed. Reopen it in Accounts first.");return;} // hunt 24 C7: a closed card's debt would be out of sight; hunt 25 C3: one already there still opens
        LinearLayout f=form();f.addView(label("Interest, an annual fee or a late fee on "+card.name+". It adds to what you owe, like the debt the card started with: no category pays for it and nothing is overspent. Assign money to "+pc.name+" (a payoff target helps) to pay it off.",13,main.muted,false));
        Choice kind=choice(f,new String[]{"− Interest or fee","↩ Refunded"},new int[]{main.red,main.green},old!=null&&old.amount>0?1:0);
        EditText amount=field(f,"Amount ("+code()+")",true);if(old!=null)amount.setText(decimal(Math.abs(old.amount)));
        AutoCompleteTextView payee=suggestField(f,"What it is",()->Arrays.asList("Interest","Annual fee","Late fee","Cash advance fee","Foreign transaction fee"));payee.setText(old!=null?old.payee:"Interest",false);
        EditText day=dateField(f,old!=null?old.date:LocalDate.now().toString());EditText memo=field(f,"Note (optional)",false);if(old!=null)memo.setText(old.memo);
        CheckBox cleared=new CheckBox(main);cleared.setText("Cleared");cleared.setMinHeight(dp(48));cleared.setChecked(old!=null&&old.cleared);f.addView(cleared);
        if(old!=null)f.addView(button("Delete",()->delete(old)));
        dialog(old==null?"Interest or fee on "+card.name:"Interest or fee",f,()->{
            if(old!=null&&main.budget.entries.stream().noneMatch(t->t.id.equals(old.id)))throw new IllegalArgumentException("This transaction was removed meanwhile.");
            Budget.Entry e=new Budget.Entry(required(payee),pc.id,card.id,date(day),Budget.cents(amount.getText().toString())*(kind.selected()==0?-1:1));
            e.memo=memo.getText().toString().trim();e.cleared=cleared.isChecked();
            if(old!=null){e.id=old.id;e.externalId=old.externalId;e.billKey=old.billKey;e.bankPayee=Budget.statementPayee(old);e.bankDate=old.bankDate;e.bankAmount=old.bankAmount;e.flag=old.flag;e.photo=old.photo;e.reconciled=old.reconciled&&e.cleared;e.reminders.addAll(old.reminders);}
            main.budget.validate(e);if(old==null||!put(old.id,e))main.budget.entries.add(0,e);});}
    void transfer(){editTransfer(null,null,0);}
    /** A card payment: a transfer from a cash account to the card, for what's set aside (or what's owed, if less). */
    void payCard(String id){Budget.Account card=main.budget.account(id);if(card==null)return;
        // Bug hunt 27 (M3): a pay-later plan with payments to come pays the next one (entered now, so it isn't paid again
        // when its date comes), not everything owed beside them.
        if(card.payLater){List<Budget.Scheduled> left=main.budget.instalmentsLeft(card);if(!left.isEmpty()){payNextInstalment(card,left.get(0));return;}}
        editTransfer(null,id,main.budget.toPay(card));} // what's set aside now, whatever month is on screen
    private void payNextInstalment(Budget.Account plan,Budget.Scheduled next){String id=next.id,planId=plan.id;
        Budget.Account from=main.budget.account(next.account);
        tracked(new AlertDialog.Builder(main).setTitle("Pay "+plan.name)
            .setMessage("Pay the next payment, "+money(-next.amount)+(from==null?"":" from "+from.name)+", today? It comes off the payments to come. To pay a different amount, choose Another amount: what you pay can then be taken off the payments left.")
            .setNegativeButton("Cancel",null)
            .setNeutralButton("Another amount",(d,w)->editTransfer(null,planId,-next.amount))
            .setPositiveButton("Pay it now",(d,w)->{if(main.change(()->main.budget.enter(scheduledById(id),"",false,LocalDate.now().toString())))toast("Paid "+money(-next.amount)+".");}));}
    private void editTransfer(Budget.Entry old){editTransfer(old,null,0);}
    private void editTransfer(Budget.Entry old,String toId,long preset){
        List<Budget.Account> accounts=old==null?main.openAccounts():main.openAccounts(main.budget.account(old.account),main.budget.account(old.destination));
        String[] names=accounts.stream().map(a->a.name).toArray(String[]::new);
        if(accounts.size()<2){toast("Add two open accounts first.");return;}LinearLayout f=form();
        int toIndex=toId==null?-1:accounts.indexOf(main.budget.account(toId)),fromIndex=0;
        if(toIndex>=0)for(int i=0;i<accounts.size();i++)if(accounts.get(i).cash()){fromIndex=i;break;}
        Spinner from=spinner(f,"From account",names,old!=null?accounts.indexOf(main.budget.account(old.account)):fromIndex),to=spinner(f,"To account",names,old!=null?accounts.indexOf(main.budget.account(old.destination)):toIndex>=0?toIndex:1);
        // Out of the budget to a tracking account (an extra loan payment, an investment): spending, so it comes from a category. In from one: income to To budget.
        List<Budget.Category> cats=main.visibleCategories(old==null?null:main.budget.category(old.category));LinearLayout categoryBox=column();
        f.addView(categoryBox);
        Spinner category=spinner(categoryBox,"Category it comes from",cats.stream().map(c->c.name+" ("+money(main.budget.available(c,main.month))+")").toArray(String[]::new),old==null?0:cats.indexOf(main.budget.category(old.category)));
        TextView crossing=label("",12,main.muted,false);f.addView(crossing);
        Runnable adapt=()->{
            Budget.Account a=accounts.get(Math.max(0,from.getSelectedItemPosition())),b=accounts.get(Math.max(0,to.getSelectedItemPosition()));
            boolean out=!a.tracking()&&b.tracking(),in=a.tracking()&&!b.tracking();categoryBox.setVisibility(out?View.VISIBLE:View.GONE);
            crossing.setText(out?"It leaves your budget: it's spending from this category.":in?"It comes into your budget: it's income to To budget.":"");
            crossing.setVisibility(out||in?View.VISIBLE:View.GONE);};adapt.run();onPick(from,adapt);onPick(to,adapt);
        EditText amount=field(f,"Amount ("+code()+")",true);if(preset>0)amount.setText(decimal(preset));f.addView(label("Date",12,main.muted,true));
        EditText day=dateField(f,old==null?LocalDate.now().toString():old.date);CheckBox cleared=new CheckBox(main);
        cleared.setText("Cleared in both accounts");f.addView(cleared);if(old!=null){amount.setText(decimal(-old.amount));
            cleared.setChecked(old.cleared);f.addView(button("Delete transfer",()->delete(old)));}
        long[] paidPlan={0};String[] paidPlanId={null};
        dialog(old!=null?"Edit transfer":toIndex>=0?"Pay "+main.budget.account(toId).name:"Transfer money",f,()->{
            if(old!=null&&main.budget.entries.stream().noneMatch(t->t.id.equals(old.id)))throw new IllegalArgumentException("This transaction was removed meanwhile."); // hunt 24 C1
            Budget.Account a=accounts.get(from.getSelectedItemPosition()),b=accounts.get(to.getSelectedItemPosition());
            boolean out=!a.tracking()&&b.tracking();if(out&&cats.isEmpty())throw new IllegalArgumentException("Add a category first.");
            Budget.Entry e=new Budget.Entry("Transfer to "+b.name,out?cats.get(Math.max(0,category.getSelectedItemPosition())).id:"",a.id,date(day),-Budget.cents(amount.getText().toString()));
            e.destination=b.id;e.cleared=cleared.isChecked();if(old!=null){e.memo=old.memo;e.reminders.addAll(old.reminders);e.flag=old.flag;e.bankPayee=Budget.statementPayee(old);e.bankDate=old.bankDate;e.bankAmount=old.bankAmount;e.reconciled=old.reconciled&&e.cleared&&e.account.equals(old.account)&&e.destination.equals(old.destination);}
            main.budget.validate(e);if(old!=null)e.id=old.id;if(old==null||!put(old.id,e))main.budget.entries.add(0,e);
            // Bug hunt 27 (M3): a new payment into a pay-later plan with payments to come: offered off those payments after.
            if(old==null&&b.payLater&&!main.budget.instalmentsLeft(b).isEmpty()){paidPlan[0]=-e.amount;paidPlanId[0]=b.id;}},
            ()->{if(paidPlan[0]>0){long paid=paidPlan[0];paidPlan[0]=0;lowerLeft(paidPlanId[0],paid,"payment");}});

    }
    /** Edits a tracking account's transaction: no category (it's off budget). */
    private void trackingEntry(String accountId,Budget.Entry old){
        Budget.Account a=main.budget.account(accountId);if(a==null)return;LinearLayout f=form();
        f.addView(label(a.name+" is a tracking account: off budget, no category. A positive amount raises its balance"+(a.liability?" (less owed)":"")+"; a negative one lowers it.",13,main.muted,false));
        AutoCompleteTextView payee=suggestField(f,"Payee or description",()->main.budget.payees());
        EditText amount=field(f,"Amount ("+code()+", + or -)",true);f.addView(label("Date",12,main.muted,true));EditText day=dateField(f,old.date);
        EditText memo=field(f,"Note (optional)",false);
        CheckBox cleared=new CheckBox(main);cleared.setText("Cleared");f.addView(cleared);Spinner flag=spinner(f,"Flag",flagChoices(),old.flag);
        payee.setText(old.payee,false);amount.setText(decimal(old.amount));memo.setText(old.memo);cleared.setChecked(old.cleared);
        f.addView(button("Delete transaction",()->delete(old)));
        dialog("Edit transaction",f,()->{Budget.Entry e=new Budget.Entry(required(payee),"",accountId,date(day),Budget.parse(amount.getText().toString()));
            e.id=old.id;e.memo=memo.getText().toString().trim();e.cleared=cleared.isChecked();e.flag=flag.getSelectedItemPosition();e.photo=old.photo;e.reconciled=old.reconciled&&e.cleared;
            e.bankPayee=Budget.statementPayee(old);e.bankDate=old.bankDate;e.bankAmount=old.bankAmount;e.reminders.addAll(old.reminders);main.budget.validate(e); // its reminders stay (set in the main form)
            if(!put(old.id,e))throw new IllegalArgumentException("This transaction was removed meanwhile.");});
    }
}
