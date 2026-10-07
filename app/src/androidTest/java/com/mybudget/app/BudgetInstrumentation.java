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
        for(String tab:new String[]{"Plan","Spending","Accounts","Reflect","Home"}){runOnMainSync(()->{View button=find(activity.getWindow().getDecorView(),tab,true);if(button==null)throw new AssertionError("Missing tab "+tab);button.performClick();if(find(activity.getWindow().getDecorView(),tab,false)==null)throw new AssertionError("Missing screen "+tab);});waitForIdleSync();}
        sentPayments(day);
        backups(migrated);
        // Hidden categories and closed accounts are saved; budgets saved before them read as visible and open.
        Budget flags=BudgetStore.decode(BudgetStore.encode(migrated));flags.categories.get(1).hidden=true;flags.accounts.get(1).closed=true;
        Budget flagsBack=BudgetStore.decode(BudgetStore.encode(flags));if(flagsBack.categories.get(0).hidden||!flagsBack.categories.get(1).hidden||flagsBack.accounts.get(0).closed||!flagsBack.accounts.get(1).closed)throw new AssertionError("Hidden/closed not saved");
        JSONObject older=new JSONObject(BudgetStore.encode(flags));older.getJSONArray("categories").getJSONObject(1).remove("hidden");older.getJSONArray("accounts").getJSONObject(1).remove("closed");
        Budget olderBack=BudgetStore.decode(older.toString());if(olderBack.categories.get(1).hidden||olderBack.accounts.get(1).closed)throw new AssertionError("Older budget not read as visible/open");
        result.putString("stream","PASS: migration preserves cash and envelopes, JSON roundtrip preserves targets/accounts/transfers, corrupt data rejected, Activity launched and all five tabs rendered, a sent payment is added once and removed on undo, backups restore everything and foreign or newer files are refused, hidden categories and closed accounts are saved and older budgets read without them.\n");finish(Activity.RESULT_OK,result);
    }catch(Throwable e){result.putString("stream","FAIL: "+e.toString()+"\n");finish(Activity.RESULT_CANCELED,result);}}
    // Backup files: everything comes back, Planner's link ids included; anything else is refused with a reason.
    private void backups(Budget b)throws Exception{
        YearMonth month=YearMonth.now();b.entries.get(0).externalId="pay-b";b.entries.get(0).billKey="planner-series-b";
        String file=BudgetStore.backup(b,LocalDateTime.of(2026,10,7,12,30,15,999));JSONObject root=new JSONObject(file);
        if(!root.getString("app").equals("MyBudget")||root.getInt("backupVersion")!=1||!root.getString("created").equals("2026-10-07T12:30:15"))throw new AssertionError("Backup header");
        BudgetStore.Backup read=BudgetStore.readBackup("\n"+file);Budget r=read.budget;
        if(!read.created.equals("2026-10-07T12:30:15")||!BudgetStore.encode(r).equals(BudgetStore.encode(b)))throw new AssertionError("Backup lost data");
        eq(r.cash(month),b.cash(month));eq(r.ready(month),b.ready(month));if(r.external("pay-b")==null||r.lastForBill("planner-series-b")==null)throw new AssertionError("Planner link ids lost");
        refused(BudgetStore.encode(b),"isn't a MyBudget backup");refused("{broken","isn't a MyBudget backup");refused("[]","isn't a MyBudget backup");
        refused(root.put("backupVersion",2).toString(),"newer MyBudget");
        root.put("backupVersion",1).getJSONArray("entries").getJSONObject(0).put("account","missing");refused(root.toString(),"damaged");
        root=new JSONObject(file);root.put("version",9);refused(root.toString(),"damaged");
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
}
