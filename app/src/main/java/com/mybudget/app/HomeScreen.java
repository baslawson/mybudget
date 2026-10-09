package com.mybudget.app;

import android.app.AlertDialog;
import android.graphics.drawable.GradientDrawable;
import android.widget.*;
import java.time.*;
import java.util.*;

/** Home: To budget, what needs attention, funding progress and the pinned categories. */
final class HomeScreen extends Ui {
    HomeScreen(MainActivity main){super(main);}
    void home(){
        // Home's parts, in the order they were held and dragged into (Movable; Default order puts them back).
        new Movable(this,"order_home","Home",CardOrder.HOME).build(key->{switch(key){
            case "ready":main.budgetScreen.readyCard();main.content.addView(primary("+ Add transaction",()->main.forms.transaction(null)));break;
            case "start":start();break;case "attention":attention();break;case "progress":progress();break;case "ahead":monthAheadCard();break;default:pinned();}});
    }
    private void start(){
        if(main.budget.accounts.isEmpty()){LinearLayout c=card();c.addView(heading("Start with the money you have",21,main.ink));
            c.addView(label("Add your bank, savings or cash account and its current balance. Then assign that money in your plan.",15,main.muted,false));
            if(main.budget.brandNew())c.addView(primary("Set up my budget",this::setup)); // never for a budget in use
            c.addView(button("Add your first account",main.accountsScreen::addAccount));}
    }
    /** Needs attention: each alert opens where it's dealt with. */
    private void attention(){
        main.content.addView(heading("Needs attention",20,main.ink));int alerts=0;
        for(Budget.Category c:main.budget.categories){long cover=main.budget.toCover(c,main.month);if(cover<=0)continue;alerts++;String id=c.id;
            LinearLayout a=alert("!",c.name+" is overspent by "+money(cover),main.red,"Cover it with money from another category or To budget.",()->main.budgetScreen.categoryDetails(main.budget.category(id)));
            a.addView(button("Cover",()->main.budgetScreen.cover(id)));}
        long ready=main.budget.spendable(main.month);
        // Hunt 25 C4: with Hide amounts, one wording for both signs (the To budget card keeps its figure white for the same reason).
        if(ready!=0&&main.hideAmounts){alerts++;
            alert("💰","Check To budget",main.blue,"Amounts are hidden. Open Budget to see what's left to assign.",()->{main.tab="Plan";main.render();});}
        else if(ready>0){alerts++;
            alert("\uD83D\uDCB0",money(ready)+" in To budget is waiting to be assigned",main.blue,"Give it a job in Budget, or use Fund targets.",()->{main.tab="Plan";main.render();});}
        else if(ready<0){alerts++;
            alert("!","To budget is below zero by "+money(-ready),main.red,"More is assigned than you have. Return money from a category in Budget.",()->{main.tab="Plan";main.render();});}
        LocalDate today=LocalDate.now();List<Budget.Scheduled> soon=main.budget.dueWithin(today,7);
        for(Budget.Scheduled s:soon.subList(0,Math.min(5,soon.size()))){alerts++;LocalDate d=LocalDate.parse(s.next);
            boolean planner=main.budget.fromPlanner.contains(s);String id=s.id;
            alert("\uD83D\uDCC5",s.payee+" · "+(planner&&s.amount==0?"no amount yet":money(s.amount)),d.isBefore(today)?main.amber:main.ink,(d.isBefore(today)?"Overdue since "+pretty(s.next):d.equals(today)?"Due today":"Due "+pretty(s.next))+(planner?" · a bill in Planner":" · tap to enter, skip or edit"),planner?()->{main.clearFilters();
                main.tab="Spending";main.render();}:()->main.forms.dueActions(id));}
        if(soon.size()>5)main.content.addView(button("All "+soon.size()+" due this week in Transactions",()->{main.clearFilters();
            main.tab="Spending";main.render();}));
        int review=main.budget.toReview().size();
        if(review>0){alerts++;
            alert("\uD83D\uDCE5",count(review,"imported transaction","imported transactions")+" to review",main.amber,"Check each one's payee and category, then approve it.",main.transactionsScreen::review);}
        // No backup for 14 days (or ever): the budget is only on this phone. "Remind me in a week" snoozes it (device preferences).
        if(main.backupDue()){alerts++;String last=AutoBackup.lastBackup(main);
            LinearLayout a=alert("\uD83D\uDCBE",last==null?"Your budget has never been backed up":"No backup in "+DataSafety.daysSince(last,today)+" days",main.amber,"It's saved only on this phone: uninstalling MyBudget or clearing its storage deletes it. Tap to back it up in Settings.",()->{main.settingsScreen.showBackup=true;
                main.openSettings();});
            a.addView(button("Remind me in a week",()->{main.prefs().edit().putString("backup_reminder_until",DataSafety.snoozeUntil(LocalDate.now())).apply();main.render();}));}
        if(alerts==0){TextView t=(TextView)empty("✅","All set: nothing needs your attention.",null,null).getChildAt(1);
            t.setTextColor(main.green);t.setTypeface(null,android.graphics.Typeface.BOLD);}
    }
    private void progress(){
        long need=0;for(Budget.Category c:main.budget.categories)if(!c.hidden)need+=main.budget.fundNeed(c,main.month);
        LinearLayout progress=card();LinearLayout top=new LinearLayout(main);top.setGravity(android.view.Gravity.CENTER_VERTICAL);
        top.addView(badge("🎯",need>0?main.amber:main.green));TextView h=heading("Your funding progress",19,main.ink);h.setPadding(dp(12),0,0,0);
        top.addView(h,new LinearLayout.LayoutParams(0,-2,1));progress.addView(top);
        progress.addView(label(need>0?money(need)+" still needed this month":"✓ Every target is funded this month",16,need>0?main.amber:main.green,true));
        progress.addView(label("Targets tell you what to fund. They do not create money.",14,main.muted,false));
    }
    /** Priority categories: the ones pinned from their menu in Budget. */
    private void pinned(){
        main.content.addView(heading("Priority categories",20,main.ink));List<Budget.Category> pinned=main.budget.pinned();
        for(Budget.Category c:pinned)main.budgetScreen.categoryCard(c);
        if(pinned.isEmpty()&&!main.budget.categories.isEmpty())empty("📌","Pin up to "+Budget.PINS+" categories to keep an eye on them here: tap a category in Budget, then Pin to Home.","Go to Budget",()->{main.tab="Plan";main.render();});
        else if(pinned.isEmpty())main.content.addView(label("Pin up to "+Budget.PINS+" categories to keep an eye on them here: tap a category in Budget, then Pin to Home.",14,main.muted,false));
    }
    /**
     * Getting a month ahead: how much of next month's targets and upcoming bills already has money assigned
     * (Budget.monthAhead). Nothing when next month asks for nothing.
     */
    private void monthAheadCard(){YearMonth next=main.month.plusMonths(1);long[] a=main.budget.monthAhead(next);if(a[0]<=0)return;
        long funded=a[0]-a[1];boolean done=a[1]==0;int percent=done?100:(int)Math.min(99,funded*100/a[0]);
        String name=next.format(java.time.format.DateTimeFormatter.ofPattern("MMMM"));
        LinearLayout card=card();LinearLayout top=new LinearLayout(main);top.setGravity(android.view.Gravity.CENTER_VERTICAL);
        top.addView(badge("🗓",done?main.green:main.blue));TextView h=heading("Next month: "+name,19,main.ink);h.setPadding(dp(12),0,0,0);
        top.addView(h,new LinearLayout.LayoutParams(0,-2,1));card.addView(top);
        card.addView(label(done?"✓ Fully funded":percent+"% funded",16,done?main.green:main.ink,true));
        progress(card,funded,a[0],done?main.green:main.primary);
        card.addView(greyLine(money(funded)+" of "+money(a[0])+" assigned for its targets and bills",money(funded),amountColour(funded),14));
        card.setOnClickListener(v->monthAheadDetails(next));}
    /** What's assigned in each later month, what next month still needs, and a way to Budget at next month. */
    private void monthAheadDetails(YearMonth next){LinearLayout f=form();String name=next.format(java.time.format.DateTimeFormatter.ofPattern("MMMM"));
        f.addView(label("A month ahead, this month's income pays next month's bills: assign to "+name+" once this month is covered, and then you're spending money that's at least a month old.",13,main.muted,false));
        TreeMap<YearMonth,Long> later=main.budget.assignedAfter(main.month);section(f,"Assigned in later months");
        if(later.isEmpty())f.addView(label("Nothing yet.",14,main.muted,false));
        for(Map.Entry<YearMonth,Long> e:later.entrySet())f.addView(greyLine(e.getKey().format(java.time.format.DateTimeFormatter.ofPattern("MMMM yyyy"))+":  "+money(e.getValue()),money(e.getValue()),amountColour(e.getValue()),14));
        List<Budget.Category> short_=new ArrayList<>();for(Budget.Category c:main.budget.categories)if(!c.hidden&&main.budget.fundNeed(c,next)>0)short_.add(c);
        short_.sort((x,y)->Long.compare(main.budget.fundNeed(y,next),main.budget.fundNeed(x,next)));
        section(f,"Still needed in "+name);if(short_.isEmpty())f.addView(label("✓ Nothing: every target and bill has its money.",14,main.green,false));
        for(Budget.Category c:short_.subList(0,Math.min(10,short_.size()))){long n=main.budget.fundNeed(c,next);f.addView(greyLine(c.name+":  "+money(n),money(n),main.amber,14));}
        if(short_.size()>10)f.addView(label("And "+(short_.size()-10)+" more.",13,main.muted,false));
        ScrollView scroll=new ScrollView(main);scroll.addView(f);
        new AlertDialog.Builder(main).setTitle("Getting a month ahead").setView(scroll).setNegativeButton("Close",null)
            .setPositiveButton("Go to "+name+" in Budget",(d,w)->{main.month=next;main.tab="Plan";main.render();}).show();}
    // First-run setup, offered on the start card of a brand-new budget (Budget.brandNew): the currency, a first account, the
    // starter categories, then what To budget is. Each step saves on Next (a change like any other) or can be skipped; Cancel
    // or Back stops there and keeps what was saved. It removes nothing but starter categories never touched (Budget.removeStarter).
    void setup(){
        List<String> codes=Budget.currencyChoices();String[] names=new String[codes.size()];
        for(int i=0;i<names.length;i++)names[i]=codes.get(i)+" · "+Currency.getInstance(codes.get(i)).getDisplayName(Locale.getDefault());
        String suggested=main.budget.currency.equals(Budget.DEFAULT_CURRENCY)?Budget.suggestedCurrency(Locale.getDefault()):main.budget.currency;
        LinearLayout f=form();f.addView(label("Which currency is your money in? Amounts are shown in it. You can change it later in Settings.",14,main.muted,false));
        Spinner currency=spinner(f,"Currency",names,codes.indexOf(suggested));
        step("Set up: 1 of 3",f,"Next",()->main.budget.currency=codes.get(currency.getSelectedItemPosition()),this::setupAccount);
    }
    private void setupAccount(){
        LinearLayout f=form();f.addView(label("The account you spend from, with what's in it now. You can add more in Accounts.",14,main.muted,false));
        Spinner type=spinner(f,"Type",new String[]{"Cash, checking or savings","Credit card"},0);EditText name=field(f,"Account name",false),balance=field(f,"Current balance ("+code()+")",true);
        onPick(type,()->balance.setHint(type.getSelectedItemPosition()==1?"Amount owed now ("+code()+", 0 if paid off)":"Current balance ("+code()+")"));
        step("Set up: 2 of 3",f,"Next",()->{String n=required(name);boolean card=type.getSelectedItemPosition()==1;
            for(Budget.Account a:main.budget.accounts)if(a.name.equalsIgnoreCase(n))throw new IllegalArgumentException("That account already exists.");
            long amount=Budget.parse(balance.getText().toString().trim().isEmpty()?"0":balance.getText().toString());
            if(amount<0)throw new IllegalArgumentException(card?"Enter what you owe as a positive amount.":"Use a nonnegative cash opening balance.");
            if(card){for(Budget.Category c:main.budget.categories)if(c.name.equalsIgnoreCase(n))throw new IllegalArgumentException("A category already has that name. Choose another name for the card.");
                main.budget.addCard(n,LocalDate.now().toString(),amount);}
            else main.budget.accounts.add(new Budget.Account(n,LocalDate.now().toString(),amount));},this::setupCategories);
    }
    private void setupCategories(){
        LinearLayout f=form();f.addView(label("Starter categories to give your money jobs. Untick the groups you don't want; you can add, rename or hide categories any time in Budget.",14,main.muted,false));
        List<String> groups=Budget.starterGroups();List<CheckBox> boxes=new ArrayList<>();
        for(String g:groups){StringBuilder in=new StringBuilder();boolean have=false;
            for(String[] s:Budget.STARTER)if(s[1].equals(g)){in.append(in.length()==0?"":", ").append(s[0]);for(Budget.Category c:main.budget.categories)have|=c.name.equals(s[0])&&c.group.equals(g);}
            CheckBox box=new CheckBox(main);box.setText(g+": "+in);box.setTextColor(main.ink);box.setTextSize(15);box.setChecked(have);box.setMinHeight(dp(48));f.addView(box);boxes.add(box);}
        f.addView(label("Categories you've already used stay either way.",12,main.muted,false));
        step("Set up: 3 of 3",f,"Next",()->{List<String> keep=new ArrayList<>();for(int i=0;i<groups.size();i++)if(boxes.get(i).isChecked())keep.add(groups.get(i));
            main.budget.removeStarter(keep);main.budget.addStarter(keep);},this::setupDone);
    }
    private void setupDone(){
        new AlertDialog.Builder(main).setTitle("You're set up")
            .setMessage("To budget is money that doesn't have a job yet: "+money(main.budget.spendable(main.month))+" now. In Budget, assign it to your categories until To budget is "+money(0)+", so all of it has a purpose.\n\nNew income goes into To budget, ready to assign.")
            .setNegativeButton("Later",null).setPositiveButton("Assign money now",(d,w)->{main.tab="Plan";main.month=YearMonth.now();main.render();}).show();
    }
    /** A setup step: [next] saves [save] (through commit) and goes on to [then]; Skip goes on without saving; Cancel stops. */
    private void step(String title,LinearLayout f,String next,Runnable save,Runnable then){
        AlertDialog[] shown={null};if(large())f.addView(button("Skip this step",()->{shown[0].dismiss();then.run();}));
        ScrollView scroll=new ScrollView(main);scroll.addView(f);AlertDialog d=new AlertDialog.Builder(main).setTitle(title).setView(scroll)
            .setNegativeButton("Cancel",null).setNeutralButton(large()?null:"Skip",null).setPositiveButton(next,null).create();shown[0]=d;
        main.editors.add(d);d.setOnDismissListener(v->main.editors.remove(d));
        d.setOnShowListener(v->{d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(w->{try{main.commit(save);main.render();}catch(Exception e){toast(e.getMessage());return;}d.dismiss();then.run();});
            if(!large())d.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(w->{d.dismiss();then.run();});});d.show();
    }
    /**
     * A Home alert: a card with a coloured edge, a round [icon] badge, a coloured title and a line of detail; tapping it opens where
     * it's dealt with. Buttons added to it go under the text.
     */
    private LinearLayout alert(String icon,String title,int color,String detail,Runnable open){LinearLayout c=card();
        GradientDrawable edge=new GradientDrawable();edge.setColor(color);
        android.graphics.drawable.LayerDrawable look=new android.graphics.drawable.LayerDrawable(new android.graphics.drawable.Drawable[]{surface(main.surface),edge});
        look.setLayerGravity(1,android.view.Gravity.START|android.view.Gravity.FILL_VERTICAL);look.setLayerWidth(1,dp(5));c.setBackground(look);c.setClipToOutline(true);
        LinearLayout top=new LinearLayout(main);top.setGravity(android.view.Gravity.CENTER_VERTICAL);top.addView(badge(icon,color));
        LinearLayout text=column();text.setPadding(dp(12),0,0,0);text.addView(label(title,16,color,true));text.addView(label(detail,13,main.muted,false));
        top.addView(text,new LinearLayout.LayoutParams(0,-2,1));c.addView(top);c.setOnClickListener(v->open.run());return c;}
}
