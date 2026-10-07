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
        System.out.println("PASS: monthly accounting, rollover, targets, edits, transfers, clearing, future reservations, exact cents and sent payments.");
    }
}
