package com.mybudget.app;

import android.app.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.content.res.ColorStateList;
import android.text.*;
import android.view.View;
import android.widget.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** Shared view builders, money and date formatting, and form helpers for MainActivity and its screens. */
class Ui {
    final MainActivity main;
    Ui(MainActivity main){this.main=main;}
    // Flags: a colour per transaction (Budget.FLAGS), shown as a dot on its row.
    static final int[] FLAG_COLORS={0,Color.rgb(229,72,77),Color.rgb(247,107,21),Color.rgb(226,178,3),Color.rgb(48,164,108),Color.rgb(62,99,221),Color.rgb(142,78,198)};
    String[] flagChoices(){String[] names=new String[Budget.FLAGS.length];names[0]="No flag";
        for(int i=1;i<names.length;i++)names[i]=main.budget.flagLabel(i);return names;}
    String when(String iso){try{return LocalDateTime.parse(iso).format(DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a",Locale.forLanguageTag("en-AU")));}catch(Exception e){return "an unknown date";}}
    static String count(int n,String one,String many){return n+" "+(n==1?one:many);}
    int dp(int n){return(int)(n*main.getResources().getDisplayMetrics().density);}
    String money(long cents){return main.hideAmounts?main.money().getCurrency().getSymbol(Locale.getDefault())+"•••":Budget.money(cents,main.money());}
    String code(){return main.budget.currency;} // the budget's currency, for labels such as "Amount (AUD)"
    String decimal(long cents){return java.math.BigDecimal.valueOf(cents,2).toPlainString();}
    LinearLayout column(){LinearLayout v=new LinearLayout(main);v.setOrientation(LinearLayout.VERTICAL);return v;}
    TextView label(String text,int size,int color,boolean bold){TextView v=new TextView(main);v.setText(text);v.setTextSize(size);
        v.setTextColor(color);v.setPadding(0,dp(4),0,dp(4));if(bold)v.setTypeface(null,Typeface.BOLD);return v;}
    /** A section or card title: a label TalkBack lists as a heading (Android 9+). */
    TextView heading(String text,int size,int color){TextView v=label(text,size,color,true);heading(v);return v;}
    static void heading(View v){if(android.os.Build.VERSION.SDK_INT>=28)v.setAccessibilityHeading(true);}
    /** [label] names [field] for TalkBack ("Amount (AUD), edit box"), when the field's hint isn't its name. */
    static void names(TextView label,View field){if(field.getId()==View.NO_ID)field.setId(View.generateViewId());label.setLabelFor(field.getId());}
    // TalkBack says how an amount box takes sums, after its name.
    static void sumsHint(EditText e){sumsHint(e,null);}
    /** [name]: what TalkBack calls the box when its hint is only an example ("0.00"); null: its hint. */
    static void sumsHint(EditText e,String name){e.setAccessibilityDelegate(new View.AccessibilityDelegate(){@Override public void onInitializeAccessibilityNodeInfo(View v,android.view.accessibility.AccessibilityNodeInfo info){
        super.onInitializeAccessibilityNodeInfo(v,info);CharSequence hint=name!=null?name:((EditText)v).getHint();info.setHintText((hint==null?"":hint+". ")+"Takes a sum too, such as 45 + 12.50.");}});}
    GradientDrawable bg(int color){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(16));return d;}
    LinearLayout card(){LinearLayout v=column();v.setPadding(dp(14),dp(10),dp(14),dp(10));v.setBackground(bg(main.surface));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,dp(4),0,dp(4));main.content.addView(v,p);return v;}
    Button button(String text,Runnable action){Button b=new Button(main);b.setText(text);b.setAllCaps(false);b.setTextSize(13);
        b.setTextColor(main.blue);b.setBackground(bg(main.buttonSurface));b.setStateListAnimator(null);b.setElevation(0);b.setMinHeight(dp(48));
        b.setMinimumHeight(dp(48));b.setMinWidth(0);b.setMinimumWidth(0);b.setPadding(dp(12),dp(8),dp(12),dp(8));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,dp(4),0,dp(4));b.setLayoutParams(p);
        b.setOnClickListener(v->action.run());return b;}
    void progress(LinearLayout parent,long funded,long goal,int color){ProgressBar bar=new ProgressBar(main,null,android.R.attr.progressBarStyleHorizontal);
        bar.setMax(100);bar.setProgress((int)Math.max(0,Math.min(100,funded*100/Math.max(1,goal))));
        bar.setProgressTintList(ColorStateList.valueOf(color));bar.setProgressBackgroundTintList(ColorStateList.valueOf(main.buttonSurface));
        bar.setContentDescription("Target progress: "+money(Math.max(0,funded))+" of "+money(goal)+", "+bar.getProgress()+"%");
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(6));p.setMargins(0,dp(5),0,dp(5));parent.addView(bar,p);}
    static String ordinal(int d){return d+(d%100>=11&&d%100<=13?"th":d%10==1?"st":d%10==2?"nd":d%10==3?"rd":"th");}
    static String dayName(int weekday){return DayOfWeek.of(weekday).getDisplayName(java.time.format.TextStyle.FULL,Locale.forLanguageTag("en-AU"));}
    LinearLayout form(){LinearLayout f=column();f.setPadding(dp(20),dp(8),dp(20),dp(8));return f;}
    // Amount boxes take quick maths ("45+12.50", see Budget.evaluate): the phone keypad has digits, . + - * / and brackets.
    static final int AMOUNT_INPUT=InputType.TYPE_CLASS_PHONE;
    EditText field(LinearLayout f,String hint,boolean numeric){EditText e=new EditText(main);e.setHint(hint);e.setSingleLine(true);
        e.setTextColor(main.ink);if(numeric){e.setInputType(AMOUNT_INPUT);sumsHint(e);}f.addView(e,new LinearLayout.LayoutParams(-1,-2));return e;}
    AutoCompleteTextView suggestField(LinearLayout f,String hint,java.util.function.Supplier<List<String>> options){AutoCompleteTextView e=Suggest.box(main,f,hint,options);
        e.setTextColor(main.ink);return e;}
    Spinner spinner(LinearLayout f,String title,String[] names,int selection){TextView t=label(title,12,main.muted,true);f.addView(t);Spinner s=new Spinner(main);names(t,s);
        s.setAdapter(new ArrayAdapter<>(main,android.R.layout.simple_spinner_dropdown_item,names));
        if(names.length>0)s.setSelection(Math.max(0,selection));f.addView(s);return s;}
    String required(EditText e){String s=e.getText().toString().trim();
        if(s.isEmpty())throw new IllegalArgumentException("Please enter a name.");return s;}
    String date(EditText e){LocalDate d=LocalDate.parse((String)e.getTag());
        if(d.isAfter(LocalDate.now())||d.getYear()<1900)throw new IllegalArgumentException("Use a date between 1900 and today.");return d.toString();}
    static String pretty(String iso){try{return LocalDate.parse(iso).format(DateTimeFormatter.ofPattern("d MMM yyyy",Locale.forLanguageTag("en-AU")));}catch(Exception e){return iso;}}
    /** A date shown as "7 Oct 2026" that opens the date picker (1900 to today); the ISO date is kept in its tag. */
    EditText dateField(LinearLayout f,String iso){return dateField(f,iso,false);}
    /** [future]: dates after today allowed (up to five years), for upcoming transactions. */
    EditText dateField(LinearLayout f,String iso,boolean future){
        EditText e=new EditText(main);e.setTag(iso);e.setText(pretty(iso));e.setTextColor(main.ink);e.setFocusable(false);e.setCursorVisible(false);
        e.setContentDescription("Date, "+pretty(iso)+". Double tap to change.");
        e.setOnClickListener(v->{LocalDate d=LocalDate.parse((String)e.getTag());
            DatePickerDialog picker=new DatePickerDialog(main,(p,y,m,day)->{String chosen=LocalDate.of(y,m+1,day).toString();e.setTag(chosen);
                e.setText(pretty(chosen));
                e.setContentDescription("Date, "+pretty(chosen)+". Double tap to change.");},d.getYear(),d.getMonthValue()-1,d.getDayOfMonth());
            picker.getDatePicker().setMaxDate(future?LocalDate.now().plusYears(5).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli():System.currentTimeMillis());
            picker.getDatePicker().setMinDate(LocalDate.of(1900,1,1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli());picker.show();});
        f.addView(e,new LinearLayout.LayoutParams(-1,-2));return e;
    }
    void onText(EditText e,Runnable changed){e.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int f){}public void onTextChanged(CharSequence s,int a,int b,int c){changed.run();}public void afterTextChanged(Editable x){}});}
    void toast(String s){Toast.makeText(main,s,Toast.LENGTH_LONG).show();}
    /** Large text: a dialog's three buttons stack and the last is cut off, so a third action goes into the form as a button. */
    boolean large(){return main.getResources().getConfiguration().fontScale>=1.3f;}
    void dialog(String title,LinearLayout f,Runnable action){
        ScrollView scroll=new ScrollView(main);scroll.addView(f);AlertDialog d=new AlertDialog.Builder(main).setTitle(title).setView(scroll)
            .setNegativeButton("Cancel",null).setPositiveButton("Save",null).create();
        main.editors.add(d);d.setOnDismissListener(v->main.editors.remove(d));
        d.setOnShowListener(v->d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(w->{try{main.commit(action);main.render();
                d.dismiss();}catch(Exception e){toast(e.getMessage());}}));d.show();
    }
    void onPick(Spinner s,Runnable changed){s.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onItemSelected(AdapterView<?> p,View v,int position,long id){changed.run();}public void onNothingSelected(AdapterView<?> p){}});}
}
