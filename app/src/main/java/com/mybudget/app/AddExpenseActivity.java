package com.mybudget.app;

import android.app.*;
import android.content.ComponentName;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.text.InputType;
import android.widget.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

/**
 * Another app's payment as an expense (Planner: "Send paid bills to MyBudget"). ADD_EXPENSE asks for the category and
 * account before saving; PAYMENT_UNDONE asks before removing the expense with that payment id. Any app can open this,
 * so nothing is saved without the user's tap, and a payment id already saved is not added again.
 */
public class AddExpenseActivity extends Activity {
    public static final String ACTION_ADD="com.mybudget.app.action.ADD_EXPENSE",ACTION_UNDONE="com.mybudget.app.action.PAYMENT_UNDONE";
    private Budget budget;private String read; // the saved data [budget] matches (reading years of transactions again takes a while)
    AlertDialog dialog; // package-private for BudgetInstrumentation
    private int expenseRed; // the amount as it's typed: a bill paid is money going out
    @Override public void onCreate(Bundle state){
        String themeMode=getSharedPreferences("appearance",0).getString("theme","Dark");
        boolean dark=themeMode.equals("Dark")||(themeMode.equals("Auto")&&(getResources().getConfiguration().uiMode&Configuration.UI_MODE_NIGHT_MASK)==Configuration.UI_MODE_NIGHT_YES);
        setTheme(dark?R.style.AppTheme_Overlay:R.style.AppTheme_Light_Overlay);
        expenseRed=dark?android.graphics.Color.rgb(255,142,150):android.graphics.Color.rgb(178,51,55); // MainActivity's red
        super.onCreate(state);
        setResult(RESULT_CANCELED);
        String raw=getSharedPreferences("budget",0).getString("data",null);
        try{budget=raw==null?new Budget():BudgetStore.decode(raw);budget.cache(true);read=raw;}catch(Exception e){fail("MyBudget couldn't read its saved budget. Open MyBudget to check it.");return;}
        Intent intent=getIntent();String id=text(intent,"paymentId",100);
        if(id.isEmpty()){fail("MyBudget couldn't read this payment.");return;}
        if(ACTION_UNDONE.equals(intent.getAction()))undone(id);else if(ACTION_ADD.equals(intent.getAction()))add(intent,id);else finish();
    }
    private String text(Intent intent,String key,int max){String s=intent.getStringExtra(key);return s==null?"":Budget.cut(s.trim(),max);}
    // In the budget's currency, as in MyBudget; dots with Hide amounts on (hunt 23), as on MyBudget's own screens.
    private String money(long cents){java.text.NumberFormat format=Budget.moneyFormat(budget.currency,Locale.getDefault());
        return getSharedPreferences("appearance",0).getBoolean("hideAmounts",false)?format.getCurrency().getSymbol(Locale.getDefault())+"•••":Budget.money(cents,format);}
    private void fail(String message){Toast.makeText(this,message,Toast.LENGTH_LONG).show();finish();}
    private void done(String summary){setResult(RESULT_OK,reply(summary));finish();}
    // Planner's answer: the line it shows, and (hunt 23) the budget's currency, so Planner stops sending bills in another one.
    private Intent reply(String summary){return new Intent().putExtra("summary",summary).putExtra("budgetCurrency",budget.currency);}
    private String sender(){ComponentName from=getCallingActivity();if(from==null)return "another app";try{return getPackageManager().getApplicationLabel(getPackageManager().getApplicationInfo(from.getPackageName(),0)).toString();}catch(Exception e){return "another app";}}
    private boolean save(){budget.changed();try{String raw=BudgetStore.encode(budget);if(!getSharedPreferences("budget",0).edit().putString("data",raw).commit())return false;read=raw;BudgetWidget.refresh(this);return true;}catch(Exception e){return false;}} // the widget shows the new money
    // The budget as saved now: MyBudget may have saved changes while this dialog was open, and saving the copy read at the start would drop them.
    private void reload(){String raw=getSharedPreferences("budget",0).getString("data",null);if(raw!=null&&raw.equals(read))return; // unchanged since read
        try{budget=raw==null?new Budget():BudgetStore.decode(raw);read=raw;}catch(Exception e){throw new IllegalStateException("MyBudget couldn't read its saved budget. Open MyBudget to check it.");}}
    private TextView label(LinearLayout f,String text,int size){TextView v=new TextView(this);v.setText(text);v.setTextSize(size);v.setPadding(0,dp(6),0,dp(2));f.addView(v);return v;}
    private EditText field(LinearLayout f,String hint,String value,int type){EditText e=new EditText(this);e.setHint(hint);e.setText(value);e.setSingleLine(true);e.setInputType(type);f.addView(e,new LinearLayout.LayoutParams(-1,-2));return e;}
    // A date shown as "7 Oct 2026" that opens the date picker (up to today); the ISO date is kept in its tag.
    private EditText dateField(LinearLayout f,String iso){
        EditText e=new EditText(this);e.setTag(iso);e.setText(Ui.pretty(iso));e.setFocusable(false);e.setCursorVisible(false);
        e.setOnClickListener(v->{LocalDate d=LocalDate.parse((String)e.getTag());DatePickerDialog picker=new DatePickerDialog(this,(p,y,m,day)->{String chosen=LocalDate.of(y,m+1,day).toString();e.setTag(chosen);e.setText(Ui.pretty(chosen));},d.getYear(),d.getMonthValue()-1,d.getDayOfMonth());picker.getDatePicker().setMaxDate(System.currentTimeMillis());picker.show();});
        f.addView(e,new LinearLayout.LayoutParams(-1,-2));return e;
    }
    private int dp(int n){return(int)(n*getResources().getDisplayMetrics().density);}

    private void add(Intent intent,String id){
        Budget.Entry existing=budget.external(id);
        if(existing!=null){dropAgain(text(intent,"upcomingId",100),text(intent,"billKey",100),id,-existing.amount);done("Already in MyBudget: "+existing.payee+" "+money(-existing.amount));return;}
        // Only in the budget's currency (amounts aren't converted); a Planner from before currencies sends none: AUD.
        if(!Budget.sameCurrency(intent.getStringExtra("currency"),budget.currency)){done("Not added to MyBudget: this bill is in "+text(intent,"currency",3)+", but your budget is in "+budget.currency+".");return;} // Planner shows it (one message, hunt 23)
        // Hidden categories, card payment categories and closed accounts aren't offered (as in MyBudget's own forms).
        List<Budget.Category> categories=new ArrayList<>();for(Budget.Category c:budget.categories)if(!c.hidden&&!c.payment())categories.add(c);
        List<Budget.Account> accounts=new ArrayList<>();for(Budget.Account a:budget.accounts)if(!a.closed&&!a.tracking())accounts.add(a); // tracking accounts are off budget: no categories
        if(accounts.isEmpty()||categories.isEmpty()){fail("Open MyBudget and add an account first, then mark the bill paid again.");return;}
        String billKey=text(intent,"billKey",100),payee=text(intent,"payee",80),note=text(intent,"note",200);
        long sent=intent.getLongExtra("amountCents",0);String date=text(intent,"date",10);
        try{LocalDate.parse(date);}catch(Exception e){date=LocalDate.now().toString();}
        Budget.Entry last=budget.lastForBill(billKey);
        Budget.Category suggested=last==null?budget.category(budget.plannerCategory(billKey)):budget.category(last.category);Budget.Account lastAccount=last==null?null:budget.account(last.account);
        if(!categories.contains(suggested))suggested=null;if(!accounts.contains(lastAccount))lastAccount=null;
        LinearLayout f=new LinearLayout(this);f.setOrientation(LinearLayout.VERTICAL);f.setPadding(dp(24),dp(4),dp(24),dp(4));
        label(f,"From "+sender(),12);
        if(last!=null&&YearMonth.from(LocalDate.parse(last.date)).equals(YearMonth.from(LocalDate.parse(date))))
            label(f,"You already have an expense for this bill this month: "+money(-last.amount)+" on "+Ui.pretty(last.date)+". Save only if this is another payment.",13);
        // Payees used before are suggested (as in MyBudget's own form).
        TextView payeeLabel=label(f,"Payee",12);AutoCompleteTextView payeeField=Suggest.box(this,f,"Payee",()->budget.payees());payeeField.setText(payee,false);Ui.names(payeeLabel,payeeField);
        TextView amountLabel=label(f,"Amount ("+budget.currency+")",12);EditText amountField=field(f,"0.00",sent>0&&sent<=10_000_000_000L?BigDecimal.valueOf(sent,2).toPlainString():"",Ui.AMOUNT_INPUT);amountField.setTextSize(22);Ui.names(amountLabel,amountField);
        android.content.res.ColorStateList plain=amountField.getTextColors();Runnable paint=()->{long v;try{v=Budget.evaluate(amountField.getText().toString());}catch(Exception x){v=0;}
            if(v>0)amountField.setTextColor(expenseRed);else amountField.setTextColor(plain);};paint.run();
        amountField.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int b,int c){}public void onTextChanged(CharSequence s,int a,int b,int c){}public void afterTextChanged(android.text.Editable s){paint.run();}});Ui.sumsHint(amountField,"Amount"); // quick maths too
        if(sent<=0)label(f,"This bill has no amount. Enter what you paid.",13);
        else if(sent>10_000_000_000L)label(f,"This bill's amount is over MyBudget's limit of 100 million. Enter what you paid, in parts if need be.",13);
        label(f,"Date",12);EditText dateField=dateField(f,date);
        String[] categoryNames=new String[categories.size()+1];categoryNames[0]="Choose a category";for(int i=0;i<categories.size();i++){Budget.Category c=categories.get(i);categoryNames[i+1]=c.name+" ("+money(budget.available(c,YearMonth.now()))+" available)";}
        TextView categoryLabel=label(f,"Category",12);Spinner category=new Spinner(this);Ui.names(categoryLabel,category);category.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,categoryNames));category.setSelection(suggested==null?0:categories.indexOf(suggested)+1);f.addView(category);
        // Picking a known payee chooses its category from last time, unless a category is already chosen.
        payeeField.setOnItemClickListener((p,v,position,rowId)->{Budget.Entry before=budget.lastForPayee(payeeField.getText().toString());if(before==null||before.split()||category.getSelectedItemPosition()!=0)return;int i=categories.indexOf(budget.category(before.category));if(i>=0)category.setSelection(i+1);});
        if(suggested!=null)label(f,last!=null?"Suggested from last time for this bill.":"The category you planned this bill from.",12);
        TextView accountLabel=label(f,"Account",12);Spinner account=new Spinner(this);Ui.names(accountLabel,account);account.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,accounts.stream().map(a->a.name).toArray(String[]::new)));account.setSelection(lastAccount==null?0:accounts.indexOf(lastAccount));f.addView(account);
        if(!note.isEmpty())label(f,"Note: "+note,12);
        ScrollView scroll=new ScrollView(this);scroll.addView(f);
        dialog=new AlertDialog.Builder(this).setTitle("Add expense").setView(scroll).setNegativeButton("Cancel",null).setPositiveButton("Save",null).create();
        dialog.setOnDismissListener(d->finish());
        dialog.setOnShowListener(d->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            try{
                if(category.getSelectedItemPosition()==0)throw new IllegalArgumentException("Choose a category.");
                Budget.Category c=categories.get(category.getSelectedItemPosition()-1);Budget.Account a=accounts.get(account.getSelectedItemPosition());
                long cents=Budget.cents(amountField.getText().toString());String day=LocalDate.parse((String)dateField.getTag()).toString();
                reload();Budget.Entry already=budget.external(id);
                if(already!=null){dropAgain(text(getIntent(),"upcomingId",100),billKey,id,-already.amount);setResult(RESULT_OK,reply("Already in MyBudget: "+already.payee+" "+money(-already.amount)));dialog.dismiss();return;}
                if(budget.category(c.id)==null)throw new IllegalArgumentException("That category no longer exists.");if(budget.account(a.id)==null)throw new IllegalArgumentException("That account no longer exists.");
                Budget.Entry e=new Budget.Entry(payeeField.getText().toString().trim(),c.id,a.id,day,-cents);e.memo=note;e.externalId=id;e.billKey=billKey;
                budget.validate(e);budget.entries.add(0,e);
                if(!save()){budget.entries.remove(e);throw new IllegalStateException("Could not save to device storage.");}
                PlannerBills.dropPaid(this,text(getIntent(),"upcomingId",100),billKey,id,cents); /* a part payment leaves the rest planned */setResult(RESULT_OK,reply("Added to MyBudget: "+c.name+" −"+money(cents)));dialog.dismiss();
            }catch(java.time.format.DateTimeParseException ex){Toast.makeText(this,"Enter the date as YYYY-MM-DD.",Toast.LENGTH_LONG).show();}
            catch(RuntimeException ex){Toast.makeText(this,ex.getMessage()==null?"Check your entry.":ex.getMessage(),Toast.LENGTH_LONG).show();}
        }));
        dialog.show();
    }

    // A payment sent again: its planned bill went when it was first added. Only by its own entry (an older Planner sends none,
    // and by billKey alone the next one of that bill would go, hunt 23).
    private void dropAgain(String upcomingId,String billKey,String id,long cents){if(!upcomingId.isEmpty())PlannerBills.dropPaid(this,upcomingId,billKey,id,cents);}

    private void undone(String id){
        Budget.Entry e=budget.external(id);
        if(e==null){setResult(RESULT_OK,reply(null));finish();return;} // never added here (or already removed): nothing to ask
        Budget.Category c=budget.category(e.category);
        // Not cancelable (Back, a tap outside): the answer goes back to the sender, which tells the user whether the expense stayed.
        dialog=new AlertDialog.Builder(this).setTitle("Remove this expense?").setCancelable(false)
            .setMessage("You marked "+e.payee+" unpaid in "+sender()+". Remove the "+money(-e.amount)+" expense"+(c==null?"":" from "+c.name)+" too?")
            .setNegativeButton("Keep it",(d,w)->setResult(RESULT_OK,reply("Marked unpaid. MyBudget kept the expense.")))
            .setPositiveButton("Remove",(d,w)->{try{reload();}catch(IllegalStateException ex){Toast.makeText(this,ex.getMessage(),Toast.LENGTH_LONG).show();return;}
                Budget.Entry now=budget.external(id);if(now==null){setResult(RESULT_OK,reply("Marked unpaid. The expense was already gone from MyBudget."));return;}
                budget.entries.remove(now);if(save())setResult(RESULT_OK,reply("Marked unpaid and removed from MyBudget."));else Toast.makeText(this,"Could not save to device storage.",Toast.LENGTH_LONG).show();})
            .create();
        dialog.setOnDismissListener(d->finish());dialog.show();
    }
}
