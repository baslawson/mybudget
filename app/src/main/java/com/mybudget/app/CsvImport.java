package com.mybudget.app;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.*;

/**
 * Bank statement CSV import. The user matches columns once (date, payee, amount, or separate money in/out columns);
 * rows already in the account (same date, amount and payee) are skipped, and so are rows dated in the future or before
 * the account opened. Outflows get the category last used with their payee, otherwise "To categorize"; inflows go to
 * Ready to Assign unless the payee's last transaction was a refund to a category. Imported rows are cleared.
 */
public final class CsvImport {
    public static final String TO_CATEGORIZE="To categorize";
    // Australian day/month first; ISO; US month/day only when day/month can't read the column.
    public static final String[] DATE_FORMATS={"d/M/uuuu","uuuu-MM-dd","d-M-uuuu","d.M.uuuu","d MMM uuuu","d/M/uu","M/d/uuuu"};
    public static final class Result { public int added,duplicates,future,beforeOpening,unreadable; public final List<Budget.Entry> entries=new ArrayList<>(); }

    /** RFC 4180 rows: quoted fields may hold commas, quotes ("") and line breaks. A leading BOM is ignored. */
    public static List<List<String>> parse(String text){
        if(text.startsWith("﻿"))text=text.substring(1);
        List<List<String>> rows=new ArrayList<>();List<String> row=new ArrayList<>();StringBuilder cell=new StringBuilder();boolean quoted=false;
        for(int i=0;i<text.length();i++){char c=text.charAt(i);
            if(quoted){if(c=='"'){if(i+1<text.length()&&text.charAt(i+1)=='"'){cell.append('"');i++;}else quoted=false;}else cell.append(c);continue;}
            if(c=='"')quoted=true;else if(c==','){row.add(cell.toString().trim());cell.setLength(0);}
            else if(c=='\n'||c=='\r'){if(c=='\r'&&i+1<text.length()&&text.charAt(i+1)=='\n')i++;row.add(cell.toString().trim());cell.setLength(0);if(!(row.size()==1&&row.get(0).isEmpty()))rows.add(row);row=new ArrayList<>();}
            else cell.append(c);}
        if(cell.length()>0||!row.isEmpty()){row.add(cell.toString().trim());if(!(row.size()==1&&row.get(0).isEmpty()))rows.add(row);}
        return rows;
    }
    /** Cents from "$1,234.56", "-12", "(12.00)" (negative), "12.00 DR" (negative) or "12.00 CR"; throws when unreadable. */
    public static long amount(String s){
        String t=s.trim().toUpperCase(Locale.ROOT);boolean negative=false;
        if(t.endsWith("DR")){negative=true;t=t.substring(0,t.length()-2).trim();}else if(t.endsWith("CR"))t=t.substring(0,t.length()-2).trim();
        if(t.startsWith("(")&&t.endsWith(")")){negative=!negative;t=t.substring(1,t.length()-1);}
        t=t.replace("$","").replace(",","").replace(" ","").replace("AUD","");if(t.startsWith("+"))t=t.substring(1);
        long v=new BigDecimal(t).movePointRight(2).longValueExact();return negative?-Math.abs(v):v;
    }
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
        for(Budget.Entry e:budget.entries)if(e.account.equals(account.id))existing.merge(key(e.date,e.amount,e.payee),1,Integer::sum);
        Budget.Category toCategorize=null;LocalDate today=LocalDate.now();
        for(int i=header?1:0;i<rows.size();i++){List<String> row=rows.get(i);
            LocalDate d;long cents;String payee;
            try{d=date(cell(row,dateColumn),dateFormat);payee=cell(row,payeeColumn);
                if(outflowColumn<0)cents=amount(cell(row,amountColumn));
                else{String in=cell(row,amountColumn),out=cell(row,outflowColumn);cents=(in.isEmpty()?0:Math.abs(amount(in)))-(out.isEmpty()?0:Math.abs(amount(out)));}
            }catch(Exception e){r.unreadable++;continue;}
            if(cents==0){r.unreadable++;continue;}if(payee.isEmpty())payee="(no payee)";if(payee.length()>80)payee=payee.substring(0,80);
            if(d.isAfter(today)){r.future++;continue;}if(d.toString().compareTo(account.date)<0){r.beforeOpening++;continue;}
            String k=key(d.toString(),cents,payee);if(existing.getOrDefault(k,0)>0){existing.merge(k,-1,Integer::sum);r.duplicates++;continue;}
            Budget.Entry last=budget.lastForPayee(payee);Budget.Category known=last==null||last.split()||last.transfer()?null:budget.category(last.category);if(known!=null&&known.payment())known=null;
            String category;
            if(cents>0)category=known!=null&&last.amount>0?known.id:"";
            else if(known!=null)category=known.id;
            else{if(toCategorize==null)toCategorize=toCategorize(budget);category=toCategorize.id;}
            Budget.Entry e=new Budget.Entry(payee,category,account.id,d.toString(),cents);e.cleared=true;e.memo="Imported";budget.validate(e);budget.entries.add(0,e);r.entries.add(e);r.added++;
        }
        return r;
    }
    private static String cell(List<String> row,int i){return i>=0&&i<row.size()?row.get(i):"";}
    private static String key(String date,long cents,String payee){return date+"|"+cents+"|"+payee.trim().toLowerCase(Locale.ROOT);}
    private static Budget.Category toCategorize(Budget b){for(Budget.Category c:b.categories)if(c.name.equalsIgnoreCase(TO_CATEGORIZE)&&!c.payment())return c;Budget.Category c=new Budget.Category(TO_CATEGORIZE);c.group="Everyday";b.categories.add(0,c);return c;}
}
