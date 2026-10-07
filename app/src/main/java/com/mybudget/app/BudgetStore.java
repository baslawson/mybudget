package com.mybudget.app;
import org.json.*;
import java.time.*;
import java.util.*;

/** Versioned storage; old snapshots are retained separately by the Activity. */
public final class BudgetStore {
    // Version 4 adds scheduled transactions (and, in later fields of the same version, splits and credit cards):
    // MyBudget before it would drop them, so it must refuse the data instead of reading it.
    public static final int VERSION=4;
    public static String encode(Budget b) throws JSONException {
        JSONObject root=new JSONObject().put("version",VERSION);JSONArray categories=new JSONArray(),accounts=new JSONArray(),entries=new JSONArray(),scheduled=new JSONArray();
        for(Budget.Category c:b.categories){JSONObject assigned=new JSONObject();for(Map.Entry<String,Long> a:c.assigned.entrySet())assigned.put(a.getKey(),a.getValue());categories.put(new JSONObject().put("id",c.id).put("name",c.name).put("group",c.group).put("target",c.target).put("targetType",c.targetType).put("due",c.due).put("hidden",c.hidden).put("snoozed",c.snoozed).put("note",c.note).put("dueDay",c.dueDay).put("cardAccount",c.cardAccount).put("assigned",assigned));}
        for(Budget.Account a:b.accounts)accounts.put(new JSONObject().put("id",a.id).put("name",a.name).put("date",a.date).put("opening",a.opening).put("reconciled",a.reconciled).put("closed",a.closed).put("type",a.type));
        for(Budget.Entry e:b.entries)entries.put(new JSONObject().put("id",e.id).put("payee",e.payee).put("category",e.category).put("account",e.account).put("destination",e.destination).put("date",e.date).put("amount",e.amount).put("memo",e.memo).put("cleared",e.cleared).put("externalId",e.externalId).put("billKey",e.billKey).put("photo",e.photo));
        for(int i=0;i<b.entries.size();i++){Budget.Entry e=b.entries.get(i);if(!e.split())continue;JSONArray parts=new JSONArray();for(Budget.Split p:e.splits)parts.put(new JSONObject().put("category",p.category).put("amount",p.amount).put("memo",p.memo));entries.getJSONObject(i).put("splits",parts);}
        for(Budget.Scheduled s:b.scheduled)scheduled.put(new JSONObject().put("id",s.id).put("payee",s.payee).put("category",s.category).put("account",s.account).put("next",s.next).put("repeat",s.repeat).put("day",s.day).put("amount",s.amount).put("memo",s.memo).put("billKey",s.billKey));
        JSONObject bills=new JSONObject();for(Map.Entry<String,String> m:b.billCategories.entrySet())bills.put(m.getKey(),m.getValue());
        return root.put("categories",categories).put("accounts",accounts).put("entries",entries).put("scheduled",scheduled).put("billCategories",bills).toString();
    }
    /** A backup file: the saved budget plus what marks it as MyBudget's, and when it was made (local date-time). */
    public static final int BACKUP_VERSION=1;
    public static final class Backup { public final Budget budget; public final String created; Backup(Budget budget,String created){this.budget=budget;this.created=created;} }
    public static String backup(Budget b,LocalDateTime created) throws JSONException {
        return new JSONObject(encode(b)).put("app","MyBudget").put("backupVersion",BACKUP_VERSION).put("created",created.withNano(0).toString()).toString(2);
    }
    /** Reads a backup file in full; throws (so nothing changes) unless it's a MyBudget backup this version can read. */
    public static Backup readBackup(String raw) throws JSONException {
        JSONObject root;try{root=new JSONObject(raw);}catch(JSONException e){throw new JSONException("This file isn't a MyBudget backup.");}
        if(!"MyBudget".equals(root.optString("app"))||!root.has("version"))throw new JSONException("This file isn't a MyBudget backup.");
        int version=root.optInt("backupVersion",0);if(version<1)throw new JSONException("This file isn't a MyBudget backup.");
        if(version>BACKUP_VERSION)throw new JSONException("This backup is from a newer MyBudget. Update MyBudget, then restore it.");
        try{return new Backup(decode(raw),root.optString("created",""));}catch(JSONException|RuntimeException e){throw new JSONException("This backup is damaged and can't be restored.");}
    }
    public static Budget decode(String raw) throws JSONException {
        JSONObject root=new JSONObject(raw);if(root.optInt("version",1)==1)return migrate(root);
        // Version 3 added expenses sent by other apps (externalId, billKey); version 2 reads without them. Optional
        // fields added later read with defaults (category hidden/snoozed/note/dueDay, account closed). Version 4: scheduled.
        int version=root.getInt("version");if(version<2||version>VERSION)throw new JSONException("Unsupported budget version.");
        Budget b=new Budget();JSONArray cats=root.getJSONArray("categories"),accounts=root.getJSONArray("accounts"),entries=root.getJSONArray("entries");
        for(int i=0;i<cats.length();i++){JSONObject j=cats.getJSONObject(i);Budget.Category c=new Budget.Category(j.getString("name"));c.id=j.getString("id");c.group=j.getString("group");c.target=j.getLong("target");c.targetType=j.getString("targetType");c.due=j.getString("due");c.hidden=j.optBoolean("hidden",false);c.snoozed=j.optString("snoozed","");c.note=j.optString("note","");c.dueDay=Math.max(0,Math.min(31,j.optInt("dueDay",0)));c.cardAccount=j.optString("cardAccount","");JSONObject assigned=j.getJSONObject("assigned");Iterator<String> keys=assigned.keys();while(keys.hasNext()){String key=keys.next();YearMonth.parse(key);c.assigned.put(key,assigned.getLong(key));}b.categories.add(c);}
        for(int i=0;i<accounts.length();i++){JSONObject j=accounts.getJSONObject(i);LocalDate.parse(j.getString("date"));Budget.Account a=new Budget.Account(j.getString("name"),j.getString("date"),j.getLong("opening"));a.id=j.getString("id");a.reconciled=j.optString("reconciled","");a.closed=j.optBoolean("closed",false);a.type=j.optString("type","cash");if(!a.type.equals("cash")&&!a.type.equals("credit"))throw new JSONException("Unknown account type.");b.accounts.add(a);}
        for(Budget.Category c:b.categories)if(c.payment()&&(b.account(c.cardAccount)==null||!b.account(c.cardAccount).credit()))throw new JSONException("Payment category without its card.");
        for(int i=0;i<entries.length();i++){JSONObject j=entries.getJSONObject(i);LocalDate.parse(j.getString("date"));Budget.Entry e=new Budget.Entry(j.getString("payee"),j.getString("category"),j.getString("account"),j.getString("date"),j.getLong("amount"));e.id=j.getString("id");e.destination=j.getString("destination");e.memo=j.getString("memo");e.cleared=j.getBoolean("cleared");e.externalId=j.optString("externalId","");e.billKey=j.optString("billKey","");e.photo=j.optString("photo","");if(!e.photo.matches("[0-9a-f-]{0,40}([.]jpg)?"))throw new JSONException("Invalid photo name."); // a file name only: never a path
            JSONArray parts=j.optJSONArray("splits");if(parts!=null)for(int k=0;k<parts.length();k++){JSONObject p=parts.getJSONObject(k);Budget.Split s=new Budget.Split(p.getString("category"),p.getLong("amount"));s.memo=p.optString("memo","");if(!s.category.isEmpty()&&b.category(s.category)==null)throw new JSONException("Invalid split.");e.splits.add(s);}
            if(e.category.equals(Budget.SPLIT)!=e.split())throw new JSONException("Invalid split.");
            if(b.account(e.account)==null||(!e.destination.isEmpty()&&b.account(e.destination)==null)||(!e.category.isEmpty()&&!e.split()&&b.category(e.category)==null))throw new JSONException("Invalid saved transaction.");b.entries.add(e);}
        JSONObject bills=root.optJSONObject("billCategories");if(bills!=null){Iterator<String> k=bills.keys();while(k.hasNext()){String key=k.next(),id=bills.getString(key);if(key.length()<=100&&b.category(id)!=null)b.billCategories.put(key,id);}} // a deleted category's bills are just forgotten
        JSONArray scheduled=root.optJSONArray("scheduled");
        if(scheduled!=null)for(int i=0;i<scheduled.length();i++){JSONObject j=scheduled.getJSONObject(i);LocalDate.parse(j.getString("next"));Budget.Scheduled s=new Budget.Scheduled(j.getString("payee"),j.getString("category"),j.getString("account"),j.getString("next"),j.getLong("amount"),j.getString("repeat"));s.id=j.getString("id");s.day=Math.max(0,Math.min(31,j.optInt("day",s.day)));s.memo=j.optString("memo","");s.billKey=j.optString("billKey","");
            if(b.account(s.account)==null||(!s.category.isEmpty()&&b.category(s.category)==null)||!Arrays.asList(Budget.Scheduled.REPEATS).contains(s.repeat))throw new JSONException("Invalid scheduled transaction.");b.scheduled.add(s);}
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
