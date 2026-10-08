package com.mybudget.app;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.*;
import java.util.regex.*;

/**
 * Bank statement import: CSV (comma, semicolon or tab), OFX/QFX or QIF. The user matches columns once (date, payee, amount,
 * or separate money in/out columns); rows already in the account (same date, amount and statement payee, or payee for rows
 * not imported) are skipped, and so are rows dated in the future or before the account opened. A row with the same amount as
 * a transaction entered here within MATCH_DAYS (or an upcoming one due then) is matched to it instead of added. Outflows get
 * the payee's suggested category (Budget.suggestedCategory), otherwise "To categorize"; inflows go to To budget unless the
 * payee's last transaction was a refund to a category. Import rules (Budget.rule) come first: the first rule whose text is in
 * the payee renames it and/or gives its category. Imported rows are cleared and wait for review.
 */
public final class CsvImport {
    public static final String TO_CATEGORIZE="To categorize";
    // Australian day/month first; ISO; US month/day only when day/month can't read the column.
    public static final String[] DATE_FORMATS={"d/M/uuuu","uuuu-MM-dd","d-M-uuuu","d.M.uuuu","d MMM uuuu","d/M/uu","M/d/uuuu"};
    /** [matched]: rows that were already here as a transaction entered by hand, from Planner or upcoming ([matches]); not added again. */
    public static final class Result { public int added,duplicates,future,beforeOpening,unreadable,matchedRules,matched;public final List<Budget.Entry> entries=new ArrayList<>(),matches=new ArrayList<>(); }
    /** How far apart (in days) a statement row and a transaction entered here may be dated and still be the same one. */
    public static final int MATCH_DAYS=7;

    /** A statement file's rows: OFX/QFX and QIF files become a header row (Date, Payee, Amount) and one row per transaction; anything else is CSV. */
    public static List<List<String>> statement(String text){
        if(text.startsWith("﻿"))text=text.substring(1);String start=text.trim();
        if(start.startsWith("OFXHEADER")||OFX.matcher(start.substring(0,Math.min(start.length(),4000))).find())return ofx(text); // OFX 1's header, or <OFX> after OFX 2's XML header
        if(start.regionMatches(true,0,"!Type:",0,6)||start.regionMatches(true,0,"!Option:",0,8)||start.regionMatches(true,0,"!Account",0,8)||start.regionMatches(true,0,"!Clear:",0,7))return qif(text); // Hunt 24 B6: !Clear:AutoSwitch too
        return parse(text);
    }
    private static final Pattern OFX=Pattern.compile("(?i)<OFX>");
    /** Hunt 25 B5: a file's text: UTF-8 (what MyBudget writes), UTF-16 with its byte order mark (Excel's "Unicode text"), else
     * Windows-1252 (an OFX 1 file's CHARSET:1252, a Latin-1 CSV), so "CAFÉ" and "£12.50" stay readable instead of turning into
     * replacement marks. */
    public static String decode(byte[] b){
        if(b.length>=2&&(b[0]==(byte)0xFF&&b[1]==(byte)0xFE||b[0]==(byte)0xFE&&b[1]==(byte)0xFF))return new String(b,java.nio.charset.StandardCharsets.UTF_16);
        try{return java.nio.charset.StandardCharsets.UTF_8.newDecoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT).onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT).decode(java.nio.ByteBuffer.wrap(b)).toString();}
        catch(java.nio.charset.CharacterCodingException e){return new String(b,java.nio.charset.Charset.forName("windows-1252"));}}
    /** RFC 4180 rows: quoted fields may hold the separator, quotes ("") and line breaks. A leading BOM is ignored. The separator is a comma, or a semicolon or tab when the first line has more of those. */
    public static List<List<String>> parse(String text){
        if(text.startsWith("﻿"))text=text.substring(1);char sep=separator(text);
        List<List<String>> rows=new ArrayList<>();List<String> row=new ArrayList<>();StringBuilder cell=new StringBuilder();boolean quoted=false;
        for(int i=0;i<text.length();i++){char c=text.charAt(i);
            if(quoted){if(c=='"'){if(i+1<text.length()&&text.charAt(i+1)=='"'){cell.append('"');i++;}else quoted=false;}else cell.append(c);continue;}
            // Hunt 25 B3: a quote opens quoting only at the start of a field (RFC 4180); inside one (BUNNINGS 12" SAW) it's just text.
            if(c=='"'&&cell.toString().trim().isEmpty()){cell.setLength(0);quoted=true;}else if(c==sep){row.add(cell.toString().trim());cell.setLength(0);}
            else if(c=='\n'||c=='\r'){if(c=='\r'&&i+1<text.length()&&text.charAt(i+1)=='\n')i++;row.add(cell.toString().trim());cell.setLength(0);if(!(row.size()==1&&row.get(0).isEmpty()))rows.add(row);row=new ArrayList<>();}
            else cell.append(c);}
        if(cell.length()>0||!row.isEmpty()){row.add(cell.toString().trim());if(!(row.size()==1&&row.get(0).isEmpty()))rows.add(row);}
        return rows;
    }
    /**
     * The separator the first line (outside quotes) uses most: comma (also when none is there), semicolon or tab. Hunt 25 B7: one
     * the next lines (up to 5) don't all have as often is passed over for one they do (a payee "PAYPAL;EBAY;AU" in a comma file).
     * Quotes count as parse() reads them (B3: only at the start of a field).
     */
    static char separator(String text){List<int[]> lines=new ArrayList<>();int[] n=new int[3];boolean quoted=false,start=true;
        for(int i=0;i<text.length()&&lines.size()<6;i++){char c=text.charAt(i);
            if(quoted){if(c=='"'){if(i+1<text.length()&&text.charAt(i+1)=='"')i++;else quoted=false;}continue;}
            if(c=='"'&&start){quoted=true;continue;}
            if(c=='\n'||c=='\r'){if(n[0]+n[1]+n[2]>0)lines.add(n);n=new int[3];start=true;continue;}
            int k=c==','?0:c==';'?1:c=='\t'?2:-1;if(k>=0){n[k]++;start=true;}else if(c!=' ')start=false;}
        if(n[0]+n[1]+n[2]>0&&lines.size()<6)lines.add(n);if(lines.isEmpty())return ',';
        int[] first=lines.get(0);boolean[] steady=new boolean[3];boolean any=false;
        for(int k=0;k<3;k++){steady[k]=first[k]>0;for(int[] l:lines)if(l[k]!=first[k])steady[k]=false;any|=steady[k];}
        int commas=first[0],semis=first[1],tabs=first[2];
        if(lines.size()>1&&any){if(!steady[0])commas=0;if(!steady[1])semis=0;if(!steady[2])tabs=0;}
        return semis>commas&&semis>=tabs?';':tabs>commas&&tabs>semis?'\t':',';}
    /**
     * OFX 1 (SGML, closing tags optional) or 2 (XML) bank and card statements, also Quicken's QFX: each STMTTRN's posted date,
     * NAME (else MEMO) and TRNAMT. Amounts are written with a point; a comma is read as the decimal point when there's no point.
     */
    public static List<List<String>> ofx(String text){List<List<String>> rows=new ArrayList<>();rows.add(Arrays.asList("Date","Payee","Amount"));
        // Hunt 24 B7: a download of several accounts (each statement's own ACCTID, under BANKACCTFROM or CCACCTFROM; a transfer's
        // other account under ...ACCTTO doesn't count) would put them all into the one account chosen.
        Set<String> ids=new HashSet<>();Matcher acct=Pattern.compile("(?is)<(?:BANK|CC)ACCTFROM>.*?<ACCTID>([^<\r\n]+)").matcher(text);while(acct.find())ids.add(acct.group(1).trim());
        if(ids.size()>1)throw new IllegalArgumentException("This file holds "+ids.size()+" accounts. Download one account's statement at a time, so its transactions go into the right account.");
        Matcher m=TRN.matcher(text);
        while(m.find()){String t=m.group(1),posted=field(t,"DTPOSTED"),amount=field(t,"TRNAMT").replace(" ",""),name=field(t,"NAME"),memo=field(t,"MEMO");
            if(posted.length()<8||!posted.substring(0,8).matches("\\d{8}")||amount.isEmpty())continue;
            if(amount.contains(",")&&!amount.contains("."))amount=amount.replace(',','.');
            String payee=(name.isEmpty()?memo:name).replaceAll("\\s+"," ").trim();
            rows.add(Arrays.asList(posted.substring(0,4)+"-"+posted.substring(4,6)+"-"+posted.substring(6,8),payee,amount));}
        return rows;}
    private static final Pattern TRN=Pattern.compile("(?is)<STMTTRN>(.*?)(?=</STMTTRN>|<STMTTRN>|</BANKTRANLIST>|$)");
    private static String field(String block,String tag){Matcher m=Pattern.compile("(?i)<"+tag+">([^<\\r\\n]*)").matcher(block);return m.find()?unescape(m.group(1).trim()):"";}
    private static String unescape(String s){Matcher m=Pattern.compile("&#(\\d{1,6});").matcher(s);StringBuffer b=new StringBuffer();
        while(m.find()){int c=Integer.parseInt(m.group(1));m.appendReplacement(b,Matcher.quoteReplacement(c>0&&c<0x110000?new String(Character.toChars(c)):""));}m.appendTail(b);
        return b.toString().replace("&lt;","<").replace("&gt;",">").replace("&quot;","\"").replace("&apos;","'").replace("&nbsp;"," ").replace("&amp;","&");}
    /**
     * QIF (Quicken interchange) bank and card files: each record's D (date), T or U (amount) and P (payee, else M memo), ended by ^.
     * Dates like 10/5'26 or 10/ 5/26 are tidied, then read as the CSV import reads them (day/month first, then month/day);
     * when one format reads them all they're written as ISO dates. Investment files are refused.
     */
    public static List<List<String>> qif(String text){List<List<String>> rows=new ArrayList<>();rows.add(Arrays.asList("Date","Payee","Amount"));
        String date="",amount="",payee="",memo="";boolean inAccount=false;int accounts=0;
        for(String raw:text.split("\r\n|\r|\n")){String line=raw.trim();if(line.isEmpty())continue;char k=line.charAt(0);String v=line.substring(1).trim();
            if(k=='!'){String h=line.toLowerCase(Locale.ROOT);if(h.startsWith("!type:invst"))throw new IllegalArgumentException("Investment QIF files can't be imported. Choose a bank or card account's file.");
                // Hunt 24 B6: an !Account block (N name, T type, D description) describes an account, not a transaction; several of them
                // mean several accounts in one file, which would all land in the one account chosen.
                if(h.startsWith("!account")){inAccount=true;if(++accounts>1)throw new IllegalArgumentException("This file holds several accounts. Export one account at a time, so its transactions go into the right account.");}
                else if(h.startsWith("!type:"))inAccount=false;continue;}
            if(k=='^'){if(!inAccount&&!date.isEmpty()&&!amount.isEmpty())rows.add(Arrays.asList(date,payee.isEmpty()?memo:payee,amount));date=amount=payee=memo="";inAccount=false;continue;}
            if(inAccount)continue;
            // Hunt 24 B5: spaces go only around the date's separators (1/ 5'26, 10/ 9/26), so "5 Oct 2026" keeps its words; Quicken's
            // one-digit year (1/5'9) is 2009.
            if(k=='D'){date=v.replace('\'','/').replaceAll("\\s*/\\s*","/").trim();if(date.matches("\\d{1,2}/\\d{1,2}/\\d"))date=date.substring(0,date.length()-1)+"0"+date.charAt(date.length()-1);}
            else if(k=='T'||k=='U'&&amount.isEmpty())amount=v;else if(k=='P')payee=v;else if(k=='M')memo=v;}
        if(!inAccount&&!date.isEmpty()&&!amount.isEmpty())rows.add(Arrays.asList(date,payee.isEmpty()?memo:payee,amount)); // a last record without its ^
        // The format that reads every date, else the one that reads the most: dates it can't read are left empty, so those rows
        // count as unreadable instead of the whole file being refused.
        List<String> formats=new ArrayList<>(Arrays.asList(DATE_FORMATS));formats.add("M/d/uu");String best=null;int most=0;
        for(String f:formats){int n=0;for(int i=1;i<rows.size();i++)try{date(rows.get(i).get(0),f);n++;}catch(Exception e){}if(n>most){most=n;best=f;}if(n==rows.size()-1)break;}
        // Hunt 25 B6: a two-digit year that would be in the future is last century's (Quicken writes 12/31/98 for 1998).
        if(best!=null)for(int i=1;i<rows.size();i++){List<String> r=rows.get(i);String iso;try{LocalDate d=date(r.get(0),best);if(best.endsWith("/uu")&&d.isAfter(LocalDate.now()))d=d.minusYears(100);iso=d.toString();}catch(Exception e){iso="";}rows.set(i,Arrays.asList(iso,r.get(1),r.get(2)));}
        return rows;}
    /**
     * Cents from "$1,234.56", "-12", "(12.00)" (negative), "12.00 DR" (negative) or "12.00 CR"; throws when unreadable.
     * A currency symbol or code around the number is ignored ("€12.50", "12.50 EUR", "A$5", "NZ$ 5", "USD -3"): codes only when
     * they're real ISO 4217 ones, so DR and CR keep their meaning ("12.50 IDR" is rupiah, not a debit). A comma is a thousands
     * separator ("1,250" is 1,250.00), except one followed by just one or two digits at the end, which only a decimal comma can
     * be: "12,50" and "1.234,56" (points then group thousands) read as 12.50 and 1,234.56, as European banks write them.
     */
    public static long amount(String s){
        String t=s.trim().toUpperCase(Locale.ROOT).replaceAll("[\\s\\u00A0\\u2007\\u202F]+"," ");boolean negative=false;
        Matcher code=CODE.matcher(t);StringBuffer kept=new StringBuffer();
        while(code.find())code.appendReplacement(kept,Budget.knownCurrency(code.group())?"":Matcher.quoteReplacement(code.group()));
        code.appendTail(kept);t=kept.toString().replaceAll("[A-Z]{0,2}\\p{Sc}","").trim(); // a symbol with its letters: $, €, A$, US$, NZ$, R$
        // Hunt 23: DR and brackets both mean money out, so "(12.00) DR" is -12.00 (they used to cancel out); CR says money in.
        boolean credit=false;if(t.endsWith("DR")){negative=true;t=t.substring(0,t.length()-2).trim();}else if(t.endsWith("CR")){credit=true;t=t.substring(0,t.length()-2).trim();}
        if(t.startsWith("(")&&t.endsWith(")")){negative=!credit;t=t.substring(1,t.length()-1);}
        else if(t.length()>1&&(t.endsWith("-")||t.endsWith("−"))&&!t.startsWith("-")){negative=true;t=t.substring(0,t.length()-1).trim();} // Hunt 24 B9: a trailing minus ("1.234,56-"), as some European banks write it
        t=t.replace('−','-'); // a typographic minus, as some banks write it
        t=t.replace(" ","");if(t.startsWith("+"))t=t.substring(1);t=DECIMAL_COMMA.matcher(t).matches()?t.replace(".","").replace(',','.'):t.replace(",","");
        long v=new BigDecimal(t).movePointRight(2).longValueExact();if(Math.abs(v)>10_000_000_000L)throw new IllegalArgumentException("Over 100 million."); // as typed amounts (Budget.evaluate)
        return negative?-Math.abs(v):v;
    }
    private static final Pattern DECIMAL_COMMA=Pattern.compile("-?(?:\\d{1,3}(?:\\.\\d{3})+|\\d+),\\d{1,2}");
    private static final Pattern TRANSFER=Pattern.compile("(?i)\\b(?:transfer|tfr|trf|xfer)\\b"); // a statement's word for a transfer
    private static final Pattern CODE=Pattern.compile("(?<![A-Z])[A-Z]{3}(?![A-Z])"); // three letters on their own: a currency code when it's a known one
    public static LocalDate date(String s,String format){return LocalDate.parse(s.trim(),DateTimeFormatter.ofPattern(format,Locale.ENGLISH).withResolverStyle(ResolverStyle.STRICT));}
    /** The first format that reads every non-empty value in [column] (after the header row when [header]), or null. */
    public static String detectDateFormat(List<List<String>> rows,int column,boolean header){
        for(String f:DATE_FORMATS){boolean all=true,any=false;for(int i=header?1:0;i<rows.size();i++){List<String> r=rows.get(i);if(column>=r.size()||r.get(column).isEmpty())continue;any=true;try{date(r.get(column),f);}catch(Exception e){all=false;break;}}if(all&&any)return f;}
        return null;
    }
    /** Whether the first row looks like column names (its cells aren't dates or amounts). */
    public static boolean looksLikeHeader(List<String> first){for(String c:first){if(c.isEmpty())continue;try{amount(c);return false;}catch(Exception e){}for(String f:DATE_FORMATS)try{date(c,f);return false;}catch(Exception e){}}return true;}

    /**
     * Adds rows to [budget] in [account]. Columns are 0-based; [outflowColumn] -1 means [amountColumn] is signed,
     * otherwise [amountColumn] holds money in and [outflowColumn] money out.
     */
    public static Result run(Budget budget,List<List<String>> rows,boolean header,int dateColumn,int payeeColumn,int amountColumn,int outflowColumn,String dateFormat,Budget.Account account){
        // Each row already in the account matches one imported row: two identical rows in one statement are two transactions.
        Result r=new Result();Map<String,Integer> existing=new HashMap<>();
        // An imported row is known by the statement's own payee text (bankPayee), so renaming or merging its payee later doesn't hide it; others by their payee.
        for(Budget.Entry e:budget.entries)if(e.account.equals(account.id))existing.merge(key(e.date,e.amount,e.bankPayee.isEmpty()?e.payee:e.bankPayee),1,Integer::sum);
        // Hunt 23: transfers made in MyBudget, in or out of this account, by date and amount: the statement names them its own way
        // ("TFR to savings" for MyBudget's "Transfer to Savings"), so a row that says it is a transfer matches one of them.
        Map<String,Integer> transfers=new HashMap<>();
        for(Budget.Entry e:budget.entries)if(e.transfer()){if(e.account.equals(account.id))transfers.merge(key(e.date,e.amount,""),1,Integer::sum);
            if(account.id.equals(e.destination))transfers.merge(key(e.date,-e.amount,""),1,Integer::sum);}
        // Matching: transactions entered here (by hand, from Planner) that no statement row has claimed yet, in this account.
        // Hunt 24 B3: not reconciled ones (already checked against the bank, and locked: matching would move them silently).
        List<Budget.Entry> unmatched=new ArrayList<>();for(Budget.Entry e:budget.entries)if(e.account.equals(account.id)&&!e.transfer()&&!e.reconciled&&Budget.statementPayee(e).isEmpty())unmatched.add(e);
        Budget.Category toCategorize=null;LocalDate today=LocalDate.now();
        // Hunt 24 B2: first every row's own checks and exact duplicates, for the whole file; then matching (closest pairs first,
        // over the whole file, so a row's order doesn't decide which transaction it takes); then the rest is added in file order.
        List<Object[]> pending=new ArrayList<>(); // {date, cents, statement payee, rule, its new name}
        for(int i=header?1:0;i<rows.size();i++){List<String> row=rows.get(i);
            LocalDate d;long cents;String payee;
            try{d=date(cell(row,dateColumn),dateFormat);payee=cell(row,payeeColumn);
                if(outflowColumn<0)cents=amount(cell(row,amountColumn));
                else{String in=cell(row,amountColumn),out=cell(row,outflowColumn);cents=(in.isEmpty()?0:Math.abs(amount(in)))-(out.isEmpty()?0:Math.abs(amount(out)));}
            }catch(Exception e){r.unreadable++;continue;}
            if(cents==0){r.unreadable++;continue;}if(payee.isEmpty())payee="(no payee)";payee=Budget.cut(payee,80);
            if(d.isAfter(today)){r.future++;continue;}if(d.toString().compareTo(account.date)<0){r.beforeOpening++;continue;}
            // A rule's new name counts for duplicates too: the row may have been imported (and renamed) before.
            Budget.Rule rule=budget.rule(payee);String named=rule==null||rule.rename.isEmpty()?payee:rule.rename;Budget.Category ruled=rule==null?null:budget.category(rule.category);if(ruled!=null&&ruled.payment())ruled=null;
            String k=key(d.toString(),cents,payee),k2=key(d.toString(),cents,named);if(existing.getOrDefault(k,0)<=0)k=k2;if(existing.getOrDefault(k,0)>0){existing.merge(k,-1,Integer::sum);r.duplicates++;
                // That transaction is this row. Hunt 24 B1: one entered here remembers the statement's payee (and is cleared), so a
                // later statement's row never takes it as well; it's out of matching for good.
                for(Iterator<Budget.Entry> it=unmatched.iterator();it.hasNext();){Budget.Entry e=it.next();if(key(e.date,e.amount,e.payee).equals(k)){it.remove();e.bankPayee=payee;e.cleared=true;break;}}
                continue;}
            String moved=key(d.toString(),cents,"");if(TRANSFER.matcher(payee).find()&&transfers.getOrDefault(moved,0)>0){transfers.merge(moved,-1,Integer::sum);r.duplicates++;continue;}
            pending.add(new Object[]{d,cents,payee,rule,named});
        }
        // The same money entered here already (same amount, dated within MATCH_DAYS): the closest pairs first (then the earlier
        // transaction, then the earlier row). That transaction takes the statement's date, is cleared and remembers the statement's
        // payee (so importing the statement again finds it), and keeps its own payee, category and note.
        List<long[]> pairs=new ArrayList<>(); // {gap, row, entry}
        for(int p=0;p<pending.size();p++){LocalDate d=(LocalDate)pending.get(p)[0];long cents=(Long)pending.get(p)[1];
            for(int u=0;u<unmatched.size();u++){Budget.Entry e=unmatched.get(u);if(e.amount!=cents)continue;long g=Math.abs(java.time.temporal.ChronoUnit.DAYS.between(d,LocalDate.parse(e.date)));if(g<=MATCH_DAYS)pairs.add(new long[]{g,p,u});}}
        pairs.sort((a,b)->a[0]!=b[0]?Long.compare(a[0],b[0]):!unmatched.get((int)a[2]).date.equals(unmatched.get((int)b[2]).date)?unmatched.get((int)a[2]).date.compareTo(unmatched.get((int)b[2]).date):Long.compare(a[1],b[1]));
        Budget.Entry[] takes=new Budget.Entry[pending.size()];Set<Budget.Entry> taken=Collections.newSetFromMap(new IdentityHashMap<>());
        for(long[] pr:pairs){Budget.Entry e=unmatched.get((int)pr[2]);if(takes[(int)pr[1]]!=null||taken.contains(e))continue;takes[(int)pr[1]]=e;taken.add(e);}
        // Rows nothing entered here took: an upcoming transaction due then (within MATCH_DAYS) is entered by the row, dated as the row.
        // Hunt 25 B2: rows in date order (a statement is often newest first), so each takes the date the transaction has reached by
        // then; B1: entered with the row's date, as its own date can still be ahead.
        Integer[] byDate=new Integer[pending.size()];for(int p=0;p<byDate.length;p++)byDate[p]=p;Arrays.sort(byDate,(a,b)->((LocalDate)pending.get(a)[0]).compareTo((LocalDate)pending.get(b)[0]));
        for(int p:byDate){if(takes[p]!=null)continue;LocalDate d=(LocalDate)pending.get(p)[0];long cents=(Long)pending.get(p)[1];Budget.Scheduled due=null;long best=Long.MAX_VALUE;
            for(Budget.Scheduled s:budget.scheduled){if(!s.account.equals(account.id)||s.amount!=cents)continue;long g=Math.abs(java.time.temporal.ChronoUnit.DAYS.between(d,LocalDate.parse(s.next)));if(g<=MATCH_DAYS&&g<best){due=s;best=g;}}
            if(due!=null)takes[p]=budget.enter(due,"",true,d.toString());}
        for(int p=0;p<pending.size();p++){Object[] row=pending.get(p);LocalDate d=(LocalDate)row[0];long cents=(Long)row[1];String payee=(String)row[2];Budget.Rule rule=(Budget.Rule)row[3];String named=(String)row[4];
            Budget.Category ruled=rule==null?null:budget.category(rule.category);if(ruled!=null&&ruled.payment())ruled=null;
            Budget.Entry match=takes[p];
            if(match!=null&&taken.contains(match)){String was=key(match.date,match.amount,match.payee);if(existing.getOrDefault(was,0)>0)existing.merge(was,-1,Integer::sum);}
            if(match!=null){match.date=d.toString();match.cleared=true;match.bankPayee=payee;budget.changed();r.matched++;r.matches.add(match);continue;}
            if(rule!=null)r.matchedRules++;String bank=payee;payee=named;
            // A statement payee imported before takes that row's payee as it's named now (a rule's rename comes first), and its category.
            Budget.Entry seen=budget.lastForBankPayee(bank);if(seen!=null&&(rule==null||rule.rename.isEmpty()))payee=seen.payee.trim();
            // The payee's suggestion (Budget.suggestedCategory: its setting, or its usual category, which one odd purchase doesn't change).
            Budget.Entry last=seen!=null&&!seen.split()?seen:budget.lastForPayee(payee);String usual=budget.suggestedCategory(payee);Budget.Category known=usual==null?null:budget.category(usual);
            String category;
            if(account.tracking())category=""; // off budget: no categories
            else if(ruled!=null)category=ruled.id;
            else if(cents>0)category=known!=null&&last!=null&&last.amount>0?known.id:""; // money in: a refund only when the payee's last one was
            else if(known!=null)category=known.id;
            else{if(toCategorize==null)toCategorize=toCategorize(budget);category=toCategorize.id;}
            Budget.Entry e=new Budget.Entry(payee,category,account.id,d.toString(),cents);e.cleared=true;e.approved=false;e.memo=Budget.IMPORTED;e.bankPayee=bank;budget.validate(e);budget.entries.add(0,e);r.entries.add(e);r.added++;
        }
        return r;
    }
    private static String cell(List<String> row,int i){return i>=0&&i<row.size()?row.get(i):"";}
    private static String key(String date,long cents,String payee){return date+"|"+cents+"|"+payee.trim().toLowerCase(Locale.ROOT);}
    private static Budget.Category toCategorize(Budget b){for(Budget.Category c:b.categories)if(c.name.equalsIgnoreCase(TO_CATEGORIZE)&&!c.payment())return c;Budget.Category c=new Budget.Category(TO_CATEGORIZE);c.group="Everyday";b.categories.add(0,c);return c;}
}
