package com.mybudget.app;

import android.app.*;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.text.*;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** Reports (tab key "Reflect"): cash flow, spending breakdown and trends, income and expenses, net worth, money age. */
final class ReportsScreen extends Ui {
    ReportsScreen(MainActivity main){super(main);}
    void reflect(){
        LinearLayout totals=card();totals.addView(label("This month's cash flow",20,main.ink,true));
        totals.addView(label("Income "+money(main.budget.income(main.month)),21,main.green,true));
        totals.addView(label("Spending "+money(main.budget.spending(main.month)),21,main.ink,true));
        totals.addView(label("Difference "+money(main.budget.income(main.month)-main.budget.spending(main.month)),17,main.blue,true));
        breakdownCard();trendsCard(); // card payments, transfers and tracking accounts aren't spending
        // Income vs spending, six months to this one: one axis from zero; the list below is the table view.
        LinearLayout flow=card();flow.addView(label("Income and spending",20,main.ink,true));
        int income=main.darkTheme?Color.parseColor("#3987E5"):Color.parseColor("#2A78D6"),spend=main.darkTheme?Color.parseColor("#D95926"):Color.parseColor("#EB6834");
        LinearLayout legend=new LinearLayout(main);legend.setGravity(Gravity.CENTER_VERTICAL);
        for(int k=0;k<2;k++){View swatch=new View(main);GradientDrawable s=new GradientDrawable();s.setColor(k==0?income:spend);
            s.setCornerRadius(dp(2));swatch.setBackground(s);LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(dp(10),dp(10));
            sp.setMargins(k==0?0:dp(14),0,dp(6),0);legend.addView(swatch,sp);
            legend.addView(label(k==0?"Income":"Spending",12,main.ink,false));}flow.addView(legend);
        long[] in=new long[6],out=new long[6];String[] names=new String[6];YearMonth[] ms=new YearMonth[6];
        for(int i=0;i<6;i++){YearMonth m=main.month.minusMonths(5-i);ms[i]=m;in[i]=main.budget.income(m);out[i]=main.budget.spending(m);
            names[i]=m.format(DateTimeFormatter.ofPattern("MMM"));}
        TextView picked=label("",13,main.ink,true);flow.addView(picked);
        java.util.function.IntConsumer show=i->picked.setText(ms[i].format(DateTimeFormatter.ofPattern("MMMM yyyy"))+":  in "+money(in[i])+"  ·  out "+money(out[i]));show.accept(5);
        CashFlowChart chart=new CashFlowChart(main,in,out,names,income,spend,main.muted,main.muted,main.buttonSurface,5,show);
        chart.setContentDescription("Income and spending chart for the last six months. The list below has the amounts.");
        flow.addView(chart,new LinearLayout.LayoutParams(-1,-2));
        incomeExpenseTable();
        // Net worth and Money age.
        LinearLayout worth=card();long now=main.budget.netWorth(main.month),change=now-main.budget.netWorth(main.month.minusMonths(1));
        worth.addView(label("Net worth",20,main.ink,true));worth.addView(label(money(now),26,main.ink,true));
        worth.addView(label((change>=0?"Up ":"Down ")+money(Math.abs(change))+" since the end of last month",13,main.muted,false));
        LinearLayout age=card();LocalDate until=main.month.isBefore(YearMonth.now())?main.month.atEndOfMonth():LocalDate.now();
        int days=main.budget.ageOfMoney(until);age.addView(label("Money age",20,main.ink,true));
        age.addView(label(days<0?"Not enough spending yet":count(days,"day","days"),26,days>=30?main.green:main.ink,true));
        age.addView(label("How old your money is when you spend it, over your last 10 outflows. 30 days or more means you're spending last month's income.",13,main.muted,false));
        main.content.addView(label("Last six months",20,main.ink,true));for(int i=5;i>=0;i--){YearMonth m=main.month.minusMonths(i);
            main.content.addView(label(m.format(DateTimeFormatter.ofPattern("MMM yyyy"))+"   In "+money(main.budget.income(m))+"   Out "+money(main.budget.spending(m)),13,main.muted,false));}
        main.content.addView(label("AUD / Saved on this device. Back up or export it in Settings. Bank sync is not included.",12,main.muted,false));
    }
    static final String[] PERIODS={"This month","Last month","Last 3 months","This year"};
    // Categorical colours in fixed order (the validated chart palette, light and dark steps); Other is grey.
    private static final int[] SLICE_LIGHT={0xFF2A78D6,0xFFEB6834,0xFF1BAF7A,0xFFEDA100,0xFFE87BA4,0xFF008300,0xFF4A3AA7},SLICE_DARK={0xFF3987E5,0xFFD95926,0xFF199E70,0xFFC98500,0xFFD55181,0xFF008300,0xFF9085E9};
    private YearMonth[] periodRange(int p){return p==1?new YearMonth[]{main.month.minusMonths(1),main.month.minusMonths(1)}:p==2?new YearMonth[]{main.month.minusMonths(2),main.month}:p==3?new YearMonth[]{main.month.withMonth(1),main.month}:new YearMonth[]{main.month,main.month};}
    private String rangeText(YearMonth[] r){DateTimeFormatter f=DateTimeFormatter.ofPattern("MMMM yyyy");
        return r[0].equals(r[1])?r[0].format(f):r[0].format(f)+" to "+r[1].format(f);}
    private static String percent(int tenths){return tenths/10+"."+tenths%10+"%";}
    private Spinner choice(String[] names,int selection,String description){Spinner s=new Spinner(main);
        s.setAdapter(new ArrayAdapter<>(main,android.R.layout.simple_spinner_dropdown_item,names));s.setSelection(Math.max(0,selection));
        s.setContentDescription(description);return s;}
    private void breakdownCard(){
        LinearLayout card=card();card.addView(label("Spending breakdown",20,main.ink,true));LinearLayout pickers=new LinearLayout(main);
        Spinner when=choice(PERIODS,main.period,"Period"),by=choice(new String[]{"By category","By group"},main.byGroup?1:0,"Show by category or by group");
        pickers.addView(when,new LinearLayout.LayoutParams(0,-2,1));pickers.addView(by,new LinearLayout.LayoutParams(0,-2,1));card.addView(pickers);
        LinearLayout body=column();card.addView(body);fillBreakdown(body);
        onPick(when,()->{if(main.period!=when.getSelectedItemPosition()){main.period=when.getSelectedItemPosition();fillBreakdown(body);}});
        onPick(by,()->{if(main.byGroup!=(by.getSelectedItemPosition()==1)){main.byGroup=!main.byGroup;fillBreakdown(body);}});
    }
    private void fillBreakdown(LinearLayout body){
        body.removeAllViews();YearMonth[] r=periodRange(main.period);List<Budget.Slice> slices=main.budget.breakdown(r[0],r[1],main.byGroup);
        long total=Budget.total(slices);body.addView(label(rangeText(r),12,main.muted,false));
        if(slices.isEmpty()){body.addView(label("No spending in this period.",14,main.muted,false));return;}
        long[] values=new long[slices.size()];int[] colors=new int[slices.size()];
        for(int i=0;i<values.length;i++){values[i]=slices.get(i).amount;
            colors[i]=slices.get(i).other?main.muted:(main.darkTheme?SLICE_DARK:SLICE_LIGHT)[i];}
        SpendingCharts.Donut donut=new SpendingCharts.Donut(main,values,colors,money(total),"spent",main.ink,main.muted);
        donut.setContentDescription("Spending breakdown chart: "+money(total)+" spent. The list below has each amount and share.");
        body.addView(donut,new LinearLayout.LayoutParams(-1,-2));
        // The table view: every slice with its amount and share, biggest first; tap for its transactions.
        for(int i=0;i<values.length;i++){Budget.Slice s=slices.get(i);LinearLayout row=new LinearLayout(main);
            row.setGravity(Gravity.CENTER_VERTICAL);row.setMinimumHeight(dp(44));
            View swatch=new View(main);GradientDrawable d=new GradientDrawable();d.setColor(colors[i]);d.setCornerRadius(dp(2));
            swatch.setBackground(d);LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(dp(10),dp(10));
            sp.setMargins(0,0,dp(8),0);row.addView(swatch,sp);
            row.addView(label(s.name,14,main.ink,false),new LinearLayout.LayoutParams(0,-2,1));
            TextView amount=label(money(s.amount)+"  ·  "+percent(s.tenths),14,main.ink,true);amount.setGravity(Gravity.END);row.addView(amount);
            row.setContentDescription(s.name+", "+money(s.amount)+", "+percent(s.tenths)+". Double tap for its transactions.");
            row.setOnClickListener(v->sliceTransactions(s,r));body.addView(row);}
        // Both views leave out categories that got back more than they spent: their refunds come off the total here, as in Spending.
        long refunds=main.budget.refunds(r[0],r[1]);
        if(refunds>0)body.addView(label("Refunds beyond spending (categories that got more back than they spent): "+money(refunds)+". Net spending: "+money(total-refunds)+".",12,main.muted,false));
        body.addView(label((main.byGroup?"Groups":"Categories")+" beyond the biggest "+Budget.SLICES+" are in Other. Tap a row for its transactions.",11,main.muted,false));
    }
    /** A slice's transactions in the period (Transactions with a category and date filter); a group or Other asks which category first. */
    private void sliceTransactions(Budget.Slice s,YearMonth[] r){
        if(s.ids.size()==1){showTransactions(s.ids.get(0),r);return;}Map<String,Long> spent=main.budget.spentBy(r[0],r[1]);
        List<String> ids=new ArrayList<>(s.ids);ids.sort((a,b)->Long.compare(spent.getOrDefault(b,0L),spent.getOrDefault(a,0L)));
        String[] names=ids.stream().map(id->{Budget.Category c=main.budget.category(id);
            return(c==null?"":c.name)+" ("+money(spent.getOrDefault(id,0L))+")";}).toArray(String[]::new);
        new AlertDialog.Builder(main).setTitle(s.name+": which category?").setItems(names,(d,n)->showTransactions(ids.get(n),r))
            .setNegativeButton("Cancel",null).show();
    }
    private void showTransactions(String categoryId,YearMonth[] r){main.clearFilters();main.categoryFilter=categoryId;
        main.fromFilter=r[0].atDay(1).toString();main.toFilter=r[1].atEndOfMonth().toString();main.tab="Spending";main.render();}
    private void trendsCard(){
        LinearLayout card=card();card.addView(label("Spending trends",20,main.ink,true));List<String> keys=new ArrayList<>(),names=new ArrayList<>();
        for(Budget.Category c:main.budget.categories)if(!c.payment()){keys.add("c:"+c.id);names.add(c.name);}
        for(String g:main.budget.groups()){keys.add("g:"+g);names.add("Group: "+g);}
        if(keys.isEmpty()){card.addView(label("Add a category to see its spending month by month.",14,main.muted,false));return;}
        if(!keys.contains(main.trendKey)){List<Budget.Slice> top=main.budget.breakdown(main.month,main.month,false);
            main.trendKey=top.isEmpty()||top.get(0).other?keys.get(0):"c:"+top.get(0).ids.get(0);} // the month's biggest spending first
        Spinner what=choice(names.toArray(new String[0]),keys.indexOf(main.trendKey),"Category or group"),span=choice(new String[]{"Last 6 months","Last 12 months"},main.trendMonths==12?1:0,"Months shown");
        card.addView(what);card.addView(span);LinearLayout body=column();card.addView(body);fillTrend(body);
        onPick(what,()->{String k=keys.get(what.getSelectedItemPosition());if(!k.equals(main.trendKey)){main.trendKey=k;fillTrend(body);}});
        onPick(span,()->{int m=span.getSelectedItemPosition()==1?12:6;if(m!=main.trendMonths){main.trendMonths=m;fillTrend(body);}});
    }
    private void fillTrend(LinearLayout body){
        body.removeAllViews();
        List<String> ids=main.trendKey.startsWith("g:")?main.budget.groupIds(main.trendKey.substring(2)):Collections.singletonList(main.trendKey.substring(2));
        int n=main.trendMonths;long[] v=main.budget.trend(ids,main.month,n);long average=Budget.average(v);
        String[] labels=new String[n];YearMonth[] ms=new YearMonth[n];for(int i=0;i<n;i++){ms[i]=main.month.minusMonths(n-1-i);
            labels[i]=ms[i].format(DateTimeFormatter.ofPattern(n>6?"MMMMM":"MMM"));}
        body.addView(label("Average "+money(average)+" a month",15,main.ink,true));TextView picked=label("",13,main.ink,true);body.addView(picked);
        java.util.function.IntConsumer show=i->picked.setText(ms[i].format(DateTimeFormatter.ofPattern("MMMM yyyy"))+":  "+money(v[i]));show.accept(n-1);
        SpendingCharts.Trend chart=new SpendingCharts.Trend(main,v,labels,average,(main.darkTheme?SLICE_DARK:SLICE_LIGHT)[0],main.muted,main.muted,main.buttonSurface,n-1,show);
        chart.setContentDescription("Spending per month for the last "+n+" months, averaging "+money(average)+". Show as a list has each month's amount.");
        body.addView(chart,new LinearLayout.LayoutParams(-1,-2));
        body.addView(label("Dashed line: the average. Tap a month for its amount.",11,main.muted,false));
        LinearLayout list=column();list.setVisibility(View.GONE);
        for(int i=n-1;i>=0;i--)list.addView(label(ms[i].format(DateTimeFormatter.ofPattern("MMM yyyy"))+"   "+money(v[i]),13,main.muted,false));
        Button toggle=button("Show as a list",()->{});toggle.setOnClickListener(x->{boolean open=list.getVisibility()!=View.VISIBLE;
            list.setVisibility(open?View.VISIBLE:View.GONE);toggle.setText(open?"Hide the list":"Show as a list");});
        body.addView(toggle);body.addView(list);
    }
    /** Income by payee and expenses by group and category over six months to the month on screen, with average and total columns (scroll sideways). */
    private void incomeExpenseTable(){
        LinearLayout card=card();card.addView(label("Income and expenses",20,main.ink,true));
        Budget.Table t=main.budget.incomeExpense(main.month.minusMonths(5),6);
        if(t.income.isEmpty()&&t.expenses.isEmpty()){card.addView(label("No income or spending in the six months to "+main.month.format(DateTimeFormatter.ofPattern("MMMM yyyy"))+".",14,main.muted,false));return;}
        card.addView(label("Six months to "+main.month.format(DateTimeFormatter.ofPattern("MMMM yyyy"))+". Scroll sideways for every month, the average and the total.",12,main.muted,false));
        String[] heads=new String[t.months.length+2];
        for(int i=0;i<t.months.length;i++)heads[i]=t.months[i].format(DateTimeFormatter.ofPattern("MMM yyyy"));heads[heads.length-2]="Average";
        heads[heads.length-1]="Total";
        LinearLayout table=new LinearLayout(main),names=column(),cols=column();HorizontalScrollView scroll=new HorizontalScrollView(main);
        scroll.addView(cols);table.addView(names,new LinearLayout.LayoutParams(dp(118),-2));
        table.addView(scroll,new LinearLayout.LayoutParams(0,-2,1));card.addView(table);
        // Opens scrolled to the end (the average and total). The scrolling part shows whole columns only, so no amount is cut
        // short at its edge (a cut "-$450.00" read as "450.00"); the names get the width left over.
        scroll.post(()->{int cell=dp(96),spare=scroll.getWidth()%cell;if(scroll.getWidth()>=cell&&spare>0){LinearLayout.LayoutParams p=(LinearLayout.LayoutParams)names.getLayoutParams();
            p.width=names.getWidth()+spare;names.setLayoutParams(p);}scroll.post(()->scroll.fullScroll(View.FOCUS_RIGHT));});
        tableRow(names,cols,"",heads,null,main.muted,true,0);
        tableRow(names,cols,"Income",null,null,main.blue,true,0);for(Budget.Row r:t.income)tableRow(names,cols,r.name,heads,r,main.ink,false,8);
        tableRow(names,cols,t.incomeTotal.name,heads,t.incomeTotal,main.ink,true,0);
        tableRow(names,cols,"Expenses",null,null,main.blue,true,0);
        for(Budget.Row r:t.expenses)tableRow(names,cols,r.name,heads,r,r.group?main.ink:main.muted,r.group,r.group?8:16);
        tableRow(names,cols,t.expenseTotal.name,heads,t.expenseTotal,main.ink,true,0);
        tableRow(names,cols,"Net (income − expenses)",heads,t.net,main.ink,true,0);
    }
    /** One table row: its name on the left, its months, average and total in the scrolling part ([r] null: [heads] themselves, or nothing for a section title). */
    private void tableRow(LinearLayout names,LinearLayout cols,String name,String[] heads,Budget.Row r,int color,boolean bold,int indent){
        TextView n=label(name,12,color,bold);n.setSingleLine(true);n.setEllipsize(TextUtils.TruncateAt.END);n.setGravity(Gravity.CENTER_VERTICAL);
        n.setPadding(dp(indent),0,dp(4),0);names.addView(n,new LinearLayout.LayoutParams(-1,dp(30)));
        LinearLayout line=new LinearLayout(main);int cells=heads==null?0:heads.length;
        for(int i=0;i<cells;i++){String text=r==null?heads[i]:money(i<r.amounts.length?r.amounts[i]:i==r.amounts.length?r.average():r.total());
            TextView c=label(text,12,r==null?main.muted:color,bold||i>=cells-2);c.setSingleLine(true);
            c.setGravity(Gravity.END|Gravity.CENTER_VERTICAL);c.setPadding(dp(4),0,dp(4),0);
            if(r!=null)c.setContentDescription(name+", "+heads[i]+", "+text);line.addView(c,new LinearLayout.LayoutParams(dp(96),dp(30)));}
        if(cells==0)line.addView(new View(main),new LinearLayout.LayoutParams(dp(1),dp(30)));cols.addView(line);
    }
}
