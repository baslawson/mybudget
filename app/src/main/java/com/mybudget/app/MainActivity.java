package com.mybudget.app;

import android.app.*;
import android.os.Bundle;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.Drawable;
import android.content.res.ColorStateList;
import android.text.*;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import java.text.NumberFormat;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class MainActivity extends Activity {
    private Budget budget=new Budget();
    private int ink,blue,muted,red,amber,green,canvas,surface,buttonSurface,primary;
    private String themeMode;
    private LinearLayout root,content;
    private String tab="Home",search="",accountFilter="";
    private String previousTab="Home";
    private YearMonth month=YearMonth.now();
    private boolean storageReadable=true;
    private final NumberFormat currency=NumberFormat.getCurrencyInstance(Locale.forLanguageTag("en-AU"));
    @Override public void onCreate(Bundle state){
        themeMode=getSharedPreferences("appearance",0).getString("theme","Dark");
        boolean dark=themeMode.equals("Dark")||(themeMode.equals("Auto")&&(getResources().getConfiguration().uiMode&Configuration.UI_MODE_NIGHT_MASK)==Configuration.UI_MODE_NIGHT_YES);
        setTheme(dark?R.style.AppTheme:R.style.AppTheme_Light);
        super.onCreate(state);
        if(dark){ink=Color.rgb(237,241,250);blue=Color.rgb(179,191,255);muted=Color.rgb(171,181,201);red=Color.rgb(255,142,150);amber=Color.rgb(245,198,110);green=Color.rgb(117,219,177);canvas=Color.rgb(17,21,31);surface=Color.rgb(32,38,53);buttonSurface=Color.rgb(43,52,78);primary=Color.rgb(65,80,159);}
        else{ink=Color.rgb(27,39,62);blue=Color.rgb(57,77,165);muted=Color.rgb(111,121,140);red=Color.rgb(178,51,55);amber=Color.rgb(159,104,12);green=Color.rgb(32,115,85);canvas=Color.rgb(243,245,250);surface=Color.WHITE;buttonSurface=Color.rgb(231,235,249);primary=blue;}
        getWindow().getDecorView().setSystemUiVisibility(dark?0:View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        if(state!=null){tab=state.getString("tab","Home");previousTab=state.getString("previousTab","Home");search=state.getString("search","");accountFilter=state.getString("accountFilter","");month=YearMonth.parse(state.getString("month",YearMonth.now().toString()));}
        load();if(storageReadable)render();
    }
    @Override protected void onSaveInstanceState(Bundle state){state.putString("tab",tab);state.putString("previousTab",previousTab);state.putString("search",search);state.putString("accountFilter",accountFilter);state.putString("month",month.toString());super.onSaveInstanceState(state);}
    private void options(View anchor){
        PopupMenu menu=new PopupMenu(this,anchor);menu.getMenu().add("Settings");
        menu.setOnMenuItemClickListener(item->{if(!tab.equals("Settings"))previousTab=tab;tab="Settings";render();return true;});menu.show();
    }
    private void closeSettings(){tab=previousTab;render();}
    @Override public void onBackPressed(){if(tab.equals("Settings"))closeSettings();else super.onBackPressed();}
    private void settings(){
        content.addView(label("Appearance",18,blue,true));LinearLayout appearance=card();
        appearance.addView(label("Theme",20,ink,true));appearance.addView(label("Choose Light, Dark, or follow your device automatically.",14,muted,false));
        appearance.addView(button("Theme: "+themeMode,this::chooseTheme));
    }
    private void chooseTheme(){
        String[] modes={"Light","Dark","Auto"};int selected=Arrays.asList(modes).indexOf(themeMode);
        new AlertDialog.Builder(this).setTitle("Appearance").setSingleChoiceItems(new String[]{"Light","Dark","Auto (follow device)"},selected,(dialog,which)->{
            String chosen=modes[which];if(chosen.equals(themeMode)){dialog.dismiss();return;}
            if(!getSharedPreferences("appearance",0).edit().putString("theme",chosen).commit()){toast("Could not save your theme preference.");return;}
            dialog.dismiss();recreate();
        }).setNegativeButton("Cancel",null).show();
    }
    private int dp(int n){return(int)(n*getResources().getDisplayMetrics().density);}
    private String money(long cents){return currency.format(java.math.BigDecimal.valueOf(cents,2));}
    private String decimal(long cents){return java.math.BigDecimal.valueOf(cents,2).toPlainString();}
    private LinearLayout column(){LinearLayout v=new LinearLayout(this);v.setOrientation(LinearLayout.VERTICAL);return v;}
    private TextView label(String text,int size,int color,boolean bold){TextView v=new TextView(this);v.setText(text);v.setTextSize(size);v.setTextColor(color);v.setPadding(0,dp(4),0,dp(4));if(bold)v.setTypeface(null,Typeface.BOLD);return v;}
    private GradientDrawable bg(int color){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(16));return d;}
    private LinearLayout card(){LinearLayout v=column();v.setPadding(dp(14),dp(10),dp(14),dp(10));v.setBackground(bg(surface));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,dp(4),0,dp(4));content.addView(v,p);return v;}
    private Button button(String text,Runnable action){Button b=new Button(this);b.setText(text);b.setAllCaps(false);b.setTextSize(13);b.setTextColor(blue);b.setBackground(bg(buttonSurface));b.setStateListAnimator(null);b.setElevation(0);b.setMinHeight(dp(48));b.setMinimumHeight(dp(48));b.setMinWidth(0);b.setMinimumWidth(0);b.setPadding(dp(12),dp(8),dp(12),dp(8));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,dp(4),0,dp(4));b.setLayoutParams(p);b.setOnClickListener(v->action.run());return b;}
    private void progress(LinearLayout parent,long funded,long goal,int color){ProgressBar bar=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);bar.setMax(100);bar.setProgress((int)Math.max(0,Math.min(100,funded*100/Math.max(1,goal))));bar.setProgressTintList(ColorStateList.valueOf(color));bar.setProgressBackgroundTintList(ColorStateList.valueOf(buttonSurface));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(6));p.setMargins(0,dp(5),0,dp(5));parent.addView(bar,p);}
    private String targetDescription(Budget.Category c){if(c.targetType.equals("Monthly"))return "Set aside "+money(c.target)+" each month";if(c.targetType.equals("Balance"))return "Save to "+money(c.target)+(c.due.isEmpty()?"":" by "+YearMonth.parse(c.due).format(DateTimeFormatter.ofPattern("MMM yyyy")));return "Refill to "+money(c.target)+" each month";}
    private void render(){
        root=column();root.setBackgroundColor(canvas);root.setPadding(dp(16),dp(12),dp(16),dp(8));setContentView(root);
        root.setOnApplyWindowInsetsListener((v,i)->{root.setPadding(dp(16),i.getSystemWindowInsetTop()+dp(8),dp(16),i.getSystemWindowInsetBottom()+dp(4));return i;});
        LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);
        ImageView logo=new ImageView(this);logo.setImageResource(R.drawable.brand_mark);logo.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);LinearLayout.LayoutParams logoSize=new LinearLayout.LayoutParams(dp(40),dp(40));logoSize.setMargins(0,0,dp(12),0);header.addView(logo,logoSize);
        LinearLayout brand=column();TextView wordmark=label("MyBudget",21,ink,true);wordmark.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));wordmark.setLetterSpacing(0.01f);wordmark.setPadding(0,0,0,0);
        SpannableString brandName=new SpannableString("MyBudget");brandName.setSpan(new android.text.style.ForegroundColorSpan(blue),0,2,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);wordmark.setText(brandName);brand.addView(wordmark);
        TextView tagline=label("Your money. Your plan.",11,muted,false);tagline.setPadding(0,dp(2),0,0);brand.addView(tagline);header.addView(brand,new LinearLayout.LayoutParams(0,-2,1));
        Button overflow=button("\u22ee",()->{});overflow.setContentDescription("More options");overflow.setTextSize(26);overflow.setMinWidth(0);overflow.setMinimumWidth(0);overflow.setPadding(0,0,0,0);overflow.setBackground(bg(Color.TRANSPARENT));overflow.setOnClickListener(v->options(v));header.addView(overflow,new LinearLayout.LayoutParams(dp(48),dp(48)));root.addView(header);root.addView(label(tab,28,ink,true));
        LinearLayout months=new LinearLayout(this);months.setGravity(Gravity.CENTER_VERTICAL);Button previous=button("\u2039",()->{month=month.minusMonths(1);render();});previous.setTextSize(26);previous.setContentDescription("Previous month");previous.setBackground(bg(Color.TRANSPARENT));months.addView(previous,new LinearLayout.LayoutParams(dp(48),dp(48)));TextView title=label(month.format(DateTimeFormatter.ofPattern("MMMM yyyy")),16,ink,true);title.setGravity(Gravity.CENTER);months.addView(title,new LinearLayout.LayoutParams(0,-2,1));Button next=button("\u203a",()->{month=month.plusMonths(1);render();});next.setTextSize(26);next.setContentDescription("Next month");next.setBackground(bg(Color.TRANSPARENT));months.addView(next,new LinearLayout.LayoutParams(dp(48),dp(48)));if(!tab.equals("Settings"))root.addView(months);
        ScrollView scroll=new ScrollView(this);content=column();scroll.addView(content);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        switch(tab){case"Settings":settings();break;case"Home":home();break;case"Plan":plan();break;case"Spending":spending();break;case"Accounts":accounts();break;default:reflect();}
        if(tab.equals("Settings")){root.addView(button("Back",this::closeSettings));return;}
        LinearLayout nav=new LinearLayout(this);nav.setPadding(0,dp(6),0,0);String[] names={"Home","Plan","Spending","Accounts","Reflect"};int[] icons={R.drawable.nav_home,R.drawable.nav_plan,R.drawable.nav_spending,R.drawable.nav_accounts,R.drawable.nav_reflect};for(int i=0;i<names.length;i++){String name=names[i];boolean selected=tab.equals(name);Button b=button(name,()->{tab=name;accountFilter="";render();});b.setTextSize(10);b.setPadding(dp(2),dp(7),dp(2),dp(5));b.setBackground(bg(selected?primary:Color.TRANSPARENT));b.setTextColor(selected?Color.WHITE:muted);b.setSelected(selected);b.setContentDescription(name+(selected?", selected":""));if(selected)b.setTypeface(null,Typeface.BOLD);Drawable icon=getDrawable(icons[i]).mutate();icon.setTint(selected?Color.WHITE:muted);icon.setBounds(0,0,dp(20),dp(20));b.setCompoundDrawables(null,icon,null,null);b.setCompoundDrawablePadding(dp(4));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(60),1);p.setMargins(dp(1),0,dp(1),0);nav.addView(b,p);}root.addView(nav);
    }
    private void readyCard(){LinearLayout c=card();c.setBackground(bg(primary));c.addView(label("READY TO ASSIGN",11,Color.WHITE,true));c.addView(label(money(budget.spendable(month)),30,Color.WHITE,true));long future=budget.futureAssigned(month);c.addView(label(future>0?money(future)+" reserved in future months":"Give the money you have a purpose.",12,Color.WHITE,false));}
    private int overspent(){int n=0;for(Budget.Category c:budget.categories)if(budget.available(c,month)<0)n++;return n;}
    private void home(){
        readyCard();content.addView(button("+ Add transaction",()->transaction(null)));
        if(budget.accounts.isEmpty()){LinearLayout c=card();c.addView(label("Start with the money you have",21,ink,true));c.addView(label("Add your bank, savings or cash account and its current balance. Then assign that money in your plan.",15,muted,false));c.addView(button("Add your first account",this::addAccount));}
        if(overspent()>0){LinearLayout c=card();c.addView(label("Cover "+overspent()+" overspent categories",19,red,true));c.addView(label("Move money to cover spending before trusting other available balances.",14,muted,false));c.addView(button("Review plan",()->{tab="Plan";render();}));}
        long need=0;for(Budget.Category c:budget.categories)need+=budget.needed(c,month);
        LinearLayout progress=card();progress.addView(label("Your funding progress",19,ink,true));progress.addView(label(money(need)+" still needed this month",16,need>0?amber:green,true));progress.addView(label("Targets tell you what to fund. They do not create money.",14,muted,false));
        content.addView(label("Your priorities",20,ink,true));int count=0;for(Budget.Category c:budget.categories)if(c.target>0){count++;categoryCard(c);}if(count==0)content.addView(label("Add targets in Plan for bills, everyday spending and future goals.",15,muted,false));
    }
    private void categoryCard(Budget.Category c){
        LinearLayout row=card();long available=budget.available(c,month),need=budget.needed(c,month);int status=available<0?red:need>0?amber:green;
        LinearLayout heading=new LinearLayout(this);heading.setGravity(Gravity.CENTER_VERTICAL);TextView name=label(c.name,16,ink,true);name.setPadding(0,0,dp(8),0);heading.addView(name,new LinearLayout.LayoutParams(0,-2,1));LinearLayout balance=column();TextView caption=label("Available",10,muted,false);caption.setGravity(Gravity.END);caption.setPadding(0,0,0,0);balance.addView(caption);TextView value=label(money(available),20,status,true);value.setGravity(Gravity.END);value.setPadding(0,0,0,0);value.setAutoSizeTextTypeUniformWithConfiguration(12,20,1,android.util.TypedValue.COMPLEX_UNIT_SP);balance.addView(value,new LinearLayout.LayoutParams(-1,dp(27)));heading.addView(balance,new LinearLayout.LayoutParams(dp(128),-2));row.addView(heading);
        LinearLayout details=new LinearLayout(this);TextView assigned=label("Assigned  "+money(budget.assigned(c,month)),11,muted,false),activity=label("Activity  "+money(budget.activity(c,month)),11,muted,false);details.addView(assigned,new LinearLayout.LayoutParams(0,-2,1));activity.setGravity(Gravity.END);details.addView(activity,new LinearLayout.LayoutParams(0,-2,1));row.addView(details);
        if(c.target>0){long base=c.targetType.equals("Monthly")?budget.assigned(c,month):c.targetType.equals("Balance")?available:c.target-need;progress(row,base,c.target,status);row.addView(label(targetDescription(c),11,muted,false));row.addView(label(need==0?"Funded for this month":money(need)+" left to fund this month",12,need>0?amber:green,true));}
        if(available<0)row.addView(label("Overspent by "+money(-available)+" - tap to cover",12,red,true));
        row.setOnClickListener(v->categoryDetails(c));
    }
    private void plan(){
        readyCard();if(overspent()>0)content.addView(label(overspent()+" categories overspent - tap to cover",14,red,true));
        content.addView(button("Fund targets",this::autoAssign));LinkedHashSet<String> groups=new LinkedHashSet<>();for(Budget.Category c:budget.categories)groups.add(c.group);
        for(String group:groups){content.addView(label(group,18,blue,true));for(Budget.Category c:budget.categories)if(c.group.equals(group))categoryCard(c);}
        content.addView(button("+ Add category",()->editCategory(null)));content.addView(button("Move money",this::move));
    }
    private void categoryDetails(Budget.Category c){
        new AlertDialog.Builder(this).setTitle(c.name).setItems(new String[]{"Assign or return money","Move money","Edit category and target","View transactions"},(d,n)->{if(n==0)assign(c);else if(n==1)move();else if(n==2)editCategory(c);else{tab="Spending";search=c.name;accountFilter="";render();}}).show();
    }
    private void spending(){
        content.addView(button("+ Add transaction",()->transaction(null)));if(!accountFilter.isEmpty())content.addView(label("Account: "+budget.account(accountFilter).name,14,blue,true));
        EditText query=field(content,"Search payee, category or memo",false);query.setText(search);LinearLayout list=column();content.addView(list);fillEntries(list);
        query.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int f){}public void onTextChanged(CharSequence s,int a,int b,int c){search=s.toString();fillEntries(list);}public void afterTextChanged(Editable e){}});
    }
    private String categoryName(Budget.Entry e){return e.transfer()?"Transfer":e.category.isEmpty()?"Income":budget.category(e.category).name;}
    private void fillEntries(LinearLayout list){
        list.removeAllViews();List<Budget.Entry> ordered=new ArrayList<>(budget.entries);ordered.sort((a,b)->b.date.compareTo(a.date));int n=0;
        for(Budget.Entry e:ordered){String text=e.payee+" "+categoryName(e)+" "+e.memo+" "+budget.account(e.account).name;if(!e.date.startsWith(month.toString())||!text.toLowerCase(Locale.ROOT).contains(search.toLowerCase(Locale.ROOT))||(!accountFilter.isEmpty()&&!e.account.equals(accountFilter)&&!e.destination.equals(accountFilter)))continue;n++;LinearLayout row=column();row.setPadding(dp(14),dp(10),dp(14),dp(10));row.setBackground(bg(surface));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,dp(5),0,dp(5));list.addView(row,p);row.addView(label(e.payee,17,ink,true));row.addView(label(categoryName(e)+" / "+budget.account(e.account).name+" / "+e.date,12,muted,false));row.addView(label(money(e.amount)+(e.cleared?"  Cleared":"  Uncleared"),17,e.amount>0?green:ink,true));if(!e.memo.isEmpty())row.addView(label(e.memo,12,muted,false));row.setOnClickListener(v->transaction(e));}
        if(n==0)list.addView(label("No matching transactions this month.",15,muted,false));
    }
    private void accounts(){
        for(Budget.Account a:budget.accounts){LinearLayout c=card();c.addView(label(a.name,20,ink,true));c.addView(label(money(budget.balance(a,false)),28,ink,true));c.addView(label("Cleared "+money(budget.balance(a,true))+" / Uncleared "+money(budget.balance(a,false)-budget.balance(a,true)),13,muted,false));if(!a.reconciled.isEmpty())c.addView(label("Last reconciled "+a.reconciled,12,green,false));c.addView(button("View transactions",()->{accountFilter=a.id;search="";tab="Spending";render();}));c.addView(button("Reconcile",()->reconcile(a)));}
        content.addView(button("+ Add cash account",this::addAccount));if(budget.accounts.size()>1)content.addView(button("Transfer between accounts",this::transfer));content.addView(label("Checking, savings and cash accounts are pooled for your plan. Transfers change where money lives, not its purpose.",14,muted,false));
    }
    private void reflect(){
        LinearLayout totals=card();totals.addView(label("This month's cash flow",20,ink,true));totals.addView(label("Income "+money(budget.income(month)),21,green,true));totals.addView(label("Spending "+money(budget.spending(month)),21,ink,true));totals.addView(label("Difference "+money(budget.income(month)-budget.spending(month)),17,blue,true));
        content.addView(label("Spending by category",20,ink,true));long total=budget.spending(month);for(Budget.Category c:budget.categories){long spent=Math.max(0,-budget.activity(c,month));if(spent==0)continue;LinearLayout r=card();r.addView(label(c.name+"  "+money(spent),16,ink,true));ProgressBar bar=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);bar.setProgress((int)(spent*100/Math.max(1,total)));r.addView(bar);}
        content.addView(label("Last six months",20,ink,true));for(int i=5;i>=0;i--){YearMonth m=month.minusMonths(i);content.addView(label(m.format(DateTimeFormatter.ofPattern("MMM yyyy"))+"   In "+money(budget.income(m))+"   Out "+money(budget.spending(m)),13,muted,false));}
        content.addView(label("AUD / Saved on this device. Credit cards, bank sync and cloud backup are not included.",12,muted,false));
    }
    private LinearLayout form(){LinearLayout f=column();f.setPadding(dp(20),dp(8),dp(20),dp(8));return f;}
    private EditText field(LinearLayout f,String hint,boolean numeric){EditText e=new EditText(this);e.setHint(hint);e.setSingleLine(true);e.setTextColor(ink);if(numeric)e.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL|InputType.TYPE_NUMBER_FLAG_SIGNED);f.addView(e,new LinearLayout.LayoutParams(-1,-2));return e;}
    private Spinner spinner(LinearLayout f,String title,String[] names,int selection){f.addView(label(title,12,muted,true));Spinner s=new Spinner(this);s.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,names));if(names.length>0)s.setSelection(Math.max(0,selection));f.addView(s);return s;}
    private String[] categoryNames(){return budget.categories.stream().map(c->c.name).toArray(String[]::new);}
    private String[] accountNames(){return budget.accounts.stream().map(a->a.name).toArray(String[]::new);}
    private String required(EditText e){String s=e.getText().toString().trim();if(s.isEmpty())throw new IllegalArgumentException("Please enter a name.");return s;}
    private String date(EditText e){LocalDate d=LocalDate.parse(e.getText().toString().trim());if(d.isAfter(LocalDate.now())||d.getYear()<1900)throw new IllegalArgumentException("Use a date between 1900 and today.");return d.toString();}
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
    private void commit(Runnable action){
        if(!storageReadable)throw new IllegalStateException("Saved data could not be read.");
        Budget before;try{before=BudgetStore.decode(BudgetStore.encode(budget));}catch(Exception e){throw new IllegalStateException("Could not prepare save.");}
        try{action.run();String raw=BudgetStore.encode(budget);if(!getSharedPreferences("budget",0).edit().putString("data",raw).commit())throw new IllegalStateException("Could not save to device storage.");}
        catch(Exception e){budget=before;throw new IllegalArgumentException(e.getMessage()==null?"Check your entry.":e.getMessage());}
    }
    private void dialog(String title,LinearLayout f,Runnable action){
        ScrollView scroll=new ScrollView(this);scroll.addView(f);AlertDialog d=new AlertDialog.Builder(this).setTitle(title).setView(scroll).setNegativeButton("Cancel",null).setPositiveButton("Save",null).create();
        editors.add(d);d.setOnDismissListener(v->editors.remove(d));d.setOnShowListener(v->d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(w->{try{commit(action);render();d.dismiss();}catch(Exception e){toast(e.getMessage());}}));d.show();
    }
    private void assign(Budget.Category c){LinearLayout f=form();f.addView(label(money(budget.spendable(month))+" ready to assign",16,blue,true));f.addView(label("Positive amount adds money; negative returns it.",13,muted,false));EditText amount=field(f,"Amount (AUD)",true);dialog("Assign to "+c.name,f,()->budget.assign(budget.category(c.id),month,Budget.parse(amount.getText().toString())));}
    private void move(){if(budget.categories.size()<2){toast("Create two categories first.");return;}LinearLayout f=form();Spinner from=spinner(f,"From",categoryNames(),0),to=spinner(f,"To",categoryNames(),1);EditText amount=field(f,"Amount (AUD)",true);dialog("Move money",f,()->budget.move(budget.categories.get(from.getSelectedItemPosition()),budget.categories.get(to.getSelectedItemPosition()),month,Budget.cents(amount.getText().toString())));}
    private void autoAssign(){long remaining=Math.max(0,budget.spendable(month));long total=0;for(Budget.Category c:budget.categories)total+=budget.needed(c,month);long fund=Math.min(remaining,total);if(fund==0){toast("No available money or underfunded targets.");return;}new AlertDialog.Builder(this).setTitle("Fund targets").setMessage("Assign "+money(fund)+" to underfunded targets in category order?").setNegativeButton("Cancel",null).setPositiveButton("Fund",(d,w)->{try{commit(()->{long left=Math.max(0,budget.spendable(month));for(Budget.Category c:budget.categories){long n=Math.min(left,budget.needed(c,month));if(n>0){budget.assign(c,month,n);left-=n;}}});render();}catch(Exception e){toast(e.getMessage());}}).show();}
    private void editCategory(Budget.Category existing){
        LinearLayout f=form();EditText name=field(f,"Category name",false),group=field(f,"Group (Bills, Everyday, Savings...)",false);String[] types={"Refill each month","Set aside each month","Save toward a balance"};Spinner type=spinner(f,"Target behavior",types,existing==null?0:existing.targetType.equals("Monthly")?1:existing.targetType.equals("Balance")?2:0);TextView explanation=label("",13,muted,false);f.addView(explanation);EditText amount=field(f,"Target amount (0 for none)",true);LinearLayout deadline=column();f.addView(deadline);EditText due=field(deadline,"Due month (YYYY-MM, optional)",false);
        if(existing!=null){name.setText(existing.name);group.setText(existing.group);amount.setText(decimal(existing.target));due.setText(existing.due);}else group.setText("Everyday");
        Runnable describe=()->{int selected=type.getSelectedItemPosition();deadline.setVisibility(selected==2?View.VISIBLE:View.GONE);explanation.setText(new String[]{"Top up what remained from last month. Example: a $500 target with $100 left asks for $400. Spending this month does not restart the target.","Add a fresh amount every month. Example: set aside $100 for repairs, even if $300 remains from earlier months.","Build up to a total balance. Example: a $1,200 goal with $300 saved and 3 months remaining asks for $300 this month. A due month is optional."}[selected]);};describe.run();type.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onItemSelected(AdapterView<?> p,View v,int position,long id){describe.run();}public void onNothingSelected(AdapterView<?> p){}});
        dialog(existing==null?"New category":"Edit category & target",f,()->{String n=required(name),g=required(group);for(Budget.Category c:budget.categories)if((existing==null||!c.id.equals(existing.id))&&c.name.equalsIgnoreCase(n))throw new IllegalArgumentException("That category already exists.");long target=amount.getText().toString().trim().isEmpty()?0:Budget.parse(amount.getText().toString());if(target<0)throw new IllegalArgumentException("Target cannot be negative.");String dueMonth=type.getSelectedItemPosition()==2?due.getText().toString().trim():"";if(!dueMonth.isEmpty()){YearMonth m=YearMonth.parse(dueMonth);if(m.getYear()<1900||m.getYear()>2100)throw new IllegalArgumentException("Choose a due year between 1900 and 2100.");}Budget.Category c=existing==null?new Budget.Category(n):budget.category(existing.id);c.name=n;c.group=g;c.target=target;c.targetType=new String[]{"Refill","Monthly","Balance"}[type.getSelectedItemPosition()];c.due=dueMonth;if(existing==null)budget.categories.add(c);});
    }
    private void addAccount(){LinearLayout f=form();EditText name=field(f,"Account name",false),opening=field(f,"Current cash balance (AUD)",true),day=field(f,"Opening date (YYYY-MM-DD)",false);day.setText(LocalDate.now().toString());f.addView(label("Add cash, checking or savings. Enter transactions from the opening date onward. Credit accounts are not supported yet.",13,muted,false));dialog("Add account",f,()->{String n=required(name);for(Budget.Account a:budget.accounts)if(a.name.equalsIgnoreCase(n))throw new IllegalArgumentException("That account already exists.");long balance=Budget.parse(opening.getText().toString());if(balance<0)throw new IllegalArgumentException("Use a nonnegative cash opening balance.");budget.accounts.add(new Budget.Account(n,date(day),balance));});}
    private void transaction(Budget.Entry old){
        if(budget.accounts.isEmpty()){toast("Add an account first.");addAccount();return;}if(old!=null&&old.transfer()){editTransfer(old);return;}
        LinearLayout f=form();Spinner kind=spinner(f,"Type",new String[]{"Expense","Income","Category refund"},old==null?0:old.amount<0?0:old.category.isEmpty()?1:2);TextView guidance=label("",12,muted,false);f.addView(guidance);EditText payee=field(f,"Payee",false);f.addView(label("Amount (AUD)",12,muted,true));EditText amount=field(f,"0.00",true);amount.setTextSize(24);f.addView(label("Date",12,muted,true));EditText day=field(f,"YYYY-MM-DD",false);Spinner account=spinner(f,"Account",accountNames(),old==null?0:budget.accounts.indexOf(budget.account(old.account)));LinearLayout categoryFields=column();f.addView(categoryFields);Spinner category=spinner(categoryFields,"Category",categoryNames(),old==null?0:budget.categories.indexOf(budget.category(old.category)));
        LinearLayout noteFields=column();EditText memo=field(noteFields,"Note (optional)",false);Button note=button(old!=null&&!old.memo.isEmpty()?"Hide note":"+ Add a note",()->{});f.addView(note);f.addView(noteFields);noteFields.setVisibility(old!=null&&!old.memo.isEmpty()?View.VISIBLE:View.GONE);note.setOnClickListener(v->{boolean show=noteFields.getVisibility()!=View.VISIBLE;noteFields.setVisibility(show?View.VISIBLE:View.GONE);note.setText(show?"Hide note":"+ Add a note");});CheckBox cleared=new CheckBox(this);cleared.setText("Cleared at the bank");cleared.setMinHeight(dp(48));f.addView(cleared);day.setText(LocalDate.now().toString());
        if(old!=null){payee.setText(old.payee);amount.setText(decimal(Math.abs(old.amount)));day.setText(old.date);memo.setText(old.memo);cleared.setChecked(old.cleared);f.addView(button("Delete transaction",()->delete(old)));}
        Runnable adapt=()->{int selected=kind.getSelectedItemPosition();boolean income=selected==1;categoryFields.setVisibility(income?View.GONE:View.VISIBLE);payee.setHint(income?"Income source":selected==2?"Refund from":"Payee");guidance.setText(income?"Adds money to Ready to Assign.":selected==2?"Returns money to the original spending category.":"Reduces the available money in your category.");};adapt.run();kind.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onItemSelected(AdapterView<?> p,View v,int position,long id){adapt.run();}public void onNothingSelected(AdapterView<?> p){}});
        dialog(old==null?"Add transaction":"Edit transaction",f,()->{int k=kind.getSelectedItemPosition();if(k!=1&&budget.categories.isEmpty())throw new IllegalArgumentException("Add a category first.");Budget.Entry e=new Budget.Entry(required(payee),k==1?"":budget.categories.get(category.getSelectedItemPosition()).id,budget.accounts.get(account.getSelectedItemPosition()).id,date(day),Budget.cents(amount.getText().toString())*(k==0?-1:1));e.memo=memo.getText().toString().trim();e.cleared=cleared.isChecked();budget.validate(e);if(old!=null){e.id=old.id;budget.entries.removeIf(t->t.id.equals(old.id));}budget.entries.add(0,e);});
    }
    private void delete(Budget.Entry e){new AlertDialog.Builder(this).setTitle("Delete transaction?").setMessage("Account and category balances will be recalculated.").setNegativeButton("Cancel",null).setPositiveButton("Delete",(d,w)->{try{commit(()->budget.entries.removeIf(t->t.id.equals(e.id)));render();for(AlertDialog editor:new ArrayList<>(editors))editor.dismiss();}catch(Exception ex){toast(ex.getMessage());}}).show();}
    private final List<AlertDialog> editors=new ArrayList<>();
    private void transfer(){editTransfer(null);}
    private void editTransfer(Budget.Entry old){
        if(budget.accounts.size()<2){toast("Add two accounts first.");return;}LinearLayout f=form();Spinner from=spinner(f,"From account",accountNames(),old==null?0:budget.accounts.indexOf(budget.account(old.account))),to=spinner(f,"To account",accountNames(),old==null?1:budget.accounts.indexOf(budget.account(old.destination)));EditText amount=field(f,"Amount (AUD)",true),day=field(f,"Date (YYYY-MM-DD)",false);day.setText(old==null?LocalDate.now().toString():old.date);CheckBox cleared=new CheckBox(this);cleared.setText("Cleared in both accounts");f.addView(cleared);if(old!=null){amount.setText(decimal(-old.amount));cleared.setChecked(old.cleared);f.addView(button("Delete transfer",()->delete(old)));}
        dialog(old==null?"Transfer money":"Edit transfer",f,()->{Budget.Account a=budget.accounts.get(from.getSelectedItemPosition()),b=budget.accounts.get(to.getSelectedItemPosition());Budget.Entry e=new Budget.Entry("Transfer to "+b.name,"",a.id,date(day),-Budget.cents(amount.getText().toString()));e.destination=b.id;e.cleared=cleared.isChecked();budget.validate(e);if(old!=null){e.id=old.id;budget.entries.removeIf(t->t.id.equals(old.id));}budget.entries.add(0,e);});
    }
    private void reconcile(Budget.Account a){
        LinearLayout f=form();f.addView(label("Cleared balance: "+money(budget.balance(a,true)),18,ink,true));f.addView(label("Compare with your bank's cleared balance, excluding pending transactions. Mark transactions cleared in Spending first.",14,muted,false));EditText value=field(f,"Bank's cleared balance (AUD)",true);dialog("Reconcile "+a.name,f,()->{long difference=Budget.parse(value.getText().toString())-budget.balance(budget.account(a.id),true);if(difference!=0)throw new IllegalArgumentException("Balances differ by "+money(difference)+". Check missing or uncleared transactions before reconciling.");budget.account(a.id).reconciled=LocalDate.now().toString();});
    }
    private void load(){
        String raw=getSharedPreferences("budget",0).getString("data",null);if(raw==null){for(String[] item:new String[][]{{"Rent","Bills"},{"Utilities","Bills"},{"Groceries","Everyday"},{"Transport","Everyday"},{"Dining out","Everyday"},{"Annual insurance","True expenses"},{"Car repairs","True expenses"},{"Emergency fund","Savings"}}){Budget.Category c=new Budget.Category(item[0]);c.group=item[1];budget.categories.add(c);}return;}
        try{budget=BudgetStore.decode(raw);if(!raw.contains("\"version\"")){String updated=BudgetStore.encode(budget);if(!getSharedPreferences("budget",0).edit().putString("legacy_backup",raw).putString("data",updated).commit())throw new IllegalStateException("Migration could not be saved.");toast("Budget upgraded. Existing balances preserved; monthly assignments begin this month.");}}
        catch(Exception e){storageReadable=false;new AlertDialog.Builder(this).setTitle("Unable to load budget").setMessage("Your saved data has been preserved. Close the app to avoid changes.").setPositiveButton("Close",(d,w)->finish()).setCancelable(false).show();}
    }
}
