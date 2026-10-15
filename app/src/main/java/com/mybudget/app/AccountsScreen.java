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
        if(main.budget.accounts.isEmpty())empty("🏦","No accounts yet. Add the bank, savings, cash or card accounts your money is in, with what's in them now.",null,null);
        List<String> open=new ArrayList<>(),tracked=new ArrayList<>();for(Budget.Account a:main.budget.accounts)if(!a.closed)(a.tracking()?tracked:open).add(a.id);
        // Each group's accounts, in the order they were held and dragged into (Movable; Default order puts them back).
        new Movable(this,"order_accounts","Accounts",open).build(id->accountCard(main.accountById(id)));
        // Tracking accounts: off budget, in Net worth only.
        if(!tracked.isEmpty()){main.content.addView(heading("Tracking accounts",18,main.blue));
            main.content.addView(label("Off budget: they count in Net worth only. Update their balance now and then.",13,main.muted,false));}
        new Movable(this,"order_tracking","Tracking accounts",tracked).build(id->trackingCard(main.accountById(id)));
        main.content.addView(button("+ Add account",this::addAccount));
        if(main.openAccounts().size()>1)main.content.addView(button("Transfer between accounts",main.forms::transfer));
        boolean anyClosed=false;
        for(Budget.Account a:main.budget.accounts)if(a.closed){if(!anyClosed)main.content.addView(heading("Closed accounts",18,main.blue));
            anyClosed=true;LinearLayout c=card();c.addView(label(a.name,17,main.muted,true));
            c.addView(label("Closed. Its transactions stay in your history.",13,main.muted,false));
            c.addView(button("View transactions",()->{main.clearFilters();main.accountFilter=a.id;main.tab="Spending";main.render();}));
            Button reopen=button("Reopen account",()->main.change(()->main.accountById(a.id).closed=false)),delete=button("Delete account",()->confirmDelete(a.id));
            pair(c,reopen,delete);}
        owedSection();
        main.content.addView(label("Checking, savings and cash accounts are pooled for your plan. Transfers change where money lives, not its purpose. Spending on a credit card moves the category's money to the card's payment category, ready to pay it. Moving money from your budget to a tracking account (an extra loan payment, an investment) is spending from a category; money from one into your budget is income to To budget.",14,main.muted,false));
    }
    private void accountCard(Budget.Account a){LinearLayout c=card();
        head(c,a.credit()?"\uD83D\uDCB3":"\uD83C\uDFE6",heading(a.name+(a.payLater?" · pay later":a.credit()?" · credit card":""),20,main.ink));
        if(a.credit()){long owed=-main.budget.balance(a,false);Budget.Category p=main.budget.paymentCategory(a);
            long ready=p==null?0:Math.max(0,main.budget.available(p,main.month));
            TextView debt=label("",28,main.ink,true);debt.setText(owed>0?tint("Owed "+money(owed),money(owed),outColour(owed)):owed<0?tint("In credit "+money(-owed),money(-owed),main.green):"Paid off");c.addView(debt);
            c.addView(label("Set aside for the payment: "+money(ready)+(owed>ready&&owed>0?" ("+money(owed-ready)+" not covered yet)":""),13,owed>ready&&owed>0?main.amber:main.green,true));
            if(a.payLater){List<Budget.Scheduled> left=main.budget.instalmentsLeft(a); // its payments still to come
                if(!left.isEmpty()){Budget.Scheduled next=left.get(0);boolean due=!LocalDate.parse(next.next).isAfter(LocalDate.now());
                    TextView line=label("",14,main.ink,true);String size=money(-next.amount);
                    line.setText(tint((due?"Payment due ":"Next payment ")+size+" on "+pretty(next.next)+" · "+count(left.size(),"payment","payments")+" left",size,main.red));c.addView(line);}
                else if(owed<=0)c.addView(label("All paid. Close it if you're done with it, or keep it for next time.",13,main.green,true));
                Button pay=button("Make a payment",()->main.forms.payCard(a.id)),fee=button("Add a late fee",()->main.forms.cardCharge(a.id,null));
                if(left.isEmpty()&&owed==0)pair(c,pay,button("Close it",()->main.change(()->main.budget.close(main.accountById(a.id)))));else pair(c,pay,fee);}
            else pair(c,button("Make a payment",()->main.forms.payCard(a.id)),button("Add interest or fee",()->main.forms.cardCharge(a.id,null)));}else{long b=main.budget.balance(a,false);c.addView(label(money(b),28,amountColour(b),true));}
        long cleared=main.budget.balance(a,true),uncleared=main.budget.balance(a,false)-cleared;TextView split=label("",13,main.muted,false);
        split.setText(tint2("Cleared "+money(cleared)+" / Uncleared "+money(uncleared),money(cleared),amountColour(cleared),money(uncleared),amountColour(uncleared)));c.addView(split);
        if(!a.reconciled.isEmpty())c.addView(label("Last reconciled "+pretty(a.reconciled),12,main.green,false));
        c.addView(button("View transactions",()->{main.clearFilters();main.accountFilter=a.id;main.tab="Spending";main.render();}));
        pair(c,button("Reconcile",()->reconcile(a)),button("Edit account",()->editAccount(a.id)));}
    private void trackingCard(Budget.Account a){
        LinearLayout c=card();head(c,a.liability?"\uD83D\uDCC9":"\uD83D\uDCC8",heading(a.name+(a.liability?" · loan or debt":" · asset"),20,main.ink));
        long balance=main.budget.balance(a,false);
        TextView worth=label("",28,main.ink,true);
        worth.setText(a.liability?(balance<0?tint("Owed "+money(-balance),money(-balance),outColour(-balance)):"Paid off"):tint(money(balance),money(balance),amountColour(balance)));c.addView(worth);
        if(a.liability&&(a.rate>0||a.payment>0))c.addView(greyLine(a.ratePercent().stripTrailingZeros().toPlainString()+"% a year · "+money(a.payment)+" "+a.frequency.toLowerCase(Locale.ROOT),money(a.payment),outColour(a.payment),13));
        String id=a.id;c.addView(button("Update balance",()->updateBalance(id)));
        if(a.liability)c.addView(button("Payoff planner",()->payoffPlanner(id)));
        pair(c,button("View transactions",()->{main.clearFilters();main.accountFilter=id;main.tab="Spending";main.render();}),button("Edit account",()->editAccount(id)));}
    void addAccount(){
        LinearLayout f=form();
        Spinner type=spinner(f,"Type",new String[]{"Cash, checking or savings","Credit card","Tracking: an asset (savings elsewhere, investments, house, super)","Tracking: a loan or debt (mortgage, car loan)","Pay later (Pay in 4, Afterpay, Zip…)"},0);
        EditText name=field(f,"Account name",false),opening=field(f,"Current cash balance ("+code()+")",true);
        f.addView(label("Opening date",12,main.muted,true));EditText day=dateField(f,LocalDate.now().toString());
        TextView help=label("",13,main.muted,false);f.addView(help);
        Runnable adapt=()->{int t=type.getSelectedItemPosition();boolean card=t==1,later=t==4; // later: a pay-later plan (Budget.addPayLater)
            opening.setHint(later?"Amount owed now ("+code()+", 0 for a new plan)":card?"Amount owed now ("+code()+", 0 if paid off)":t==2?"What it's worth now ("+code()+")":t==3?"Amount owed now ("+code()+")":"Current cash balance ("+code()+")");
            help.setText(later?"For Pay in 4, Afterpay, Zip and the like. In Add transaction, tick Pay later and choose this plan: the purchase is spent from its category now, and its payments are set up as upcoming transfers from your account.":card?"What you owe now is old debt: it gets a payment category with nothing set aside, so assign money to that category to pay it down. New spending on the card moves the category's money there for you.":t>=2&&t<=3?"Off budget: it doesn't change To budget or your categories, only Net worth. Update its balance now and then."+(t==3?" Add the interest rate and payment in Edit account for the payoff planner.":""):"Enter transactions from the opening date onward.");};
        adapt.run();onPick(type,adapt);
        dialog("Add account",f,()->{String n=required(name);
            for(Budget.Account a:main.budget.accounts)if(a.name.equalsIgnoreCase(n))throw new IllegalArgumentException("That account already exists.");
            long balance=Budget.parse(opening.getText().toString().trim().isEmpty()?"0":opening.getText().toString());
            int t=type.getSelectedItemPosition();
            if(balance<0)throw new IllegalArgumentException(t==1||t==3||t==4?"Enter what you owe as a positive amount.":t==2?"Enter what it's worth as a positive amount.":"Use a nonnegative cash opening balance.");
            if(t==2||t==3){main.budget.addTracking(n,date(day),balance,t==3);return;}
            if(t==1||t==4){for(Budget.Category c:main.budget.categories)if(c.name.equalsIgnoreCase(n))throw new IllegalArgumentException("A category already has that name. Choose another name for the "+(t==4?"plan":"card")+".");
                Budget.Account card=main.budget.addCard(n,date(day),balance);card.payLater=t==4;}
            else main.budget.accounts.add(new Budget.Account(n,date(day),balance));});
    }
    // A difference can be settled with an adjustment into To budget after the user confirms.
    private void reconcile(Budget.Account a){
        // Bug hunt 27 (M1): a card is reconciled in what's owed, as its card and forms show it (typing the statement's
        // "owed" figure into a signed balance box offered an adjustment of twice the debt).
        boolean card=a.credit();long cleared=main.budget.balance(a,true);
        LinearLayout f=form();f.addView(label(card?(cleared<=0?"Cleared, owed: "+money(-cleared):"Cleared, in credit: "+money(cleared)):"Cleared balance: "+money(cleared),18,main.ink,true));
        f.addView(label((card?"Compare with what your statement says you owe, excluding pending transactions (in credit: type it with a minus). ":"Compare with your bank's cleared balance, excluding pending transactions. ")+"Mark transactions cleared in Transactions first. Reconciling locks the cleared ones: changing one later asks first.",14,main.muted,false));
        EditText value=field(f,card?"Owed on the statement ("+code()+")":"Bank's cleared balance ("+code()+")",true);
        ScrollView scroll=new ScrollView(main);scroll.addView(f);AlertDialog d=new AlertDialog.Builder(main).setTitle("Reconcile "+a.name)
            .setView(scroll).setNegativeButton("Cancel",null).setPositiveButton("Reconcile",null).create();
        main.editors.add(d);d.setOnDismissListener(v->main.editors.remove(d));
        d.setOnShowListener(v->d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(w->{
            long bank;try{long typed=Budget.parse(value.getText().toString());bank=card?-typed:typed;}catch(Exception e){toast(e.getMessage());return;}
            String today=LocalDate.now().toString();Budget.Entry adjust=main.budget.adjustment(main.accountById(a.id),bank,today);
            if(adjust==null){int[] n={0};if(main.change(()->{Budget.Account acc=main.accountById(a.id);acc.reconciled=today;n[0]=main.budget.lockReconciled(acc);})){d.dismiss();toast("Reconciled."+(n[0]>0?" "+count(n[0],"cleared transaction is","cleared transactions are")+" locked now.":""));}return;}
            new AlertDialog.Builder(main).setTitle("Balances differ by "+money(adjust.amount))
                .setMessage("Check for missing or uncleared transactions first. Or add a cleared adjustment of "+money(adjust.amount)+" into To budget so "+a.name+" matches your bank.")
                .setNegativeButton("Check first",null)
                    .setPositiveButton("Add adjustment",(d2,x)->{if(main.change(()->{Budget.Account acc=main.accountById(a.id);
                        Budget.Entry e=main.budget.adjustment(acc,bank,today);if(e!=null){main.budget.validate(e);main.budget.entries.add(0,e);}
                        acc.reconciled=today;main.budget.lockReconciled(acc);})){d.dismiss();toast("Adjustment added and reconciled. Cleared transactions are locked now.");}}).show();
        }));d.show();
    }
    /**
     * Deleting any account: says what goes with it (its transactions, upcoming ones, a card's payment category) and what stays
     * (the other side of transfers), with a red Delete; Undo is offered for a few seconds after.
     */
    private void confirmDelete(String id){Budget.Account a=main.budget.account(id);if(a==null)return;int[] n=main.budget.deleteCounts(a);
        StringBuilder m=new StringBuilder();
        if(n[0]+n[2]==0)m.append("It has no transactions of its own.");
        else m.append(n[0]>0?count(n[0],"transaction","transactions"):"").append(n[0]>0&&n[2]>0?" and ":"").append(n[2]>0?count(n[2],"upcoming transaction","upcoming transactions"):"")
            .append(" in it ").append(n[0]+n[2]==1?"is":"are").append(" deleted too.");
        if(n[1]>0)m.append("\n\n").append(n[1]==1?"1 transfer with another account stays there as money in or out, so its balance doesn't change."
            :n[1]+" transfers with other accounts stay there as money in or out, so their balances don't change.");
        if(main.budget.paymentCategory(a)!=null)m.append("\n\nIts payment category goes too; any money in it goes back to To budget.");
        if(!a.tracking()&&n[0]>0)m.append("\n\nCategory balances in past months change.");
        m.append("\n\nYou can undo this for a few seconds.");
        Ui.danger(new AlertDialog.Builder(main).setTitle("Delete "+a.name+"?").setMessage(m.toString()).setNegativeButton("Cancel",null)
            .setPositiveButton("Delete",(d,w)->{if(main.deleteWithUndo("Account deleted",()->main.budget.deleteAccount(main.accountById(id))))for(AlertDialog editor:new ArrayList<>(main.editors))editor.dismiss();}).show());}
    private void editAccount(String id){
        Budget.Account a=main.budget.account(id);if(a==null)return;LinearLayout f=form();f.addView(label("Name",12,main.muted,true));
        EditText name=field(f,"Account name",false);name.setText(a.name);
        // The opening balance and date: changeable until the account is reconciled (Budget.setOpening), with what changes shown first.
        boolean owes=a.credit()||a.tracking()&&a.liability,fixed=main.budget.reconciled(a);
        String what=owes?"Owed when added":a.tracking()?"Worth when added":"Opening balance";EditText opening=null,opened=null;
        if(fixed){f.addView(greyLine("Opened "+pretty(a.date)+" with "+money(a.opening)+". It's fixed now that the account has been reconciled. To correct its balance, use Reconcile.",money(a.opening),amountColour(a.opening),13));
            f.addView(button("Reconcile",()->{for(AlertDialog editor:new ArrayList<>(main.editors))editor.dismiss();reconcile(main.accountById(id));}));}
        else{f.addView(label(what+" ("+code()+")",12,main.muted,true));opening=field(f,what,true);
            if(main.hideAmounts)opening.setHint("Hidden · leave empty to keep it");else opening.setText(decimal(Math.abs(a.opening)));
            f.addView(label("Opening date",12,main.muted,true));opened=dateField(f,a.date);
            f.addView(label("Until you reconcile it, these can be corrected: "+(owes?"what it owes":a.tracking()?"Net worth":"To budget")+" from the opening month on is worked out again.",12,main.muted,false));}
        EditText openingBox=opening,openedBox=opened;String[] change={null,null}; // a changed opening ({cents, date}), confirmed after the save
        long balance=main.budget.balance(a,false);
        if(balance==0)f.addView(button("Close account",()->new AlertDialog.Builder(main).setTitle("Close "+a.name+"?")
            .setMessage("It moves to Closed accounts and isn't offered for new transactions. Its history stays, and you can reopen it.")
            .setNegativeButton("Cancel",null)
            .setPositiveButton("Close account",(d,w)->{if(main.change(()->main.budget.close(main.accountById(id))))for(AlertDialog editor:new ArrayList<>(main.editors))editor.dismiss();}).show()));
        else f.addView(greyLine("To close it, first move its "+money(balance)+" to another account: an account closes at a zero balance.",money(balance),amountColour(balance),13));
        f.addView(button("Delete account",()->confirmDelete(id)));
        // A loan's terms, for the payoff planner.
        LinearLayout terms=column();if(a.liability)f.addView(terms);terms.addView(label("Loan terms (for the payoff planner)",12,main.muted,true));
        EditText rate=field(terms,"Interest rate (% a year, e.g. 6.25)",false);
        rate.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);EditText payment=field(terms,"Regular payment ("+code()+")",true);
        Spinner often=spinner(terms,"Paid",Budget.FREQUENCIES,Arrays.asList(Budget.FREQUENCIES).indexOf(a.frequency));
        if(a.rate>0)rate.setText(a.ratePercent().stripTrailingZeros().toPlainString());
        if(a.payment>0){if(main.hideAmounts)payment.setHint("Regular payment: "+money(a.payment)+" (empty keeps it)");else payment.setText(decimal(a.payment));} // Hide amounts: the payment isn't shown
        dialog("Edit account",f,()->{String n=required(name);
            for(Budget.Account o:main.budget.accounts)if(!o.id.equals(id)&&o.name.equalsIgnoreCase(n))throw new IllegalArgumentException("That account already exists.");
            long r=0,p=0;if(a.liability){String rt=rate.getText().toString().trim();
                try{r=rt.isEmpty()?0:new java.math.BigDecimal(rt).movePointRight(3).longValueExact();}catch(RuntimeException e){r=-1;}
                if(r<0||r>100_000)throw new IllegalArgumentException("Enter an interest rate from 0 to 100%, with up to three decimals.");
                String pt=payment.getText().toString().trim();p=pt.isEmpty()?(main.hideAmounts?main.accountById(id).payment:0):Budget.parse(pt);
                if(p<0)throw new IllegalArgumentException("Enter the payment as a positive amount.");}
            Budget.Account acc=main.accountById(id);
            // A changed opening is checked now (on a copy, so a refusal keeps the form open) and confirmed after the save.
            change[0]=null;change[1]=null;
            if(openingBox!=null){String typed=openingBox.getText().toString().trim();
                long size=typed.isEmpty()?(main.hideAmounts?Math.abs(acc.opening):0):Budget.parse(typed);if(size<0)throw new IllegalArgumentException("Enter the amount as a positive number.");
                long cents=owes?-size:size;String day=(String)openedBox.getTag();
                if(cents!=acc.opening||!day.equals(acc.date)){Budget copy=copyOf();copy.setOpening(copy.account(id),cents,day);change[0]=String.valueOf(cents);change[1]=day;}}
            main.budget.rename(acc,n);
            if(acc.liability){acc.rate=r;acc.payment=p;acc.frequency=Budget.FREQUENCIES[often.getSelectedItemPosition()];}},
            ()->{if(change[0]!=null){long cents=Long.parseLong(change[0]);String day=change[1];change[0]=null;confirmOpening(id,cents,day);}});
    }
    /** The budget as it is, to try a change on without touching it. */
    private Budget copyOf(){try{return BudgetStore.decode(BudgetStore.encode(main.budget));}catch(org.json.JSONException e){throw new IllegalStateException("Could not work out the change.");}}
    /**
     * Asks before changing account [id]'s opening to [cents] on [day]: what it opened with, and what changes (To budget in the
     * opening month and now, or what a card or debt owes, or Net worth), with a warning when a month's To budget would go
     * below zero. Changed with Undo.
     */
    private void confirmOpening(String id,long cents,String day){Budget.Account a=main.budget.account(id);if(a==null)return;
        Budget before=copyOf(),after=copyOf();try{after.setOpening(after.account(id),cents,day);}catch(IllegalArgumentException e){toast(e.getMessage());return;}
        Budget.Account was=before.account(id),now=after.account(id);StringBuilder m=new StringBuilder();
        boolean owes=a.credit()||a.tracking()&&a.liability;DateTimeFormatter month=DateTimeFormatter.ofPattern("MMMM yyyy",Locale.forLanguageTag("en-AU"));
        boolean sameDay=was.date.equals(now.date),sameAmount=was.opening==now.opening; // only what changes is spelt out
        m.append(a.name).append(owes?" owed ":a.tracking()?" was worth ":" opened with ").append(money(Math.abs(was.opening)));
        if(sameDay)m.append(" → ").append(money(Math.abs(now.opening))).append(" (").append(pretty(now.date)).append(").\n\n");
        else m.append(" on ").append(pretty(was.date)).append(" → ").append(sameAmount?"":money(Math.abs(now.opening))+" ").append("on ").append(pretty(now.date)).append(".\n\n");
        YearMonth from=YearMonth.from(LocalDate.parse(was.date.compareTo(now.date)<0?was.date:now.date)),today=YearMonth.now();String warning=null;
        if(a.cash()){m.append("To budget in ").append(from.format(month)).append(": ").append(money(before.ready(from))).append(" → ").append(money(after.ready(from))).append(".");
            if(!from.equals(today))m.append("\nTo budget now (").append(today.format(month)).append("): ").append(money(before.ready(today))).append(" → ").append(money(after.ready(today))).append(".");
            for(YearMonth k=from;!k.isAfter(today);k=k.plusMonths(1))if(after.ready(k)<0&&before.ready(k)>=0){
                warning=k.format(month)+"'s To budget would be "+money(after.ready(k))+": more was assigned than you'd have had.";break;}}
        else if(owes)m.append("What it owes now: ").append(money(-before.balance(was,false))).append(" → ").append(money(-after.balance(now,false))).append(".");
        else m.append("Net worth now: ").append(money(before.netWorth(today))).append(" → ").append(money(after.netWorth(today))).append(".");
        m.append(from.equals(today)?"":"\nThe months between change the same way.");
        CharSequence text=warning==null?m:android.text.TextUtils.concat(m,"\n\n",tint(warning,warning,main.red));
        tracked(new AlertDialog.Builder(main).setTitle("Change the opening balance?").setMessage(text).setNegativeButton("Cancel",null)
            .setPositiveButton("Change it",(d,w)->main.deleteWithUndo("Opening balance changed",()->main.budget.setOpening(main.accountById(id),cents,day))));}
    /** A tracking account's value update: the new balance (what's owed, for a debt) as a cleared transaction for the difference. */
    private void updateBalance(String id){
        Budget.Account a=main.budget.account(id);if(a==null)return;long now=main.budget.balance(a,false);LinearLayout f=form();
        f.addView(greyLine((a.liability?"Owed now: "+money(-now):"Balance now: "+money(now))+". Enter the figure from your statement for the date below; the difference from its balance on that date is recorded as a balance update. It doesn't touch your budget.",a.liability?money(-now):money(now),a.liability?outColour(-now):amountColour(now),13));
        EditText value=field(f,a.liability?"Amount owed ("+code()+")":"What it's worth ("+code()+")",true);f.addView(label("Date",12,main.muted,true));
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
        f.addView(label("Pay extra each payment",12,main.muted,true));EditText extra=field(f,"Extra ("+code()+")",true);
        TextView result=label("",15,main.blue,true);result.setAccessibilityLiveRegion(android.view.View.ACCESSIBILITY_LIVE_REGION_POLITE);f.addView(result);
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
    // ---- Money owed between people (user, 14 Oct; Budget.Owed) ----
    /** Who owes you and whom you owe, each tapped for their records, Add and Settle up. Only a record: no account or category changes. */
    private void owedSection(){
        main.content.addView(heading("Owed between people",18,main.blue));LinkedHashMap<String,Long> people=main.budget.owedBalances(false);
        if(people.isEmpty())main.content.addView(label("Keep track of money a friend owes you, or you owe them: a shared bill, a loan, tickets bought for others.",13,main.muted,false));
        for(Map.Entry<String,Long> p:people.entrySet()){String name=p.getKey();long v=p.getValue();LinearLayout c=card();
            c.addView(label(name,17,main.ink,true));
            c.addView(greyLine(v>0?name+" owes you "+money(v):"You owe "+name+" "+money(-v),money(Math.abs(v)),amountColour(v),15)); // bug hunt 28 M8: plain with Hide amounts
            c.setOnClickListener(x->person(name));}
        main.content.addView(button("+ Add money owed",()->addOwed("")));
    }
    /** [name]'s records, newest first, each tapped to correct or delete it (bug hunt 28 M6), with Add and Settle up. */
    void person(String name){LinearLayout f=form();long left=main.budget.owedBalances(true).getOrDefault(name,0L);
        f.addView(label(left==0?"All settled.":left>0?name+" owes you "+money(left):"You owe "+name+" "+money(-left),17,left==0?main.muted:amountColour(left),true));
        section(f,"Records");AlertDialog[] shown={null};
        for(Budget.Owed o:main.budget.owedBy(name)){
            String what=(o.amount>0?"They owe ":"You owe ")+money(Math.abs(o.amount));if(o.note.equals("Settled up"))what=(o.amount>0?"You paid ":"They paid ")+money(Math.abs(o.amount))+" · settled up";
            TextView line=greyLine(pretty(o.date)+" · "+what+(o.note.isEmpty()||o.note.equals("Settled up")?"":" · "+o.note),money(Math.abs(o.amount)),main.ink,14);
            line.setMinHeight(dp(48));line.setGravity(android.view.Gravity.CENTER_VERTICAL);pressable(line);line.setContentDescription(line.getText()+". Double tap to change or delete it.");
            String id=o.id;line.setOnClickListener(v->{if(shown[0]!=null)shown[0].dismiss();editRecord(id);});f.addView(line);}
        f.addView(label("Tap a record to change or delete it. A record only: it doesn't change an account or a category. When they pay you back, also enter it as income.",12,main.muted,false));
        ScrollView scroll=new ScrollView(main);scroll.addView(f);
        AlertDialog.Builder b=new AlertDialog.Builder(main).setTitle(name).setView(scroll).setNegativeButton("Close",null).setNeutralButton("Add",(d,w)->addOwed(name));
        // With the Undo bar a delete has (2026-10-15): settling up was final at one tap. When they owed you, it also offers
        // to enter what they paid back as income, in the same change (one Undo takes back both).
        if(left!=0)b.setPositiveButton("Settle up",(d,w)->{if(left>0)settleWithIncome(name,left);
            else main.changeWithUndo(name+" settled up",name+"'s settling up undone.",()->main.budget.settle(name,LocalDate.now().toString()));});
        shown[0]=b.show();}
    /**
     * Settling up with someone who owed you [cents] (2026-10-15): also enter what they paid back as income (to the category it
     * was spent from, see Budget.owedCategory, or To budget), into
     * the account it went to, or settle up only. Either way one change, which the Undo bar takes back whole.
     */
    private void settleWithIncome(String name,long cents){
        // Bug hunt 30 B7: money paid back goes into a cash account, not onto a card.
        List<Budget.Account> into=main.openAccounts();into.removeIf(a->a.tracking()||a.credit());
        // B8: with no account for it, settled up only, and saying why.
        if(into.isEmpty()){main.changeWithUndo(name+" settled up (no budget account for the income)",name+"'s settling up undone.",()->main.budget.settle(name,LocalDate.now().toString()));return;}
        // B7: owed for part of a transaction, it goes back to that transaction's category (or To budget, if chosen).
        String fromId=main.budget.owedCategory(name);Budget.Category from=fromId==null?null:main.budget.category(fromId);
        LinearLayout f=form();
        f.addView(label(name+" paid you back "+money(cents)+"? It can go in as income as well.",14,main.ink,false));
        String[] names=new String[into.size()];for(int i=0;i<names.length;i++)names[i]=into.get(i).name;
        android.widget.Spinner account=spinner(f,"Into account",names,0);
        String[] ids=new String[into.size()];for(int i=0;i<ids.length;i++)ids[i]=into.get(i).id;
        android.widget.Spinner back=from==null?null:spinner(f,"Into category",new String[]{from.name+" (where it was spent)","To budget"},0);
        if(from==null)f.addView(label("It goes to To budget.",12,main.muted,false));
        new AlertDialog.Builder(main).setTitle("Settle up with "+name).setView(f).setNegativeButton("Cancel",null)
            .setNeutralButton("Settle up only",(d,w)->main.changeWithUndo(name+" settled up",name+"'s settling up undone.",()->main.budget.settle(name,LocalDate.now().toString())))
            .setPositiveButton("Add income too",(d,w)->{String accountId=ids[Math.max(0,account.getSelectedItemPosition())];String today=LocalDate.now().toString();
                main.changeWithUndo(name+" settled up, "+money(cents)+" in",name+"'s settling up and income undone.",()->{
                    String category=from!=null&&back.getSelectedItemPosition()==0?from.id:"";
                    Budget.Entry income=new Budget.Entry(name,category,accountId,today,cents);income.memo="Paid back (settled up)";
                    main.budget.validate(income);main.budget.entries.add(0,income);main.budget.settle(name,today);});})
            .show();}
    /** A record corrected (which way, amount, date, note) or deleted (bug hunt 28 M6). */
    private void editRecord(String id){Budget.Owed o=null;for(Budget.Owed w:main.budget.owed)if(w.id.equals(id))o=w;if(o==null)return;String name=o.person;
        LinearLayout f=form();Spinner way=spinner(f,"Which way",new String[]{"They owe me","I owe them"},o.amount>0?0:1);
        EditText amount=field(f,"Amount ("+code()+")",true);amount.setText(decimal(Math.abs(o.amount)));EditText note=field(f,"What for (optional)",false);note.setText(o.note);
        EditText date=dateField(f,o.date);
        f.addView(button("Delete this record",()->Ui.danger(new AlertDialog.Builder(main).setTitle("Delete this record?").setMessage("Only the record goes: no account or category changes.")
            .setNegativeButton("Cancel",null).setPositiveButton("Delete",(d,w)->{try{main.commit(()->main.budget.editOwed(id,0,"",""));for(AlertDialog e:new ArrayList<>(main.editors))e.dismiss();main.render();person(name);}catch(Exception x){toast(x.getMessage());}}).show())));
        dialog("Money owed",f,()->{long cents=Budget.parse(amount.getText().toString());if(cents<=0)throw new IllegalArgumentException("Enter an amount above zero.");
            main.budget.editOwed(id,way.getSelectedItemPosition()==0?cents:-cents,date(date),note.getText().toString());},()->person(name));}
    /** A new record: who, which way, how much, when and what for. */
    void addOwed(String who){LinearLayout f=form();
        AutoCompleteTextView person=suggestField(f,"Who",()->new ArrayList<>(main.budget.owedBalances(true).keySet()));person.setText(who);
        Spinner way=spinner(f,"Which way",new String[]{"They owe me","I owe them"},0);EditText amount=field(f,"Amount ("+code()+")",true),note=field(f,"What for (optional)",false);
        EditText date=dateField(f,LocalDate.now().toString());
        dialog("Money owed",f,()->{long cents=Budget.parse(amount.getText().toString());if(cents<=0)throw new IllegalArgumentException("Enter an amount above zero.");
            main.budget.addOwed(person.getText().toString(),way.getSelectedItemPosition()==0?cents:-cents,date(date),note.getText().toString(),"");});}
    /** From a transaction (its editor): someone owes part of it; half to start with, the payee as the note, linked to it. */
    void owedFor(Budget.Entry e){LinearLayout f=form();
        AutoCompleteTextView person=suggestField(f,"Who owes you",()->new ArrayList<>(main.budget.owedBalances(true).keySet()));
        EditText amount=field(f,"Their part ("+code()+")",true);amount.setText(decimal(Math.abs(e.amount)/2));EditText note=field(f,"What for",false);note.setText(e.payee);
        f.addView(label("Of "+money(-e.amount)+" on "+pretty(e.date)+". A record only: the transaction stays as it is.",12,main.muted,false));
        String id=e.id,day=e.date;
        dialog("Someone owes part of this",f,()->{long cents=Budget.parse(amount.getText().toString());if(cents<=0)throw new IllegalArgumentException("Enter an amount above zero.");
            main.budget.addOwed(person.getText().toString(),cents,day,note.getText().toString(),id);});}
}
