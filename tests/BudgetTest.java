import com.mybudget.app.Budget;
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
        csv();batchOne();phaseB();phaseC();
        System.out.println("PASS: monthly accounting, rollover, targets, edits, transfers, clearing, future reservations, exact cents, sent payments, CSV export, category delete/reorder, account close/delete, reconcile adjustments, quick assign and payees.");
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
