package com.mybudget.app;
import java.math.BigDecimal;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;

/** Cash accounts describe where money is; envelopes describe its purpose. */
public final class Budget {
    public static String id() { return UUID.randomUUID().toString(); }
    public static final class Category {
        public String id=Budget.id(), name, group="Everyday", targetType="Refill", due="";
        public long target;
        public final Map<String,Long> assigned=new TreeMap<>();
        public Category(String name) { this.name=name; }
    }
    public static final class Account {
        public String id=Budget.id(), name, date, reconciled="";
        public long opening;
        public Account(String name,String date,long opening) { this.name=name;this.date=date;this.opening=opening; }
    }
    public static final class Entry {
        public String id=Budget.id(),payee,category,account,destination="",date,memo="";
        public long amount;
        public boolean cleared;
        public Entry(String payee,String category,String account,String date,long amount) {this.payee=payee;this.category=category;this.account=account;this.date=date;this.amount=amount;}
        public boolean transfer(){return !destination.isEmpty();}
    }
    public final List<Category> categories=new ArrayList<>();
    public final List<Account> accounts=new ArrayList<>();
    public final List<Entry> entries=new ArrayList<>();
    public static long parse(String input) {
        try {long v=new BigDecimal(input.trim()).movePointRight(2).longValueExact();if(v < -10_000_000_000L || v>10_000_000_000L)throw new IllegalArgumentException();return v;}
        catch(RuntimeException e){throw new IllegalArgumentException("Enter an amount with at most two decimal places (maximum $100 million).");}
    }
    public static long cents(String input){long v=parse(input);if(v<=0)throw new IllegalArgumentException("Enter a positive amount.");return v;}
    public Category category(String id){for(Category c:categories)if(c.id.equals(id))return c;return null;}
    public Account account(String id){for(Account a:accounts)if(a.id.equals(id))return a;return null;}
    public long assigned(Category c,YearMonth m){return c.assigned.getOrDefault(m.toString(),0L);}
    public long activity(Category c,YearMonth m){long n=0;for(Entry e:entries)if(!e.transfer()&&e.category.equals(c.id)&&e.date.startsWith(m.toString()))n+=e.amount;return n;}
    private YearMonth first(Category c,YearMonth until){YearMonth first=until;for(String key:c.assigned.keySet())if(YearMonth.parse(key).isBefore(first))first=YearMonth.parse(key);for(Entry e:entries)if(e.category.equals(c.id)&&YearMonth.from(LocalDate.parse(e.date)).isBefore(first))first=YearMonth.from(LocalDate.parse(e.date));return first;}
    public long available(Category c,YearMonth month){long n=0;for(YearMonth m=first(c,month);!m.isAfter(month);m=m.plusMonths(1))n=Math.max(0,n)+assigned(c,m)+activity(c,m);return n;}
    public long cash(YearMonth month){String end=month.atEndOfMonth().toString();long n=0;for(Account a:accounts)if(a.date.compareTo(end)<=0)n+=a.opening;for(Entry e:entries)if(!e.transfer()&&e.date.compareTo(end)<=0)n+=e.amount;return n;}
    public long balance(Account a,boolean clearedOnly){long n=a.opening;for(Entry e:entries)if(!clearedOnly||e.cleared){if(e.account.equals(a.id))n+=e.amount;if(e.destination.equals(a.id))n-=e.amount;}return n;}
    public long ready(YearMonth m){long n=cash(m);for(Category c:categories)n-=available(c,m);return n;}
    public long futureAssigned(YearMonth m){long n=0;for(Category c:categories)for(Map.Entry<String,Long>a:c.assigned.entrySet())if(a.getKey().compareTo(m.toString())>0)n+=a.getValue();return n;}
    public long spendable(YearMonth m){return ready(m)-futureAssigned(m);}
    public void assign(Category c,YearMonth m,long amount){if(amount>0&&amount>spendable(m))throw new IllegalArgumentException("Not enough unassigned money; check future months too.");if(amount<0&&-amount>Math.max(0,available(c,m)))throw new IllegalArgumentException("You cannot return more than this category has available.");if(m.isAfter(YearMonth.now())&&assigned(c,m)+amount<0)throw new IllegalArgumentException("Move carried-over money in the current month, or return only this future month's assignment.");c.assigned.put(m.toString(),assigned(c,m)+amount);}
    public void move(Category from,Category to,YearMonth m,long amount){if(from==to||amount<=0||amount>available(from,m))throw new IllegalArgumentException("Choose different categories and an amount available in the source.");if(m.isAfter(YearMonth.now())&&assigned(from,m)-amount<0)throw new IllegalArgumentException("Move carried-over money in the current month.");from.assigned.put(m.toString(),assigned(from,m)-amount);to.assigned.put(m.toString(),assigned(to,m)+amount);}
    public long needed(Category c,YearMonth m){
        if(c.target<=0)return 0;
        if(c.targetType.equals("Monthly"))return Math.max(0,c.target-assigned(c,m));
        if(c.targetType.equals("Balance")&&!c.due.isEmpty()){YearMonth due=YearMonth.parse(c.due);long remaining=Math.max(0,c.target-available(c,m));long months=Math.max(1,ChronoUnit.MONTHS.between(m,due)+1);return(remaining+months-1)/months;}
        long base=c.targetType.equals("Refill")?(m.isAfter(YearMonth.now())?0:Math.max(0,available(c,m.minusMonths(1))))+assigned(c,m):available(c,m);
        return Math.max(0,c.target-base);
    }
    public long spending(YearMonth m){long n=0;for(Entry e:entries)if(!e.transfer()&&!e.category.isEmpty()&&e.date.startsWith(m.toString()))n-=e.amount;return n;}
    public long income(YearMonth m){long n=0;for(Entry e:entries)if(!e.transfer()&&e.category.isEmpty()&&e.date.startsWith(m.toString()))n+=e.amount;return n;}
    public void validate(Entry e){
        Account a=account(e.account);if(a==null)throw new IllegalArgumentException("Choose an account.");LocalDate date=LocalDate.parse(e.date);
        if(date.isAfter(LocalDate.now()))throw new IllegalArgumentException("Use today or a past date.");if(e.date.compareTo(a.date)<0)throw new IllegalArgumentException("Transaction date is before this account's opening date.");
        if(e.payee.trim().isEmpty()||e.amount==0)throw new IllegalArgumentException("Enter a payee and a nonzero amount.");
        if(e.transfer()){Account to=account(e.destination);if(to==null||to==a||e.amount>=0)throw new IllegalArgumentException("Choose a different destination account.");if(e.date.compareTo(to.date)<0)throw new IllegalArgumentException("Date is before the destination account's opening date.");}
        else if(!e.category.isEmpty()&&category(e.category)==null)throw new IllegalArgumentException("Choose a category.");
    }
}
