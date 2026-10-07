package com.mybudget.app;

import android.app.Instrumentation;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import org.json.*;
import java.time.*;

/** Exercises Android's real JSON implementation and starts the actual Activity. */
public class BudgetInstrumentation extends Instrumentation {
    @Override public void onCreate(Bundle arguments){super.onCreate(arguments);start();}
    private void eq(long a,long b){if(a!=b)throw new AssertionError(a+" != "+b);}
    private View find(View v,String text,boolean button){if(v instanceof TextView&&((TextView)v).getText().toString().equals(text)&&(!button||v instanceof Button))return v;if(v instanceof ViewGroup){ViewGroup group=(ViewGroup)v;for(int i=0;i<group.getChildCount();i++){View found=find(group.getChildAt(i),text,button);if(found!=null)return found;}}return null;}
    @Override public void onStart(){Bundle result=new Bundle();try{
        YearMonth month=YearMonth.now();String day=month.atDay(1).toString();
        JSONObject legacy=new JSONObject().put("ready",10000).put("categories",new JSONArray().put(new JSONObject().put("name","Groceries").put("available",15000)).put(new JSONObject().put("name","Savings").put("available",50000))).put("entries",new JSONArray().put(new JSONObject().put("payee","Shop").put("category","Groceries").put("date",day).put("amount",-5000)));
        Budget migrated=BudgetStore.decode(legacy.toString());eq(migrated.cash(month),75000);eq(migrated.ready(month),10000);eq(migrated.available(migrated.categories.get(0),month),15000);eq(migrated.assigned(migrated.categories.get(0),month),20000);
        Budget.Category food=migrated.categories.get(0);food.target=25000;food.targetType="Balance";food.due=month.plusMonths(2).toString();food.group="Everyday";Budget.Account bank=migrated.accounts.get(0);bank.reconciled=day;
        Budget.Account savings=new Budget.Account("Savings account",day,0);migrated.accounts.add(savings);Budget.Entry transfer=new Budget.Entry("Transfer","",bank.id,day,-10000);transfer.destination=savings.id;transfer.cleared=true;transfer.memo="Moving cash";migrated.entries.add(transfer);
        Budget restored=BudgetStore.decode(BudgetStore.encode(migrated));eq(restored.cash(month),75000);eq(restored.ready(month),10000);eq(restored.categories.get(0).target,25000);eq(restored.balance(restored.accounts.get(1),false),10000);if(!restored.entries.get(1).memo.equals("Moving cash")||!restored.accounts.get(0).reconciled.equals(day))throw new AssertionError("Metadata lost");
        legacy.getJSONArray("entries").getJSONObject(0).put("date",month.minusMonths(1).atDay(1).toString());Budget previous=BudgetStore.decode(legacy.toString());eq(previous.ready(month),10000);eq(previous.available(previous.categories.get(0),month),15000);
        boolean rejected=false;try{BudgetStore.decode("{broken}");}catch(Exception expected){rejected=true;}if(!rejected)throw new AssertionError("Corrupt data accepted");
        Intent intent=new Intent(getTargetContext(),MainActivity.class);intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);Activity activity=startActivitySync(intent);waitForIdleSync();if(activity.isFinishing())throw new AssertionError("Activity closed");
        for(String tab:new String[]{"Budget","Transactions","Accounts","Reports","Home"}){runOnMainSync(()->{View button=find(activity.getWindow().getDecorView(),tab,true);if(button==null)throw new AssertionError("Missing tab "+tab);button.performClick();if(find(activity.getWindow().getDecorView(),tab,false)==null)throw new AssertionError("Missing screen "+tab);});waitForIdleSync();}
        sentPayments(day);
        dataSafety((MainActivity)activity,day);
        backups(migrated);
        // Hidden categories and closed accounts are saved; budgets saved before them read as visible and open.
        Budget flags=BudgetStore.decode(BudgetStore.encode(migrated));flags.categories.get(1).hidden=true;flags.accounts.get(1).closed=true;
        Budget flagsBack=BudgetStore.decode(BudgetStore.encode(flags));if(flagsBack.categories.get(0).hidden||!flagsBack.categories.get(1).hidden||flagsBack.accounts.get(0).closed||!flagsBack.accounts.get(1).closed)throw new AssertionError("Hidden/closed not saved");
        JSONObject older=new JSONObject(BudgetStore.encode(flags));older.getJSONArray("categories").getJSONObject(1).remove("hidden");older.getJSONArray("accounts").getJSONObject(1).remove("closed");
        Budget olderBack=BudgetStore.decode(older.toString());if(olderBack.categories.get(1).hidden||olderBack.accounts.get(1).closed)throw new AssertionError("Older budget not read as visible/open");
        // Weekly, by-date and debt targets and month notes round-trip (and in backups); budgets saved before them read with defaults.
        newTargets(flags);
        // Tracking accounts, loan terms, flags and their names, review marks, hidden payees and import rules round-trip; older budgets read with defaults.
        batch2(BudgetStore.decode(BudgetStore.encode(flags)));
        // Categories pinned to Home round-trip (and in backups); budgets saved before them read as not pinned.
        batch3(BudgetStore.decode(BudgetStore.encode(flags)));
        // Version 4: scheduled transactions are saved and read back; a newer version is refused.
        Budget.Scheduled sched=new Budget.Scheduled("Landlord",flags.categories.get(0).id,flags.accounts.get(0).id,"2026-01-31",-50000,"Monthly");sched.memo="Lease";sched.billKey="planner-series-r";flags.scheduled.add(sched);
        JSONObject v4=new JSONObject(BudgetStore.encode(flags));if(v4.getInt("version")!=5)throw new AssertionError("Not version 5");Budget.Scheduled back=BudgetStore.decode(v4.toString()).scheduled.get(0);
        // Version 4 data (MyBudget 0.0.5) still reads, with defaults for later fields.
        JSONObject was4=new JSONObject(v4.toString()).put("version",4);if(!BudgetStore.encode(BudgetStore.decode(was4.toString())).equals(BudgetStore.encode(flags)))throw new AssertionError("Version 4 not read");
        if(!back.id.equals(sched.id)||!back.next.equals("2026-01-31")||back.day!=31||!back.repeat.equals("Monthly")||back.amount!=-50000||!back.memo.equals("Lease")||!back.billKey.equals("planner-series-r"))throw new AssertionError("Scheduled lost data");
        // Splits round-trip; a split part with an unknown category, or a split marker without parts, is refused.
        Budget.Entry split=new Budget.Entry("Supermarket",Budget.SPLIT,flags.accounts.get(0).id,flags.accounts.get(0).date,-9000);split.splits.add(new Budget.Split(flags.categories.get(0).id,-7000));split.splits.add(new Budget.Split("",-2000));split.splits.get(0).memo="Food";flags.entries.add(split);
        Budget.Entry splitBack=BudgetStore.decode(BudgetStore.encode(flags)).entries.get(flags.entries.size()-1);if(splitBack.splits.size()!=2||splitBack.splits.get(0).amount!=-7000||!splitBack.splits.get(0).memo.equals("Food")||!splitBack.splits.get(1).category.isEmpty())throw new AssertionError("Split lost data");
        JSONObject badSplit=new JSONObject(BudgetStore.encode(flags));JSONObject last=badSplit.getJSONArray("entries").getJSONObject(flags.entries.size()-1);last.getJSONArray("splits").getJSONObject(0).put("category","missing");
        boolean refusedSplit=false;try{BudgetStore.decode(badSplit.toString());}catch(JSONException expected){refusedSplit=true;}last.remove("splits");boolean refusedMarker=false;try{BudgetStore.decode(badSplit.toString());}catch(JSONException expected){refusedMarker=true;}
        if(!refusedSplit||!refusedMarker)throw new AssertionError("Bad split accepted");flags.entries.remove(split);
        // Credit cards: type and payment category round-trip; a payment category without its card is refused.
        Budget.Account card=flags.addCard("Visa",flags.accounts.get(0).date,12345);Budget cardBack=BudgetStore.decode(BudgetStore.encode(flags));Budget.Account cardRead=cardBack.account(card.id);
        if(cardRead==null||!cardRead.credit()||cardRead.opening!=-12345||cardBack.paymentCategory(cardRead)==null||!cardBack.paymentCategory(cardRead).group.equals("Credit card payments"))throw new AssertionError("Card lost data");
        JSONObject orphan=new JSONObject(BudgetStore.encode(flags));JSONArray accs=orphan.getJSONArray("accounts");for(int i=0;i<accs.length();i++)if(accs.getJSONObject(i).getString("id").equals(card.id))accs.getJSONObject(i).put("type","cash");
        boolean refusedOrphan=false;try{BudgetStore.decode(orphan.toString());}catch(JSONException expected){refusedOrphan=true;}if(!refusedOrphan)throw new AssertionError("Payment category without a card accepted");
        flags.categories.remove(flags.paymentCategory(card));flags.accounts.remove(card);
        // Photos: the file name round-trips; anything that could be a path is refused.
        Budget.Entry withPhoto=flags.entries.get(0);withPhoto.photo="0b6f3c1e-9a2d-4f5e-8c7b-1a2b3c4d5e6f.jpg";if(!BudgetStore.decode(BudgetStore.encode(flags)).entries.get(0).photo.equals(withPhoto.photo))throw new AssertionError("Photo name lost");
        JSONObject badPhoto=new JSONObject(BudgetStore.encode(flags));badPhoto.getJSONArray("entries").getJSONObject(0).put("photo","../../shared_prefs/budget.xml");boolean refusedPhoto=false;try{BudgetStore.decode(badPhoto.toString());}catch(JSONException expected){refusedPhoto=true;}if(!refusedPhoto)throw new AssertionError("Photo path accepted");withPhoto.photo="";
        // Planner's bills: chosen categories round-trip; a list from Planner is checked and trimmed.
        flags.billCategories.put("planner-series-x",flags.categories.get(0).id);flags.billCategories.put("planner-bill-y","missing");Budget billsBack=BudgetStore.decode(BudgetStore.encode(flags));
        if(!flags.categories.get(0).id.equals(billsBack.billCategories.get("planner-series-x"))||billsBack.billCategories.containsKey("planner-bill-y"))throw new AssertionError("Bill categories");flags.billCategories.clear();
        JSONArray cleaned=new JSONArray(PlannerBills.clean("[{\"id\":\"planner-bill-1\",\"billKey\":\"planner-bill-1\",\"payee\":\" Water \",\"due\":\"2026-10-12\",\"amountCents\":5000},{\"id\":\"planner-bill-2\",\"billKey\":\"planner-bill-2\",\"payee\":\"Bad\",\"due\":\"2026-10-12\",\"amountCents\":-1},{\"id\":\"planner-bill-3\",\"billKey\":\"planner-bill-3\",\"payee\":\"Gas\",\"due\":\"2026-10-13\"}]"));
        if(cleaned.length()!=2||!cleaned.getJSONObject(0).getString("payee").equals("Water")||cleaned.getJSONObject(1).has("amountCents"))throw new AssertionError("Planner list not cleaned: "+cleaned);
        // A paid bill leaves the list: by its id; without one (older Planner), the earliest entry of the same bill.
        String list="[{\"id\":\"planner-bill-5\",\"billKey\":\"planner-series-s\",\"payee\":\"Rent\",\"due\":\"2026-11-01\"},{\"id\":\"planner-bill-4\",\"billKey\":\"planner-series-s\",\"payee\":\"Rent\",\"due\":\"2026-10-01\"},{\"id\":\"planner-bill-9\",\"billKey\":\"planner-bill-9\",\"payee\":\"Gas\",\"due\":\"2026-10-03\"}]";
        if(!new JSONArray(PlannerBills.without(list,"planner-bill-9","planner-bill-9")).toString().contains("planner-bill-5")||new JSONArray(PlannerBills.without(list,"planner-bill-9","planner-bill-9")).length()!=2)throw new AssertionError("Drop by id");
        JSONArray noId=new JSONArray(PlannerBills.without(list,"","planner-series-s"));if(noId.length()!=2||noId.toString().contains("planner-bill-4"))throw new AssertionError("Drop the earliest of the bill");
        if(PlannerBills.without(list,"planner-bill-77","planner-series-s")!=null)throw new AssertionError("An unknown id drops nothing");
        // A part payment of a bill with an amount takes that much off (once per payment); paying the rest drops it.
        String owed="[{\"id\":\"planner-bill-6\",\"billKey\":\"planner-bill-6\",\"payee\":\"Power\",\"due\":\"2026-10-20\",\"amountCents\":10000}]";
        String part=PlannerBills.without(owed,"planner-bill-6","planner-bill-6","pay-1",4000);if(new JSONArray(part).getJSONObject(0).getLong("amountCents")!=6000)throw new AssertionError("Part payment");
        if(PlannerBills.without(part,"planner-bill-6","planner-bill-6","pay-1",4000)!=null)throw new AssertionError("Same payment taken off twice");
        if(new JSONArray(PlannerBills.without(part,"planner-bill-6","planner-bill-6","pay-2",6000)).length()!=0||new JSONArray(PlannerBills.without(owed,"planner-bill-6","planner-bill-6","pay-3",12000)).length()!=0)throw new AssertionError("Paid in full: dropped");
        android.content.SharedPreferences bp=getTargetContext().getSharedPreferences("budget",0);String keptAt=bp.getString("planner_bills_at",null);
        try{bp.edit().putString("planner_bills_at",LocalDateTime.now().minusDays(8).withNano(0).toString()).commit();if(!PlannerBills.stale(getTargetContext()))throw new AssertionError("Old list not stale");
            bp.edit().putString("planner_bills_at",LocalDateTime.now().minusDays(2).withNano(0).toString()).commit();if(PlannerBills.stale(getTargetContext()))throw new AssertionError("Recent list stale");}
        finally{if(keptAt==null)bp.edit().remove("planner_bills_at").commit();else bp.edit().putString("planner_bills_at",keptAt).commit();}
        boolean badList=false;try{PlannerBills.clean("{not a list");}catch(JSONException expected){badList=true;}if(!badList)throw new AssertionError("Bad list accepted");
        boolean newer=false;try{BudgetStore.decode(v4.put("version",6).toString());}catch(JSONException expected){newer=true;}if(!newer)throw new AssertionError("Version 6 accepted");
        result.putString("stream","PASS: migration preserves cash and envelopes, JSON roundtrip preserves targets/accounts/transfers, corrupt data rejected, Activity launched and all five tabs rendered, a sent payment is added once and removed on undo, backups restore everything and foreign or newer files are refused, hidden categories and closed accounts are saved and older budgets read without them, weekly/by-date targets and month notes round-trip (backups too) and older budgets read with defaults,scheduled transactions and splits round-trip in version 4, bad splits and version 6 are refused, version 4 data and version 1 backups still read, credit cards and their payment categories round-trip, photo names round-trip and paths are refused, Planner bill categories round-trip, tracking accounts, loan terms, flags, review marks, hidden payees and import rules round-trip (backups too) and older budgets read with defaults, its lists are checked, a paid bill leaves the list and old lists are stale, Home asks for a backup until one is made, and Undo puts a deleted transaction back but never over data saved meanwhile.\n");finish(Activity.RESULT_OK,result);
    }catch(Throwable e){result.putString("stream","FAIL: "+e.toString()+"\n");finish(Activity.RESULT_CANCELED,result);}}
    // Backup files: everything comes back, Planner's link ids included; anything else is refused with a reason.
    private void backups(Budget b)throws Exception{
        YearMonth month=YearMonth.now();b.entries.get(0).externalId="pay-b";b.entries.get(0).billKey="planner-series-b";
        String file=BudgetStore.backup(b,LocalDateTime.of(2026,10,7,12,30,15,999));JSONObject root=new JSONObject(file);
        if(!root.getString("app").equals("MyBudget")||root.getInt("backupVersion")!=2||root.getInt("version")!=5||!root.getString("created").equals("2026-10-07T12:30:15"))throw new AssertionError("Backup header");
        BudgetStore.Backup read=BudgetStore.readBackup("\n"+file);Budget r=read.budget;
        if(!read.created.equals("2026-10-07T12:30:15")||!BudgetStore.encode(r).equals(BudgetStore.encode(b)))throw new AssertionError("Backup lost data");
        eq(r.cash(month),b.cash(month));eq(r.ready(month),b.ready(month));if(r.external("pay-b")==null||r.lastForBill("planner-series-b")==null)throw new AssertionError("Planner link ids lost");
        refused(BudgetStore.encode(b),"isn't a MyBudget backup");refused("{broken","isn't a MyBudget backup");refused("[]","isn't a MyBudget backup");
        refused(root.put("backupVersion",3).toString(),"newer MyBudget");
        // A version 1 backup (MyBudget 0.0.5: storage version 4) still restores.
        JSONObject v1=new JSONObject(file).put("backupVersion",1).put("version",4);if(!BudgetStore.encode(BudgetStore.readBackup(v1.toString()).budget).equals(BudgetStore.encode(b)))throw new AssertionError("Version 1 backup not restored");
        root.put("backupVersion",1).getJSONArray("entries").getJSONObject(0).put("account","missing");refused(root.toString(),"damaged");
        root=new JSONObject(file);root.put("version",9);refused(root.toString(),"newer MyBudget"); // a newer budget inside a current backup
        root=new JSONObject(file);root.getJSONArray("categories").getJSONObject(0).put("due","not-a-month");refused(root.toString(),"damaged");
        root=new JSONObject(file);JSONObject parted=root.getJSONArray("entries").getJSONObject(0);parted.put("category",Budget.SPLIT).put("splits",new JSONArray().put(new JSONObject().put("category","").put("amount",parted.getLong("amount")-1)).put(new JSONObject().put("category","").put("amount",0)));refused(root.toString(),"damaged");
        // A row imported before bankPayee was kept reads with its payee as the statement text; other rows keep none.
        int row=0;while(b.entries.get(row).transfer())row++;root=new JSONObject(file);JSONObject imported=root.getJSONArray("entries").getJSONObject(row).put("memo",Budget.IMPORTED).put("bankPayee","");Budget legacy=BudgetStore.readBackup(root.toString()).budget;
        for(int i=0;i<b.entries.size();i++)if(!legacy.entries.get(i).bankPayee.equals(i==row?imported.getString("payee").trim():b.entries.get(i).bankPayee))throw new AssertionError("Legacy import's statement text");
    }
    private void newTargets(Budget b)throws Exception{
        Budget.Category weekly=b.categories.get(0),dated=b.categories.get(1);String wt=weekly.targetType,dt=dated.targetType;
        weekly.targetType="Weekly";weekly.weekday=3;weekly.weeklyRefill=false;dated.targetType="ByDate";dated.dueDate="2027-01-15";dated.repeatMonths=6;b.setMonthNote(YearMonth.of(2026,10),"Holiday month");
        Budget back=BudgetStore.readBackup(BudgetStore.backup(b,LocalDateTime.now())).budget;Budget.Category w=back.category(weekly.id),d=back.category(dated.id);
        if(!w.targetType.equals("Weekly")||w.weekday!=3||w.weeklyRefill||!d.targetType.equals("ByDate")||!d.dueDate.equals("2027-01-15")||d.repeatMonths!=6||!back.monthNote(YearMonth.of(2026,10)).equals("Holiday month"))throw new AssertionError("New targets or month note lost");
        if(!BudgetStore.encode(back).equals(BudgetStore.encode(b)))throw new AssertionError("Round trip changed data");
        JSONObject older=new JSONObject(BudgetStore.encode(b));older.remove("monthNotes");for(int i=0;i<older.getJSONArray("categories").length();i++){JSONObject c=older.getJSONArray("categories").getJSONObject(i);c.remove("weekday");c.remove("weeklyRefill");c.remove("dueDate");c.remove("repeatMonths");}
        Budget old=BudgetStore.decode(older.toString());Budget.Category o=old.category(weekly.id);if(o.weekday!=1||!o.weeklyRefill||!old.category(dated.id).dueDate.isEmpty()||old.category(dated.id).repeatMonths!=0||!old.monthNotes.isEmpty())throw new AssertionError("Older budget not read with defaults");
        JSONObject bad=new JSONObject(BudgetStore.encode(b));bad.getJSONObject("monthNotes").put("not-a-month","x");boolean refused=false;try{BudgetStore.decode(bad.toString());}catch(RuntimeException|JSONException expected){refused=true;}if(!refused)throw new AssertionError("Bad month accepted");
        weekly.targetType=wt;dated.targetType=dt;weekly.weekday=1;weekly.weeklyRefill=true;dated.dueDate="";dated.repeatMonths=0;b.monthNotes.clear();
    }
    private void batch2(Budget b)throws Exception{
        String day=b.accounts.get(0).date;Budget.Account shares=b.addTracking("Shares",day,500000,false),home=b.addTracking("Mortgage",day,40000000,true);home.rate=6250;home.payment=250000;home.frequency="Every 2 weeks";
        Budget.Entry flagged=b.entries.get(0);flagged.flag=4;flagged.approved=false;flagged.bankPayee="EFTPOS SHOP 123";Budget.Entry update=b.valueUpdate(shares,510000,day);b.entries.add(0,update);
        Budget.Entry extra=new Budget.Entry("Transfer to Mortgage",b.categories.get(0).id,b.accounts.get(0).id,day,-1000);extra.destination=home.id;b.validate(extra);b.entries.add(0,extra);
        b.flagNames[4]="Tax";b.flagNames[1]="Check";b.hidePayee("Old shop",true);b.rules.add(new Budget.Rule("WOOLWORTHS","Woolworths",b.categories.get(0).id));b.rules.add(new Budget.Rule("uber","Uber",""));
        YearMonth month=YearMonth.now();long cash=b.cash(month),ready=b.ready(month),worth=b.netWorth(month);
        for(Budget back:new Budget[]{BudgetStore.decode(BudgetStore.encode(b)),BudgetStore.readBackup(BudgetStore.backup(b,LocalDateTime.now())).budget}){
            Budget.Account s=back.account(shares.id),h=back.account(home.id);
            if(s==null||!s.tracking()||s.liability||h==null||!h.tracking()||!h.liability||h.rate!=6250||h.payment!=250000||!h.frequency.equals("Every 2 weeks")||h.opening!=-40000000)throw new AssertionError("Tracking account or loan terms lost");
            Budget.Entry f=back.entries.get(2);if(f.flag!=4||f.approved||!f.bankPayee.equals("EFTPOS SHOP 123")||!back.entries.get(0).bankPayee.isEmpty()||!back.entries.get(0).approved||!back.entries.get(0).category.equals(extra.category)||!back.entries.get(0).destination.equals(home.id))throw new AssertionError("Flag, review mark or crossing transfer lost");
            if(!back.flagNames[4].equals("Tax")||!back.flagNames[1].equals("Check")||!back.flagNames[2].isEmpty()||!back.hiddenPayee("OLD SHOP")||back.rules.size()!=2||!back.rules.get(0).contains.equals("WOOLWORTHS")||!back.rules.get(0).category.equals(b.categories.get(0).id)||!back.rules.get(1).rename.equals("Uber"))throw new AssertionError("Flag names, hidden payees or rules lost");
            eq(back.cash(month),cash);eq(back.ready(month),ready);eq(back.netWorth(month),worth);if(!BudgetStore.encode(back).equals(BudgetStore.encode(b)))throw new AssertionError("Round trip changed data");}
        // Older budgets (before these fields): no flags, approved, assets without terms, no names, hidden payees or rules.
        JSONObject older=new JSONObject(BudgetStore.encode(b));older.remove("flagNames");older.remove("hiddenPayees");older.remove("rules");
        for(int i=0;i<older.getJSONArray("entries").length();i++){JSONObject e=older.getJSONArray("entries").getJSONObject(i);e.remove("flag");e.remove("approved");e.remove("bankPayee");}
        for(int i=0;i<older.getJSONArray("accounts").length();i++){JSONObject a=older.getJSONArray("accounts").getJSONObject(i);a.remove("liability");a.remove("rate");a.remove("payment");a.remove("frequency");}
        Budget old=BudgetStore.decode(older.toString());for(Budget.Entry e:old.entries)if(e.flag!=0||!e.approved||!e.bankPayee.isEmpty())throw new AssertionError("Older entries not read as unflagged and approved");
        Budget.Account oh=old.account(home.id);if(oh.liability||oh.rate!=0||oh.payment!=0||!oh.frequency.equals("Monthly")||!old.hiddenPayees.isEmpty()||!old.rules.isEmpty()||!old.flagNames[4].isEmpty())throw new AssertionError("Older budget not read with defaults");
        // A rule whose category was deleted keeps its rename; one left with nothing to do is dropped. A liability mark on a budget account is ignored.
        JSONObject odd=new JSONObject(BudgetStore.encode(b));odd.getJSONArray("rules").getJSONObject(0).put("category","missing");odd.getJSONArray("rules").put(new JSONObject().put("contains","x").put("category","missing"));odd.getJSONArray("accounts").getJSONObject(0).put("liability",true);
        Budget oddBack=BudgetStore.decode(odd.toString());if(oddBack.rules.size()!=2||!oddBack.rules.get(0).category.isEmpty()||oddBack.accounts.get(0).liability)throw new AssertionError("Odd rules or liability not cleaned");
    }
    private void batch3(Budget b)throws Exception{
        b.pin(b.categories.get(1),true);String pinned=b.categories.get(1).id;
        for(Budget back:new Budget[]{BudgetStore.decode(BudgetStore.encode(b)),BudgetStore.readBackup(BudgetStore.backup(b,LocalDateTime.now())).budget}){if(!back.category(pinned).pinned||back.categories.get(0).pinned) /* the pin itself round-trips; this category is hidden here, so pinned() leaves it out */throw new AssertionError("Pinned category lost");if(!BudgetStore.encode(back).equals(BudgetStore.encode(b)))throw new AssertionError("Round trip changed data");}
        JSONObject older=new JSONObject(BudgetStore.encode(b));for(int i=0;i<older.getJSONArray("categories").length();i++)older.getJSONArray("categories").getJSONObject(i).remove("pinned");if(!BudgetStore.decode(older.toString()).pinned().isEmpty())throw new AssertionError("Older budget not read as unpinned");
    }
    private void refused(String file,String reason){try{BudgetStore.readBackup(file);}catch(JSONException e){if(e.getMessage()!=null&&e.getMessage().contains(reason))return;throw new AssertionError("Wrong reason: "+e.getMessage());}throw new AssertionError("Accepted: "+reason);}
    // Planner's "Send paid bills to MyBudget", through AddExpenseActivity. The saved budget is put back afterwards.
    private void sentPayments(String day)throws Exception{
        android.content.SharedPreferences prefs=getTargetContext().getSharedPreferences("budget",0);String saved=prefs.getString("data",null);
        try{
            Budget seed=new Budget();Budget.Account bank=new Budget.Account("Everyday",day,100000);seed.accounts.add(bank);Budget.Category power=new Budget.Category("Utilities");seed.categories.add(power);seed.categories.add(new Budget.Category("Groceries"));
            Budget.Entry earlier=new Budget.Entry("Electricity",power.id,bank.id,day,-100);earlier.externalId="pay-old";earlier.billKey="planner-series-t";seed.entries.add(earlier);
            Budget roundTrip=BudgetStore.decode(BudgetStore.encode(seed));if(!roundTrip.entries.get(0).externalId.equals("pay-old")||!roundTrip.entries.get(0).billKey.equals("planner-series-t"))throw new AssertionError("Sent payment ids lost");
            JSONObject v2=new JSONObject(BudgetStore.encode(seed)).put("version",2);JSONObject v2Entry=v2.getJSONArray("entries").getJSONObject(0);v2Entry.remove("externalId");v2Entry.remove("billKey");
            if(!BudgetStore.decode(v2.toString()).entries.get(0).externalId.isEmpty())throw new AssertionError("Version 2 not read");
            if(!prefs.edit().putString("data",BudgetStore.encode(seed)).commit())throw new AssertionError("Seed not saved");
            Intent add=new Intent(AddExpenseActivity.ACTION_ADD).setClassName(getTargetContext(),AddExpenseActivity.class.getName()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra("paymentId","pay-t").putExtra("billKey","planner-series-t").putExtra("payee","Electricity").putExtra("amountCents",14280L).putExtra("currency","AUD").putExtra("date",LocalDate.now().toString()).putExtra("note","From Planner");
            AddExpenseActivity screen=(AddExpenseActivity)startActivitySync(add);waitForIdleSync();
            runOnMainSync(()->screen.dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).performClick());waitForIdleSync();
            Budget.Entry sent=BudgetStore.decode(prefs.getString("data",null)).external("pay-t");
            if(sent==null||sent.amount!=-14280||!sent.category.equals(power.id)||!sent.memo.equals("From Planner"))throw new AssertionError("Sent payment not saved with the suggested category");
            getTargetContext().startActivity(add);waitForIdleSync();Thread.sleep(1500);waitForIdleSync();
            int copies=0;for(Budget.Entry e:BudgetStore.decode(prefs.getString("data",null)).entries)if(e.externalId.equals("pay-t"))copies++;eq(copies,1);
            Intent undo=new Intent(AddExpenseActivity.ACTION_UNDONE).setClassName(getTargetContext(),AddExpenseActivity.class.getName()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("paymentId","pay-t");
            AddExpenseActivity ask=(AddExpenseActivity)startActivitySync(undo);waitForIdleSync();
            runOnMainSync(()->ask.dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).performClick());waitForIdleSync();
            Budget after=BudgetStore.decode(prefs.getString("data",null));if(after.external("pay-t")!=null||after.external("pay-old")==null)throw new AssertionError("Undo removed the wrong expense");
        }finally{
            android.content.SharedPreferences.Editor restore=prefs.edit();if(saved==null)restore.remove("data");else restore.putString("data",saved);restore.commit();
        }
    }
    // Home's backup reminder and a delete's Undo, on the running Activity. The saved budget and backup dates are put back afterwards.
    private void dataSafety(MainActivity main,String day)throws Exception{
        android.content.SharedPreferences prefs=getTargetContext().getSharedPreferences("budget",0);View screen=main.getWindow().getDecorView();
        String saved=prefs.getString("data",null),last=prefs.getString("last_backup",null),autoLast=prefs.getString("auto_backup_last",null),snooze=prefs.getString("backup_reminder_until",null);
        try{
            Budget seed=new Budget();Budget.Account bank=new Budget.Account("Everyday",day,100000);seed.accounts.add(bank);Budget.Category power=new Budget.Category("Utilities");seed.categories.add(power);
            Budget.Entry paid=new Budget.Entry("Electricity",power.id,bank.id,day,-14280);paid.externalId="pay-u";paid.billKey="planner-series-u";seed.entries.add(paid);String raw=BudgetStore.encode(seed);
            if(!prefs.edit().putString("data",raw).remove("last_backup").remove("auto_backup_last").remove("backup_reminder_until").commit())throw new AssertionError("Seed not saved");
            runOnMainSync(()->{main.load();main.tab="Home";main.render();if(find(screen,"Your budget has never been backed up",false)==null)throw new AssertionError("No backup reminder");
                main.prefs().edit().putString("last_backup",LocalDate.now().toString()).commit();main.render();if(find(screen,"Your budget has never been backed up",false)!=null)throw new AssertionError("Reminder after a backup");
                main.openSettings();if(find(screen,"Last backup: "+Ui.pretty(LocalDate.now().toString()),false)==null)throw new AssertionError("No Last backup line");main.tab="Home";main.render();
                // Undo puts the deleted Planner payment back as it was (its ids too).
                if(!main.deleteWithUndo("Transaction deleted",()->main.budget.entries.removeIf(e->e.externalId.equals("pay-u")))||find(screen,"Transaction deleted",false)==null)throw new AssertionError("No Undo bar");
                find(screen,"Undo",true).performClick();if(find(screen,"Transaction deleted",false)!=null)throw new AssertionError("Undo bar stayed");});
            if(!raw.equals(prefs.getString("data",null))||!BudgetStore.decode(prefs.getString("data",null)).external("pay-u").billKey.equals("planner-series-u"))throw new AssertionError("Undo didn't put the budget back");
            // Saved meanwhile (an expense from Planner): Undo is refused and the new expense stays.
            runOnMainSync(()->{if(!main.deleteWithUndo("Transaction deleted",()->main.budget.entries.removeIf(e->e.externalId.equals("pay-u"))))throw new AssertionError("Delete failed");});
            Budget meanwhile=BudgetStore.decode(prefs.getString("data",null));Budget.Entry sent=new Budget.Entry("Water",power.id,bank.id,day,-5000);sent.externalId="pay-v";meanwhile.entries.add(sent);
            prefs.edit().putString("data",BudgetStore.encode(meanwhile)).commit();
            runOnMainSync(()->find(screen,"Undo",true).performClick());
            Budget after=BudgetStore.decode(prefs.getString("data",null));if(after.external("pay-v")==null||after.external("pay-u")!=null)throw new AssertionError("Undo overwrote data saved meanwhile");
        }finally{
            android.content.SharedPreferences.Editor restore=prefs.edit();String[][] keys={{"data",saved},{"last_backup",last},{"auto_backup_last",autoLast},{"backup_reminder_until",snooze}};
            for(String[] k:keys)if(k[1]==null)restore.remove(k[0]);else restore.putString(k[0],k[1]);restore.commit();
            runOnMainSync(()->{main.budget=new Budget();main.load();main.tab="Home";main.render();});
        }
    }
}
