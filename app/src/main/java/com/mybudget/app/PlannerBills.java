package com.mybudget.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import org.json.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Planner's upcoming bills ("Send paid bills to MyBudget" in Planner also shares these). Each list replaces the last;
 * they're kept in preferences (planner_bills, planner_bills_at), not in the budget: they aren't money, only what's
 * coming, and the expense still arrives when the bill is marked paid in Planner (AddExpenseActivity). On Android 14
 * and later only Planner's packages are accepted, since Planner shares its identity with the broadcast.
 */
public class PlannerBills extends BroadcastReceiver {
    public static final String ACTION="com.mybudget.app.action.UPCOMING_BILLS";
    static final Set<String> PLANNER=new HashSet<>(Arrays.asList("io.github.baslawson.planner","io.github.baslawson.planner.debug"));
    static final int MAX=200;
    @Override public void onReceive(Context context,Intent intent){
        if(!ACTION.equals(intent.getAction()))return;
        if(Build.VERSION.SDK_INT>=34&&!PLANNER.contains(getSentFromPackage()))return;
        String clean;try{clean=clean(intent.getStringExtra("bills"));}catch(Exception e){return;}
        context.getSharedPreferences("budget",0).edit().putString("planner_bills",clean).putString("planner_bills_at",LocalDateTime.now().withNano(0).toString()).apply();
    }
    /** Checks and trims a list from Planner; throws if it isn't one. */
    static String clean(String raw)throws JSONException{
        JSONArray in=new JSONArray(raw==null?"[]":raw),out=new JSONArray();
        for(int i=0;i<in.length()&&out.length()<MAX;i++){JSONObject b=in.getJSONObject(i);String id=b.getString("id"),key=b.getString("billKey"),payee=b.getString("payee").trim();LocalDate due=LocalDate.parse(b.getString("due"));
            if(id.isEmpty()||id.length()>100||key.isEmpty()||key.length()>100||payee.isEmpty())continue;JSONObject o=new JSONObject().put("id",id).put("billKey",key).put("payee",payee.length()>80?payee.substring(0,80):payee).put("due",due.toString());
            if(b.has("amountCents")){long c=b.getLong("amountCents");if(c<=0||c>10_000_000_000L)continue;o.put("amountCents",c);}out.put(o);}
        return out.toString();
    }
    /** The saved list as upcoming transactions (amount negative, 0 = no amount), each with its planned category. */
    static List<Budget.Scheduled> read(Context context,Budget budget){
        List<Budget.Scheduled> list=new ArrayList<>();
        try{JSONArray a=new JSONArray(context.getSharedPreferences("budget",0).getString("planner_bills","[]"));
            for(int i=0;i<a.length();i++){JSONObject b=a.getJSONObject(i);String key=b.getString("billKey");Budget.Scheduled s=new Budget.Scheduled(b.getString("payee"),budget.plannerCategory(key),"",b.getString("due"),-b.optLong("amountCents",0),"Never");s.id=b.getString("id");s.billKey=key;list.add(s);}
        }catch(Exception ignored){}
        return list;
    }
}
