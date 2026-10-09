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

/** Budget (tab key "Plan"): categories and their targets, assigning and moving money, budget reset. */
final class BudgetScreen extends Ui {
    BudgetScreen(MainActivity main){super(main);}
    /** Budget reset: every category's money in this month goes back into To budget, to start the plan afresh. The budget before is kept for Undo. */
    void planReset(){
        if(!main.storageReadable)return;if(main.month.isAfter(YearMonth.now())){toast("Reset this month or an earlier one.");return;}
        Map<Budget.Category,Long> back=main.budget.resetAmounts(main.month);long total=0;for(long a:back.values())total+=a;
        int n=back.size(); // exactly what the reset returns
        if(n==0){toast("No category has money to return this month.");return;}
        String monthName=main.month.format(DateTimeFormatter.ofPattern("MMMM yyyy"));
        Ui.danger(new AlertDialog.Builder(main).setTitle("Budget reset")
            .setMessage("Return "+money(total)+" from "+count(n,"category","categories")+" into To budget in "+monthName+", then assign it again by today's priorities?\n\nTargets and transactions stay. You can undo this on Budget.")
            .setNegativeButton("Cancel",null).setPositiveButton("Reset",(d,w)->{String before=main.prefs().getString("data",null);
                if(main.change(()->main.budget.planReset(main.month))){if(!main.prefs().edit().putString("before_reset",before==null?"":before).putString("before_reset_at",LocalDateTime.now().withNano(0).toString()).commit())toast("Reset done, but Undo couldn't be saved.");else{main.tab="Plan";main.render();}}}).show());
    }
    private void undoPlanReset(){
        new AlertDialog.Builder(main).setTitle("Undo budget reset?")
            .setMessage("Puts back the plan you had before the reset on "+when(main.prefs().getString("before_reset_at",""))+". Changes made since are lost.")
            .setNegativeButton("Cancel",null).setPositiveButton("Undo reset",(d,w)->{
            String before=main.prefs().getString("before_reset",null);if(before==null){main.render();return;}
            Budget previous;
            try{previous=before.isEmpty()?null:BudgetStore.decode(before);}catch(Exception e){toast("The plan from before the reset can't be read. Nothing was changed.");return;}
            android.content.SharedPreferences.Editor edit=main.prefs().edit().remove("before_reset").remove("before_reset_at");
            if(previous==null)edit.remove("data");else edit.putString("data",before);
            if(!edit.commit()){toast("Could not save to device storage.");return;}BudgetWidget.refresh(main);
            if(previous==null){main.budget=new Budget();main.load();}else{main.budget=previous;main.loaded=before;}main.render();
            toast("Budget reset undone.");}).show();
    }
    private String targetDescription(Budget.Category c){String by=c.dueDay>0?" by the "+ordinal(c.dueDay):"";
        if(c.targetType.equals("Monthly"))return "Set aside "+money(c.target)+by+" each month";
        if(c.targetType.equals("Balance"))return "Save to "+money(c.target)+(c.due.isEmpty()?"":" by "+YearMonth.parse(c.due).format(DateTimeFormatter.ofPattern("MMM yyyy")));
        if(c.targetType.equals("Debt"))return "Pay "+money(c.target)+by+" on this debt each month";
        if(c.targetType.equals("Weekly")){int n=Budget.weekdaysIn(main.month,c.weekday);
            return (c.weeklyRefill?"Refill to ":"Set aside ")+money(c.target)+" every "+dayName(c.weekday)+" · "+n+" this month = "+money(c.target*n);} // e.g. "$40 every Monday · 5 this month = $200"
        if(c.targetType.equals("ByDate")){LocalDate d=Budget.dueFor(c,main.month);
            String every=c.repeatMonths>0?", then every "+c.repeatMonths+" months":"";
            return "Save "+money(c.target)+" by "+pretty(d==null?c.dueDate:d.toString())+every;}
        return "Refill to "+money(c.target)+by+" each month";}
    /**
     * To budget: the brand colour deepening to violet, the figure large. When it changes (an assignment, income) it counts from
     * the old figure to the new one; when it reaches zero in the month on screen, a burst of confetti: every dollar has a job.
     */
    void readyCard(){LinearLayout c=card();GradientDrawable g=new GradientDrawable(GradientDrawable.Orientation.TL_BR,
            new int[]{main.primary,main.darkTheme?Color.rgb(96,66,168):Color.rgb(88,58,166)});g.setCornerRadius(dp(20));c.setBackground(g);
        c.setPadding(dp(18),dp(14),dp(18),dp(16));c.setElevation(main.darkTheme?0:dp(3));
        TextView title=label("TO BUDGET",11,Color.WHITE,true);title.setLetterSpacing(0.12f);title.setAlpha(0.85f);c.addView(title);
        // The figure: green above zero, red below, white at zero (and with Hide amounts, so the colour doesn't tell). The card is
        // dark indigo in both themes, so it takes the dark theme's brighter green and red, which stay readable on it.
        long ready=main.budget.spendable(main.month);int tone=main.hideAmounts||ready==0?Color.WHITE:ready>0?Color.rgb(117,219,177):Color.rgb(255,142,150);
        TextView figure=label(money(ready),34,tone,true);c.addView(figure);
        // Hunt 23 M3: counted only from the figure this month had (another tab may have changed the month since it was shown).
        Long before=main.shownReady;boolean sameMonth=main.month.equals(main.shownMonth)&&main.month.equals(main.shownReadyMonth);main.shownReady=ready;main.shownReadyMonth=main.month;
        if(motion()&&sameMonth&&before!=null&&before!=ready&&!main.hideAmounts){figure.setContentDescription(money(ready));
            android.animation.ValueAnimator count=android.animation.ValueAnimator.ofFloat(0f,1f);count.setDuration(700);
            count.setInterpolator(new android.view.animation.DecelerateInterpolator(2f));long from=before;
            count.addUpdateListener(a->figure.setText(money(from+Math.round((ready-from)*(float)a.getAnimatedValue()))));
            count.addListener(new android.animation.AnimatorListenerAdapter(){@Override public void onAnimationEnd(android.animation.Animator a){figure.setText(money(ready));}});
            figure.setText(money(from));count.start();}
        boolean allAssigned=ready==0&&!main.hideAmounts&&!main.budget.accounts.isEmpty()&&!main.budget.categories.isEmpty();long future=main.budget.futureAssigned(main.month); // hunt 25 C4: not with Hide amounts (it would tell the figure)
        TextView line=label(allAssigned?"Every dollar has a job 🎉":main.hideAmounts?"Amounts are hidden.":future>0?money(future)+" reserved in future months":"Give the money you have a purpose.",13,Color.WHITE,allAssigned);
        line.setAlpha(allAssigned?1f:0.9f);c.addView(line);
        TextView how=label("How it's worked out ›",12,Color.WHITE,false);how.setAlpha(0.75f);how.setPadding(0,dp(4),0,0);c.addView(how);c.setOnClickListener(v->readyBreakdown());
        if(allAssigned&&sameMonth&&before!=null&&before!=0){line.setPivotX(0);pop(line);Burst.over(c,main.darkTheme);confirm(c);}}
    /** How To budget is worked out (Budget.readyParts): parts that add up to it exactly, then this month's income and Assigned. */
    void readyBreakdown(){YearMonth m=main.month;long[] p=main.budget.readyParts(m);long ready=main.budget.spendable(m);
        String name=m.format(DateTimeFormatter.ofPattern("MMMM yyyy"));LinearLayout f=form();
        f.addView(label("To budget is the money in your accounts that no category holds yet.",13,main.muted,false));
        partRow(f,"In your cash and bank accounts"+(m.isBefore(YearMonth.now())?" at the end of "+name:""),p[0],false);
        partRow(f,"Held in categories",-p[1],false);
        boolean cards=false;for(Budget.Account a:main.budget.accounts)cards|=a.credit();
        if(cards||p[2]!=0)partRow(f,"Set aside for card payments",-p[2],false);
        if(p[3]!=0)partRow(f,"Card payments below zero (paid beyond what was set aside, or card credit)",p[3],false); // hunt 24 A1
        if(p[4]!=0)partRow(f,"Overspent in cash this month (comes off next month's To budget)",p[4],false);
        if(p[5]!=0)partRow(f,"Assigned in later months",-p[5],false);
        View rule=new View(main);rule.setBackgroundColor(tint(main.ink,main.darkTheme?60:40));LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,dp(1));rp.setMargins(0,dp(6),0,dp(2));f.addView(rule,rp);
        partRow(f,"To budget",ready,true);
        long income=main.budget.income(m),assigned=main.budget.assignedIn(m);
        TextView month=label("",13,main.muted,false);month.setText(main.hideAmounts?"In "+name+": income "+money(income)+", assigned "+money(assigned)+".":tint2("In "+name+": income "+money(income)+", assigned "+money(assigned)+".",money(income),amountColour(income),money(assigned),amountColour(assigned)));
        month.setPadding(0,dp(10),0,0);f.addView(month);
        ScrollView scroll=new ScrollView(main);scroll.addView(f);
        new AlertDialog.Builder(main).setTitle("How To budget is worked out").setView(scroll).setPositiveButton("OK",null).show();}
    /** One line of the To budget sum: its name, and the amount at the end (under it with large text). */
    private void partRow(LinearLayout f,String name,long amount,boolean total){LinearLayout r=new LinearLayout(main);r.setPadding(0,dp(5),0,dp(5));
        r.setGravity(Gravity.CENTER_VERTICAL);if(large())r.setOrientation(LinearLayout.VERTICAL);
        TextView n=label(name,total?16:14,main.ink,total),v=label(money(amount),total?18:14,main.hideAmounts?main.ink:amountColour(amount),true); // hunt 24 A2: no sign shown by colour with Hide amounts
        r.addView(n,large()?new LinearLayout.LayoutParams(-1,-2):new LinearLayout.LayoutParams(0,-2,1));if(!large()){v.setGravity(Gravity.END);v.setPadding(dp(12),0,0,0);}
        r.addView(v);f.addView(r);}
    private int overspent(){int n=0;for(Budget.Category c:main.budget.categories)if(main.budget.toCover(c,main.month)>0)n++;return n;}
    void categoryCard(Budget.Category c){
        LinearLayout row=card();
        long available=main.budget.available(c,main.month),need=main.budget.needed(c,main.month),cover=main.budget.toCover(c,main.month);
        int status=cover>0?main.red:need>0||available<0?main.amber:main.green;
        LinearLayout heading=new LinearLayout(main);heading.setGravity(Gravity.CENTER_VERTICAL);TextView name=label(c.name,16,main.ink,true);
        name.setPadding(0,0,dp(8),0);boolean large=main.getResources().getConfiguration().fontScale>=1.3f; // large text: the name on its own line, Available under it
        if(large)heading.setOrientation(LinearLayout.VERTICAL);heading.addView(name,large?new LinearLayout.LayoutParams(-1,-2):new LinearLayout.LayoutParams(0,-2,1));LinearLayout balance=column();
        TextView caption=label("Available",10,main.muted,false);caption.setGravity(Gravity.CENTER);caption.setPadding(0,0,0,dp(2));balance.addView(caption);
        // Available in a pill tinted by its state: green (fine), amber (still to fund, or below zero on a card), red (overspent).
        TextView value=label(money(available),20,status,true);value.setGravity(Gravity.CENTER);value.setPadding(dp(8),0,dp(8),0);
        GradientDrawable pill=bg(tint(status,main.darkTheme?46:24));pill.setCornerRadius(dp(14));value.setBackground(pill);
        value.setAutoSizeTextTypeUniformWithConfiguration(12,20,1,android.util.TypedValue.COMPLEX_UNIT_SP);
        float scale=Math.max(1f,Math.min(1.6f,main.getResources().getConfiguration().fontScale)); // large text: room for the whole amount
        balance.addView(value,new LinearLayout.LayoutParams(-1,Math.round(dp(30)*scale)));
        heading.addView(balance,new LinearLayout.LayoutParams(large?-1:Math.round(dp(128)*scale),-2));row.addView(heading);
        LinearLayout details=new LinearLayout(main);
        long given=main.budget.assigned(c,main.month),moved=main.budget.activity(c,main.month);
        TextView assigned=label("",11,main.muted,false),activity=label("",11,main.muted,false);
        assigned.setText(tint("Assigned  "+money(given),money(given),amountColour(given)));activity.setText(tint("Activity  "+money(moved),money(moved),amountColour(moved)));
        details.addView(assigned,new LinearLayout.LayoutParams(0,-2,1));activity.setGravity(Gravity.END);
        details.addView(activity,new LinearLayout.LayoutParams(0,-2,1));row.addView(details);
        // Progress: Set aside and debt payments by this month's Assigned; a balance by Available; by date toward the whole amount; refills (and weekly) toward this month's amount.
        if(c.target>0){String t=c.targetType;
            long goal=t.equals("Weekly")?Budget.weeklyGoal(c,main.month):c.target,base=t.equals("Monthly")||t.equals("Debt")?main.budget.assigned(c,main.month):t.equals("Balance")?available:t.equals("ByDate")?main.budget.carried(c,main.month)+main.budget.assigned(c,main.month):goal-need;
            progress(row,base,goal,status);row.addView(label(targetDescription(c),11,main.muted,false));
            boolean passed=t.equals("ByDate")&&Budget.dueFor(c,main.month)==null;
            boolean snoozed=c.snoozed.equals(main.month.toString());
            TextView state=snoozed?label("Target snoozed this month",12,main.muted,true):passed?label("Due date passed: it asks for nothing more",12,main.muted,true):label(need==0?"✓ Funded for this month":money(need)+" left to fund this month",12,need>0?main.amber:main.green,true);
            if(need==0&&!snoozed&&!passed)state.setContentDescription("Funded for this month");row.addView(state);
            // A target that was short when this month was last on screen and is funded now: its check mark pops, with a firm tap.
            String key=main.month+"/"+c.id;boolean funded=need==0&&!snoozed&&!passed;
            if(funded&&main.shownUnfunded.contains(key)&&main.month.equals(main.shownMonth)){state.setPivotX(0);pop(state);confirm(row);}
            if(funded)main.shownUnfunded.remove(key);else main.shownUnfunded.add(key);}
        Budget.Pace pace=main.budget.pace(c,main.month,LocalDate.now());
        if(pace!=null)row.addView(label("Spending faster than the month: "+pace.spent+"% spent, "+pace.elapsed+"% of the month gone",12,main.amber,false)); // the current month only
        if(!c.note.isEmpty())row.addView(label(c.note,12,main.muted,false));
        long upcoming=main.budget.upcoming(c,main.month);
        if(upcoming>0)row.addView(greyLine("Upcoming bills this month: "+money(upcoming),money(upcoming),outColour(upcoming),12));
        long onCredit=main.budget.creditOverspent(c,main.month);
        if(available<0&&onCredit>=-available)row.addView(label("Overspent on a credit card by "+money(-available)+": it becomes card debt unless you cover it",12,main.amber,true));
        else if(cover>0)row.addView(label("Overspent by "+money(cover)+" - tap to cover",12,main.red,true));
        else if(available<0)row.addView(label("Below zero by a refund or credit on the card: it carries on, with nothing to cover",12,main.amber,false));
        if(c.payment()){Budget.Account card=main.budget.account(c.cardAccount);
            if(card!=null){long owed=-main.budget.balance(card,false);
                row.addView(greyLine("Pays "+card.name+(owed>0?" · owed "+money(owed):" · paid off"),owed>0?money(owed):"",outColour(owed),12));}
            long charges=main.budget.cardCharges(c,main.month); // more debt, not overspending: assign money here to pay it off
            if(charges!=0)row.addView(greyLine("Interest and fees this month: "+money(charges),money(charges),outColour(charges),12));}
        row.setOnClickListener(v->categoryDetails(c));
    }
    void plan(){
        monthNoteRow();readyCard();
        String resetAt=main.prefs().getString("before_reset_at",null);
        if(resetAt!=null){LinearLayout r=card();
            r.addView(label("Budget reset on "+when(resetAt)+". Assign your money again by today's priorities.",13,main.muted,false));
            LinearLayout buttons=new LinearLayout(main);
            Button undo=button("Undo budget reset",this::undoPlanReset),keep=button("Keep",()->{main.prefs().edit().remove("before_reset").remove("before_reset_at").apply();
                main.render();});buttons.addView(undo,new LinearLayout.LayoutParams(0,-2,2));
            LinearLayout.LayoutParams kp=new LinearLayout.LayoutParams(0,-2,1);kp.setMargins(dp(8),0,0,0);
            buttons.addView(keep,kp);r.addView(buttons);}
        if(overspent()>0)main.content.addView(label(count(overspent(),"category","categories")+" overspent - tap to cover",14,main.red,true));
        main.content.addView(primary("Fund targets",this::autoAssign));LinkedHashSet<String> groups=new LinkedHashSet<>();
        for(Budget.Category c:main.budget.categories)if(!c.hidden)groups.add(c.group);
        Set<String> collapsed=collapsedGroups();
        for(String group:groups){boolean shut=collapsed.contains(group);long total=0;int n=0;
            for(Budget.Category c:main.budget.categories)if(!c.hidden&&c.group.equals(group)){total+=main.budget.available(c,main.month);n++;}
            groupHeader(group,shut,total,n);
            for(Budget.Category c:main.budget.categories)if(!c.hidden&&c.group.equals(group)&&(!shut||main.budget.toCover(c,main.month)>0))categoryCard(c);} // overspent stays in sight, folded or not
        main.content.addView(button("+ Add category",()->editCategory(null)));main.content.addView(button("Move money",this::move));
        List<Budget.Category> hidden=new ArrayList<>();long held=0;for(Budget.Category c:main.budget.categories)if(c.hidden){hidden.add(c);
            held+=main.budget.available(c,main.month);}
        if(!hidden.isEmpty()){main.content.addView(button((main.showHidden?"Collapse hidden categories (":"Show hidden categories (")+hidden.size()+")"+(held!=0?" · holds "+money(held):""),()->{main.showHidden=!main.showHidden;
                main.render();}));if(main.showHidden)for(Budget.Category c:hidden)categoryCard(c);}
    }
    // Groups folded away on Budget: a display choice on this phone (prefs "appearance"), not part of the budget or its backups.
    private Set<String> collapsedGroups(){return new HashSet<>(main.getSharedPreferences("appearance",0).getStringSet("collapsed_groups",new HashSet<>()));}
    /**
     * A held group heading: the group's edit screen. Its name, applied with Save (Budget.renameGroup: another group's name merges
     * them; folded stays folded); its categories, hidden ones too (tap one for its own edit form; ↑ ↓ reorder them at once); a new
     * category in it; and the whole group moved up or down on Budget (Budget.moveGroup, at once).
     */
    private void editGroup(String group){
        LinearLayout f=form();EditText name=field(f,"Group name",false);name.setText(group);name.setSelection(group.length());
        f.addView(label("Using another group's name puts the two together.",12,main.muted,false));
        TextView inside=heading("Categories in this group",15,main.ink);inside.setPadding(0,dp(14),0,dp(2));f.addView(inside);
        LinearLayout list=column();f.addView(list);AlertDialog[] d=new AlertDialog[1];
        // Leaving a category's form opened from here comes back here, in the group the category ended up in.
        java.util.function.Consumer<String> back=g->{if(hasGroup(g))editGroup(g);else if(hasGroup(group))editGroup(group);};
        f.addView(button("+ Add a category to this group",()->{d[0].dismiss();editCategory(null,group,back);}));
        Button up=button("Move group up",()->{}),down=button("Move group down",()->{});pair(f,up,down);
        Runnable[] fill=new Runnable[1];
        fill[0]=()->{list.removeAllViews();List<Budget.Category> in=new ArrayList<>();for(Budget.Category c:main.budget.categories)if(c.group.equals(group))in.add(c);
            for(int i=0;i<in.size();i++){Budget.Category c=in.get(i);String id=c.id;long available=main.budget.available(c,main.month);
                LinearLayout row=new LinearLayout(main);row.setGravity(Gravity.CENTER_VERTICAL);row.setMinimumHeight(dp(56));pressable(row);
                LinearLayout text=column();text.addView(label(c.name+(c.hidden?" · hidden":""),15,c.hidden?main.muted:main.ink,true));
                text.addView(label(money(available)+" available",12,amountColour(available),false));row.addView(text,new LinearLayout.LayoutParams(0,-2,1));
                row.addView(step("↑","Move "+c.name+" up",i>0,()->{if(main.change(()->main.budget.reorder(main.categoryById(id),-1)))fill[0].run();}),new LinearLayout.LayoutParams(dp(48),dp(48)));
                row.addView(step("↓","Move "+c.name+" down",i<in.size()-1,()->{if(main.change(()->main.budget.reorder(main.categoryById(id),1)))fill[0].run();}),new LinearLayout.LayoutParams(dp(48),dp(48)));
                TextView go=label("›",22,main.muted,false);go.setPadding(dp(8),0,dp(4),0);go.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);row.addView(go);
                row.setContentDescription(c.name+(c.hidden?", hidden, ":", ")+money(available)+" available. Double tap to edit.");
                row.setOnClickListener(v->{d[0].dismiss();editCategory(main.categoryById(id),group,back);});list.addView(row,new LinearLayout.LayoutParams(-1,-2));}
            List<String> shown=new ArrayList<>();for(Budget.Category c:main.budget.categories)if(!c.hidden&&!shown.contains(c.group))shown.add(c.group);
            int at=shown.indexOf(group);up.setEnabled(at>0);up.setAlpha(at>0?1f:0.4f);down.setEnabled(at>=0&&at<shown.size()-1);down.setAlpha(down.isEnabled()?1f:0.4f);};
        up.setOnClickListener(v->{if(main.change(()->main.budget.moveGroup(group,-1)))fill[0].run();});
        down.setOnClickListener(v->{if(main.change(()->main.budget.moveGroup(group,1)))fill[0].run();});
        fill[0].run();
        d[0]=dialog("Edit group",f,()->{String to=name.getText().toString();if(to.trim().equals(group))return; // unchanged: no merge with a group that differs only in capitals
            main.budget.renameGroup(group,to);Set<String> shut=collapsedGroups();
            if(shut.remove(group)){for(Budget.Category c:main.budget.categories)if(c.group.trim().equalsIgnoreCase(to.trim())){shut.add(c.group);break;}
                main.getSharedPreferences("appearance",0).edit().putStringSet("collapsed_groups",shut).apply();}});}
    /** A ↑ or ↓ in the group screen's rows, drawn bold (the font's arrows are thin); faded and off at the ends. */
    private View step(String symbol,String said,boolean enabled,Runnable action){boolean up=symbol.equals("↑");
        android.graphics.Paint pen=new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);pen.setColor(main.blue);pen.setStyle(android.graphics.Paint.Style.STROKE);
        pen.setStrokeWidth(dp(3));pen.setStrokeCap(android.graphics.Paint.Cap.ROUND);pen.setStrokeJoin(android.graphics.Paint.Join.ROUND);
        View b=new View(main){@Override protected void onDraw(android.graphics.Canvas c){float x=getWidth()/2f,y=getHeight()/2f,u=dp(1),d=up?-1:1,tip=y+d*8*u;
            c.drawLine(x,y-d*8*u,x,tip,pen);android.graphics.Path head=new android.graphics.Path();head.moveTo(x-6*u,tip-d*6*u);head.lineTo(x,tip);head.lineTo(x+6*u,tip-d*6*u);c.drawPath(head,pen);}};
        pressable(b);b.setClickable(true);b.setFocusable(true);b.setContentDescription(said);b.setEnabled(enabled);b.setAlpha(enabled?1f:0.3f);
        b.setAccessibilityDelegate(new View.AccessibilityDelegate(){@Override public void onInitializeAccessibilityNodeInfo(View v,android.view.accessibility.AccessibilityNodeInfo info){
            super.onInitializeAccessibilityNodeInfo(v,info);info.setClassName(Button.class.getName());}});
        b.setOnClickListener(v->{tick(v);action.run();});return b;}
    /** A group's ⋮: move the whole group (by its neighbours' names), edit it, or sort the groups A to Z (the default order). */
    private void groupMenu(String group,View at){List<String> order=main.budget.shownGroups();int i=order.indexOf(group),last=order.size()-1;if(i<0)return;
        List<MoveMenu.Choice> c=new ArrayList<>();
        if(i>1)c.add(new MoveMenu.Choice(MoveMenu.TOP,"Move to top",false,()->main.change(()->main.budget.moveGroupTo(group,0))));
        if(i>0)c.add(new MoveMenu.Choice(MoveMenu.UP,"Move above "+order.get(i-1),false,()->main.change(()->main.budget.moveGroupTo(group,i-1))));
        if(i<last)c.add(new MoveMenu.Choice(MoveMenu.DOWN,"Move below "+order.get(i+1),false,()->main.change(()->main.budget.moveGroupTo(group,i+1))));
        if(i<last-1)c.add(new MoveMenu.Choice(MoveMenu.BOTTOM,"Move to bottom",false,()->main.change(()->main.budget.moveGroupTo(group,last))));
        c.add(new MoveMenu.Choice(MoveMenu.EDIT,"Edit group",true,()->editGroup(group)));
        if(!main.budget.groupsSorted())c.add(new MoveMenu.Choice(MoveMenu.RESET,"Sort groups A to Z",true,()->main.change(()->main.budget.sortGroups())));
        MoveMenu.show(this,at,group,(i+1)+" of "+order.size()+" groups",c);}
    /** A group's heading: tap to fold its categories away or bring them back, hold for its edit screen. Shows the group's total Available (and, folded, how many). */
    private void groupHeader(String group,boolean shut,long total,int n){
        LinearLayout row=new LinearLayout(main);row.setGravity(Gravity.CENTER_VERTICAL);row.setMinimumHeight(dp(48));row.setPadding(dp(4),dp(8),dp(4),0);pressable(row);
        TextView arrow=label(shut?"▸":"▾",16,main.blue,true);arrow.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        row.addView(arrow,new LinearLayout.LayoutParams(dp(22),-2));
        TextView name=label(group,18,main.blue,true);row.addView(name,new LinearLayout.LayoutParams(0,-2,1));
        String sumText=(shut?count(n,"category","categories")+" · ":"")+money(total);TextView sum=label("",13,main.muted,true);
        sum.setText(tint(sumText,money(total),amountColour(total)));row.addView(sum);
        View[] dots=new View[1];dots[0]=MoveMenu.dots(this,main.blue,"Options for "+group,()->groupMenu(group,dots[0]));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(dp(48),dp(48));lp.setMarginEnd(-dp(10));row.addView(dots[0],lp);
        heading(row);row.setContentDescription(group+", "+(shut?"collapsed, ":"")+money(total)+" available. Double tap to "+(shut?"show":"hide")+" its categories.");
        row.setOnClickListener(v->{Set<String> now=collapsedGroups();if(!now.remove(group))now.add(group);tick(v);
            main.getSharedPreferences("appearance",0).edit().putStringSet("collapsed_groups",now).apply();main.render();});
        row.setOnLongClickListener(v->{editGroup(group);return true;});
        row.setAccessibilityDelegate(new View.AccessibilityDelegate(){@Override public void onInitializeAccessibilityNodeInfo(View v,android.view.accessibility.AccessibilityNodeInfo info){
            super.onInitializeAccessibilityNodeInfo(v,info);info.addAction(new android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_LONG_CLICK,"edit group"));}});
        main.content.addView(row,new LinearLayout.LayoutParams(-1,-2));}
    /** The month's note (top of Budget): the text with a small Edit, or a small "+ Note" when there's none. */
    private void monthNoteRow(){
        String note=main.budget.monthNote(main.month);LinearLayout row=new LinearLayout(main);row.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(note.isEmpty()?new View(main):label(note,13,main.ink,false),new LinearLayout.LayoutParams(0,-2,1));
        Button edit=button(note.isEmpty()?"+ Note for "+main.month.format(DateTimeFormatter.ofPattern("MMMM")):"Edit",this::editMonthNote);
        edit.setTextSize(12);edit.setBackground(bg(Color.TRANSPARENT));
        edit.setContentDescription(note.isEmpty()?"Add a note for this month":"Edit this month's note");
        row.addView(edit,new LinearLayout.LayoutParams(-2,-2));main.content.addView(row);
    }
    private void editMonthNote(){
        YearMonth m=main.month;LinearLayout f=form();
        f.addView(label("A reminder for "+m.format(DateTimeFormatter.ofPattern("MMMM yyyy"))+", shown at the top of Budget. Up to "+Budget.MONTH_NOTE_MAX+" characters; leave it empty to remove it.",13,main.muted,false));
        EditText text=field(f,"Note for this month",false);text.setSingleLine(false);text.setMaxLines(5);
        text.setFilters(new InputFilter[]{new InputFilter.LengthFilter(Budget.MONTH_NOTE_MAX)});text.setText(main.budget.monthNote(m));
        dialog("Month note",f,()->main.budget.setMonthNote(m,text.getText().toString()));
    }
    /** A category's actions, in a sheet: money, the category itself, its place in the group, and hiding or deleting it. */
    void categoryDetails(Budget.Category c){
        String id=c.id;long available=main.budget.available(c,main.month);
        List<MoveMenu.Choice> money=new ArrayList<>(),self=new ArrayList<>(),place=new ArrayList<>(),away=new ArrayList<>();
        if(main.budget.toCover(c,main.month)>0)money.add(MoveMenu.Choice.danger(MoveMenu.COVER,"Cover overspending",()->cover(id)));
        money.add(new MoveMenu.Choice(MoveMenu.MONEY,"Assign or return money",false,()->assign(c)));
        money.add(new MoveMenu.Choice(MoveMenu.SWAP,"Move money",false,this::move));
        self.add(new MoveMenu.Choice(MoveMenu.EDIT,"Edit category and target",false,()->editCategory(c)));
        self.add(new MoveMenu.Choice(MoveMenu.LIST,"View transactions",false,()->{main.clearFilters();main.tab="Spending";main.categoryFilter=id;main.render();}));
        if(c.target>0){boolean snoozed=c.snoozed.equals(main.month.toString());String m=main.month.toString();
            self.add(new MoveMenu.Choice(MoveMenu.SNOOZE,snoozed?"Unsnooze target":"Snooze target this month",false,()->main.change(()->main.categoryById(id).snoozed=snoozed?"":m)));}
        boolean pinned=c.pinned;
        self.add(new MoveMenu.Choice(MoveMenu.PIN,pinned?"Unpin from Home":"Pin to Home",false,
            ()->{if(main.change(()->main.budget.pin(main.categoryById(id),!pinned)))toast(pinned?"Unpinned from Home.":"Pinned to Home: it shows under Priority categories.");}));
        Budget.Category above=neighbour(c,-1),below=neighbour(c,1); // the ones reorder() swaps with: in its group, hidden ones too
        if(above!=null)place.add(new MoveMenu.Choice(MoveMenu.UP,"Move above "+above.name,false,()->reorder(id,-1)));
        if(below!=null)place.add(new MoveMenu.Choice(MoveMenu.DOWN,"Move below "+below.name,false,()->reorder(id,1)));
        away.add(new MoveMenu.Choice(c.hidden?MoveMenu.SHOW:MoveMenu.HIDE,c.hidden?"Unhide":"Hide",false,()->hide(id,!c.hidden)));
        if(!c.payment())away.add(MoveMenu.Choice.danger(MoveMenu.DELETE,"Delete category",()->deleteCategory(id)));
        String about=c.group+(c.hidden?" · hidden":"")+" · "+money(available)+" available";
        MoveMenu.sheet(this,c.name,about,Arrays.asList(money,self,place,away),null);
    }
    /** The category next to [c] in its group (hidden ones too), up (-1) or down (1); null at the end. */
    private Budget.Category neighbour(Budget.Category c,int direction){List<Budget.Category> all=main.budget.categories;
        for(int j=all.indexOf(c)+direction;j>=0&&j<all.size();j+=direction)if(all.get(j).group.equals(c.group))return all.get(j);return null;}
    private void reorder(String id,int direction){main.change(()->{if(!main.budget.reorder(main.categoryById(id),direction))throw new IllegalArgumentException(direction<0?"Already first in its group.":"Already last in its group.");});}
    private void hide(String id,boolean hidden){boolean[] unpinned={false};
        if(main.change(()->unpinned[0]=main.budget.setHidden(main.categoryById(id),hidden)))toast(hidden?"Hidden. It's at the bottom of Budget; its money still counts.":unpinned[0]?"Back in your plan. Home already has "+Budget.PINS+" pinned, so it was unpinned.":"Back in your plan.");}
    private void deleteCategory(String id){
        Budget.Category c=main.budget.category(id);if(c==null)return;
        if(!main.budget.used(c)){Ui.danger(new AlertDialog.Builder(main).setTitle("Delete "+c.name+"?").setMessage("It has no transactions or assigned money.")
                .setNegativeButton("Cancel",null)
                .setPositiveButton("Delete",(d,w)->main.deleteWithUndo("Category deleted",()->main.budget.deleteCategory(main.categoryById(id),null))).show());return;}
        List<Budget.Category> others=new ArrayList<>();for(Budget.Category o:main.budget.categories)if(o!=c&&!o.payment())others.add(o);
        if(others.isEmpty()){toast("Add another category first, to take its transactions and money.");return;}
        java.util.function.IntConsumer chosen=n->{String into=others.get(n).id;
            int count=main.budget.entriesIn(c);
            Ui.danger(new AlertDialog.Builder(main).setTitle("Delete "+c.name+"?")
                .setMessage("Its "+count(count,"transaction","transactions")+" and the money assigned to it in every month move to "+others.get(n).name+". Bills from Planner then suggest "+others.get(n).name+" too. Past months' balances in "+others.get(n).name+" may change.")
                .setNegativeButton("Cancel",null)
                    .setPositiveButton("Move and delete",(d2,w)->main.deleteWithUndo("Category deleted",()->main.budget.deleteCategory(main.categoryById(id),main.categoryById(into)))).show());};
        List<MoveMenu.Choice> into=new ArrayList<>();for(int i=0;i<others.size();i++){int n=i;Budget.Category o=others.get(i);
            into.add(categoryPick(o,main.budget.available(o,main.month),()->chosen.accept(n)));}
        MoveMenu.sheet(this,"Move "+c.name+" to…","Its transactions and money go to the category you pick, then it's deleted.",Collections.singletonList(into),null);
    }
    /** Cover overspending: pick the envelope the money comes from (categories with money, or To budget). */
    void cover(String id){
        Budget.Category c=main.budget.category(id);if(c==null)return;long missing=main.budget.toCover(c,main.month);if(missing<=0)return;
        List<Budget.Category> sources=new ArrayList<>();
        for(Budget.Category o:main.budget.categories)if(o!=c&&main.budget.available(o,main.month)>0)sources.add(o);
        sources.sort((a,b)->Long.compare(main.budget.available(b,main.month),main.budget.available(a,main.month)));
        // Each choice: where from (null = To budget), the later month whose assignment it is (null = this month), and how much it has.
        List<String> labels=new ArrayList<>();List<Budget.Category> froms=new ArrayList<>();List<YearMonth> laters=new ArrayList<>();List<Long> has=new ArrayList<>();
        long ready=main.budget.spendableFrom(main.month);if(ready>0){labels.add("To budget ("+money(ready)+")");froms.add(null);laters.add(null);has.add(ready);}
        for(Budget.Category o:sources){long a=main.budget.available(o,main.month);labels.add(o.name+" ("+money(a)+")");froms.add(o);laters.add(null);has.add(a);}
        // Money already assigned in later months (from this month on, while To budget isn't below zero): Budget.coverFromFuture.
        if(!main.month.isBefore(YearMonth.now())&&ready>=0)for(YearMonth later:main.budget.assignedAfter(main.month).keySet())
            for(Budget.Category o:main.budget.categories){long back=o==c?0:main.budget.futureCover(o,later);if(back<=0)continue;
                labels.add(o.name+" in "+later.format(DateTimeFormatter.ofPattern("MMMM"))+" ("+money(back)+" assigned)");froms.add(o);laters.add(later);has.add(back);}
        if(labels.isEmpty()){toast("No category has money to move. Record income or assign money first.");return;}
        // Sheet rows: To budget, then categories with money, then later months' assignments, each with what it can give.
        List<MoveMenu.Choice> now=new ArrayList<>(),cats=new ArrayList<>(),ahead=new ArrayList<>();
        for(int i=0;i<labels.size();i++){int n=i;Budget.Category from=froms.get(i);YearMonth later=laters.get(i);Runnable pick=()->coverFrom(id,c,missing,froms,laters,has,n);
            if(from==null)now.add(new MoveMenu.Choice(MoveMenu.MONEY,"To budget",false,pick).sub("Money without a job yet").detail(money(has.get(i)),amountColour(has.get(i))));
            else if(later==null)cats.add(categoryPick(from,has.get(i),pick));
            else ahead.add(categoryPick(from,has.get(i),pick).sub("Assigned in "+later.format(DateTimeFormatter.ofPattern("MMMM yyyy"))));}
        MoveMenu.sheet(this,"Cover "+money(missing)+" for "+c.name,"Take the money from:",Arrays.asList(now,cats,ahead),null);
    }
    /** Cover's confirmation for the source picked in its sheet ([n] in the lists cover() built). */
    private void coverFrom(String id,Budget.Category c,long missing,List<Budget.Category> froms,List<YearMonth> laters,List<Long> has,int n){{
            Budget.Category from=froms.get(n);YearMonth later=laters.get(n);long amount=Math.min(missing,has.get(n));String fromId=from==null?null:from.id;
            String laterName=later==null?"":later.format(DateTimeFormatter.ofPattern("MMMM yyyy"));
            String source=from==null?"To budget":later==null?from.name:from.name+"'s money assigned in "+laterName;
            String note=later==null?"":"\n\n"+from.name+" will have "+money(main.budget.assigned(from,later)-amount)+" assigned in "+laterName+": assign it again when more money comes in.";
            tracked(new AlertDialog.Builder(main).setTitle("Cover overspending")
                .setMessage("Move "+money(amount)+" from "+source+" to "+c.name+"?"+(amount<missing?"\n\nThat covers part of it; "+money(missing-amount)+" stays overspent.":"")+note)
                .setNegativeButton("Cancel",null)
                    .setPositiveButton("Cover",(d2,w)->main.change(()->{if(fromId==null)main.budget.assign(main.categoryById(id),main.month,amount);
                        else if(later==null)main.budget.move(main.categoryById(fromId),main.categoryById(id),main.month,amount);
                        else main.budget.coverFromFuture(main.categoryById(fromId),later,main.categoryById(id),main.month,amount);})));
        }
    }
    private void assign(Budget.Category c){
        LinearLayout f=form();long spare=main.budget.spendable(main.month);TextView head=label("",16,main.blue,true);head.setText(tint(money(spare)+" to budget",money(spare),amountColour(spare)));f.addView(head);
        long most=main.budget.spendableFrom(main.month);if(most<spare)f.addView(greyLine("Up to "+money(Math.max(0,most))+" can be assigned here: a later month needs the rest.",money(Math.max(0,most)),main.ink,13));
        long now=main.budget.assigned(c,main.month);
        f.addView(greyLine("Assigned this month: "+money(now)+". A positive amount adds money; a negative one returns it.",money(now),amountColour(now),13));
        EditText amount=field(f,"Amount ("+code()+")",true);TextView result=label("",13,main.blue,true);f.addView(result);
        onText(amount,()->{try{long after=now+Budget.parse(amount.getText().toString());result.setText(tint("Assigned becomes "+money(after),money(after),amountColour(after)));}catch(Exception e){result.setText("");}});
        // Quick amounts: each fills in the change to this month's Assigned.
        f.addView(label("Quick amounts",12,main.muted,true));YearMonth last=main.month.minusMonths(1);
        long lastAssigned=main.budget.assigned(c,last),spentLast=main.budget.spent(c,last),average=main.budget.averageSpent(c,main.month),averageAssigned=main.budget.averageAssigned(c,main.month);
        List<String> names=new ArrayList<>();List<Long> changes=new ArrayList<>();
        long underfunded=main.budget.fundNeed(c,main.month);if(underfunded>0){names.add("Underfunded: "+money(underfunded));
            changes.add(underfunded);} // what Fund targets would give it
        names.add("Assigned last month: "+money(lastAssigned));changes.add(lastAssigned-now);
        names.add("Average assigned, last 3 months: "+money(averageAssigned));changes.add(averageAssigned-now);
        if(!c.payment()){names.add("Spent last month: "+money(spentLast));changes.add(spentLast-now);
            names.add("Average spent, last 3 months: "+money(average));changes.add(average-now);} // a card payment isn't spending
        long available=main.budget.available(c,main.month),toZero=main.budget.resetAvailableChange(c,main.month);
        if(toZero!=0){names.add((toZero==-available?"Reset available to zero":"Cover overspending")+" (now "+money(available)+")");
            changes.add(toZero);} // not offered in a future month when carried money would have to go
        long reset=main.budget.resetChange(c,main.month);
        if(reset!=0){names.add(reset==-now?"Reset assigned to zero":"Reset assigned: return what's left, "+money(-reset));changes.add(reset);}
        for(int i=0;i<names.size();i++){long change=changes.get(i);Button b=button(names.get(i),()->amount.setText(decimal(change)));
            b.setTextSize(12);f.addView(b);}
        dialog("Assign to "+c.name,f,()->main.budget.assign(main.categoryById(c.id),main.month,Budget.parse(amount.getText().toString())));
    }
    private String[] availableNames(){return main.budget.categories.stream().map(c->c.name+" ("+money(main.budget.available(c,main.month))+")").toArray(String[]::new);}
    private void move(){if(main.budget.categories.size()<2){toast("Create two categories first.");return;}LinearLayout f=form();
        Spinner from=spinner(f,"From",availableNames(),0),to=spinner(f,"To",availableNames(),1);EditText amount=field(f,"Amount ("+code()+")",true);
        dialog("Move money",f,()->main.budget.move(main.budget.categories.get(from.getSelectedItemPosition()),main.budget.categories.get(to.getSelectedItemPosition()),main.month,Budget.cents(amount.getText().toString())));}
    private void autoAssign(){long remaining=Math.max(0,main.budget.spendableFrom(main.month));long total=0;
        for(Budget.Category c:main.budget.categories)if(!c.hidden)total+=main.budget.fundNeed(c,main.month);long fund=Math.min(remaining,total);
        if(fund==0){toast("No available money or underfunded targets.");return;}new AlertDialog.Builder(main).setTitle("Fund targets")
            .setMessage("Assign "+money(fund)+" to underfunded targets and upcoming bills, earliest due first?").setNegativeButton("Cancel",null)
            .setPositiveButton("Fund",(d,w)->{try{main.commit(()->{long left=Math.max(0,main.budget.spendableFrom(main.month));
                    for(Budget.Category c:main.budget.fundOrder(main.month)){if(c.hidden)continue;
                        long n=Math.min(left,main.budget.fundNeed(c,main.month));if(n>0){main.budget.assign(c,main.month,n);left-=n;}}});
                main.render();}catch(Exception e){toast(e.getMessage());}}).show();}
    // Target kinds as saved (targetType), in the form's order.
    private static final String[] TARGET_TYPES={"Refill","Monthly","Balance","Weekly","ByDate","Debt"};
    private static final int[] REPEAT_MONTHS={0,3,6,12};
    private void editCategory(Budget.Category existing){editCategory(existing,"Everyday",null);}
    /** Whether any category (hidden ones too) is in [group]. */
    private boolean hasGroup(String group){for(Budget.Category c:main.budget.categories)if(c.group.equals(group))return true;return false;}
    /** [newGroup]: the group a new category starts in. [back]: given the category's group when the person leaves the form (saved or not). */
    private void editCategory(Budget.Category existing,String newGroup,java.util.function.Consumer<String> back){
        LinearLayout f=form();EditText name=field(f,"Category name",false);
        AutoCompleteTextView group=suggestField(f,"Group (Bills, Everyday, Savings...)",()->main.budget.groups());
        String[] types={"Refill each month","Set aside each month","Save toward a balance","Weekly amount","Save for spending by a date","Monthly debt payment"};
        Spinner type=spinner(f,"Target behavior",types,existing==null?0:Math.max(0,Arrays.asList(TARGET_TYPES).indexOf(existing.targetType)));
        TextView explanation=label("",13,main.muted,false);f.addView(explanation);EditText amount=field(f,"Target amount (0 for none)",true);
        LinearLayout deadline=column();f.addView(deadline);EditText due=field(deadline,"Due month (YYYY-MM, optional)",false);
        // Weekly: the weekday and whether each week tops up or adds a fresh amount. By date: the date and an optional repeat.
        LinearLayout weekly=column();f.addView(weekly);String[] days=new String[7];for(int i=0;i<7;i++)days[i]=dayName(i+1);
        Spinner weekday=spinner(weekly,"Every",days,existing==null?0:existing.weekday-1);
        Spinner weeklyMode=spinner(weekly,"Each week",new String[]{"Refill up to the amount","Set aside another amount"},existing==null||existing.weeklyRefill?0:1);
        LinearLayout byDate=column();f.addView(byDate);byDate.addView(label("Due date",12,main.muted,true));
        EditText dueDate=dateField(byDate,existing!=null&&!existing.dueDate.isEmpty()?existing.dueDate:LocalDate.now().plusMonths(3).toString(),true);
        int repeatAt=0;for(int i=0;i<REPEAT_MONTHS.length;i++)if(existing!=null&&existing.repeatMonths==REPEAT_MONTHS[i])repeatAt=i;
        Spinner repeat=spinner(byDate,"After the date",new String[]{"Stop asking","Repeat every 3 months","Repeat every 6 months","Repeat every 12 months"},repeatAt);
        LinearLayout dayRow=column();f.addView(dayRow);EditText dueDay=field(dayRow,"Due day of the month (1-31, optional)",false);
        dueDay.setInputType(InputType.TYPE_CLASS_NUMBER);dayRow.addView(label("Fund targets funds the earliest due first.",12,main.muted,false));
        EditText note=field(f,"Note (optional)",false);
        if(existing!=null){name.setText(existing.name);group.setText(existing.group,false);amount.setText(decimal(existing.target));
            due.setText(existing.due);dueDay.setText(existing.dueDay>0?String.valueOf(existing.dueDay):"");
            note.setText(existing.note);}else group.setText(newGroup,false);
        Runnable describe=()->{String t=TARGET_TYPES[type.getSelectedItemPosition()];
            deadline.setVisibility(t.equals("Balance")?View.VISIBLE:View.GONE);weekly.setVisibility(t.equals("Weekly")?View.VISIBLE:View.GONE);
            byDate.setVisibility(t.equals("ByDate")?View.VISIBLE:View.GONE);
            dayRow.setVisibility(t.equals("Refill")||t.equals("Monthly")||t.equals("Debt")?View.VISIBLE:View.GONE);
            amount.setHint(t.equals("Weekly")?"Amount each week (0 for none)":t.equals("ByDate")?"Amount needed by the date (0 for none)":t.equals("Debt")?"Payment each month (0 for none)":"Target amount (0 for none)");
            explanation.setText(new String[]{"Top up what remained from last month. Example: a $500 target with $100 left asks for $400. Spending this month does not restart the target.","Add a fresh amount every month. Example: set aside $100 for repairs, even if $300 remains from earlier months.","Build up to a total balance. Example: a $1,200 goal with $300 saved and 3 months remaining asks for $300 this month. A due month is optional.",
                "An amount every week on the day you choose. Example: $40 every Monday asks for $200 in a month with 5 Mondays, $160 with 4. Refill up to counts what's left from last month; Set aside another adds the full amount each week.","Save money to spend by a date, such as a $250 bill due 15 January. What's still needed is spread evenly over the months up to and including the due month. After the date it stops asking, or starts again for the next date if it repeats.","A fixed payment toward a debt every month, such as $300 on a loan. It asks for the full amount each month, like Set aside, even if money remains from earlier months."}[type.getSelectedItemPosition()]);};
        describe.run();
        type.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onItemSelected(AdapterView<?> p,View v,int position,long id){describe.run();}public void onNothingSelected(AdapterView<?> p){}});
        // Groups already in the plan are suggested; a group typed in other capitals is saved in its existing spelling ("bills" -> "Bills").
        // The target amount takes quick maths; starting with + adds to the current target.
        String[] savedGroup={existing==null?newGroup:existing.group};
        dialog(existing==null?"New category":"Edit category & target",f,()->{
            String n=required(name),g=main.budget.existingGroup(required(group),existing==null?null:main.budget.category(existing.id));
            for(Budget.Category c:main.budget.categories)if((existing==null||!c.id.equals(existing.id))&&c.name.equalsIgnoreCase(n))throw new IllegalArgumentException("That category already exists.");
            String t=TARGET_TYPES[type.getSelectedItemPosition()];
            long target=amount.getText().toString().trim().isEmpty()?0:Budget.adjust(amount.getText().toString(),existing==null?0:existing.target);
            if(target<0)throw new IllegalArgumentException("Target cannot be negative.");
            String dueMonth=t.equals("Balance")?due.getText().toString().trim():"";
            if(!dueMonth.isEmpty()){YearMonth m=YearMonth.parse(dueMonth);
                if(m.getYear()<1900||m.getYear()>2100)throw new IllegalArgumentException("Choose a due year between 1900 and 2100.");}
            Budget.Category c=existing==null?new Budget.Category(n):main.budget.category(existing.id);c.name=n;c.group=g;c.target=target;
            c.targetType=t;c.due=dueMonth;String day=dueDay.getText().toString().trim();int d=0;
            if(dayRow.getVisibility()==View.VISIBLE&&!day.isEmpty()){try{d=Integer.parseInt(day);}catch(NumberFormatException e){d=-1;}
                if(d<1||d>31)throw new IllegalArgumentException("Enter a due day from 1 to 31, or leave it empty.");}c.dueDay=d;
            c.weekday=weekday.getSelectedItemPosition()+1;c.weeklyRefill=weeklyMode.getSelectedItemPosition()==0;
            c.dueDate=t.equals("ByDate")?LocalDate.parse((String)dueDate.getTag()).toString():"";
            c.repeatMonths=t.equals("ByDate")?REPEAT_MONTHS[repeat.getSelectedItemPosition()]:0;
            c.note=note.getText().toString().trim();if(existing==null)main.budget.categories.add(c);savedGroup[0]=g;},
            back==null?null:()->back.accept(savedGroup[0]));
    }
}
