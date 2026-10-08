package com.mybudget.app;

import android.app.*;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
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
        if(!main.accountFilter.isEmpty())list.removeIf(s->!s.account.equals(main.accountFilter));if(list.isEmpty())return;
        sectionHeading("📅","Coming up",count(list.size(),"upcoming","upcoming"));
        for(Budget.Scheduled s:list){
            boolean isDue=!LocalDate.parse(s.next).isAfter(LocalDate.now());Budget.Category c=main.budget.category(s.category);
            Budget.Account a=main.budget.account(s.account);String id=s.id;
            listRow(s.payee,(s.split()?splitNames(s.splits):s.category.isEmpty()?"To budget":c==null?"":c.name)+" · "+(a==null?"":a.name)+" · "+pretty(s.next)+" · "+main.forms.repeatLabel(s),
                money(s.amount),amountColour(s.amount),isDue?"Due - tap to enter or skip":null,main.amber,()->main.forms.dueActions(id));}
    }
    /** A section of the screen: an icon, its name (a heading for TalkBack) and a quiet count on the right. */
    private void sectionHeading(String icon,String title,String detail){LinearLayout row=new LinearLayout(main);row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(2),dp(18),dp(4),dp(4));TextView i=label(icon,18,main.ink,false);i.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        i.setPadding(0,0,dp(8),0);row.addView(i);TextView t=heading(title,19,main.ink);row.addView(t,new LinearLayout.LayoutParams(0,-2,1));
        if(detail!=null)row.addView(label(detail,13,main.muted,true));main.content.addView(row,new LinearLayout.LayoutParams(-1,-2));}
    /** A row in Coming up or Bills from Planner: payee badge, payee and details, the amount on the right; [note] under it in [noteColour]. */
    private void listRow(String payee,String detail,String amount,int amountColour,String note,int noteColour,Runnable open){
        LinearLayout row=new LinearLayout(main);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(12),dp(10),dp(14),dp(10));
        row.setBackground(surface(main.surface));pressable(row);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,dp(4),0,dp(4));
        main.content.addView(row,p);row.addView(badge(initial(payee),FLAG_COLORS[1+Math.floorMod(payee.toLowerCase(Locale.ROOT).hashCode(),FLAG_COLORS.length-1)]));
        LinearLayout middle=column();middle.setPadding(dp(12),0,dp(8),0);row.addView(middle,new LinearLayout.LayoutParams(0,-2,1));
        TextView name=label(payee,16,main.ink,true);name.setPadding(0,0,0,0);middle.addView(name);
        TextView d=label(detail,12,main.muted,false);d.setPadding(0,dp(2),0,0);middle.addView(d);
        if(note!=null)middle.addView(label(note,12,noteColour,true));
        TextView a=label(amount,16,amountColour,true);a.setPadding(0,0,0,0);row.addView(a);row.setOnClickListener(v->open.run());}
    /** Planner's upcoming bills: what's coming, with the category each is planned from (tap to choose or change it). */
    private void plannerList(){
        if(!main.accountFilter.isEmpty())return;
        if(PlannerBills.stale(main)&&!main.prefs().getString("planner_bills","[]").equals("[]")){main.content.addView(label("Planner's upcoming bills are from "+when(main.prefs().getString("planner_bills_at",""))+", so they aren't planned for. Open Planner to send them again.",12,main.muted,false));return;}
        if(main.budget.fromPlanner.isEmpty())return;
        sectionHeading("🧾","Bills from Planner",count(main.budget.fromPlanner.size(),"bill","bills"));
        String at=main.prefs().getString("planner_bills_at",null);
        main.content.addView(label("Sent by Planner"+(at==null?"":" on "+when(at))+". They're added here when you mark them paid in Planner.",12,main.muted,false));
        for(Budget.Scheduled s:main.budget.fromPlanner){Budget.Category c=main.budget.category(s.category);
            boolean overdue=LocalDate.parse(s.next).isBefore(LocalDate.now());String key=s.billKey,payee=s.payee;
            listRow(s.payee,(overdue?"Overdue since ":"Due ")+pretty(s.next)+" · "+(c==null?"no category yet":c.name),s.amount==0?"No amount":money(s.amount),amountColour(s.amount),
                c==null?"Choose a category to plan for it":null,main.amber,()->chooseBillCategory(key,payee));}
    }
    private void chooseBillCategory(String billKey,String payee){
        List<Budget.Category> cats=main.visibleCategories(null);if(cats.isEmpty()){toast("Add a category first.");return;}
        new AlertDialog.Builder(main).setTitle("Plan "+payee+" from")
            .setItems(cats.stream().map(c->c.name+" ("+money(main.budget.available(c,main.month))+")").toArray(String[]::new),(d,n)->{String id=cats.get(n).id;
            if(main.change(()->main.budget.billCategories.put(billKey,id)))toast("Planned from "+cats.get(n).name+". MyBudget suggests it when the bill is paid, too.");}).show();
    }
    /** The month on screen at a glance: money in (green), money out (red) and what's left over. */
    private void monthSummary(){long in=main.budget.income(main.month),out=main.budget.spending(main.month),net=in-out;
        LinearLayout c=card();c.setOrientation(LinearLayout.HORIZONTAL);c.setPadding(dp(8),dp(12),dp(8),dp(12));
        // Hunt 23 M2: signed and coloured by the value: refunds above spending make money out a plus (green), and an uncategorised
        // outflow above income makes money in a minus (red).
        String[] names={"Money in","Money out",net<0?"Short by":"Left over"};long[] values={Math.abs(in),Math.abs(out),Math.abs(net)};
        int[] colours={amountColour(in),outColour(out),amountColour(net)};String[] signs={in<0?"−":"+",out<0?"+":"−",net<0?"−":""};
        for(int i=0;i<3;i++){LinearLayout col=column();col.setGravity(Gravity.CENTER_HORIZONTAL);
            TextView n=label(names[i],11,main.muted,true);n.setGravity(Gravity.CENTER);col.addView(n);
            TextView v=label((values[i]==0?"":signs[i])+money(values[i]),16,values[i]==0?main.muted:colours[i],true);v.setGravity(Gravity.CENTER);v.setMaxLines(1);
            v.setAutoSizeTextTypeUniformWithConfiguration(10,16,1,android.util.TypedValue.COMPLEX_UNIT_SP);col.addView(v,new LinearLayout.LayoutParams(-1,dp(30)));
            col.setContentDescription(names[i]+" this month: "+(values[i]==0?"":signs[i])+money(values[i]));col.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
            for(int k=0;k<col.getChildCount();k++)col.getChildAt(k).setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            c.addView(col,new LinearLayout.LayoutParams(0,-2,1));}
        c.setForeground(null);} // not tappable
    /** Add transaction: a round + over the bottom right of the screen, always in reach (the list leaves room under its last row). */
    private void addButton(){Button add=button("+",()->main.forms.transaction(null));add.setTextSize(30);add.setTextColor(Color.WHITE);add.setPadding(0,0,0,dp(3));
        GradientDrawable round=new GradientDrawable();round.setShape(GradientDrawable.OVAL);round.setColor(main.primary);add.setBackground(round);
        GradientDrawable mask=new GradientDrawable();mask.setShape(GradientDrawable.OVAL);mask.setColor(Color.WHITE);
        add.setForeground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(tint(Color.WHITE,70)),null,mask));
        add.setElevation(dp(6));add.setContentDescription("Add transaction");
        // Hunt 23 M5: TalkBack reaches it before the list (it is drawn over it, so it would come last).
        add.setId(View.generateViewId());main.body.getChildAt(0).setAccessibilityTraversalAfter(add.getId());
        android.widget.FrameLayout.LayoutParams p=new android.widget.FrameLayout.LayoutParams(dp(64),dp(64),Gravity.BOTTOM|Gravity.END);p.setMargins(0,0,dp(4),dp(12));
        main.body.addView(add,p);main.content.setPadding(0,0,0,dp(88));
        if(motion()&&main.shownTab!=null&&!main.shownTab.equals("Spending")){add.setScaleX(0f);add.setScaleY(0f); // pops in with the screen
            add.animate().scaleX(1f).scaleY(1f).setStartDelay(120).setDuration(260).setInterpolator(new android.view.animation.OvershootInterpolator(2f)).start();}}
    void spending(){
        monthSummary();addButton();
        int review=main.budget.toReview().size();if(review>0){LinearLayout c=card();
            c.addView(label(count(review,"imported transaction","imported transactions")+" to review",17,main.amber,true));
            c.addView(label("Check each one's payee and category, then approve it.",13,main.muted,false));
            c.addView(button("Review "+review+" imported",this::review));}
        upcomingList();plannerList();
        sectionHeading("💳","Transactions",null);
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
        f.addView(label("Dates (without them, the month on screen)",12,main.muted,true));CheckBox useFrom=new CheckBox(main);useFrom.setText("From");useFrom.setMinHeight(dp(48));
        useFrom.setChecked(!main.fromFilter.isEmpty());f.addView(useFrom);
        EditText from=dateField(f,main.fromFilter.isEmpty()?main.month.atDay(1).toString():main.fromFilter);
        CheckBox useTo=new CheckBox(main);useTo.setText("To");useTo.setMinHeight(dp(48));useTo.setChecked(!main.toFilter.isEmpty());f.addView(useTo);
        EditText to=dateField(f,main.toFilter.isEmpty()?LocalDate.now().toString():main.toFilter);
        AlertDialog[] shown={null};if(large())f.addView(button("Clear all filters",()->{shown[0].dismiss();main.clearFilters();main.render();}));
        ScrollView scroll=new ScrollView(main);scroll.addView(f);AlertDialog d=new AlertDialog.Builder(main).setTitle("Filter transactions")
            .setView(scroll).setNegativeButton("Cancel",null).setNeutralButton(large()?null:"Clear all",(x,w)->{main.clearFilters();main.render();})
            .setPositiveButton("Apply",null).create();shown[0]=d;
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
            TextView what=label("",15,main.ink,true);what.setText(tint(e.payee+"  "+money(e.amount),money(e.amount),amountColour(e.amount)));row.addView(what);Budget.Account a=main.budget.account(e.account);
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
        // Newest first, under a heading for each day ("Today", "Yesterday", "Monday 6 Oct"). Each row: the payee's initial in a
        // coloured circle, payee and details in the middle, the amount and Cleared/Uncleared on the right.
        String day=null;LocalDate today=LocalDate.now();
        for(Budget.Entry e:main.budget.filter(currentFilter())){n++;
            if(!e.date.equals(day)){day=e.date;TextView h=heading(dayHeading(LocalDate.parse(e.date),today),13,main.muted);h.setPadding(dp(4),dp(n==1?4:12),0,dp(2));list.addView(h);}
            LinearLayout row=new LinearLayout(main);row.setGravity(android.view.Gravity.CENTER_VERTICAL);row.setPadding(dp(12),dp(10),dp(14),dp(10));
            row.setBackground(surface(main.surface));pressable(row);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);
            p.setMargins(0,dp(4),0,dp(4));list.addView(row,p);
            row.addView(badge(initial(e.payee),FLAG_COLORS[1+Math.floorMod(e.payee.toLowerCase(Locale.ROOT).hashCode(),FLAG_COLORS.length-1)]));
            LinearLayout middle=column();middle.setPadding(dp(12),0,dp(8),0);row.addView(middle,new LinearLayout.LayoutParams(0,-2,1));
            TextView payee=label(e.payee,16,main.ink,true);payee.setPadding(0,0,0,0);
            if(e.flag>0&&e.flag<FLAG_COLORS.length){SpannableString s=new SpannableString("● "+e.payee);
                s.setSpan(new android.text.style.ForegroundColorSpan(FLAG_COLORS[e.flag]),0,1,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);payee.setText(s);
                payee.setContentDescription(e.payee+", "+main.budget.flagLabel(e.flag)+" flag");}middle.addView(payee);
            if(!e.approved)middle.addView(label("To review · imported",12,main.amber,true));
            TextView where=label(categoryName(e)+" · "+main.budget.account(e.account).name,12,main.muted,false);where.setPadding(0,dp(2),0,0);middle.addView(where);
            if(running!=null&&running.containsKey(e.id)){long bal=running.get(e.id);middle.addView(greyLine("Balance "+money(bal),money(bal),amountColour(bal),12));}
            if(!e.memo.isEmpty())middle.addView(label(e.memo,12,main.muted,false));
            if(!e.photo.isEmpty())middle.addView(label("📎 Photo attached",12,main.blue,false));
            LinearLayout right=column();right.setGravity(android.view.Gravity.END);
            TextView amount=label(money(e.amount),16,amountColour(e.amount),true);amount.setGravity(android.view.Gravity.END);amount.setPadding(0,0,0,0);right.addView(amount);
            TextView cleared=label(e.cleared?"Cleared":"Uncleared",11,e.cleared?main.green:main.muted,false);cleared.setGravity(android.view.Gravity.END);cleared.setPadding(0,dp(2),0,0);right.addView(cleared);
            row.addView(right,new LinearLayout.LayoutParams(-2,-2));row.setOnClickListener(v->main.forms.transaction(e));}
        if(n==0){boolean none=main.budget.entries.isEmpty();list.addView(emptyRow(none?"🧾":"🔍",none?"No transactions yet. Add what you spend or earn and it shows here.":main.fromFilter.isEmpty()&&main.toFilter.isEmpty()?"No matching transactions this month.":"No matching transactions in these dates."));}
    }
    /** "Today", "Yesterday", "Monday 6 Oct" (with the year when it isn't this year). */
    static String dayHeading(LocalDate d,LocalDate today){if(d.equals(today))return "Today";if(d.equals(today.minusDays(1)))return "Yesterday";
        return d.format(DateTimeFormatter.ofPattern(d.getYear()==today.getYear()?"EEEE d MMM":"EEEE d MMM yyyy",Locale.forLanguageTag("en-AU")));}
    /** A payee's badge letter: its first letter or digit ("Transfer to Savings" → T), or "•" when it has none. */
    static String initial(String payee){for(int i=0;i<payee.length();){int c=payee.codePointAt(i);if(Character.isLetterOrDigit(c))return new String(Character.toChars(Character.toUpperCase(c)));i+=Character.charCount(c);}return "•";}
    /** The empty list: a symbol and a line, in the list (not a card added to the screen). */
    private View emptyRow(String symbol,String text){LinearLayout c=column();c.setGravity(android.view.Gravity.CENTER_HORIZONTAL);c.setPadding(dp(20),dp(24),dp(20),dp(24));
        TextView s=label(symbol,40,main.ink,false);s.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);c.addView(s);
        TextView t=label(text,15,main.muted,false);t.setGravity(android.view.Gravity.CENTER);c.addView(t);return c;
    }
}
