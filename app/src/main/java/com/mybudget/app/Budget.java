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
    public static final String SPLIT="split",PAY_BY_TRANSFER="Pay a credit card with a transfer to it. Its payment category takes only that card's interest and fees.";
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
        public boolean reconciled; // cleared and part of a balance checked against the bank (Reconcile): editing it asks first
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
        // An upcoming split (category SPLIT): its parts, as an Entry's; entering it makes a split with the same parts.
        public final List<Split> splits=new ArrayList<>();
        public boolean split(){return !splits.isEmpty();}
        /** The part of this upcoming transaction that goes to category [id] ("" = To budget). */
        public long amountIn(String id){if(split()){long n=0;for(Split s:splits)if(s.category.equals(id))n+=s.amount;return n;}return category.equals(id)?amount:0;}
        public boolean touches(String id){if(split()){for(Split s:splits)if(s.category.equals(id))return true;return false;}return category.equals(id);}
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
    // The budget's one currency (ISO 4217; "AUD" unless chosen in Settings; storage version 6). Amounts are cents whatever it
    // is: changing it converts nothing, it only changes how money is shown. Planner's bills come in only in this currency.
    public static final String DEFAULT_CURRENCY="AUD";
    public String currency=DEFAULT_CURRENCY;
    /** A currency MyBudget can use: one of the currencies this phone knows, but not gold, test or "no currency" codes. */
    public static boolean knownCurrency(String code){return code!=null&&code.matches("[A-Z]{3}")&&!NOT_MONEY.contains(code)&&available().contains(code);}
    /** Hunt 23: a currency a saved budget, a backup or a Planner bill may be in: one this phone knows, or one newer than its
     *  list (VES, SLE, ZWG on an older Android), so a backup made on a newer phone still restores. */
    public static boolean storableCurrency(String code){return knownCurrency(code)||NEWER_CURRENCIES.contains(code);}
    // ISO 4217 codes from 2018 on, which an older phone's list may lack.
    private static final Set<String> NEWER_CURRENCIES=new HashSet<>(Arrays.asList("MRU","STN","VES","UYW","VED","SLE","ZWG","XCG"));
    private static final Set<String> NOT_MONEY=new HashSet<>(Arrays.asList("XAU","XAG","XPT","XPD","XDR","XBA","XBB","XBC","XBD","XSU","XUA","XTS","XXX"));
    private static Set<String> codes;
    // Android makes up a currency for any three letters (Currency.getInstance("ZZZ") works there), so only the listed ones count.
    private static synchronized Set<String> available(){if(codes==null){codes=new HashSet<>();for(Currency c:Currency.getAvailableCurrencies())codes.add(c.getCurrencyCode());}return codes;}
    /** Whether a Planner bill or payment in [sent] can go into a budget in [budget]; none sent (a Planner before currencies) is AUD. */
    public static boolean sameCurrency(String sent,String budget){return (sent==null||sent.trim().isEmpty()?DEFAULT_CURRENCY:sent.trim()).equals(budget);}
    static final String[] COMMON_CURRENCIES={"AUD","NZD","USD","CAD","GBP","EUR","JPY"};
    /**
     * Currencies to choose from: the common ones first, then the others in use today (some country's currency on this phone),
     * A to Z by code. Currency.getAvailableCurrencies() also has old ones (pesetas, 1927 afghanis), so it isn't the list.
     */
    public static List<String> currencyChoices(){List<String> all=new ArrayList<>(Arrays.asList(COMMON_CURRENCIES));TreeSet<String> rest=new TreeSet<>();
        for(Locale l:Locale.getAvailableLocales()){if(l.getCountry().isEmpty())continue;try{Currency c=Currency.getInstance(l);if(c!=null&&!all.contains(c.getCurrencyCode())&&knownCurrency(c.getCurrencyCode()))rest.add(c.getCurrencyCode());}catch(IllegalArgumentException ignored){}}
        all.addAll(rest);return all;}
    /**
     * Money in [code] in [locale]'s number style ("$1,234.56" for AUD in Australia, "1.234,56 €" for EUR in Germany), always
     * with the two decimals stored, even for a currency without cents (JPY): amounts are cents in every currency.
     */
    public static java.text.NumberFormat moneyFormat(String code,Locale locale){java.text.NumberFormat f=java.text.NumberFormat.getCurrencyInstance(locale);
        Currency c;try{c=Currency.getInstance(storableCurrency(code)?code:DEFAULT_CURRENCY);}catch(IllegalArgumentException e){c=Currency.getInstance(DEFAULT_CURRENCY);}f.setCurrency(c);f.setMinimumFractionDigits(2);f.setMaximumFractionDigits(2);return f;}
    public static String money(long cents,java.text.NumberFormat format){return format.format(BigDecimal.valueOf(cents,2));}
    /** The category for a Planner bill: the one chosen here, else its last expense's; "" when not known yet. */
    public String plannerCategory(String billKey){String id=billCategories.get(billKey);Category c=id==null?null:category(id);if(c!=null&&!c.payment())return c.id;Entry last=lastForBill(billKey);c=last==null||last.split()?null:category(last.category);return c==null||c.payment()?"":c.id;}
    private List<Scheduled> planned(){List<Scheduled> all=new ArrayList<>(scheduled);all.addAll(fromPlanner);return all;}
    /** Scheduled transactions whose date has come (on or before [today]), oldest first. */
    public List<Scheduled> due(LocalDate today){List<Scheduled> list=new ArrayList<>();for(Scheduled s:scheduled)if(!LocalDate.parse(s.next).isAfter(today))list.add(s);list.sort(Comparator.comparing(s->s.next));return list;}
    public void validate(Scheduled s){
        Account a=account(s.account);if(a==null)throw new IllegalArgumentException("Choose an account.");LocalDate d=LocalDate.parse(s.next);if(s.next.compareTo(a.date)<0)throw new IllegalArgumentException("The date is before this account's opening date.");
        if(d.isAfter(LocalDate.now().plusYears(5)))throw new IllegalArgumentException("Schedule within the next five years.");
        if(s.payee.trim().isEmpty()||s.amount==0)throw new IllegalArgumentException("Enter a payee and a nonzero amount.");if(a.tracking()&&!s.category.isEmpty())throw new IllegalArgumentException(TRACKING_NO_CATEGORY);
        if(s.split()||s.category.equals(SPLIT))validateParts(s.category,s.splits,s.amount);
        else{if(!s.category.isEmpty()&&category(s.category)==null)throw new IllegalArgumentException("Choose a category.");if(!s.category.isEmpty()&&category(s.category).payment())throw new IllegalArgumentException(PAY_BY_TRANSFER);}
        if(!Arrays.asList(Scheduled.REPEATS).contains(s.repeat))throw new IllegalArgumentException("Choose how often it repeats.");
    }
    /** Enters [s]'s current date as a transaction and moves it to its next date (or removes it). */
    public Entry enter(Scheduled s){return enter(s,"",false);}
    /** As enter(s), with a photo and the Cleared tick (a new repeating transaction dated today or earlier). */
    public Entry enter(Scheduled s,String photo,boolean cleared){return enter(s,photo,cleared,s.next);}
    /** As enter(s,photo,cleared), dated [date] (hunt 25 B1: a statement row paying it a few days early, before its date comes). */
    public Entry enter(Scheduled s,String photo,boolean cleared,String date){Entry e=new Entry(s.payee,s.category,s.account,date,s.amount);e.memo=s.memo;for(Split p:s.splits){Split c=new Split(p.category,p.amount);c.memo=p.memo;e.splits.add(c);}e.billKey=s.billKey;e.photo=photo;e.cleared=cleared;validate(e);entries.add(0,e);changed();advance(s);return e;}
    /** Skips [s]'s current date without a transaction. */
    public void advance(Scheduled s){LocalDate n=s.after(LocalDate.parse(s.next));if(n==null)scheduled.remove(s);else s.next=n.toString();}
    /** Every date [s] falls on in [m] (from its next date on). In the current month, overdue dates from before it count too: they're still to pay. */
    public List<LocalDate> datesIn(Scheduled s,YearMonth m){List<LocalDate> list=new ArrayList<>();LocalDate d=LocalDate.parse(s.next),end=m.atEndOfMonth();boolean now=m.equals(YearMonth.now());for(int i=0;list.size()<400&&i<100000&&d!=null&&!d.isAfter(end);i++){if(now||!d.isBefore(m.atDay(1)))list.add(d);d=s.after(d);}return list;}
    /** Upcoming outflows from [c] in [m]: what scheduled bills will take (each part of an upcoming split in its own category). */
    public long upcoming(Category c,YearMonth m){long n=0;for(Scheduled s:planned()){long part=s.amountIn(c.id);if(part<0)n+=-part*datesIn(s,m).size();}return n;}
    /** What Fund targets assigns: the target's need, or enough for this month's upcoming bills, whichever is more. */
    public long fundNeed(Category c,YearMonth m){return Math.max(needed(c,m),Math.max(0,upcoming(c,m)-availableAfterBills(c,m)));}
    /**
     * Hunt 25 A3: [c]'s Available in [m] less the carried money that bills due from this month until [m] will still take (in
     * a later month, this month's unpaid rent isn't money for next month's). This month and earlier: just Available.
     */
    public long availableAfterBills(Category c,YearMonth m){long a=available(c,m);YearMonth now=YearMonth.now();if(!m.isAfter(now))return a;
        long due=0;for(YearMonth k=now;k.isBefore(m);k=k.plusMonths(1))due+=upcoming(c,k);return a-Math.min(due,carried(c,m));}
    /** The first day in [m] money is needed by: the due day or the first upcoming bill (32 = none, 0 = overdue). */
    public int firstDue(Category c,YearMonth m){int first=dueDay(c,m);for(Scheduled s:planned())if(s.amountIn(c.id)<0)for(LocalDate d:datesIn(s,m))first=Math.min(first,d.isBefore(m.atDay(1))?0:d.getDayOfMonth());return first;}
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
    /** The newest expense from [billKey] by date, or null: its category is suggested next time. */
    public Entry lastForBill(String billKey){if(billKey==null||billKey.isEmpty())return null;Entry best=null; // hunt 23: by date, as an edit no longer moves an old one to the front
        for(Entry e:entries)if(e.billKey.equals(billKey)&&(best==null||e.date.compareTo(best.date)>0))best=e;return best;}
    /** An amount box's text in cents. Quick maths works too: see evaluate. */
    public static long parse(String input) {return evaluate(input);}
    /**
     * Quick maths: amounts (at most two decimals) with + - * / and brackets, the usual order ("45+12.50", "100-20",
     * "3*12.5", "(10+5)/2", "+250"). Each * and / is rounded half up to the cent. Up to $100 million either way.
     */
    public static long evaluate(String input) {
        try {String s=input.replaceAll("\\s","");if(s.indexOf('.')<0)s=s.replaceAll("(\\d),(\\d{1,2})(?!\\d)","$1.$2"); // hunt 26 C8: "12,50" typed on a phone with comma decimals (a comma was never accepted before)
            if(s.length()>100)throw new IllegalArgumentException();Calc c=new Calc(s);BigDecimal v=c.sum();if(c.at!=s.length())throw new IllegalArgumentException();
            long cents=v.movePointRight(2).longValueExact();if(cents < -10_000_000_000L || cents>10_000_000_000L)throw new IllegalArgumentException();return cents;}
        catch(RuntimeException e){throw new IllegalArgumentException("Enter an amount with at most two decimal places (maximum 100 million).");}
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
    public long activity(Category c,YearMonth m){return sums().activity(c,m);}
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
    // Overspending (a negative balance) resets each month: it's taken from To budget. A card payment category's negative
    // balance that is card credit (a refund after the card was paid, a reward sent to To budget) carries on instead (Sums.available).
    public long available(Category c,YearMonth month){return sums().available(c,month);}
    /** Overspending to cover in [m]. A payment category below zero by card credit has nothing to cover: it carries on. */
    public long toCover(Category c,YearMonth m){return sums().toCover(c,m);}
    /** What Pay card fills in: what's set aside this month (below zero is nothing to pay), or what's owed, if less. */
    public long toPay(Account card){Category p=paymentCategory(card);long owed=-balance(card,false),ready=p==null?0:Math.max(0,available(p,YearMonth.now()));return Math.max(0,Math.min(owed,ready));}
    /**
     * Money in cash accounts at the end of [month] (what the plan assigns). Credit cards hold debt, not money: their
     * spending isn't cash (it moves money between categories instead), but a payment from a cash account is. A part
     * sent into To budget on a card (a reward credit, an adjustment) isn't cash either: see paymentActivity.
     */
    public long cash(YearMonth month){return sums().cash(month);}
    /** Each of [a]'s transactions (by id) -> the account's balance after it, cleared or not, transfers included; same-day ones in the order added. */
    public Map<String,Long> runningBalances(Account a){
        List<Entry> list=new ArrayList<>();for(int i=entries.size()-1;i>=0;i--){Entry e=entries.get(i);if(e.account.equals(a.id)||e.destination.equals(a.id))list.add(e);} // entries are kept newest first
        list.sort(Comparator.comparing(e->e.date));long n=a.opening;Map<String,Long> map=new HashMap<>();for(Entry e:list){if(e.account.equals(a.id))n+=e.amount;if(e.destination.equals(a.id))n-=e.amount;map.put(e.id,n);}return map;
    }
    /** [a]'s balance at the end of [day] (YYYY-MM-DD), cleared or not. */
    public long balanceOn(Account a,String day){long n=a.opening;for(Entry e:entries){if(e.date.compareTo(day)>0)continue;if(e.account.equals(a.id))n+=e.amount;if(e.destination.equals(a.id))n-=e.amount;}return n;}
    public long balance(Account a,boolean clearedOnly){return sums().balance(a,clearedOnly);}
    /** To budget: cash less what categories hold. Overspending on a card is card debt, so it doesn't count here. */
    public long ready(YearMonth m){return sums().ready(m);}
    // Credit cards in an envelope system. Spending on a card from a category with money moves that money to the card's
    // payment category, ready to pay the bill; spending beyond what the category has is credit overspending: it shows
    // in the category this month and then becomes card debt, without touching To budget. A payment (a transfer
    // from a cash account to the card) uses the payment category's money.
    /** Net spending (refunds negative) in [c] on credit card [card] in [m]; card null = on every card. */
    public long creditSpent(Category c,YearMonth m,Account card){return sums().creditSpent(c,m,card);}
    /** The part of [c]'s overspending in [m] that came from card spending (it becomes debt, not less To budget). */
    public long creditOverspent(Category c,YearMonth m){return sums().creditOverspent(c,m);}
    /**
     * Money moved from [c] to [card]'s payment category in [m]. A card with net refunds gives them back in full; a card
     * with net spending gets its share of what the category could pay for (the rest is credit overspending).
     */
    public long movedToCard(Category c,YearMonth m,Account card){return sums().movedToCard(c,m,card);}
    private static long share(long funded,long part,long all){return BigDecimal.valueOf(funded).multiply(BigDecimal.valueOf(part)).divide(BigDecimal.valueOf(all),0,java.math.RoundingMode.HALF_UP).longValueExact();}
    public Category paymentCategory(Account card){for(Category c:categories)if(card.id.equals(c.cardAccount))return c;return null;}
    /** Adds a credit card owing [owed] (a positive amount) and its payment category. Old debt starts with nothing set aside. */
    public Account addCard(String name,String date,long owed){Account a=new Account(name,date,-owed);a.type="credit";accounts.add(a);Category p=new Category(name);p.group=paymentGroup();p.cardAccount=a.id;categories.add(p);changed();return a;}
    /** The group card payment categories are in: "Credit card payments", or what it was renamed to (hunt 26 B4: a new card joins it). */
    String paymentGroup(){for(Category c:categories)if(c.payment())return c.group;return "Credit card payments";}
    /** An account's balance at the end of [m] (a card's is negative while it's owed). */
    public long balanceAt(Account a,YearMonth m){return sums().balanceAt(a,m);}
    public long futureAssigned(YearMonth m){long n=0;for(Category c:categories)for(Map.Entry<String,Long>a:c.assigned.entrySet())if(a.getKey().compareTo(m.toString())>0)n+=a.getValue();return n;}
    public long spendable(YearMonth m){return ready(m)-futureAssigned(m);}
    /** Hunt 25 A2: money assigned in [m] carries into every later month, so it has to be spare in each of them (a later month's
     * outflow to To budget or overspending can have used [m]'s spare money), up to now or the last month with money assigned. */
    public long spendableFrom(YearMonth m){long n=spendable(m);YearMonth last=YearMonth.now();for(Category c:categories)for(String k:c.assigned.keySet()){YearMonth a=YearMonth.parse(k);if(a.isAfter(last))last=a;}
        for(YearMonth k=m.plusMonths(1);!k.isAfter(last);k=k.plusMonths(1))n=Math.min(n,spendable(k));return n;}
    public void assign(Category c,YearMonth m,long amount){if(amount>0&&amount>spendableFrom(m))throw new IllegalArgumentException("Not enough unassigned money; check future months too.");if(amount<0&&-amount>Math.max(0,available(c,m)))throw new IllegalArgumentException("You cannot return more than this category has available.");if(m.isAfter(YearMonth.now())&&assigned(c,m)+amount<0)throw new IllegalArgumentException("Move carried-over money in the current month, or return only this future month's assignment.");c.assigned.put(m.toString(),assigned(c,m)+amount);assignedChanged(c);}
    public void move(Category from,Category to,YearMonth m,long amount){if(from==to||amount<=0||amount>available(from,m))throw new IllegalArgumentException("Choose different categories and an amount available in the source.");if(m.isAfter(YearMonth.now())&&assigned(from,m)-amount<0)throw new IllegalArgumentException("Move carried-over money in the current month.");from.assigned.put(m.toString(),assigned(from,m)-amount);to.assigned.put(m.toString(),assigned(to,m)+amount);assignedChanged(from);assignedChanged(to);}
    public long needed(Category c,YearMonth m){Long ask=ask(c,m);return ask==null?0:Math.max(0,ask-assigned(c,m));}
    /** What [c]'s target asks for in [m] before anything is assigned in [m] (needed is this less [m]'s Assigned); 0 without one. */
    public long targetAsk(Category c,YearMonth m){Long ask=ask(c,m);return ask==null?0:ask;}
    /** needed() before [m]'s Assigned is taken off; null when the target asks for nothing at all (none, snoozed, or a by-date target that's over). */
    private Long ask(Category c,YearMonth m){
        if(c.target<=0||c.snoozed.equals(m.toString()))return null;
        if(c.targetType.equals("Monthly")||c.targetType.equals("Debt"))return c.target; // a debt payment: a fixed amount each month
        // Weekly: the amount for each chosen weekday in the month; refill counts what's left from last month (not in future months, as Refill).
        if(c.targetType.equals("Weekly"))return weeklyGoal(c,m)-(c.weeklyRefill&&!m.isAfter(YearMonth.now())?carried(c,m):0);
        // By date: what's still to save (less what came in from earlier months), spread evenly over the months up to the due month.
        if(c.targetType.equals("ByDate")){LocalDate due=dueFor(c,m);if(due==null)return null;long months=ChronoUnit.MONTHS.between(m,YearMonth.from(due))+1,left=Math.max(0,c.target-carried(c,m));return (left+months-1)/months;}
        // Balance with a due month: like by date, what's still to save (less what came in) over the months left.
        if(c.targetType.equals("Balance")&&!c.due.isEmpty()){YearMonth due=YearMonth.parse(c.due);long remaining=Math.max(0,c.target-carried(c,m));long months=Math.max(1,ChronoUnit.MONTHS.between(m,due)+1);return (remaining+months-1)/months;}
        // Refill: up to the target, counting what's left from last month; a balance: up to the target, counting everything but this month's Assigned.
        return c.target-(c.targetType.equals("Refill")?(m.isAfter(YearMonth.now())?0:Math.max(0,available(c,m.minusMonths(1)))):availableAfterBills(c,m)-assigned(c,m));
    }
    /**
     * Getting a month ahead, for [m] (usually next month): {what its targets and upcoming bills ask for before anything is
     * assigned in [m], what they still need (Fund targets' figure)}. Hidden categories are left out, as on Home.
     */
    public long[] monthAhead(YearMonth m){long asked=0,still=0;
        for(Category c:categories){if(c.hidden)continue;long need=fundNeed(c,m),a=assigned(c,m),bills=upcoming(c,m)-(availableAfterBills(c,m)-a);
            asked+=Math.max(need,Math.max(0,Math.max(targetAsk(c,m),bills)));still+=need;}
        return new long[]{asked,still};}
    /** Money assigned in each month after [m], earliest first (months adding up to nothing left out). */
    public TreeMap<YearMonth,Long> assignedAfter(YearMonth m){TreeMap<YearMonth,Long> map=new TreeMap<>();
        for(Category c:categories)for(Map.Entry<String,Long> a:c.assigned.entrySet()){YearMonth k=YearMonth.parse(a.getKey());if(k.isAfter(m))map.merge(k,a.getValue(),Long::sum);}
        map.values().removeIf(v->v==0);return map;}
    /** What [c] can give back from its assignment in the later month [future] to cover overspending (Cover overspending). */
    public long futureCover(Category c,YearMonth future){return c.payment()?0:Math.max(0,Math.min(assigned(c,future),available(c,future)));}
    /**
     * Covers [to]'s overspending in [m] (this month or a later one) with [amount] of the money [from] has assigned in the
     * later month [future]: it goes back to To budget there, then into [to] in [m]. Cash doesn't change.
     */
    public void coverFromFuture(Category from,YearMonth future,Category to,YearMonth m,long amount){
        if(!future.isAfter(m)||m.isBefore(YearMonth.now()))throw new IllegalArgumentException("Cover with a later month's money in this month or a later one.");
        if(from==to||amount<=0||amount>futureCover(from,future)||amount>toCover(to,m))throw new IllegalArgumentException("Choose an amount that month has assigned and this category needs.");
        if(spendable(m)<0)throw new IllegalArgumentException("To budget is below zero: return money in Budget first.");
        assign(from,future,-amount);assign(to,m,amount);
    }
    /**
     * To budget in [m] in parts that add up to it exactly: {money in cash accounts, what categories hold (not card payments),
     * what's set aside for card payments, what card payment categories are below zero (paid beyond what was set aside, or card
     * credit), cash overspending in [m], money assigned in later months}. To budget = cash − held − set aside + beyond +
     * overspent − later. Cash overspending comes off To budget only the month after (ready); card overspending is card debt,
     * never cash, so it isn't a part.
     */
    public long[] readyParts(YearMonth m){long held=0,set=0,beyond=0,over=0;
        for(Category c:categories){long a=available(c,m);if(c.payment()){if(a>=0)set+=a;else beyond+=-a;}else if(a>=0)held+=a;else over+=-a-creditOverspent(c,m);}
        return new long[]{cash(m),held,set,beyond,over,futureAssigned(m)};}
    /** Money assigned in [m], all categories together. */
    public long assignedIn(YearMonth m){long n=0;for(Category c:categories)n+=assigned(c,m);return n;}
    public long spending(YearMonth m){return sums().spending(m);}
    public long income(YearMonth m){return sums().income(m);}
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
     * Money in and out of the budget counts (budgetAmount): spending on a card is an outflow on its date, like cash spending,
     * and a card payment (a transfer within the budget) isn't, so the figure follows spending. What a card owed when it was
     * added isn't money received or spent here.
     */
    public int ageOfMoney(LocalDate until){Sums s=sums();return s.ages.computeIfAbsent(until,d->moneyAge(d,s));}
    private int moneyAge(LocalDate until,Sums s){
        List<long[]> events=new ArrayList<>(); // day, amount (+ in, - out)
        for(Account a:accounts)if(a.cash()&&a.opening>0&&!LocalDate.parse(a.date).isAfter(until))events.add(new long[]{LocalDate.parse(a.date).toEpochDay(),a.opening});
        long end=until.toEpochDay();for(int i=0;i<entries.size();i++){long day=s.day(i);if(day>end)continue;long n=s.budgetAmount(entries.get(i));if(n!=0)events.add(new long[]{day,n});}
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
    // Month maths read every transaction for every category and month, and a card's payment category reads every other
    // category's: years of transactions took minutes. Sums works the same sums out from one pass over the transactions
    // (each month's in-and-out per category, card and account) and remembers what it has worked out. By default each call
    // starts afresh, so a change made straight to the lists or fields always counts. cache(true) keeps one between calls
    // (MainActivity from screen to screen, every change going through its commit(); the widget; Planner's form): then changed() must follow a change made straight to the fields,
    // as Budget's own changes (assign, move, enter, deleting...) do themselves. BudgetTest compares the results with the plain sums.
    private boolean caching;private Sums memo;
    /** [on]: month maths are kept between calls until changed(); off: worked out afresh for each call. Either way, starts afresh. */
    public void cache(boolean on){caching=on;memo=null;}
    public boolean cached(){return caching;}
    /** The budget changed: month maths kept by cache(true) are worked out again. */
    public void changed(){memo=null;}
    /** Only [c]'s Assigned changed (assign, move): its Available, the card payment categories' and To budget are worked out again. */
    private void assignedChanged(Category c){if(memo!=null){memo.runs.remove(c);memo.runs.keySet().removeIf(Category::payment);memo.ready.clear();}}
    private Sums sums(){if(!caching)return new Sums();if(memo==null)memo=new Sums();return memo;}
    /** A month as one number (year * 12 + month - 1), from a YearMonth or from a date ("YYYY-MM-DD", as LocalDate writes it). */
    static int month(YearMonth m){return m.getYear()*12+m.getMonthValue()-1;}
    static int month(String date){if(date.length()>=7&&date.charAt(4)=='-'&&date.charAt(0)!='+'&&date.charAt(0)!='-')return((date.charAt(0)-'0')*1000+(date.charAt(1)-'0')*100+(date.charAt(2)-'0')*10+(date.charAt(3)-'0'))*12+(date.charAt(5)-'0')*10+(date.charAt(6)-'0')-1;
        int dash=date.indexOf('-',1);return Integer.parseInt(date.substring(0,dash))*12+Integer.parseInt(date.substring(dash+1,dash+3))-1;}
    private static YearMonth yearMonth(int month){return YearMonth.of(Math.floorDiv(month,12),Math.floorMod(month,12)+1);}
    private final class Sums {
        final Map<String,Account> accountById=new HashMap<>();final Map<String,Category> categoryById=new HashMap<>(); // the first with each id, as account() and category()
        int lo,hi=-1;int[][] inMonth; // the months with transactions, and each one's transactions (index in entries, in list order)
        final Map<String,long[]> in=new HashMap<>(),paidIn=new HashMap<>(),advanced=new HashMap<>(),moved=new HashMap<>(); // per month, by category or account id (see build)
        final Map<String,long[]> balances=new HashMap<>(); // account id -> {all, cleared}
        final Map<String,Integer> touched=new HashMap<>(); // category id -> the first month a transaction touches it
        final Map<Account,Map<String,long[]>> onCard=new IdentityHashMap<>();final Map<Account,long[]> freed=new IdentityHashMap<>(); // by budget account
        long[] cashIn,spent;
        final Map<LocalDate,Integer> ages=new HashMap<>();long[] days; // money age by day; each transaction's date as a day number (read once)
        long day(int i){if(days==null){days=new long[entries.size()];for(int j=0;j<days.length;j++){String d=entries.get(j).date;int m=month(d); // as LocalDate.parse(d).toEpochDay()
            days[j]=d.length()==10&&d.charAt(4)=='-'&&d.charAt(7)=='-'&&d.charAt(0)!='+'?LocalDate.of(Math.floorDiv(m,12),Math.floorMod(m,12)+1,(d.charAt(8)-'0')*10+d.charAt(9)-'0').toEpochDay():LocalDate.parse(d).toEpochDay();}}return days[i];}
        final Map<Category,Run> runs=new IdentityHashMap<>();final Map<Integer,Long> ready=new HashMap<>();final Map<String,Map<String,Long>> spentBy=new HashMap<>();
        Sums(){for(Account a:accounts)accountById.putIfAbsent(a.id,a);for(Category c:categories)categoryById.putIfAbsent(c.id,c);
            int n=entries.size();int[] at=new int[n];lo=Integer.MAX_VALUE;hi=Integer.MIN_VALUE;
            for(int i=0;i<n;i++){at[i]=month(entries.get(i).date);lo=Math.min(lo,at[i]);hi=Math.max(hi,at[i]);}
            if(n==0){lo=0;hi=-1;}int size=hi-lo+1;int[] count=new int[Math.max(0,size)];for(int i=0;i<n;i++)count[at[i]-lo]++;
            inMonth=new int[Math.max(0,size)][];for(int k=0;k<size;k++)inMonth[k]=new int[count[k]];int[] filled=new int[Math.max(0,size)];
            cashIn=new long[Math.max(0,size)];spent=new long[Math.max(0,size)];
            for(int i=0;i<n;i++){Entry e=entries.get(i);int k=at[i]-lo;inMonth[k][filled[k]++]=i;add(e,k);}
        }
        private long[] row(Map<String,long[]> map,String key){long[] r=map.get(key);if(r==null)map.put(key,r=new long[hi-lo+1]);return r;}
        // One transaction's part in each sum, exactly as the plain sums count it: budgetIn per category it touches (and To budget),
        // card spending per card and category, payments into and cash advances out of cards, cash, spending and account balances.
        private void add(Entry e,int k){
            boolean transfer=e.transfer();Account a=accountById.get(e.account),to=transfer?accountById.get(e.destination):null;
            Account budget=!transfer?(a!=null&&!a.tracking()?a:null):a==null||to==null||a.tracking()==to.tracking()?null:a.tracking()?to:a; // as budgetAccount(e)
            // As budgetIn(e,id): the parts count in their categories unless it's off budget; money in from a tracking account goes to To budget.
            boolean counts=transfer?budget!=null&&budget.id.equals(e.account):a==null||!a.tracking(),fromTracking=transfer&&budget!=null&&!counts;
            if(e.split()){Set<String> ids=new LinkedHashSet<>();for(Split p:e.splits)ids.add(p.category);for(String id:ids)part(id,counts?e.amountIn(id):fromTracking&&id.isEmpty()?-e.amount:0,budget,k);
                if(!ids.contains(""))part(null,fromTracking?-e.amount:0,budget,k);}
            else{part(e.category,counts?e.amount:fromTracking&&e.category.isEmpty()?-e.amount:0,budget,k);if(!e.category.isEmpty())part(null,fromTracking?-e.amount:0,budget,k);}
            long toBudget=counts?e.amountIn(""):fromTracking?-e.amount:0;
            if(budget!=null)freed.computeIfAbsent(budget,x->new long[hi-lo+1])[k]+=toBudget;spent[k]-=(budget==null?0:counts?e.amount:-e.amount)-toBudget;
            if(transfer){if(a!=null&&a.cash())row(paidIn,e.destination)[k]+=e.amount;if(to!=null&&to.cash())row(advanced,e.account)[k]+=e.amount;
                if(a!=null&&to!=null&&a.cash()!=to.cash())cashIn[k]+=a.cash()?e.amount:-e.amount;} // in or out of cash (to a card or a tracking account)
            else if(a!=null&&a.cash())cashIn[k]+=e.amount;
            row(moved,e.account)[k]+=e.amount;row(moved,e.destination)[k]-=e.amount;
            long[] b=balances.computeIfAbsent(e.account,x->new long[2]),d=balances.computeIfAbsent(e.destination,x->new long[2]);
            b[0]+=e.amount;d[0]-=e.amount;if(e.cleared){b[1]+=e.amount;d[1]-=e.amount;}
        }
        /** Category [id]'s part [n] of a transaction it touches (null: To budget's, when the transaction doesn't touch ""). */
        private void part(String id,long n,Account budget,int k){
            if(id==null){if(n!=0)row(in,"")[k]+=n;return;}
            Integer first=touched.get(id);if(first==null||k+lo<first)touched.put(id,k+lo);if(n!=0)row(in,id)[k]+=n;
            if(budget!=null&&budget.credit())onCard.computeIfAbsent(budget,x->new HashMap<>()).computeIfAbsent(id,x->new long[hi-lo+1])[k]-=n;
        }
        long at(long[] r,int month){int i=month-lo;return r==null||i<0||i>=r.length?0:r[i];}
        long upTo(long[] r,int month){long n=0;if(r!=null)for(int i=0;i<r.length&&i<=month-lo;i++)n+=r[i];return n;}
        Account budgetAccount(Entry e){Account a=accountById.get(e.account);if(!e.transfer())return a!=null&&!a.tracking()?a:null;Account to=accountById.get(e.destination);
            if(a==null||to==null||a.tracking()==to.tracking())return null;return a.tracking()?to:a;}
        long budgetIn(Entry e,String id){
            if(!e.transfer()){long n=e.amountIn(id);if(n==0)return 0;Account a=accountById.get(e.account);return a!=null&&a.tracking()?0:n;}
            Account a=budgetAccount(e);if(a==null)return 0;return a.id.equals(e.account)?e.amountIn(id):id.isEmpty()?-e.amount:0;}
        long budgetAmount(Entry e){Account a=budgetAccount(e);return a==null?0:a.id.equals(e.account)?e.amount:-e.amount;}
        long activity(Category c,YearMonth m){return c.payment()?paymentActivity(c,m):at(in.get(c.id),month(m));}
        long income(YearMonth m){return at(in.get(""),month(m));}
        long spending(YearMonth m){return at(spent,month(m));}
        long cash(YearMonth m){String end=m.atEndOfMonth().toString();long n=0;for(Account a:accounts)if(a.cash()&&a.date.compareTo(end)<=0)n+=a.opening;return n+upTo(cashIn,month(m));}
        long balanceAt(Account a,YearMonth m){if(a.date.compareTo(m.atEndOfMonth().toString())>0)return 0;return a.opening+upTo(moved.get(a.id),month(m));}
        long balance(Account a,boolean clearedOnly){long[] b=balances.get(a.id);return a.opening+(b==null?0:b[clearedOnly?1:0]);}
        /** A category's Available month by month, from the first month with money or a transaction (see available). */
        final class Run { final Category c;final int first;long n,start;int done;long[] end=new long[0];
            Run(Category c){this.c=c;int f=Integer.MAX_VALUE;for(String key:c.assigned.keySet())f=Math.min(f,month(YearMonth.parse(key)));Integer t=touched.get(c.id);if(t!=null)f=Math.min(f,t);
                if(c.payment()){Account card=accountById.get(c.cardAccount);if(card!=null)f=Math.min(f,month(YearMonth.from(LocalDate.parse(card.date))));}first=f;}
            long until(int to){
                for(;first+done<=to;done++){YearMonth m=yearMonth(first+done);n=n<0&&c.payment()?Math.max(n,-cardCredit(c,m.minusMonths(1),start)):Math.max(0,n);start=n;n+=assigned(c,m)+activity(c,m);
                    if(done==end.length)end=Arrays.copyOf(end,Math.max(12,end.length*2));end[done]=n;}
                return end[to-first];}
        }
        // From the first month with assigned money or a transaction (a card's from its opening), month by month to [month]:
        // what's left carries forward, overspending resets (a payment category's card credit carries on); with nothing before
        // [month], just [month]'s Assigned and Activity.
        long available(Category c,YearMonth month){int to=month(month);Run r=runs.get(c);if(r==null)runs.put(c,r=new Run(c));
            return r.first>to?assigned(c,month)+activity(c,month):r.until(to);}
        /**
         * How far payment category [pc] may stay below zero after [m] (which it started at [start]): the card's credit, what
         * already carried, and its To budget parts in [m]. Below that, more was paid than was set aside: overspending.
         */
        long cardCredit(Category pc,YearMonth m,long start){Account card=accountById.get(pc.cardAccount);if(card==null)return 0;
            // Hunt 24 C4: and its interest and fees in [m] (the payment category's own row holds only those): they use up card credit
            // but are more debt, never overspending.
            // Hunt 25 A1: what carried, less the card credit it held at the end of last month (counted again in [m]'s balance, as far
            // as it's still there): credit spent since is used up, as when the credit and the spending fall in one month. A reward
            // on a card that still owes (no credit) carries on.
            return Math.max(0,balanceAt(card,m))+Math.max(0,-start-Math.max(0,balanceAt(card,m.minusMonths(1))))+Math.max(0,at(freed.get(card),month(m)))+Math.max(0,-at(in.get(pc.id),month(m)));}
        long toCover(Category c,YearMonth m){long a=available(c,m);if(a>=0)return 0;if(!c.payment())return -a;long start=a-assigned(c,m)-activity(c,m);return Math.max(0,-a-cardCredit(c,m,start));}
        long ready(YearMonth m){return ready.computeIfAbsent(month(m),k->{long n=cash(m);for(Category c:categories)n-=available(c,m)+creditOverspent(c,m);return n;});}
        long creditSpent(Category c,YearMonth m,Account card){long n=0;int k=month(m);for(Map.Entry<Account,Map<String,long[]>> x:onCard.entrySet())if(card==null||x.getKey()==card)n+=at(x.getValue().get(c.id),k);return n;}
        long creditOverspent(Category c,YearMonth m){if(c.payment())return 0;long a=available(c,m);if(a>=0)return 0;return Math.min(-a,creditSpending(c,m));}
        /** Spending on cards that had more spending than refunds in [m] (each card counted on its own). */
        long creditSpending(Category c,YearMonth m){long n=0;for(Account a:accounts)if(a.credit())n+=Math.max(0,creditSpent(c,m,a));return n;}
        long movedToCard(Category c,YearMonth m,Account card){
            long mine=creditSpent(c,m,card);if(mine<=0)return mine;long all=creditSpending(c,m),funded=all-creditOverspent(c,m);if(all==mine)return funded;
            // Rounded by running total (cards in account order), so the cards' shares add up to the funded amount exactly.
            long before=0;for(Account a:accounts){if(a==card)break;if(a.credit())before+=Math.max(0,creditSpent(c,m,a));}
            return share(funded,before+mine,all)-share(funded,before,all);
        }
        // A card's To budget parts (a reward credit, a refund with no category, a reconcile adjustment) change what's owed
        // without moving cash, so they move money between To budget and the payment category: an inflow frees set-aside
        // money (less is owed), an outflow sets more aside from To budget (more is owed). Payments from a cash account use the
        // money set aside; a cash advance (card to a cash account) is borrowed money, set aside here to repay, so To budget
        // doesn't grow while the card owes more; money in from a tracking account counts like a refund to To budget.
        long paymentActivity(Category pc,YearMonth m){
            Account card=accountById.get(pc.cardAccount);if(card==null)return 0;long n=0;for(Category c:categories)if(!c.payment())n+=movedToCard(c,m,card);
            int k=month(m);return n+at(paidIn.get(card.id),k)-at(advanced.get(card.id),k)-at(freed.get(card),k);
        }
        /** The transactions dated in [from] to [to], in list order. */
        List<Entry> between(YearMonth from,YearMonth to){List<Integer> list=new ArrayList<>();for(int k=Math.max(lo,month(from));k<=Math.min(hi,month(to));k++)for(int i:inMonth[k-lo])list.add(i);
            Collections.sort(list);List<Entry> r=new ArrayList<>();for(int i:list)r.add(entries.get(i));return r;}
        Map<String,Long> spentBy(YearMonth from,YearMonth to){return spentBy.computeIfAbsent(from+" "+to,x->{
            String start=from.atDay(1).toString(),end=to.atEndOfMonth().toString();Map<String,Long> map=new LinkedHashMap<>();
            for(Entry e:between(from,to)){if(e.date.compareTo(start)<0||e.date.compareTo(end)>0)continue;Set<String> ids=new LinkedHashSet<>();if(e.split())for(Split p:e.splits)ids.add(p.category);else ids.add(e.category);
                for(String id:ids){Category c=id.isEmpty()?null:categoryById.get(id);if(c==null)continue;long n=-budgetIn(e,id);if(n!=0)map.merge(id,n,Long::sum);}} // a payment category here is its card's interest and fees
            return map;});}
    }
    // Reports count budget accounts only (tracking accounts are in Net worth only); a transfer within the budget isn't spending
    // or income, and a card payment category's activity isn't spending.
    /** Net spending (refunds take off) per spending category in the months [from] to [to]: category id -> cents. Card payment categories are left out. */
    public Map<String,Long> spentBy(YearMonth from,YearMonth to){return new LinkedHashMap<>(sums().spentBy(from,to));}
    /** A part of the spending breakdown: a category, a group, or Other (what's beyond the biggest SLICES). tenths: its share in tenths of a percent. */
    public static final class Slice { public final String name;public final boolean other;public final List<String> ids=new ArrayList<>();public long amount;public int tenths; Slice(String name,boolean other){this.name=name;this.other=other;} }
    public static final int SLICES=7;
    /**
     * Spending in [from] to [to] by category (or by group), biggest first. A category with more refunds than spending is
     * left out of both views (see refunds), so they add up to the same total.
     * Beyond the biggest SLICES, the rest fold into Other (last). Shares add up to exactly 100.0% (largest remainder).
     */
    public List<Slice> breakdown(YearMonth from,YearMonth to,boolean byGroup){return breakdown(from,to,byGroup,SLICES);}
    /** The breakdown with the biggest [limit] kept apart (the rest into Other). */
    public List<Slice> breakdown(YearMonth from,YearMonth to,boolean byGroup,int limit){
        Map<String,Long> spent=spentBy(from,to);LinkedHashMap<String,Slice> map=new LinkedHashMap<>();
        for(Category c:categories){long n=spent.getOrDefault(c.id,0L);if(n<=0)continue;String key=byGroup?c.group.trim().toLowerCase(Locale.ROOT):c.id;Slice s=map.get(key);if(s==null)map.put(key,s=new Slice(byGroup?c.group.trim():c.name,false));s.ids.add(c.id);s.amount+=n;}
        List<Slice> list=new ArrayList<>(map.values());list.removeIf(s->s.amount<=0);list.sort((a,b)->Long.compare(b.amount,a.amount));
        if(list.size()>limit){Slice other=new Slice("Other",true);for(Slice s:list.subList(limit,list.size())){other.ids.addAll(s.ids);other.amount+=s.amount;}list=new ArrayList<>(list.subList(0,limit));list.add(other);}
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
    // Yearly report (Reports): a calendar year's income, spending and net, month by month, its biggest categories and every
    // group's total. Spending is the spending breakdown's: budget accounts only, net of refunds; transfers, card payments and
    // tracking accounts aren't spending. Income is money into To budget, as in the cash flow chart.
    public static final int TOP=10;
    public static final class Year { public final int year;public final long[] income=new long[12],spending=new long[12];public List<Slice> top,groups;public long refunds;
        Year(int year){this.year=year;}
        public long income(){long n=0;for(long v:income)n+=v;return n;} public long spending(){long n=0;for(long v:spending)n+=v;return n;} public long net(){return income()-spending();} }
    /** [year]'s report: each month's income and net spending, the TOP categories by spending (the rest in Other) and each group, with shares of what they spent. */
    public Year year(int year){
        Year r=new Year(year);YearMonth from=YearMonth.of(year,1),to=YearMonth.of(year,12);
        for(int i=0;i<12;i++){YearMonth m=from.plusMonths(i);r.income[i]=income(m);long n=0;for(long v:spentBy(m,m).values())n+=v;r.spending[i]=n;}
        r.top=breakdown(from,to,false,TOP);r.groups=breakdown(from,to,true,Integer.MAX_VALUE);r.refunds=refunds(from,to);return r;
    }
    /** The years the yearly report can show: from the first transaction's year to the last one's, and [current] in any case. */
    public int[] years(int current){int first=current,last=current;for(Entry e:entries){int y=Integer.parseInt(e.date.substring(0,4));first=Math.min(first,y);last=Math.max(last,y);}return new int[]{first,last};}
    /** A row of the income and expense table: an amount per month; group: a group's subtotal row. */
    public static final class Row { public final String name;public final boolean group;public final long[] amounts; Row(String name,boolean group,int months){this.name=name;this.group=group;amounts=new long[months];}
        public long total(){long n=0;for(long v:amounts)n+=v;return n;} public long average(){return Budget.average(amounts);} boolean empty(){for(long v:amounts)if(v!=0)return false;return true;} }
    /** Income by payee and expenses by category (each group's subtotal row before its categories), month by month, with totals and net (income - expenses). */
    public static final class Table { public final YearMonth[] months;public final List<Row> income=new ArrayList<>(),expenses=new ArrayList<>();public final Row incomeTotal,expenseTotal,net;
        Table(YearMonth from,int n){months=new YearMonth[n];for(int i=0;i<n;i++)months[i]=from.plusMonths(i);incomeTotal=new Row("Total income",false,n);expenseTotal=new Row("Total expenses",false,n);net=new Row("Net",false,n);} }
    /** The income and expense table for the [months] months from [from]. Income is money into To budget (net, by payee, biggest first); expenses are net of refunds, in plan order. */
    public Table incomeExpense(YearMonth from,int months){
        Table t=new Table(from,months);String start=from.atDay(1).toString(),end=from.plusMonths(months-1).atEndOfMonth().toString();LinkedHashMap<String,Row> payees=new LinkedHashMap<>();
        for(Entry e:sums().between(from,from.plusMonths(months-1))){if(e.date.compareTo(start)<0||e.date.compareTo(end)>0)continue;long n=budgetIn(e,"");if(n==0)continue;int i=(int)ChronoUnit.MONTHS.between(from,YearMonth.from(LocalDate.parse(e.date)));Row r=payees.get(key(e.payee));if(r==null)payees.put(key(e.payee),r=new Row(e.payee.trim(),false,months));r.amounts[i]+=n;} // newest first: a payee's newest spelling
        for(Row r:payees.values())if(!r.empty())t.income.add(r);t.income.sort((a,b)->Long.compare(b.total(),a.total()));
        List<Map<String,Long>> spent=new ArrayList<>();for(YearMonth m:t.months)spent.add(spentBy(m,m));
        LinkedHashMap<String,List<Category>> groups=new LinkedHashMap<>();for(Category c:categories)groups.computeIfAbsent(c.group.trim().toLowerCase(Locale.ROOT),k->new ArrayList<>()).add(c);
        for(List<Category> list:groups.values()){Row g=new Row(list.get(0).group.trim(),true,months);List<Row> rows=new ArrayList<>();
            for(Category c:list){Row r=new Row(c.payment()?c.name+" interest and fees":c.name,false,months);for(int i=0;i<months;i++){r.amounts[i]=spent.get(i).getOrDefault(c.id,0L);g.amounts[i]+=r.amounts[i];}if(!r.empty())rows.add(r);} // hunt 24 C5: a card's interest counts, as in the other reports
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
    // A new budget's starter categories (name, group), added when there's no saved budget; the first-run setup lets you untick groups.
    public static final String[][] STARTER={{"Rent","Bills"},{"Utilities","Bills"},{"Groceries","Everyday"},{"Transport","Everyday"},{"Dining out","Everyday"},{"Annual insurance","True expenses"},{"Car repairs","True expenses"},{"Emergency fund","Savings"}};
    public static List<String> starterGroups(){List<String> g=new ArrayList<>();for(String[] s:STARTER)if(!g.contains(s[1]))g.add(s[1]);return g;}
    /** Starter categories in [groups] that aren't here yet (by name, any capitals) are added; returns how many. */
    public int addStarter(Collection<String> groups){int n=0;for(String[] s:STARTER){if(!groups.contains(s[1]))continue;boolean have=false;for(Category c:categories)have|=c.name.trim().equalsIgnoreCase(s[0]);if(have)continue;Category c=new Category(s[0]);c.group=s[1];categories.add(c);n++;}changed();return n;}
    /**
     * Starter categories in groups not in [keep] go, but only as they came: never used (no money, transactions or upcoming ones),
     * with no target, note, pin or hidden mark, and nothing (a Planner bill, an import rule) pointing at them. Returns how many.
     */
    public int removeStarter(Collection<String> keep){int n=0;for(String[] s:STARTER){if(keep.contains(s[1]))continue;for(Category c:new ArrayList<>(categories)){
            if(!c.name.equals(s[0])||!c.group.equals(s[1])||c.payment()||used(c)||c.target!=0||!c.note.isEmpty()||c.pinned||c.hidden||billCategories.containsValue(c.id))continue;
            boolean ruled=false;for(Rule r:rules)ruled|=r.category.equals(c.id);if(ruled)continue;deleteCategory(c,null);n++;}}return n;}
    /** A brand-new budget, for the first-run setup: no accounts, transactions or upcoming ones, and no money assigned to any category. */
    public boolean brandNew(){if(!accounts.isEmpty()||!entries.isEmpty()||!scheduled.isEmpty())return false;for(Category c:categories)for(long v:c.assigned.values())if(v!=0)return false;return true;}
    /** The currency the first-run setup suggests: [locale]'s country's, when it's one of the common ones; AUD otherwise. */
    public static String suggestedCurrency(Locale locale){try{String code=Currency.getInstance(locale).getCurrencyCode();if(Arrays.asList(COMMON_CURRENCIES).contains(code))return code;}catch(RuntimeException ignored){}return DEFAULT_CURRENCY;}
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
    public boolean used(Category c){for(Entry e:entries)if(e.touches(c.id))return true;for(Scheduled s:scheduled)if(s.touches(c.id))return true;for(long v:c.assigned.values())if(v!=0)return true;return false;}
    public int entriesIn(Category c){int n=0;for(Entry e:entries)if(e.touches(c.id))n++;return n;}
    /** Deletes [c]; its transactions and monthly assignments move to [into] (needed when it was used). Cash doesn't change. */
    public void deleteCategory(Category c,Category into){
        if(c.payment())throw new IllegalArgumentException("This is a credit card's payment category. Delete or close the card instead.");
        if(into!=null&&into.payment())throw new IllegalArgumentException("Choose a spending category, not a card payment.");
        if(into==c||(into==null&&used(c)))throw new IllegalArgumentException("Choose another category to take its transactions and money.");
        if(into!=null){for(Entry e:entries){if(e.category.equals(c.id))e.category=into.id;for(Split s:e.splits)if(s.category.equals(c.id))s.category=into.id;}for(Scheduled s:scheduled){if(s.category.equals(c.id))s.category=into.id;for(Split p:s.splits)if(p.category.equals(c.id))p.category=into.id;}for(Map.Entry<String,Long>a:c.assigned.entrySet())into.assigned.merge(a.getKey(),a.getValue(),Long::sum);}
        for(Map.Entry<String,String> m:billCategories.entrySet())if(m.getValue().equals(c.id))m.setValue(into==null?"":into.id);billCategories.values().removeIf(String::isEmpty);
        for(Rule r:rules)if(r.category.equals(c.id))r.category=into==null?"":into.id;rules.removeIf(r->r.rename.isEmpty()&&r.category.isEmpty()); // an import rule left with nothing to do goes
        if(into!=null)for(Map.Entry<String,String> m:payeeCategories.entrySet())if(m.getValue().equals(c.id))m.setValue(into.id);payeeCategories.values().removeIf(id->id.equals(c.id)); // unused: automatic again
        categories.remove(c);changed();
    }
    /** Swaps [c] with the next category of its group up (-1) or down (+1); false at the end of the group. */
    public boolean reorder(Category c,int direction){int i=categories.indexOf(c);for(int j=i+direction;j>=0&&j<categories.size();j+=direction)if(categories.get(j).group.equals(c.group)){Collections.swap(categories,i,j);changed();return true;}return false;}
    /**
     * Renames group [from]: every category in it (hidden ones too) moves to [to]. A name another group already has (any case)
     * merges them under that group's spelling.
     */
    public void renameGroup(String from,String to){String name=to==null?"":to.trim();if(name.isEmpty())throw new IllegalArgumentException("Enter a group name.");
        boolean cards=false;for(Category c:categories)if(c.group.equals(from)&&c.payment())cards=true;
        for(Category c:categories)if(!c.group.equals(from)&&c.group.trim().equalsIgnoreCase(name)){if(c.payment()!=cards)throw new IllegalArgumentException("Card payment categories keep a group of their own."); // B4
            name=c.group;break;}
        boolean any=false;for(Category c:categories)if(c.group.equals(from)){c.group=name;any=true;}
        if(!any)throw new IllegalArgumentException("That group no longer exists.");changed();}
    /**
     * Moves [group] past the group next to it on Budget ([direction] -1 up, 1 down; groups with only hidden categories aren't
     * shown, so they're skipped and go last). Each group's own order stays. False when it's already first or last.
     */
    public boolean moveGroup(String group,int direction){int i=shownGroups().indexOf(group);return i>=0&&moveGroupTo(group,i+direction);}
    /** The groups in Budget's order (those with a category shown). */
    public List<String> shownGroups(){List<String> order=new ArrayList<>();for(Category c:categories)if(!c.hidden&&!order.contains(c.group))order.add(c.group);return order;}
    /** Puts [group] at place [to] among the shown groups (moveGroup's rules). False when it's not shown, [to] is outside, or it's there already. */
    public boolean moveGroupTo(String group,int to){List<String> order=shownGroups();int i=order.indexOf(group);
        if(i<0||to<0||to>=order.size()||to==i)return false;order.remove(i);order.add(to,group);return groupOrder(order);}
    /** Shown groups A to Z (ignoring capitals; Budget's default order). False when they already are. */
    public boolean sortGroups(){List<String> order=shownGroups();order.sort(String.CASE_INSENSITIVE_ORDER);return groupOrder(order);}
    public boolean groupsSorted(){List<String> order=shownGroups(),sorted=new ArrayList<>(order);sorted.sort(String.CASE_INSENSITIVE_ORDER);return order.equals(sorted);}
    /** Orders the categories by [order] (shown groups; the others after them, as they were). False when nothing moves. */
    private boolean groupOrder(List<String> order){if(order.equals(shownGroups()))return false;List<String> all=new ArrayList<>(order);
        for(Category c:categories)if(!all.contains(c.group))all.add(c.group);
        List<Category> sorted=new ArrayList<>(categories);sorted.sort(Comparator.comparingInt(c->all.indexOf(c.group))); // stable: each group keeps its order
        categories.clear();categories.addAll(sorted);changed();return true;}
    // Accounts: close at zero, delete only unused.
    public boolean usedAccount(Account a){for(Entry e:entries)if(e.account.equals(a.id)||e.destination.equals(a.id))return true;for(Scheduled s:scheduled)if(s.account.equals(a.id))return true;return false;}
    /** Renames [a]; its transfers' default payee ("Transfer to <name>") follows. */
    public void rename(Account a,String name){for(Entry e:entries)if(e.destination.equals(a.id)&&e.payee.equals("Transfer to "+a.name))e.payee="Transfer to "+name;Category p=paymentCategory(a);if(p!=null&&p.name.equals(a.name))p.name=name;a.name=name;}
    public void close(Account a){if(balance(a,false)!=0)throw new IllegalArgumentException("Move the money out first: an account closes at a zero balance.");for(Scheduled s:scheduled)if(s.account.equals(a.id))throw new IllegalArgumentException("Move or delete its upcoming transactions first.");a.closed=true;}
    /**
     * What deleting [a] takes with it: {its own transactions, transfers with another account (that side stays there), upcoming
     * transactions}.
     */
    public int[] deleteCounts(Account a){int own=0,kept=0,upcoming=0;
        for(Entry e:entries){boolean from=e.account.equals(a.id),to=e.destination.equals(a.id);if(!from&&!to)continue;
            if(e.transfer()&&account(from?e.destination:e.account)!=null)kept++;else own++;}
        for(Scheduled s:scheduled)if(s.account.equals(a.id))upcoming++;return new int[]{own,kept,upcoming};}
    /**
     * Deletes [a] (any account: open, closed, card or tracking) with its transactions and upcoming ones. A transfer with another
     * account keeps that account's side as a plain transaction, so its balance stays: money that left [a] becomes money in there
     * ("Transfer from A", to To budget); money that came into [a] stays money out there ("Transfer to A", its category kept, none
     * from a tracking account). A card's payment category goes too; anything still in it goes back to To budget.
     */
    public void deleteAccount(Account a){
        for(Entry e:entries){boolean from=e.account.equals(a.id),to=e.destination.equals(a.id);if(!e.transfer()||!from&&!to)continue;
            Account other=account(from?e.destination:e.account);if(other==null)continue;
            if(from){if(e.payee.equals("Transfer to "+other.name))e.payee="Transfer from "+a.name;e.account=other.id;e.amount=-e.amount;e.category="";}
            else if(other.tracking())e.category="";
            e.destination="";e.splits.clear();}
        entries.removeIf(e->e.account.equals(a.id)||e.destination.equals(a.id));scheduled.removeIf(s->s.account.equals(a.id));
        Category p=paymentCategory(a);
        if(p!=null){for(Entry e:entries){if(e.category.equals(p.id))e.category="";for(Split s:e.splits)if(s.category.equals(p.id))s.category="";}
            for(Scheduled s:scheduled){if(s.category.equals(p.id))s.category="";for(Split q:s.splits)if(q.category.equals(p.id))q.category="";}
            billCategories.values().removeIf(id->id.equals(p.id));payeeCategories.values().removeIf(id->id.equals(p.id));
            for(Rule r:rules)if(r.category.equals(p.id))r.category="";rules.removeIf(r->r.rename.isEmpty()&&r.category.isEmpty());categories.remove(p);}
        accounts.remove(a);changed();}
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
        if(hiddenPayees.remove(key(from))&&!existing)hiddenPayees.add(key(t));
        String setting=payeeCategories.remove(key(from));if(setting!=null)payeeCategories.putIfAbsent(key(t),setting);return n; // the payee kept keeps its own setting
    }
    // A payee's category suggestion (new transactions and imported rows): automatic, always one category, or none.
    /** Lower-case payee -> the category always suggested for it, or "" for none. Payees not here are automatic (usualCategory). */
    public final Map<String,String> payeeCategories=new TreeMap<>();
    /** Sets [payee]'s suggestion: null = automatic, "" = don't suggest, else always that (spending) category. */
    public void setPayeeCategory(String payee,String category){String k=key(payee);if(k.isEmpty())throw new IllegalArgumentException("Choose a payee.");
        if(category==null){payeeCategories.remove(k);return;}Category c=category(category);if(!category.isEmpty()&&(c==null||c.payment()))throw new IllegalArgumentException("Choose a spending category.");payeeCategories.put(k,category);}
    /** The category suggested for [payee]'s next transaction (an id), or null for none: its setting, else usualCategory. */
    public String suggestedCategory(String payee){String set=payeeCategories.get(key(payee));
        if(set!=null){if(set.isEmpty())return null;Category c=category(set);if(c!=null&&!c.payment())return c.id;} // a fixed category that's gone: automatic again
        return usualCategory(payee);}
    /**
     * [payee]'s usual category, from its transactions oldest first (splits and transfers left out): the first category, which
     * changes only when two of the three latest agree on another. One odd purchase doesn't move it; a real change does, on
     * the second time. Null when none has a (spending) category. Imports' "To categorize" is a placeholder, not a category: left out.
     */
    public String usualCategory(String payee){String k=key(payee);List<Entry> list=new ArrayList<>();
        for(int i=entries.size()-1;i>=0;i--){Entry e=entries.get(i);if(!e.transfer()&&!e.split()&&!e.category.isEmpty()&&key(e.payee).equals(k)){Category c=category(e.category);if(c!=null&&!c.payment()&&!c.name.equalsIgnoreCase(CsvImport.TO_CATEGORIZE))list.add(e);}} // entries are kept newest first: oldest added first here
        list.sort(Comparator.comparing(e->e.date));String usual=null;Deque<String> latest=new ArrayDeque<>();
        for(Entry e:list){latest.addLast(e.category);if(latest.size()>3)latest.removeFirst();
            if(usual==null)usual=e.category;else if(!e.category.equals(usual)&&Collections.frequency(latest,e.category)>=2)usual=e.category;}
        return usual;}
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
        String t=f.text.trim().toLowerCase(Locale.ROOT);if(t.isEmpty())return true;
        // An amount: by size (money in or out). With >, >=, <, <= or = only the amount counts; a plain number also finds text.
        long[] q=amountQuery(t);if(q!=null){long a=Math.abs(e.amount),v=q[1];
            boolean hit=q[0]==2?a>v:q[0]==3?a>=v:q[0]==4?a<v:q[0]==5?a<=v:a==v;if(hit||q[0]!=0)return hit;}
        return searchText(e).toLowerCase(Locale.ROOT).contains(t);
    }
    private static final java.util.regex.Pattern AMOUNT=java.util.regex.Pattern.compile("(>=|<=|>|<|=)?\\s*[$€£]?\\s*(\\d{1,3}(?:,\\d{3})+|\\d+)(\\.\\d{1,2})?");
    /**
     * An amount search ("42.50", ">=100", "< 50", "=$1,200"): {operator, cents}, operator 0 = a plain number, 1 =, 2 >,
     * 3 >=, 4 <, 5 <=. Null when [text] isn't one (it's searched as text).
     */
    public static long[] amountQuery(String text){
        java.util.regex.Matcher m=AMOUNT.matcher(text.trim());if(!m.matches())return null;String op=m.group(1)==null?"":m.group(1);
        try{long cents=new BigDecimal(m.group(2).replace(",","")+(m.group(3)==null?"":m.group(3))).movePointRight(2).longValueExact();
            return new long[]{op.isEmpty()?0:op.equals("=")?1:op.equals(">")?2:op.equals(">=")?3:op.equals("<")?4:5,cents};}
        catch(ArithmeticException x){return null;}
    }
    /** [f]'s transactions, newest first. */
    public List<Entry> filter(Filter f){List<Entry> list=new ArrayList<>();for(Entry e:entries)if(matches(f,e))list.add(e);list.sort((a,b)->b.date.compareTo(a.date));return list;}
    private String searchText(Entry e){StringBuilder s=new StringBuilder(e.payee).append(' ').append(e.memo);Account a=account(e.account),to=account(e.destination);if(a!=null)s.append(' ').append(a.name);if(to!=null)s.append(' ').append(to.name);
        if(e.split())for(Split p:e.splits){Category c=category(p.category);s.append(' ').append(c==null?"To budget":c.name).append(' ').append(p.memo);}
        else{Category c=category(e.category);Account own=account(e.account);if(c!=null||e.transfer()||own==null||!own.tracking())s.append(' ').append(c!=null?c.name:e.transfer()?"Transfer":"To budget");}return s.toString();} // a tracking account's own entry has no category
    // Tracking accounts and loans.
    /** Adds a tracking account: an asset worth [value], or a debt owing [value] (a positive amount; saved as a negative balance). */
    public Account addTracking(String name,String date,long value,boolean liability){if(value<0)throw new IllegalArgumentException("Enter the amount as a positive number.");Account a=new Account(name,date,liability?-value:value);a.type="tracking";a.liability=liability;accounts.add(a);changed();return a;}
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
    /** [s] at most [max] chars, never cutting an emoji (or other character outside the basic range) in half (hunt 23). */
    public static String cut(String s,int max){if(s.length()<=max)return s;int end=max;if(end>0&&Character.isHighSurrogate(s.charAt(end-1)))end--;return s.substring(0,end);}
    public static String statementPayee(Entry e){if(!e.bankPayee.isEmpty())return e.bankPayee;if(e.transfer()||!e.memo.trim().equals(IMPORTED))return "";String p=e.payee.trim();return cut(p,PAYEE_MAX);}
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
        else if(e.split()||e.category.equals(SPLIT))validateParts(e.category,e.splits,e.amount);
        else if(!e.category.isEmpty()&&category(e.category)==null)throw new IllegalArgumentException("Choose a category.");
        else if(!e.category.isEmpty()&&category(e.category).payment()&&!category(e.category).cardAccount.equals(e.account))throw new IllegalArgumentException(PAY_BY_TRANSFER);
    }
    // Card interest and fees: an expense (or its refund) on a card in that card's own payment category. It's more debt, like what
    // was owed when the card was added: it doesn't take money from a category (nothing to cover, not overspending) or from To
    // budget, and nothing is set aside for it until money is assigned to the payment category (a payoff target helps).
    /** Whether [e] is a card's interest or fee (or a refund of one): on the card, in its own payment category. */
    public boolean cardCharge(Entry e){if(e.transfer()||e.split())return false;Category c=category(e.category);return c!=null&&c.payment()&&c.cardAccount.equals(e.account);}
    /** Interest and fees on [pc]'s card in [m], less refunds of them. */
    public long cardCharges(Category pc,YearMonth m){if(!pc.payment())return 0;String start=m.atDay(1).toString(),end=m.atEndOfMonth().toString();long n=0;
        for(Entry e:entries)if(cardCharge(e)&&e.category.equals(pc.id)&&e.date.compareTo(start)>=0&&e.date.compareTo(end)<=0)n-=e.amount;return n;}
    // Reconciled transactions: Reconcile locks what's cleared in the account then, so the balance checked against the bank stays
    // as it was. They can still be changed, after a warning; unticking Cleared unlocks one.
    /** Marks [a]'s cleared transactions (transfers in or out too) reconciled; returns how many weren't already. */
    public int lockReconciled(Account a){int n=0;for(Entry e:entries)if(e.cleared&&!e.reconciled&&(e.account.equals(a.id)||e.destination.equals(a.id))){e.reconciled=true;n++;}return n;}
    /** A split's (or an upcoming split's) parts: two or more, none $0, To budget or spending categories, adding up to [amount]. */
    private void validateParts(String category,List<Split> parts,long amount){
        if(!category.equals(SPLIT)||parts.size()<2)throw new IllegalArgumentException("A split needs at least two parts.");long sum=0;
        for(Split p:parts){if(p.amount==0)throw new IllegalArgumentException("Give every part of the split an amount.");if(!p.category.isEmpty()&&category(p.category)==null)throw new IllegalArgumentException("Choose a category for every part.");if(!p.category.isEmpty()&&category(p.category).payment())throw new IllegalArgumentException(PAY_BY_TRANSFER);sum+=p.amount;}
        if(sum!=amount)throw new IllegalArgumentException("The parts of the split must add up to the total.");
    }
}
