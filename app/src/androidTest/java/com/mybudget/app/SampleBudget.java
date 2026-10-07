package com.mybudget.app;

import java.time.*;
import java.util.*;

/**
 * A made-up budget for speed checks (BudgetInstrumentation with "-e sample 5y") and for comparing cached month maths with
 * the plain sums (BudgetTest). Cash, savings and wallet accounts, two credit cards, a tracked loan and asset; grouped
 * categories with every kind of target; monthly assignments; a few transactions a day: income, spending (some split, some
 * on the cards, some refunds and card credits), savings transfers, card payments, cash advances, loan payments and balance
 * updates; upcoming and repeating ones. The same seed and day give the same budget. Pure Java: no Android classes.
 */
public final class SampleBudget {
    private SampleBudget(){}
    private static final String[][] CATEGORIES={{"Rent","Bills"},{"Electricity","Bills"},{"Water","Bills"},{"Internet","Bills"},{"Phone","Bills"},
        {"Groceries","Everyday"},{"Dining out","Everyday"},{"Coffee","Everyday"},{"Household","Everyday"},{"Personal care","Everyday"},
        {"Fuel","Transport"},{"Public transport","Transport"},{"Parking","Transport"},{"Car repairs","Transport"},{"Rego","Transport"},
        {"Doctor","Health"},{"Pharmacy","Health"},{"Dentist","Health"},{"Gym","Health"},{"Health insurance","Health"},
        {"Streaming","Fun"},{"Books","Fun"},{"Hobbies","Fun"},{"Concerts","Fun"},{"Holidays","Fun"},
        {"Gifts","True expenses"},{"Clothing","True expenses"},{"Car insurance","True expenses"},{"Home insurance","True expenses"},{"Annual fees","True expenses"},
        {"Kids","Family"},{"School","Family"},{"Pets","Family"},{"Charity","Family"},{"Haircuts","Family"},
        {"Emergency fund","Savings"},{"New car","Savings"},{"Investments","Savings"},{"Extra mortgage","Debt"},{"Student loan","Debt"}};
    private static final String[] TARGETS={"Refill","Monthly","Balance","Weekly","ByDate","Debt"};
    /** [months] months up to [today], [categories] categories (at most 40), about [perMonth] transactions a month. */
    public static Budget make(long seed,LocalDate today,int months,int categories,int perMonth){
        Random r=new Random(seed);Budget b=new Budget();LocalDate start=today.withDayOfMonth(1).minusMonths(months-1);String open=start.toString();
        Budget.Account everyday=new Budget.Account("Everyday",open,350000),savings=new Budget.Account("Savings",open,1200000),wallet=new Budget.Account("Wallet",open,15000);
        b.accounts.add(everyday);b.accounts.add(savings);b.accounts.add(wallet);
        Budget.Account visa=b.addCard("Visa",open,85000),master=b.addCard("Mastercard",start.plusMonths(Math.min(3,months-1)).toString(),0);
        Budget.Account loan=b.addTracking("Mortgage",open,42000000,true);loan.rate=6250;loan.payment=260000;loan.frequency="Monthly";
        Budget.Account shares=b.addTracking("Shares",open,1500000,false);
        List<Budget.Category> spend=new ArrayList<>();long[] mean=new long[Math.min(categories,CATEGORIES.length)];
        for(int i=0;i<mean.length;i++){Budget.Category c=new Budget.Category(CATEGORIES[i][0]);c.group=CATEGORIES[i][1];b.categories.add(c);spend.add(c);
            mean[i]=1000+r.nextInt(25000);if(c.name.equals("Rent"))mean[i]=220000;
            if(i%3==0){c.targetType=TARGETS[(i/3)%TARGETS.length];c.target=c.targetType.equals("Weekly")?mean[i]/4:mean[i];c.dueDay=c.targetType.equals("Refill")||c.targetType.equals("Monthly")?1+r.nextInt(28):0;
                if(c.targetType.equals("Balance")){c.target=mean[i]*12;c.due=YearMonth.from(today).plusMonths(6).toString();}
                if(c.targetType.equals("ByDate")){c.target=mean[i]*4;c.dueDate=today.plusMonths(2).withDayOfMonth(15).toString();c.repeatMonths=6;}
                if(c.targetType.equals("Weekly"))c.weekday=1+r.nextInt(7);}
            if(i==4)c.snoozed=YearMonth.from(today).toString();if(i==7)c.note="Cafe near work";if(i<4)c.pinned=true;if(i==mean.length-1&&i>8)c.hidden=true;}
        String[] payees={"Woolworths","Coles","Aldi","Shell","BP","Bunnings","Kmart","Chemist Warehouse","Uber","Netflix","Spotify","Telstra","Origin","Sydney Water","Cafe Roma","Thai Palace","JB Hi-Fi","Myer","Vet Clinic","Dr Smith"};
        String extra=categoryNamed(b,"Extra mortgage")==null?spend.get(spend.size()-1).id:categoryNamed(b,"Extra mortgage");int visits=Math.max(1,(perMonth-8)/spend.size());
        List<Budget.Entry> made=new ArrayList<>();Map<Budget.Account,Long> cardSpent=new HashMap<>();
        for(int k=0;k<months;k++){YearMonth m=YearMonth.from(start).plusMonths(k);int last=m.equals(YearMonth.from(today))?today.getDayOfMonth():m.lengthOfMonth();String key=m.toString();
            // Assignments: about what each category spends; card payment categories get some to pay down old debt; a few in next month.
            for(int i=0;i<spend.size();i++){long a=mean[i]*(80+r.nextInt(41))/100;spend.get(i).assigned.put(key,a);}
            if(k%2==0)b.paymentCategory(visa).assigned.put(key,10000L+r.nextInt(20000));
            if(k==months-1&&spend.size()>2){spend.get(1).assigned.put(m.plusMonths(1).toString(),mean[1]);spend.get(2).assigned.put(m.plusMonths(1).toString(),mean[2]/2);}
            for(int day=1;day<=last;day++){String d=m.atDay(day).toString();
                if(day==1||day==15)made.add(entry(b,"Employer","",everyday,d,610000+r.nextInt(30000)));
                if(day==2)made.add(transfer(b,"Transfer to Savings","",everyday,savings,d,60000));
                if(day==3&&k%4==1)made.add(transfer(b,"Transfer to Everyday","",savings,everyday,d,40000));
                if(day==5){for(Budget.Account card:new Budget.Account[]{visa,master}){long owed=cardSpent.getOrDefault(card,0L);if(owed>0&&card.date.compareTo(d)<=0){made.add(transfer(b,"Transfer to "+card.name,"",everyday,card,d,owed));cardSpent.put(card,0L);}}}
                if(day==10)made.add(transfer(b,"Transfer to Mortgage",extra,everyday,loan,d,260000));
                if(day==11&&k%3==0){Budget.Entry e=new Budget.Entry("Balance update","",loan.id,d,-(80000+r.nextInt(20000)));e.cleared=true;add(b,made,e);}
                if(day==12&&k%6==0){Budget.Entry e=new Budget.Entry("Balance update","",shares.id,d,r.nextInt(200000)-50000);e.cleared=true;add(b,made,e);}
                if(day==20&&cardSpent.getOrDefault(wallet,0L)>0){made.add(transfer(b,"Transfer to Wallet","",everyday,wallet,d,cardSpent.get(wallet)));cardSpent.put(wallet,0L);} // tops the wallet up by what was spent
                if(day==21&&k%9==4)made.add(transfer(b,"Transfer to Everyday","",visa,everyday,d,20000)); // a cash advance
                if(day==22&&k%5==2)made.add(transfer(b,"Transfer to Everyday","",shares,everyday,d,50000)); // money in from a tracking account
                // Everyday spending: perMonth less the fixed ones above, spread over the days.
                int n=Math.max(0,(perMonth-8)*(day+1)/m.lengthOfMonth()-(perMonth-8)*day/m.lengthOfMonth());
                for(int j=0;j<n;j++){int i=r.nextInt(spend.size());Budget.Category c=spend.get(i);if(c.id.equals(extra))c=spend.get(0);
                    int which=r.nextInt(100);Budget.Account a=which<55?everyday:which<80?visa:which<90&&master.date.compareTo(d)<=0?master:wallet;
                    long amount=-Math.max(100,mean[i]*(20+r.nextInt(161))/100/visits);String payee=payees[r.nextInt(payees.length)];int kind=r.nextInt(100);
                    Budget.Entry e;
                    if(kind<4&&spend.size()>2){Budget.Category c2=spend.get((i+1+r.nextInt(spend.size()-1))%spend.size());if(c2.id.equals(extra))c2=spend.get(1);
                        e=new Budget.Entry(payee,Budget.SPLIT,a.id,d,0);long p1=amount/2,p2=amount-p1;e.splits.add(new Budget.Split(c.id,p1));e.splits.add(new Budget.Split(c2==c?spend.get((i+1)%spend.size()).id:c2.id,p2));
                        if(r.nextInt(4)==0){e.splits.get(1).amount=p2-500;Budget.Split cash=new Budget.Split("",500);cash.memo="Cash out";e.splits.add(cash);} // a part into To budget
                        e.amount=0;for(Budget.Split s:e.splits)e.amount+=s.amount;}
                    else if(kind<7)e=new Budget.Entry(payee,c.id,a.id,d,-amount/2); // a refund
                    else if(kind<8&&a.credit())e=new Budget.Entry("Reward credit","",a.id,d,1000+r.nextInt(3000)); // card credit into To budget
                    else e=new Budget.Entry(payee,c.id,a.id,d,amount);
                    e.cleared=m.atDay(day).isBefore(today.minusDays(5));if(r.nextInt(30)==0)e.flag=1+r.nextInt(6);if(r.nextInt(12)==0)e.memo="Note "+r.nextInt(50);
                    if(k==months-1&&r.nextInt(10)==0){e.approved=false;e.memo=Budget.IMPORTED;e.bankPayee=payee.toUpperCase(Locale.ROOT)+" 1234";}
                    add(b,made,e);if(!a.cash()||a==wallet)cardSpent.merge(a,-e.amount,Long::sum);}}} // what each card (and the wallet) needs paying back
        Collections.reverse(made);b.entries.addAll(made); // kept newest first, as the app adds them
        // Upcoming and repeating ones (a split too), from tomorrow.
        LocalDate next=today.plusDays(1);Budget.Category rent=spend.get(0),fuel=categoryNamed(b,"Fuel")==null?spend.get(0):b.category(categoryNamed(b,"Fuel"));
        b.scheduled.add(new Budget.Scheduled("Landlord",rent.id,everyday.id,today.plusMonths(1).withDayOfMonth(1).toString(),-220000,"Monthly"));
        b.scheduled.add(new Budget.Scheduled("Netflix",spend.get(Math.min(20,spend.size()-1)).id,visa.id,next.toString(),-2299,"Monthly"));
        b.scheduled.add(new Budget.Scheduled("Shell",fuel.id,everyday.id,next.plusDays(2).toString(),-6000,"Weekly"));
        b.scheduled.add(new Budget.Scheduled("Insurer",spend.get(Math.min(27,spend.size()-1)).id,everyday.id,next.plusMonths(3).toString(),-95000,"Yearly"));
        b.scheduled.add(new Budget.Scheduled("Employer","",everyday.id,next.plusDays(6).toString(),420000,"Every 2 weeks"));
        Budget.Scheduled split=new Budget.Scheduled("Costco",Budget.SPLIT,everyday.id,next.plusDays(9).toString(),-30000,"Every 3 months");
        split.splits.add(new Budget.Split(spend.get(Math.min(5,spend.size()-1)).id,-20000));split.splits.add(new Budget.Split(spend.get(Math.min(8,spend.size()-1)).id,-10000));b.scheduled.add(split);
        b.scheduled.add(new Budget.Scheduled("Dentist",spend.get(Math.min(17,spend.size()-1)).id,everyday.id,next.plusDays(40).toString(),-18000,"Never"));
        for(Budget.Scheduled s:b.scheduled)b.validate(s);
        b.setMonthNote(YearMonth.from(today),"Big month: rego and insurance");b.rules.add(new Budget.Rule("WOOLWORTHS","Woolworths",spend.get(Math.min(5,spend.size()-1)).id));
        b.hidePayee("Dr Smith",true);b.flagNames[4]="Tax";
        return b;
    }
    private static String categoryNamed(Budget b,String name){for(Budget.Category c:b.categories)if(c.name.equals(name))return c.id;return null;}
    private static Budget.Entry entry(Budget b,String payee,String category,Budget.Account a,String date,long amount){Budget.Entry e=new Budget.Entry(payee,category,a.id,date,amount);e.cleared=true;b.validate(e);return e;}
    private static Budget.Entry transfer(Budget b,String payee,String category,Budget.Account from,Budget.Account to,String date,long amount){
        Budget.Entry e=new Budget.Entry(payee,category==null?"":category,from.id,date,-amount);e.destination=to.id;e.cleared=true;b.validate(e);return e;}
    private static void add(Budget b,List<Budget.Entry> made,Budget.Entry e){b.validate(e);made.add(e);}
}
