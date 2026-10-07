package com.mybudget.app;

import android.app.*;
import android.content.ComponentName;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.text.InputType;
import android.widget.*;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.*;
import java.util.*;

/**
 * Another app's payment as an expense (Planner: "Send paid bills to MyBudget"). ADD_EXPENSE asks for the category and
 * account before saving; PAYMENT_UNDONE asks before removing the expense with that payment id. Any app can open this,
 * so nothing is saved without the user's tap, and a payment id already saved is not added again.
 */
public class AddExpenseActivity extends Activity {
    public static final String ACTION_ADD="com.mybudget.app.action.ADD_EXPENSE",ACTION_UNDONE="com.mybudget.app.action.PAYMENT_UNDONE";
    private Budget budget;
    private final NumberFormat currency=NumberFormat.getCurrencyInstance(Locale.forLanguageTag("en-AU"));
    AlertDialog dialog; // package-private for BudgetInstrumentation
    @Override public void onCreate(Bundle state){
        String themeMode=getSharedPreferences("appearance",0).getString("theme","Dark");
        boolean dark=themeMode.equals("Dark")||(themeMode.equals("Auto")&&(getResources().getConfiguration().uiMode&Configuration.UI_MODE_NIGHT_MASK)==Configuration.UI_MODE_NIGHT_YES);
        setTheme(dark?R.style.AppTheme_Overlay:R.style.AppTheme_Light_Overlay);
        super.onCreate(state);
        setResult(RESULT_CANCELED);
        String raw=getSharedPreferences("budget",0).getString("data",null);
        try{budget=raw==null?new Budget():BudgetStore.decode(raw);}catch(Exception e){fail("MyBudget couldn't read its saved budget. Open MyBudget to check it.");return;}
        Intent intent=getIntent();String id=text(intent,"paymentId",100);
        if(id.isEmpty()){fail("MyBudget couldn't read this payment.");return;}
        if(ACTION_UNDONE.equals(intent.getAction()))undone(id);else if(ACTION_ADD.equals(intent.getAction()))add(intent,id);else finish();
    }
    private String text(Intent intent,String key,int max){String s=intent.getStringExtra(key);return s==null?"":s.trim().substring(0,Math.min(max,s.trim().length()));}
    private String money(long cents){return currency.format(BigDecimal.valueOf(cents,2));}
    private void fail(String message){Toast.makeText(this,message,Toast.LENGTH_LONG).show();finish();}
    private void done(String summary){setResult(RESULT_OK,new Intent().putExtra("summary",summary));finish();}
    private String sender(){ComponentName from=getCallingActivity();if(from==null)return "another app";try{return getPackageManager().getApplicationLabel(getPackageManager().getApplicationInfo(from.getPackageName(),0)).toString();}catch(Exception e){return "another app";}}
    private boolean save(){try{String raw=BudgetStore.encode(budget);return getSharedPreferences("budget",0).edit().putString("data",raw).commit();}catch(Exception e){return false;}}
    // The budget as saved now: MyBudget may have saved changes while this dialog was open, and saving the copy read at the start would drop them.
    private void reload(){String raw=getSharedPreferences("budget",0).getString("data",null);try{budget=raw==null?new Budget():BudgetStore.decode(raw);}catch(Exception e){throw new IllegalStateException("MyBudget couldn't read its saved budget. Open MyBudget to check it.");}}
    private TextView label(LinearLayout f,String text,int size){TextView v=new TextView(this);v.setText(text);v.setTextSize(size);v.setPadding(0,dp(6),0,dp(2));f.addView(v);return v;}
    private EditText field(LinearLayout f,String hint,String value,int type){EditText e=new EditText(this);e.setHint(hint);e.setText(value);e.setSingleLine(true);e.setInputType(type);f.addView(e,new LinearLayout.LayoutParams(-1,-2));return e;}
    // A date shown as "7 Oct 2026" that opens the date picker (up to today); the ISO date is kept in its tag.
    private EditText dateField(LinearLayout f,String iso){
        EditText e=new EditText(this);e.setTag(iso);e.setText(MainActivity.pretty(iso));e.setFocusable(false);e.setCursorVisible(false);
        e.setOnClickListener(v->{LocalDate d=LocalDate.parse((String)e.getTag());DatePickerDialog picker=new DatePickerDialog(this,(p,y,m,day)->{String chosen=LocalDate.of(y,m+1,day).toString();e.setTag(chosen);e.setText(MainActivity.pretty(chosen));},d.getYear(),d.getMonthValue()-1,d.getDayOfMonth());picker.getDatePicker().setMaxDate(System.currentTimeMillis());picker.show();});
        f.addView(e,new LinearLayout.LayoutParams(-1,-2));return e;
    }
    private int dp(int n){return(int)(n*getResources().getDisplayMetrics().density);}

    private void add(Intent intent,String id){
        Budget.Entry existing=budget.external(id);
        if(existing!=null){PlannerBills.dropPaid(this,text(intent,"upcomingId",100),text(intent,"billKey",100));done("Already in MyBudget: "+existing.payee+" "+money(-existing.amount));return;}
        if(!"AUD".equals(intent.getStringExtra("currency"))){fail("MyBudget records AUD only, so this payment wasn't added.");return;}
        // Hidden categories, card payment categories and closed accounts aren't offered (as in MyBudget's own forms).
        List<Budget.Category> categories=new ArrayList<>();for(Budget.Category c:budget.categories)if(!c.hidden&&!c.payment())categories.add(c);
        List<Budget.Account> accounts=new ArrayList<>();for(Budget.Account a:budget.accounts)if(!a.closed)accounts.add(a);
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
            label(f,"You already have an expense for this bill this month: "+money(-last.amount)+" on "+MainActivity.pretty(last.date)+". Save only if this is another payment.",13);
        label(f,"Payee",12);EditText payeeField=field(f,"Payee",payee,InputType.TYPE_CLASS_TEXT);
        label(f,"Amount (AUD)",12);EditText amountField=field(f,"0.00",sent>0&&sent<=10_000_000_000L?BigDecimal.valueOf(sent,2).toPlainString():"",InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);amountField.setTextSize(22);
        if(sent<=0)label(f,"This bill has no amount. Enter what you paid.",13);
        label(f,"Date",12);EditText dateField=dateField(f,date);
        String[] categoryNames=new String[categories.size()+1];categoryNames[0]="Choose a category";for(int i=0;i<categories.size();i++){Budget.Category c=categories.get(i);categoryNames[i+1]=c.name+" ("+money(budget.available(c,YearMonth.now()))+" available)";}
        label(f,"Category",12);Spinner category=new Spinner(this);category.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,categoryNames));category.setSelection(suggested==null?0:categories.indexOf(suggested)+1);f.addView(category);
        if(suggested!=null)label(f,last!=null?"Suggested from last time for this bill.":"The category you planned this bill from.",12);
        label(f,"Account",12);Spinner account=new Spinner(this);account.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,accounts.stream().map(a->a.name).toArray(String[]::new)));account.setSelection(lastAccount==null?0:accounts.indexOf(lastAccount));f.addView(account);
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
                if(already!=null){PlannerBills.dropPaid(this,text(getIntent(),"upcomingId",100),billKey);setResult(RESULT_OK,new Intent().putExtra("summary","Already in MyBudget: "+already.payee+" "+money(-already.amount)));dialog.dismiss();return;}
                if(budget.category(c.id)==null)throw new IllegalArgumentException("That category no longer exists.");if(budget.account(a.id)==null)throw new IllegalArgumentException("That account no longer exists.");
                Budget.Entry e=new Budget.Entry(payeeField.getText().toString().trim(),c.id,a.id,day,-cents);e.memo=note;e.externalId=id;e.billKey=billKey;
                budget.validate(e);budget.entries.add(0,e);
                if(!save()){budget.entries.remove(e);throw new IllegalStateException("Could not save to device storage.");}
                PlannerBills.dropPaid(this,text(getIntent(),"upcomingId",100),billKey);setResult(RESULT_OK,new Intent().putExtra("summary","Added to MyBudget: "+c.name+" −"+money(cents)));dialog.dismiss();
            }catch(java.time.format.DateTimeParseException ex){Toast.makeText(this,"Enter the date as YYYY-MM-DD.",Toast.LENGTH_LONG).show();}
            catch(RuntimeException ex){Toast.makeText(this,ex.getMessage()==null?"Check your entry.":ex.getMessage(),Toast.LENGTH_LONG).show();}
        }));
        dialog.show();
    }

    private void undone(String id){
        Budget.Entry e=budget.external(id);
        if(e==null){finish();return;} // never added here (or already removed): nothing to ask
        Budget.Category c=budget.category(e.category);
        dialog=new AlertDialog.Builder(this).setTitle("Remove this expense?")
            .setMessage("You marked "+e.payee+" unpaid in "+sender()+". Remove the "+money(-e.amount)+" expense"+(c==null?"":" from "+c.name)+" too?")
            .setNegativeButton("Keep it",(d,w)->setResult(RESULT_OK,new Intent().putExtra("summary","Marked unpaid. MyBudget kept the expense.")))
            .setPositiveButton("Remove",(d,w)->{try{reload();}catch(IllegalStateException ex){Toast.makeText(this,ex.getMessage(),Toast.LENGTH_LONG).show();return;}
                Budget.Entry now=budget.external(id);if(now==null){setResult(RESULT_OK,new Intent().putExtra("summary","Marked unpaid. The expense was already gone from MyBudget."));return;}
                budget.entries.remove(now);if(save())setResult(RESULT_OK,new Intent().putExtra("summary","Marked unpaid and removed from MyBudget."));else Toast.makeText(this,"Could not save to device storage.",Toast.LENGTH_LONG).show();})
            .create();
        dialog.setOnDismissListener(d->finish());dialog.show();
    }
}
