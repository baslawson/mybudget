package com.mybudget.app;
import org.json.*;
import java.time.*;
import java.util.*;

/** Versioned storage; old snapshots are retained separately by the Activity. */
public final class BudgetStore {
    // Version 4 adds scheduled transactions (and, in later fields of the same version, splits and credit cards):
    // MyBudget before it would drop them, so it must refuse the data instead of reading it.
    // Version 5 marks the fields added after 0.0.5 (new targets, tracking accounts, flags, review marks, notes, rules, hidden
    // payees, pins, bankPayee): 0.0.5 read them as version 4 and dropped them on its next save. Version 4 reads with defaults.
    // Version 6 adds upcoming splits (a scheduled transaction's category "split" with its "splits"): MyBudget 0.0.7 would
    // refuse or lose them, so it must refuse the data instead. Versions 2 to 5 read as before (no upcoming splits).
    // Version 6 also adds the budget's "currency" (ISO 4217); versions 1 to 5 read as AUD, the only currency before it.
    // Version 7 adds "payeeCategories" (a payee's category suggestion: always one category, or none) and an entry's "reconciled": MyBudget 0.0.8 would drop
    // it on its next save, so it must refuse the data instead. Versions 2 to 6 read with none (every payee automatic).
    public static final int VERSION=7;
    private static final java.util.regex.Pattern PHOTO=java.util.regex.Pattern.compile("[0-9a-f-]{0,40}([.]jpg)?"); // a photo is a file name only: never a path
    // Written out directly (years of transactions made building every JSONObject first slow), exactly as JSONObject would
    // write it: the same keys in the same order, JSONObject's escaping, plain whole numbers, a null string left out.
    // BudgetInstrumentation checks it against the JSONObject way.
    public static String encode(Budget b) throws JSONException {
        StringBuilder o=new StringBuilder(256+b.entries.size()*360);o.append("{\"version\":").append(VERSION).append(",\"categories\":[");
        for(int i=0;i<b.categories.size();i++){Budget.Category c=b.categories.get(i);open(o,i);put(o,"id",c.id);put(o,"name",c.name);put(o,"group",c.group);put(o,"target",c.target);put(o,"targetType",c.targetType);put(o,"due",c.due);put(o,"hidden",c.hidden);put(o,"snoozed",c.snoozed);put(o,"note",c.note);put(o,"dueDay",c.dueDay);put(o,"cardAccount",c.cardAccount);put(o,"weekday",c.weekday);put(o,"weeklyRefill",c.weeklyRefill);put(o,"dueDate",c.dueDate);put(o,"repeatMonths",c.repeatMonths);put(o,"pinned",c.pinned);
            o.append(",\"assigned\":{");boolean first=true;for(Map.Entry<String,Long> a:c.assigned.entrySet()){if(a.getValue()==null)continue;string(o.append(first?"":","),a.getKey()).append(':').append(a.getValue());first=false;}o.append("}}");}
        o.append("],\"accounts\":[");
        for(int i=0;i<b.accounts.size();i++){Budget.Account a=b.accounts.get(i);open(o,i);put(o,"id",a.id);put(o,"name",a.name);put(o,"date",a.date);put(o,"opening",a.opening);put(o,"reconciled",a.reconciled);put(o,"closed",a.closed);put(o,"type",a.type);put(o,"liability",a.liability);put(o,"rate",a.rate);put(o,"payment",a.payment);put(o,"frequency",a.frequency);o.append('}');}
        o.append("],\"entries\":[");
        for(int i=0;i<b.entries.size();i++){Budget.Entry e=b.entries.get(i);open(o,i);put(o,"id",e.id);put(o,"payee",e.payee);put(o,"category",e.category);put(o,"account",e.account);put(o,"destination",e.destination);put(o,"date",e.date);put(o,"amount",e.amount);put(o,"memo",e.memo);put(o,"cleared",e.cleared);put(o,"externalId",e.externalId);put(o,"billKey",e.billKey);put(o,"photo",e.photo);put(o,"flag",e.flag);put(o,"approved",e.approved);put(o,"bankPayee",e.bankPayee);put(o,"reconciled",e.reconciled);
            if(e.split())parts(o,e.splits);o.append('}');}
        o.append("],\"scheduled\":[");
        for(int i=0;i<b.scheduled.size();i++){Budget.Scheduled s=b.scheduled.get(i);open(o,i);put(o,"id",s.id);put(o,"payee",s.payee);put(o,"category",s.category);put(o,"account",s.account);put(o,"next",s.next);put(o,"repeat",s.repeat);put(o,"day",s.day);put(o,"amount",s.amount);put(o,"memo",s.memo);put(o,"billKey",s.billKey);
            if(s.split())parts(o,s.splits);o.append('}');}
        o.append("],\"billCategories\":{");for(Map.Entry<String,String> m:b.billCategories.entrySet())put(o,m.getKey(),m.getValue());
        o.append("},\"monthNotes\":{");for(Map.Entry<String,String> m:b.monthNotes.entrySet())put(o,m.getKey(),m.getValue());
        o.append("},\"flagNames\":[");for(int i=0;i<b.flagNames.length;i++){o.append(i>0?",":"");if(b.flagNames[i]==null)o.append("null");else string(o,b.flagNames[i]);}
        o.append("],\"hiddenPayees\":[");int n=0;for(String p:b.hiddenPayees){o.append(n++>0?",":"");if(p==null)o.append("null");else string(o,p);}
        o.append("],\"rules\":[");for(int i=0;i<b.rules.size();i++){Budget.Rule r=b.rules.get(i);open(o,i);put(o,"contains",r.contains);put(o,"rename",r.rename);put(o,"category",r.category);o.append('}');}
        o.append("],\"payeeCategories\":{");for(Map.Entry<String,String> m:b.payeeCategories.entrySet())put(o,m.getKey(),m.getValue());
        o.append('}');put(o,"currency",b.currency);return o.append('}').toString();
    }
    private static void open(StringBuilder o,int i){o.append(i>0?",{":"{");}
    /** ,"key":value (no comma straight after an opening brace); a null string is left out, as JSONObject.put(key,null) does. */
    private static void put(StringBuilder o,String key,String value){if(value==null)return;string(string(comma(o),key).append(':'),value);}
    private static void put(StringBuilder o,String key,long value){string(comma(o),key).append(':').append(value);}
    private static void put(StringBuilder o,String key,boolean value){string(comma(o),key).append(':').append(value);}
    /** [s] in quotes, escaped as JSONObject (JSONStringer) escapes it. */
    private static StringBuilder string(StringBuilder o,String s){o.append('"');
        for(int i=0;i<s.length();i++){char c=s.charAt(i);switch(c){case'"':case'\\':case'/':o.append('\\').append(c);break;case'\t':o.append("\\t");break;case'\b':o.append("\\b");break;
            case'\n':o.append("\\n");break;case'\r':o.append("\\r");break;case'\f':o.append("\\f");break;default:if(c<=0x1F)o.append(String.format("\\u%04x",(int)c));else o.append(c);}}
        return o.append('"');}
    private static StringBuilder comma(StringBuilder o){char last=o.charAt(o.length()-1);return last=='{'?o:o.append(',');}
    private static void parts(StringBuilder o,List<Budget.Split> parts){o.append(",\"splits\":[");for(int i=0;i<parts.size();i++){Budget.Split p=parts.get(i);open(o,i);put(o,"category",p.category);put(o,"amount",p.amount);put(o,"memo",p.memo);o.append('}');}o.append(']');}
    /** A backup file: the saved budget plus what marks it as MyBudget's, and when it was made (local date-time). */
    // Backup version 2: storage version 5 inside (MyBudget 0.0.5 refuses it as newer). Version 1 backups still restore.
    // Backup version 3: storage version 6 (upcoming splits, the currency; MyBudget 0.0.7 refuses it as newer). Versions 1 and 2 still restore.
    // Backup version 4: storage version 7 (payee category suggestions; MyBudget 0.0.8 refuses it as newer). Versions 1 to 3 still restore.
    public static final int BACKUP_VERSION=4;
    public static final class Backup { public final Budget budget; public final String created; Backup(Budget budget,String created){this.budget=budget;this.created=created;} }
    public static String backup(Budget b,LocalDateTime created) throws JSONException {
        return new JSONObject(encode(b)).put("app","MyBudget").put("backupVersion",BACKUP_VERSION).put("created",created.withNano(0).toString()).toString(2);
    }
    /** Reads a backup file in full; throws (so nothing changes) unless it's a MyBudget backup this version can read. */
    public static Backup readBackup(String raw) throws JSONException {
        JSONObject root;try{root=new JSONObject(raw);}catch(JSONException e){throw new JSONException("This file isn't a MyBudget backup.");}
        if(!"MyBudget".equals(root.optString("app"))||!root.has("version"))throw new JSONException("This file isn't a MyBudget backup.");
        int version=root.optInt("backupVersion",0);if(version<1)throw new JSONException("This file isn't a MyBudget backup.");
        if(version>BACKUP_VERSION||root.optInt("version",1)>VERSION)throw new JSONException("This backup is from a newer MyBudget. Update MyBudget, then restore it.");
        try{return new Backup(decode(raw),root.optString("created",""));}catch(JSONException|RuntimeException e){throw new JSONException("This backup is damaged and can't be restored.");}
    }
    public static Budget decode(String raw) throws JSONException {
        JSONObject root=new JSONObject(raw);if(root.optInt("version",1)==1)return migrate(root);
        // Version 3 added expenses sent by other apps (externalId, billKey); version 2 reads without them. Optional
        // fields added later read with defaults (category hidden/snoozed/note/dueDay, account closed). Version 4: scheduled.
        int version=root.getInt("version");if(version<2||version>VERSION)throw new JSONException("Unsupported budget version.");
        Budget b=new Budget();JSONArray cats=root.getJSONArray("categories"),accounts=root.getJSONArray("accounts"),entries=root.getJSONArray("entries");
        for(int i=0;i<cats.length();i++){JSONObject j=cats.getJSONObject(i);Budget.Category c=new Budget.Category(j.getString("name"));c.id=j.getString("id");c.group=j.getString("group");c.target=j.getLong("target");c.targetType=j.getString("targetType");c.due=j.getString("due");if(!c.due.isEmpty())YearMonth.parse(c.due);c.hidden=j.optBoolean("hidden",false);c.snoozed=j.optString("snoozed","");c.note=j.optString("note","");c.dueDay=Math.max(0,Math.min(31,j.optInt("dueDay",0)));c.cardAccount=j.optString("cardAccount","");
            // Weekly and by-date targets (added later in version 4): missing reads as Monday, refill, no date, no repeat.
            c.weekday=Math.max(1,Math.min(7,j.optInt("weekday",1)));c.weeklyRefill=j.optBoolean("weeklyRefill",true);c.dueDate=j.optString("dueDate","");if(!c.dueDate.isEmpty())LocalDate.parse(c.dueDate);int repeat=j.optInt("repeatMonths",0);c.repeatMonths=repeat==3||repeat==6||repeat==12?repeat:0;c.pinned=j.optBoolean("pinned",false); // pinned to Home (added later in version 4)
            JSONObject assigned=j.getJSONObject("assigned");Iterator<String> keys=assigned.keys();while(keys.hasNext()){String key=keys.next();YearMonth.parse(key);c.assigned.put(key,assigned.getLong(key));}b.categories.add(c);}
        for(int i=0;i<accounts.length();i++){JSONObject j=accounts.getJSONObject(i);LocalDate.parse(j.getString("date"));Budget.Account a=new Budget.Account(j.getString("name"),j.getString("date"),j.getLong("opening"));a.id=j.getString("id");a.reconciled=j.optString("reconciled","");a.closed=j.optBoolean("closed",false);a.type=j.optString("type","cash");if(!a.type.equals("cash")&&!a.type.equals("credit")&&!a.type.equals("tracking"))throw new JSONException("Unknown account type.");
            // Tracking accounts and loan terms (added later in version 4): missing reads as an asset with no terms. A MyBudget before them refuses "tracking".
            a.liability=a.tracking()&&j.optBoolean("liability",false);a.rate=Math.max(0,Math.min(100_000,j.optLong("rate",0)));a.payment=Math.max(0,j.optLong("payment",0));a.frequency=j.optString("frequency","Monthly");if(!Arrays.asList(Budget.FREQUENCIES).contains(a.frequency))a.frequency="Monthly";b.accounts.add(a);}
        for(Budget.Category c:b.categories)if(c.payment()&&(b.account(c.cardAccount)==null||!b.account(c.cardAccount).credit()))throw new JSONException("Payment category without its card.");
        // Transactions look their accounts and categories up by id (the first with each id, as Budget.account and category do).
        Map<String,Budget.Account> accountIds=new HashMap<>();for(Budget.Account a:b.accounts)accountIds.putIfAbsent(a.id,a);Map<String,Budget.Category> categoryIds=new HashMap<>();for(Budget.Category c:b.categories)categoryIds.putIfAbsent(c.id,c);
        for(int i=0;i<entries.length();i++){JSONObject j=entries.getJSONObject(i);LocalDate.parse(j.getString("date"));Budget.Entry e=new Budget.Entry(j.getString("payee"),j.getString("category"),j.getString("account"),j.getString("date"),j.getLong("amount"));e.id=j.getString("id");e.destination=j.getString("destination");e.memo=j.getString("memo");e.cleared=j.getBoolean("cleared");e.externalId=j.optString("externalId","");e.billKey=j.optString("billKey","");e.photo=j.optString("photo","");e.flag=Math.max(0,Math.min(Budget.FLAGS.length-1,j.optInt("flag",0)));e.approved=j.optBoolean("approved",true);e.bankPayee=j.optString("bankPayee","").trim();e.bankPayee=Budget.cut(e.bankPayee,80);e.reconciled=j.optBoolean("reconciled",false)&&e.cleared;if(!PHOTO.matcher(e.photo).matches())throw new JSONException("Invalid photo name."); // a file name only: never a path (reconciled: version 7, missing = not reconciled)
            JSONArray parts=j.optJSONArray("splits");if(parts!=null)for(int k=0;k<parts.length();k++){JSONObject p=parts.getJSONObject(k);Budget.Split s=new Budget.Split(p.getString("category"),p.getLong("amount"));s.memo=p.optString("memo","");if(!s.category.isEmpty()&&categoryIds.get(s.category)==null)throw new JSONException("Invalid split.");e.splits.add(s);}
            long parted=0;for(Budget.Split s:e.splits)parted+=s.amount;if(e.category.equals(Budget.SPLIT)!=e.split()||(e.split()&&parted!=e.amount))throw new JSONException("Invalid split."); // the parts add up to the total
            // Rows imported before bankPayee was kept (0.0.6) matched re-imports by payee: keep that text, so renaming them later doesn't import them again.
            // Not only for version 4: 0.0.6 saved such rows as version 5 with no bankPayee. Other transactions keep none.
            e.bankPayee=Budget.statementPayee(e);
            if(accountIds.get(e.account)==null||(!e.destination.isEmpty()&&accountIds.get(e.destination)==null)||(!e.category.isEmpty()&&!e.split()&&categoryIds.get(e.category)==null))throw new JSONException("Invalid saved transaction.");b.entries.add(e);}
        JSONObject bills=root.optJSONObject("billCategories");if(bills!=null){Iterator<String> k=bills.keys();while(k.hasNext()){String key=k.next(),id=bills.getString(key);if(key.length()<=100&&b.category(id)!=null)b.billCategories.put(key,id);}} // a deleted category's bills are just forgotten
        JSONObject notes=root.optJSONObject("monthNotes");if(notes!=null){Iterator<String> k=notes.keys();while(k.hasNext()){String key=k.next(),text=notes.getString(key).trim();YearMonth.parse(key);if(!text.isEmpty())b.monthNotes.put(key,Budget.cut(text,Budget.MONTH_NOTE_MAX));}} // month notes: added later in version 4
        // Flag names, hidden payees and import rules (added later in version 4): missing reads as none. A rule's deleted category is dropped.
        JSONArray flagNames=root.optJSONArray("flagNames");if(flagNames!=null)for(int i=1;i<Math.min(flagNames.length(),Budget.FLAGS.length);i++){String n=flagNames.optString(i,"").trim();b.flagNames[i]=Budget.cut(n,30);}
        JSONArray hidden=root.optJSONArray("hiddenPayees");if(hidden!=null)for(int i=0;i<hidden.length();i++){String p=hidden.getString(i).trim().toLowerCase(Locale.ROOT);if(!p.isEmpty())b.hiddenPayees.add(p);}
        JSONArray rules=root.optJSONArray("rules");if(rules!=null)for(int i=0;i<rules.length();i++){JSONObject j=rules.getJSONObject(i);Budget.Rule r=new Budget.Rule(j.getString("contains"),j.optString("rename",""),j.optString("category",""));if(b.category(r.category)==null)r.category="";boolean twice=false;for(Budget.Rule o:b.rules)twice|=o.contains.trim().equalsIgnoreCase(r.contains.trim());if(!twice&&!r.contains.trim().isEmpty()&&(!r.rename.trim().isEmpty()||!r.category.isEmpty()))b.rules.add(r);} // a repeated text never matched (the first wins): dropped
        // Payee category suggestions (version 7; missing: every payee automatic). A deleted or card payment category is forgotten.
        JSONObject chosen=root.optJSONObject("payeeCategories");if(chosen!=null){Iterator<String> k=chosen.keys();while(k.hasNext()){String key=k.next(),id=chosen.getString(key),p=key.trim().toLowerCase(Locale.ROOT);
            Budget.Category c=b.category(id);if(!p.isEmpty()&&p.length()<=Budget.PAYEE_MAX&&(id.isEmpty()||c!=null&&!c.payment()))b.payeeCategories.put(p,id);}}
        // Currency (version 6; missing: AUD). An unknown code is damaged data, refused rather than shown as some other money.
        if(version>=6){String code=root.optString("currency",Budget.DEFAULT_CURRENCY);if(!Budget.storableCurrency(code))throw new JSONException("Unknown currency.");b.currency=code;}
        JSONArray scheduled=root.optJSONArray("scheduled");
        if(scheduled!=null)for(int i=0;i<scheduled.length();i++){JSONObject j=scheduled.getJSONObject(i);LocalDate.parse(j.getString("next"));Budget.Scheduled s=new Budget.Scheduled(j.getString("payee"),j.getString("category"),j.getString("account"),j.getString("next"),j.getLong("amount"),j.getString("repeat"));s.id=j.getString("id");s.day=Math.max(0,Math.min(31,j.optInt("day",s.day)));s.memo=j.optString("memo","");s.billKey=j.optString("billKey","");
            // Upcoming splits (version 6): parts in known categories (or To budget), adding up to the amount, only with category "split".
            JSONArray parts=j.optJSONArray("splits");if(parts!=null)for(int k=0;k<parts.length();k++){JSONObject p=parts.getJSONObject(k);Budget.Split part=new Budget.Split(p.getString("category"),p.getLong("amount"));part.memo=p.optString("memo","");if(!part.category.isEmpty()&&b.category(part.category)==null)throw new JSONException("Invalid split.");s.splits.add(part);}
            long parted=0;for(Budget.Split p:s.splits)parted+=p.amount;if(s.category.equals(Budget.SPLIT)!=s.split()||(s.split()&&parted!=s.amount))throw new JSONException("Invalid split.");
            if(b.account(s.account)==null||(!s.category.isEmpty()&&!s.split()&&b.category(s.category)==null)||!Arrays.asList(Budget.Scheduled.REPEATS).contains(s.repeat))throw new JSONException("Invalid scheduled transaction.");b.scheduled.add(s);}
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
