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
        // The cards, in the order they were held and dragged into (Movable; Default order puts them back).
        new Movable(this,"order_reports","Reports",CardOrder.REPORTS).build(key->{switch(key){case "cash":cashCard();break;case "breakdown":breakdownCard();break;
            case "trends":trendsCard();break;case "flow":flowCard();break;case "table":incomeExpenseTable();break;case "year":yearCard();break;
            case "worth":worthCard();break;default:ageCard();}});
        main.content.addView(heading("Last six months",20,main.ink));for(int i=5;i>=0;i--){YearMonth m=main.month.minusMonths(i);
            TextView line=label("",13,main.muted,false);line.setText(inOut(m.format(DateTimeFormatter.ofPattern("MMM yyyy"))+"   In ",main.budget.income(m),"   Out ",main.budget.spending(m)));main.content.addView(line);}
        main.content.addView(label(code()+" / Saved on this device. Back up or export it in Settings. Bank sync is not included.",12,main.muted,false));
    }
    private void cashCard(){
        LinearLayout totals=card();totals.addView(heading("This month's cash flow",20,main.ink));
        long monthIn=main.budget.income(main.month),monthOut=main.budget.spending(main.month); // card payments, transfers and tracking accounts aren't spending
        totals.addView(label("Income "+money(monthIn),21,amountColour(monthIn),true));
        totals.addView(label("Spending "+money(monthOut),21,outColour(monthOut),true));
        totals.addView(label("Difference "+money(monthIn-monthOut),17,amountColour(monthIn-monthOut),true));
    }
    /** Income vs spending, six months to this one: one axis from zero; the list below is the table view. */
    private void flowCard(){
        LinearLayout flow=card();flow.addView(heading("Income and spending",20,main.ink));
        int income=incomeColor(),spend=spendColor();
        LinearLayout legend=new LinearLayout(main);legend.setGravity(Gravity.CENTER_VERTICAL);
        for(int k=0;k<2;k++){View swatch=new View(main);GradientDrawable s=new GradientDrawable();s.setColor(k==0?income:spend);
            s.setCornerRadius(dp(2));swatch.setBackground(s);swatch.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(dp(10),dp(10));
            sp.setMargins(k==0?0:dp(14),0,dp(6),0);legend.addView(swatch,sp);
            legend.addView(label(k==0?"Income":"Spending",12,main.ink,false));}flow.addView(legend);
        long[] in=new long[6],out=new long[6];String[] names=new String[6];YearMonth[] ms=new YearMonth[6];
        for(int i=0;i<6;i++){YearMonth m=main.month.minusMonths(5-i);ms[i]=m;in[i]=main.budget.income(m);out[i]=main.budget.spending(m);
            names[i]=m.format(DateTimeFormatter.ofPattern("MMM"));}
        TextView picked=label("",13,main.ink,true);picked.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);flow.addView(picked);
        java.util.function.IntConsumer show=i->picked.setText(inOut(ms[i].format(DateTimeFormatter.ofPattern("MMMM yyyy"))+":  in ",in[i],"  ·  out ",out[i]));show.accept(5);
        CashFlowChart chart=new CashFlowChart(main,in,out,names,income,spend,main.muted,main.muted,main.buttonSurface,5,show);
        String[] said=new String[6];for(int i=0;i<6;i++)said[i]=ms[i].format(DateTimeFormatter.ofPattern("MMMM yyyy"))+": income "+money(in[i])+", spending "+money(out[i]);
        chart.setContentDescription("Income and spending chart, last six months. "+String.join(". ",said)+".");chart.spoken(said);
        if(main.hideAmounts)flow.addView(label("The bars are hidden while amounts are hidden.",12,main.muted,false)); // hunt 26 C7: drawn to scale they tell which is bigger
        else flow.addView(chart,new LinearLayout.LayoutParams(-1,-2));
    }
    private void worthCard(){
        LinearLayout worth=card();long now=main.budget.netWorth(main.month),change=now-main.budget.netWorth(main.month.minusMonths(1));
        worth.addView(heading("Net worth",20,main.ink));worth.addView(label(money(now),26,amountColour(now),true));
        TextView moved=label("",13,main.muted,false);String by=main.hideAmounts?"Changed by ":change>=0?"Up ":"Down "; // hunt 26 C7: hidden, no direction
        moved.setText(tint(by+money(Math.abs(change))+" since the end of last month",money(Math.abs(change)),amountColour(change)));worth.addView(moved);
    }
    private void ageCard(){
        LinearLayout age=card();LocalDate until=main.month.isBefore(YearMonth.now())?main.month.atEndOfMonth():LocalDate.now();
        int days=main.budget.ageOfMoney(until);age.addView(heading("Money age",20,main.ink));
        age.addView(label(days<0?"Not enough spending yet":count(days,"day","days"),26,days>=30?main.green:main.ink,true));
        age.addView(label("How old your money is when you spend it, over your last 10 outflows. 30 days or more means you're spending last month's income.",13,main.muted,false));
    }
    /** "[before]in-amount[between]out-amount": money in coloured by its sign, money out (spending, shown without a minus) red. */
    private CharSequence inOut(String before,long in,String between,long out){
        return tint2(before+money(in)+between+money(out),money(in),amountColour(in),money(out),outColour(out));}
    private int incomeColor(){return main.darkTheme?Color.parseColor("#3987E5"):Color.parseColor("#2A78D6");}
    private int spendColor(){return main.darkTheme?Color.parseColor("#D95926"):Color.parseColor("#EB6834");}
    /** The yearly report (Budget.year): pick a year with transactions; income, spending and net, each month, the top 10 categories and every group. */
    private void yearCard(){LinearLayout card=card();card.addView(heading("Yearly report",20,main.ink));LinearLayout body=column();card.addView(body);fillYear(body);}
    private void fillYear(LinearLayout body){
        body.removeAllViews();int now=LocalDate.now().getYear();int[] range=main.budget.years(now);
        int year=main.reportYear==0?now:Math.max(range[0],Math.min(range[1],main.reportYear));
        LinearLayout pick=new LinearLayout(main);pick.setGravity(Gravity.CENTER_VERTICAL);
        Button back=button("‹",()->{main.reportYear=year-1;fillYear(body);}),next=button("›",()->{main.reportYear=year+1;fillYear(body);});
        for(Button b:new Button[]{back,next}){b.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP,26); // symbols in a 48dp box: they don't grow with the text size
            b.setBackground(bg(Color.TRANSPARENT));}
        back.setContentDescription("Previous year");next.setContentDescription("Next year");back.setEnabled(year>range[0]);next.setEnabled(year<range[1]);
        back.setAlpha(back.isEnabled()?1f:0.3f);next.setAlpha(next.isEnabled()?1f:0.3f); // only years with transactions
        TextView title=label(String.valueOf(year),18,main.ink,true);title.setGravity(Gravity.CENTER);
        pick.addView(back,new LinearLayout.LayoutParams(dp(48),dp(48)));pick.addView(title,new LinearLayout.LayoutParams(0,-2,1));pick.addView(next,new LinearLayout.LayoutParams(dp(48),dp(48)));body.addView(pick);
        Budget.Year y=main.budget.year(year);
        body.addView(label("Income "+money(y.income()),17,amountColour(y.income()),true));body.addView(label("Spending "+money(y.spending()),17,outColour(y.spending()),true));
        body.addView(label("Net (income − spending) "+money(y.net()),15,amountColour(y.net()),true));
        if(y.income()==0&&y.spending()==0&&y.top.isEmpty()){quiet(body,"📊","No income or spending in "+year+".");return;}
        // Month by month: the chart (tap a month for its amounts) and the same as a list.
        String[] names=new String[12];YearMonth[] ms=new YearMonth[12];for(int i=0;i<12;i++){ms[i]=YearMonth.of(year,i+1);names[i]=ms[i].format(DateTimeFormatter.ofPattern("MMMMM"));}
        TextView picked=label("",13,main.ink,true);picked.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);body.addView(picked);
        java.util.function.IntConsumer show=i->picked.setText(inOut(ms[i].format(DateTimeFormatter.ofPattern("MMMM yyyy"))+":  in ",y.income[i],"  ·  out ",y.spending[i]));
        int selected=year==now?LocalDate.now().getMonthValue()-1:11;show.accept(selected);
        CashFlowChart chart=new CashFlowChart(main,y.income,y.spending,names,incomeColor(),spendColor(),main.muted,main.muted,main.buttonSurface,selected,show);
        String[] said=new String[12];for(int i=0;i<12;i++)said[i]=ms[i].format(DateTimeFormatter.ofPattern("MMMM"))+": income "+money(y.income[i])+", spending "+money(y.spending[i]);
        chart.setContentDescription("Income and spending chart for each month of "+year+". "+String.join(". ",said)+".");chart.spoken(said);
        if(main.hideAmounts)body.addView(label("The bars are hidden while amounts are hidden.",12,main.muted,false));else body.addView(chart,new LinearLayout.LayoutParams(-1,-2)); // C7
        for(int i=0;i<12;i++){TextView line=label("",13,main.muted,false);line.setText(inOut(ms[i].format(DateTimeFormatter.ofPattern("MMM"))+"   In ",y.income[i],"   Out ",y.spending[i]));body.addView(line);}
        // The biggest categories and every group, with their share of the year's spending (as the spending breakdown); tap for transactions.
        YearMonth[] r={YearMonth.of(year,1),YearMonth.of(year,12)};
        body.addView(heading("Top "+Budget.TOP+" categories",16,main.ink));yearRows(body,y.top,r);
        body.addView(heading("By group",16,main.ink));yearRows(body,y.groups,r);
        if(y.refunds>0)body.addView(greyLine("Refunds beyond spending (categories that got more back than they spent): "+money(y.refunds)+", taken off the year's spending.",money(y.refunds),amountColour(y.refunds),12));
    }
    private void yearRows(LinearLayout body,List<Budget.Slice> slices,YearMonth[] r){
        if(slices.isEmpty()){quiet(body,"📊","No spending this year.");return;}
        for(Budget.Slice s:slices){LinearLayout row=new LinearLayout(main);row.setGravity(Gravity.CENTER_VERTICAL);row.setMinimumHeight(dp(48));
            TextView amount=sliceAmount(s);nameAndAmount(row,s.name,amount);
            row.setContentDescription(s.name+", "+money(s.amount)+", "+percent(s.tenths)+". Double tap for its transactions.");
            row.setOnClickListener(v->sliceTransactions(s,r));body.addView(row);}
    }
    /** A slice's spending (red: money out) and its share. */
    private TextView sliceAmount(Budget.Slice s){TextView amount=label("",14,main.ink,true);amount.setGravity(Gravity.END);
        amount.setText(tint(money(s.amount)+"  ·  "+percent(s.tenths),money(s.amount),outColour(s.amount)));return amount;}
    /** A row's name and amount side by side; with large text the amount goes under the name, so a name never breaks mid-word. */
    private void nameAndAmount(LinearLayout row,String name,TextView amount){TextView n=label(name,14,main.ink,false);
        if(main.getResources().getConfiguration().fontScale<1.3f){row.addView(n,new LinearLayout.LayoutParams(0,-2,1));row.addView(amount);return;}
        LinearLayout both=column();both.addView(n);both.addView(amount,new LinearLayout.LayoutParams(-1,-2));row.addView(both,new LinearLayout.LayoutParams(0,-2,1));}
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
        LinearLayout card=card();card.addView(heading("Spending breakdown",20,main.ink));LinearLayout pickers=new LinearLayout(main);
        Spinner when=choice(PERIODS,main.period,"Period"),by=choice(new String[]{"By category","By group"},main.byGroup?1:0,"Show by category or by group");
        pickers.addView(when,new LinearLayout.LayoutParams(0,-2,1));pickers.addView(by,new LinearLayout.LayoutParams(0,-2,1));card.addView(pickers);
        LinearLayout body=column();card.addView(body);fillBreakdown(body);
        onPick(when,()->{if(main.period!=when.getSelectedItemPosition()){main.period=when.getSelectedItemPosition();fillBreakdown(body);}});
        onPick(by,()->{if(main.byGroup!=(by.getSelectedItemPosition()==1)){main.byGroup=!main.byGroup;fillBreakdown(body);}});
    }
    private void fillBreakdown(LinearLayout body){
        body.removeAllViews();YearMonth[] r=periodRange(main.period);List<Budget.Slice> slices=main.budget.breakdown(r[0],r[1],main.byGroup);
        long total=Budget.total(slices);body.addView(label(rangeText(r),12,main.muted,false));
        if(slices.isEmpty()){quiet(body,"📊","No spending in this period.");return;}
        long[] values=new long[slices.size()];int[] colors=new int[slices.size()];
        for(int i=0;i<values.length;i++){values[i]=slices.get(i).amount;
            colors[i]=slices.get(i).other?main.muted:(main.darkTheme?SLICE_DARK:SLICE_LIGHT)[i];}
        SpendingCharts.Donut donut=new SpendingCharts.Donut(main,values,colors,money(total),"spent",main.ink,main.muted);
        StringBuilder said=new StringBuilder("Spending breakdown chart: "+money(total)+" spent");for(Budget.Slice s:slices)said.append(". ").append(s.name).append(", ").append(money(s.amount)).append(", ").append(percent(s.tenths));
        donut.setContentDescription(said+".");
        body.addView(donut,new LinearLayout.LayoutParams(-1,-2));
        // The table view: every slice with its amount and share, biggest first; tap for its transactions.
        for(int i=0;i<values.length;i++){Budget.Slice s=slices.get(i);LinearLayout row=new LinearLayout(main);
            row.setGravity(Gravity.CENTER_VERTICAL);row.setMinimumHeight(dp(48));
            View swatch=new View(main);GradientDrawable d=new GradientDrawable();d.setColor(colors[i]);d.setCornerRadius(dp(2));
            swatch.setBackground(d);swatch.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(dp(10),dp(10));
            sp.setMargins(0,0,dp(8),0);row.addView(swatch,sp);
            TextView amount=sliceAmount(s);nameAndAmount(row,s.name,amount);
            row.setContentDescription(s.name+", "+money(s.amount)+", "+percent(s.tenths)+". Double tap for its transactions.");
            row.setOnClickListener(v->sliceTransactions(s,r));body.addView(row);}
        // Both views leave out categories that got back more than they spent: their refunds come off the total here, as in Spending.
        long refunds=main.budget.refunds(r[0],r[1]);
        if(refunds>0){TextView note=label("",12,main.muted,false);String text="Refunds beyond spending (categories that got more back than they spent): "+money(refunds)+". Net spending: "+money(total-refunds)+".";
            note.setText(tint2(text,money(refunds),amountColour(refunds),money(total-refunds),outColour(total-refunds)));body.addView(note);}
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
        LinearLayout card=card();card.addView(heading("Spending trends",20,main.ink));List<String> keys=new ArrayList<>(),names=new ArrayList<>();
        for(Budget.Category c:main.budget.categories)if(!c.payment()){keys.add("c:"+c.id);names.add(c.name);}
        for(String g:main.budget.groups()){keys.add("g:"+g);names.add("Group: "+g);}
        if(keys.isEmpty()){card.addView(label("Add a category to see its spending month by month.",14,main.muted,false));return;}
        if(!keys.contains(main.trendKey)){List<Budget.Slice> top=main.budget.breakdown(main.month,main.month,false);
            main.trendKey=keys.get(0);for(Budget.Slice s:top)if(!s.other&&keys.contains("c:"+s.ids.get(0))){main.trendKey="c:"+s.ids.get(0);break;}} // the month's biggest spending first (hunt 25 C6: of the categories listed, not a card's interest)
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
        TextView avg=label("",15,main.ink,true);avg.setText(tint("Average "+money(average)+" a month",money(average),outColour(average)));body.addView(avg);
        TextView picked=label("",13,main.ink,true);picked.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);body.addView(picked);
        java.util.function.IntConsumer show=i->{String month=ms[i].format(DateTimeFormatter.ofPattern("MMMM yyyy"))+":  ";picked.setText(tint(month+money(v[i]),money(v[i]),outColour(v[i])));};show.accept(n-1);
        SpendingCharts.Trend chart=new SpendingCharts.Trend(main,v,labels,average,(main.darkTheme?SLICE_DARK:SLICE_LIGHT)[0],main.muted,main.muted,main.buttonSurface,n-1,show);
        String[] said=new String[n];for(int i=0;i<n;i++)said[i]=ms[i].format(DateTimeFormatter.ofPattern("MMMM yyyy"))+": "+money(v[i]);
        chart.setContentDescription("Spending per month for the last "+n+" months, averaging "+money(average)+". "+String.join(". ",said)+".");chart.spoken(said);
        body.addView(chart,new LinearLayout.LayoutParams(-1,-2));
        body.addView(label("Dashed line: the average. Tap a month for its amount.",11,main.muted,false));
        LinearLayout list=column();list.setVisibility(View.GONE);
        for(int i=n-1;i>=0;i--){TextView row=label("",13,main.muted,false);String month=ms[i].format(DateTimeFormatter.ofPattern("MMM yyyy"))+"   ";
            row.setText(tint(month+money(v[i]),money(v[i]),outColour(v[i])));list.addView(row);}
        Button toggle=button("Show as a list",()->{});toggle.setOnClickListener(x->{boolean open=list.getVisibility()!=View.VISIBLE;
            list.setVisibility(open?View.VISIBLE:View.GONE);toggle.setText(open?"Hide the list":"Show as a list");});
        body.addView(toggle);body.addView(list);
    }
    /** Income by payee and expenses by group and category over six months to the month on screen, with average and total columns (scroll sideways). */
    private void incomeExpenseTable(){
        LinearLayout card=card();card.addView(heading("Income and expenses",20,main.ink));
        Budget.Table t=main.budget.incomeExpense(main.month.minusMonths(5),6);
        if(t.income.isEmpty()&&t.expenses.isEmpty()){quiet(card,"📊","No income or spending in the six months to "+main.month.format(DateTimeFormatter.ofPattern("MMMM yyyy"))+".");return;}
        card.addView(label("Six months to "+main.month.format(DateTimeFormatter.ofPattern("MMMM yyyy"))+". Scroll sideways for every month, the average and the total.",12,main.muted,false));
        String[] heads=new String[t.months.length+2];
        for(int i=0;i<t.months.length;i++)heads[i]=t.months[i].format(DateTimeFormatter.ofPattern("MMM yyyy"));heads[heads.length-2]="Average";
        heads[heads.length-1]="Total";
        LinearLayout table=new LinearLayout(main),names=column(),cols=column();HorizontalScrollView scroll=new HorizontalScrollView(main);
        scroll.addView(cols);table.addView(names,new LinearLayout.LayoutParams(grown(118),-2));
        table.addView(scroll,new LinearLayout.LayoutParams(0,-2,1));card.addView(table);
        // Opens scrolled to the end (the average and total). The scrolling part shows whole columns only, so no amount is cut
        // short at its edge (a cut "-$450.00" read as "450.00"); the names get the width left over (with large text, less, so one column fits).
        scroll.post(()->{int cell=grown(96),total=names.getWidth()+scroll.getWidth(),least=Math.max(dp(72),Math.min(grown(118),total-cell)),fit=Math.max(1,(total-least)/cell);
            LinearLayout.LayoutParams p=(LinearLayout.LayoutParams)names.getLayoutParams();if(total-fit*cell!=names.getWidth()){p.width=total-fit*cell;names.setLayoutParams(p);}
            scroll.post(()->scroll.fullScroll(View.FOCUS_RIGHT));});
        tableRow(names,cols,"",heads,null,main.muted,true,0,1);
        tableRow(names,cols,"Income",null,null,main.blue,true,0,1);for(Budget.Row r:t.income)tableRow(names,cols,r.name,heads,r,main.ink,false,8,1);
        tableRow(names,cols,t.incomeTotal.name,heads,t.incomeTotal,main.ink,true,0,1);
        tableRow(names,cols,"Expenses",null,null,main.blue,true,0,-1);
        for(Budget.Row r:t.expenses)tableRow(names,cols,r.name,heads,r,r.group?main.ink:main.muted,r.group,r.group?8:16,-1);
        tableRow(names,cols,t.expenseTotal.name,heads,t.expenseTotal,main.ink,true,0,-1);
        tableRow(names,cols,"Net (income − expenses)",heads,t.net,main.ink,true,0,1);
    }
    /** [n] dp, grown with large text (up to twice), for the table's fixed rows and columns, so amounts aren't cut. */
    private int grown(int n){return Math.round(dp(n)*Math.max(1f,Math.min(2f,main.getResources().getConfiguration().fontScale)));}
    /**
     * One table row: its name on the left, its months, average and total in the scrolling part ([r] null: [heads] themselves, or
     * nothing for a section title). Each amount is coloured by its sign; [sign] -1: expenses (money out, shown without a minus) are red.
     */
    private void tableRow(LinearLayout names,LinearLayout cols,String name,String[] heads,Budget.Row r,int color,boolean bold,int indent,int sign){
        TextView n=label(name,12,color,bold);n.setSingleLine(true);n.setEllipsize(TextUtils.TruncateAt.END);n.setGravity(Gravity.CENTER_VERTICAL);
        n.setPadding(dp(indent),0,dp(4),0);names.addView(n,new LinearLayout.LayoutParams(-1,grown(30)));
        LinearLayout line=new LinearLayout(main);int cells=heads==null?0:heads.length;
        for(int i=0;i<cells;i++){long v=r==null?0:i<r.amounts.length?r.amounts[i]:i==r.amounts.length?r.average():r.total();String text=r==null?heads[i]:money(v);
            TextView c=label(text,12,r==null?main.muted:v==0?color:sign<0?outColour(v):amountColour(v),bold||i>=cells-2);c.setSingleLine(true);
            c.setGravity(Gravity.END|Gravity.CENTER_VERTICAL);c.setPadding(dp(4),0,dp(4),0);
            if(r!=null)c.setContentDescription(name+", "+heads[i]+", "+text);line.addView(c,new LinearLayout.LayoutParams(grown(96),grown(30)));}
        if(cells==0)line.addView(new View(main),new LinearLayout.LayoutParams(dp(1),grown(30)));cols.addView(line);
    }
}
