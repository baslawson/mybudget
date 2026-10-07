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
        public boolean hidden; // left out of Plan and pickers; its money still counts
        public String snoozed="",note=""; // snoozed: the month (YYYY-MM) its target asks for nothing
        public int dueDay; // 1-31 for "by the 15th" on Refill/Monthly targets; 0 = end of month
        public final Map<String,Long> assigned=new TreeMap<>();
        public Category(String name) { this.name=name; }
    }
    public static final class Account {
        public String id=Budget.id(), name, date, reconciled="";
        public long opening;
        public boolean closed; // only at a zero balance; keeps its transactions
        public Account(String name,String date,long opening) { this.name=name;this.date=date;this.opening=opening; }
    }
    public static final class Entry {
        public String id=Budget.id(),payee,category,account,destination="",date,memo="";
        // Set when another app (Planner) sent this expense: its payment id, and its bill (the same for every month's bill).
        public String externalId="",billKey="";
        public long amount;
        public boolean cleared;
        public Entry(String payee,String category,String account,String date,long amount) {this.payee=payee;this.category=category;this.account=account;this.date=date;this.amount=amount;}
        public boolean transfer(){return !destination.isEmpty();}
    }
    /**
     * A future or repeating transaction (YNAB's scheduled transactions). It isn't money yet: when its date comes,
     * the user enters (or skips) it, and only then does it become an Entry. Repeats keep their day of the month.
     */
    public static final class Scheduled {
        public static final String[] REPEATS={"Never","Weekly","Every 2 weeks","Monthly","Every 3 months","Yearly"};
        public String id=Budget.id(),payee,category,account,next,repeat="Never",memo="",billKey="";
        public long amount;public int day; // day: the day of the month repeats keep (0 = next's day)
        public Scheduled(String payee,String category,String account,String next,long amount,String repeat){this.payee=payee;this.category=category;this.account=account;this.next=next;this.amount=amount;this.repeat=repeat;day=LocalDate.parse(next).getDayOfMonth();}
        /** The date after [d] in this repeat, or null for Never. */
        public LocalDate after(LocalDate d){
            switch(repeat){case"Weekly":return d.plusWeeks(1);case"Every 2 weeks":return d.plusWeeks(2);case"Yearly":return d.plusYears(1);
                case"Monthly":case"Every 3 months":{LocalDate m=d.plusMonths(repeat.equals("Monthly")?1:3);int want=day>0?day:d.getDayOfMonth();return m.withDayOfMonth(Math.min(want,m.lengthOfMonth()));}
                default:return null;}
        }
    }
    public final List<Scheduled> scheduled=new ArrayList<>();
    /** Scheduled transactions whose date has come (on or before [today]), oldest first. */
    public List<Scheduled> due(LocalDate today){List<Scheduled> list=new ArrayList<>();for(Scheduled s:scheduled)if(!LocalDate.parse(s.next).isAfter(today))list.add(s);list.sort(Comparator.comparing(s->s.next));return list;}
    public void validate(Scheduled s){
        Account a=account(s.account);if(a==null)throw new IllegalArgumentException("Choose an account.");LocalDate d=LocalDate.parse(s.next);if(s.next.compareTo(a.date)<0)throw new IllegalArgumentException("The date is before this account's opening date.");
        if(d.isAfter(LocalDate.now().plusYears(5)))throw new IllegalArgumentException("Schedule within the next five years.");
        if(s.payee.trim().isEmpty()||s.amount==0)throw new IllegalArgumentException("Enter a payee and a nonzero amount.");if(!s.category.isEmpty()&&category(s.category)==null)throw new IllegalArgumentException("Choose a category.");
        if(!Arrays.asList(Scheduled.REPEATS).contains(s.repeat))throw new IllegalArgumentException("Choose how often it repeats.");
    }
    /** Enters [s]'s current date as a transaction and moves it to its next date (or removes it). */
    public Entry enter(Scheduled s){Entry e=new Entry(s.payee,s.category,s.account,s.next,s.amount);e.memo=s.memo;e.billKey=s.billKey;validate(e);entries.add(0,e);advance(s);return e;}
    /** Skips [s]'s current date without a transaction. */
    public void advance(Scheduled s){LocalDate n=s.after(LocalDate.parse(s.next));if(n==null)scheduled.remove(s);else s.next=n.toString();}
    /** Every date [s] falls on in [m] (from its next date on). */
    public List<LocalDate> datesIn(Scheduled s,YearMonth m){List<LocalDate> list=new ArrayList<>();LocalDate d=LocalDate.parse(s.next),end=m.atEndOfMonth();for(int i=0;i<400&&d!=null&&!d.isAfter(end);i++){if(!d.isBefore(m.atDay(1)))list.add(d);d=s.after(d);}return list;}
    /** Upcoming outflows from [c] in [m]: what scheduled bills will take. */
    public long upcoming(Category c,YearMonth m){long n=0;for(Scheduled s:scheduled)if(s.category.equals(c.id)&&s.amount<0)n+=-s.amount*datesIn(s,m).size();return n;}
    /** What Fund targets assigns: the target's need, or enough for this month's upcoming bills, whichever is more. */
    public long fundNeed(Category c,YearMonth m){return Math.max(needed(c,m),Math.max(0,upcoming(c,m)-available(c,m)));}
    /** The first day in [m] money is needed by: the due day or the first upcoming bill (32 = none). */
    public int firstDue(Category c,YearMonth m){int first=c.dueDay==0?32:c.dueDay;for(Scheduled s:scheduled)if(s.category.equals(c.id)&&s.amount<0)for(LocalDate d:datesIn(s,m))first=Math.min(first,d.getDayOfMonth());return first;}
    public final List<Category> categories=new ArrayList<>();
    public final List<Account> accounts=new ArrayList<>();
    public final List<Entry> entries=new ArrayList<>();
    public Entry external(String id){if(id==null||id.isEmpty())return null;for(Entry e:entries)if(e.externalId.equals(id))return e;return null;}
    /** The newest expense from [billKey] (entries are kept newest first), or null: its category is suggested next time. */
    public Entry lastForBill(String billKey){if(billKey==null||billKey.isEmpty())return null;for(Entry e:entries)if(e.billKey.equals(billKey))return e;return null;}
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
        if(c.target<=0||c.snoozed.equals(m.toString()))return 0;
        if(c.targetType.equals("Monthly"))return Math.max(0,c.target-assigned(c,m));
        if(c.targetType.equals("Balance")&&!c.due.isEmpty()){YearMonth due=YearMonth.parse(c.due);long remaining=Math.max(0,c.target-available(c,m));long months=Math.max(1,ChronoUnit.MONTHS.between(m,due)+1);return(remaining+months-1)/months;}
        long base=c.targetType.equals("Refill")?(m.isAfter(YearMonth.now())?0:Math.max(0,available(c,m.minusMonths(1))))+assigned(c,m):available(c,m);
        return Math.max(0,c.target-base);
    }
    public long spending(YearMonth m){long n=0;for(Entry e:entries)if(!e.transfer()&&!e.category.isEmpty()&&e.date.startsWith(m.toString()))n-=e.amount;return n;}
    public long income(YearMonth m){long n=0;for(Entry e:entries)if(!e.transfer()&&e.category.isEmpty()&&e.date.startsWith(m.toString()))n+=e.amount;return n;}
    /** Fund targets' order in [m]: earliest due day or upcoming bill first (neither = end of month), otherwise as in the plan. */
    public List<Category> fundOrder(YearMonth m){List<Category> list=new ArrayList<>(categories);list.sort(Comparator.comparingInt(c->firstDue(c,m)));return list;}
    /** Plan reset: every category's positive Available in [m] goes back to Ready to Assign. Returns the total. */
    public long planReset(YearMonth m){
        if(m.isAfter(YearMonth.now()))throw new IllegalArgumentException("Reset this month or an earlier one.");
        long total=0;for(Category c:categories){long a=available(c,m);if(a>0){assign(c,m,-a);total+=a;}}return total;
    }
    /** Net worth at the end of [m]: everything in the accounts. */
    public long netWorth(YearMonth m){return cash(m);}
    /**
     * Age of Money (YNAB's rule 4): money spent is matched to the oldest money received (opening balances and
     * inflows), first in first out; each outflow's age is its matched days weighted by amount. The result is
     * the average over the last 10 outflows up to [until], or -1 when there are none.
     */
    public int ageOfMoney(LocalDate until){
        List<long[]> events=new ArrayList<>(); // day, amount (+ in, - out)
        for(Account a:accounts)if(a.opening>0&&!LocalDate.parse(a.date).isAfter(until))events.add(new long[]{LocalDate.parse(a.date).toEpochDay(),a.opening});
        for(Entry e:entries)if(!e.transfer()&&e.amount!=0&&!LocalDate.parse(e.date).isAfter(until))events.add(new long[]{LocalDate.parse(e.date).toEpochDay(),e.amount});
        events.sort((x,y)->x[0]!=y[0]?Long.compare(x[0],y[0]):Long.compare(y[1],x[1])); // a day's money in before money out
        ArrayDeque<long[]> pool=new ArrayDeque<>();List<Double> ages=new ArrayList<>();
        for(long[] ev:events){
            if(ev[1]>0){pool.add(new long[]{ev[0],ev[1]});continue;}
            long left=-ev[1],matched=0;double days=0;
            while(left>0&&!pool.isEmpty()){long[] head=pool.peek();long use=Math.min(left,head[1]);days+=(double)use*(ev[0]-head[0]);matched+=use;left-=use;head[1]-=use;if(head[1]==0)pool.poll();}
            if(matched>0)ages.add(days/matched);
        }
        if(ages.isEmpty())return -1;double sum=0;List<Double> last=ages.subList(Math.max(0,ages.size()-10),ages.size());for(double a:last)sum+=a;return(int)Math.round(sum/last.size());
    }
    // Categories: delete (moving history to another), reorder within a group.
    public boolean used(Category c){for(Entry e:entries)if(e.category.equals(c.id))return true;for(Scheduled s:scheduled)if(s.category.equals(c.id))return true;for(long v:c.assigned.values())if(v!=0)return true;return false;}
    public int entriesIn(Category c){int n=0;for(Entry e:entries)if(e.category.equals(c.id))n++;return n;}
    /** Deletes [c]; its transactions and monthly assignments move to [into] (needed when it was used). Cash doesn't change. */
    public void deleteCategory(Category c,Category into){
        if(into==c||(into==null&&used(c)))throw new IllegalArgumentException("Choose another category to take its transactions and money.");
        if(into!=null){for(Entry e:entries)if(e.category.equals(c.id))e.category=into.id;for(Scheduled s:scheduled)if(s.category.equals(c.id))s.category=into.id;for(Map.Entry<String,Long>a:c.assigned.entrySet())into.assigned.merge(a.getKey(),a.getValue(),Long::sum);}
        categories.remove(c);
    }
    /** Swaps [c] with the next category of its group up (-1) or down (+1); false at the end of the group. */
    public boolean reorder(Category c,int direction){int i=categories.indexOf(c);for(int j=i+direction;j>=0&&j<categories.size();j+=direction)if(categories.get(j).group.equals(c.group)){Collections.swap(categories,i,j);return true;}return false;}
    // Accounts: close at zero, delete only unused.
    public boolean usedAccount(Account a){for(Entry e:entries)if(e.account.equals(a.id)||e.destination.equals(a.id))return true;for(Scheduled s:scheduled)if(s.account.equals(a.id))return true;return false;}
    /** Renames [a]; its transfers' default payee ("Transfer to <name>") follows. */
    public void rename(Account a,String name){for(Entry e:entries)if(e.destination.equals(a.id)&&e.payee.equals("Transfer to "+a.name))e.payee="Transfer to "+name;a.name=name;}
    public void close(Account a){if(balance(a,false)!=0)throw new IllegalArgumentException("Move the money out first: an account closes at a $0 balance.");for(Scheduled s:scheduled)if(s.account.equals(a.id))throw new IllegalArgumentException("Move or delete its upcoming transactions first.");a.closed=true;}
    public void deleteAccount(Account a){if(usedAccount(a))throw new IllegalArgumentException("This account has transactions. Close it instead.");accounts.remove(a);}
    /** Reconciling when the bank's cleared balance differs: a cleared inflow/outflow to Ready to Assign for the difference. */
    public Entry adjustment(Account a,long bankCleared,String today){long difference=bankCleared-balance(a,true);if(difference==0)return null;Entry e=new Entry("Reconciliation adjustment","",a.id,today,difference);e.cleared=true;return e;}
    // Quick assign: what each choice adds to this month's Assigned.
    public long spent(Category c,YearMonth m){return Math.max(0,-activity(c,m));}
    public long averageSpent(Category c,YearMonth m){long n=0;for(int i=1;i<=3;i++)n+=spent(c,m.minusMonths(i));return n/3;}
    /** Change that puts Assigned at 0, or as near as the rules allow (money already spent can't be returned). */
    public long resetChange(Category c,YearMonth m){long a=assigned(c,m);return a<=0?-a:-Math.min(a,Math.max(0,available(c,m)));}
    // Payees: newest first, and the last transaction with one (for its category).
    public List<String> payees(){List<Entry> ordered=new ArrayList<>(entries);ordered.sort((a,b)->b.date.compareTo(a.date));LinkedHashMap<String,String> seen=new LinkedHashMap<>();for(Entry e:ordered)if(!e.transfer())seen.putIfAbsent(e.payee.toLowerCase(Locale.ROOT),e.payee);return new ArrayList<>(seen.values());}
    public Entry lastForPayee(String payee){Entry best=null;for(Entry e:entries)if(!e.transfer()&&e.payee.equalsIgnoreCase(payee.trim())&&(best==null||e.date.compareTo(best.date)>0))best=e;return best;}
    /** Every transaction as CSV for spreadsheets, newest date first. Export only: a backup is what restores. */
    public String csv(){
        StringBuilder out=new StringBuilder("Date,Payee,Category,Group,Account,Transfer to,Amount,Note,Cleared\r\n");List<Entry> ordered=new ArrayList<>(entries);ordered.sort((a,b)->b.date.compareTo(a.date));
        for(Entry e:ordered){Category c=category(e.category);Account a=account(e.account),to=account(e.destination);
            out.append(String.join(",",e.date,cell(e.payee),cell(e.transfer()?"":c==null?"Ready to Assign":c.name),cell(e.transfer()||c==null?"":c.group),cell(a==null?"":a.name),cell(to==null?"":to.name),BigDecimal.valueOf(e.amount,2).toPlainString(),cell(e.memo),e.cleared?"Yes":"No")).append("\r\n");}
        return out.toString();
    }
    // A spreadsheet runs text starting with = + - @ as a formula: a leading ' keeps it text. Quoted when needed.
    static String cell(String s){if(s==null)s="";if(!s.isEmpty()&&"=+-@\t\r".indexOf(s.charAt(0))>=0)s="'"+s;return s.matches("(?s).*[,\"\r\n].*")?"\""+s.replace("\"","\"\"")+"\"":s;}
    public void validate(Entry e){
        Account a=account(e.account);if(a==null)throw new IllegalArgumentException("Choose an account.");LocalDate date=LocalDate.parse(e.date);
        if(date.isAfter(LocalDate.now()))throw new IllegalArgumentException("Use today or a past date.");if(e.date.compareTo(a.date)<0)throw new IllegalArgumentException("Transaction date is before this account's opening date.");
        if(e.payee.trim().isEmpty()||e.amount==0)throw new IllegalArgumentException("Enter a payee and a nonzero amount.");
        if(e.transfer()){Account to=account(e.destination);if(to==null||to==a||e.amount>=0)throw new IllegalArgumentException("Choose a different destination account.");if(e.date.compareTo(to.date)<0)throw new IllegalArgumentException("Date is before the destination account's opening date.");}
        else if(!e.category.isEmpty()&&category(e.category)==null)throw new IllegalArgumentException("Choose a category.");
    }
}
