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
 * Currencies: Planner sends "billsAll" (every currency, each bill with its "currency") and, for a MyBudget before
 * currencies, "bills" (its AUD bills). Without billsAll (an older Planner) the bills are AUD. The whole list is kept, each
 * bill with its currency; only those in the budget's currency are planned for (read), so after a change of currency in
 * Settings that currency's bills show at once, and a list saved before currencies (no "currency") counts as AUD.
 */
public class PlannerBills extends BroadcastReceiver {
    public static final String ACTION="com.mybudget.app.action.UPCOMING_BILLS";
    static final Set<String> PLANNER=new HashSet<>(Arrays.asList("io.github.baslawson.planner","io.github.baslawson.planner.debug"));
    static final int MAX=200;
    @Override public void onReceive(Context context,Intent intent){
        if(!ACTION.equals(intent.getAction()))return;
        if(Build.VERSION.SDK_INT>=34&&!PLANNER.contains(getSentFromPackage()))return;
        String clean;try{clean=clean(intent);}catch(Exception e){return;}
        context.getSharedPreferences("budget",0).edit().putString("planner_bills",clean).putString("planner_bills_at",LocalDateTime.now().withNano(0).toString()).apply();
    }
    /** The list a broadcast carries, checked: billsAll when it's there (every currency), else bills (AUD). */
    static String clean(Intent intent)throws JSONException{String all=intent.getStringExtra("billsAll");return all!=null?clean(all,true):clean(intent.getStringExtra("bills"),false);}
    static String clean(String raw)throws JSONException{return clean(raw,false);}
    /** Checks and trims a list from Planner; throws if it isn't one. [withCurrency]: billsAll, each bill with its currency (one without a known currency is left out); else all AUD. */
    static String clean(String raw,boolean withCurrency)throws JSONException{
        JSONArray in=new JSONArray(raw==null?"[]":raw),out=new JSONArray();
        for(int i=0;i<in.length()&&out.length()<MAX;i++){JSONObject b=in.getJSONObject(i);String id=b.getString("id"),key=b.getString("billKey"),payee=b.getString("payee").trim();LocalDate due=LocalDate.parse(b.getString("due"));
            String currency=withCurrency?b.optString("currency","").trim():Budget.DEFAULT_CURRENCY;
            if(id.isEmpty()||id.length()>100||key.isEmpty()||key.length()>100||payee.isEmpty()||!Budget.storableCurrency(currency))continue;JSONObject o=new JSONObject().put("id",id).put("billKey",key).put("payee",Budget.cut(payee,80)).put("due",due.toString()).put("currency",currency);
            // Hunt 23: an amount MyBudget can't hold (over 100 million, as IDR bills can be) leaves the bill planned without one; zero or less is no bill.
            if(b.has("amountCents")){long c=b.getLong("amountCents");if(c<=0)continue;if(c<=10_000_000_000L)o.put("amountCents",c);}out.put(o);}
        return out.toString();
    }
    /** A list older than this is ignored: Planner sends one each time it opens, so it's out of date (or Planner is gone). */
    static final int STALE_DAYS=7;
    static boolean stale(Context context){String at=context.getSharedPreferences("budget",0).getString("planner_bills_at",null);try{return at!=null&&LocalDateTime.parse(at).isBefore(LocalDateTime.now().minusDays(STALE_DAYS));}catch(Exception e){return true;}}
    /**
     * A bill was just paid (its expense of [paidCents], payment [paymentId], added): drop it from the list now, rather than
     * plan for it twice until Planner next sends. By its entry id from Planner; without one (an older Planner), the earliest
     * entry for the same bill. A part payment of a bill with a known amount only takes that much off it.
     */
    static void dropPaid(Context context,String upcomingId,String billKey,String paymentId,long paidCents){
        android.content.SharedPreferences prefs=context.getSharedPreferences("budget",0);
        try{String kept=without(prefs.getString("planner_bills","[]"),upcomingId,billKey,paymentId,paidCents);if(kept!=null)prefs.edit().putString("planner_bills",kept).apply();}catch(Exception ignored){}
    }
    /** [list] less the paid entry (see dropPaid), or null when nothing matches. */
    static String without(String list,String upcomingId,String billKey)throws JSONException{return without(list,upcomingId,billKey,"",0);}
    /** [list] less the paid entry, or with [paidCents] taken off its amount when that's less (once per [paymentId]); null when nothing changes. */
    static String without(String list,String upcomingId,String billKey,String paymentId,long paidCents)throws JSONException{
        JSONArray a=new JSONArray(list);int drop=-1;
        for(int i=0;i<a.length()&&drop<0;i++)if(upcomingId!=null&&!upcomingId.isEmpty()&&upcomingId.equals(a.getJSONObject(i).optString("id")))drop=i;
        if(drop<0&&(upcomingId==null||upcomingId.isEmpty())&&billKey!=null&&!billKey.isEmpty())for(int i=0;i<a.length();i++)if(billKey.equals(a.getJSONObject(i).optString("billKey"))&&(drop<0||a.getJSONObject(i).optString("due").compareTo(a.getJSONObject(drop).optString("due"))<0))drop=i;
        if(drop<0)return null;JSONObject b=a.getJSONObject(drop);long due=b.optLong("amountCents",0);
        if(due>0&&paidCents>0&&paidCents<due){
            // The "paid" ids keep the same payment (sent again) from being taken off twice.
            JSONArray paid=b.optJSONArray("paid");if(paid==null)b.put("paid",paid=new JSONArray());String id=paymentId==null?"":paymentId;
            if(!id.isEmpty())for(int i=0;i<paid.length();i++)if(id.equals(paid.optString(i)))return null;
            if(!id.isEmpty())paid.put(id);b.put("amountCents",due-paidCents);return a.toString();
        }
        a.remove(drop);return a.toString();
    }
    /** The saved list's bills in the budget's currency as upcoming transactions (amount negative, 0 = no amount), each with its planned category. */
    static List<Budget.Scheduled> read(Context context,Budget budget){
        List<Budget.Scheduled> list=new ArrayList<>();if(stale(context))return list;
        try{JSONArray a=new JSONArray(context.getSharedPreferences("budget",0).getString("planner_bills","[]"));
            for(int i=0;i<a.length();i++){JSONObject b=a.getJSONObject(i);if(!Budget.sameCurrency(b.optString("currency",""),budget.currency))continue;String key=b.getString("billKey");Budget.Scheduled s=new Budget.Scheduled(b.getString("payee"),budget.plannerCategory(key),"",b.getString("due"),-b.optLong("amountCents",0),"Never");s.id=b.getString("id");s.billKey=key;list.add(s);}
        }catch(Exception ignored){}
        return list;
    }
}
