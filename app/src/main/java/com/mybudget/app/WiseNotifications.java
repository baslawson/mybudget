package com.mybudget.app;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Wise's card payment notifications as transactions (WiseListener reads them; this part has no Android in it, so BudgetTest
 * checks it). Wise (com.transferwise.android, English) posts a card payment as title "Card payment", text
 * "13.33 AUD spent at Steam." (seen on Wise 9.51, Nov 2026). Anything else from Wise is left alone: transfers, money in and
 * offers read differently, and a guess could add money that isn't there.
 *
 * A payment is added to the account chosen as the Wise account, To review (like a statement row), with the payee's usual
 * category or import rule, else To categorize. It isn't marked as a statement row (no bankPayee), so importing the Wise
 * statement later matches it (CsvImport: same amount within a week) instead of adding it again, and Planner's "Use imported"
 * can link it to a bill paid with the card.
 */
public final class WiseNotifications {
    private WiseNotifications(){}
    public static final String PACKAGE="com.transferwise.android";
    public static final String NOTE="Wise notification"; // the note on what a notification added
    public static final String TITLE="Card payment";

    public static final class Payment {
        public final long cents;public final String currency,merchant;
        Payment(long cents,String currency,String merchant){this.cents=cents;this.currency=currency;this.merchant=merchant;}
        @Override public String toString(){return cents+" "+currency+" "+merchant;}
    }
    // "13.33 AUD spent at Steam." / "1,234.50 AUD spent at Amazon.com.au." / "5 AUD spent at Bob's Café"
    private static final Pattern SPENT=Pattern.compile("(\\d{1,3}(?:,\\d{3})+|\\d+)(?:\\.(\\d{1,2}))?\\s+([A-Z]{3})\\s+spent at\\s+(.+)");

    /** The card payment in a Wise notification's [title] and [text], or null when it isn't one or can't be read exactly. */
    public static Payment read(String title,String text){
        if(title==null||text==null||!title.trim().equalsIgnoreCase(TITLE))return null;
        // One line only: anything after it (a balance, an offer) would end up in the payee.
        String t=text.trim();if(t.indexOf('\n')>=0||t.indexOf('\r')>=0)return null;
        Matcher m=SPENT.matcher(t.replaceAll("[\\s\\u00A0\\u202F]+"," "));if(!m.matches())return null;
        String whole=m.group(1).replace(",",""),part=m.group(2)==null?"0":m.group(2);if(part.length()==1)part+="0";
        if(whole.length()>9)return null; // over 100 million: as typed amounts
        long cents=Long.parseLong(whole)*100+Long.parseLong(part);if(cents<=0)return null;
        String merchant=m.group(4).trim();if(merchant.endsWith("."))merchant=merchant.substring(0,merchant.length()-1).trim();
        if(merchant.isEmpty())return null;
        return new Payment(cents,m.group(3),Budget.cut(merchant,80));
    }

    public enum Outcome { ADDED, ENTERED_UPCOMING, ALREADY_HERE, OTHER_CURRENCY, NO_ACCOUNT, BEFORE_OPENING, FUTURE }
    public static final class Result {
        public final Outcome outcome;public final Budget.Entry entry; // what was added, entered, or already here; null otherwise
        public Result(Outcome outcome,Budget.Entry entry){this.outcome=outcome;this.entry=entry;}
        public boolean changed(){return outcome==Outcome.ADDED||outcome==Outcome.ENTERED_UPCOMING;}
    }

    /** The accounts a Wise account can be: open, in the budget (not tracking). */
    public static boolean usable(Budget.Account a){return a!=null&&!a.closed&&!a.tracking();}
    /** Whether a notification added [e] (or entered it, from an upcoming transaction: its own note then comes first). */
    public static boolean fromWise(Budget.Entry e){return e.memo.equals(NOTE)||e.memo.endsWith(" · "+NOTE);}
    /** How near its date an upcoming transaction is entered by a payment: less than a week, so a weekly one entered today isn't
     *  taken again next week by a second payment of the same amount (CsvImport's matching, hunt 26 B1). */
    static final int UPCOMING_DAYS=3;
    static final int STATEMENT_DAYS=1;

    /**
     * Adds [p], paid on [date], to [accountId] in [b]. [claimed]: ids of transactions earlier notifications were already taken
     * as (so two equal coffees aren't one). In order: the budget's currency only; a transaction already here with the same amount
     * is this payment (nothing added): entered by hand or from Planner within CsvImport.MATCH_DAYS, or a statement row or reconciled
     * one within STATEMENT_DAYS of its bank date (the statement imported before the notification was read; a wider gap is an
     * earlier payment); then an upcoming transaction due within UPCOMING_DAYS is entered, to review; else a new one to review.
     */
    public static Result add(Budget b,String accountId,Payment p,LocalDate date,Set<String> claimed){
        Budget.Account a=b.account(accountId==null?"":accountId);if(!usable(a))return new Result(Outcome.NO_ACCOUNT,null);
        if(!p.currency.equalsIgnoreCase(b.currency))return new Result(Outcome.OTHER_CURRENCY,null);
        if(date.isAfter(Budget.latestToday()))return new Result(Outcome.FUTURE,null);
        if(date.toString().compareTo(a.date)<0)return new Result(Outcome.BEFORE_OPENING,null);
        long cents=-p.cents;
        Budget.Entry here=null;long gap=Long.MAX_VALUE;
        for(Budget.Entry e:b.entries){if(!e.account.equals(a.id)||e.transfer()||e.amount!=cents||fromWise(e)||claimed.contains(e.id))continue;
            boolean banked=e.reconciled||!Budget.statementPayee(e).isEmpty();String on=banked&&e.bankDate!=null&&!e.bankDate.isEmpty()?e.bankDate:e.date;
            long g;try{g=Math.abs(ChronoUnit.DAYS.between(date,LocalDate.parse(on)));}catch(RuntimeException x){g=Math.abs(ChronoUnit.DAYS.between(date,LocalDate.parse(e.date)));}
            if(g<=(banked?STATEMENT_DAYS:CsvImport.MATCH_DAYS)&&g<gap){here=e;gap=g;}}
        if(here!=null)return new Result(Outcome.ALREADY_HERE,here);
        Budget.Scheduled due=null;gap=Long.MAX_VALUE;
        for(Budget.Scheduled s:b.scheduled){if(!s.account.equals(a.id)||s.amount!=cents||!s.destination.isEmpty())continue;
            long g=Math.abs(ChronoUnit.DAYS.between(date,LocalDate.parse(s.next)));if(g<=UPCOMING_DAYS&&g<gap){due=s;gap=g;}}
        if(due!=null){Budget.Entry e=b.enter(due,"",false,date.toString());e.approved=false;e.memo=e.memo.trim().isEmpty()?NOTE:Budget.cut(e.memo.trim(),200)+" · "+NOTE;
            b.changed();return new Result(Outcome.ENTERED_UPCOMING,e);}
        // As a statement row's payee and category (CsvImport.run): an import rule first, then the payee's suggestion.
        Budget.Rule rule=b.rule(p.merchant);String payee=rule==null||rule.rename.isEmpty()?p.merchant:rule.rename;
        Budget.Category ruled=rule==null?null:b.category(rule.category);if(ruled!=null&&ruled.payment())ruled=null;
        String usual=b.suggestedCategory(payee);Budget.Category known=usual==null?null:b.category(usual);
        String category=ruled!=null?ruled.id:known!=null&&!known.payment()?known.id:CsvImport.toCategorize(b).id;
        Budget.Entry e=new Budget.Entry(payee,category,a.id,date.toString(),cents);e.approved=false;e.memo=NOTE;
        b.validate(e);b.entries.add(0,e);b.changed();return new Result(Outcome.ADDED,e);
    }

    /** One line for Settings: what happened to the last Wise card payment. */
    public static String describe(Result r,Payment p,String date){
        String what=String.format(Locale.ROOT,"%d.%02d %s at %s",p.cents/100,p.cents%100,p.currency,p.merchant);
        switch(r.outcome){
            case ADDED:return "Added "+what+" ("+date+") to review.";
            case ENTERED_UPCOMING:return "Entered the upcoming "+r.entry.payee+" with "+what+" ("+date+").";
            case ALREADY_HERE:return what+" ("+date+") was already in MyBudget as "+r.entry.payee+": not added again.";
            case OTHER_CURRENCY:return "Not added: "+what+" isn't in your budget's currency.";
            case NO_ACCOUNT:return "Not added: "+what+". Choose your Wise account again.";
            case BEFORE_OPENING:return "Not added: "+what+" ("+date+") is before your Wise account's opening date.";
            default:return "Not added: "+what+" ("+date+") is dated in the future.";
        }
    }
}
