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
    private void viewPhoto(String name){android.graphics.Bitmap bm=main.photoBitmap(name,1600);
        if(bm==null){toast("The photo isn't on this phone.");return;}ImageView img=new ImageView(main);img.setImageBitmap(bm);
        img.setAdjustViewBounds(true);img.setContentDescription("Photo of this transaction");new AlertDialog.Builder(main).setView(img)
            .setPositiveButton("Close",null).show();}
    private static final String[] REPEAT_LABELS={"Doesn't repeat","Weekly","Every 2 weeks","Monthly","Every 3 months","Yearly"};
    void transaction(Budget.Entry old){transaction(old,null);}
    /**
     * Adds or edits a transaction ([old]) or an upcoming one ([sched]). A new one with a future date or a repeat
     * becomes upcoming: it waits in Transactions until its day, when the user enters or skips it.
     */
    void transaction(Budget.Entry old,Budget.Scheduled sched){
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
        LinearLayout f=form();
        Spinner kind=spinner(f,"Type",new String[]{"Expense","Income","Category refund"},oldAmount<0?0:keepCategory.isEmpty()?1:2);
        TextView guidance=label("",12,main.muted,false);f.addView(guidance);
        // Payees used before are suggested; picking one on a new transaction fills in the category it had last time.
        AutoCompleteTextView payee=suggestField(f,"Payee",()->main.budget.payees());
        f.addView(label("Amount ("+code()+")",12,main.muted,true));EditText amount=field(f,"0.00",true);amount.setTextSize(24);
        f.addView(label("Date",12,main.muted,true));
        EditText day=dateField(f,old!=null?old.date:sched!=null?sched.next:LocalDate.now().toString(),old==null);
        Spinner account=spinner(f,"Account",accounts.stream().map(a->a.name).toArray(String[]::new),keepAccount==null?0:accounts.indexOf(main.budget.account(keepAccount)));
        LinearLayout categoryFields=column();f.addView(categoryFields);
        Spinner category=spinner(categoryFields,"Category",categories.stream().map(c->c.name).toArray(String[]::new),keepCategory.isEmpty()?0:categories.indexOf(main.budget.category(keepCategory)));
        // Split: the parts (positive amounts while editing) replace the category; the amount becomes their total.
        List<Budget.Split> parts=new ArrayList<>();
        for(Budget.Split p:old!=null?old.splits:sched!=null?sched.splits:new ArrayList<Budget.Split>()){Budget.Split c=new Budget.Split(p.category,Math.abs(p.amount));c.memo=p.memo;parts.add(c);
            Budget.Category pc=main.budget.category(p.category);if(pc!=null&&!pc.payment()&&!categories.contains(pc))categories.add(pc);} // a part's hidden category stays choosable
        TextView splitSummary=label("",13,main.ink,false);categoryFields.addView(splitSummary);
        Button splitButton=button("Split into categories",()->{});categoryFields.addView(splitButton);
        Runnable showSplit=()->{boolean on=!parts.isEmpty();category.setVisibility(on?View.GONE:View.VISIBLE);
            splitSummary.setVisibility(on?View.VISIBLE:View.GONE);splitButton.setText(on?"Edit split":"Split into categories");amount.setEnabled(!on);
            if(on){long sum=0;StringBuilder s=new StringBuilder("Split: ");
                for(int i=0;i<parts.size();i++){Budget.Split p=parts.get(i);Budget.Category c=main.budget.category(p.category);sum+=p.amount;
                    s.append(i>0?", ":"").append(c==null?"To budget":c.name).append(" ").append(money(p.amount));}splitSummary.setText(s);
                amount.setText(decimal(sum));}};
        splitButton.setOnClickListener(v->{long total;try{total=Budget.cents(amount.getText().toString());}catch(Exception e){total=0;}
            editSplit(parts,categories,categories.isEmpty()?null:categories.get(Math.max(0,category.getSelectedItemPosition())),total,showSplit);});
        showSplit.run();
        boolean[] categoryChosen={old!=null||sched!=null};category.setOnTouchListener((v,ev)->{categoryChosen[0]=true;return false;});
        payee.setOnItemClickListener((p,v,position,id)->{Budget.Entry last=main.budget.lastForPayee(payee.getText().toString());
            if(old!=null||categoryChosen[0]||last==null)return;
            if(last.category.isEmpty()){kind.setSelection(1);return;}int i=categories.indexOf(main.budget.category(last.category));if(i<0)return;
            category.setSelection(i);if(kind.getSelectedItemPosition()==1)kind.setSelection(last.amount<0?0:2);});
        // Notes used before are suggested, those with this payee first.
        LinearLayout noteFields=column();
        AutoCompleteTextView memo=suggestField(noteFields,"Note (optional)",()->main.budget.memos(payee.getText().toString()));
        Button note=button(old!=null&&!old.memo.isEmpty()?"Hide note":"+ Add a note",()->{});f.addView(note);f.addView(noteFields);
        noteFields.setVisibility(old!=null&&!old.memo.isEmpty()?View.VISIBLE:View.GONE);
        note.setOnClickListener(v->{boolean show=noteFields.getVisibility()!=View.VISIBLE;noteFields.setVisibility(show?View.VISIBLE:View.GONE);
            note.setText(show?"Hide note":"+ Add a note");});CheckBox cleared=new CheckBox(main);cleared.setText("Cleared at the bank");
        cleared.setMinHeight(dp(48));f.addView(cleared);LinearLayout flagBox=column();f.addView(flagBox);
        Spinner flag=spinner(flagBox,"Flag",flagChoices(),old!=null?old.flag:0);
        if(sched!=null)flagBox.setVisibility(View.GONE); // upcoming transactions have no flag
        // A photo (e.g. a receipt), kept on this phone. Picking one leaves this form open (see onRestart).
        String[] photo={old!=null?old.photo:""};LinearLayout photoBox=column();if(sched==null)f.addView(photoBox);
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
        if(old==null){repeat=spinner(f,"Repeat",REPEAT_LABELS,sched==null?0:Arrays.asList(Budget.Scheduled.REPEATS).indexOf(sched.repeat));
            f.addView(label("A future date or a repeat makes it upcoming: it waits in Transactions, and you enter it when the day comes.",12,main.muted,false));}
        if(sched!=null){cleared.setVisibility(View.GONE);payee.setText(sched.payee,false);amount.setText(decimal(Math.abs(sched.amount)));
            memo.setText(sched.memo,false);if(!sched.memo.isEmpty()){noteFields.setVisibility(View.VISIBLE);note.setText("Hide note");}
            f.addView(button("Delete upcoming transaction",()->deleteScheduled(sched.id)));}
        if(old!=null){payee.setText(old.payee,false);amount.setText(decimal(Math.abs(old.amount)));memo.setText(old.memo,false);
            cleared.setChecked(old.cleared);f.addView(button("Delete transaction",()->delete(old)));}
        Spinner repeatField=repeat;
        Runnable adapt=()->{int selected=kind.getSelectedItemPosition();boolean income=selected==1;
            categoryFields.setVisibility(income?View.GONE:View.VISIBLE);payee.setHint(income?"Income source":selected==2?"Refund from":"Payee");
            guidance.setText(income?"Adds money into To budget.":selected==2?"Returns money to the original spending category.":"Reduces the available money in your category.");};
        adapt.run();
        kind.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onItemSelected(AdapterView<?> p,View v,int position,long id){adapt.run();}public void onNothingSelected(AdapterView<?> p){}});
        dialog(sched!=null?"Edit upcoming transaction":old==null?"Add transaction":"Edit transaction",f,()->{int k=kind.getSelectedItemPosition();
            if(k!=1&&categories.isEmpty())throw new IllegalArgumentException("Add a category first.");
            boolean isSplit=k!=1&&!parts.isEmpty();
            if(old!=null&&main.budget.entries.stream().noneMatch(t->t.id.equals(old.id)))throw new IllegalArgumentException("This transaction was removed meanwhile."); // the data may have been read in again since the form opened
            String p=required(payee),cat=k==1?"":isSplit?Budget.SPLIT:categories.get(category.getSelectedItemPosition()).id,acc=accounts.get(account.getSelectedItemPosition()).id,memoText=memo.getText().toString().trim();
            long cents=Budget.cents(amount.getText().toString())*(k==0?-1:1);
            String rep=repeatField==null?"Never":Budget.Scheduled.REPEATS[repeatField.getSelectedItemPosition()];
            LocalDate when=LocalDate.parse((String)day.getTag());
            if(old==null&&(sched!=null||when.isAfter(LocalDate.now())||!rep.equals("Never"))){
                Budget.Scheduled s=new Budget.Scheduled(p,cat,acc,when.toString(),cents,rep);s.memo=memoText;
                if(isSplit)for(Budget.Split part:parts){Budget.Split c=new Budget.Split(part.category,part.amount*(k==0?-1:1));c.memo=part.memo;s.splits.add(c);} // entered later with the same parts
                if(sched!=null){s.id=sched.id;s.billKey=sched.billKey;}
                if(sched==null&&!when.isAfter(LocalDate.now())){main.budget.validate(s);
                    main.budget.enter(s,photo[0],cleared.isChecked()).flag=flag.getSelectedItemPosition();
                    if(!rep.equals("Never"))main.budget.scheduled.add(s);
                    return;} // today or earlier: entered now (with its photo and Cleared tick), the repeat continues
                main.budget.validate(s);main.budget.scheduled.removeIf(t->t.id.equals(s.id));main.budget.scheduled.add(s);return;
            }
            Budget.Entry e=new Budget.Entry(p,cat,acc,date(day),cents);e.memo=memoText;e.photo=photo[0];
            if(isSplit)for(Budget.Split part:parts){Budget.Split s=new Budget.Split(part.category,part.amount*(k==0?-1:1));s.memo=part.memo;
                e.splits.add(s);}e.cleared=cleared.isChecked();e.flag=flag.getSelectedItemPosition();main.budget.validate(e);
            if(old!=null){e.id=old.id;e.externalId=old.externalId;e.billKey=old.billKey;e.bankPayee=Budget.statementPayee(old);
                main.budget.entries.removeIf(t->t.id.equals(old.id));}main.budget.entries.add(0,e);});
    }
    /** Edits [parts] (category + positive amount per row); at least two parts. Remove split empties them. */
    private void editSplit(List<Budget.Split> parts,List<Budget.Category> categories,Budget.Category first,long total,Runnable done){
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
            EditText a=new EditText(main);a.setHint("0.00");a.setTextColor(main.ink);a.setSingleLine(true);a.setInputType(AMOUNT_INPUT);
            if(cents!=null&&cents>0)a.setText(decimal(cents));onText(a,total2);a.setOnFocusChangeListener((v,has)->{if(has)current[0]=a;});
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
        f.addView(sum);total2.run();
        ScrollView scroll=new ScrollView(main);scroll.addView(f);AlertDialog d=new AlertDialog.Builder(main).setTitle("Split").setView(scroll)
            .setNegativeButton("Cancel",null).setPositiveButton("Done",null)
            .setNeutralButton(parts.isEmpty()?null:"Remove split",(x,w)->{parts.clear();done.run();}).create();
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
        new AlertDialog.Builder(main).setTitle(s.payee+" · "+money(s.amount))
            .setItems(names.toArray(new String[0]),(d,n)->actions.get(n).run()).show();
    }
    String repeatLabel(Budget.Scheduled s){int i=Arrays.asList(Budget.Scheduled.REPEATS).indexOf(s.repeat);return i<=0?"Once":REPEAT_LABELS[i];}
    private void delete(Budget.Entry e){new AlertDialog.Builder(main).setTitle("Delete transaction?")
            .setMessage("Account and category balances will be recalculated.").setNegativeButton("Cancel",null)
            .setPositiveButton("Delete",(d,w)->{if(main.deleteWithUndo(e.transfer()?"Transfer deleted":"Transaction deleted",()->{if(!main.budget.entries.removeIf(t->t.id.equals(e.id)))throw new IllegalArgumentException("That transaction no longer exists.");}))
                for(AlertDialog editor:new ArrayList<>(main.editors))editor.dismiss();}).show();}
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
            Budget.Account a=accounts.get(from.getSelectedItemPosition()),b=accounts.get(to.getSelectedItemPosition());
            boolean out=!a.tracking()&&b.tracking();if(out&&cats.isEmpty())throw new IllegalArgumentException("Add a category first.");
            Budget.Entry e=new Budget.Entry("Transfer to "+b.name,out?cats.get(Math.max(0,category.getSelectedItemPosition())).id:"",a.id,date(day),-Budget.cents(amount.getText().toString()));
            e.destination=b.id;e.cleared=cleared.isChecked();if(old!=null){e.memo=old.memo;e.flag=old.flag;e.bankPayee=Budget.statementPayee(old);}
            main.budget.validate(e);if(old!=null){e.id=old.id;main.budget.entries.removeIf(t->t.id.equals(old.id));}main.budget.entries.add(0,e);});
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
            e.id=old.id;e.memo=memo.getText().toString().trim();e.cleared=cleared.isChecked();e.flag=flag.getSelectedItemPosition();e.photo=old.photo;
            e.bankPayee=Budget.statementPayee(old);main.budget.validate(e);
            if(!main.budget.entries.removeIf(t->t.id.equals(old.id)))throw new IllegalArgumentException("This transaction was removed meanwhile.");
            main.budget.entries.add(0,e);});
    }
}
