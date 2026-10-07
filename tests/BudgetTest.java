import com.mybudget.app.Budget;
import com.mybudget.app.CsvImport;
import com.mybudget.app.DataSafety;
import java.time.YearMonth;
import java.time.LocalDate;
import java.math.BigDecimal;

public class BudgetTest {
    static void equal(long actual,long expected,String message){if(actual!=expected)throw new AssertionError(message+": "+actual+" != "+expected);}
    static void rejects(Runnable action){try{action.run();}catch(IllegalArgumentException e){return;}throw new AssertionError("Expected rejection");}
    public static void main(String[] args){
        YearMonth jan=YearMonth.of(2025,1),feb=jan.plusMonths(1);
        Budget b=new Budget();Budget.Account bank=new Budget.Account("Bank","2025-01-01",100000);b.accounts.add(bank);
        Budget.Category food=new Budget.Category("Food"),savings=new Budget.Category("Savings");b.categories.add(food);b.categories.add(savings);
        b.assign(food,jan,20000);b.assign(savings,jan,80000);equal(b.ready(jan),0,"Fully assigned");
        Budget.Entry shop=new Budget.Entry("Shop",food.id,bank.id,"2025-01-02",-25000);b.validate(shop);b.entries.add(shop);
        equal(b.available(food,jan),-5000,"Cash overspending");equal(b.cash(jan),75000,"Cash after spending");
        equal(b.available(food,feb),0,"Negative envelope resets");equal(b.ready(feb),-5000,"Overspending reduces next month ready");
        equal(b.available(savings,feb),80000,"Positive balance rolls over");equal(b.assigned(savings,feb),0,"Monthly assigned resets");
        b.move(savings,food,jan,5000);equal(b.available(food,jan),0,"Cover overspending");equal(b.ready(feb),0,"Cover updates next month");equal(b.cash(jan),75000,"Moves preserve cash");
        shop.amount=-20000;equal(b.available(food,jan),5000,"Editing expense recalculates");equal(b.cash(jan),80000,"Editing updates cash");
        b.entries.remove(shop);equal(b.available(food,jan),25000,"Deleting restores category");equal(b.cash(jan),100000,"Deleting restores cash");
        food.target=30000;food.targetType="Refill";equal(b.needed(food,feb),5000,"Refill uses carryover");
        food.targetType="Monthly";equal(b.needed(food,feb),30000,"Fresh monthly amount");
        food.targetType="Balance";food.target=45000;food.due="2025-03";equal(b.needed(food,feb),10000,"Deadline savings contribution");
        Budget.Account other=new Budget.Account("Savings account","2025-01-01",0);b.accounts.add(other);
        Budget.Entry transfer=new Budget.Entry("Transfer","",bank.id,"2025-01-05",-10000);transfer.destination=other.id;b.validate(transfer);b.entries.add(transfer);
        equal(b.balance(bank,false),90000,"Transfer source");equal(b.balance(other,false),10000,"Transfer destination");equal(b.cash(jan),100000,"Transfer preserves cash");equal(b.spending(jan),0,"Transfer is not spending");equal(b.balance(bank,true),100000,"Uncleared excluded");transfer.cleared=true;equal(b.balance(bank,true),90000,"Cleared included");
        b.entries.add(new Budget.Entry("Income","",bank.id,"2025-02-01",10000));equal(b.ready(jan),0,"Future income excluded");equal(b.ready(feb),10000,"Income in correct month");
        b.assign(food,feb,10000);equal(b.spendable(jan),-10000,"Future reservation visible");rejects(()->b.assign(food,jan,1));
        equal(Budget.cents("0.01"),1,"Exact cents");equal(Budget.parse("-10.25"),-1025,"Signed assignment");
        for(String invalid:new String[]{"0","-1","1.001","abc","100000000.01"})rejects(()->Budget.cents(invalid));
        rejects(()->b.move(food,savings,jan,999999));rejects(()->b.move(food,food,jan,1));
        Budget.Entry refund=new Budget.Entry("Refund",food.id,bank.id,"2025-01-04",500);b.entries.add(refund);equal(b.activity(food,jan),500,"Refund replenishes category");equal(b.income(jan),0,"Refund is not new income");
        YearMonth future=YearMonth.now().plusMonths(1);food.targetType="Refill";food.target=30000;equal(b.needed(food,future),30000,"Future refill does not prematurely count carryover");rejects(()->b.assign(food,future,-1));
        Budget.Entry power=new Budget.Entry("Electricity",food.id,bank.id,"2025-01-06",-14280);power.externalId="pay-1";power.billKey="planner-series-s1";b.entries.add(0,power);
        if(b.external("pay-1")!=power||b.external("pay-2")!=null||b.external("")!=null)throw new AssertionError("Payment id lookup");
        Budget.Entry older=new Budget.Entry("Electricity",savings.id,bank.id,"2025-01-01",-100);older.billKey="planner-series-s1";b.entries.add(older);
        if(b.lastForBill("planner-series-s1")!=power||b.lastForBill("planner-bill-9")!=null)throw new AssertionError("Newest expense for a bill");
        csv();batchOne();phaseB();phaseC();phaseD();phaseE();phaseF();phaseG();fixes();fixes2();suggestions();batch1();batch2();batch3();hunt21();hunt22();dataSafety();
        System.out.println("PASS: monthly accounting, rollover, targets, edits, transfers, clearing, future reservations, exact cents, sent payments, CSV export, category delete/reorder, account close/delete, reconcile adjustments, quick assign, payees and typing suggestions, weekly/by-date/debt targets, more quick amounts, month notes, running balances, split helpers, quick maths, tracking accounts, loan payoff, flags and filters, review, payee tools and import rules, spending breakdown and trends, income vs expense table, spending pace, pinned categories, bills due soon, backup reminder and snooze, undo after a delete, daily automatic backups.");
    }
    static void batch1(){
        // Weekly targets: amount x the chosen weekdays in the month. September 2025 has 5 Mondays, February 2025 has 4.
        YearMonth sep=YearMonth.of(2025,9),oct=sep.plusMonths(1),feb=YearMonth.of(2025,2),jan=feb.minusMonths(1);
        equal(Budget.weekdaysIn(sep,1),5,"5 Mondays in Sep 2025");equal(Budget.weekdaysIn(feb,1),4,"4 Mondays in Feb 2025");equal(Budget.weekdaysIn(sep,7),4,"4 Sundays in Sep 2025");
        Budget b=new Budget();Budget.Account bank=new Budget.Account("Bank","2025-01-01",500000);b.accounts.add(bank);
        Budget.Category fuel=new Budget.Category("Fuel");fuel.targetType="Weekly";fuel.target=4000;fuel.weekday=1;b.categories.add(fuel);
        equal(b.needed(fuel,sep),20000,"$40 x 5 Mondays");equal(b.needed(fuel,feb),16000,"$40 x 4 Mondays");fuel.weekday=7;equal(b.needed(fuel,sep),16000,"$40 x 4 Sundays");fuel.weekday=1;
        // Refill counts what's left from last month; set aside doesn't. Assigned this month counts in both.
        b.assign(fuel,YearMonth.of(2025,8),3000);equal(b.carried(fuel,sep),3000,"Carried in");equal(b.needed(fuel,sep),17000,"Refill: $200 less $30 left");
        fuel.weeklyRefill=false;equal(b.needed(fuel,sep),20000,"Set aside: the full $200");b.assign(fuel,sep,5000);equal(b.needed(fuel,sep),15000,"Set aside less this month's assigned");fuel.weeklyRefill=true;equal(b.needed(fuel,sep),12000,"Refill less carried and assigned");
        equal(b.firstDue(fuel,sep),1,"First Monday of Sep 2025 is the 1st");equal(b.firstDue(fuel,oct),6,"First Monday of Oct 2025 is the 6th");
        fuel.snoozed=sep.toString();equal(b.needed(fuel,sep),0,"Snoozed weekly asks nothing");fuel.snoozed="";
        // By date: what's left spread evenly over the months up to and including the due month; then stops, or repeats.
        Budget.Category rates=new Budget.Category("Council rates");rates.targetType="ByDate";rates.target=25000;rates.dueDate="2025-12-15";b.categories.add(rates);
        equal(b.needed(rates,sep),6250,"$250 over Sep-Dec");equal(b.firstDue(rates,sep),32,"Not due in Sep");equal(b.firstDue(rates,YearMonth.of(2025,12)),15,"Due on the 15th in Dec");
        b.assign(rates,sep,6250);equal(b.needed(rates,sep),0,"This month's share assigned");equal(b.needed(rates,oct),6250,"$187.50 over Oct-Dec");
        b.assign(rates,oct,6250);b.assign(rates,YearMonth.of(2025,11),6250);equal(b.needed(rates,YearMonth.of(2025,12)),6250,"Last share in the due month");
        b.assign(rates,YearMonth.of(2025,12),6250);Budget.Entry bill=new Budget.Entry("Council",rates.id,bank.id,"2025-12-15",-25000);b.entries.add(bill);
        equal(b.needed(rates,YearMonth.of(2025,12)),0,"Paying in the due month doesn't ask again");equal(b.needed(rates,YearMonth.of(2026,1)),0,"After the date it stops asking");
        if(Budget.dueFor(rates,YearMonth.of(2026,1))!=null)throw new AssertionError("No repeat: no next date");
        rates.repeatMonths=3;same(String.valueOf(Budget.dueFor(rates,YearMonth.of(2026,1))),"2026-03-15","Next date 3 months on");equal(b.needed(rates,YearMonth.of(2026,1)),8334,"$250 over Jan-Mar, rounded up");
        same(String.valueOf(Budget.dueFor(rates,YearMonth.of(2026,4))),"2026-06-15","And again");rates.repeatMonths=12;same(String.valueOf(Budget.dueFor(rates,YearMonth.of(2026,1))),"2026-12-15","Yearly");
        Budget.Category odd=new Budget.Category("Odd");odd.targetType="ByDate";odd.target=100;odd.dueDate="2025-01-31";odd.repeatMonths=3;same(String.valueOf(Budget.dueFor(odd,YearMonth.of(2025,7))),"2025-07-31","Repeats count from the first date (the 31st stays)");
        b.assign(rates,YearMonth.of(2026,1),8334);rates.snoozed="2026-02";equal(b.needed(rates,YearMonth.of(2026,2)),0,"Snoozed by date");rates.snoozed="";
        // Debt payment: a fixed amount each month, like Set aside, with a due day.
        Budget.Category loan=new Budget.Category("Car loan");loan.targetType="Debt";loan.target=30000;loan.dueDay=20;b.categories.add(loan);
        equal(b.needed(loan,sep),30000,"Debt payment asks the full amount");b.assign(loan,sep,30000);equal(b.needed(loan,oct),30000,"Again next month, whatever is left");equal(b.firstDue(loan,sep),20,"Debt due day");
        equal(b.fundNeed(loan,oct),30000,"Fund targets uses it");
        // Existing kinds unchanged.
        Budget.Category groc=new Budget.Category("Groceries");groc.target=50000;b.categories.add(groc);b.assign(groc,jan,10000);equal(b.needed(groc,feb),40000,"Refill unchanged");groc.targetType="Monthly";equal(b.needed(groc,feb),50000,"Monthly unchanged");equal(b.firstDue(groc,feb),32,"No due day");
        // Quick amounts: average assigned and average spent (rounded to the cent), reset available, reset assigned.
        Budget q=new Budget();bank=new Budget.Account("Bank","2025-01-01",1000000);q.accounts.add(bank);Budget.Category food=new Budget.Category("Food");q.categories.add(food);YearMonth apr=YearMonth.of(2025,4);
        q.assign(food,jan,10000);q.assign(food,feb,10000);q.assign(food,YearMonth.of(2025,3),10001);equal(q.averageAssigned(food,apr),10000,"Average assigned 300.01/3 rounds to 100.00");q.assign(food,YearMonth.of(2025,3),1);equal(q.averageAssigned(food,apr),10001,"300.02/3 = 100.0067 rounds up");
        q.entries.add(new Budget.Entry("Shop",food.id,bank.id,"2025-02-10",-101));q.entries.add(new Budget.Entry("Shop",food.id,bank.id,"2025-03-10",-101));equal(q.averageSpent(food,apr),67,"2.02/3 = 0.673 rounds to 0.67");q.entries.add(new Budget.Entry("Shop",food.id,bank.id,"2025-01-10",-1));equal(q.averageSpent(food,apr),68,"2.03/3 = 0.6767 rounds up");
        q.assign(food,apr,5000);long avail=q.available(food,apr);equal(q.resetAvailableChange(food,apr),-avail,"Reset available returns it all");q.assign(food,apr,q.resetAvailableChange(food,apr));equal(q.available(food,apr),0,"Available now $0");
        Budget r=new Budget();bank=new Budget.Account("Bank","2025-01-01",10000);r.accounts.add(bank);Budget.Category over=new Budget.Category("Over");r.categories.add(over);r.entries.add(new Budget.Entry("Shop",over.id,bank.id,"2025-01-05",-2500));
        equal(r.resetAvailableChange(over,jan),2500,"Reset available covers overspending");r.assign(over,jan,r.resetAvailableChange(over,jan));equal(r.available(over,jan),0,"Covered to $0");equal(r.resetChange(over,jan),0,"Reset assigned: spent money stays");
        Budget.Category fresh=new Budget.Category("Fresh");r.categories.add(fresh);r.assign(fresh,jan,1000);equal(r.resetChange(fresh,jan),-1000,"Reset assigned to $0");
        Budget.Category target=new Budget.Category("Target");target.target=2000;target.targetType="Monthly";r.categories.add(target);equal(r.fundNeed(target,jan),2000,"Underfunded = what Fund targets gives it");
        // Month notes: one per month, trimmed, at most 200 characters, empty removes.
        Budget n=new Budget();n.setMonthNote(sep,"  Holiday month: go easy on dining out ");same(n.monthNote(sep),"Holiday month: go easy on dining out","Note trimmed");same(n.monthNote(oct),"","Other months have none");
        StringBuilder longNote=new StringBuilder();for(int i=0;i<201;i++)longNote.append('x');rejects(()->n.setMonthNote(oct,longNote.toString()));n.setMonthNote(oct,longNote.substring(1));equal(n.monthNote(oct).length(),200,"200 characters allowed");
        n.setMonthNote(sep," ");if(n.monthNotes.containsKey(sep.toString()))throw new AssertionError("Empty note removes it");
        // Running balance: oldest first from the opening balance, transfers both ways, cleared or not; same-day ones in the order added.
        Budget rb=new Budget();Budget.Account main=new Budget.Account("Main","2025-01-01",10000),save=new Budget.Account("Save","2025-01-01",0);rb.accounts.add(main);rb.accounts.add(save);
        Budget.Entry pay=new Budget.Entry("Pay","",main.id,"2025-01-03",50000),shop=new Budget.Entry("Shop","",main.id,"2025-01-05",-2000),move=new Budget.Entry("Transfer to Save","",main.id,"2025-01-05",-30000),back=new Budget.Entry("Transfer to Main","",save.id,"2025-01-09",-5000);move.destination=save.id;back.destination=main.id;pay.cleared=true;
        rb.entries.add(0,pay);rb.entries.add(0,shop);rb.entries.add(0,move);rb.entries.add(0,back);java.util.Map<String,Long> run=rb.runningBalances(main);
        equal(run.get(pay.id),60000,"After pay");equal(run.get(shop.id),58000,"After shop");equal(run.get(move.id),28000,"After transfer out (added later the same day)");equal(run.get(back.id),33000,"After transfer in");equal(run.size(),4,"Only this account's");equal(run.get(back.id),rb.balance(main,false),"Newest = the balance");
        equal(rb.runningBalances(save).get(back.id),25000,"Savings side");
        // Split helpers.
        long[] parts=Budget.splitEvenly(1000,3);equal(parts[0],334,"Leftover cent on the first part");equal(parts[1],333,"Second");equal(parts[2],333,"Third");parts=Budget.splitEvenly(1001,3);equal(parts[0],334,"First +1");equal(parts[1],334,"Second +1");equal(parts[2],333,"Third");
        equal(Budget.splitEvenly(900,3)[2],300,"Even split");equal(Budget.remaining(10000,2500,1250),6250,"Fill remaining");equal(Budget.remaining(10000),10000,"Nothing else yet");
        // Quick maths: precedence, brackets, decimals, half-up rounding to the cent, leading +, errors.
        equal(Budget.evaluate("45+12.50"),5750,"Add");equal(Budget.evaluate("100-20"),8000,"Subtract");equal(Budget.evaluate("3*12.5"),3750,"Multiply");equal(Budget.evaluate("+250"),25000,"Leading +");
        equal(Budget.evaluate("10+2*3"),1600,"* before +");equal(Budget.evaluate("(10+2)*3"),3600,"Brackets");equal(Budget.evaluate("100/3"),3333,"Divide rounds to the cent");equal(Budget.evaluate("200/3"),6667,"Half up");equal(Budget.evaluate("0.05/2"),3,"0.025 rounds up to 0.03");
        equal(Budget.evaluate(" 1 + 2 "),300,"Spaces");equal(Budget.evaluate("-5+2"),-300,"Negative");equal(Budget.evaluate("2*-3"),-600,"Unary minus");equal(Budget.evaluate("1.000"),100,"Trailing zeros as before");equal(Budget.parse("12.34+0.66"),1300,"parse does quick maths");equal(Budget.cents("45+12.50"),5750,"cents does quick maths");
        equal(Budget.adjust("+50",30000),35000,"+ adds to the current value");equal(Budget.adjust("50",30000),5000,"Plain replaces");equal(Budget.adjust("+5*2",30000),31000,"+ then maths");
        for(String bad:new String[]{"","+","1+","5/0","1.001","1.005*2+1.001","(1+2","1+2)","abc","1..2","2**3","100000000+0.01","1e5","1,000"})rejects(()->Budget.evaluate(bad));
        rejects(()->Budget.cents("10-20"));equal(Budget.evaluate("99999999*1+1"),10000000000L,"$100 million exactly");
    }
    static void batch3(){
        YearMonth jan=YearMonth.of(2025,1),feb=jan.plusMonths(1),mar=feb.plusMonths(1);
        // Spending breakdown: budget accounts only, net of refunds, card spending and transfers out to tracking included; biggest first, beyond 7 into Other.
        Budget b=new Budget();Budget.Account bank=new Budget.Account("Bank","2025-01-01",10000000),save=new Budget.Account("Save","2025-01-01",0);b.accounts.add(bank);b.accounts.add(save);Budget.Account visa=b.addCard("Visa","2025-01-01",0);Budget.Account shares=b.addTracking("Shares","2025-01-01",100000,false);
        Budget.Category[] c=new Budget.Category[9];for(int i=0;i<9;i++){c[i]=new Budget.Category("C"+i);c[i].group=i<3?"Bills":i<6?"everyday":"Fun";b.categories.add(c[i]);b.assign(c[i],jan,200000);}c[4].group="Everyday";
        java.util.function.Consumer<Budget.Entry> add=e->{b.validate(e);b.entries.add(0,e);};
        add.accept(new Budget.Entry("Shop",c[0].id,visa.id,"2025-01-03",-90000)); // on the card
        Budget.Entry split=new Budget.Entry("Kmart",Budget.SPLIT,bank.id,"2025-01-04",-150000);split.splits.add(new Budget.Split(c[1].id,-80000));split.splits.add(new Budget.Split(c[2].id,-70000));add.accept(split);
        Budget.Entry invest=new Budget.Entry("Transfer to Shares",c[3].id,bank.id,"2025-01-05",-60000);invest.destination=shares.id;add.accept(invest); // out of the budget: spending
        add.accept(new Budget.Entry("Shop",c[4].id,bank.id,"2025-01-06",-60000));add.accept(new Budget.Entry("Refund",c[4].id,bank.id,"2025-01-07",10000));
        for(int i=5;i<9;i++)add.accept(new Budget.Entry("Shop",c[i].id,bank.id,"2025-01-08",-(9-i)*10000L));
        java.util.List<Budget.Slice> s=b.breakdown(jan,jan,false);long total=Budget.total(s);equal(total,450000,"Breakdown total");equal(total,b.spending(jan),"Breakdown = spending");equal(s.size(),8,"Top 7 and Other");
        StringBuilder names=new StringBuilder();int tenths=0;for(Budget.Slice x:s){names.append(x.name).append(x.other?"*":"").append(",");tenths+=x.tenths;}same(names.toString(),"C0,C1,C2,C3,C4,C5,C6,Other*,","Biggest first, Other last");equal(tenths,1000,"Shares add up to 100.0%");
        equal(s.get(0).amount,90000,"Card spending counts");equal(s.get(0).tenths,200,"20.0%");equal(s.get(1).amount,80000,"Split part");equal(s.get(3).amount,60000,"Out to tracking counts");equal(s.get(4).amount,50000,"Net of the refund");equal(s.get(7).amount,30000,"Other: C7 + C8");equal(s.get(7).ids.size(),2,"Other's categories");
        // Not spending: transfers within the budget, card payments, tracking accounts' own transactions, income.
        b.entries.add(0,transfer(bank,save,"2025-01-10",-500000));b.entries.add(0,transfer(bank,visa,"2025-01-11",-90000));b.entries.add(0,b.valueUpdate(shares,200000,"2025-01-12"));
        b.entries.add(0,transfer(shares,bank,"2025-01-13",-20000));b.entries.add(0,new Budget.Entry("Employer","",bank.id,"2025-01-15",300000));b.entries.add(0,new Budget.Entry("EMPLOYER ","",bank.id,"2025-01-20",50000));b.entries.add(0,new Budget.Entry("Odd",c[5].id,shares.id,"2025-01-14",-7000)); // a category on a tracking account (not allowed now) still isn't spending
        equal(Budget.total(b.breakdown(jan,jan,false)),450000,"Transfers, payments, tracking and income aren't spending");equal(b.movedToCard(c[0],jan,visa),90000,"Card spending moved money to the payment category");
        for(Budget.Slice x:b.breakdown(jan,jan,false))if(x.ids.contains(b.paymentCategory(visa).id))throw new AssertionError("A card payment category isn't spending");
        java.util.List<Budget.Slice> g=b.breakdown(jan,jan,true);StringBuilder gs=new StringBuilder();for(Budget.Slice x:g)gs.append(x.name).append(" ").append(x.amount).append(" ").append(x.tenths).append(",");
        same(gs.toString(),"Bills 240000 534,everyday 150000 333,Fun 60000 133,","By group (any capitals, first spelling); the tied remainder goes to the first");equal(g.get(1).ids.size(),3,"Everyday's categories");
        // A category with more refunds than spending is left out; Other appears only beyond 7.
        Budget.Category returns=new Budget.Category("Returns");returns.group="Fun";b.categories.add(returns);add.accept(new Budget.Entry("Store",returns.id,bank.id,"2025-01-21",5000));
        for(Budget.Slice x:b.breakdown(jan,jan,false))if(x.ids.contains(returns.id))throw new AssertionError("Net refunds left out");equal(b.spending(jan),445000,"Spending nets the refund");
        Budget few=new Budget();Budget.Account fb=new Budget.Account("Bank","2025-01-01",0);few.accounts.add(fb);for(int i=0;i<7;i++){Budget.Category x=new Budget.Category("F"+i);few.categories.add(x);few.entries.add(new Budget.Entry("Shop",x.id,fb.id,"2025-01-02",-100));}
        java.util.List<Budget.Slice> seven=few.breakdown(jan,jan,false);equal(seven.size(),7,"Seven: no Other");for(Budget.Slice x:seven)if(x.other)throw new AssertionError("No Other");equal(seven.get(0).tenths,143,"1/7: a remainder goes to the first");equal(seven.get(6).tenths,142,"14.2%");equal(few.breakdown(feb,feb,false).size(),0,"An empty month");
        // Several months: the period's spending.
        add.accept(new Budget.Entry("Shop",c[0].id,bank.id,"2025-02-03",-30000));add.accept(new Budget.Entry("Refund",c[0].id,bank.id,"2025-03-03",1000));
        equal(b.breakdown(jan,mar,false).get(0).amount,119000,"Three months, net");equal(Budget.total(b.breakdown(jan,mar,false)),450000+30000-1000,"Three months' total");
        // Spending trends: per month (a refund-only month is $0) and the average, half up.
        long[] t=b.trend(java.util.Collections.singletonList(c[0].id),mar,3);same(java.util.Arrays.toString(t),"[90000, 30000, 0]","Trend per month");equal(Budget.average(t),40000,"Average");
        t=b.trend(java.util.Collections.singletonList(c[0].id),mar,6);same(java.util.Arrays.toString(t),"[0, 0, 0, 90000, 30000, 0]","Six months back");equal(Budget.average(t),20000,"Six months' average");
        same(java.util.Arrays.toString(b.trend(b.groupIds("BILLS"),jan,1)),"[240000]","A group's trend");equal(b.groupIds("Credit card payments").size(),0,"Card payments aren't a spending group");
        equal(Budget.average(new long[]{1,2}),2,"Half up");equal(Budget.average(new long[]{1,1,2}),1,"1.33");equal(Budget.average(new long[0]),0,"Nothing");
        // Income vs expense table: income by payee (any capitals), expenses by group then category, totals = income and spending, net.
        add.accept(new Budget.Entry("Employer","",bank.id,"2025-02-15",100000));Budget.Category spare=new Budget.Category("Unused");spare.group="Spare";b.categories.add(spare);
        Budget.Table tb=b.incomeExpense(jan,3);equal(tb.months.length,3,"Three columns");if(!tb.months[2].equals(mar))throw new AssertionError("Months");
        equal(tb.income.size(),2,"Two income sources");same(tb.income.get(0).name,"Employer","Biggest first, newest spelling");same(java.util.Arrays.toString(tb.income.get(0).amounts),"[350000, 100000, 0]","One payee in any capitals");equal(tb.income.get(0).total(),450000,"Row total");equal(tb.income.get(0).average(),150000,"Row average");
        same(tb.income.get(1).name,"Transfer to Bank","Money in from a tracking account is income");equal(tb.income.get(1).amounts[0],20000,"Its amount");
        StringBuilder rows=new StringBuilder();for(Budget.Row r:tb.expenses)rows.append(r.group?"["+r.name+"]":r.name).append(",");same(rows.toString(),"[Bills],C0,C1,C2,[everyday],C3,C4,C5,[Fun],C6,C7,C8,Returns,","Groups then categories; unused and card payments left out");
        same(java.util.Arrays.toString(tb.expenses.get(1).amounts),"[90000, 30000, -1000]","A category's months, refunds netted");equal(tb.expenses.get(1).total(),119000,"Its total");equal(tb.expenses.get(1).average(),39667,"Its average, half up");equal(tb.expenses.get(0).amounts[0],240000,"Group subtotal");equal(tb.expenses.get(12).amounts[0],-5000,"A refund-only category");
        for(int i=0;i<3;i++){YearMonth m=jan.plusMonths(i);equal(tb.incomeTotal.amounts[i],b.income(m),"Total income "+m);equal(tb.expenseTotal.amounts[i],b.spending(m),"Total expenses "+m);equal(tb.net.amounts[i],b.income(m)-b.spending(m),"Net "+m);}
        equal(tb.net.total(),tb.incomeTotal.total()-tb.expenseTotal.total(),"Net total");equal(tb.incomeTotal.amounts[0],370000,"January income");equal(tb.expenseTotal.amounts[0],445000,"January expenses");equal(tb.net.amounts[0],-75000,"January net");
        equal(b.incomeExpense(jan.minusMonths(6),2).expenses.size(),0,"Months without transactions: no rows");equal(b.incomeExpense(jan.minusMonths(6),2).net.total(),0,"Nothing in or out");
        // Spending pace (April: 30 days): spent share >= month gone + 20 points, at least $10 spent; the current month only.
        YearMonth apr=YearMonth.of(2025,4);Budget p=new Budget();Budget.Account pb=new Budget.Account("Bank","2025-01-01",1000000);p.accounts.add(pb);Budget.Category food=new Budget.Category("Food");p.categories.add(food);p.assign(food,apr,50000);
        Budget.Entry shop=new Budget.Entry("Shop",food.id,pb.id,"2025-04-02",-35000);p.entries.add(shop);
        Budget.Pace pace=p.pace(food,apr,LocalDate.of(2025,4,12));equal(pace.spent,70,"70% spent");equal(pace.elapsed,40,"40% of the month gone");
        if(p.pace(food,apr,LocalDate.of(2025,4,15))==null)throw new AssertionError("70% at 50% + 20: shown (inclusive)");if(p.pace(food,apr,LocalDate.of(2025,4,16))!=null)throw new AssertionError("70% at 53%: not by 20 points");
        if(p.pace(food,apr,LocalDate.of(2025,4,30))!=null)throw new AssertionError("The last day: 100% gone");if(p.pace(food,apr.minusMonths(1),LocalDate.of(2025,4,12))!=null||p.pace(food,apr,LocalDate.of(2025,5,1))!=null)throw new AssertionError("The current month only");
        shop.amount=-12000;pace=p.pace(food,apr,LocalDate.of(2025,4,1));equal(pace.spent,24,"The first day: 24% spent");equal(pace.elapsed,3,"3% gone");shop.amount=-10000;if(p.pace(food,apr,LocalDate.of(2025,4,1))!=null)throw new AssertionError("20% on the first day: 3% + 20 is more");
        shop.amount=-40000;Budget.Entry refund=new Budget.Entry("Refund",food.id,pb.id,"2025-04-03",10000);p.entries.add(refund);equal(p.pace(food,apr,LocalDate.of(2025,4,12)).spent,60,"Net of refunds: 60%");refund.amount=15000;if(p.pace(food,apr,LocalDate.of(2025,4,12))!=null)throw new AssertionError("A bigger refund: 50%");
        p.entries.remove(refund);shop.amount=-60000;if(p.pace(food,apr,LocalDate.of(2025,4,12))!=null)throw new AssertionError("Overspent shows on its own");
        Budget.Category small=new Budget.Category("Small");p.categories.add(small);p.assign(small,apr,2000);Budget.Entry bit=new Budget.Entry("Shop",small.id,pb.id,"2025-04-01",-999);p.entries.add(bit);if(p.pace(small,apr,LocalDate.of(2025,4,1))!=null)throw new AssertionError("Under $10");bit.amount=-1000;equal(p.pace(small,apr,LocalDate.of(2025,4,1)).spent,50,"$10 of $20");
        Budget.Category loose=new Budget.Category("Loose");p.categories.add(loose);p.assign(loose,apr.minusMonths(1),50000);p.entries.add(new Budget.Entry("Shop",loose.id,pb.id,"2025-04-02",-35000));
        if(p.pace(loose,apr,LocalDate.of(2025,4,12))!=null)throw new AssertionError("No target and nothing assigned this month");loose.target=50000;equal(p.pace(loose,apr,LocalDate.of(2025,4,12)).spent,70,"A target: out of what came from last month");loose.snoozed=apr.toString();if(p.pace(loose,apr,LocalDate.of(2025,4,12))!=null)throw new AssertionError("A snoozed target with nothing assigned");
        Budget.Account pv=p.addCard("Visa","2025-01-01",0);Budget.Category pay=p.paymentCategory(pv);p.assign(pay,apr,50000);if(p.pace(pay,apr,LocalDate.of(2025,4,12))!=null)throw new AssertionError("Not for a card payment");
        Budget.Category bill=new Budget.Category("Rent bill");bill.targetType="Monthly";bill.target=180000;p.categories.add(bill);p.assign(bill,apr,180000);Budget.Entry paid=new Budget.Entry("Landlord",bill.id,pb.id,"2025-04-02",-180000);p.validate(paid);p.entries.add(paid);if(p.pace(bill,apr,LocalDate.of(2025,4,7))!=null)throw new AssertionError("A bill paid once a month isn't spending too fast");p.entries.remove(paid);
        // Pinned categories: up to 5, in plan order.
        Budget h=new Budget();for(int i=0;i<7;i++)h.categories.add(new Budget.Category("P"+i));for(int i=6;i>=2;i--)h.pin(h.categories.get(i),true);rejects(()->h.pin(h.categories.get(0),true));h.pin(h.categories.get(2),true);
        StringBuilder pins=new StringBuilder();for(Budget.Category x:h.pinned())pins.append(x.name);same(pins.toString(),"P2P3P4P5P6","Pinned, in plan order");h.pin(h.categories.get(4),false);h.pin(h.categories.get(0),true);pins.setLength(0);for(Budget.Category x:h.pinned())pins.append(x.name);same(pins.toString(),"P0P2P3P5P6","Unpin, then pin another");
        // Due within 7 days: overdue ones and Planner's bills too, soonest first.
        LocalDate today=LocalDate.of(2025,4,10);Budget d=new Budget();for(String[] x:new String[][]{{"Late","2025-04-07"},{"Week","2025-04-17"},{"Later","2025-04-18"},{"Today","2025-04-10"}})d.scheduled.add(new Budget.Scheduled(x[0],"","",x[1],-100,"Never"));d.fromPlanner.add(new Budget.Scheduled("Power","","","2025-04-12",-100,"Never"));
        StringBuilder due=new StringBuilder();for(Budget.Scheduled x:d.dueWithin(today,7))due.append(x.payee).append(",");same(due.toString(),"Late,Today,Power,Week,","Overdue, today, Planner's, up to 7 days");
    }
    static Budget.Entry transfer(Budget.Account from,Budget.Account to,String date,long amount){Budget.Entry e=new Budget.Entry("Transfer to "+to.name,"",from.id,date,amount);e.destination=to.id;return e;}
    static void batch2(){
        YearMonth jan=YearMonth.of(2025,1),feb=jan.plusMonths(1),mar=feb.plusMonths(1);
        // Tracking accounts are off budget: not cash, To budget, categories, income or spending. Net worth counts them (an asset +, a debt -).
        Budget b=new Budget();Budget.Account bank=new Budget.Account("Bank","2025-01-01",500000);b.accounts.add(bank);Budget.Account visa=b.addCard("Visa","2025-01-01",0);
        Budget.Category food=new Budget.Category("Food"),extra=new Budget.Category("Extra repayments");b.categories.add(food);b.categories.add(extra);b.assign(food,jan,100000);b.assign(extra,jan,200000);
        long ready=b.ready(jan),cash=b.cash(jan),worth=b.netWorth(jan);
        Budget.Account shares=b.addTracking("Shares","2025-01-01",1000000,false),home=b.addTracking("Mortgage","2025-01-01",40000000,true);equal(home.opening,-40000000,"A debt is a negative balance");rejects(()->b.addTracking("Bad","2025-01-01",-1,false));
        equal(b.cash(jan),cash,"Tracking openings aren't cash");equal(b.ready(jan),ready,"Nor To budget");equal(b.netWorth(jan),worth+1000000-40000000,"Net worth: asset +, debt -");
        Budget.Entry rise=b.valueUpdate(shares,1050000,"2025-01-20");equal(rise.amount,50000,"Value update: the difference");if(!rise.category.isEmpty()||!rise.cleared)throw new AssertionError("Value update: cleared, no category");b.validate(rise);b.entries.add(0,rise);
        Budget.Entry interest=new Budget.Entry("Interest","",home.id,"2025-01-31",-200000);b.validate(interest);b.entries.add(0,interest);
        equal(b.cash(jan),cash,"Tracking transactions aren't cash");equal(b.ready(jan),ready,"Nor To budget");equal(b.income(jan),0,"Nor income");equal(b.spending(jan),0,"Nor spending");equal(b.activity(food,jan),0,"Nor category activity");
        equal(b.netWorth(jan),worth+1050000-40200000,"Net worth follows them");if(b.valueUpdate(shares,1050000,"2025-01-21")!=null)throw new AssertionError("Already at that value");equal(b.valueUpdate(home,40100000,"2025-01-31").amount,100000,"A debt's value update: owed less");
        rejects(()->b.validate(new Budget.Entry("Shop",food.id,shares.id,"2025-01-05",-100)));rejects(()->b.valueUpdate(bank,1,"2025-01-05"));
        rejects(()->b.validate(new Budget.Scheduled("Broker",food.id,shares.id,"2025-03-01",-100,"Never")));
        // Out of the budget into a tracking account: spending from a category (it needs one). Cash leaves; To budget doesn't change.
        Budget.Entry pay=new Budget.Entry("Transfer to Mortgage","",bank.id,"2025-02-01",-50000);pay.destination=home.id;rejects(()->b.validate(pay));pay.category=b.paymentCategory(visa).id;rejects(()->b.validate(pay));pay.category=extra.id;b.validate(pay);b.entries.add(0,pay);
        equal(b.activity(extra,feb),-50000,"Out of the category");equal(b.spending(feb),50000,"Spending");equal(b.income(feb),0,"Not income");equal(b.cash(feb),cash-50000,"Cash leaves");equal(b.ready(feb),ready,"To budget unchanged");equal(b.balance(home,false),-40150000,"Less owed");equal(b.netWorth(feb),b.netWorth(jan),"Moving money keeps net worth");
        // Into the budget from a tracking account: income to To budget (no category).
        Budget.Entry sell=new Budget.Entry("Transfer to Bank","",shares.id,"2025-02-10",-30000);sell.destination=bank.id;b.validate(sell);b.entries.add(0,sell);
        equal(b.income(feb),30000,"Income");equal(b.spending(feb),50000,"Not spending");equal(b.ready(feb),ready+30000,"To budget grows");equal(b.cash(feb),cash-20000,"Cash arrives");sell.category=food.id;rejects(()->b.validate(sell));sell.category="";
        // With a credit card: out to tracking is card spending from a category; in from tracking pays the card down and frees money to To budget.
        Budget.Entry viaCard=new Budget.Entry("Transfer to Shares","",visa.id,"2025-02-12",-10000);viaCard.destination=shares.id;viaCard.category=food.id;b.validate(viaCard);b.entries.add(0,viaCard);
        equal(b.activity(food,feb),-10000,"Card to tracking: from the category");equal(b.available(b.paymentCategory(visa),feb),10000,"Moved to the card's payment category");equal(b.cash(feb),cash-20000,"Card spending isn't cash");
        Budget.Entry intoCard=new Budget.Entry("Transfer to Visa","",shares.id,"2025-02-14",-4000);intoCard.destination=visa.id;b.validate(intoCard);b.entries.add(0,intoCard);equal(b.income(feb),34000,"Into a card from tracking: income");equal(b.balance(visa,false),-6000,"Card owes less");
        // Between tracking accounts, or within the budget: no category, nothing for the budget.
        Budget.Entry between=new Budget.Entry("Transfer to Mortgage","",shares.id,"2025-02-20",-5000);between.destination=home.id;b.validate(between);b.entries.add(0,between);equal(b.income(feb),34000,"Tracking to tracking: not income");equal(b.spending(feb),60000,"Nor spending");between.category=food.id;rejects(()->b.validate(between));between.category="";
        Budget.Account save=new Budget.Account("Save","2025-01-01",0);b.accounts.add(save);Budget.Entry inside=new Budget.Entry("Transfer to Save","",bank.id,"2025-02-20",-100);inside.destination=save.id;inside.category=food.id;rejects(()->b.validate(inside));
        balanced(b,jan,mar,"Tracking and crossing transfers balanced");
        if(!b.csv().contains("2025-02-01,Transfer to Mortgage,Extra repayments,Everyday,Bank,Mortgage,-500.00,,No\r\n"))throw new AssertionError("A transfer out to tracking exports its category");
        // Loan payoff: monthly interest (balance x rate / 12, half up to the cent); the last payment is what's left.
        Budget.Payoff hand=Budget.payoff(100000,new BigDecimal("12"),50000,0);equal(hand.months,3,"$1,000 at 12% with $500: 3 months");equal(hand.interest,1525,"Interest $10.00 + $5.10 + $0.15");
        Budget.Payoff loan=Budget.payoff(10000000,new BigDecimal("5"),106066,0);equal(loan.months,120,"$100,000 at 5% with $1,060.66 a month: the 10-year loan");equal(loan.interest,2727847,"Its interest to the cent");if(Math.abs(loan.interest-2727907)>100)throw new AssertionError("Within $1 of the published $27,279.07");
        Budget.Payoff base=Budget.payoff(20000000,new BigDecimal("6"),119910,0),faster=Budget.payoff(20000000,new BigDecimal("6"),119910,10000);
        equal(base.months,361,"$200,000 at 6% with $1,199.10: 30 years and a last few cents");if(Math.abs(base.interest-23167638)>100)throw new AssertionError("Within $1 of the published $231,676.38: "+base.interest);
        equal(faster.months,295,"$100 extra");same(Budget.duration(base.months-faster.months),"5 years 6 months","Sooner");equal(base.interest-faster.interest,4913886,"Less interest");
        Budget.Payoff stuck=Budget.payoff(20000000,new BigDecimal("6"),100000,0);if(stuck.covers||stuck.finished)throw new AssertionError("$1,000 doesn't cover $1,000 interest");
        Budget.Payoff slow=Budget.payoff(20000000,new BigDecimal("6"),100001,0);if(!slow.covers||slow.finished||slow.months!=Budget.PAYOFF_MAX_MONTHS)throw new AssertionError("Covers it, but not within 100 years");
        if(!Budget.payoff(20000000,new BigDecimal("6"),100000,50000).covers)throw new AssertionError("The extra covers it");equal(Budget.payoff(0,new BigDecimal("6"),100,0).months,0,"Nothing owed");equal(Budget.payoff(120000,BigDecimal.ZERO,10000,0).interest,0,"No interest at 0%");
        equal(Budget.perMonth(50000,"Weekly"),216667,"$500 a week = $2,166.67 a month");equal(Budget.perMonth(100000,"Every 2 weeks"),216667,"Fortnightly");equal(Budget.perMonth(1234,"Monthly"),1234,"Monthly");
        same(Budget.duration(27),"2 years 3 months","Duration");same(Budget.duration(12),"1 year","One year");same(Budget.duration(1),"1 month","One month");same(Budget.duration(0),"0 months","None");
        // Flags and filters: each filter narrows, and they combine.
        Budget f=new Budget();Budget.Account a1=new Budget.Account("Everyday","2025-01-01",0),a2=new Budget.Account("Bills","2025-01-01",0);f.accounts.add(a1);f.accounts.add(a2);Budget.Category groc=new Budget.Category("Groceries"),work=new Budget.Category("Work");f.categories.add(groc);f.categories.add(work);
        Budget.Entry e1=new Budget.Entry("Coles",groc.id,a1.id,"2025-01-03",-5000),e2=new Budget.Entry("Officeworks",work.id,a1.id,"2025-01-10",-2000),e3=new Budget.Entry("Coles",groc.id,a2.id,"2025-02-01",-3000),e4=new Budget.Entry("Transfer to Bills","",a1.id,"2025-01-15",-10000),e5=new Budget.Entry("Kmart",Budget.SPLIT,a2.id,"2025-01-20",-3000);
        e1.flag=4;e1.cleared=true;e2.flag=4;e2.memo="Printer ink";e3.flag=1;e3.cleared=true;e4.destination=a2.id;e5.splits.add(new Budget.Split(groc.id,-1000));e5.splits.add(new Budget.Split(work.id,-2000));for(Budget.Entry e:new Budget.Entry[]{e1,e2,e3,e4,e5}){f.validate(e);f.entries.add(e);}
        Budget.Filter q=new Budget.Filter();if(q.any())throw new AssertionError("A new filter is empty");equal(f.filter(q).size(),5,"No filter: all");if(f.filter(q).get(0)!=e3)throw new AssertionError("Newest first");
        q.flag=4;ids(f.filter(q),"Officeworks,Coles","Green flag");if(!q.any())throw new AssertionError("A flag is a filter");q.cleared=1;ids(f.filter(q),"Coles","Green and cleared");q.cleared=0;ids(f.filter(q),"Officeworks","Green and uncleared");
        q=new Budget.Filter();q.flag=0;ids(f.filter(q),"Kmart,Transfer to Bills","No flag");
        q=new Budget.Filter();q.account=a2.id;ids(f.filter(q),"Coles,Kmart,Transfer to Bills","Account, transfers in included");q=new Budget.Filter();q.category=work.id;ids(f.filter(q),"Kmart,Officeworks","Category, split parts included");
        q=new Budget.Filter();q.text="INK";ids(f.filter(q),"Officeworks","Text in the note");q.text="work";ids(f.filter(q),"Kmart,Officeworks","Text in category names");q.text="everyday";ids(f.filter(q),"Transfer to Bills,Officeworks,Coles","Text in the account name");
        q=new Budget.Filter();q.from="2025-01-10";q.to="2025-01-31";ids(f.filter(q),"Kmart,Transfer to Bills,Officeworks","Date range, inclusive");
        q.account=a1.id;q.text="o";ids(f.filter(q),"Transfer to Bills,Officeworks","Account, dates and text together");q.flag=4;ids(f.filter(q),"Officeworks","And a flag");q.category=groc.id;ids(f.filter(q),"","Nothing matches all");
        f.flagNames[4]="Tax";same(f.flagLabel(4),"Green · Tax","Named flag");same(f.flagLabel(1),"Red","Unnamed flag");same(f.flagLabel(0),"None","No flag");same(f.flagLabel(9),"None","Out of range");
        // Review: imported rows wait; manual ones don't. A transfer is one transaction in both accounts: approving it approves both sides.
        Budget r=new Budget();Budget.Account r1=new Budget.Account("Everyday","2025-01-01",0),r2=new Budget.Account("Save","2025-01-01",0);r.accounts.add(r1);r.accounts.add(r2);Budget.Category shop=new Budget.Category("Shopping");r.categories.add(shop);
        Budget.Entry manual=new Budget.Entry("Shop",shop.id,r1.id,"2025-01-02",-100);r.entries.add(manual);if(!manual.approved)throw new AssertionError("Manual entries are approved");
        java.util.List<java.util.List<String>> rows=new java.util.ArrayList<>();rows.add(java.util.Arrays.asList("03/01/2025","Cafe","-5.00"));rows.add(java.util.Arrays.asList("04/01/2025","Pay","1000.00"));
        CsvImport.Result in=CsvImport.run(r,rows,false,0,1,2,-1,"d/M/uuuu",r1);equal(in.added,2,"Imported");for(Budget.Entry e:in.entries)if(e.approved)throw new AssertionError("Imported rows wait for review");equal(r.toReview().size(),2,"Two to review");
        Budget.Entry move=new Budget.Entry("Transfer to Save","",r1.id,"2025-01-05",-50);move.destination=r2.id;move.approved=false;r.entries.add(move);Budget.Filter onSave=new Budget.Filter();onSave.account=r2.id;
        if(r.filter(onSave).get(0).approved)throw new AssertionError("Unapproved in the other account too");r.approve(move);if(!r.filter(onSave).get(0).approved||r.toReview().contains(move))throw new AssertionError("Approving one side approves the other");
        r.approve(in.entries.get(0));equal(r.toReview().size(),1,"One left");equal(r.approveAll(),1,"Approve all");equal(r.toReview().size(),0,"None left");
        // Payees: rename (transactions and upcoming ones; transfers keep theirs), merge, hide from suggestions.
        Budget p=new Budget();Budget.Account pb=new Budget.Account("Bank","2025-01-01",0),ps=new Budget.Account("Coles card","2025-01-01",0);p.accounts.add(pb);p.accounts.add(ps);
        for(String[] x:new String[][]{{"Coles","2025-01-02"},{"COLES ","2025-01-03"},{"Woolworths","2025-01-04"},{"Woolies","2025-01-05"},{"Aldi","2025-01-06"}})p.entries.add(new Budget.Entry(x[0],"",pb.id,x[1],-100));
        Budget.Entry tr=new Budget.Entry("Coles","",pb.id,"2025-01-07",-100);tr.destination=ps.id;p.entries.add(tr);p.scheduled.add(new Budget.Scheduled("coles","",pb.id,"2025-01-31",-100,"Monthly"));
        equal(p.renamePayee("coles","Coles Supermarket"),3,"Two transactions and an upcoming one");same(tr.payee,"Coles","A transfer keeps its name");same(p.scheduled.get(0).payee,"Coles Supermarket","Upcoming renamed");if(!p.payees().contains("Coles Supermarket")||p.payees().contains("Coles"))throw new AssertionError("Renamed in suggestions");
        rejects(()->p.renamePayee("Aldi"," "));p.hidePayee("woolies",true);if(p.payees().contains("Woolies")||!p.allPayees().contains("Woolies")||!p.hiddenPayee(" WOOLIES"))throw new AssertionError("Hidden from suggestions only");
        p.renamePayee("Woolies","Woolies Metro");if(!p.hiddenPayee("woolies metro"))throw new AssertionError("A new name stays hidden");
        equal(p.mergePayees(java.util.Arrays.asList("Woolies Metro","Woolworths"),"Woolworths"),1,"Merged into the one kept");if(!p.payees().contains("Woolworths")||p.allPayees().contains("Woolies Metro")||p.hiddenPayee("Woolworths")||!p.hiddenPayees.isEmpty())throw new AssertionError("Merge keeps the visible payee");
        p.hidePayee("Aldi",true);p.hidePayee("ALDI",false);if(p.hiddenPayee("Aldi"))throw new AssertionError("Unhide");
        // Import rules (in CSV import): payee contains the text, ignoring capitals; the first match wins; then the usual last-category guess.
        Budget ir=new Budget();Budget.Account ib=new Budget.Account("Bank","2025-01-01",0);ir.accounts.add(ib);Budget.Category gro=new Budget.Category("Groceries"),car=new Budget.Category("Transport"),fun=new Budget.Category("Fun");ir.categories.add(gro);ir.categories.add(car);ir.categories.add(fun);
        ir.rules.add(new Budget.Rule("woolworths","Woolworths",gro.id));ir.rules.add(new Budget.Rule("WOOL","Wool shop",""));ir.rules.add(new Budget.Rule("uber","",car.id));ir.rules.add(new Budget.Rule("cafe","Corner cafe",""));
        if(ir.rule("WOOLWORTHS 1234 SYDNEY")!=ir.rules.get(0)||ir.rule("Woolen mills")!=ir.rules.get(1)||ir.rule("Bunnings")!=null)throw new AssertionError("First match, any capitals");
        java.util.List<java.util.List<String>> st=new java.util.ArrayList<>();for(String[] x:new String[][]{{"03/01/2025","WOOLWORTHS 1234 SYDNEY","-50.00"},{"04/01/2025","Uber *Trip","-12.00"},{"05/01/2025","Woolen mills","-30"},{"06/01/2025","Bunnings","-5"},{"07/01/2025","CAFE","-4"}})st.add(java.util.Arrays.asList(x));
        CsvImport.Result ru=CsvImport.run(ir,st,false,0,1,2,-1,"d/M/uuuu",ib);equal(ru.added,5,"All imported");equal(ru.matchedRules,4,"4 matched rules");
        java.util.Map<String,Budget.Entry> by=new java.util.HashMap<>();for(Budget.Entry e:ru.entries)by.put(e.date,e);
        same(by.get("2025-01-03").payee+"|"+ir.category(by.get("2025-01-03").category).name,"Woolworths|Groceries","Rename and category");
        same(by.get("2025-01-04").payee+"|"+ir.category(by.get("2025-01-04").category).name,"Uber *Trip|Transport","Category only");
        same(by.get("2025-01-05").payee+"|"+ir.category(by.get("2025-01-05").category).name,"Wool shop|"+CsvImport.TO_CATEGORIZE,"Rename only: no category known yet");
        same(by.get("2025-01-06").payee+"|"+ir.category(by.get("2025-01-06").category).name,"Bunnings|"+CsvImport.TO_CATEGORIZE,"No rule");
        // Renamed rows are still found as duplicates; after a rename-only rule, the usual guess goes by the new name.
        ir.entries.add(new Budget.Entry("Corner cafe",fun.id,ib.id,"2025-01-08",-1));st.add(java.util.Arrays.asList("08/01/2025","cafe 2","-7"));
        CsvImport.Result again=CsvImport.run(ir,st,false,0,1,2,-1,"d/M/uuuu",ib);equal(again.duplicates,5,"Renamed rows are duplicates");equal(again.added,1,"Only the new row");same(again.entries.get(0).payee+"|"+ir.category(again.entries.get(0).category).name,"Corner cafe|Fun","Guessed from the new name");
        rejects(()->ir.validate(new Budget.Rule(" ","X","")));rejects(()->ir.validate(new Budget.Rule("x","","")));rejects(()->ir.validate(new Budget.Rule("x","","missing")));Budget.Rule ok=new Budget.Rule(" shell ","  Shell ","");ir.validate(ok);same(ok.contains+"|"+ok.rename,"shell|Shell","Trimmed");
        ir.deleteCategory(car,fun);same(ir.rules.get(2).category,fun.id,"A deleted category's rules move with its transactions");
    }
    static void ids(java.util.List<Budget.Entry> list,String payees,String message){StringBuilder s=new StringBuilder();for(Budget.Entry e:list)s.append(s.length()>0?",":"").append(e.payee);same(s.toString(),payees,message);}
    static void fixes(){
        YearMonth jan=YearMonth.of(2025,1);
        // A To budget inflow on a card (a reward) frees set-aside money: To budget plus categories stays the cash, and what's set aside matches what's owed.
        Budget b=new Budget();Budget.Account bank=new Budget.Account("Bank","2025-01-01",10000);b.accounts.add(bank);Budget.Category food=new Budget.Category("Food");b.categories.add(food);
        Budget.Account visa=b.addCard("Visa","2025-01-01",0);Budget.Category pay=b.paymentCategory(visa);
        b.assign(food,jan,10000);b.entries.add(new Budget.Entry("Shop",food.id,visa.id,"2025-01-05",-10000));equal(b.available(pay,jan),10000,"Funded spending set aside");
        Budget.Entry reward=new Budget.Entry("Cashback","",visa.id,"2025-01-06",1000);b.validate(reward);b.entries.add(reward);
        equal(b.ready(jan),1000,"Reward to To budget");equal(b.available(pay,jan),9000,"Set aside = owed");equal(-b.balance(visa,false),9000,"Card owes less");
        equal(b.ready(jan)+b.available(food,jan)+b.available(pay,jan),b.balance(bank,false),"To budget + categories = cash (before paying)");
        Budget.Entry payment=new Budget.Entry("Transfer to Visa","",bank.id,"2025-01-07",-Math.min(-b.balance(visa,false),b.available(pay,jan)));payment.destination=visa.id;b.validate(payment);b.entries.add(payment);
        equal(b.balance(visa,false),0,"Paid off");equal(b.balance(bank,false),1000,"Cash left");equal(b.available(pay,jan),0,"Payment category used up");
        equal(b.ready(jan)+b.available(food,jan)+b.available(pay,jan),b.balance(bank,false),"To budget + categories = cash (after paying)");
        // Mirror: a negative reconcile adjustment on a card sets more aside from To budget, once.
        b=new Budget();bank=new Budget.Account("Bank","2025-01-01",20000);b.accounts.add(bank);food=new Budget.Category("Food");b.categories.add(food);visa=b.addCard("Visa","2025-01-01",0);pay=b.paymentCategory(visa);
        b.assign(food,jan,10000);Budget.Entry shop=new Budget.Entry("Shop",food.id,visa.id,"2025-01-05",-10000);shop.cleared=true;b.entries.add(shop);
        Budget.Entry adj=b.adjustment(visa,b.balance(visa,true)-1000,"2025-01-06");equal(adj.amount,-1000,"Card adjustment");b.validate(adj);b.entries.add(adj);
        equal(b.ready(jan),9000,"Taken from To budget");equal(b.available(pay,jan),11000,"Set aside = owed");equal(b.ready(jan)+b.available(food,jan)+b.available(pay,jan),b.balance(bank,false),"To budget + categories = cash (adjusted)");
        payment=new Budget.Entry("Transfer to Visa","",bank.id,"2025-01-07",-11000);payment.destination=visa.id;b.entries.add(payment);
        equal(b.available(pay,jan),0,"Not overspent after paying");equal(b.ready(jan),9000,"Taken only once");equal(b.ready(jan)+b.available(food,jan)+b.available(pay,jan),b.balance(bank,false),"To budget + categories = cash (adjusted, paid)");
        // Budget reset: the dialog's count and total are what the reset returns (card payment money left out).
        b=new Budget();bank=new Budget.Account("Bank","2025-01-01",100000);b.accounts.add(bank);food=new Budget.Category("Food");Budget.Category rent=new Budget.Category("Rent");b.categories.add(food);b.categories.add(rent);visa=b.addCard("Visa","2025-01-01",0);pay=b.paymentCategory(visa);
        b.assign(food,jan,30000);b.assign(rent,jan,5000);b.entries.add(new Budget.Entry("Shop",food.id,visa.id,"2025-01-05",-10000));equal(b.available(pay,jan),10000,"Payment category has money");
        java.util.Map<Budget.Category,Long> back=b.resetAmounts(jan);long total=0;for(long v:back.values())total+=v;equal(back.size(),2,"Reset counts Food and Rent only");if(back.containsKey(pay))throw new AssertionError("Payment category not counted");
        equal(b.planReset(jan),total,"Reset returns what the dialog says");equal(total,25000,"Reset total");
        // Funded spending over three cards: the shares add up to the funded amount, to the cent.
        b=new Budget();bank=new Budget.Account("Bank","2025-01-01",100);b.accounts.add(bank);food=new Budget.Category("Food");b.categories.add(food);b.assign(food,jan,100);
        Budget.Account[] cards={b.addCard("A","2025-01-01",0),b.addCard("B","2025-01-01",0),b.addCard("C","2025-01-01",0)};long moved=0,ready=b.ready(jan);
        for(Budget.Account c:cards)b.entries.add(new Budget.Entry("Shop",food.id,c.id,"2025-01-05",-100));for(Budget.Account c:cards)moved+=b.movedToCard(food,jan,c);
        equal(moved,100,"Shares add up to the funded $1.00");equal(b.ready(jan),ready,"No cent leaks into To budget");
        // Overdue upcoming bills (here one of Planner's from last month) count in this month's need and come first.
        YearMonth now=YearMonth.now();b=new Budget();Budget.Category power=new Budget.Category("Power");b.categories.add(power);power.dueDay=20;
        Budget.Scheduled late=new Budget.Scheduled("Electricity",power.id,"",now.minusMonths(1).atDay(15).toString(),-5000,"Never");b.fromPlanner.add(late);
        equal(b.upcoming(power,now),5000,"Overdue bill needed this month");equal(b.fundNeed(power,now),5000,"Fund targets covers it");equal(b.firstDue(power,now),0,"Overdue comes first");
        equal(b.upcoming(power,now.minusMonths(1)),5000,"Still in its own month");equal(b.upcoming(power,now.plusMonths(1)),0,"Not in later months");
        Budget.Scheduled rentDue=new Budget.Scheduled("Rent",power.id,"",now.minusMonths(1).atDay(1).toString(),-1000,"Monthly");b.scheduled.add(rentDue);equal(b.datesIn(rentDue,now).size(),2,"Last month's unpaid date and this month's");
        // CSV import: identical rows in one statement are separate transactions; each row already there matches one.
        Budget c=new Budget();bank=new Budget.Account("Bank","2026-01-01",0);c.accounts.add(bank);food=new Budget.Category("Food");c.categories.add(food);c.entries.add(new Budget.Entry("Shop",food.id,bank.id,"2026-09-01",-500));
        java.util.List<java.util.List<String>> twice=CsvImport.parse("1/9/2026,Shop,-5.00\n1/9/2026,Shop,-5.00\n2/9/2026,Cafe,-3.00\n2/9/2026,Cafe,-3.00\n");
        CsvImport.Result r=CsvImport.run(c,twice,false,0,1,2,-1,"d/M/uuuu",bank);equal(r.added,3,"Second Shop and both Cafes added");equal(r.duplicates,1,"One Shop already there");
        r=CsvImport.run(c,twice,false,0,1,2,-1,"d/M/uuuu",bank);equal(r.added,0,"Importing again adds nothing");equal(r.duplicates,4,"All four known");
    }
    /** To budget + every category = cash, in each month from [from] to [to]. */
    static void balanced(Budget b,YearMonth from,YearMonth to,String message){for(YearMonth m=from;!m.isAfter(to);m=m.plusMonths(1)){long n=b.ready(m);for(Budget.Category c:b.categories)n+=b.available(c,m)+b.creditOverspent(c,m);equal(n,b.cash(m),message+" "+m);}}
    static void fixes2(){
        YearMonth sep=YearMonth.of(2025,9),oct=sep.plusMonths(1),nov=oct.plusMonths(1),dec=nov.plusMonths(1);
        // M1 A: a card refund after the card was paid leaves the payment category below zero (the card is in credit): it carries on.
        Budget b=new Budget();Budget.Account bank=new Budget.Account("Bank","2025-09-01",100000);b.accounts.add(bank);Budget.Category food=new Budget.Category("Groceries");b.categories.add(food);
        Budget.Account visa=b.addCard("Visa","2025-09-01",0);Budget.Category pay=b.paymentCategory(visa);
        b.assign(food,sep,10000);b.entries.add(new Budget.Entry("Shop",food.id,visa.id,"2025-09-05",-5000));Budget.Entry paid=new Budget.Entry("Transfer to Visa","",bank.id,"2025-09-20",-5000);paid.destination=visa.id;b.validate(paid);b.entries.add(paid);
        equal(b.available(pay,sep),0,"A: paid");equal(b.ready(sep),90000,"A: Sep To budget");
        b.entries.add(new Budget.Entry("Refund",food.id,visa.id,"2025-10-03",2000));equal(b.available(food,oct),7000,"A: refund back to Groceries");equal(b.available(pay,oct),-2000,"A: card in credit");equal(b.ready(oct),90000,"A: Oct To budget");
        equal(b.available(pay,nov),-2000,"A: credit carries on");equal(b.ready(nov),90000,"A: Nov To budget unchanged");equal(b.ready(dec),90000,"A: Dec To budget unchanged");
        equal(b.toCover(pay,oct),0,"A: nothing to cover");equal(b.toPay(visa),0,"A: nothing to pay");balanced(b,sep,dec,"A balanced");
        // M1 B: a reward to To budget on a card with old debt and nothing set aside: assignable, and stays so.
        b=new Budget();bank=new Budget.Account("Bank","2025-09-01",100000);b.accounts.add(bank);food=new Budget.Category("Groceries");b.categories.add(food);visa=b.addCard("Visa","2025-09-01",50000);pay=b.paymentCategory(visa);
        b.entries.add(new Budget.Entry("Cashback","",visa.id,"2025-10-05",1000));equal(b.ready(oct),101000,"B: Oct To budget");b.assign(food,oct,101000);
        equal(b.ready(oct),0,"B: all assigned");equal(b.ready(nov),0,"B: Nov To budget stays 0");equal(b.available(pay,nov),-1000,"B: carries on");equal(b.toCover(pay,oct),0,"B: nothing to cover");balanced(b,sep,dec,"B balanced");
        // M1: paying more than was set aside (old debt, nothing assigned) is still overspending: covered, or taken from next month's To budget.
        b=new Budget();bank=new Budget.Account("Bank","2025-09-01",100000);b.accounts.add(bank);visa=b.addCard("Visa","2025-09-01",50000);pay=b.paymentCategory(visa);
        Budget.Entry over=new Budget.Entry("Transfer to Visa","",bank.id,"2025-10-10",-60000);over.destination=visa.id;b.entries.add(over);
        equal(b.available(pay,oct),-60000,"Overpaid");equal(b.toCover(pay,oct),50000,"The $500 old debt is overspending to cover");equal(b.available(pay,nov),-10000,"The $100 card credit carries on");equal(b.ready(nov),50000,"Rest taken from To budget");balanced(b,sep,dec,"Overpaid balanced");
        // M2: a card payment isn't spending (Reports, quick assign).
        equal(b.spent(pay,oct),0,"Payment category spent");
        // M3: a new repeating transaction entered now keeps its photo and Cleared tick.
        Budget.Scheduled rent=new Budget.Scheduled("Rent","",bank.id,"2025-10-01",-1000,"Monthly");Budget.Entry e=b.enter(rent,"receipt.jpg",true);
        same(e.photo,"receipt.jpg","Photo kept");if(!e.cleared||b.entries.get(0)!=e)throw new AssertionError("Cleared kept");same(rent.next,"2025-11-01","Repeat continues");
        // M4: Pay card fills in what's set aside this month, not in the month on screen.
        b=new Budget();bank=new Budget.Account("Bank","2025-01-01",100000);b.accounts.add(bank);food=new Budget.Category("Food");b.categories.add(food);visa=b.addCard("Visa","2025-01-01",0);pay=b.paymentCategory(visa);
        YearMonth jan=YearMonth.of(2025,1),now=YearMonth.now();b.assign(food,jan,10000);b.entries.add(new Budget.Entry("Shop",food.id,visa.id,"2025-01-05",-10000));
        b.assign(food,now,5000);b.entries.add(new Budget.Entry("Shop",food.id,visa.id,java.time.LocalDate.now().toString(),-5000));equal(b.toPay(visa),15000,"Set aside now");
    }
    static void phaseG(){
        YearMonth oct=YearMonth.of(2026,10);
        Budget b=new Budget();Budget.Account bank=new Budget.Account("Bank","2026-01-01",100000);b.accounts.add(bank);Budget.Category power=new Budget.Category("Utilities"),phone=new Budget.Category("Phone"),spare=new Budget.Category("Spare");b.categories.add(power);b.categories.add(phone);b.categories.add(spare);
        Budget.Account visa=b.addCard("Visa","2026-01-01",0);
        // Category for a Planner bill: chosen here first, else its last expense's, else none.
        Budget.Entry paid=new Budget.Entry("Electricity",power.id,bank.id,"2026-09-09",-14000);paid.billKey="planner-series-e";b.entries.add(paid);
        same(b.plannerCategory("planner-series-e"),power.id,"From the last expense");same(b.plannerCategory("planner-bill-9"),"","Unknown bill");
        b.billCategories.put("planner-bill-9",phone.id);same(b.plannerCategory("planner-bill-9"),phone.id,"Chosen here");b.billCategories.put("planner-series-e",phone.id);same(b.plannerCategory("planner-series-e"),phone.id,"Choice wins over history");
        b.billCategories.put("planner-bill-8",b.paymentCategory(visa).id);same(b.plannerCategory("planner-bill-8"),"","Never a card payment category");
        // Planner's bills count as upcoming bills (not money), by their category; no category or no amount counts nothing.
        Budget.Scheduled e=new Budget.Scheduled("Electricity",b.plannerCategory("planner-series-e"),"","2026-10-09",-15000,"Never");Budget.Scheduled unknown=new Budget.Scheduled("Water","","","2026-10-12",-5000,"Never");Budget.Scheduled noAmount=new Budget.Scheduled("Gas",phone.id,"","2026-10-03",0,"Never");
        b.fromPlanner.add(e);b.fromPlanner.add(unknown);b.fromPlanner.add(noAmount);long cash=b.cash(oct);
        equal(b.upcoming(phone,oct),15000,"Planner bill planned from Phone");equal(b.fundNeed(phone,oct),15000,"Fund targets covers it");equal(b.cash(oct),cash,"Not money");equal(b.firstDue(phone,oct),9,"Earliest Planner bill with an amount");
        if(!b.due(java.time.LocalDate.of(2026,12,1)).isEmpty())throw new AssertionError("Planner bills are never due to enter here");
        // Deleting a category moves (or forgets) the bills planned from it.
        b.deleteCategory(phone,spare);same(b.billCategories.get("planner-bill-9"),spare.id,"Moved with the category");
        Budget.Category gone=new Budget.Category("Gone");b.categories.add(gone);b.billCategories.put("planner-bill-7",gone.id);b.deleteCategory(gone,null);if(b.billCategories.containsKey("planner-bill-7"))throw new AssertionError("Forgotten with an unused category");
    }
    static void phaseF(){
        // CSV reading: quotes, commas and line breaks in fields, BOM, CRLF.
        java.util.List<java.util.List<String>> rows=CsvImport.parse("﻿Date,Description,Amount\r\n3/10/2026,\"Coles, Sydney\",-45.10\r\n\"4/10/2026\",\"Say \"\"hi\"\"\nline 2\",\"$1,200.00\"\r\n\r\n");
        equal(rows.size(),3,"Rows (blank line skipped)");same(rows.get(1).get(1),"Coles, Sydney","Quoted comma");same(rows.get(2).get(1),"Say \"hi\"\nline 2","Quotes and line break");same(rows.get(0).get(0),"Date","BOM removed");
        equal(CsvImport.amount("-45.10"),-4510,"Signed");equal(CsvImport.amount("$1,200.00"),120000,"Dollar sign and comma");equal(CsvImport.amount("(12.50)"),-1250,"Brackets");equal(CsvImport.amount("12.50 DR"),-1250,"Debit");equal(CsvImport.amount("12.50 CR"),1250,"Credit");
        if(!CsvImport.looksLikeHeader(rows.get(0))||CsvImport.looksLikeHeader(rows.get(1)))throw new AssertionError("Header detection");
        same(CsvImport.detectDateFormat(rows,0,true),"d/M/uuuu","Australian dates");
        same(CsvImport.detectDateFormat(CsvImport.parse("12/31/2025\n1/2/2026\n"),0,false),"M/d/uuuu","US dates when day/month can't read them");same(CsvImport.detectDateFormat(CsvImport.parse("2026-10-03\n"),0,false),"uuuu-MM-dd","ISO");
        if(CsvImport.detectDateFormat(CsvImport.parse("yesterday\n"),0,false)!=null)throw new AssertionError("Unreadable dates");
        // Import: payee memory, To categorize, duplicates, future and early rows skipped.
        Budget b=new Budget();Budget.Account bank=new Budget.Account("Bank","2026-01-01",100000);b.accounts.add(bank);Budget.Category food=new Budget.Category("Food");b.categories.add(food);
        Budget.Entry old=new Budget.Entry("Coles",food.id,bank.id,"2026-09-01",-1000);b.entries.add(old);Budget.Entry dup=new Budget.Entry("Netflix",food.id,bank.id,"2026-10-02",-1699);b.entries.add(dup);
        String future=java.time.LocalDate.now().plusDays(3).format(java.time.format.DateTimeFormatter.ofPattern("d/M/uuuu"));
        java.util.List<java.util.List<String>> statement=CsvImport.parse("Date,Payee,Amount\n3/10/2026,COLES,-45.10\n2/10/2026,Netflix,-16.99\n4/10/2026,Employer,2500.00\n5/10/2026,Hardware,-30\n"+future+",Later,-1\n1/12/2025,Early,-1\nnot a date,X,-1\n");
        CsvImport.Result r=CsvImport.run(b,statement,true,0,1,2,-1,"d/M/uuuu",bank);
        equal(r.added,3,"Added");equal(r.duplicates,1,"Duplicate skipped");equal(r.future,1,"Future skipped");equal(r.beforeOpening,1,"Before opening skipped");equal(r.unreadable,1,"Unreadable row");
        Budget.Entry coles=r.entries.get(0);same(coles.category,food.id,"Payee memory (case-insensitive)");if(!coles.cleared)throw new AssertionError("Imported rows are cleared");
        same(r.entries.get(1).category,"","Income to Ready to Assign");Budget.Category tc=b.category(r.entries.get(2).category);same(tc.name,CsvImport.TO_CATEGORIZE,"Unknown payee to To categorize");
        CsvImport.Result again=CsvImport.run(b,statement,true,0,1,2,-1,"d/M/uuuu",bank);equal(again.added,0,"Importing twice adds nothing");equal(again.duplicates,4,"All known");equal(b.categories.size(),2,"One To categorize category");
        // Separate money-in and money-out columns.
        CsvImport.Result split=CsvImport.run(b,CsvImport.parse("6/10/2026,Shop,,12.00\n6/10/2026,Refund,3.00,\n"),false,0,1,2,3,"d/M/uuuu",bank);equal(split.added,2,"In/out columns");equal(split.entries.get(0).amount,-1200,"Out column is negative");equal(split.entries.get(1).amount,300,"In column positive");
    }
    static void phaseE(){
        YearMonth jan=YearMonth.of(2025,1),feb=jan.plusMonths(1);
        Budget b=new Budget();Budget.Account bank=new Budget.Account("Bank","2025-01-01",100000);b.accounts.add(bank);
        Budget.Category food=new Budget.Category("Food"),fun=new Budget.Category("Fun");b.categories.add(food);b.categories.add(fun);
        Budget.Account visa=b.addCard("Visa","2025-01-01",50000);Budget.Category pay=b.paymentCategory(visa);
        if(pay==null||!pay.payment()||!visa.credit()||!pay.group.equals("Credit card payments"))throw new AssertionError("Card and payment category");
        equal(b.balance(visa,false),-50000,"Old debt owed");equal(b.cash(jan),100000,"Card debt isn't cash");
        b.assign(food,jan,30000);equal(b.ready(jan),70000,"Old debt doesn't touch Ready to Assign");equal(b.available(pay,jan),0,"Nothing set aside for old debt");
        // Funded card spending moves the money to the payment category.
        b.entries.add(new Budget.Entry("Shop",food.id,visa.id,"2025-01-05",-10000));
        equal(b.available(food,jan),20000,"Category spent");equal(b.available(pay,jan),10000,"Moved to the card's payment");equal(b.ready(jan),70000,"Ready unchanged");equal(b.cash(jan),100000,"Card spending isn't cash");
        // Unfunded card spending: credit overspending, then card debt; Ready to Assign never changes.
        b.entries.add(new Budget.Entry("Concert",fun.id,visa.id,"2025-01-06",-5000));
        equal(b.available(fun,jan),-5000,"Shows overspent this month");equal(b.creditOverspent(fun,jan),5000,"On credit");equal(b.available(pay,jan),10000,"Nothing moved for it");equal(b.ready(jan),70000,"Ready unchanged by credit overspending");
        equal(b.available(fun,feb),0,"Resets next month");equal(b.ready(feb),70000,"Became debt, not less Ready to Assign");
        // Cash overspending still reduces next month's Ready to Assign (unchanged rule).
        Budget.Category gifts=new Budget.Category("Gifts");b.categories.add(gifts);b.entries.add(new Budget.Entry("Gift",gifts.id,bank.id,"2025-01-07",-1000));equal(b.ready(jan),70000,"Cash overspending shows in the category");equal(b.ready(feb),69000,"Then reduces Ready to Assign");
        // A payment from the bank uses the payment category's money.
        Budget.Entry payment=new Budget.Entry("Transfer to Visa","",bank.id,"2025-02-10",-10000);payment.destination=visa.id;b.validate(payment);b.entries.add(payment);
        equal(b.cash(feb),89000,"Payment leaves cash");equal(b.available(pay,feb),0,"Payment category used");equal(b.ready(feb),69000,"Ready unchanged by a covered payment");
        // Paying off old debt: assign to the payment category from Ready to Assign, then pay.
        b.assign(pay,feb,20000);equal(b.ready(feb),49000,"Assigned for old debt");Budget.Entry payOld=new Budget.Entry("Transfer to Visa","",bank.id,"2025-02-11",-20000);payOld.destination=visa.id;b.entries.add(payOld);equal(b.available(pay,feb),0,"Paid");equal(b.ready(feb),49000,"Still");
        equal(b.balance(visa,false),-50000-10000-5000+10000+20000,"Card balance");
        // A refund on the card moves money back from the payment category.
        b.entries.add(new Budget.Entry("Refund",food.id,visa.id,"2025-02-12",2000));equal(b.available(food,feb),22000,"Refund back to the category");equal(b.available(pay,feb),-2000,"Out of the payment category");equal(b.ready(feb),49000,"Ready unchanged by a card refund");
        // Net worth includes card debt; spending/income reports include card spending.
        equal(b.netWorth(feb),b.balanceAt(bank,feb)+b.balanceAt(visa,feb),"Net worth is all accounts");equal(b.netWorth(feb),69000-35000+2000,"Bank 69000, card -33000");equal(b.spending(jan),16000,"Card spending is spending");
        // Age of Money counts the payment, not the card spending.
        equal(b.ageOfMoney(java.time.LocalDate.of(2025,1,31)),6,"Only the cash gift (6 days)");
        // Rules.
        rejects(()->b.validate(new Budget.Entry("x",pay.id,bank.id,"2025-02-13",-100)));rejects(()->b.deleteCategory(pay,null));rejects(()->b.deleteCategory(fun,pay));
        b.planReset(feb);equal(b.available(pay,feb),-2000,"Plan reset leaves card payment money");
        b.rename(visa,"Visa Gold");same(pay.name,"Visa Gold","Payment category follows the card's name");
        Budget.Account amex=b.addCard("Amex","2025-01-01",0);Budget.Category amexPay=b.paymentCategory(amex);b.assign(amexPay,feb,100);rejects(()->b.deleteAccount(amex));b.assign(amexPay,feb,-100);amexPay.assigned.clear();b.deleteAccount(amex);if(b.paymentCategory(amex)!=null||b.categories.contains(amexPay))throw new AssertionError("Payment category goes with its card");
        // Two cards: funded spending splits by card.
        Budget.Account mc=b.addCard("Mastercard","2025-01-01",0);b.assign(food,feb,5000);b.entries.add(new Budget.Entry("Shop",food.id,mc.id,"2025-02-14",-3000));equal(b.movedToCard(food,feb,mc),3000,"Moved to the right card");equal(b.movedToCard(food,feb,visa),-2000,"Refund only on Visa");
    }
    static void phaseD(){
        YearMonth jan=YearMonth.of(2025,1);
        Budget b=new Budget();Budget.Account bank=new Budget.Account("Bank","2025-01-01",100000);b.accounts.add(bank);Budget.Category food=new Budget.Category("Food"),home=new Budget.Category("Household");b.categories.add(food);b.categories.add(home);
        b.assign(food,jan,30000);b.assign(home,jan,10000);
        Budget.Entry shop=new Budget.Entry("Supermarket",Budget.SPLIT,bank.id,"2025-01-05",-9000);shop.splits.add(new Budget.Split(food.id,-7000));shop.splits.add(new Budget.Split(home.id,-2000));b.validate(shop);b.entries.add(shop);
        equal(b.activity(food,jan),-7000,"Food part");equal(b.activity(home,jan),-2000,"Household part");equal(b.spending(jan),9000,"Split is spending");equal(b.income(jan),0,"Not income");equal(b.cash(jan),91000,"Cash once");equal(b.balance(bank,false),91000,"Account once");
        // A part to Ready to Assign counts as income (e.g. cash back).
        Budget.Entry back=new Budget.Entry("Shop with cash back",Budget.SPLIT,bank.id,"2025-01-06",-1000);back.splits.add(new Budget.Split(food.id,-3000));back.splits.add(new Budget.Split("",2000));b.validate(back);b.entries.add(back);
        equal(b.income(jan),2000,"Ready to Assign part is income");equal(b.spending(jan),12000,"Category part is spending");
        // Validation.
        Budget.Entry bad=new Budget.Entry("x",Budget.SPLIT,bank.id,"2025-01-07",-1000);bad.splits.add(new Budget.Split(food.id,-600));bad.splits.add(new Budget.Split(home.id,-300));rejects(()->b.validate(bad));
        bad.splits.get(1).amount=-400;b.validate(bad);bad.splits.remove(1);bad.splits.get(0).amount=-1000;rejects(()->b.validate(bad));
        Budget.Entry zero=new Budget.Entry("x",Budget.SPLIT,bank.id,"2025-01-07",-1000);zero.splits.add(new Budget.Split(food.id,-1000));zero.splits.add(new Budget.Split(home.id,0));rejects(()->b.validate(zero));
        // CSV: one row per part, each with its own category and amount.
        String csv=b.csv();if(!csv.contains("2025-01-05,Supermarket,Food,Everyday,Bank,,-70.00,,No")||!csv.contains("2025-01-05,Supermarket,Household,Everyday,Bank,,-20.00,,No")||!csv.contains("2025-01-06,Shop with cash back,To budget,,Bank,,20.00,,No"))throw new AssertionError("Split CSV rows:\n"+csv);
        // Deleting a category moves split parts too.
        if(!b.used(home)||b.entriesIn(home)!=1)throw new AssertionError("Split part counts as use");b.deleteCategory(home,food);equal(b.activity(food,jan),-7000-2000-3000,"Parts moved");same(shop.splits.get(1).category,food.id,"Part's category moved");
    }
    static void phaseC(){
        java.time.LocalDate d=java.time.LocalDate.of(2025,1,31);
        Budget b=new Budget();Budget.Account bank=new Budget.Account("Bank","2025-01-01",100000);b.accounts.add(bank);Budget.Category rent=new Budget.Category("Rent"),food=new Budget.Category("Food");b.categories.add(rent);b.categories.add(food);
        // Monthly repeats keep their day: 31 Jan, 28 Feb, 31 Mar.
        Budget.Scheduled s=new Budget.Scheduled("Landlord",rent.id,bank.id,d.toString(),-50000,"Monthly");same(s.after(d).toString(),"2025-02-28","Short month");same(s.after(s.after(d)).toString(),"2025-03-31","Day kept");
        same(new Budget.Scheduled("x","",bank.id,"2025-01-01",1,"Every 2 weeks").after(java.time.LocalDate.of(2025,1,1)).toString(),"2025-01-15","Fortnightly");if(new Budget.Scheduled("x","",bank.id,"2025-01-01",1,"Never").after(d)!=null)throw new AssertionError("Never repeats");
        b.validate(s);b.scheduled.add(s);
        // Due, enter, skip.
        if(b.due(java.time.LocalDate.of(2025,1,30)).size()!=0||b.due(d).size()!=1)throw new AssertionError("Due on its day");
        Budget.Entry e=b.enter(s);same(e.date,"2025-01-31","Entered on its date");equal(e.amount,-50000,"Entered amount");same(s.next,"2025-02-28","Moves to the next date");equal(b.entries.size(),1,"One entry");
        b.advance(s);same(s.next,"2025-03-31","Skipped");equal(b.entries.size(),1,"Skip adds nothing");
        Budget.Scheduled once=new Budget.Scheduled("Gift",food.id,bank.id,"2025-01-10",-1000,"Never");b.scheduled.add(once);b.advance(once);if(b.scheduled.contains(once))throw new AssertionError("A one-off goes when skipped");
        // Upcoming bills feed Fund targets: need = upcoming - available, by the earliest date.
        YearMonth mar=YearMonth.of(2025,3);Budget.Scheduled weekly=new Budget.Scheduled("Shop",food.id,bank.id,"2025-03-03",-2000,"Weekly");b.scheduled.add(weekly);
        equal(b.datesIn(weekly,mar).size(),5,"Five Mondays in March 2025");equal(b.upcoming(food,mar),10000,"Weekly bills in the month");equal(b.upcoming(rent,mar),50000,"Monthly bill");
        b.assign(food,mar,4000);equal(b.fundNeed(food,mar),6000,"Upcoming minus available");food.target=20000;food.targetType="Monthly";equal(b.fundNeed(food,mar),16000,"Target need when larger");
        same(b.fundOrder(mar).get(0).name,"Food","Earliest bill first (3rd before 31st)");
        // Scheduled transactions count as use.
        Budget.Account spare=new Budget.Account("Spare","2025-01-01",0);b.accounts.add(spare);weekly.account=spare.id;rejects(()->b.close(spare));rejects(()->b.deleteAccount(spare));
        Budget.Category other=new Budget.Category("Other");b.categories.add(other);rejects(()->b.deleteCategory(food,null));b.deleteCategory(food,other);same(weekly.category,other.id,"Scheduled moves with a deleted category");
        // Validation: no zero, no unknown repeat, not before the account opened.
        rejects(()->b.validate(new Budget.Scheduled("x",rent.id,bank.id,"2025-05-01",0,"Monthly")));rejects(()->b.validate(new Budget.Scheduled("x",rent.id,bank.id,"2025-05-01",-1,"Daily")));rejects(()->b.validate(new Budget.Scheduled("x",rent.id,bank.id,"2024-12-01",-1,"Never")));
    }
    static void phaseB(){
        YearMonth jan=YearMonth.of(2025,1),feb=jan.plusMonths(1);
        Budget b=new Budget();Budget.Account bank=new Budget.Account("Bank","2025-01-01",100000);b.accounts.add(bank);
        Budget.Category rent=new Budget.Category("Rent"),phone=new Budget.Category("Phone"),food=new Budget.Category("Food");b.categories.add(rent);b.categories.add(phone);b.categories.add(food);
        rent.targetType="Monthly";rent.target=50000;rent.dueDay=28;phone.targetType="Monthly";phone.target=3000;phone.dueDay=3;food.targetType="Monthly";food.target=20000;
        // Snooze: nothing needed in that month only.
        equal(b.needed(rent,jan),50000,"Needed before snooze");rent.snoozed=jan.toString();equal(b.needed(rent,jan),0,"Snoozed month");equal(b.needed(rent,feb),50000,"Next month asks again");rent.snoozed="";
        // Fund order: earliest due day first, no day last, otherwise plan order.
        same(b.fundOrder(jan).get(0).name+","+b.fundOrder(jan).get(1).name+","+b.fundOrder(jan).get(2).name,"Phone,Rent,Food","Fund order by due day");
        // Plan reset returns every positive Available; overspent stays.
        b.assign(rent,jan,40000);b.assign(food,jan,30000);b.entries.add(new Budget.Entry("Shop",food.id,bank.id,"2025-01-11",-10000));
        Budget.Entry over=new Budget.Entry("Bill",phone.id,bank.id,"2025-01-12",-500);b.entries.add(over);
        long cash=b.cash(jan);equal(b.planReset(jan),60000,"Reset total");equal(b.available(rent,jan),0,"Rent emptied");equal(b.available(food,jan),0,"Food emptied");equal(b.available(phone,jan),-500,"Overspending stays");equal(b.cash(jan),cash,"Reset keeps cash");equal(b.ready(jan),cash+500,"All back to Ready to Assign");
        rejects(()->b.planReset(YearMonth.now().plusMonths(1)));
        equal(b.netWorth(jan),100000-10000-500,"Net worth is cash");
        // Age of Money: oldest money first.
        Budget a=new Budget();Budget.Account acc=new Budget.Account("Bank","2025-01-01",100000);a.accounts.add(acc);Budget.Category c=new Budget.Category("Stuff");a.categories.add(c);
        equal(a.ageOfMoney(java.time.LocalDate.of(2025,3,1)),-1,"No outflows yet");
        a.entries.add(new Budget.Entry("A",c.id,acc.id,"2025-01-11",-10000));a.entries.add(new Budget.Entry("Pay","",acc.id,"2025-02-01",50000));a.entries.add(new Budget.Entry("B",c.id,acc.id,"2025-02-11",-100000));
        equal(a.ageOfMoney(java.time.LocalDate.of(2025,1,31)),10,"First outflow: 10 days");equal(a.ageOfMoney(java.time.LocalDate.of(2025,3,1)),24,"Average of 10 and 37.9 days");
        Budget.Entry move=new Budget.Entry("Transfer","",acc.id,"2025-02-12",-1000);move.destination=acc.id;a.entries.add(move);equal(a.ageOfMoney(java.time.LocalDate.of(2025,3,1)),24,"Transfers aren't spending");
    }
    static void batchOne(){
        YearMonth jan=YearMonth.of(2025,1),feb=jan.plusMonths(1),mar=feb.plusMonths(1),apr=mar.plusMonths(1);
        Budget b=new Budget();Budget.Account bank=new Budget.Account("Bank","2025-01-01",100000);b.accounts.add(bank);
        Budget.Category food=new Budget.Category("Food"),eat=new Budget.Category("Eating out"),rent=new Budget.Category("Rent"),spare=new Budget.Category("Spare");rent.group="Bills";b.categories.add(food);b.categories.add(rent);b.categories.add(eat);b.categories.add(spare);
        b.assign(food,jan,30000);b.assign(eat,jan,10000);b.assign(eat,feb,5000);
        Budget.Entry dinner=new Budget.Entry("Cafe",eat.id,bank.id,"2025-01-10",-4000);dinner.billKey="planner-series-x";b.entries.add(dinner);b.entries.add(new Budget.Entry("Shop",food.id,bank.id,"2025-02-03",-12000));
        long cash=b.cash(feb),ready=b.ready(feb),both=b.available(food,feb)+b.available(eat,feb);
        // Delete: history and money move; totals stay; Planner's bill follows to the new category.
        if(!b.used(eat)||b.used(spare))throw new AssertionError("Used category");rejects(()->b.deleteCategory(eat,null));rejects(()->b.deleteCategory(eat,eat));
        b.deleteCategory(eat,food);equal(b.categories.size(),3,"Category removed");if(!dinner.category.equals(food.id)||b.lastForBill("planner-series-x").category!=food.id)throw new AssertionError("Transactions moved");
        equal(b.assigned(food,jan),40000,"Assignments merged");equal(b.assigned(food,feb),5000,"Later month merged");equal(b.cash(feb),cash,"Cash unchanged");equal(b.ready(feb),ready,"Ready unchanged");equal(b.available(food,feb),both,"Available combined");
        b.deleteCategory(spare,null);equal(b.categories.size(),2,"Unused category deleted");
        // Reorder only within a group.
        Budget.Category fuel=new Budget.Category("Fuel");b.categories.add(fuel);if(!b.reorder(fuel,-1)||b.categories.indexOf(fuel)!=0||b.reorder(fuel,-1)||!b.reorder(fuel,1)||b.categories.get(2)!=fuel)throw new AssertionError("Reorder in group");if(b.reorder(rent,1)||b.reorder(rent,-1))throw new AssertionError("Bills group has one category");
        // Hiding changes nothing in the sums.
        food.hidden=true;equal(b.ready(feb),ready,"Hidden money still counts");food.hidden=false;
        // Accounts: close at $0, delete only unused.
        Budget.Account wallet=new Budget.Account("Wallet","2025-01-01",0),old=new Budget.Account("Old","2025-01-01",500);b.accounts.add(wallet);b.accounts.add(old);
        b.close(wallet);if(!wallet.closed)throw new AssertionError("Closed");rejects(()->b.close(old));rejects(()->b.deleteAccount(bank));b.deleteAccount(old);equal(b.accounts.size(),2,"Unused account deleted");
        // Reconcile: an adjustment for the difference, cleared, to Ready to Assign.
        if(b.adjustment(bank,b.balance(bank,true),"2025-02-10")!=null)throw new AssertionError("No adjustment when equal");
        Budget.Entry adj=b.adjustment(bank,b.balance(bank,true)-250,"2025-02-10");equal(adj.amount,-250,"Adjustment amount");if(!adj.cleared||!adj.category.isEmpty()||!adj.account.equals(bank.id))throw new AssertionError("Adjustment fields");
        b.validate(adj);b.entries.add(adj);long readyBefore=b.ready(feb);equal(b.balance(bank,true),100000-250,"Cleared now matches the bank");equal(readyBefore,ready-250,"Adjustment goes to Ready to Assign");
        // Quick assign amounts.
        equal(b.spent(food,feb),12000,"Spent last month");equal(b.spent(food,jan),4000,"Spent moved with the history");equal(b.averageSpent(food,apr),(0+12000+4000)/3,"Average of three months");
        equal(b.resetChange(food,jan),-36000,"Reset returns only what's still available");equal(b.resetChange(food,mar),0,"Nothing assigned");
        // Payees: newest first, case-insensitive, transfers left out.
        b.entries.add(new Budget.Entry("cafe",food.id,bank.id,"2025-02-20",-500));Budget.Entry move=new Budget.Entry("Transfer to Wallet","",bank.id,"2025-02-21",-1);move.destination=wallet.id;b.entries.add(move);
        same(String.join("|",b.payees()),"cafe|Reconciliation adjustment|Shop","Payees: newest spelling, no transfers");if(b.lastForPayee(" CAFE ").date.compareTo("2025-02-20")!=0)throw new AssertionError("Newest for payee");
        b.rename(wallet,"Purse");same(move.payee,"Transfer to Purse","Transfer payee follows a rename");same(wallet.name,"Purse","Renamed");
    }
    static void same(String actual,String expected,String message){if(!actual.equals(expected))throw new AssertionError(message+":\n"+actual+"\n!=\n"+expected);}
    static void suggestions(){
        Budget b=new Budget();Budget.Account bank=new Budget.Account("Bank","2025-01-01",0);b.accounts.add(bank);
        // Groups: plan order, each once (first spelling), card payment groups left out; typed groups take the existing spelling.
        Budget.Category rent=new Budget.Category("Rent"),food=new Budget.Category("Food"),power=new Budget.Category("Power"),fun=new Budget.Category("Fun");
        rent.group="Bills";food.group="Everyday";power.group="bills";fun.group=" Wants ";for(Budget.Category c:new Budget.Category[]{rent,food,power,fun})b.categories.add(c);b.addCard("Visa","2025-01-01",0);
        same(String.join("|",b.groups()),"Bills|Everyday|Wants","Groups in plan order, once each");
        same(b.existingGroup("bills"),"Bills","Typed group takes the existing spelling");same(b.existingGroup(" EVERYDAY "),"Everyday","Trimmed, any capitals");
        same(b.existingGroup(" Travel "),"Travel","A new group is kept");same(b.existingGroup("wants"),"Wants","Spelling without spaces");same(b.existingGroup("credit card PAYMENTS"),"Credit card payments","Card group spelling");
        Budget.Category solo=new Budget.Category("Gym");solo.group="health";b.categories.add(solo);same(b.existingGroup("Health",solo),"Health","Editing the only category in a group can respell it");same(b.existingGroup("Health"),"health","Another category joins it");
        // Notes: this payee's first, then the rest; newest first, each once, no blanks or automatic ones, at most 50.
        String[][] rows={{"Shop","2025-01-02","Weekly shop"},{"Cafe","2025-01-05","Lunch"},{"Shop","2025-01-09","Milk"},{"Cafe","2025-01-10","weekly shop"},{"Shop","2025-01-03",""},{"Bank feed","2025-01-11","Imported"},{"Shop","2025-01-01","Party"}};
        for(String[] r:rows){Budget.Entry e=new Budget.Entry(r[0],food.id,bank.id,r[1],-100);e.memo=r[2];b.entries.add(e);}
        same(String.join("|",b.memos(" shop ")),"Milk|Weekly shop|Party|Lunch","Payee's notes first, then others, newest first");
        same(String.join("|",b.memos("")),"weekly shop|Milk|Lunch|Party","No payee: newest first, each once");same(String.join("|",b.memos(null)),"weekly shop|Milk|Lunch|Party","Null payee");
        for(int i=0;i<60;i++){Budget.Entry e=new Budget.Entry("Many","",bank.id,"2025-02-01",100);e.memo="Note "+i;b.entries.add(e);}equal(b.memos("Shop").size(),50,"At most 50 notes");same(b.memos("Shop").get(0),"Milk","Payee's notes still first");
    }
    static void hunt21(){
        YearMonth jan=YearMonth.of(2025,1),feb=jan.plusMonths(1);
        // M1: a re-imported statement is spotted by the bank's own payee text, even after the payee was renamed or merged.
        Budget b=new Budget();Budget.Account bank=new Budget.Account("Bank","2025-01-01",100000);b.accounts.add(bank);Budget.Category food=new Budget.Category("Food");b.categories.add(food);
        b.rules.add(new Budget.Rule("WOOL","Woolworths",""));java.util.List<java.util.List<String>> rows=CsvImport.parse("2025-01-03,SHOP 123,-45.00\n2025-01-04,WOOLWORTHS 99,-20.00\n2025-01-05,Cafe,-7.00\n");
        Budget.Entry manual=new Budget.Entry("Cafe",food.id,bank.id,"2025-01-05",-700);b.entries.add(manual);
        CsvImport.Result first=CsvImport.run(b,rows,false,0,1,2,-1,"uuuu-MM-dd",bank);equal(first.added,2,"First import");equal(first.duplicates,1,"A manual entry still matches by payee");
        same(b.entries.get(1).bankPayee+"|"+b.entries.get(0).bankPayee,"SHOP 123|WOOLWORTHS 99","Statement text kept");same(b.entries.get(0).payee,"Woolworths","Rule renamed it");same(manual.bankPayee,"","Manual entries have none");
        b.renamePayee("shop 123","Corner shop");b.mergePayees(java.util.Collections.singletonList("Woolworths"),"Cafe");
        CsvImport.Result again=CsvImport.run(b,rows,false,0,1,2,-1,"uuuu-MM-dd",bank);equal(again.added,0,"Renamed and merged payees still spotted");equal(again.duplicates,3,"All three already there");
        Budget.Entry old=new Budget.Entry("Gym","",bank.id,"2025-01-06",-1000);b.entries.add(old);equal(CsvImport.run(b,CsvImport.parse("2025-01-06,Gym,-10.00\n2025-01-06,Gym,-10.00\n"),false,0,1,2,-1,"uuuu-MM-dd",bank).added,1,"Per-row counting kept");
        // M2: renaming or merging a payee updates import rules that rename to it.
        Budget p=new Budget();Budget.Account pb=new Budget.Account("Bank","2025-01-01",0);p.accounts.add(pb);p.entries.add(new Budget.Entry("Woolworths","",pb.id,"2025-01-02",-100));p.entries.add(new Budget.Entry("Coles","",pb.id,"2025-01-02",-100));
        p.rules.add(new Budget.Rule("WOOL","woolworths",""));p.rules.add(new Budget.Rule("COLES","Coles",""));p.rules.add(new Budget.Rule("X","","c"));
        p.renamePayee("Woolworths","Woolies");same(p.rules.get(0).rename,"Woolies","Rule follows a rename");p.mergePayees(java.util.Collections.singletonList("coles"),"Woolies");same(p.rules.get(1).rename,"Woolies","Rule follows a merge");same(p.rules.get(2).rename,"","Empty rename untouched");
        // M4: a balance target with a due month asks for its share and reaches 0 once that's assigned.
        Budget t=new Budget();t.accounts.add(new Budget.Account("Bank","2025-01-01",500000));Budget.Category car=new Budget.Category("Car");car.targetType="Balance";car.target=120000;car.due="2025-02";t.categories.add(car);
        equal(t.needed(car,jan),60000,"Half in January");t.assign(car,jan,60000);equal(t.needed(car,jan),0,"Assigned: nothing more");equal(t.needed(car,feb),60000,"The rest in February");t.assign(car,feb,60000);equal(t.needed(car,feb),0,"Reached");
        Budget.Category none=new Budget.Category("Fund");none.targetType="Balance";none.target=50000;t.categories.add(none);t.assign(none,jan,20000);equal(t.needed(none,jan),30000,"No due month: as before");
        // M5: a cash advance doesn't grow To budget: the borrowed money is set aside in the card's payment category to repay.
        Budget c=new Budget();Budget.Account cb=new Budget.Account("Bank","2025-01-01",10000);c.accounts.add(cb);Budget.Account card=c.addCard("Visa","2025-01-01",0);Budget.Category pay=c.paymentCategory(card);
        Budget.Entry advance=new Budget.Entry("Cash advance","",card.id,"2025-01-05",-4000);advance.destination=cb.id;c.validate(advance);c.entries.add(advance);
        equal(c.cash(jan),14000,"Cash is there");equal(c.ready(jan),10000,"To budget unchanged");equal(c.available(pay,jan),4000,"Set aside to repay");balanced(c,jan,feb,"Cash advance");
        Budget.Entry repay=new Budget.Entry("Pay Visa","",cb.id,"2025-01-20",-4000);repay.destination=card.id;c.validate(repay);c.entries.add(repay);equal(c.ready(jan),10000,"Repaid");equal(c.available(pay,jan),0,"Payment used");equal(c.balance(card,false),0,"Card paid off");balanced(c,jan,feb,"Repaid advance");
        // M6: by category and by group add up the same, and less refunds they're the month's spending.
        Budget s=new Budget();Budget.Account sb=new Budget.Account("Bank","2025-01-01",100000);s.accounts.add(sb);Budget.Category groc=new Budget.Category("Groceries"),dine=new Budget.Category("Dining");groc.group="Food";dine.group="Food";s.categories.add(groc);s.categories.add(dine);
        s.entries.add(new Budget.Entry("Shop",groc.id,sb.id,"2025-01-03",-10000));s.entries.add(new Budget.Entry("Refund",dine.id,sb.id,"2025-01-04",3000));
        long byCat=Budget.total(s.breakdown(jan,jan,false)),byGroup=Budget.total(s.breakdown(jan,jan,true));equal(byCat,byGroup,"Both views add up the same");equal(byCat-s.refunds(jan,jan),s.spending(jan),"Less refunds: spending");equal(s.spending(jan),7000,"Net spending");
        // M7: a tracking account's own entries export and search without a category.
        Budget k=new Budget();Budget.Account kb=new Budget.Account("Bank","2025-01-01",0);k.accounts.add(kb);Budget.Account shares=k.addTracking("Shares","2025-01-01",100000,false);Budget.Entry up=k.valueUpdate(shares,105000,"2025-01-10");k.validate(up);k.entries.add(up);
        same(k.csv(),"Date,Payee,Category,Group,Account,Transfer to,Amount,Note,Cleared\r\n2025-01-10,Balance update,,,Shares,,50.00,,Yes\r\n","Tracking entry has no category");
        Budget.Filter f=new Budget.Filter();f.text="to budget";equal(k.filter(f).size(),0,"Not found as To budget");k.entries.add(new Budget.Entry("Pay","",kb.id,"2025-01-11",100));equal(k.filter(f).size(),1,"A budget account's income still is");
        // M10: a balance update on a past date takes the difference from the balance on that date.
        Budget.Entry later=new Budget.Entry("Balance update","",shares.id,"2025-03-01",5000);k.entries.add(later);Budget.Entry back=k.valueUpdate(shares,120000,"2025-02-01");equal(back.amount,15000,"From the balance on 1 Feb (1,050.00)");
        // M9: two rules can't share their text (any capitals); a rule can be saved over itself.
        Budget.Rule uber=new Budget.Rule("uber","Uber","");k.rules.add(uber);rejects(()->k.validate(new Budget.Rule(" UBER ","Ride","")));k.validate(new Budget.Rule("Uber","Uber rides",""),uber);
        // M12: in a future month, Reset available isn't offered when it would take carried money; this month's Assigned can go.
        YearMonth now=YearMonth.now(),next=now.plusMonths(1);Budget r=new Budget();r.accounts.add(new Budget.Account("Bank","2025-01-01",100000));Budget.Category kept=new Budget.Category("Kept"),fresh=new Budget.Category("Fresh");r.categories.add(kept);r.categories.add(fresh);
        r.assign(kept,now,5000);equal(r.resetAvailableChange(kept,next),0,"Carried money: not offered");equal(r.resetAvailableChange(kept,now),-5000,"This month: offered");
        r.assign(fresh,next,3000);long z=r.resetAvailableChange(fresh,next);equal(z,-3000,"Only this future month's");r.assign(fresh,next,z);equal(r.available(fresh,next),0,"Assign takes it");
        // M13: hidden categories don't show on Home or count toward the pins.
        Budget h=new Budget();for(int i=0;i<6;i++)h.categories.add(new Budget.Category("C"+i));for(int i=0;i<5;i++)h.pin(h.categories.get(i),true);h.categories.get(0).hidden=true;
        equal(h.pinned().size(),4,"Hidden left out");h.pin(h.categories.get(5),true);equal(h.pinned().size(),5,"Room for another");if(h.pinned().contains(h.categories.get(0)))throw new AssertionError("Hidden pinned shown");
    }
    static void hunt22(){
        YearMonth jan=YearMonth.of(2025,1);
        // M1: a row imported before bankPayee was kept (note "Imported", no bankPayee) keeps its statement text when renamed, so it isn't imported again.
        Budget b=new Budget();Budget.Account bank=new Budget.Account("Bank","2025-01-01",100000);b.accounts.add(bank);Budget.Category groc=new Budget.Category("Groceries");b.categories.add(groc);
        Budget.Entry legacy=new Budget.Entry("SHOP 9",groc.id,bank.id,"2025-01-03",-500);legacy.memo=Budget.IMPORTED;b.entries.add(legacy);Budget.Entry manual=new Budget.Entry("Cafe","",bank.id,"2025-01-03",-300);b.entries.add(manual);
        same(Budget.statementPayee(legacy),"SHOP 9","Legacy import: its payee");same(Budget.statementPayee(manual),"","Manual: none");
        b.renamePayee("shop 9","Corner shop");same(legacy.bankPayee,"SHOP 9","Kept before the rename");b.renamePayee("Cafe","Coffee");same(manual.bankPayee,"","Manual still none");
        equal(CsvImport.run(b,CsvImport.parse("2025-01-03,SHOP 9,-5.00\n"),false,0,1,2,-1,"uuuu-MM-dd",bank).duplicates,1,"Renamed legacy row still spotted");
        // M2: a statement payee seen before takes its row's new name and category.
        Budget.Entry wool=new Budget.Entry("WOOLWORTHS 123",groc.id,bank.id,"2025-01-04",-2000);wool.bankPayee="WOOLWORTHS 123";b.entries.add(0,wool);b.renamePayee("WOOLWORTHS 123","Woolies");
        CsvImport.Result r=CsvImport.run(b,CsvImport.parse("2025-01-10,woolworths 123,-12.00\n"),false,0,1,2,-1,"uuuu-MM-dd",bank);equal(r.added,1,"New row");same(r.entries.get(0).payee,"Woolies","Renamed payee");same(r.entries.get(0).category,groc.id,"Its category");same(r.entries.get(0).bankPayee,"woolworths 123","Own statement text");
        b.rules.add(new Budget.Rule("WOOLWORTHS","Woolworths",""));same(CsvImport.run(b,CsvImport.parse("2025-01-11,WOOLWORTHS 123,-13.00\n"),false,0,1,2,-1,"uuuu-MM-dd",bank).entries.get(0).payee,"Woolworths","A rule's rename comes first");
        // M10: statement amounts over $100 million are unreadable, like typed ones.
        equal(CsvImport.amount("100,000,000.00"),10_000_000_000L,"$100 million");rejectsAny(()->CsvImport.amount("100000000.01"));rejectsAny(()->CsvImport.amount("(100000000.01)"));
        equal(CsvImport.run(b,CsvImport.parse("2025-01-12,Big,-100000000.01\n"),false,0,1,2,-1,"uuuu-MM-dd",bank).unreadable,1,"Over the cap: unreadable");
        // M4: a yearly repeat from 29 Feb comes back to the 29th in leap years.
        Budget.Scheduled y=new Budget.Scheduled("Rego","",bank.id,"2024-02-29",-100,"Yearly");LocalDate d=LocalDate.of(2024,2,29);for(int i=0;i<4;i++)d=y.after(d);same(d.toString(),"2028-02-29","Back on the 29th");same(y.after(LocalDate.of(2024,2,29)).toString(),"2025-02-28","28th in other years");
        same(new Budget.Scheduled("x","",bank.id,"2025-03-15",-100,"Yearly").after(LocalDate.of(2025,3,15)).toString(),"2026-03-15","Other dates as before");
        // M7: a card payment category below zero by card credit isn't offered a reset; overspending still is.
        Budget c=new Budget();Budget.Account cb=new Budget.Account("Bank","2025-01-01",10000);c.accounts.add(cb);Budget.Account card=c.addCard("Visa","2025-01-01",0);Budget.Category pay=c.paymentCategory(card),food=new Budget.Category("Food");c.categories.add(food);
        Budget.Entry refund=new Budget.Entry("Refund",food.id,card.id,"2025-01-05",5000);c.validate(refund);c.entries.add(refund);equal(c.available(pay,jan),-5000,"Card credit");equal(c.resetAvailableChange(pay,jan),0,"Nothing offered for card credit");
        Budget.Entry over=new Budget.Entry("Shop",food.id,cb.id,"2025-01-06",-2500);c.entries.add(over);equal(c.resetAvailableChange(food,jan),-c.available(food,jan),"Spending category as before");
        // M8: split evenly adds up for negative totals too.
        long[] parts=Budget.splitEvenly(-1000,3);equal(parts[0],-334,"Leftover cent first");equal(parts[1],-333,"Second");equal(parts[0]+parts[1]+parts[2],-1000,"Adds up");
        long sum=0;for(long p:Budget.splitEvenly(-10,4))sum+=p;equal(sum,-10,"-10 over 4");
        // M9: unhiding a pinned category when Home already has 5 unpins it; with room it stays pinned.
        Budget h=new Budget();for(int i=0;i<6;i++)h.categories.add(new Budget.Category("C"+i));for(int i=0;i<5;i++)h.pin(h.categories.get(i),true);h.setHidden(h.categories.get(0),true);h.pin(h.categories.get(5),true);
        if(!h.setHidden(h.categories.get(0),false)||h.categories.get(0).pinned||h.pinned().size()!=5)throw new AssertionError("Unhidden over the limit: unpinned");
        h.setHidden(h.categories.get(1),true);if(h.setHidden(h.categories.get(1),false)||!h.categories.get(1).pinned)throw new AssertionError("With room: stays pinned");
    }
    static void rejectsAny(Runnable action){try{action.run();}catch(RuntimeException e){return;}throw new AssertionError("Expected rejection");}
    static void csv(){
        Budget b=new Budget();Budget.Account bank=new Budget.Account("Bank, main","2025-01-01",0),cash=new Budget.Account("Cash","2025-01-01",0);b.accounts.add(bank);b.accounts.add(cash);
        Budget.Category odd=new Budget.Category("=SUM(A1)");odd.group="Bills";b.categories.add(odd);
        Budget.Entry quoted=new Budget.Entry("Say \"hi\"",odd.id,bank.id,"2025-01-03",-1234);quoted.memo="-note\nline 2";quoted.cleared=true;b.entries.add(quoted);
        b.entries.add(new Budget.Entry("Pay","",bank.id,"2025-01-05",250000));
        Budget.Entry move=new Budget.Entry("Transfer to Cash","",bank.id,"2025-01-01",-500);move.destination=cash.id;b.entries.add(move);
        same(b.csv(),"Date,Payee,Category,Group,Account,Transfer to,Amount,Note,Cleared\r\n"
            +"2025-01-05,Pay,To budget,,\"Bank, main\",,2500.00,,No\r\n"
            +"2025-01-03,\"Say \"\"hi\"\"\",'=SUM(A1),Bills,\"Bank, main\",,-12.34,\"'-note\nline 2\",Yes\r\n"
            +"2025-01-01,Transfer to Cash,,,\"Bank, main\",Cash,-5.00,,No\r\n","CSV rows: newest first, quoted, formulas kept as text");
        same(new Budget().csv(),"Date,Payee,Category,Group,Account,Transfer to,Amount,Note,Cleared\r\n","Empty budget exports the header");
    }
    // Data safety: Home's backup reminder and its snooze, when a delete can still be undone, the daily automatic backup.
    static void dataSafety(){
        LocalDate today=LocalDate.of(2026,10,8);
        if(!DataSafety.backupReminderDue(true,null,null,today))throw new AssertionError("Never backed up: remind");
        if(DataSafety.backupReminderDue(false,null,null,today))throw new AssertionError("No accounts yet: no reminder");
        if(DataSafety.backupReminderDue(true,"2026-09-25",null,today))throw new AssertionError("13 days: not yet");
        if(!DataSafety.backupReminderDue(true,"2026-09-24",null,today))throw new AssertionError("14 days: remind");
        if(DataSafety.backupReminderDue(true,"2026-10-08",null,today)||DataSafety.backupReminderDue(true,"2026-10-20",null,today))throw new AssertionError("Backed up today (or a date ahead): no reminder");
        if(!DataSafety.backupReminderDue(true,"not a date","also not",today))throw new AssertionError("Unreadable dates: never backed up, no snooze");
        equal(DataSafety.daysSince("2026-09-24",today),14,"Days since");equal(DataSafety.daysSince(null,today),-1,"Never");
        same(DataSafety.latest("2026-09-24","2026-10-01"),"2026-10-01","Automatic backup later");same(DataSafety.latest("2026-10-02",null),"2026-10-02","Only Back up budget");
        if(DataSafety.latest(null,"")!=null)throw new AssertionError("Neither: never");
        String until=DataSafety.snoozeUntil(today);same(until,"2026-10-15","A week");
        if(DataSafety.backupReminderDue(true,null,until,today)||DataSafety.backupReminderDue(true,null,until,today.plusDays(6)))throw new AssertionError("Snoozed for the week");
        if(!DataSafety.backupReminderDue(true,null,until,today.plusDays(7)))throw new AssertionError("Snooze over after a week");
        if(!DataSafety.backupReminderDue(true,null,"2027-01-01",today))throw new AssertionError("A snooze further than a week ahead (clock change) is ignored");
        // Undo of a delete: only while the saved data is what the delete saved, and on screen.
        if(!DataSafety.undoAllowed("after","after","after"))throw new AssertionError("Unchanged: undo");
        if(DataSafety.undoAllowed("after+planner","after","after"))throw new AssertionError("Saved meanwhile (Planner): no undo");
        if(DataSafety.undoAllowed("after","after","older")||DataSafety.undoAllowed(null,"after","after")||DataSafety.undoAllowed("x",null,"x"))throw new AssertionError("Screen out of date, data gone or nothing to undo: no undo");
        // Automatic backup: a new day writes a new file, the same day doesn't; the newest 7 are kept and other files left alone.
        if(!DataSafety.autoBackupDue(today,null)||!DataSafety.autoBackupDue(today,"2026-10-07"))throw new AssertionError("New day: back up");
        if(DataSafety.autoBackupDue(today,"2026-10-08"))throw new AssertionError("Same day: no second backup");
        same(DataSafety.autoBackupName(today),"MyBudget-auto-2026-10-08.json","Today's file");
        if(DataSafety.autoBackupName(today).equals(DataSafety.autoBackupName(today.plusDays(1))))throw new AssertionError("A new day's file is a new file");
        java.util.List<String> folder=new java.util.ArrayList<>(java.util.Arrays.asList("MyBudget-backup-2026-01-01.json","notes.txt","MyBudget-auto-2026-10-01.json"));
        for(int i=0;i<5;i++)folder.add(DataSafety.autoBackupName(today.minusDays(2+i)));
        if(!DataSafety.autoBackupsToDelete(folder,DataSafety.autoBackupName(today)).isEmpty())throw new AssertionError("6 old + today's = 7: none deleted");
        folder.add("MyBudget-auto-2026-09-20.json");folder.add(DataSafety.autoBackupName(today));
        same(String.join(",",DataSafety.autoBackupsToDelete(folder,DataSafety.autoBackupName(today))),"MyBudget-auto-2026-09-20.json","The oldest beyond 7 go; today's and other files stay");
    }
}
