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
        result.putString("stream","PASS: migration preserves cash and envelopes, JSON roundtrip preserves targets/accounts/transfers, corrupt data rejected, Activity launched and all five tabs rendered.\n");finish(Activity.RESULT_OK,result);
    }catch(Throwable e){result.putString("stream","FAIL: "+e.toString()+"\n");finish(Activity.RESULT_CANCELED,result);}}
}
