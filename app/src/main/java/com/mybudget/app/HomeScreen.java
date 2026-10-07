package com.mybudget.app;

import android.widget.*;
import java.time.*;
import java.util.*;

/** Home: To budget, what needs attention, funding progress and the pinned categories. */
final class HomeScreen extends Ui {
    HomeScreen(MainActivity main){super(main);}
    void home(){
        main.budgetScreen.readyCard();main.content.addView(button("+ Add transaction",()->main.forms.transaction(null)));
        if(main.budget.accounts.isEmpty()){LinearLayout c=card();c.addView(label("Start with the money you have",21,main.ink,true));
            c.addView(label("Add your bank, savings or cash account and its current balance. Then assign that money in your plan.",15,main.muted,false));
            c.addView(button("Add your first account",main.accountsScreen::addAccount));}
        // Needs attention: each alert opens where it's dealt with.
        main.content.addView(label("Needs attention",20,main.ink,true));int alerts=0;
        for(Budget.Category c:main.budget.categories){long cover=main.budget.toCover(c,main.month);if(cover<=0)continue;alerts++;String id=c.id;
            LinearLayout a=alert(c.name+" is overspent by "+money(cover),main.red,"Cover it with money from another category or To budget.",()->main.budgetScreen.categoryDetails(main.budget.category(id)));
            a.addView(button("Cover",()->main.budgetScreen.cover(id)));}
        long ready=main.budget.spendable(main.month);
        if(ready>0){alerts++;
            alert(money(ready)+" in To budget is waiting to be assigned",main.blue,"Give it a job in Budget, or use Fund targets.",()->{main.tab="Plan";main.render();});}
        else if(ready<0){alerts++;
            alert("To budget is below $0 by "+money(-ready),main.red,"More is assigned than you have. Return money from a category in Budget.",()->{main.tab="Plan";main.render();});}
        LocalDate today=LocalDate.now();List<Budget.Scheduled> soon=main.budget.dueWithin(today,7);
        for(Budget.Scheduled s:soon.subList(0,Math.min(5,soon.size()))){alerts++;LocalDate d=LocalDate.parse(s.next);
            boolean planner=main.budget.fromPlanner.contains(s);String id=s.id;
            alert(s.payee+" · "+(planner&&s.amount==0?"no amount yet":money(s.amount)),d.isBefore(today)?main.amber:main.ink,(d.isBefore(today)?"Overdue since "+pretty(s.next):d.equals(today)?"Due today":"Due "+pretty(s.next))+(planner?" · a bill in Planner":" · tap to enter, skip or edit"),planner?()->{main.clearFilters();
                main.tab="Spending";main.render();}:()->main.forms.dueActions(id));}
        if(soon.size()>5)main.content.addView(button("All "+soon.size()+" due this week in Transactions",()->{main.clearFilters();
            main.tab="Spending";main.render();}));
        int review=main.budget.toReview().size();
        if(review>0){alerts++;
            alert(count(review,"imported transaction","imported transactions")+" to review",main.amber,"Check each one's payee and category, then approve it.",main.transactionsScreen::review);}
        // No backup for 14 days (or ever): the budget is only on this phone. "Remind me in a week" snoozes it (device preferences).
        if(main.backupDue()){alerts++;String last=AutoBackup.lastBackup(main);
            LinearLayout a=alert(last==null?"Your budget has never been backed up":"No backup in "+DataSafety.daysSince(last,today)+" days",main.amber,"It's saved only on this phone: uninstalling MyBudget or clearing its storage deletes it. Tap to back it up in Settings.",()->{main.settingsScreen.showBackup=true;
                main.openSettings();});
            a.addView(button("Remind me in a week",()->{main.prefs().edit().putString("backup_reminder_until",DataSafety.snoozeUntil(LocalDate.now())).apply();main.render();}));}
        if(alerts==0)main.content.addView(label("All set: nothing needs your attention.",15,main.green,true));
        long need=0;for(Budget.Category c:main.budget.categories)if(!c.hidden)need+=main.budget.fundNeed(c,main.month);
        LinearLayout progress=card();progress.addView(label("Your funding progress",19,main.ink,true));
        progress.addView(label(money(need)+" still needed this month",16,need>0?main.amber:main.green,true));
        progress.addView(label("Targets tell you what to fund. They do not create money.",14,main.muted,false));
        // Priority categories: the ones pinned from their menu in Budget.
        main.content.addView(label("Priority categories",20,main.ink,true));List<Budget.Category> pinned=main.budget.pinned();
        for(Budget.Category c:pinned)main.budgetScreen.categoryCard(c);
        if(pinned.isEmpty())main.content.addView(label("Pin up to "+Budget.PINS+" categories to keep an eye on them here: tap a category in Budget, then Pin to Home.",14,main.muted,false));
    }
    /** A Home alert: a card with a coloured title and a line of detail; tapping it opens where it's dealt with. */
    private LinearLayout alert(String title,int color,String detail,Runnable open){LinearLayout c=card();c.addView(label(title,16,color,true));
        c.addView(label(detail,13,main.muted,false));c.setOnClickListener(v->open.run());return c;}
}
