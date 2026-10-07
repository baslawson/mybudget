package com.mybudget.app;

import android.app.*;
import android.text.*;
import android.widget.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** Accounts: budget, credit card and tracking accounts; adding, editing, reconciling, the payoff planner. */
final class AccountsScreen extends Ui {
    AccountsScreen(MainActivity main){super(main);}
    void accounts(){
        for(Budget.Account a:main.budget.accounts){if(a.closed||a.tracking())continue;LinearLayout c=card();
            c.addView(label(a.name+(a.credit()?" · credit card":""),20,main.ink,true));
            if(a.credit()){long owed=-main.budget.balance(a,false);Budget.Category p=main.budget.paymentCategory(a);
                long ready=p==null?0:Math.max(0,main.budget.available(p,main.month));
                c.addView(label(owed>0?"Owed "+money(owed):owed<0?"In credit "+money(-owed):"Paid off",28,main.ink,true));
                c.addView(label("Set aside for the payment: "+money(ready)+(owed>ready&&owed>0?" ("+money(owed-ready)+" not covered yet)":""),13,owed>ready&&owed>0?main.amber:main.green,true));
                c.addView(button("Make a payment",()->main.forms.payCard(a.id)));}else c.addView(label(money(main.budget.balance(a,false)),28,main.ink,true));
            c.addView(label("Cleared "+money(main.budget.balance(a,true))+" / Uncleared "+money(main.budget.balance(a,false)-main.budget.balance(a,true)),13,main.muted,false));
            if(!a.reconciled.isEmpty())c.addView(label("Last reconciled "+pretty(a.reconciled),12,main.green,false));
            c.addView(button("View transactions",()->{main.clearFilters();main.accountFilter=a.id;main.tab="Spending";main.render();}));
            c.addView(button("Reconcile",()->reconcile(a)));c.addView(button("Edit account",()->editAccount(a.id)));}
        // Tracking accounts: off budget, in Net worth only.
        boolean anyTracking=false;for(Budget.Account a:main.budget.accounts){if(a.closed||!a.tracking())continue;
            if(!anyTracking){main.content.addView(label("Tracking accounts",18,main.blue,true));
                main.content.addView(label("Off budget: they count in Net worth only. Update their balance now and then.",13,main.muted,false));}anyTracking=true;
            LinearLayout c=card();c.addView(label(a.name+(a.liability?" · loan or debt":" · asset"),20,main.ink,true));
            long balance=main.budget.balance(a,false);
            c.addView(label(a.liability?(balance<0?"Owed "+money(-balance):"Paid off"):money(balance),28,main.ink,true));
            if(a.liability&&(a.rate>0||a.payment>0))c.addView(label(a.ratePercent().stripTrailingZeros().toPlainString()+"% a year · "+money(a.payment)+" "+a.frequency.toLowerCase(Locale.ROOT),13,main.muted,false));
            String id=a.id;c.addView(button("Update balance",()->updateBalance(id)));
            if(a.liability)c.addView(button("Payoff planner",()->payoffPlanner(id)));
            c.addView(button("View transactions",()->{main.clearFilters();main.accountFilter=id;main.tab="Spending";main.render();}));
            c.addView(button("Edit account",()->editAccount(id)));}
        main.content.addView(button("+ Add account",this::addAccount));
        if(main.openAccounts().size()>1)main.content.addView(button("Transfer between accounts",main.forms::transfer));
        boolean anyClosed=false;
        for(Budget.Account a:main.budget.accounts)if(a.closed){if(!anyClosed)main.content.addView(label("Closed accounts",18,main.blue,true));
            anyClosed=true;LinearLayout c=card();c.addView(label(a.name,17,main.muted,true));
            c.addView(label("Closed. Its transactions stay in your history.",13,main.muted,false));
            c.addView(button("View transactions",()->{main.clearFilters();main.accountFilter=a.id;main.tab="Spending";main.render();}));
            c.addView(button("Reopen account",()->main.change(()->main.accountById(a.id).closed=false)));}
        main.content.addView(label("Checking, savings and cash accounts are pooled for your plan. Transfers change where money lives, not its purpose. Spending on a credit card moves the category's money to the card's payment category, ready to pay it. Moving money from your budget to a tracking account (an extra loan payment, an investment) is spending from a category; money from one into your budget is income to To budget.",14,main.muted,false));
    }
    void addAccount(){
        LinearLayout f=form();
        Spinner type=spinner(f,"Type",new String[]{"Cash, checking or savings","Credit card","Tracking: an asset (savings elsewhere, investments, house, super)","Tracking: a loan or debt (mortgage, car loan)"},0);
        EditText name=field(f,"Account name",false),opening=field(f,"Current cash balance (AUD)",true);
        f.addView(label("Opening date",12,main.muted,true));EditText day=dateField(f,LocalDate.now().toString());
        TextView help=label("",13,main.muted,false);f.addView(help);
        Runnable adapt=()->{int t=type.getSelectedItemPosition();boolean card=t==1;
            opening.setHint(card?"Amount owed now (AUD, 0 if paid off)":t==2?"What it's worth now (AUD)":t==3?"Amount owed now (AUD)":"Current cash balance (AUD)");
            help.setText(card?"What you owe now is old debt: it gets a payment category with nothing set aside, so assign money to that category to pay it down. New spending on the card moves the category's money there for you.":t>=2?"Off budget: it doesn't change To budget or your categories, only Net worth. Update its balance now and then."+(t==3?" Add the interest rate and payment in Edit account for the payoff planner.":""):"Enter transactions from the opening date onward.");};
        adapt.run();onPick(type,adapt);
        dialog("Add account",f,()->{String n=required(name);
            for(Budget.Account a:main.budget.accounts)if(a.name.equalsIgnoreCase(n))throw new IllegalArgumentException("That account already exists.");
            long balance=Budget.parse(opening.getText().toString().trim().isEmpty()?"0":opening.getText().toString());
            int t=type.getSelectedItemPosition();
            if(balance<0)throw new IllegalArgumentException(t==1||t==3?"Enter what you owe as a positive amount.":t==2?"Enter what it's worth as a positive amount.":"Use a nonnegative cash opening balance.");
            if(t>=2){main.budget.addTracking(n,date(day),balance,t==3);return;}
            if(t==1){for(Budget.Category c:main.budget.categories)if(c.name.equalsIgnoreCase(n))throw new IllegalArgumentException("A category already has that name. Choose another name for the card.");
                main.budget.addCard(n,date(day),balance);}
            else main.budget.accounts.add(new Budget.Account(n,date(day),balance));});
    }
    // A difference can be settled with an adjustment into To budget after the user confirms.
    private void reconcile(Budget.Account a){
        LinearLayout f=form();f.addView(label("Cleared balance: "+money(main.budget.balance(a,true)),18,main.ink,true));
        f.addView(label("Compare with your bank's cleared balance, excluding pending transactions. Mark transactions cleared in Transactions first.",14,main.muted,false));
        EditText value=field(f,"Bank's cleared balance (AUD)",true);
        ScrollView scroll=new ScrollView(main);scroll.addView(f);AlertDialog d=new AlertDialog.Builder(main).setTitle("Reconcile "+a.name)
            .setView(scroll).setNegativeButton("Cancel",null).setPositiveButton("Reconcile",null).create();
        main.editors.add(d);d.setOnDismissListener(v->main.editors.remove(d));
        d.setOnShowListener(v->d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(w->{
            long bank;try{bank=Budget.parse(value.getText().toString());}catch(Exception e){toast(e.getMessage());return;}
            String today=LocalDate.now().toString();Budget.Entry adjust=main.budget.adjustment(main.accountById(a.id),bank,today);
            if(adjust==null){if(main.change(()->main.accountById(a.id).reconciled=today)){d.dismiss();toast("Reconciled.");}return;}
            new AlertDialog.Builder(main).setTitle("Balances differ by "+money(adjust.amount))
                .setMessage("Check for missing or uncleared transactions first. Or add a cleared adjustment of "+money(adjust.amount)+" into To budget so "+a.name+" matches your bank.")
                .setNegativeButton("Check first",null)
                    .setPositiveButton("Add adjustment",(d2,x)->{if(main.change(()->{Budget.Account acc=main.accountById(a.id);
                        Budget.Entry e=main.budget.adjustment(acc,bank,today);if(e!=null){main.budget.validate(e);main.budget.entries.add(0,e);}
                        acc.reconciled=today;})){d.dismiss();toast("Adjustment added and reconciled.");}}).show();
        }));d.show();
    }
    private void editAccount(String id){
        Budget.Account a=main.budget.account(id);if(a==null)return;LinearLayout f=form();f.addView(label("Name",12,main.muted,true));
        EditText name=field(f,"Account name",false);name.setText(a.name);
        f.addView(label("Opened "+pretty(a.date)+" with "+money(a.opening)+". These stay fixed so past months don't change.",13,main.muted,false));
        long balance=main.budget.balance(a,false);
        if(balance==0)f.addView(button("Close account",()->new AlertDialog.Builder(main).setTitle("Close "+a.name+"?")
            .setMessage("It moves to Closed accounts and isn't offered for new transactions. Its history stays, and you can reopen it.")
            .setNegativeButton("Cancel",null)
            .setPositiveButton("Close account",(d,w)->{if(main.change(()->main.budget.close(main.accountById(id))))for(AlertDialog editor:new ArrayList<>(main.editors))editor.dismiss();}).show()));
        else f.addView(label("To close it, first move its "+money(balance)+" to another account: an account closes at $0.",13,main.muted,false));
        if(!main.budget.usedAccount(a))f.addView(button("Delete account",()->new AlertDialog.Builder(main).setTitle("Delete "+a.name+"?")
            .setMessage("It has no transactions. Its opening balance of "+money(a.opening)+" leaves your plan.").setNegativeButton("Cancel",null)
            .setPositiveButton("Delete",(d,w)->{if(main.change(()->main.budget.deleteAccount(main.accountById(id))))for(AlertDialog editor:new ArrayList<>(main.editors))editor.dismiss();}).show()));
        // A loan's terms, for the payoff planner.
        LinearLayout terms=column();if(a.liability)f.addView(terms);terms.addView(label("Loan terms (for the payoff planner)",12,main.muted,true));
        EditText rate=field(terms,"Interest rate (% a year, e.g. 6.25)",false);
        rate.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);EditText payment=field(terms,"Regular payment (AUD)",true);
        Spinner often=spinner(terms,"Paid",Budget.FREQUENCIES,Arrays.asList(Budget.FREQUENCIES).indexOf(a.frequency));
        if(a.rate>0)rate.setText(a.ratePercent().stripTrailingZeros().toPlainString());
        if(a.payment>0){if(main.hideAmounts)payment.setHint("Regular payment: $••• (empty keeps it)");else payment.setText(decimal(a.payment));} // Hide amounts: the payment isn't shown
        dialog("Edit account",f,()->{String n=required(name);
            for(Budget.Account o:main.budget.accounts)if(!o.id.equals(id)&&o.name.equalsIgnoreCase(n))throw new IllegalArgumentException("That account already exists.");
            long r=0,p=0;if(a.liability){String rt=rate.getText().toString().trim();
                try{r=rt.isEmpty()?0:new java.math.BigDecimal(rt).movePointRight(3).longValueExact();}catch(RuntimeException e){r=-1;}
                if(r<0||r>100_000)throw new IllegalArgumentException("Enter an interest rate from 0 to 100%, with up to three decimals.");
                String pt=payment.getText().toString().trim();p=pt.isEmpty()?(main.hideAmounts?main.accountById(id).payment:0):Budget.parse(pt);
                if(p<0)throw new IllegalArgumentException("Enter the payment as a positive amount.");}
            Budget.Account acc=main.accountById(id);main.budget.rename(acc,n);
            if(acc.liability){acc.rate=r;acc.payment=p;acc.frequency=Budget.FREQUENCIES[often.getSelectedItemPosition()];}});
    }
    /** A tracking account's value update: the new balance (what's owed, for a debt) as a cleared transaction for the difference. */
    private void updateBalance(String id){
        Budget.Account a=main.budget.account(id);if(a==null)return;long now=main.budget.balance(a,false);LinearLayout f=form();
        f.addView(label((a.liability?"Owed now: "+money(-now):"Balance now: "+money(now))+". Enter the figure from your statement for the date below; the difference from its balance on that date is recorded as a balance update. It doesn't touch your budget.",13,main.muted,false));
        EditText value=field(f,a.liability?"Amount owed (AUD)":"What it's worth (AUD)",true);f.addView(label("Date",12,main.muted,true));
        EditText day=dateField(f,LocalDate.now().toString());
        dialog("Update "+a.name,f,()->{long v=Budget.parse(value.getText().toString());
            if(v<0)throw new IllegalArgumentException("Enter the amount as a positive number.");
            Budget.Entry e=main.budget.valueUpdate(main.accountById(id),v,date(day));
            if(e==null)throw new IllegalArgumentException("That's already its balance.");main.budget.validate(e);main.budget.entries.add(0,e);});
    }
    /** A loan's payoff date and interest at its payment, and how much sooner (and cheaper) an extra amount each payment makes it. */
    private void payoffPlanner(String id){
        Budget.Account a=main.budget.account(id);if(a==null)return;long owed=-main.budget.balance(a,false);LinearLayout f=form();
        if(owed<=0){toast(a.name+" is paid off.");return;}
        if(a.payment<=0){toast("Add the interest rate and regular payment in Edit account first.");editAccount(id);return;}
        long monthly=Budget.perMonth(a.payment,a.frequency);java.math.BigDecimal rate=a.ratePercent();
        f.addView(label("Owed "+money(owed)+" at "+rate.stripTrailingZeros().toPlainString()+"% a year, paying "+money(a.payment)+" "+a.frequency.toLowerCase(Locale.ROOT)+(a.frequency.equals("Monthly")?"":" (about "+money(monthly)+" a month)")+".",14,main.ink,false));
        Budget.Payoff base=Budget.payoff(owed,rate,monthly,0);f.addView(label(payoffText(base),17,base.finished?main.green:main.red,true));
        f.addView(label("Pay extra each payment",12,main.muted,true));EditText extra=field(f,"Extra (AUD)",true);
        TextView result=label("",15,main.blue,true);f.addView(result);
        f.addView(label("Interest is worked out monthly on what's owed, rounded to the cent; a weekly or fortnightly payment counts as its monthly average. Your lender's figures may differ a little.",12,main.muted,false));
        onText(extra,()->{long x;try{String t=extra.getText().toString().trim();
                x=t.isEmpty()?0:Budget.parse(t);}catch(Exception e){result.setText("");return;}if(x<=0){result.setText("");return;}
            Budget.Payoff faster=Budget.payoff(owed,rate,monthly,Budget.perMonth(x,a.frequency));
            if(!faster.finished){result.setText(payoffText(faster));return;}
            result.setText(money(x)+" extra: "+payoffText(faster)+(base.finished?"\n"+Budget.duration(base.months-faster.months)+" sooner, "+money(base.interest-faster.interest)+" less interest":""));});
        ScrollView scroll=new ScrollView(main);scroll.addView(f);new AlertDialog.Builder(main).setTitle("Payoff planner: "+a.name).setView(scroll)
            .setPositiveButton("Close",null).show();
    }
    private String payoffText(Budget.Payoff p){if(!p.covers)return "This payment doesn't cover the interest: the loan would never be paid off.";
        if(!p.finished)return "At this payment it takes over 100 years to pay off.";
        return "Paid off by "+YearMonth.now().plusMonths(p.months).format(DateTimeFormatter.ofPattern("MMMM yyyy"))+" ("+Budget.duration(p.months)+"), with "+money(p.interest)+" interest";}
}
