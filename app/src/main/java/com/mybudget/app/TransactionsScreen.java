package com.mybudget.app;

import android.app.*;
import android.text.*;
import android.view.View;
import android.widget.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** Transactions (tab key "Spending"): upcoming and Planner bills, filters, the list, imported ones to review. */
final class TransactionsScreen extends Ui {
    TransactionsScreen(MainActivity main){super(main);}
    /** Spending's Upcoming section: scheduled transactions by date; due ones first and marked. */
    private void upcomingList(){
        if(main.budget.scheduled.isEmpty())return;List<Budget.Scheduled> list=new ArrayList<>(main.budget.scheduled);
        list.sort(Comparator.comparing(s->s.next));
        main.content.addView(label("Upcoming",18,main.blue,true));
        for(Budget.Scheduled s:list){if(!main.accountFilter.isEmpty()&&!s.account.equals(main.accountFilter))continue;
            boolean isDue=!LocalDate.parse(s.next).isAfter(LocalDate.now());Budget.Category c=main.budget.category(s.category);
            Budget.Account a=main.budget.account(s.account);
            LinearLayout row=card();row.addView(label(s.payee,17,main.ink,true));
            row.addView(label((s.split()?splitNames(s.splits):s.category.isEmpty()?"To budget":c==null?"":c.name)+" / "+(a==null?"":a.name)+" / "+pretty(s.next)+" · "+main.forms.repeatLabel(s),12,main.muted,false));
            row.addView(label(money(s.amount),17,s.amount>0?main.green:main.ink,true));
            if(isDue)row.addView(label("Due - tap to enter or skip",12,main.amber,true));String id=s.id;
            row.setOnClickListener(v->main.forms.dueActions(id));}
    }
    /** Planner's upcoming bills: what's coming, with the category each is planned from (tap to choose or change it). */
    private void plannerList(){
        if(!main.accountFilter.isEmpty())return;
        if(PlannerBills.stale(main)&&!main.prefs().getString("planner_bills","[]").equals("[]")){main.content.addView(label("Planner's upcoming bills are from "+when(main.prefs().getString("planner_bills_at",""))+", so they aren't planned for. Open Planner to send them again.",12,main.muted,false));return;}
        if(main.budget.fromPlanner.isEmpty())return;
        main.content.addView(label("Coming up in Planner",18,main.blue,true));
        String at=main.prefs().getString("planner_bills_at",null);
        main.content.addView(label("Sent by Planner"+(at==null?"":" on "+when(at))+". They're added here when you mark them paid in Planner.",12,main.muted,false));
        for(Budget.Scheduled s:main.budget.fromPlanner){Budget.Category c=main.budget.category(s.category);LinearLayout row=card();
            row.addView(label(s.payee,17,main.ink,true));
            boolean overdue=LocalDate.parse(s.next).isBefore(LocalDate.now());
            row.addView(label((overdue?"Overdue since ":"Due ")+pretty(s.next)+" · "+(c==null?"no category yet":c.name),12,overdue?main.amber:main.muted,false));
            row.addView(label(s.amount==0?"No amount in Planner":money(s.amount),17,main.ink,true));
            if(c==null)row.addView(label("Choose a category to plan for it",12,main.amber,true));
            String key=s.billKey,payee=s.payee;row.setOnClickListener(v->chooseBillCategory(key,payee));}
    }
    private void chooseBillCategory(String billKey,String payee){
        List<Budget.Category> cats=main.visibleCategories(null);if(cats.isEmpty()){toast("Add a category first.");return;}
        new AlertDialog.Builder(main).setTitle("Plan "+payee+" from")
            .setItems(cats.stream().map(c->c.name+" ("+money(main.budget.available(c,main.month))+")").toArray(String[]::new),(d,n)->{String id=cats.get(n).id;
            if(main.change(()->main.budget.billCategories.put(billKey,id)))toast("Planned from "+cats.get(n).name+". MyBudget suggests it when the bill is paid, too.");}).show();
    }
    void spending(){
        main.content.addView(button("+ Add transaction",()->main.forms.transaction(null)));
        int review=main.budget.toReview().size();if(review>0){LinearLayout c=card();
            c.addView(label(count(review,"imported transaction","imported transactions")+" to review",17,main.amber,true));
            c.addView(label("Check each one's payee and category, then approve it.",13,main.muted,false));
            c.addView(button("Review "+review+" imported",this::review));}
        upcomingList();plannerList();
        LinearLayout tools=new LinearLayout(main);
        Button filters=button(filtered()?"Filters (on)":"Filters",this::filters),payees=button("Payees",main.settingsScreen::payees);
        tools.addView(filters,new LinearLayout.LayoutParams(0,-2,1));LinearLayout.LayoutParams pp=new LinearLayout.LayoutParams(0,-2,1);
        pp.setMargins(dp(8),0,0,0);tools.addView(payees,pp);main.content.addView(tools);
        EditText query=field(main.content,"Search payee, category or memo",false);query.setText(main.search);
        TextView summary=label("",13,main.blue,true);main.content.addView(summary);
        Button clear=button("Clear filters",()->{main.clearFilters();main.render();});main.content.addView(clear);
        LinearLayout list=column();main.content.addView(list);
        Runnable refresh=()->{summary.setText(filterSummary());
            summary.setVisibility(filtered()||!main.search.trim().isEmpty()?View.VISIBLE:View.GONE);clear.setVisibility(summary.getVisibility());
            fillEntries(list);};refresh.run();
        query.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int f){}public void onTextChanged(CharSequence s,int a,int b,int c){main.search=s.toString();
                refresh.run();}public void afterTextChanged(Editable e){}});
    }
    // Transactions filters: they combine (Budget.matches). Without a date range the list shows the month on screen.
    private boolean filtered(){return !main.accountFilter.isEmpty()||!main.categoryFilter.isEmpty()||main.flagFilter>=0||main.clearedFilter>=0||!main.fromFilter.isEmpty()||!main.toFilter.isEmpty();}
    private Budget.Filter currentFilter(){
        Budget.Filter f=new Budget.Filter();f.text=main.search;f.account=main.budget.account(main.accountFilter)==null?"":main.accountFilter;
        f.category=main.budget.category(main.categoryFilter)==null?"":main.categoryFilter;f.flag=main.flagFilter;f.cleared=main.clearedFilter;
        f.from=main.fromFilter;f.to=main.toFilter;
        if(f.from.isEmpty()&&f.to.isEmpty()){f.from=main.month.atDay(1).toString();f.to=main.month.atEndOfMonth().toString();}return f;
    }
    /** The current filter as one line: "Showing: Everyday · Groceries · Green flag · Uncleared · 1 Sep 2026 to 30 Sep 2026 · "coles"". */
    private String filterSummary(){
        List<String> parts=new ArrayList<>();Budget.Account a=main.budget.account(main.accountFilter);
        Budget.Category c=main.budget.category(main.categoryFilter);if(a!=null)parts.add(a.name);if(c!=null)parts.add(c.name);
        if(main.flagFilter==0)parts.add("No flag");else if(main.flagFilter>0)parts.add(main.budget.flagLabel(main.flagFilter)+" flag");
        if(main.clearedFilter>=0)parts.add(main.clearedFilter==1?"Cleared":"Uncleared");
        if(!main.fromFilter.isEmpty()&&!main.toFilter.isEmpty())parts.add(pretty(main.fromFilter)+" to "+pretty(main.toFilter));else if(!main.fromFilter.isEmpty())parts.add("From "+pretty(main.fromFilter));else if(!main.toFilter.isEmpty())parts.add("Up to "+pretty(main.toFilter));else parts.add(main.month.format(DateTimeFormatter.ofPattern("MMMM yyyy")));
        if(!main.search.trim().isEmpty())parts.add("\""+main.search.trim()+"\"");return "Showing: "+String.join(" · ",parts);
    }
    private void filters(){
        List<Budget.Account> accs=new ArrayList<>(main.budget.accounts);List<Budget.Category> cats=new ArrayList<>(main.budget.categories);
        String[] accNames=new String[accs.size()+1],catNames=new String[cats.size()+1],flags=new String[Budget.FLAGS.length+1];
        accNames[0]="Any account";catNames[0]="Any category";flags[0]="Any flag";
        for(int i=0;i<accs.size();i++)accNames[i+1]=accs.get(i).name+(accs.get(i).closed?" (closed)":"");
        for(int i=0;i<cats.size();i++)catNames[i+1]=cats.get(i).name;String[] choices=flagChoices();
        for(int i=0;i<choices.length;i++)flags[i+1]=choices[i];
        LinearLayout f=form();
        Spinner account=spinner(f,"Account",accNames,accs.indexOf(main.budget.account(main.accountFilter))+1),category=spinner(f,"Category",catNames,cats.indexOf(main.budget.category(main.categoryFilter))+1),flag=spinner(f,"Flag",flags,main.flagFilter+1),cleared=spinner(f,"Cleared",new String[]{"Cleared or not","Cleared","Uncleared"},main.clearedFilter<0?0:main.clearedFilter==1?1:2);
        f.addView(label("Dates (without them, the month on screen)",12,main.muted,true));CheckBox useFrom=new CheckBox(main);useFrom.setText("From");
        useFrom.setChecked(!main.fromFilter.isEmpty());f.addView(useFrom);
        EditText from=dateField(f,main.fromFilter.isEmpty()?main.month.atDay(1).toString():main.fromFilter);
        CheckBox useTo=new CheckBox(main);useTo.setText("To");useTo.setChecked(!main.toFilter.isEmpty());f.addView(useTo);
        EditText to=dateField(f,main.toFilter.isEmpty()?LocalDate.now().toString():main.toFilter);
        ScrollView scroll=new ScrollView(main);scroll.addView(f);AlertDialog d=new AlertDialog.Builder(main).setTitle("Filter transactions")
            .setView(scroll).setNegativeButton("Cancel",null).setNeutralButton("Clear all",(x,w)->{main.clearFilters();main.render();})
            .setPositiveButton("Apply",null).create();
        d.setOnShowListener(v->d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(w->{
            String fromDay=useFrom.isChecked()?(String)from.getTag():"",toDay=useTo.isChecked()?(String)to.getTag():"";
            if(!fromDay.isEmpty()&&!toDay.isEmpty()&&fromDay.compareTo(toDay)>0){toast("The From date is after the To date.");return;}
            int a=account.getSelectedItemPosition(),c=category.getSelectedItemPosition(),cl=cleared.getSelectedItemPosition();
            main.accountFilter=a==0?"":accs.get(a-1).id;main.categoryFilter=c==0?"":cats.get(c-1).id;main.flagFilter=flag.getSelectedItemPosition()-1;
            main.clearedFilter=cl==0?-1:cl==1?1:0;main.fromFilter=fromDay;main.toFilter=toDay;d.dismiss();main.render();}));d.show();
    }
    /** Imported transactions waiting for review: approve each (or edit it, which approves it too), or all at once. */
    void review(){
        List<Budget.Entry> list=main.budget.toReview();if(list.isEmpty()){main.render();return;}LinearLayout f=form();
        f.addView(label("Imported from a bank statement. Check the payee and category; editing one approves it.",13,main.muted,false));AlertDialog[] shown={null};
        for(Budget.Entry e:list.subList(0,Math.min(100,list.size()))){LinearLayout row=column();row.setPadding(0,dp(6),0,dp(6));
            row.addView(label(e.payee+"  "+money(e.amount),15,e.amount>0?main.green:main.ink,true));Budget.Account a=main.budget.account(e.account);
            row.addView(label(categoryName(e)+" / "+(a==null?"":a.name)+" / "+pretty(e.date),12,main.muted,false));
            LinearLayout buttons=new LinearLayout(main);String id=e.id;
            Button approve=button("Approve",()->{if(main.change(()->main.budget.approve(entryById(id)))){shown[0].dismiss();
                    review();}}),edit=button("Edit",()->{shown[0].dismiss();
                Budget.Entry t=main.budget.entries.stream().filter(x->x.id.equals(id)).findFirst().orElse(null);
                if(t!=null)main.forms.transaction(t);});
            buttons.addView(approve,new LinearLayout.LayoutParams(0,-2,1));LinearLayout.LayoutParams ep=new LinearLayout.LayoutParams(0,-2,1);
            ep.setMargins(dp(8),0,0,0);buttons.addView(edit,ep);row.addView(buttons);f.addView(row);}
        if(list.size()>100)f.addView(label("And "+(list.size()-100)+" more: Approve all includes them.",12,main.muted,false));
        ScrollView scroll=new ScrollView(main);scroll.addView(f);
        shown[0]=new AlertDialog.Builder(main).setTitle(count(list.size(),"transaction","transactions")+" to review").setView(scroll)
            .setNegativeButton("Close",null).setPositiveButton("Approve all",(x,w)->{int[] n={0};
            if(main.change(()->n[0]=main.budget.approveAll()))toast(count(n[0],"transaction","transactions")+" approved.");}).show();
    }
    private Budget.Entry entryById(String id){for(Budget.Entry e:main.budget.entries)if(e.id.equals(id))return e;
        throw new IllegalArgumentException("That transaction no longer exists.");}
    /** "Split: Food, Household" (a transaction's or an upcoming one's parts). */
    private String splitNames(List<Budget.Split> parts){StringBuilder s=new StringBuilder("Split:");
        for(Budget.Split p:parts){Budget.Category c=main.budget.category(p.category);s.append(" ").append(c==null?"To budget":c.name).append(",");}return s.substring(0,s.length()-1);}
    // Money not given to a category goes into To budget (income and reconcile adjustments).
    private String categoryName(Budget.Entry e){if(e.split())return splitNames(e.splits);
        Budget.Category c=main.budget.category(e.category);
        if(e.transfer())return c!=null?"Transfer · "+c.name:main.budget.crossing(e)?"Transfer · To budget":"Transfer"; // in or out of the budget, to or from a tracking account
        Budget.Account a=main.budget.account(e.account);return a!=null&&a.tracking()?"Tracking account":c==null?"To budget":c.name;}
    private void fillEntries(LinearLayout list){
        list.removeAllViews();int n=0;
        Budget.Account shown=main.accountFilter.isEmpty()?null:main.budget.account(main.accountFilter);
        Map<String,Long> running=shown==null?null:main.budget.runningBalances(shown); // one account's list: its balance after each transaction
        for(Budget.Entry e:main.budget.filter(currentFilter())){n++;LinearLayout row=column();row.setPadding(dp(14),dp(10),dp(14),dp(10));
            row.setBackground(bg(main.surface));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);
            p.setMargins(0,dp(5),0,dp(5));list.addView(row,p);
            TextView payee=label(e.payee,17,main.ink,true);
            if(e.flag>0&&e.flag<FLAG_COLORS.length){SpannableString s=new SpannableString("● "+e.payee);
                s.setSpan(new android.text.style.ForegroundColorSpan(FLAG_COLORS[e.flag]),0,1,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);payee.setText(s);
                payee.setContentDescription(e.payee+", "+main.budget.flagLabel(e.flag)+" flag");}row.addView(payee);
            if(!e.approved)row.addView(label("To review · imported",12,main.amber,true));
            row.addView(label(categoryName(e)+" / "+main.budget.account(e.account).name+" / "+pretty(e.date),12,main.muted,false));
            row.addView(label(money(e.amount)+(e.cleared?"  Cleared":"  Uncleared"),17,e.amount>0?main.green:main.ink,true));
            if(running!=null&&running.containsKey(e.id))row.addView(label("Balance "+money(running.get(e.id)),12,main.muted,false));
            if(!e.memo.isEmpty())row.addView(label(e.memo,12,main.muted,false));
            if(!e.photo.isEmpty())row.addView(label("Photo attached",12,main.blue,false));row.setOnClickListener(v->main.forms.transaction(e));}
        if(n==0)list.addView(label(main.fromFilter.isEmpty()&&main.toFilter.isEmpty()?"No matching transactions this month.":"No matching transactions in these dates.",15,main.muted,false));
    }
}
