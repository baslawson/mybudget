import com.mybudget.app.Budget;
import com.mybudget.app.CsvImport;
import java.time.YearMonth;

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
        csv();batchOne();phaseB();phaseC();phaseD();phaseE();phaseF();
        System.out.println("PASS: monthly accounting, rollover, targets, edits, transfers, clearing, future reservations, exact cents, sent payments, CSV export, category delete/reorder, account close/delete, reconcile adjustments, quick assign and payees.");
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
        String csv=b.csv();if(!csv.contains("2025-01-05,Supermarket,Food,Everyday,Bank,,-70.00,,No")||!csv.contains("2025-01-05,Supermarket,Household,Everyday,Bank,,-20.00,,No")||!csv.contains("2025-01-06,Shop with cash back,Ready to Assign,,Bank,,20.00,,No"))throw new AssertionError("Split CSV rows:\n"+csv);
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
    static void csv(){
        Budget b=new Budget();Budget.Account bank=new Budget.Account("Bank, main","2025-01-01",0),cash=new Budget.Account("Cash","2025-01-01",0);b.accounts.add(bank);b.accounts.add(cash);
        Budget.Category odd=new Budget.Category("=SUM(A1)");odd.group="Bills";b.categories.add(odd);
        Budget.Entry quoted=new Budget.Entry("Say \"hi\"",odd.id,bank.id,"2025-01-03",-1234);quoted.memo="-note\nline 2";quoted.cleared=true;b.entries.add(quoted);
        b.entries.add(new Budget.Entry("Pay","",bank.id,"2025-01-05",250000));
        Budget.Entry move=new Budget.Entry("Transfer to Cash","",bank.id,"2025-01-01",-500);move.destination=cash.id;b.entries.add(move);
        same(b.csv(),"Date,Payee,Category,Group,Account,Transfer to,Amount,Note,Cleared\r\n"
            +"2025-01-05,Pay,Ready to Assign,,\"Bank, main\",,2500.00,,No\r\n"
            +"2025-01-03,\"Say \"\"hi\"\"\",'=SUM(A1),Bills,\"Bank, main\",,-12.34,\"'-note\nline 2\",Yes\r\n"
            +"2025-01-01,Transfer to Cash,,,\"Bank, main\",Cash,-5.00,,No\r\n","CSV rows: newest first, quoted, formulas kept as text");
        same(new Budget().csv(),"Date,Payee,Category,Group,Account,Transfer to,Amount,Note,Cleared\r\n","Empty budget exports the header");
    }
}
