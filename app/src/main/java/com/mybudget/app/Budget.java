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
        public boolean hidden; // left out of Budget and pickers; its money still counts
        public String snoozed="",note=""; // snoozed: the month (YYYY-MM) its target asks for nothing
        public String cardAccount=""; // set on a credit card's payment category: the card's account id
        public boolean payment(){return !cardAccount.isEmpty();}
        public int dueDay; // 1-31 for "by the 15th" on Refill/Monthly targets; 0 = end of month
        public final Map<String,Long> assigned=new TreeMap<>();
        public Category(String name) { this.name=name; }
    }
    public static final class Account {
        public String id=Budget.id(), name, date, reconciled="";
        public long opening; // a credit card's is negative: what was owed when it was added
        public String type="cash"; // "cash" (cash, checking, savings) or "credit"
        public boolean credit(){return type.equals("credit");}
        public boolean closed; // only at a zero balance; keeps its transactions
        public Account(String name,String date,long opening) { this.name=name;this.date=date;this.opening=opening; }
    }
    public static final String SPLIT="split",PAY_BY_TRANSFER="Pay a credit card with a transfer to it, not with its payment category.";
    public static final class Split { public String category,memo=""; public long amount; public Split(String category,long amount){this.category=category;this.amount=amount;} }
    public static final class Entry {
        public String id=Budget.id(),payee,category,account,destination="",date,memo="";
        // Set when another app (Planner) sent this expense: its payment id, and its bill (the same for every month's bill).
        public String externalId="",billKey="";
        public String photo=""; // a JPEG in files/photos (on this phone only: backups don't carry photos)
        public long amount;
        public boolean cleared;
        // A split (category SPLIT) spreads [amount] over parts, each with a category ("" = To budget).
        public final List<Split> splits=new ArrayList<>();
        public Entry(String payee,String category,String account,String date,long amount) {this.payee=payee;this.category=category;this.account=account;this.date=date;this.amount=amount;}
        public boolean split(){return !splits.isEmpty();}
        /** The part of this transaction that goes to category [id] ("" = To budget). */
        public long amountIn(String id){if(split()){long n=0;for(Split s:splits)if(s.category.equals(id))n+=s.amount;return n;}return category.equals(id)?amount:0;}
        public boolean touches(String id){if(split()){for(Split s:splits)if(s.category.equals(id))return true;return false;}return category.equals(id);}
        public boolean transfer(){return !destination.isEmpty();}
    }
    /**
     * A future or repeating transaction (a scheduled transaction). It isn't money yet: when its date comes,
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
    /** Planner's upcoming bills (PlannerBills): planned for like scheduled ones, but not saved here and never entered. */
    public final List<Scheduled> fromPlanner=new ArrayList<>();
    /** A Planner bill (billKey) -> the category chosen for it here, until its first expense says so. */
    public final Map<String,String> billCategories=new TreeMap<>();
    /** The category for a Planner bill: the one chosen here, else its last expense's; "" when not known yet. */
    public String plannerCategory(String billKey){String id=billCategories.get(billKey);Category c=id==null?null:category(id);if(c!=null&&!c.payment())return c.id;Entry last=lastForBill(billKey);c=last==null||last.split()?null:category(last.category);return c==null||c.payment()?"":c.id;}
    private List<Scheduled> planned(){List<Scheduled> all=new ArrayList<>(scheduled);all.addAll(fromPlanner);return all;}
    /** Scheduled transactions whose date has come (on or before [today]), oldest first. */
    public List<Scheduled> due(LocalDate today){List<Scheduled> list=new ArrayList<>();for(Scheduled s:scheduled)if(!LocalDate.parse(s.next).isAfter(today))list.add(s);list.sort(Comparator.comparing(s->s.next));return list;}
    public void validate(Scheduled s){
        Account a=account(s.account);if(a==null)throw new IllegalArgumentException("Choose an account.");LocalDate d=LocalDate.parse(s.next);if(s.next.compareTo(a.date)<0)throw new IllegalArgumentException("The date is before this account's opening date.");
        if(d.isAfter(LocalDate.now().plusYears(5)))throw new IllegalArgumentException("Schedule within the next five years.");
        if(s.payee.trim().isEmpty()||s.amount==0)throw new IllegalArgumentException("Enter a payee and a nonzero amount.");if(!s.category.isEmpty()&&category(s.category)==null)throw new IllegalArgumentException("Choose a category.");if(!s.category.isEmpty()&&category(s.category).payment())throw new IllegalArgumentException(PAY_BY_TRANSFER);
        if(!Arrays.asList(Scheduled.REPEATS).contains(s.repeat))throw new IllegalArgumentException("Choose how often it repeats.");
    }
    /** Enters [s]'s current date as a transaction and moves it to its next date (or removes it). */
    public Entry enter(Scheduled s){return enter(s,"",false);}
    /** As enter(s), with a photo and the Cleared tick (a new repeating transaction dated today or earlier). */
    public Entry enter(Scheduled s,String photo,boolean cleared){Entry e=new Entry(s.payee,s.category,s.account,s.next,s.amount);e.memo=s.memo;e.billKey=s.billKey;e.photo=photo;e.cleared=cleared;validate(e);entries.add(0,e);advance(s);return e;}
    /** Skips [s]'s current date without a transaction. */
    public void advance(Scheduled s){LocalDate n=s.after(LocalDate.parse(s.next));if(n==null)scheduled.remove(s);else s.next=n.toString();}
    /** Every date [s] falls on in [m] (from its next date on). In the current month, overdue dates from before it count too: they're still to pay. */
    public List<LocalDate> datesIn(Scheduled s,YearMonth m){List<LocalDate> list=new ArrayList<>();LocalDate d=LocalDate.parse(s.next),end=m.atEndOfMonth();boolean now=m.equals(YearMonth.now());for(int i=0;i<400&&d!=null&&!d.isAfter(end);i++){if(now||!d.isBefore(m.atDay(1)))list.add(d);d=s.after(d);}return list;}
    /** Upcoming outflows from [c] in [m]: what scheduled bills will take. */
    public long upcoming(Category c,YearMonth m){long n=0;for(Scheduled s:planned())if(s.category.equals(c.id)&&s.amount<0)n+=-s.amount*datesIn(s,m).size();return n;}
    /** What Fund targets assigns: the target's need, or enough for this month's upcoming bills, whichever is more. */
    public long fundNeed(Category c,YearMonth m){return Math.max(needed(c,m),Math.max(0,upcoming(c,m)-available(c,m)));}
    /** The first day in [m] money is needed by: the due day or the first upcoming bill (32 = none, 0 = overdue). */
    public int firstDue(Category c,YearMonth m){int first=c.dueDay==0?32:c.dueDay;for(Scheduled s:planned())if(s.category.equals(c.id)&&s.amount<0)for(LocalDate d:datesIn(s,m))first=Math.min(first,d.isBefore(m.atDay(1))?0:d.getDayOfMonth());return first;}
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
    public long activity(Category c,YearMonth m){if(c.payment())return paymentActivity(c,m);long n=0;for(Entry e:entries)if(!e.transfer()&&e.date.startsWith(m.toString()))n+=e.amountIn(c.id);return n;}
    private YearMonth first(Category c,YearMonth until){YearMonth first=until;for(String key:c.assigned.keySet())if(YearMonth.parse(key).isBefore(first))first=YearMonth.parse(key);for(Entry e:entries)if(e.touches(c.id)&&YearMonth.from(LocalDate.parse(e.date)).isBefore(first))first=YearMonth.from(LocalDate.parse(e.date));
        if(c.payment()){Account card=account(c.cardAccount);if(card!=null&&YearMonth.from(LocalDate.parse(card.date)).isBefore(first))first=YearMonth.from(LocalDate.parse(card.date));}return first;}
    // Overspending (a negative balance) resets each month: it's taken from To budget. A card payment category's negative
    // balance that is card credit (a refund after the card was paid, a reward sent to To budget) carries on instead.
    public long available(Category c,YearMonth month){long n=0,start=0;for(YearMonth m=first(c,month);!m.isAfter(month);m=m.plusMonths(1)){n=n<0&&c.payment()?Math.max(n,-cardCredit(c,m.minusMonths(1),start)):Math.max(0,n);start=n;n+=assigned(c,m)+activity(c,m);}return n;}
    /**
     * How far payment category [pc] may stay below zero after [m] (which it started at [start]): the card's credit, what
     * already carried, and its To budget parts in [m]. Below that, more was paid than was set aside: overspending.
     */
    private long cardCredit(Category pc,YearMonth m,long start){Account card=account(pc.cardAccount);if(card==null)return 0;long freed=0;for(Entry e:entries)if(!e.transfer()&&e.account.equals(card.id)&&e.date.startsWith(m.toString()))freed+=e.amountIn("");return Math.max(0,balanceAt(card,m))+Math.max(0,-start)+Math.max(0,freed);}
    /** Overspending to cover in [m]. A payment category below zero by card credit has nothing to cover: it carries on. */
    public long toCover(Category c,YearMonth m){long a=available(c,m);if(a>=0)return 0;if(!c.payment())return -a;long start=a-assigned(c,m)-activity(c,m);return Math.max(0,-a-cardCredit(c,m,start));}
    /** What Pay card fills in: what's set aside this month (below zero is nothing to pay), or what's owed, if less. */
    public long toPay(Account card){Category p=paymentCategory(card);long owed=-balance(card,false),ready=p==null?0:Math.max(0,available(p,YearMonth.now()));return Math.max(0,Math.min(owed,ready));}
    /**
     * Money in cash accounts at the end of [month] (what the plan assigns). Credit cards hold debt, not money: their
     * spending isn't cash (it moves money between categories instead), but a payment from a cash account is. A part
     * sent into To budget on a card (a reward credit, an adjustment) isn't cash either: see paymentActivity.
     */
    public long cash(YearMonth month){
        String end=month.atEndOfMonth().toString();long n=0;for(Account a:accounts)if(!a.credit()&&a.date.compareTo(end)<=0)n+=a.opening;
        for(Entry e:entries){if(e.date.compareTo(end)>0)continue;Account a=account(e.account);if(a==null)continue;
            if(e.transfer()){Account to=account(e.destination);if(to!=null&&a.credit()!=to.credit())n+=a.credit()?-e.amount:e.amount;}
            else if(!a.credit())n+=e.amount;}
        return n;
    }
    public long balance(Account a,boolean clearedOnly){long n=a.opening;for(Entry e:entries)if(!clearedOnly||e.cleared){if(e.account.equals(a.id))n+=e.amount;if(e.destination.equals(a.id))n-=e.amount;}return n;}
    /** To budget: cash less what categories hold. Overspending on a card is card debt, so it doesn't count here. */
    public long ready(YearMonth m){long n=cash(m);for(Category c:categories)n-=available(c,m)+creditOverspent(c,m);return n;}
    // Credit cards in an envelope system. Spending on a card from a category with money moves that money to the card's
    // payment category, ready to pay the bill; spending beyond what the category has is credit overspending: it shows
    // in the category this month and then becomes card debt, without touching To budget. A payment (a transfer
    // from a cash account to the card) uses the payment category's money.
    /** Net spending (refunds negative) in [c] on credit card [card] in [m]; card null = on every card. */
    public long creditSpent(Category c,YearMonth m,Account card){long n=0;for(Entry e:entries){if(e.transfer()||!e.date.startsWith(m.toString()))continue;Account a=account(e.account);if(a!=null&&a.credit()&&(card==null||a==card))n-=e.amountIn(c.id);}return n;}
    /** The part of [c]'s overspending in [m] that came from card spending (it becomes debt, not less To budget). */
    public long creditOverspent(Category c,YearMonth m){if(c.payment())return 0;long a=available(c,m);if(a>=0)return 0;return Math.min(-a,creditSpending(c,m));}
    /** Spending on cards that had more spending than refunds in [m] (each card counted on its own). */
    private long creditSpending(Category c,YearMonth m){long n=0;for(Account a:accounts)if(a.credit())n+=Math.max(0,creditSpent(c,m,a));return n;}
    /**
     * Money moved from [c] to [card]'s payment category in [m]. A card with net refunds gives them back in full; a card
     * with net spending gets its share of what the category could pay for (the rest is credit overspending).
     */
    public long movedToCard(Category c,YearMonth m,Account card){
        long mine=creditSpent(c,m,card);if(mine<=0)return mine;long all=creditSpending(c,m),funded=all-creditOverspent(c,m);if(all==mine)return funded;
        // Rounded by running total (cards in account order), so the cards' shares add up to the funded amount exactly.
        long before=0;for(Account a:accounts){if(a==card)break;if(a.credit())before+=Math.max(0,creditSpent(c,m,a));}
        return share(funded,before+mine,all)-share(funded,before,all);
    }
    private static long share(long funded,long part,long all){return BigDecimal.valueOf(funded).multiply(BigDecimal.valueOf(part)).divide(BigDecimal.valueOf(all),0,java.math.RoundingMode.HALF_UP).longValueExact();}
    // A card's To budget parts (a reward credit, a refund with no category, a reconcile adjustment) change what's owed
    // without moving cash, so they move money between To budget and the payment category: an inflow frees set-aside
    // money (less is owed), an outflow sets more aside from To budget (more is owed).
    private long paymentActivity(Category pc,YearMonth m){
        Account card=account(pc.cardAccount);if(card==null)return 0;long n=0;for(Category c:categories)if(!c.payment())n+=movedToCard(c,m,card);
        for(Entry e:entries)if(e.transfer()&&e.destination.equals(card.id)&&e.date.startsWith(m.toString())){Account from=account(e.account);if(from!=null&&!from.credit())n+=e.amount;}
        for(Entry e:entries)if(!e.transfer()&&e.account.equals(card.id)&&e.date.startsWith(m.toString()))n-=e.amountIn("");
        return n;
    }
    public Category paymentCategory(Account card){for(Category c:categories)if(card.id.equals(c.cardAccount))return c;return null;}
    /** Adds a credit card owing [owed] (a positive amount) and its payment category. Old debt starts with nothing set aside. */
    public Account addCard(String name,String date,long owed){Account a=new Account(name,date,-owed);a.type="credit";accounts.add(a);Category p=new Category(name);p.group="Credit card payments";p.cardAccount=a.id;categories.add(p);return a;}
    /** An account's balance at the end of [m] (a card's is negative while it's owed). */
    public long balanceAt(Account a,YearMonth m){String end=m.atEndOfMonth().toString();if(a.date.compareTo(end)>0)return 0;long n=a.opening;for(Entry e:entries){if(e.date.compareTo(end)>0)continue;if(e.account.equals(a.id))n+=e.amount;if(e.destination.equals(a.id))n-=e.amount;}return n;}
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
    public long spending(YearMonth m){long n=0;for(Entry e:entries)if(!e.transfer()&&e.date.startsWith(m.toString()))n-=e.amount-e.amountIn("");return n;}
    public long income(YearMonth m){long n=0;for(Entry e:entries)if(!e.transfer()&&e.date.startsWith(m.toString()))n+=e.amountIn("");return n;}
    /** Fund targets' order in [m]: earliest due day or upcoming bill first (neither = end of month), otherwise as in the plan. */
    public List<Category> fundOrder(YearMonth m){List<Category> list=new ArrayList<>(categories);list.sort(Comparator.comparingInt(c->firstDue(c,m)));return list;}
    /** What Budget reset returns in [m]: each category's positive Available. Card payment money stays: it pays debt already spent. */
    public Map<Category,Long> resetAmounts(YearMonth m){Map<Category,Long> map=new LinkedHashMap<>();for(Category c:categories){if(c.payment())continue;long a=available(c,m);if(a>0)map.put(c,a);}return map;}
    /** Budget reset: every category's positive Available in [m] goes back into To budget. Returns the total. */
    public long planReset(YearMonth m){
        if(m.isAfter(YearMonth.now()))throw new IllegalArgumentException("Reset this month or an earlier one.");
        long total=0;for(Map.Entry<Category,Long> r:resetAmounts(m).entrySet()){assign(r.getKey(),m,-r.getValue());total+=r.getValue();}return total;
    }
    /** Net worth at the end of [m]: everything in the accounts. */
    public long netWorth(YearMonth m){long n=0;for(Account a:accounts)n+=balanceAt(a,m);return n;}
    /**
     * Money age (how long money waits before it's spent): money spent is matched to the oldest money received (opening balances and
     * inflows), first in first out; each outflow's age is its matched days weighted by amount. The result is
     * the average over the last 10 outflows up to [until], or -1 when there are none.
     */
    public int ageOfMoney(LocalDate until){
        List<long[]> events=new ArrayList<>(); // day, amount (+ in, - out)
        // Cash accounts only: card spending isn't money spent until the card is paid, and the payment is the outflow.
        for(Account a:accounts)if(!a.credit()&&a.opening>0&&!LocalDate.parse(a.date).isAfter(until))events.add(new long[]{LocalDate.parse(a.date).toEpochDay(),a.opening});
        for(Entry e:entries){if(e.amount==0||LocalDate.parse(e.date).isAfter(until))continue;Account a=account(e.account);if(a==null)continue;long day=LocalDate.parse(e.date).toEpochDay();
            if(e.transfer()){Account to=account(e.destination);if(to!=null&&a.credit()!=to.credit())events.add(new long[]{day,a.credit()?-e.amount:e.amount});}else if(!a.credit())events.add(new long[]{day,e.amount});}
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
    public boolean used(Category c){for(Entry e:entries)if(e.touches(c.id))return true;for(Scheduled s:scheduled)if(s.category.equals(c.id))return true;for(long v:c.assigned.values())if(v!=0)return true;return false;}
    public int entriesIn(Category c){int n=0;for(Entry e:entries)if(e.touches(c.id))n++;return n;}
    /** Deletes [c]; its transactions and monthly assignments move to [into] (needed when it was used). Cash doesn't change. */
    public void deleteCategory(Category c,Category into){
        if(c.payment())throw new IllegalArgumentException("This is a credit card's payment category. Delete or close the card instead.");
        if(into!=null&&into.payment())throw new IllegalArgumentException("Choose a spending category, not a card payment.");
        if(into==c||(into==null&&used(c)))throw new IllegalArgumentException("Choose another category to take its transactions and money.");
        if(into!=null){for(Entry e:entries){if(e.category.equals(c.id))e.category=into.id;for(Split s:e.splits)if(s.category.equals(c.id))s.category=into.id;}for(Scheduled s:scheduled)if(s.category.equals(c.id))s.category=into.id;for(Map.Entry<String,Long>a:c.assigned.entrySet())into.assigned.merge(a.getKey(),a.getValue(),Long::sum);}
        for(Map.Entry<String,String> m:billCategories.entrySet())if(m.getValue().equals(c.id))m.setValue(into==null?"":into.id);billCategories.values().removeIf(String::isEmpty);
        categories.remove(c);
    }
    /** Swaps [c] with the next category of its group up (-1) or down (+1); false at the end of the group. */
    public boolean reorder(Category c,int direction){int i=categories.indexOf(c);for(int j=i+direction;j>=0&&j<categories.size();j+=direction)if(categories.get(j).group.equals(c.group)){Collections.swap(categories,i,j);return true;}return false;}
    // Accounts: close at zero, delete only unused.
    public boolean usedAccount(Account a){for(Entry e:entries)if(e.account.equals(a.id)||e.destination.equals(a.id))return true;for(Scheduled s:scheduled)if(s.account.equals(a.id))return true;return false;}
    /** Renames [a]; its transfers' default payee ("Transfer to <name>") follows. */
    public void rename(Account a,String name){for(Entry e:entries)if(e.destination.equals(a.id)&&e.payee.equals("Transfer to "+a.name))e.payee="Transfer to "+name;Category p=paymentCategory(a);if(p!=null&&p.name.equals(a.name))p.name=name;a.name=name;}
    public void close(Account a){if(balance(a,false)!=0)throw new IllegalArgumentException("Move the money out first: an account closes at a $0 balance.");for(Scheduled s:scheduled)if(s.account.equals(a.id))throw new IllegalArgumentException("Move or delete its upcoming transactions first.");a.closed=true;}
    public void deleteAccount(Account a){if(usedAccount(a))throw new IllegalArgumentException("This account has transactions. Close it instead.");Category p=paymentCategory(a);
        if(p!=null){for(long v:p.assigned.values())if(v!=0)throw new IllegalArgumentException("Move the money out of its payment category first.");categories.remove(p);}accounts.remove(a);}
    /** Reconciling when the bank's cleared balance differs: a cleared inflow/outflow into To budget for the difference (on a card, see paymentActivity). */
    public Entry adjustment(Account a,long bankCleared,String today){long difference=bankCleared-balance(a,true);if(difference==0)return null;Entry e=new Entry("Reconciliation adjustment","",a.id,today,difference);e.cleared=true;return e;}
    // Quick assign: what each choice adds to this month's Assigned.
    public long spent(Category c,YearMonth m){return c.payment()?0:Math.max(0,-activity(c,m));} // a card payment isn't spending
    public long averageSpent(Category c,YearMonth m){long n=0;for(int i=1;i<=3;i++)n+=spent(c,m.minusMonths(i));return n/3;}
    /** Change that puts Assigned at 0, or as near as the rules allow (money already spent can't be returned). */
    public long resetChange(Category c,YearMonth m){long a=assigned(c,m);return a<=0?-a:-Math.min(a,Math.max(0,available(c,m)));}
    // Payees: newest first, and the last transaction with one (for its category).
    public List<String> payees(){List<Entry> ordered=new ArrayList<>(entries);ordered.sort((a,b)->b.date.compareTo(a.date));LinkedHashMap<String,String> seen=new LinkedHashMap<>();for(Entry e:ordered)if(!e.transfer())seen.putIfAbsent(e.payee.toLowerCase(Locale.ROOT),e.payee);return new ArrayList<>(seen.values());}
    public Entry lastForPayee(String payee){Entry best=null;for(Entry e:entries)if(!e.transfer()&&e.payee.equalsIgnoreCase(payee.trim())&&(best==null||e.date.compareTo(best.date)>0))best=e;return best;}
    /** Every transaction as CSV for spreadsheets, newest date first. Export only: a backup is what restores. */
    public String csv(){
        StringBuilder out=new StringBuilder("Date,Payee,Category,Group,Account,Transfer to,Amount,Note,Cleared\r\n");List<Entry> ordered=new ArrayList<>(entries);ordered.sort((a,b)->b.date.compareTo(a.date));
        for(Entry e:ordered){Account a=account(e.account),to=account(e.destination);
            // A split is one row per part (its note, or the transaction's), so spreadsheet totals by category add up.
            List<Split> parts=e.split()?e.splits:Collections.singletonList(new Split(e.category,e.amount));
            for(Split p:parts){Category c=category(p.category);String note=e.split()&&!p.memo.isEmpty()?p.memo:e.memo;
                out.append(String.join(",",e.date,cell(e.payee),cell(e.transfer()?"":c==null?"To budget":c.name),cell(e.transfer()||c==null?"":c.group),cell(a==null?"":a.name),cell(to==null?"":to.name),BigDecimal.valueOf(p.amount,2).toPlainString(),cell(note),e.cleared?"Yes":"No")).append("\r\n");}}
        return out.toString();
    }
    // A spreadsheet runs text starting with = + - @ as a formula: a leading ' keeps it text. Quoted when needed.
    static String cell(String s){if(s==null)s="";if(!s.isEmpty()&&"=+-@\t\r".indexOf(s.charAt(0))>=0)s="'"+s;return s.matches("(?s).*[,\"\r\n].*")?"\""+s.replace("\"","\"\"")+"\"":s;}
    public void validate(Entry e){
        Account a=account(e.account);if(a==null)throw new IllegalArgumentException("Choose an account.");LocalDate date=LocalDate.parse(e.date);
        if(date.isAfter(LocalDate.now()))throw new IllegalArgumentException("Use today or a past date.");if(e.date.compareTo(a.date)<0)throw new IllegalArgumentException("Transaction date is before this account's opening date.");
        if(e.payee.trim().isEmpty()||e.amount==0)throw new IllegalArgumentException("Enter a payee and a nonzero amount.");
        if(e.transfer()){Account to=account(e.destination);if(to==null||to==a||e.amount>=0)throw new IllegalArgumentException("Choose a different destination account.");if(e.date.compareTo(to.date)<0)throw new IllegalArgumentException("Date is before the destination account's opening date.");}
        else if(e.split()||e.category.equals(SPLIT)){
            if(!e.category.equals(SPLIT)||e.splits.size()<2)throw new IllegalArgumentException("A split needs at least two parts.");long sum=0;
            for(Split p:e.splits){if(p.amount==0)throw new IllegalArgumentException("Give every part of the split an amount.");if(!p.category.isEmpty()&&category(p.category)==null)throw new IllegalArgumentException("Choose a category for every part.");if(!p.category.isEmpty()&&category(p.category).payment())throw new IllegalArgumentException(PAY_BY_TRANSFER);sum+=p.amount;}
            if(sum!=e.amount)throw new IllegalArgumentException("The parts of the split must add up to the total.");
        }
        else if(!e.category.isEmpty()&&category(e.category)==null)throw new IllegalArgumentException("Choose a category.");
        else if(!e.category.isEmpty()&&category(e.category).payment())throw new IllegalArgumentException(PAY_BY_TRANSFER);
    }
}
