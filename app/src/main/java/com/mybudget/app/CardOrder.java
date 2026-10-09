package com.mybudget.app;

import java.util.*;

/**
 * The order of a screen's movable sections (Movable), kept free of Android so tests/BudgetTest.java can check it. Saved on this
 * device as their keys joined by commas (not in the backup); keys no longer there are dropped and new ones go at the end.
 */
public final class CardOrder {
    private CardOrder(){}
    /** Reports, Home: every section in the default order. Accounts use the accounts' ids, in the budget's order. */
    public static final List<String> REPORTS=Collections.unmodifiableList(Arrays.asList("cash","breakdown","trends","flow","table","year","worth","age")),
        HOME=Collections.unmodifiableList(Arrays.asList("ready","start","attention","progress","ahead","pinned"));
    /** [all] in [saved] order (null or empty: the default order). */
    public static List<String> order(String saved,List<String> all){
        LinkedHashSet<String> keys=new LinkedHashSet<>();
        if(saved!=null)for(String k:saved.split(","))if(all.contains(k.trim()))keys.add(k.trim());
        keys.addAll(all);return new ArrayList<>(keys);}
    /** [full] with the keys in [shown] put in that order, in the places those keys held; the rest (not on screen now) stay where they were. */
    public static List<String> moved(List<String> full,List<String> shown){
        List<String> out=new ArrayList<>(full);Iterator<String> next=shown.iterator();
        for(int i=0;i<out.size();i++)if(shown.contains(out.get(i)))out.set(i,next.next());return out;}
    /** [shown] with [key] taken out and put back at [to] (clamped: 0 the top, a large number the bottom). */
    public static List<String> place(List<String> shown,String key,int to){
        List<String> out=new ArrayList<>(shown);if(!out.remove(key))return out;out.add(Math.max(0,Math.min(to,out.size())),key);return out;}
    /** [order] as saved, or null when it is the default order (nothing to keep). */
    public static String save(List<String> order,List<String> all){return order.equals(all)?null:String.join(",",order);}
}
