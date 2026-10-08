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
        // 1. The amount: large, signed and coloured by where the money goes (− red out, + green in), with the kind under it.
        // A - or + typed first picks Expense or Income and is taken off, so the box always holds the size of the amount.
        LinearLayout amountRow=new LinearLayout(main);amountRow.setGravity(Gravity.CENTER_VERTICAL);amountRow.setPadding(0,dp(8),0,0);
        TextView sign=label("−",36,main.red,true);sign.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);sign.setPadding(0,0,dp(6),0);
        amountRow.addView(sign);EditText amount=new EditText(main);amount.setHint("0.00");amount.setSingleLine(true);amount.setInputType(AMOUNT_INPUT);
        amount.setTextSize(36);amount.setTypeface(null,android.graphics.Typeface.BOLD);amount.setFontFeatureSettings("tnum");sumsHint(amount,"Amount ("+code()+")");
        amountRow.addView(amount,new LinearLayout.LayoutParams(0,-2,1));TextView currency=label(code(),15,main.muted,true);
        currency.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);amountRow.addView(currency);f.addView(amountRow);
        Choice kind=choice(f,new String[]{"− Expense","+ Income","↩ Refund"},new int[]{main.red,main.green,main.green},oldAmount<0?0:keepCategory.isEmpty()?1:2);
        TextView guidance=label("",12,main.muted,false);f.addView(guidance);
        Runnable paint=()->{boolean out=kind.selected()==0;int c=out?main.red:main.green;sign.setText(out?"−":"+");sign.setTextColor(c);amount.setTextColor(c);};
        // 2. Who and what. Payees used before are suggested; picking one on a new transaction fills in the category it had last
        // time and offers last time's amount.
        section(f,"Who and what");
        AutoCompleteTextView payee=suggestField(f,"Payee",()->main.budget.payees());
        TextView lastTime=label("",13,main.blue,true);lastTime.setVisibility(View.GONE);lastTime.setMinHeight(dp(48));lastTime.setGravity(Gravity.CENTER_VERTICAL);pressable(lastTime);f.addView(lastTime);
        LinearLayout categoryFields=column();f.addView(categoryFields);
        // Category: the 6 used most in the last 120 days as chips, then "All categories…" for the rest, wrapped onto more lines so
        // all of them are in sight. A category not among the 6 (picked from the list, or the one being edited) takes the 6th place.
        int[] cat={keepCategory.isEmpty()?-1:categories.indexOf(main.budget.category(keepCategory))};
        Map<String,Integer> uses=new HashMap<>();String since=LocalDate.now().minusDays(120).toString();
        for(Budget.Entry e:main.budget.entries)if(e.date.compareTo(since)>=0&&!e.category.isEmpty())uses.merge(e.category,1,Integer::sum);
        List<Budget.Category> often=new ArrayList<>(categories);often.sort((a,b)->uses.getOrDefault(b.id,0)-uses.getOrDefault(a.id,0));
        List<Budget.Category> top=new ArrayList<>(often.subList(0,Math.min(CATEGORY_CHIPS,often.size()))),shown=new ArrayList<>(top);
        TextView categoryTitle=label("Category",12,main.muted,true);categoryFields.addView(categoryTitle);Chips categoryChips=chips(categoryFields,true);
        TextView preview=label("",14,main.muted,true);categoryFields.addView(preview);
        // Split: the parts (positive amounts while editing) replace the category; the amount becomes their total.
        List<Budget.Split> parts=new ArrayList<>();
        for(Budget.Split p:old!=null?old.splits:sched!=null?sched.splits:new ArrayList<Budget.Split>()){Budget.Split c=new Budget.Split(p.category,Math.abs(p.amount));c.memo=p.memo;parts.add(c);
            Budget.Category pc=main.budget.category(p.category);if(pc!=null&&!pc.payment()&&!categories.contains(pc))categories.add(pc);} // a part's hidden category stays choosable
        TextView splitSummary=label("",13,main.ink,false);categoryFields.addView(splitSummary);
        Button splitButton=button("Split into categories",()->{});splitButton.setBackground(bg(Color.TRANSPARENT));splitButton.setTextSize(13);categoryFields.addView(splitButton);
        // A card transaction that is really the card's interest or a fee (an imported one, say) becomes one (cardCharge).
        Budget.Account onCard=old==null?null:main.budget.account(old.account);
        if(onCard!=null&&onCard.credit()&&!old.split()){Button charge=button("It's interest or a fee on "+onCard.name,()->{for(AlertDialog ed:new ArrayList<>(main.editors))ed.dismiss();cardCharge(old.account,old);});
            charge.setBackground(bg(Color.TRANSPARENT));charge.setTextSize(13);categoryFields.addView(charge);}
        boolean[] categoryChosen={old!=null||sched!=null};
        Runnable[] showPreview={()->{}},showCategories={null};
        showCategories[0]=()->{shown.clear();shown.addAll(top);
            if(cat[0]>=0&&!top.contains(categories.get(cat[0]))){if(shown.size()<CATEGORY_CHIPS)shown.add(categories.get(cat[0]));else shown.set(CATEGORY_CHIPS-1,categories.get(cat[0]));}
            List<String> names=new ArrayList<>();for(Budget.Category c:shown)names.add(c.name);names.add("All categories…");
            categoryChips.show(names,cat[0]<0?-1:shown.indexOf(categories.get(cat[0])),n->{
                if(n<shown.size()){cat[0]=categories.indexOf(shown.get(n));categoryChosen[0]=true;showCategories[0].run();showPreview[0].run();return;}
                YearMonth m=YearMonth.now();String[] all=categories.stream().map(c->c.name+"  ·  "+money(main.budget.available(fresh(c),m))).toArray(String[]::new);
                new AlertDialog.Builder(main).setTitle("Choose a category").setItems(all,(d,i)->{cat[0]=i;categoryChosen[0]=true;showCategories[0].run();showPreview[0].run();}).show();});};
        showCategories[0].run();
        Runnable showSplit=()->{boolean on=!parts.isEmpty();categoryChips.view.setVisibility(on?View.GONE:View.VISIBLE);categoryTitle.setVisibility(categoryChips.view.getVisibility());
            splitSummary.setVisibility(on?View.VISIBLE:View.GONE);splitButton.setText(on?"Edit split":"Split into categories");amount.setEnabled(!on);
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
        section(f,"When and where");
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
        // The category's Available after this transaction, in the month of its date: what's left (green) or overspent by (red).
        showPreview[0]=()->{int k=kind.selected();if(k==1||!parts.isEmpty()||cat[0]<0){preview.setVisibility(View.GONE);return;}
            Budget.Category c=fresh(categories.get(cat[0]));long typed;try{typed=Math.max(0,Budget.evaluate(amount.getText().toString()));}catch(Exception e){typed=0;}
            YearMonth m=YearMonth.from(LocalDate.parse((String)day.getTag()));long now=main.budget.available(c,m);
            if(old!=null&&old.category.equals(c.id)&&YearMonth.from(LocalDate.parse(old.date)).equals(m))now-=old.amount; // editing: without its old amount
            long after=now+(k==0?-typed:typed);preview.setVisibility(View.VISIBLE);
            if(typed==0){preview.setTextColor(main.muted);preview.setText(tint(c.name+" has "+money(now)+" available",money(now),amountColour(now)));}
            else if(after>=0){preview.setText(c.name+" will have "+money(after)+" left");preview.setTextColor(amountColour(after));}
            else{preview.setText(c.name+" will be overspent by "+money(-after));preview.setTextColor(main.red);}};
        // 4. More: note, photo, flag, Cleared and repeat, folded away unless this transaction already uses one of them.
        LinearLayout more=column();Button moreButton=button("",()->{});moreButton.setBackground(bg(Color.TRANSPARENT));
        f.addView(moreButton);f.addView(more);
        // Notes used before are suggested, those with this payee first.
        AutoCompleteTextView memo=suggestField(more,"Note (optional)",()->main.budget.memos(payee.getText().toString()));
        CheckBox cleared=new CheckBox(main);cleared.setText("Cleared at the bank");cleared.setTextColor(main.ink);
        cleared.setMinHeight(dp(48));more.addView(cleared);LinearLayout flagBox=column();more.addView(flagBox);
        Spinner flag=spinner(flagBox,"Flag",flagChoices(),old!=null?old.flag:0);
        if(sched!=null)flagBox.setVisibility(View.GONE); // upcoming transactions have no flag
        // A photo (e.g. a receipt), kept on this phone. Picking one leaves this form open (see onRestart).
        String[] photo={old!=null?old.photo:""};LinearLayout photoBox=column();if(sched==null)more.addView(photoBox);
        Runnable[] showPhotoBox=new Runnable[1];
        showPhotoBox[0]=()->{photoBox.removeAllViews();
            // Take a photo (the camera app) or choose one from the gallery; either is copied into photos at most 1600 px.
            if(photo[0].isEmpty())photoBox.addView(button("+ Add a photo",()->new AlertDialog.Builder(main).setTitle("Add a photo")
                .setItems(new String[]{"Take a photo","Choose from gallery"},(d,n)->{main.photoTarget=u->{try{photo[0]=main.copyPhoto(u);}catch(Exception e){toast("Could not add that photo.");}
                    showPhotoBox[0].run();};main.pickingPhoto=true;
                main.photoForm=main.editors.isEmpty()?null:main.editors.get(main.editors.size()-1); // this form: the newest open one
                if(!(n==0?main.takePhoto():main.choosePhoto())){main.pickingPhoto=false;main.photoTarget=null;main.photoForm=null;}}).show()));
            else{android.graphics.Bitmap bm=main.photoBitmap(photo[0],480);
                if(bm==null)photoBox.addView(label("The photo isn't on this phone.",13,main.muted,false));else{ImageView img=new ImageView(main);
                    img.setImageBitmap(bm);img.setAdjustViewBounds(true);img.setScaleType(ImageView.ScaleType.FIT_START);
                    img.setContentDescription("Photo of this transaction. Double tap to view it larger.");
                    img.setOnClickListener(v->viewPhoto(photo[0]));photoBox.addView(img,new LinearLayout.LayoutParams(-1,dp(160)));}
                photoBox.addView(button("Remove photo",()->{photo[0]="";showPhotoBox[0].run();}));}};
        showPhotoBox[0].run();
        Spinner repeat=null;
        if(old==null){repeat=spinner(more,"Repeat",REPEAT_LABELS,sched==null?0:Arrays.asList(Budget.Scheduled.REPEATS).indexOf(sched.repeat));
            more.addView(label("A future date or a repeat makes it upcoming: it waits in Transactions, and you enter it when the day comes.",12,main.muted,false));}
        if(sched!=null){cleared.setVisibility(View.GONE);payee.setText(sched.payee,false);amount.setText(decimal(Math.abs(sched.amount)));
            memo.setText(sched.memo,false);f.addView(button("Delete upcoming transaction",()->deleteScheduled(sched.id)));}
        if(old!=null){payee.setText(old.payee,false);amount.setText(decimal(Math.abs(old.amount)));memo.setText(old.memo,false);
            cleared.setChecked(old.cleared);f.addView(button("Delete transaction",()->delete(old)));}
        boolean used=old!=null&&(!old.memo.isEmpty()||!old.photo.isEmpty()||old.flag>0)||sched!=null&&(!sched.memo.isEmpty()||!sched.repeat.equals("Never"));
        boolean[] open={used};Runnable showMore=()->{more.setVisibility(open[0]?View.VISIBLE:View.GONE);
            moreButton.setText((open[0]?"▾ ":"▸ ")+"More: note, photo, flag, cleared"+(old==null?", repeat":""));
            moreButton.setContentDescription("More details: note, photo, flag, cleared"+(old==null?", repeat":"")+(open[0]?", shown":", hidden"));};
        moreButton.setOnClickListener(v->{open[0]=!open[0];tick(v);showMore.run();});showMore.run();
        showSplit.run();
        Spinner repeatField=repeat;
        Runnable adapt=()->{int selected=kind.selected();boolean income=selected==1;
            categoryFields.setVisibility(income?View.GONE:View.VISIBLE);payee.setHint(income?"Income source":selected==2?"Refund from":"Payee");
            guidance.setText(income?"Adds money into To budget.":selected==2?"Returns money to the original spending category.":"Reduces the available money in your category.");
            paint.run();showPreview[0].run();};
        adapt.run();kind.changed=adapt;
        amount.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int b,int c){}public void onTextChanged(CharSequence s,int a,int b,int c){}
            public void afterTextChanged(android.text.Editable s){
                if(s.length()>0&&(s.charAt(0)=='-'||s.charAt(0)=='−')){kind.picked=true;kind.set(0);s.delete(0,1);return;} // the change re-runs this
                if(s.length()>0&&s.charAt(0)=='+'){kind.picked=true;if(kind.selected()==0)kind.set(1);s.delete(0,1);return;}
                if(s.toString().trim().length()>0)lastTime.setVisibility(View.GONE);showPreview[0].run();}});
        Runnable save=()->{int k=kind.selected();
            if(k!=1&&categories.isEmpty())throw new IllegalArgumentException("Add a category first.");
            boolean isSplit=k!=1&&!parts.isEmpty();
            if(k!=1&&!isSplit&&cat[0]<0)throw new IllegalArgumentException("Choose a category.");
            if(old!=null&&main.budget.entries.stream().noneMatch(t->t.id.equals(old.id)))throw new IllegalArgumentException("This transaction was removed meanwhile."); // the data may have been read in again since the form opened
            String p=required(payee),cat2=k==1?"":isSplit?Budget.SPLIT:categories.get(cat[0]).id,acc2=accounts.get(acc[0]).id,memoText=memo.getText().toString().trim();
            long cents=Budget.cents(amount.getText().toString())*(k==0?-1:1);
            String rep=repeatField==null?"Never":Budget.Scheduled.REPEATS[repeatField.getSelectedItemPosition()];
            LocalDate when=LocalDate.parse((String)day.getTag());
            if(old==null&&(sched!=null||when.isAfter(LocalDate.now())||!rep.equals("Never"))){
                Budget.Scheduled s=new Budget.Scheduled(p,cat2,acc2,when.toString(),cents,rep);s.memo=memoText;
                if(isSplit)for(Budget.Split part:parts){Budget.Split c=new Budget.Split(part.category,part.amount*(k==0?-1:1));c.memo=part.memo;s.splits.add(c);} // entered later with the same parts
                if(sched!=null){s.id=sched.id;s.billKey=sched.billKey;
                    if(when.toString().equals(sched.next))s.day=sched.day;} // Hunt 23 M1: rent on the 31st, shown as 30 Nov, stays the 31st
                if(sched==null&&!when.isAfter(LocalDate.now())){main.budget.validate(s);
                    main.budget.enter(s,photo[0],cleared.isChecked()).flag=flag.getSelectedItemPosition();
                    if(!rep.equals("Never"))main.budget.scheduled.add(s);
                    return;} // today or earlier: entered now (with its photo and Cleared tick), the repeat continues
                main.budget.validate(s);main.budget.scheduled.removeIf(t->t.id.equals(s.id));main.budget.scheduled.add(s);return;
            }
            Budget.Entry e=new Budget.Entry(p,cat2,acc2,date(day),cents);e.memo=memoText;e.photo=photo[0];
            if(isSplit)for(Budget.Split part:parts){Budget.Split s=new Budget.Split(part.category,part.amount*(k==0?-1:1));s.memo=part.memo;
                e.splits.add(s);}e.cleared=cleared.isChecked();e.flag=flag.getSelectedItemPosition();main.budget.validate(e);
            if(old!=null){e.id=old.id;e.externalId=old.externalId;e.billKey=old.billKey;e.bankPayee=Budget.statementPayee(old);e.reconciled=old.reconciled&&e.cleared&&e.account.equals(old.account); // unticking Cleared, or another account (hunt 24 C6), unlocks it
                if(!put(old.id,e))main.budget.entries.add(0,e);}else main.budget.entries.add(0,e);};
        // After a save: which account to start on next time, and a short "Saved" line.
        Runnable saved=()->{if(adding)main.getSharedPreferences("appearance",0).edit().putString("last_account",accounts.get(acc[0]).id).apply();
            long size;try{size=Budget.cents(amount.getText().toString());}catch(Exception e){size=0;}
            Toast.makeText(main,"Saved: "+payee.getText().toString().trim()+" "+(kind.selected()==0?"−":"+")+money(size),Toast.LENGTH_SHORT).show();};
        // Save and add another (new transactions only): the kind, date and account stay; everything else starts afresh.
        Runnable again=!adding?null:()->{payee.setText("",false);amount.setText("");memo.setText("",false);photo[0]="";showPhotoBox[0].run();
            parts.clear();showSplit.run();flag.setSelection(0);cleared.setChecked(false);if(repeatField!=null)repeatField.setSelection(0);
            cat[0]=-1;categoryChosen[0]=false;showCategories[0].run();lastTime.setVisibility(View.GONE);showPreview[0].run();amount.requestFocus();};
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
            s.setAdapter(new ArrayAdapter<>(main,android.R.layout.simple_spinner_dropdown_item,names));
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
        if(large()&&!parts.isEmpty())f.addView(button("Remove split",()->{parts.clear();done.run();shown[0].dismiss();}));
        ScrollView scroll=new ScrollView(main);scroll.addView(f);AlertDialog d=new AlertDialog.Builder(main).setTitle("Split").setView(scroll)
            .setNegativeButton("Cancel",null).setPositiveButton("Done",null)
            .setNeutralButton(parts.isEmpty()||large()?null:"Remove split",(x,w)->{parts.clear();done.run();}).create();shown[0]=d;
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
    private void deleteScheduled(String id){new AlertDialog.Builder(main).setTitle("Delete upcoming transaction?")
            .setMessage("It and its repeats are removed. Transactions already entered stay.").setNegativeButton("Cancel",null)
            .setPositiveButton("Delete",(d,w)->{if(main.deleteWithUndo("Upcoming transaction deleted",()->main.budget.scheduled.remove(scheduledById(id))))for(AlertDialog editor:new ArrayList<>(main.editors))editor.dismiss();}).show();}
    /** A due upcoming transaction: enter it (it becomes money), skip this date, or edit it. */
    void dueActions(String id){
        Budget.Scheduled s;try{s=scheduledById(id);}catch(Exception e){return;}
        boolean isDue=!LocalDate.parse(s.next).isAfter(LocalDate.now());
        List<String> names=new ArrayList<>();List<Runnable> actions=new ArrayList<>();
        if(isDue){names.add("Enter it now");
            actions.add(()->{if(main.change(()->main.budget.enter(scheduledById(id))))toast("Entered "+s.payee+".");});
            names.add(s.repeat.equals("Never")?"Skip it (delete)":"Skip this one");
            actions.add(()->main.change(()->main.budget.advance(scheduledById(id))));}
        names.add("Edit");actions.add(()->transaction(null,s));
        tracked(new AlertDialog.Builder(main).setTitle(s.payee+" · "+money(s.amount))
            .setItems(names.toArray(new String[0]),(d,n)->actions.get(n).run()));
    }
    String repeatLabel(Budget.Scheduled s){int i=Arrays.asList(Budget.Scheduled.REPEATS).indexOf(s.repeat);return i<=0?"Once":REPEAT_LABELS[i];}
    private void delete(Budget.Entry e){new AlertDialog.Builder(main).setTitle("Delete transaction?")
            .setMessage("Account and category balances will be recalculated.").setNegativeButton("Cancel",null)
            .setPositiveButton("Delete",(d,w)->{if(main.deleteWithUndo(e.transfer()?"Transfer deleted":"Transaction deleted",()->{if(!main.budget.entries.removeIf(t->t.id.equals(e.id)))throw new IllegalArgumentException("That transaction no longer exists.");}))
                for(AlertDialog editor:new ArrayList<>(main.editors))editor.dismiss();}).show();}
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
            if(old!=null){e.id=old.id;e.externalId=old.externalId;e.billKey=old.billKey;e.bankPayee=Budget.statementPayee(old);e.flag=old.flag;e.photo=old.photo;e.reconciled=old.reconciled&&e.cleared;}
            main.budget.validate(e);if(old==null||!put(old.id,e))main.budget.entries.add(0,e);});}
    void transfer(){editTransfer(null,null,0);}
    /** A card payment: a transfer from a cash account to the card, for what's set aside (or what's owed, if less). */
    void payCard(String id){Budget.Account card=main.budget.account(id);if(card==null)return;
        editTransfer(null,id,main.budget.toPay(card));} // what's set aside now, whatever month is on screen
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
        dialog(old!=null?"Edit transfer":toIndex>=0?"Pay "+main.budget.account(toId).name:"Transfer money",f,()->{
            if(old!=null&&main.budget.entries.stream().noneMatch(t->t.id.equals(old.id)))throw new IllegalArgumentException("This transaction was removed meanwhile."); // hunt 24 C1
            Budget.Account a=accounts.get(from.getSelectedItemPosition()),b=accounts.get(to.getSelectedItemPosition());
            boolean out=!a.tracking()&&b.tracking();if(out&&cats.isEmpty())throw new IllegalArgumentException("Add a category first.");
            Budget.Entry e=new Budget.Entry("Transfer to "+b.name,out?cats.get(Math.max(0,category.getSelectedItemPosition())).id:"",a.id,date(day),-Budget.cents(amount.getText().toString()));
            e.destination=b.id;e.cleared=cleared.isChecked();if(old!=null){e.memo=old.memo;e.flag=old.flag;e.bankPayee=Budget.statementPayee(old);e.reconciled=old.reconciled&&e.cleared&&e.account.equals(old.account)&&e.destination.equals(old.destination);}
            main.budget.validate(e);if(old!=null)e.id=old.id;if(old==null||!put(old.id,e))main.budget.entries.add(0,e);});

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
            e.bankPayee=Budget.statementPayee(old);main.budget.validate(e);
            if(!put(old.id,e))throw new IllegalArgumentException("This transaction was removed meanwhile.");});
    }
}
