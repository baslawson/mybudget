package com.mybudget.app;
import org.json.*;
import java.time.*;
import java.util.*;

/** Versioned storage; old snapshots are retained separately by the Activity. */
public final class BudgetStore {
    public static String encode(Budget b) throws JSONException {
        JSONObject root=new JSONObject().put("version",2);JSONArray categories=new JSONArray(),accounts=new JSONArray(),entries=new JSONArray();
        for(Budget.Category c:b.categories){JSONObject assigned=new JSONObject();for(Map.Entry<String,Long> a:c.assigned.entrySet())assigned.put(a.getKey(),a.getValue());categories.put(new JSONObject().put("id",c.id).put("name",c.name).put("group",c.group).put("target",c.target).put("targetType",c.targetType).put("due",c.due).put("assigned",assigned));}
        for(Budget.Account a:b.accounts)accounts.put(new JSONObject().put("id",a.id).put("name",a.name).put("date",a.date).put("opening",a.opening).put("reconciled",a.reconciled));
        for(Budget.Entry e:b.entries)entries.put(new JSONObject().put("id",e.id).put("payee",e.payee).put("category",e.category).put("account",e.account).put("destination",e.destination).put("date",e.date).put("amount",e.amount).put("memo",e.memo).put("cleared",e.cleared));
        return root.put("categories",categories).put("accounts",accounts).put("entries",entries).toString();
    }
    public static Budget decode(String raw) throws JSONException {
        JSONObject root=new JSONObject(raw);if(root.optInt("version",1)==1)return migrate(root);
        if(root.getInt("version")!=2)throw new JSONException("Unsupported budget version.");
        Budget b=new Budget();JSONArray cats=root.getJSONArray("categories"),accounts=root.getJSONArray("accounts"),entries=root.getJSONArray("entries");
        for(int i=0;i<cats.length();i++){JSONObject j=cats.getJSONObject(i);Budget.Category c=new Budget.Category(j.getString("name"));c.id=j.getString("id");c.group=j.getString("group");c.target=j.getLong("target");c.targetType=j.getString("targetType");c.due=j.getString("due");JSONObject assigned=j.getJSONObject("assigned");Iterator<String> keys=assigned.keys();while(keys.hasNext()){String key=keys.next();YearMonth.parse(key);c.assigned.put(key,assigned.getLong(key));}b.categories.add(c);}
        for(int i=0;i<accounts.length();i++){JSONObject j=accounts.getJSONObject(i);LocalDate.parse(j.getString("date"));Budget.Account a=new Budget.Account(j.getString("name"),j.getString("date"),j.getLong("opening"));a.id=j.getString("id");a.reconciled=j.optString("reconciled","");b.accounts.add(a);}
        for(int i=0;i<entries.length();i++){JSONObject j=entries.getJSONObject(i);LocalDate.parse(j.getString("date"));Budget.Entry e=new Budget.Entry(j.getString("payee"),j.getString("category"),j.getString("account"),j.getString("date"),j.getLong("amount"));e.id=j.getString("id");e.destination=j.getString("destination");e.memo=j.getString("memo");e.cleared=j.getBoolean("cleared");if(b.account(e.account)==null||(!e.destination.isEmpty()&&b.account(e.destination)==null)||(!e.category.isEmpty()&&b.category(e.category)==null))throw new JSONException("Invalid saved transaction.");b.entries.add(e);}
        return b;
    }
    private static Budget migrate(JSONObject root)throws JSONException {
        Budget b=new Budget();YearMonth month=YearMonth.now();JSONArray cats=root.getJSONArray("categories"),entries=root.getJSONArray("entries");long cash=root.getLong("ready"),net=0;String start=LocalDate.now().toString();
        Map<String,Budget.Category> byName=new HashMap<>();Map<String,Long> balances=new HashMap<>();
        for(int i=0;i<cats.length();i++){JSONObject j=cats.getJSONObject(i);Budget.Category c=new Budget.Category(j.getString("name"));b.categories.add(c);byName.put(c.name,c);balances.put(c.id,j.getLong("available"));cash+=j.getLong("available");}
        Budget.Account a=new Budget.Account("Main account",start,0);b.accounts.add(a);
        for(int i=0;i<entries.length();i++){JSONObject j=entries.getJSONObject(i);String day=LocalDate.parse(j.getString("date")).toString();if(day.compareTo(start)<0)start=day;long amount=j.getLong("amount");Budget.Category c=byName.get(j.getString("category"));if(amount<0&&c==null)throw new JSONException("Unknown legacy category.");Budget.Entry e=new Budget.Entry(j.getString("payee"),amount<0?c.id:"",a.id,day,amount);b.entries.add(e);net+=amount;}
        a.date=start;a.opening=cash-net;
        // Old versions did not record assignments: preserve today's envelopes,
        // rather than inventing past monthly assignments.
        for(Budget.Category c:b.categories)c.assigned.put(month.toString(),balances.get(c.id)-b.activity(c,month)-Math.max(0,b.available(c,month.minusMonths(1))));
        return b;
    }
}
