package com.mybudget.app;

import android.app.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.content.Context;
import android.content.res.ColorStateList;
import android.text.*;
import android.view.View;
import android.view.ViewGroup;
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
    // Digits all the same width ("tnum"), so amounts line up from row to row.
    TextView label(String text,int size,int color,boolean bold){TextView v=new TextView(main);v.setText(text);v.setTextSize(size);
        v.setTextColor(color);v.setPadding(0,dp(4),0,dp(4));v.setFontFeatureSettings("tnum");if(bold)v.setTypeface(null,Typeface.BOLD);return v;}
    // Motion and feel. Animations follow the phone's setting (Settings > Accessibility > Remove animations, or the developer scales):
    // with animations off, nothing moves and every view simply shows its final state.
    static boolean motion(){return android.animation.ValueAnimator.areAnimatorsEnabled();}
    static int tint(int color,int alpha){return Color.argb(alpha,Color.red(color),Color.green(color),Color.blue(color));}
    /** A press ripple over a view (its foreground, so a background set later keeps it), in the shape of a rounded card or button. */
    void pressable(View v){GradientDrawable mask=new GradientDrawable();mask.setColor(Color.WHITE);mask.setCornerRadius(dp(16));
        v.setForeground(new android.graphics.drawable.RippleDrawable(ColorStateList.valueOf(tint(main.blue,main.darkTheme?56:36)),null,mask));}
    /** A light tap under the finger (tabs, toggles); [confirm]: a firmer one for a saved change. Both follow the phone's touch feedback setting. */
    static void tick(View v){v.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);}
    static void confirm(View v){v.performHapticFeedback(android.os.Build.VERSION.SDK_INT>=30?android.view.HapticFeedbackConstants.CONFIRM:android.view.HapticFeedbackConstants.VIRTUAL_KEY);}
    /** A card's surface: rounded, with a hairline edge so cards stand off the background in both themes. */
    GradientDrawable surface(int color){GradientDrawable d=bg(color);d.setStroke(dp(1),tint(main.ink,main.darkTheme?26:18));return d;}
    /** A round badge with a symbol or letter in it (payees, accounts, alerts), on a soft tint of [color]. */
    TextView badge(String text,int color){TextView b=new TextView(main);b.setText(text);b.setTextSize(16);b.setGravity(android.view.Gravity.CENTER);
        b.setTextColor(color);b.setTypeface(null,Typeface.BOLD);GradientDrawable d=new GradientDrawable();d.setShape(GradientDrawable.OVAL);
        d.setColor(tint(color,main.darkTheme?52:30));b.setBackground(d);b.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        b.setLayoutParams(new LinearLayout.LayoutParams(dp(40),dp(40)));return b;}
    /** A friendly empty state: a large symbol, a line of text, and optionally a button that gets started. */
    LinearLayout empty(String symbol,String text,String action,Runnable run){LinearLayout c=card();c.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
        c.setPadding(dp(20),dp(18),dp(20),dp(14));TextView s=label(symbol,40,main.ink,false);s.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);c.addView(s);
        TextView t=label(text,15,main.muted,false);t.setGravity(android.view.Gravity.CENTER);c.addView(t);
        if(action!=null)c.addView(primary(action,run));return c;}
    /** An empty state inside a card (a report with nothing to show): a symbol over a line of text, centred. */
    void quiet(LinearLayout parent,String symbol,String text){TextView s=label(symbol,32,main.ink,false);s.setGravity(android.view.Gravity.CENTER);
        s.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);parent.addView(s,new LinearLayout.LayoutParams(-1,-2));
        TextView t=label(text,14,main.muted,false);t.setGravity(android.view.Gravity.CENTER);parent.addView(t,new LinearLayout.LayoutParams(-1,-2));}
    /** A card's title row: a round [icon] badge, then [title]. */
    void head(LinearLayout card,String icon,TextView title){LinearLayout row=new LinearLayout(main);row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.addView(badge(icon,main.blue));title.setPadding(dp(12),dp(4),0,dp(4));row.addView(title,new LinearLayout.LayoutParams(0,-2,1));card.addView(row);}
    /** Two buttons side by side, sharing the width. */
    void pair(LinearLayout parent,Button a,Button b){LinearLayout row=new LinearLayout(main);
        LinearLayout.LayoutParams pa=new LinearLayout.LayoutParams(0,-2,1),pb=new LinearLayout.LayoutParams(0,-2,1);pa.setMargins(0,dp(4),dp(4),dp(4));pb.setMargins(dp(4),dp(4),0,dp(4));
        row.addView(a,pa);row.addView(b,pb);parent.addView(row,new LinearLayout.LayoutParams(-1,-2));}
    /** A short pop as something appears (a funded target's check mark): it grows past full size and settles. */
    static void pop(View v){if(!motion())return;v.setScaleX(0.6f);v.setScaleY(0.6f);
        v.animate().scaleX(1f).scaleY(1f).setDuration(380).setInterpolator(new android.view.animation.OvershootInterpolator(3f)).start();}
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
    LinearLayout card(){LinearLayout v=column();v.setPadding(dp(16),dp(12),dp(16),dp(12));v.setBackground(surface(main.surface));pressable(v);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,dp(5),0,dp(5));main.content.addView(v,p);return v;}
    Button button(String text,Runnable action){Button b=new Button(main);b.setText(text);b.setAllCaps(false);b.setTextSize(13);
        b.setTextColor(main.blue);b.setBackground(bg(main.buttonSurface));b.setStateListAnimator(null);b.setElevation(0);b.setMinHeight(dp(48));
        b.setMinimumHeight(dp(48));b.setMinWidth(0);b.setMinimumWidth(0);b.setPadding(dp(12),dp(8),dp(12),dp(8));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,dp(4),0,dp(4));b.setLayoutParams(p);
        pressable(b);b.setOnClickListener(v->action.run());return b;}
    /** The main action of a screen or card: filled in the brand colour, white bold text. */
    Button primary(String text,Runnable action){Button b=button(text,action);b.setBackground(bg(main.primary));b.setTextColor(Color.WHITE);
        b.setTypeface(null,Typeface.BOLD);b.setTextSize(14);return b;}
    // A rounded bar that fills from the left as the screen opens.
    void progress(LinearLayout parent,long funded,long goal,int color){ProgressBar bar=new ProgressBar(main,null,android.R.attr.progressBarStyleHorizontal);
        int percent=(int)Math.max(0,Math.min(100,funded*100/Math.max(1,goal)));bar.setMax(1000);
        GradientDrawable track=new GradientDrawable();track.setColor(main.buttonSurface);track.setCornerRadius(dp(4));
        GradientDrawable fill=new GradientDrawable();fill.setColor(color);fill.setCornerRadius(dp(4));
        android.graphics.drawable.LayerDrawable layers=new android.graphics.drawable.LayerDrawable(new android.graphics.drawable.Drawable[]{track,
            new android.graphics.drawable.ClipDrawable(fill,android.view.Gravity.START,android.graphics.drawable.ClipDrawable.HORIZONTAL)});
        layers.setId(0,android.R.id.background);layers.setId(1,android.R.id.progress);bar.setProgressDrawable(layers);
        bar.setContentDescription("Target progress: "+money(Math.max(0,funded))+" of "+money(goal)+", "+percent+"%");
        if(motion()&&percent>0){android.animation.ObjectAnimator grow=android.animation.ObjectAnimator.ofInt(bar,"progress",0,percent*10);grow.setDuration(650);
            grow.setInterpolator(new android.view.animation.DecelerateInterpolator(2f));grow.start();}else bar.setProgress(percent*10);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(8));p.setMargins(0,dp(6),0,dp(6));parent.addView(bar,p);}
    static String ordinal(int d){return d+(d%100>=11&&d%100<=13?"th":d%10==1?"st":d%10==2?"nd":d%10==3?"rd":"th");}
    static String dayName(int weekday){return DayOfWeek.of(weekday).getDisplayName(java.time.format.TextStyle.FULL,Locale.forLanguageTag("en-AU"));}
    LinearLayout form(){LinearLayout f=column();f.setPadding(dp(20),dp(8),dp(20),dp(8));return f;}
    // Amount boxes take quick maths ("45+12.50", see Budget.evaluate): the phone keypad has digits, . + - * / and brackets.
    static final int AMOUNT_INPUT=InputType.TYPE_CLASS_PHONE;
    EditText field(LinearLayout f,String hint,boolean numeric){EditText e=new EditText(main);e.setHint(hint);e.setSingleLine(true);
        e.setTextColor(main.ink);if(numeric){e.setInputType(AMOUNT_INPUT);sumsHint(e);signColours(e,1);}f.addView(e,new LinearLayout.LayoutParams(-1,-2));return e;}
    /**
     * An amount box's text turns red when what's typed works out negative and green when positive, as it's typed (sums too:
     * "45-50" is red). [sign] -1: the box holds money going out (an expense part), so a positive amount shows red.
     */
    void signColours(EditText e,int sign){Runnable paint=()->{long v;try{v=Budget.evaluate(e.getText().toString())*sign;}catch(Exception x){v=0;}
            e.setTextColor(amountColour(v));};
        onText(e,paint);paint.run();}
    // Amounts everywhere: positive in the green Budget uses for Available, negative in red, zero grey. Target figures keep
    // their own colours (amber while a target is short).
    int amountColour(long cents){return main.hideAmounts?main.ink:cents>0?main.green:cents<0?main.red:main.muted;} // zero: quiet grey
    /** Money going out shown without a minus (Spending, Money out, a card's Owed): red when there is some. */
    int outColour(long cents){return main.hideAmounts?main.ink:cents>0?main.red:cents<0?main.green:main.muted;}
    /** [text] with the last [part] in it (an amount inside a line, "Assigned A$40.00") shown in [colour]. The last (hunt 23): the amount
     *  ends the line, and a payee before it may hold the same text ("REFUND $20.00  A$20.00"). */
    static CharSequence tint(CharSequence text,String part,int colour){android.text.SpannableString s=new android.text.SpannableString(text);
        int at=part.isEmpty()?-1:text.toString().lastIndexOf(part);
        if(at>=0)s.setSpan(new android.text.style.ForegroundColorSpan(colour),at,at+part.length(),android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);return s;}
    /** A quiet grey line of [size] with the [amount] in it coloured ([colour]: amountColour or outColour). */
    TextView greyLine(String text,String amount,int colour,int size){TextView t=label("",size,main.muted,false);t.setText(tint(text,amount,colour));return t;}
    /** A line with two amounts, each coloured: [a] in [ca], then [b] (searched after [a], so equal amounts work) in [cb]. */
    static CharSequence tint2(String text,String a,int ca,String b,int cb){int i=text.indexOf(a);int j=i<0?-1:text.indexOf(b,i+a.length());
        android.text.SpannableString s=new android.text.SpannableString(text);
        if(i>=0)s.setSpan(new android.text.style.ForegroundColorSpan(ca),i,i+a.length(),android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        if(j>=0)s.setSpan(new android.text.style.ForegroundColorSpan(cb),j,j+b.length(),android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);return s;}
    /** A form section's title ("WHO AND WHAT"): small, spaced capitals; a heading for TalkBack. */
    TextView section(LinearLayout f,String title){TextView t=label(title.toUpperCase(Locale.ROOT),12,main.muted,true);t.setLetterSpacing(0.1f);
        t.setPadding(0,dp(18),0,dp(4));heading(t);t.setContentDescription(title);f.addView(t);return t;}
    /**
     * One choice from a few big buttons in a row (Expense / Income / Refund). The chosen one is tinted in its own colour; the
     * others are plain. [changed] runs after the user (or set) picks another.
     */
    final class Choice {
        final LinearLayout view=new LinearLayout(main);final List<Button> buttons=new ArrayList<>();final String[] names;final int[] colours;
        int selected;Runnable changed=()->{};boolean picked; // picked: chosen by the user (a tap), not set by the form
        Choice(String[] names,int[] colours,int selected){this.names=names;this.colours=colours;this.selected=selected;
            float scale=Math.max(1f,Math.min(1.6f,main.getResources().getConfiguration().fontScale)); // large text: taller, and the label shrinks to one line
            for(int i=0;i<names.length;i++){int n=i;Button b=button(names[i],()->{picked=true;if(selected()!=n){tick(view);set(n);}});b.setTextSize(15);
                b.setMaxLines(1);b.setPadding(dp(4),0,dp(4),0);b.setAutoSizeTextTypeUniformWithConfiguration(10,15,1,android.util.TypedValue.COMPLEX_UNIT_SP);
                LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,Math.round(dp(52)*scale),1);p.setMargins(i==0?0:dp(4),dp(6),i==names.length-1?0:dp(4),dp(6));
                view.addView(b,p);buttons.add(b);}
            style();}
        int selected(){return selected;}
        void set(int n){selected=n;style();changed.run();}
        private void style(){for(int i=0;i<buttons.size();i++){Button b=buttons.get(i);boolean on=i==selected;
            GradientDrawable d=bg(on?tint(colours[i],main.darkTheme?56:30):main.buttonSurface);if(on)d.setStroke(dp(2),colours[i]);b.setBackground(d);
            b.setTextColor(on?colours[i]:main.muted);b.setTypeface(null,on?Typeface.BOLD:Typeface.NORMAL);
            b.setContentDescription(names[i].replaceAll("^[^A-Za-z]+","")+(on?", selected":""));}}
    }
    Choice choice(LinearLayout f,String[] names,int[] colours,int selected){Choice c=new Choice(names,colours,selected);f.addView(c.view,new LinearLayout.LayoutParams(-1,-2));return c;}
    /**
     * Round-ended chips (Today / Yesterday / Pick a date, accounts, categories): the chosen one is filled. In a row that scrolls
     * sideways, or [wrap]ped onto more lines so every chip is in sight. [pick] gets the tapped chip's index; it decides what is
     * chosen (show() redraws).
     */
    final class Chips {
        final ViewGroup view,row;final boolean wrap;
        Chips(boolean wrap){this.wrap=wrap;if(wrap){view=row=new Flow(main,dp(8));}
            else{HorizontalScrollView scroll=new HorizontalScrollView(main);scroll.setHorizontalScrollBarEnabled(false);row=new LinearLayout(main);scroll.addView(row);view=scroll;}
            row.setPadding(0,dp(4),0,dp(4));}
        void show(List<String> names,int selected,java.util.function.IntConsumer pick){row.removeAllViews();
            for(int i=0;i<names.size();i++){int n=i;boolean on=i==selected;Button b=button(names.get(i),()->{tick(view);pick.accept(n);});
                b.setTextSize(14);b.setPadding(dp(16),0,dp(16),0);GradientDrawable d=bg(on?main.primary:main.buttonSurface);d.setCornerRadius(dp(24));
                b.setBackground(d);b.setTextColor(on?Color.WHITE:main.ink);if(on)b.setTypeface(null,Typeface.BOLD);
                b.setContentDescription(names.get(i)+(on?", selected":""));
                // Hunt 23: wrapped, a long name at large text may take two lines: the chip grows to fit (48dp at least).
                if(wrap){b.setMinHeight(dp(48));b.setMinimumHeight(dp(48));b.setPadding(dp(16),dp(6),dp(16),dp(6));}
                LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-2,wrap?-2:dp(48));p.setMargins(0,0,wrap?0:dp(8),0);row.addView(b,p);}
            if(selected>=0&&!wrap)view.post(()->{View on=row.getChildAt(selected);if(on==null)return; // the chosen chip in sight
                int left=on.getLeft(),right=on.getRight(),x=view.getScrollX(),w=view.getWidth();
                if(left<x||right>x+w)((HorizontalScrollView)view).smoothScrollTo(Math.max(0,left-dp(8)),0);});}
    }
    Chips chips(LinearLayout f){return chips(f,false);}
    Chips chips(LinearLayout f,boolean wrap){Chips c=new Chips(wrap);f.addView(c.view,new LinearLayout.LayoutParams(-1,-2));return c;}
    /** Lays its children out left to right, [gap] apart, starting a new line when the next one doesn't fit (the category chips). */
    static final class Flow extends ViewGroup {
        private final int gap;
        Flow(android.content.Context context,int gap){super(context);this.gap=gap;}
        /** Places each child (when [place]) and returns the height the lines take. */
        private int lines(int width,boolean place){int max=width-getPaddingLeft()-getPaddingRight(),x=0,y=0,line=0;
            for(int i=0;i<getChildCount();i++){View c=getChildAt(i);if(c.getVisibility()==GONE)continue;int w=c.getMeasuredWidth(),h=c.getMeasuredHeight();
                if(x>0&&x+w>max){x=0;y+=line+gap;line=0;}
                if(place)c.layout(getPaddingLeft()+x,getPaddingTop()+y,getPaddingLeft()+x+w,getPaddingTop()+y+h);x+=w+gap;line=Math.max(line,h);}
            return y+line+getPaddingTop()+getPaddingBottom();}
        @Override protected void onMeasure(int widthSpec,int heightSpec){int width=MeasureSpec.getSize(widthSpec),max=Math.max(0,width-getPaddingLeft()-getPaddingRight());
            for(int i=0;i<getChildCount();i++){View c=getChildAt(i);int h=c.getLayoutParams().height;
                c.measure(MeasureSpec.makeMeasureSpec(max,MeasureSpec.AT_MOST),h>0?MeasureSpec.makeMeasureSpec(h,MeasureSpec.EXACTLY):MeasureSpec.makeMeasureSpec(0,MeasureSpec.UNSPECIFIED));}
            setMeasuredDimension(width,lines(width,false));}
        @Override protected void onLayout(boolean changed,int l,int t,int r,int b){lines(r-l,true);}
    }
    AutoCompleteTextView suggestField(LinearLayout f,String hint,java.util.function.Supplier<List<String>> options){AutoCompleteTextView e=Suggest.box(main,f,hint,options);
        e.setTextColor(main.ink);return e;}
    /** A category to pick in a sheet: its first letter, its group under the name and [amount] at the end, coloured by sign. */
    MoveMenu.Choice categoryPick(Budget.Category c,long amount,Runnable picked){
        return MoveMenu.Choice.pick(c.name,picked).sub(c.group+(c.hidden?" · hidden":"")).detail(money(amount),amountColour(amount));}
    /**
     * Fills [s] with [names], its open list in a rounded, raised box with roomy rows and the current one in bold, in the
     * window's own colours (also in Planner's add-expense window, which has no Ui).
     */
    /** A question that deletes, removes or resets something: its main button red instead of indigo (call after show()). */
    static AlertDialog danger(AlertDialog d){Button b=d.getButton(AlertDialog.BUTTON_POSITIVE);if(b!=null)b.setBackgroundTintList(ColorStateList.valueOf(Color.rgb(192,57,63)));return d;}
    static void dropdown(Spinner s,String[] names){Context c=s.getContext();float dp=c.getResources().getDisplayMetrics().density;
        android.content.res.TypedArray a=c.obtainStyledAttributes(new int[]{android.R.attr.textColorPrimary,android.R.attr.colorBackgroundFloating,android.R.attr.colorAccent});
        int ink=a.getColor(0,Color.BLACK),floating=a.getColor(1,Color.WHITE),accent=a.getColor(2,ink);a.recycle();
        s.setAdapter(new ArrayAdapter<String>(c,android.R.layout.simple_spinner_dropdown_item,names){
            @Override public View getDropDownView(int at,View reuse,ViewGroup parent){TextView t=(TextView)super.getDropDownView(at,reuse,parent);boolean on=at==s.getSelectedItemPosition();
                t.setEllipsize(TextUtils.TruncateAt.END);t.setPadding((int)(20*dp),(int)(14*dp),(int)(20*dp),(int)(14*dp));t.setMinHeight((int)(52*dp));t.setTextSize(16);
                t.setTextColor(on?accent:ink);t.setTypeface(null,on?android.graphics.Typeface.BOLD:android.graphics.Typeface.NORMAL);return t;}});
        GradientDrawable box=new GradientDrawable();box.setColor(floating);box.setCornerRadius(16*dp);box.setStroke((int)Math.max(1,dp),tint(ink,30));
        s.setPopupBackgroundDrawable(box);}
    Spinner spinner(LinearLayout f,String title,String[] names,int selection){TextView t=label(title,12,main.muted,true);f.addView(t);Spinner s=new Spinner(main);names(t,s);
        dropdown(s,names);
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
    /** Hunt 25 C7: shows [b]'s dialog as one of main.editors, so data read in meanwhile (a save from Planner) closes it with the
     * forms instead of leaving it on old figures (Cover's amounts, a category's or upcoming transaction's actions, Review). */
    AlertDialog tracked(AlertDialog.Builder b){AlertDialog d=b.create();main.editors.add(d);d.setOnDismissListener(v->main.editors.remove(d));d.show();return d;}
    void toast(String s){Toast.makeText(main,s,Toast.LENGTH_LONG).show();}
    /** Large text: a dialog's three buttons stack and the last is cut off, so a third action goes into the form as a button. */
    boolean large(){return main.getResources().getConfiguration().fontScale>=1.3f;}
    AlertDialog dialog(String title,LinearLayout f,Runnable action){return dialog(title,f,action,null);}
    /** [closed]: runs when the person leaves the form (Save that worked, Cancel, Back), not when a reload closes the forms. */
    AlertDialog dialog(String title,LinearLayout f,Runnable action,Runnable closed){
        ScrollView scroll=new ScrollView(main);scroll.addView(f);AlertDialog d=new AlertDialog.Builder(main).setTitle(title).setView(scroll)
            .setNegativeButton("Cancel",closed==null?null:(x,w)->closed.run()).setPositiveButton("Save",null).create();
        main.editors.add(d);d.setOnDismissListener(v->main.editors.remove(d));if(closed!=null)d.setOnCancelListener(v->closed.run());
        d.setOnShowListener(v->d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(w->{try{main.commit(action);confirm(w);main.render();
                d.dismiss();}catch(Exception e){toast(e.getMessage());return;}if(closed!=null)closed.run();}));d.show();return d;
    }
    /**
     * A full-screen form (Add transaction): Cancel and Save, and with [again] Save and add another, which saves, clears the form
     * for the next one (again) and stays open. [saved] runs after each save that worked. [focus]: the box the keyboard opens on.
     */
    void sheet(String title,LinearLayout f,Runnable action,Runnable saved,Runnable again,EditText focus){
        ScrollView scroll=new ScrollView(main);Button inForm=again!=null&&large()?button("Save and add another",()->{}):null; // large text: in the form (see large())
        if(inForm!=null)f.addView(inForm);scroll.addView(f);
        AlertDialog.Builder b=new AlertDialog.Builder(main).setTitle(title).setView(scroll).setNegativeButton("Cancel",null).setPositiveButton("Save",null);
        if(again!=null&&inForm==null)b.setNeutralButton("Save and add another",null);
        AlertDialog d=b.create();main.editors.add(d);d.setOnDismissListener(v->main.editors.remove(d));
        java.util.function.Predicate<View> save=w->{try{main.commit(action);}catch(Exception e){toast(e.getMessage());return false;}
            confirm(w);saved.run();main.render();return true;};
        View.OnClickListener next=w->{if(save.test(w)){again.run();scroll.scrollTo(0,0);}};
        d.setOnShowListener(v->{d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(w->{if(save.test(w))d.dismiss();});
            if(again!=null&&inForm==null)d.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(next);});
        if(inForm!=null)inForm.setOnClickListener(next);
        if(focus!=null){focus.requestFocus();d.getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE|android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);}
        d.show();d.getWindow().setLayout(-1,-1);
    }
    void onPick(Spinner s,Runnable changed){s.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onItemSelected(AdapterView<?> p,View v,int position,long id){changed.run();}public void onNothingSelected(AdapterView<?> p){}});}
}
