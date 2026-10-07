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
        public int dueDay; // 1-31 for "by the 15th" on Refill/Monthly/Debt targets; 0 = end of month
        // Weekly targets: [target] every [weekday] (1 = Monday ... 7 = Sunday); refill tops up to it each week, else a fresh amount each week.
        public int weekday=1;public boolean weeklyRefill=true;
        // By date targets: [target] by [dueDate] (YYYY-MM-DD), then again every [repeatMonths] months (0 = once).
        public String dueDate="";public int repeatMonths;
        public boolean pinned; // a priority category on Home (at most PINS)
        public final Map<String,Long> assigned=new TreeMap<>();
        public Category(String name) { this.name=name; }
    }
    public static final class Account {
        public String id=Budget.id(), name, date, reconciled="";
        public long opening; // a credit card's is negative: what was owed when it was added
        public String type="cash"; // "cash" (cash, checking, savings), "credit", or "tracking" (off budget: an asset or a debt it only follows)
        public boolean credit(){return type.equals("credit");}
        public boolean cash(){return type.equals("cash");}
        public boolean tracking(){return type.equals("tracking");}
        public boolean liability; // a tracking account that's owed (mortgage, car loan): its balance is negative, like a card's
        // A loan's terms: rate in thousandths of a percent a year (6.25% = 6250), the regular payment and how often it's paid.
        public long rate,payment;public String frequency="Monthly";
        public BigDecimal ratePercent(){return BigDecimal.valueOf(rate,3);}
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
        public int flag; // 0 = none, else a colour in FLAGS
        public boolean approved=true; // false: imported from a statement and still to review
        public String bankPayee=""; // imported rows: the statement's own payee text (before rules or renames), so a re-import still spots them
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
            switch(repeat){case"Weekly":return d.plusWeeks(1);case"Every 2 weeks":return d.plusWeeks(2);
                case"Monthly":case"Every 3 months":case"Yearly":{LocalDate m=d.plusMonths(repeat.equals("Monthly")?1:repeat.equals("Yearly")?12:3);int want=day>0?day:d.getDayOfMonth();return m.withDayOfMonth(Math.min(want,m.lengthOfMonth()));} // 29 Feb comes back in leap years
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
        if(s.payee.trim().isEmpty()||s.amount==0)throw new IllegalArgumentException("Enter a payee and a nonzero amount.");if(!s.category.isEmpty()&&category(s.category)==null)throw new IllegalArgumentException("Choose a category.");if(!s.category.isEmpty()&&category(s.category).payment())throw new IllegalArgumentException(PAY_BY_TRANSFER);if(a.tracking()&&!s.category.isEmpty())throw new IllegalArgumentException(TRACKING_NO_CATEGORY);
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
    public int firstDue(Category c,YearMonth m){int first=dueDay(c,m);for(Scheduled s:planned())if(s.category.equals(c.id)&&s.amount<0)for(LocalDate d:datesIn(s,m))first=Math.min(first,d.isBefore(m.atDay(1))?0:d.getDayOfMonth());return first;}
    /** The target's own due day in [m]: a weekly target's first chosen weekday, a by-date target's date when it falls in [m], else the due day (32 = none). */
    static int dueDay(Category c,YearMonth m){
        if(c.targetType.equals("Weekly"))return c.target>0?m.atDay(1).with(java.time.temporal.TemporalAdjusters.firstInMonth(DayOfWeek.of(c.weekday))).getDayOfMonth():32;
        if(c.targetType.equals("ByDate")){LocalDate d=c.target>0?dueFor(c,m):null;return d!=null&&YearMonth.from(d).equals(m)?d.getDayOfMonth():32;}
        return c.dueDay==0?32:c.dueDay;
    }
    /** How many times [weekday] (1 = Monday) falls in [m]: 4 or 5. */
    public static int weekdaysIn(YearMonth m,int weekday){int n=0;for(int d=1;d<=m.lengthOfMonth();d++)if(m.atDay(d).getDayOfWeek().getValue()==weekday)n++;return n;}
    /** A weekly target's amount for [m]: the weekly amount times the chosen weekdays in the month. */
    public static long weeklyGoal(Category c,YearMonth m){return c.target*weekdaysIn(m,c.weekday);}
    /** A by-date target's next due date from [m] on: its date, or with a repeat the first repeat in or after [m]; null once it has passed (or none). */
    public static LocalDate dueFor(Category c,YearMonth m){
        if(c.dueDate.isEmpty())return null;LocalDate d;try{d=LocalDate.parse(c.dueDate);}catch(RuntimeException e){return null;}
        if(!YearMonth.from(d).isBefore(m))return d;if(c.repeatMonths<=0)return null;
        long behind=ChronoUnit.MONTHS.between(YearMonth.from(d),m),k=(behind+c.repeatMonths-1)/c.repeatMonths;return d.plusMonths(k*c.repeatMonths); // from the first date, so the 31st stays the 31st where it can
    }
    /** What [c] brought into [m] from earlier months (nothing when overspent). */
    public long carried(Category c,YearMonth m){return Math.max(0,available(c,m.minusMonths(1)));}
    public final List<Category> categories=new ArrayList<>();
    public final List<Account> accounts=new ArrayList<>();
    public final List<Entry> entries=new ArrayList<>();
    /** A note per month ("YYYY-MM" -> text, up to 200 characters), shown at the top of Budget. */
    public final Map<String,String> monthNotes=new TreeMap<>();
    public static final int MONTH_NOTE_MAX=200;
    public String monthNote(YearMonth m){return monthNotes.getOrDefault(m.toString(),"");}
    public void setMonthNote(YearMonth m,String text){String t=text==null?"":text.trim();if(t.length()>MONTH_NOTE_MAX)throw new IllegalArgumentException("Keep the month's note to "+MONTH_NOTE_MAX+" characters.");if(t.isEmpty())monthNotes.remove(m.toString());else monthNotes.put(m.toString(),t);}
    public Entry external(String id){if(id==null||id.isEmpty())return null;for(Entry e:entries)if(e.externalId.equals(id))return e;return null;}
    /** The newest expense from [billKey] (entries are kept newest first), or null: its category is suggested next time. */
    public Entry lastForBill(String billKey){if(billKey==null||billKey.isEmpty())return null;for(Entry e:entries)if(e.billKey.equals(billKey))return e;return null;}
    /** An amount box's text in cents. Quick maths works too: see evaluate. */
    public static long parse(String input) {return evaluate(input);}
    /**
     * Quick maths: amounts (at most two decimals) with + - * / and brackets, the usual order ("45+12.50", "100-20",
     * "3*12.5", "(10+5)/2", "+250"). Each * and / is rounded half up to the cent. Up to $100 million either way.
     */
    public static long evaluate(String input) {
        try {String s=input.replaceAll("\\s","");if(s.length()>100)throw new IllegalArgumentException();Calc c=new Calc(s);BigDecimal v=c.sum();if(c.at!=s.length())throw new IllegalArgumentException();
            long cents=v.movePointRight(2).longValueExact();if(cents < -10_000_000_000L || cents>10_000_000_000L)throw new IllegalArgumentException();return cents;}
        catch(RuntimeException e){throw new IllegalArgumentException("Enter an amount with at most two decimal places (maximum $100 million).");}
    }
    /** As evaluate, but text starting with + adds to [current] ("+50" on a $300 target makes $350). */
    public static long adjust(String input,long current){String s=input.trim();return s.startsWith("+")?evaluate(BigDecimal.valueOf(current,2).toPlainString()+s):evaluate(s);}
    private static final class Calc {
        final String s;int at;Calc(String s){this.s=s;}
        boolean eat(char ch){if(at<s.length()&&s.charAt(at)==ch){at++;return true;}return false;}
        BigDecimal sum(){BigDecimal v=product();while(true){if(eat('+'))v=check(v.add(product()));else if(eat('-'))v=check(v.subtract(product()));else return v;}}
        BigDecimal product(){BigDecimal v=unary();while(true){if(eat('*'))v=check(v.multiply(unary()).setScale(2,java.math.RoundingMode.HALF_UP));else if(eat('/')){BigDecimal d=unary();if(d.signum()==0)throw new ArithmeticException();v=check(v.divide(d,2,java.math.RoundingMode.HALF_UP));}else return v;}}
        BigDecimal unary(){
            if(eat('+'))return unary();if(eat('-'))return unary().negate();if(eat('(')){BigDecimal v=sum();if(!eat(')'))throw new IllegalArgumentException();return v;}
            int start=at;while(at<s.length()&&(s.charAt(at)>='0'&&s.charAt(at)<='9'||s.charAt(at)=='.'))at++;BigDecimal v=new BigDecimal(s.substring(start,at));if(v.stripTrailingZeros().scale()>2)throw new IllegalArgumentException();return check(v.setScale(2,java.math.RoundingMode.HALF_UP));
        }
        static BigDecimal check(BigDecimal v){if(v.abs().compareTo(BigDecimal.valueOf(1_000_000_000_000L))>0)throw new ArithmeticException();return v;} // keeps every step small
    }
    /** Split evenly: [total] over [parts], leftover cents on the first parts (a negative total too: the parts always add up to it). */
    public static long[] splitEvenly(long total,int parts){long[] r=new long[parts];long sign=total<0?-1:1,size=Math.abs(total),base=size/parts,left=size-base*parts;for(int i=0;i<parts;i++)r[i]=sign*(base+(i<left?1:0));return r;}
    /** Fill remaining: what's left of [total] after [others]. */
    public static long remaining(long total,long... others){long n=total;for(long o:others)n-=o;return n;}
    public static long cents(String input){long v=parse(input);if(v<=0)throw new IllegalArgumentException("Enter a positive amount.");return v;}
    public Category category(String id){for(Category c:categories)if(c.id.equals(id))return c;return null;}
    public Account account(String id){for(Account a:accounts)if(a.id.equals(id))return a;return null;}
    public long assigned(Category c,YearMonth m){return c.assigned.getOrDefault(m.toString(),0L);}
    public long activity(Category c,YearMonth m){if(c.payment())return paymentActivity(c,m);long n=0;for(Entry e:entries)if(e.date.startsWith(m.toString()))n+=budgetIn(e,c.id);return n;}
    // Tracking accounts (savings held elsewhere, investments, a house, a mortgage) are off budget: their transactions never
    // touch To budget, categories or spending; only Net worth counts them. Money crossing between a budget account and a
    // tracking account does count: going out of the budget it's spending from a category (an extra mortgage payment, an
    // investment deposit); coming in it's income to To budget.
    public static final String TRACKING_NO_CATEGORY="Tracking accounts are off budget: their transactions have no category.";
    /** A transfer between a budget account and a tracking account. */
    public boolean crossing(Entry e){if(!e.transfer())return false;Account a=account(e.account),to=account(e.destination);return a!=null&&to!=null&&a.tracking()!=to.tracking();}
    /** The budget account [e] counts in: its own account (null for a tracking one), a crossing transfer's budget side, or null for a transfer within the budget. */
    public Account budgetAccount(Entry e){Account a=account(e.account);if(!e.transfer())return a!=null&&!a.tracking()?a:null;if(!crossing(e))return null;return a.tracking()?account(e.destination):a;}
    /** [e]'s amount as the budget sees it (from its budget account's side; 0 when off budget). */
    public long budgetAmount(Entry e){Account a=budgetAccount(e);return a==null?0:a.id.equals(e.account)?e.amount:-e.amount;}
    /** The part of [e] the budget sees in category [id] ("" = To budget). Money in from a tracking account goes to To budget. */
    public long budgetIn(Entry e,String id){
        if(!e.transfer()){long n=e.amountIn(id);if(n==0)return 0;Account a=account(e.account);return a!=null&&a.tracking()?0:n;}
        Account a=budgetAccount(e);if(a==null)return 0;return a.id.equals(e.account)?e.amountIn(id):id.isEmpty()?-e.amount:0;
    }
    private YearMonth first(Category c,YearMonth until){YearMonth first=until;for(String key:c.assigned.keySet())if(YearMonth.parse(key).isBefore(first))first=YearMonth.parse(key);for(Entry e:entries)if(e.touches(c.id)&&YearMonth.from(LocalDate.parse(e.date)).isBefore(first))first=YearMonth.from(LocalDate.parse(e.date));
        if(c.payment()){Account card=account(c.cardAccount);if(card!=null&&YearMonth.from(LocalDate.parse(card.date)).isBefore(first))first=YearMonth.from(LocalDate.parse(card.date));}return first;}
    // Overspending (a negative balance) resets each month: it's taken from To budget. A card payment category's negative
    // balance that is card credit (a refund after the card was paid, a reward sent to To budget) carries on instead.
    public long available(Category c,YearMonth month){long n=0,start=0;for(YearMonth m=first(c,month);!m.isAfter(month);m=m.plusMonths(1)){n=n<0&&c.payment()?Math.max(n,-cardCredit(c,m.minusMonths(1),start)):Math.max(0,n);start=n;n+=assigned(c,m)+activity(c,m);}return n;}
    /**
     * How far payment category [pc] may stay below zero after [m] (which it started at [start]): the card's credit, what
     * already carried, and its To budget parts in [m]. Below that, more was paid than was set aside: overspending.
     */
    private long cardCredit(Category pc,YearMonth m,long start){Account card=account(pc.cardAccount);if(card==null)return 0;long freed=0;for(Entry e:entries)if((e.account.equals(card.id)||e.destination.equals(card.id))&&e.date.startsWith(m.toString())&&budgetAccount(e)==card)freed+=budgetIn(e,"");return Math.max(0,balanceAt(card,m))+Math.max(0,-start)+Math.max(0,freed);}
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
        String end=month.atEndOfMonth().toString();long n=0;for(Account a:accounts)if(a.cash()&&a.date.compareTo(end)<=0)n+=a.opening;
        for(Entry e:entries){if(e.date.compareTo(end)>0)continue;Account a=account(e.account);if(a==null)continue;
            if(e.transfer()){Account to=account(e.destination);if(to!=null&&a.cash()!=to.cash())n+=a.cash()?e.amount:-e.amount;} // in or out of cash (to a card or a tracking account)
            else if(a.cash())n+=e.amount;}
        return n;
    }
    /** Each of [a]'s transactions (by id) -> the account's balance after it, cleared or not, transfers included; same-day ones in the order added. */
    public Map<String,Long> runningBalances(Account a){
        List<Entry> list=new ArrayList<>();for(int i=entries.size()-1;i>=0;i--){Entry e=entries.get(i);if(e.account.equals(a.id)||e.destination.equals(a.id))list.add(e);} // entries are kept newest first
        list.sort(Comparator.comparing(e->e.date));long n=a.opening;Map<String,Long> map=new HashMap<>();for(Entry e:list){if(e.account.equals(a.id))n+=e.amount;if(e.destination.equals(a.id))n-=e.amount;map.put(e.id,n);}return map;
    }
    /** [a]'s balance at the end of [day] (YYYY-MM-DD), cleared or not. */
    public long balanceOn(Account a,String day){long n=a.opening;for(Entry e:entries){if(e.date.compareTo(day)>0)continue;if(e.account.equals(a.id))n+=e.amount;if(e.destination.equals(a.id))n-=e.amount;}return n;}
    public long balance(Account a,boolean clearedOnly){long n=a.opening;for(Entry e:entries)if(!clearedOnly||e.cleared){if(e.account.equals(a.id))n+=e.amount;if(e.destination.equals(a.id))n-=e.amount;}return n;}
    /** To budget: cash less what categories hold. Overspending on a card is card debt, so it doesn't count here. */
    public long ready(YearMonth m){long n=cash(m);for(Category c:categories)n-=available(c,m)+creditOverspent(c,m);return n;}
    // Credit cards in an envelope system. Spending on a card from a category with money moves that money to the card's
    // payment category, ready to pay the bill; spending beyond what the category has is credit overspending: it shows
    // in the category this month and then becomes card debt, without touching To budget. A payment (a transfer
    // from a cash account to the card) uses the payment category's money.
    /** Net spending (refunds negative) in [c] on credit card [card] in [m]; card null = on every card. */
    public long creditSpent(Category c,YearMonth m,Account card){long n=0;for(Entry e:entries){if(!e.touches(c.id)||!e.date.startsWith(m.toString()))continue;Account a=budgetAccount(e);if(a!=null&&a.credit()&&(card==null||a==card))n-=budgetIn(e,c.id);}return n;}
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
        for(Entry e:entries)if(e.transfer()&&e.destination.equals(card.id)&&e.date.startsWith(m.toString())){Account from=account(e.account);if(from!=null&&from.cash())n+=e.amount;}
        // A cash advance (card to a cash account) is borrowed money: it's set aside here to repay, so To budget doesn't grow while the card owes more.
        for(Entry e:entries)if(e.transfer()&&e.account.equals(card.id)&&e.date.startsWith(m.toString())){Account to=account(e.destination);if(to!=null&&to.cash())n-=e.amount;}
        for(Entry e:entries)if((e.account.equals(card.id)||e.destination.equals(card.id))&&e.date.startsWith(m.toString())&&budgetAccount(e)==card)n-=budgetIn(e,""); // money in from a tracking account counts like a refund to To budget
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
        if(c.targetType.equals("Monthly")||c.targetType.equals("Debt"))return Math.max(0,c.target-assigned(c,m)); // a debt payment: a fixed amount each month
        // Weekly: the amount for each chosen weekday in the month; refill counts what's left from last month (not in future months, as Refill).
        if(c.targetType.equals("Weekly"))return Math.max(0,weeklyGoal(c,m)-(c.weeklyRefill&&!m.isAfter(YearMonth.now())?carried(c,m):0)-assigned(c,m));
        // By date: what's still to save (less what came in from earlier months), spread evenly over the months up to the due month.
        if(c.targetType.equals("ByDate")){LocalDate due=dueFor(c,m);if(due==null)return 0;long months=ChronoUnit.MONTHS.between(m,YearMonth.from(due))+1,left=Math.max(0,c.target-carried(c,m));return Math.max(0,(left+months-1)/months-assigned(c,m));}
        // Balance with a due month: like by date, what's still to save (less what came in) over the months left, less this month's Assigned.
        if(c.targetType.equals("Balance")&&!c.due.isEmpty()){YearMonth due=YearMonth.parse(c.due);long remaining=Math.max(0,c.target-carried(c,m));long months=Math.max(1,ChronoUnit.MONTHS.between(m,due)+1);return Math.max(0,(remaining+months-1)/months-assigned(c,m));}
        long base=c.targetType.equals("Refill")?(m.isAfter(YearMonth.now())?0:Math.max(0,available(c,m.minusMonths(1))))+assigned(c,m):available(c,m);
        return Math.max(0,c.target-base);
    }
    public long spending(YearMonth m){long n=0;for(Entry e:entries)if(e.date.startsWith(m.toString()))n-=budgetAmount(e)-budgetIn(e,"");return n;}
    public long income(YearMonth m){long n=0;for(Entry e:entries)if(e.date.startsWith(m.toString()))n+=budgetIn(e,"");return n;}
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
        for(Account a:accounts)if(a.cash()&&a.opening>0&&!LocalDate.parse(a.date).isAfter(until))events.add(new long[]{LocalDate.parse(a.date).toEpochDay(),a.opening});
        for(Entry e:entries){if(e.amount==0||LocalDate.parse(e.date).isAfter(until))continue;Account a=account(e.account);if(a==null)continue;long day=LocalDate.parse(e.date).toEpochDay();
            if(e.transfer()){Account to=account(e.destination);if(to!=null&&a.cash()!=to.cash())events.add(new long[]{day,a.cash()?e.amount:-e.amount});}else if(a.cash())events.add(new long[]{day,e.amount});}
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
    // Reports count budget accounts only (tracking accounts are in Net worth only); a transfer within the budget isn't spending
    // or income, and a card payment category's activity isn't spending.
    /** Net spending (refunds take off) per spending category in the months [from] to [to]: category id -> cents. Card payment categories are left out. */
    public Map<String,Long> spentBy(YearMonth from,YearMonth to){
        String start=from.atDay(1).toString(),end=to.atEndOfMonth().toString();Map<String,Long> map=new LinkedHashMap<>();
        for(Entry e:entries){if(e.date.compareTo(start)<0||e.date.compareTo(end)>0)continue;Set<String> ids=new LinkedHashSet<>();if(e.split())for(Split p:e.splits)ids.add(p.category);else ids.add(e.category);
            for(String id:ids){Category c=id.isEmpty()?null:category(id);if(c==null||c.payment())continue;long n=-budgetIn(e,id);if(n!=0)map.merge(id,n,Long::sum);}}
        return map;
    }
    /** A part of the spending breakdown: a category, a group, or Other (what's beyond the biggest SLICES). tenths: its share in tenths of a percent. */
    public static final class Slice { public final String name;public final boolean other;public final List<String> ids=new ArrayList<>();public long amount;public int tenths; Slice(String name,boolean other){this.name=name;this.other=other;} }
    public static final int SLICES=7;
    /**
     * Spending in [from] to [to] by category (or by group), biggest first. A category with more refunds than spending is
     * left out of both views (see refunds), so they add up to the same total.
     * Beyond the biggest SLICES, the rest fold into Other (last). Shares add up to exactly 100.0% (largest remainder).
     */
    public List<Slice> breakdown(YearMonth from,YearMonth to,boolean byGroup){
        Map<String,Long> spent=spentBy(from,to);LinkedHashMap<String,Slice> map=new LinkedHashMap<>();
        for(Category c:categories){long n=spent.getOrDefault(c.id,0L);if(n<=0)continue;String key=byGroup?c.group.trim().toLowerCase(Locale.ROOT):c.id;Slice s=map.get(key);if(s==null)map.put(key,s=new Slice(byGroup?c.group.trim():c.name,false));s.ids.add(c.id);s.amount+=n;}
        List<Slice> list=new ArrayList<>(map.values());list.removeIf(s->s.amount<=0);list.sort((a,b)->Long.compare(b.amount,a.amount));
        if(list.size()>SLICES){Slice other=new Slice("Other",true);for(Slice s:list.subList(SLICES,list.size())){other.ids.addAll(s.ids);other.amount+=s.amount;}list=new ArrayList<>(list.subList(0,SLICES));list.add(other);}
        long total=0;for(Slice s:list)total+=s.amount;if(total<=0)return list;int given=0;long[] left=new long[list.size()];
        for(int i=0;i<list.size();i++){Slice s=list.get(i);s.tenths=(int)(s.amount*1000/total);left[i]=s.amount*1000%total;given+=s.tenths;}
        while(given<1000){int best=0;for(int i=1;i<left.length;i++)if(left[i]>left[best])best=i;list.get(best).tenths++;left[best]=-1;given++;}
        return list;
    }
    /** A breakdown's total: spending in categories that spent more than they got back. Less refunds(), it's the period's net spending. */
    public static long total(List<Slice> slices){long n=0;for(Slice s:slices)n+=s.amount;return n;}
    /** What categories with more refunds than spending got back in [from] to [to] (left out of breakdown). */
    public long refunds(YearMonth from,YearMonth to){long n=0;for(long v:spentBy(from,to).values())if(v<0)n-=v;return n;}
    /** Spending categories (not card payments) in [group], any capitals. */
    public List<String> groupIds(String group){List<String> ids=new ArrayList<>();for(Category c:categories)if(!c.payment()&&c.group.trim().equalsIgnoreCase(group.trim()))ids.add(c.id);return ids;}
    /** Spending trend: net spending in categories [ids] each month of the [months] months up to [last], oldest first; a month with more refunds than spending is $0. */
    public long[] trend(Collection<String> ids,YearMonth last,int months){long[] r=new long[months];for(int i=0;i<months;i++){YearMonth m=last.minusMonths(months-1-i);Map<String,Long> s=spentBy(m,m);long n=0;for(String id:ids)n+=s.getOrDefault(id,0L);r[i]=Math.max(0,n);}return r;}
    /** The average of [values], to the cent, half up. */
    public static long average(long[] values){if(values.length==0)return 0;long n=0;for(long v:values)n+=v;return BigDecimal.valueOf(n).divide(BigDecimal.valueOf(values.length),0,java.math.RoundingMode.HALF_UP).longValueExact();}
    /** A row of the income and expense table: an amount per month; group: a group's subtotal row. */
    public static final class Row { public final String name;public final boolean group;public final long[] amounts; Row(String name,boolean group,int months){this.name=name;this.group=group;amounts=new long[months];}
        public long total(){long n=0;for(long v:amounts)n+=v;return n;} public long average(){return Budget.average(amounts);} boolean empty(){for(long v:amounts)if(v!=0)return false;return true;} }
    /** Income by payee and expenses by category (each group's subtotal row before its categories), month by month, with totals and net (income - expenses). */
    public static final class Table { public final YearMonth[] months;public final List<Row> income=new ArrayList<>(),expenses=new ArrayList<>();public final Row incomeTotal,expenseTotal,net;
        Table(YearMonth from,int n){months=new YearMonth[n];for(int i=0;i<n;i++)months[i]=from.plusMonths(i);incomeTotal=new Row("Total income",false,n);expenseTotal=new Row("Total expenses",false,n);net=new Row("Net",false,n);} }
    /** The income and expense table for the [months] months from [from]. Income is money into To budget (net, by payee, biggest first); expenses are net of refunds, in plan order. */
    public Table incomeExpense(YearMonth from,int months){
        Table t=new Table(from,months);String start=from.atDay(1).toString(),end=from.plusMonths(months-1).atEndOfMonth().toString();LinkedHashMap<String,Row> payees=new LinkedHashMap<>();
        for(Entry e:entries){if(e.date.compareTo(start)<0||e.date.compareTo(end)>0)continue;long n=budgetIn(e,"");if(n==0)continue;int i=(int)ChronoUnit.MONTHS.between(from,YearMonth.from(LocalDate.parse(e.date)));Row r=payees.get(key(e.payee));if(r==null)payees.put(key(e.payee),r=new Row(e.payee.trim(),false,months));r.amounts[i]+=n;} // newest first: a payee's newest spelling
        for(Row r:payees.values())if(!r.empty())t.income.add(r);t.income.sort((a,b)->Long.compare(b.total(),a.total()));
        List<Map<String,Long>> spent=new ArrayList<>();for(YearMonth m:t.months)spent.add(spentBy(m,m));
        LinkedHashMap<String,List<Category>> groups=new LinkedHashMap<>();for(Category c:categories)if(!c.payment())groups.computeIfAbsent(c.group.trim().toLowerCase(Locale.ROOT),k->new ArrayList<>()).add(c);
        for(List<Category> list:groups.values()){Row g=new Row(list.get(0).group.trim(),true,months);List<Row> rows=new ArrayList<>();
            for(Category c:list){Row r=new Row(c.name,false,months);for(int i=0;i<months;i++){r.amounts[i]=spent.get(i).getOrDefault(c.id,0L);g.amounts[i]+=r.amounts[i];}if(!r.empty())rows.add(r);}
            if(!rows.isEmpty()){t.expenses.add(g);t.expenses.addAll(rows);}}
        for(int i=0;i<months;i++){for(Row r:t.income)t.incomeTotal.amounts[i]+=r.amounts[i];for(Row r:t.expenses)if(r.group)t.expenseTotal.amounts[i]+=r.amounts[i];t.net.amounts[i]=t.incomeTotal.amounts[i]-t.expenseTotal.amounts[i];}
        return t;
    }
    /** Spending pace: [spent]% of the month's money spent with [elapsed]% of the month gone. */
    public static final class Pace { public final int spent,elapsed; Pace(int spent,int elapsed){this.spent=spent;this.elapsed=elapsed;} }
    public static final long PACE_MIN=1000;public static final int PACE_MARGIN=20;
    /**
     * Whether [c] is spending faster than the month on [today] (in [m], the current month only): it has a target or money assigned,
     * at least $10 is spent (net of refunds), and the share spent is at least the share of the month gone (counting today) plus
     * 20 points. The month's money is what came in from last month plus what's assigned; with neither, a monthly target's amount.
     * Nothing when it's already overspent (that shows on its own) or for a card payment category. Null when there's no hint.
     */
    public Pace pace(Category c,YearMonth m,LocalDate today){
        // Only for everyday spending (a Refill or Weekly target, or none): a bill or debt paid once a month (Set aside, Debt,
        // By date) or savings toward a balance is all spent at once, which isn't spending too fast.
        if(c.targetType.equals("Monthly")||c.targetType.equals("Debt")||c.targetType.equals("ByDate")||c.targetType.equals("Balance"))return null;
        if(c.payment()||!YearMonth.from(today).equals(m))return null;boolean targeted=c.target>0&&!c.snoozed.equals(m.toString());if(!targeted&&assigned(c,m)<=0)return null;
        long base=carried(c,m)+assigned(c,m);if(base<=0&&targeted)base=c.targetType.equals("Weekly")?weeklyGoal(c,m):c.targetType.equals("Refill")||c.targetType.equals("Monthly")||c.targetType.equals("Debt")?c.target:0;
        long spent=spent(c,m);if(base<=0||spent<PACE_MIN||available(c,m)<0)return null;
        long day=today.getDayOfMonth(),days=m.lengthOfMonth();
        if(100*spent*days<(100*day+PACE_MARGIN*days)*base)return null; // spent/base >= day/days + 20%, in whole numbers
        return new Pace((int)Math.min(999,(200*spent+base)/(2*base)),(int)((200*day+days)/(2*days)));
    }
    // Home: categories pinned as priorities, and what's due soon.
    public static final int PINS=5;
    /** Pinned categories (at most PINS), in plan order. A hidden one keeps its pin but doesn't show or count. */
    public List<Category> pinned(){List<Category> list=new ArrayList<>();for(Category c:categories)if(c.pinned&&!c.hidden&&list.size()<PINS)list.add(c);return list;}
    public void pin(Category c,boolean on){if(on&&!c.pinned&&pinned().size()>=PINS)throw new IllegalArgumentException("Pin up to "+PINS+" categories to Home. Unpin one first.");c.pinned=on;}
    /** Hides or unhides [c]. Unhiding a pinned one when Home already has PINS unpins it; returns true then. */
    public boolean setHidden(Category c,boolean hidden){boolean unpin=!hidden&&c.hidden&&c.pinned&&pinned().size()>=PINS;if(unpin)c.pinned=false;c.hidden=hidden;return unpin;}
    /** Scheduled transactions and Planner's bills due by [today] + [days], overdue ones too, soonest first. */
    public List<Scheduled> dueWithin(LocalDate today,int days){String until=today.plusDays(days).toString();List<Scheduled> list=new ArrayList<>();for(Scheduled s:planned())if(s.next.compareTo(until)<=0)list.add(s);list.sort(Comparator.comparing(s->s.next));return list;}
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
        for(Rule r:rules)if(r.category.equals(c.id))r.category=into==null?"":into.id;rules.removeIf(r->r.rename.isEmpty()&&r.category.isEmpty()); // an import rule left with nothing to do goes
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
    public long averageSpent(Category c,YearMonth m){long n=0;for(int i=1;i<=3;i++)n+=spent(c,m.minusMonths(i));return third(n);}
    /** Average assigned over the 3 full months before [m]. */
    public long averageAssigned(Category c,YearMonth m){long n=0;for(int i=1;i<=3;i++)n+=assigned(c,m.minusMonths(i));return third(n);}
    private static long third(long n){return BigDecimal.valueOf(n).divide(BigDecimal.valueOf(3),0,java.math.RoundingMode.HALF_UP).longValueExact();} // to the cent, half up
    /** Change that brings Available to $0 (adds what's overspent, or returns what's there). */
    /** 0 when assign wouldn't take it: in a future month only that month's Assigned can be returned. */
    /** A card payment category below zero by card credit is fine: only its overspending is offered (see toCover). */
    public long resetAvailableChange(Category c,YearMonth m){long a=available(c,m),change=c.payment()&&a<0?toCover(c,m):-a;return m.isAfter(YearMonth.now())&&assigned(c,m)+change<0?0:change;}
    /** Change that puts Assigned at 0, or as near as the rules allow (money already spent can't be returned). */
    public long resetChange(Category c,YearMonth m){long a=assigned(c,m);return a<=0?-a:-Math.min(a,Math.max(0,available(c,m)));}
    // Payees: newest first, and the last transaction with one (for its category). Hidden payees aren't suggested.
    public List<String> payees(){List<String> list=allPayees();list.removeIf(this::hiddenPayee);return list;}
    /** Every payee (hidden ones too), newest first, each once (its newest spelling); upcoming transactions' too. */
    public List<String> allPayees(){List<Entry> ordered=new ArrayList<>(entries);ordered.sort((a,b)->b.date.compareTo(a.date));LinkedHashMap<String,String> seen=new LinkedHashMap<>();for(Entry e:ordered)if(!e.transfer())seen.putIfAbsent(key(e.payee),e.payee.trim());for(Scheduled s:scheduled)seen.putIfAbsent(key(s.payee),s.payee.trim());return new ArrayList<>(seen.values());}
    private static String key(String payee){return payee.trim().toLowerCase(Locale.ROOT);}
    // Payee tools: rename, merge, hide from suggestions.
    /** Payees left out of suggestions (lower case). Their transactions stay. */
    public final Set<String> hiddenPayees=new TreeSet<>();
    public boolean hiddenPayee(String payee){return hiddenPayees.contains(key(payee));}
    public void hidePayee(String payee,boolean hide){if(hide)hiddenPayees.add(key(payee));else hiddenPayees.remove(key(payee));}
    public static final int PAYEE_MAX=80;
    /** Renames [from] (any capitals) to [to] on every transaction and upcoming one, and in import rules that rename to it; returns how many transactions changed. A hidden payee stays hidden unless [to] is already in use. */
    public int renamePayee(String from,String to){
        String t=to==null?"":to.trim();if(t.isEmpty())throw new IllegalArgumentException("Enter the payee's new name.");if(t.length()>PAYEE_MAX)throw new IllegalArgumentException("Keep a payee's name to "+PAYEE_MAX+" characters.");
        boolean existing=!key(from).equals(key(t))&&allPayees().stream().anyMatch(p->key(p).equals(key(t)));int n=0;
        for(Entry e:entries)if(!e.transfer()&&key(e.payee).equals(key(from))){if(e.bankPayee.isEmpty())e.bankPayee=statementPayee(e);e.payee=t;n++;}for(Scheduled s:scheduled)if(key(s.payee).equals(key(from))){s.payee=t;n++;}for(Rule r:rules)if(key(r.rename).equals(key(from)))r.rename=t;
        if(hiddenPayees.remove(key(from))&&!existing)hiddenPayees.add(key(t));return n;
    }
    /** Merges [payees] into [keep] (spelled as given): each is renamed to it. Returns the transactions changed. */
    public int mergePayees(Collection<String> payees,String keep){int n=0;for(String p:payees)if(!key(p).equals(key(keep)))n+=renamePayee(p,keep);return n;}
    // Import rules: when a statement's payee contains some text, rename it and/or give it a category. The first match wins.
    public static final class Rule { public String contains,rename="",category=""; public Rule(String contains,String rename,String category){this.contains=contains;this.rename=rename;this.category=category;} }
    public final List<Rule> rules=new ArrayList<>();
    /** The first rule whose text is in [payee] (ignoring capitals), or null. */
    public Rule rule(String payee){String p=payee.toLowerCase(Locale.ROOT);for(Rule r:rules)if(!r.contains.trim().isEmpty()&&p.contains(r.contains.trim().toLowerCase(Locale.ROOT)))return r;return null;}
    public void validate(Rule r){validate(r,null);}
    /** Checks [r], which replaces [old] (null for a new rule). Rules are known by their text, so two can't share it. */
    public void validate(Rule r,Rule old){r.contains=r.contains.trim();r.rename=r.rename.trim();if(r.contains.isEmpty())throw new IllegalArgumentException("Enter the text the payee contains.");if(r.rename.isEmpty()&&r.category.isEmpty())throw new IllegalArgumentException("Choose a new name, a category, or both.");
        for(Rule o:rules)if(o!=r&&o!=old&&o.contains.trim().equalsIgnoreCase(r.contains))throw new IllegalArgumentException("There's already a rule for that text. Edit that one instead.");
        if(r.rename.length()>PAYEE_MAX)throw new IllegalArgumentException("Keep a payee's name to "+PAYEE_MAX+" characters.");Category c=category(r.category);if(!r.category.isEmpty()&&(c==null||c.payment()))throw new IllegalArgumentException("Choose a spending category.");}
    // Flags: a colour per transaction, each colour with an optional name (set once in Settings).
    public static final String[] FLAGS={"None","Red","Orange","Yellow","Green","Blue","Purple"};
    public final String[] flagNames={"","","","","","",""};
    /** "Green" or "Green · Tax". */
    public String flagLabel(int flag){if(flag<=0||flag>=FLAGS.length)return FLAGS[0];String n=flagNames[flag].trim();return n.isEmpty()?FLAGS[flag]:FLAGS[flag]+" · "+n;}
    // Review: transactions imported from a statement wait to be approved. A transfer is one transaction in both accounts, so approving it approves both sides.
    public List<Entry> toReview(){List<Entry> list=new ArrayList<>();for(Entry e:entries)if(!e.approved)list.add(e);list.sort((a,b)->b.date.compareTo(a.date));return list;}
    public void approve(Entry e){e.approved=true;}
    public int approveAll(){int n=0;for(Entry e:entries)if(!e.approved){e.approved=true;n++;}return n;}
    /** Transactions search and filters; empty or -1 means any. Filters combine. */
    public static final class Filter {
        public String text="",account="",category="",from="",to=""; // from/to: ISO dates, inclusive
        public int flag=-1,cleared=-1; // flag: 0 = no flag; cleared: 1 = cleared, 0 = uncleared
        public boolean any(){return !text.trim().isEmpty()||!account.isEmpty()||!category.isEmpty()||!from.isEmpty()||!to.isEmpty()||flag>=0||cleared>=0;}
    }
    /** Whether [e] passes [f]. Text matches the payee, note, account names or category names (any capitals). */
    public boolean matches(Filter f,Entry e){
        if(!f.account.isEmpty()&&!e.account.equals(f.account)&&!e.destination.equals(f.account))return false;
        if(!f.category.isEmpty()&&!e.touches(f.category))return false;
        if(f.flag>=0&&e.flag!=f.flag)return false;if(f.cleared>=0&&e.cleared!=(f.cleared==1))return false;
        if(!f.from.isEmpty()&&e.date.compareTo(f.from)<0)return false;if(!f.to.isEmpty()&&e.date.compareTo(f.to)>0)return false;
        String t=f.text.trim().toLowerCase(Locale.ROOT);return t.isEmpty()||searchText(e).toLowerCase(Locale.ROOT).contains(t);
    }
    /** [f]'s transactions, newest first. */
    public List<Entry> filter(Filter f){List<Entry> list=new ArrayList<>();for(Entry e:entries)if(matches(f,e))list.add(e);list.sort((a,b)->b.date.compareTo(a.date));return list;}
    private String searchText(Entry e){StringBuilder s=new StringBuilder(e.payee).append(' ').append(e.memo);Account a=account(e.account),to=account(e.destination);if(a!=null)s.append(' ').append(a.name);if(to!=null)s.append(' ').append(to.name);
        if(e.split())for(Split p:e.splits){Category c=category(p.category);s.append(' ').append(c==null?"To budget":c.name).append(' ').append(p.memo);}
        else{Category c=category(e.category);Account own=account(e.account);if(c!=null||e.transfer()||own==null||!own.tracking())s.append(' ').append(c!=null?c.name:e.transfer()?"Transfer":"To budget");}return s.toString();} // a tracking account's own entry has no category
    // Tracking accounts and loans.
    /** Adds a tracking account: an asset worth [value], or a debt owing [value] (a positive amount; saved as a negative balance). */
    public Account addTracking(String name,String date,long value,boolean liability){if(value<0)throw new IllegalArgumentException("Enter the amount as a positive number.");Account a=new Account(name,date,liability?-value:value);a.type="tracking";a.liability=liability;accounts.add(a);return a;}
    /** A value update: a cleared transaction (no category) that brings [a]'s balance on [today] (its date) to [value] (owed, for a debt), or null when it's already there. */
    public Entry valueUpdate(Account a,long value,String today){if(!a.tracking())throw new IllegalArgumentException("Only a tracking account takes value updates.");long difference=(a.liability?-value:value)-balanceOn(a,today);if(difference==0)return null;Entry e=new Entry("Balance update","",a.id,today,difference);e.cleared=true;return e;} // from the balance on that date: later entries still count after it
    public static final String[] FREQUENCIES={"Weekly","Every 2 weeks","Monthly"};
    /** [amount] paid at [frequency] as a monthly amount (52 weeks or 26 fortnights a year), to the cent, half up. */
    public static long perMonth(long amount,String frequency){int a=frequency.equals("Weekly")?52:frequency.equals("Every 2 weeks")?26:12;return BigDecimal.valueOf(amount).multiply(BigDecimal.valueOf(a)).divide(BigDecimal.valueOf(12),0,java.math.RoundingMode.HALF_UP).longValueExact();}
    /** A payoff plan: months to pay off and the interest paid; covers is false when the payment doesn't cover the first month's interest, finished false when it takes over 100 years. */
    public static final class Payoff { public final int months;public final long interest;public final boolean covers,finished; Payoff(int months,long interest,boolean covers,boolean finished){this.months=months;this.interest=interest;this.covers=covers;this.finished=finished;} }
    public static final int PAYOFF_MAX_MONTHS=1200;
    /**
     * Paying off [balance] (owed, in cents) at [annualRatePercent] a year, compounded monthly, with [payment] + [extra] a
     * month: each month's interest is the balance x rate / 12, rounded half up to the cent; the last payment is what's left.
     */
    public static Payoff payoff(long balance,BigDecimal annualRatePercent,long payment,long extra){
        if(balance<=0)return new Payoff(0,0,true,true);long pay=payment+extra,interest=0;int months=0;
        while(balance>0){long i=BigDecimal.valueOf(balance).multiply(annualRatePercent).divide(BigDecimal.valueOf(1200),0,java.math.RoundingMode.HALF_UP).longValueExact();
            if(pay<=i)return new Payoff(months,interest,months>0,false); // the payment doesn't cover the interest: it never pays off
            if(months>=PAYOFF_MAX_MONTHS)return new Payoff(months,interest,true,false);
            balance+=i;interest+=i;balance-=Math.min(pay,balance);months++;}
        return new Payoff(months,interest,true,true);
    }
    /** "2 years 3 months", "1 year", "5 months". */
    public static String duration(int months){int y=months/12,m=months%12;String years=y==0?"":y+(y==1?" year":" years"),rest=m==0?"":m+(m==1?" month":" months");return y==0&&m==0?"0 months":(years+" "+rest).trim();}
    public Entry lastForPayee(String payee){Entry best=null;for(Entry e:entries)if(!e.transfer()&&e.payee.equalsIgnoreCase(payee.trim())&&(best==null||e.date.compareTo(best.date)>0))best=e;return best;}
    /** The newest transaction imported with the statement payee text [bank] (any capitals), or null: its payee may have been renamed since. */
    public Entry lastForBankPayee(String bank){Entry best=null;for(Entry e:entries)if(!e.transfer()&&!e.bankPayee.isEmpty()&&e.bankPayee.equalsIgnoreCase(bank.trim())&&(best==null||e.date.compareTo(best.date)>0))best=e;return best;}
    // Notes: the ones used with [payee] first, then the rest; newest first, each once, at most 50. Automatic notes are left out.
    public static final String IMPORTED="Imported"; // the note on rows from a bank statement (CsvImport)
    /**
     * [e]'s statement payee text: its bankPayee, else for a row imported before bankPayee was kept (0.0.6; its note is
     * still IMPORTED) its payee, which is what re-imports matched it on; "" for other transactions.
     */
    public static String statementPayee(Entry e){if(!e.bankPayee.isEmpty())return e.bankPayee;if(e.transfer()||!e.memo.trim().equals(IMPORTED))return "";String p=e.payee.trim();return p.length()>PAYEE_MAX?p.substring(0,PAYEE_MAX):p;}
    public List<String> memos(String payee){
        List<Entry> ordered=new ArrayList<>(entries);ordered.sort((a,b)->b.date.compareTo(a.date));String p=payee==null?"":payee.trim();LinkedHashMap<String,String> seen=new LinkedHashMap<>();
        for(int pass=0;pass<2;pass++)for(Entry e:ordered){String m=e.memo.trim();boolean theirs=!p.isEmpty()&&e.payee.trim().equalsIgnoreCase(p);if(m.isEmpty()||m.equals(IMPORTED)||theirs!=(pass==0))continue;seen.putIfAbsent(m.toLowerCase(Locale.ROOT),m);}
        List<String> list=new ArrayList<>(seen.values());return list.subList(0,Math.min(50,list.size()));
    }
    // Groups: each once (first spelling), in plan order. A card payment category's group isn't offered: it's for cards.
    public List<String> groups(){LinkedHashMap<String,String> seen=new LinkedHashMap<>();for(Category c:categories)if(!c.payment()&&!c.group.trim().isEmpty())seen.putIfAbsent(c.group.trim().toLowerCase(Locale.ROOT),c.group.trim());return new ArrayList<>(seen.values());}
    /** The group [typed] names, spelled as it already is ("bills" -> "Bills"), not counting [editing]'s own; else [typed] trimmed. */
    public String existingGroup(String typed,Category editing){String t=typed.trim();for(Category c:categories)if(c!=editing&&c.group.trim().equalsIgnoreCase(t))return c.group.trim();return t;}
    public String existingGroup(String typed){return existingGroup(typed,null);}
    /** Every transaction as CSV for spreadsheets, newest date first. Export only: a backup is what restores. */
    public String csv(){
        StringBuilder out=new StringBuilder("Date,Payee,Category,Group,Account,Transfer to,Amount,Note,Cleared\r\n");List<Entry> ordered=new ArrayList<>(entries);ordered.sort((a,b)->b.date.compareTo(a.date));
        for(Entry e:ordered){Account a=account(e.account),to=account(e.destination);
            // A split is one row per part (its note, or the transaction's), so spreadsheet totals by category add up.
            List<Split> parts=e.split()?e.splits:Collections.singletonList(new Split(e.category,e.amount));
            for(Split p:parts){Category c=category(p.category);String note=e.split()&&!p.memo.isEmpty()?p.memo:e.memo;
                boolean noCategory=(e.transfer()||a!=null&&a.tracking())&&c==null; // a transfer (unless it pays into a tracking account from a category), or a tracking account's own entry
                out.append(String.join(",",e.date,cell(e.payee),cell(noCategory?"":c==null?"To budget":c.name),cell(noCategory||c==null?"":c.group),cell(a==null?"":a.name),cell(to==null?"":to.name),BigDecimal.valueOf(p.amount,2).toPlainString(),cell(note),e.cleared?"Yes":"No")).append("\r\n");}}
        return out.toString();
    }
    // A spreadsheet runs text starting with = + - @ as a formula: a leading ' keeps it text. Quoted when needed.
    static String cell(String s){if(s==null)s="";if(!s.isEmpty()&&"=+-@\t\r".indexOf(s.charAt(0))>=0)s="'"+s;return s.matches("(?s).*[,\"\r\n].*")?"\""+s.replace("\"","\"\"")+"\"":s;}
    public void validate(Entry e){
        Account a=account(e.account);if(a==null)throw new IllegalArgumentException("Choose an account.");LocalDate date=LocalDate.parse(e.date);
        if(date.isAfter(LocalDate.now()))throw new IllegalArgumentException("Use today or a past date.");if(e.date.compareTo(a.date)<0)throw new IllegalArgumentException("Transaction date is before this account's opening date.");
        if(e.payee.trim().isEmpty()||e.amount==0)throw new IllegalArgumentException("Enter a payee and a nonzero amount.");
        if(e.transfer()){Account to=account(e.destination);if(to==null||to==a||e.amount>=0)throw new IllegalArgumentException("Choose a different destination account.");if(e.date.compareTo(to.date)<0)throw new IllegalArgumentException("Date is before the destination account's opening date.");
            if(e.split()||e.category.equals(SPLIT))throw new IllegalArgumentException("A transfer can't be split.");
            // Out of the budget into a tracking account: spending, so it needs a category. Anything else has none (money in from tracking goes to To budget).
            if(!a.tracking()&&to.tracking()){if(e.category.isEmpty()||category(e.category)==null)throw new IllegalArgumentException("Choose the category this money comes from: it leaves your budget.");if(category(e.category).payment())throw new IllegalArgumentException(PAY_BY_TRANSFER);}
            else if(!e.category.isEmpty())throw new IllegalArgumentException("Only a transfer out of your budget to a tracking account has a category.");}
        else if(a.tracking()&&!e.category.isEmpty())throw new IllegalArgumentException(TRACKING_NO_CATEGORY);
        else if(e.split()||e.category.equals(SPLIT)){
            if(!e.category.equals(SPLIT)||e.splits.size()<2)throw new IllegalArgumentException("A split needs at least two parts.");long sum=0;
            for(Split p:e.splits){if(p.amount==0)throw new IllegalArgumentException("Give every part of the split an amount.");if(!p.category.isEmpty()&&category(p.category)==null)throw new IllegalArgumentException("Choose a category for every part.");if(!p.category.isEmpty()&&category(p.category).payment())throw new IllegalArgumentException(PAY_BY_TRANSFER);sum+=p.amount;}
            if(sum!=e.amount)throw new IllegalArgumentException("The parts of the split must add up to the total.");
        }
        else if(!e.category.isEmpty()&&category(e.category)==null)throw new IllegalArgumentException("Choose a category.");
        else if(!e.category.isEmpty()&&category(e.category).payment())throw new IllegalArgumentException(PAY_BY_TRANSFER);
    }
}
