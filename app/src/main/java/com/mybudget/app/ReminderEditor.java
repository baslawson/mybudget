package com.mybudget.app;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import java.time.*;
import java.util.*;
import java.util.function.Supplier;

/**
 * A transaction form's Reminders section, as Planner's (RemindersSection): the "Reminders" heading, a warning with "Turn on"
 * while notifications are off, hint lines, one card per reminder (its time, which opens "Change reminder"; ✕ to remove it; when
 * it goes off; its sound), and "+ Add reminder": On the day at 9:00, 1 day before, 1 week before, Custom…, Pick date and time….
 * It edits copies: the form's save keeps them ([list], ids given then to new ones, see [assignIds]).
 */
final class ReminderEditor extends Ui {
    final List<Budget.Reminder> list=new ArrayList<>();
    private final Supplier<String> date;private LinearLayout box;
    /** [from]: the reminders it has now; [date]: the date they count from, as the form has it (it may change). */
    ReminderEditor(MainActivity main,List<Budget.Reminder> from,Supplier<String> date){super(main);this.date=date;for(Budget.Reminder r:from)list.add(r.copy());}
    void addTo(LinearLayout f){section(f,"Reminders");box=column();f.addView(box,new LinearLayout.LayoutParams(-1,-2));show();}
    /** Draws the section again (after a change, or when the form's date changed). */
    void show(){if(box==null)return;box.removeAllViews();LocalDate day;try{day=LocalDate.parse(date.get());}catch(Exception e){day=LocalDate.now();}
        ReminderSound def=ReminderSound.setting(main);
        if(!ReminderNotifications.notificationsEnabled(main)){LinearLayout row=new LinearLayout(main);row.setGravity(Gravity.CENTER_VERTICAL);
            row.addView(label("Notifications are off, so reminders won't show.",13,main.red,false),new LinearLayout.LayoutParams(0,-2,1));
            row.addView(button("Turn on",()->main.askNotifications(this::show)));box.addView(row);}
        box.addView(label("Reminders count from "+Reminders.time(Reminders.START,main)+" on the transaction's date.",12,main.muted,false));
        String hint=alarmHint(ReminderNotifications.exactAlarmsAllowed(main),!list.isEmpty(),ringsAny(def));if(hint!=null)box.addView(label(hint,12,main.muted,false));
        for(Budget.Reminder r:list)box.addView(row(r,day,def));
        Button add=button("+ Add reminder",()->choose(null));add.setBackground(bg(Color.TRANSPARENT));add.setTextColor(main.blue);add.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);
        box.addView(add,new LinearLayout.LayoutParams(-2,dp(48)));}
    private boolean ringsAny(ReminderSound def){for(Budget.Reminder r:list)if(ReminderSound.resolve(r.ring,r.ringSeconds,def).alarmSeconds()!=null)return true;return false;}
    /**
     * The warning under the reminders while exact alarms are off (the default from Android 14 for apps without USE_EXACT_ALARM):
     * they may come late, and one set to ring can't ring at all (Android starts the ringing alarm only from an exact alarm).
     */
    static String alarmHint(boolean exactAllowed,boolean hasReminder,boolean ring){if(exactAllowed||!hasReminder)return null;
        return ring?"Exact alarms are off: reminders may come late, and can't ring, only notify. Enable Alarms & reminders in app settings so they can ring."
            :"Android may deliver this reminder late. Enable Alarms & reminders in app settings for precise timing.";}
    /** One reminder as a card: its time (tap to change it), ✕, when it goes off, and its sound. */
    private View row(Budget.Reminder r,LocalDate day,ReminderSound def){LinearLayout card=column();card.setPadding(dp(12),dp(4),dp(4),dp(8));card.setBackground(surface(main.surface));
        String label=Reminders.label(r,main);LinearLayout top=new LinearLayout(main);top.setGravity(Gravity.CENTER_VERTICAL);
        Button change=button(label+" ▾",()->choose(r));change.setBackground(bg(Color.TRANSPARENT));change.setTextColor(main.ink);change.setTextSize(14);change.setTypeface(null,Typeface.BOLD);
        change.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);change.setPadding(0,0,0,0);change.setContentDescription("Change reminder: "+label);
        top.addView(change,new LinearLayout.LayoutParams(0,dp(48),1));
        Button remove=button("✕",()->{list.remove(r);tick(box);show();});remove.setBackground(bg(Color.TRANSPARENT));remove.setTextColor(main.muted);remove.setContentDescription("Remove reminder: "+label);
        top.addView(remove,new LinearLayout.LayoutParams(dp(48),dp(48)));card.addView(top);
        long at=Reminders.trigger(day,r,ZoneId.systemDefault()).toInstant().toEpochMilli();
        card.addView(label(Reminders.moment(at,main)+(at<=System.currentTimeMillis()?" · passed":""),13,at<=System.currentTimeMillis()?main.amber:main.muted,false));
        ReminderSound sound=ReminderSound.of(r.ring,r.ringSeconds);
        Button pick=button("🔔 "+sound.choiceLabel(def)+" ▾",()->{List<MoveMenu.Choice> c=new ArrayList<>();
            for(ReminderSound s:ReminderSound.values())c.add(new MoveMenu.Choice(MoveMenu.NONE,s.choiceLabel(def),false,()->{r.ring=s.ring;r.ringSeconds=s.seconds;show();}).selected(s==sound));
            MoveMenu.sheet(this,"Reminder sound","Default is set in Settings › Reminders",Collections.singletonList(c),null);});
        pick.setBackground(bg(Color.TRANSPARENT));pick.setTextColor(main.blue);pick.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);pick.setPadding(0,0,0,0);
        pick.setContentDescription("Sound: "+sound.choiceLabel(def));card.addView(pick,new LinearLayout.LayoutParams(-2,dp(48)));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,dp(6),0,0);card.setLayoutParams(p);return card;}

    /** "Add reminder" ([r] null) or "Change reminder: …" ([r]): the presets, Custom… and Pick date and time…. */
    private void choose(Budget.Reminder r){List<MoveMenu.Choice> c=new ArrayList<>();
        for(Object[] p:Reminders.PRESETS)c.add(new MoveMenu.Choice(MoveMenu.SNOOZE,((String)p[2]).replace("9:00",Reminders.time(Reminders.START,main)),false,()->put(r,(Integer)p[0],(String)p[1])).selected(r!=null&&r.amount==(Integer)p[0]&&r.unit.equals(p[1])));
        c.add(new MoveMenu.Choice(MoveMenu.EDIT,"Custom…",false,()->custom(r)));
        c.add(new MoveMenu.Choice(MoveMenu.LIST,"Pick date and time…",false,()->pickDate(r)));
        MoveMenu.sheet(this,r==null?"Add reminder":"Change reminder",r==null?"Counted from "+Reminders.time(Reminders.START,main)+" on its date":Reminders.label(r,main),Collections.singletonList(c),null);}
    /** Adds a reminder [amount] [unit] from 9:00, or changes [r] to it (its sound stays). The same time twice is one reminder. */
    private void put(Budget.Reminder r,int amount,String unit){
        for(Budget.Reminder o:list)if(o!=r&&o.offsetMinutes()==(long)amount*(unit.equals("days")?1440:unit.equals("hours")?60:1)&&(o.unit.equals("days"))==(unit.equals("days"))){toast("That reminder is already there.");return;}
        if(r==null){Budget.Reminder n=new Budget.Reminder(0,amount,unit);list.add(n);} // its id comes with the save (assignIds)
        else{r.amount=amount;r.unit=unit;}
        tick(box);show();}
    /** A number of minutes, hours or days, before or after 9:00 on the date (Planner's Custom reminder). */
    private void custom(Budget.Reminder r){LinearLayout f=form();EditText amount=field(f,"How long",true);amount.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        amount.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(4)});
        amount.setText(String.valueOf(r!=null&&r.amount!=0?Math.abs(r.amount):1));
        Spinner unit=spinner(f,"Unit",new String[]{"Minutes","Hours","Days"},r!=null?Arrays.asList(Budget.Reminder.UNITS).indexOf(r.unit):2);
        Spinner side=spinner(f,"When",new String[]{"Before 9:00 on the date".replace("9:00",Reminders.time(Reminders.START,main)),"After 9:00 on the date".replace("9:00",Reminders.time(Reminders.START,main))},r!=null&&r.amount<0?1:0);
        AlertDialog d=new AlertDialog.Builder(main).setTitle("Custom reminder").setView(f).setNegativeButton("Cancel",null).setPositiveButton(r==null?"Add reminder":"Change reminder",null).create();
        main.editors.add(d);d.setOnDismissListener(v->main.editors.remove(d));
        d.setOnShowListener(v->d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(w->{int n;try{n=Integer.parseInt(amount.getText().toString().trim());}catch(Exception e){n=0;}
            if(n<1){toast("Enter how long, 1 or more.");return;}String u=Budget.Reminder.UNITS[unit.getSelectedItemPosition()];
            if((long)n*(u.equals("days")?1440:u.equals("hours")?60:1)>Reminders.MAX_OFFSET_MINUTES){toast("That's too far from the date.");return;}
            d.dismiss();put(r,side.getSelectedItemPosition()==1?-n:n,u);}));d.show();}
    /** A reminder at a date and time, from the calendar then the clock, kept as the time from 9:00 on the date (so it moves with it). */
    private void pickDate(Budget.Reminder r){LocalDate day;try{day=LocalDate.parse(date.get());}catch(Exception e){day=LocalDate.now();}LocalDate from=day;
        LocalDateTime start=r!=null?Reminders.trigger(day,r,ZoneId.systemDefault()).toLocalDateTime():day.atTime(Reminders.START);
        int theme=main.darkTheme?R.style.DatePickerDialog_Dark:R.style.DatePickerDialog_Light;
        DatePickerDialog picker=new DatePickerDialog(main,theme,(p,y,m,dd)->{LocalDate chosen=LocalDate.of(y,m+1,dd);
            new TimePickerDialog(main,theme,(t,h,min)->{LocalDateTime at=chosen.atTime(h,min);
                if(at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()<=System.currentTimeMillis()){toast("Choose a time still to come.");return;}
                Object[] offset=Reminders.at(from,at,ZoneId.systemDefault());if(offset==null){toast("That's too far from the date.");return;}
                put(r,(Integer)offset[0],(String)offset[1]);},start.getHour(),start.getMinute(),android.text.format.DateFormat.is24HourFormat(main)).show();},
            start.getYear(),start.getMonthValue()-1,start.getDayOfMonth());
        picker.getDatePicker().setMinDate(System.currentTimeMillis()-1000);picker.show();}

    /** At the save: new reminders get their ids (from 1, never used before in this budget), on the budget being saved. */
    List<Budget.Reminder> assignIds(Budget b){List<Budget.Reminder> out=new ArrayList<>();for(Budget.Reminder r:list){Budget.Reminder c=r.copy();if(c.id<=0)c.id=b.newReminderId();out.add(c);}return out;}
}
