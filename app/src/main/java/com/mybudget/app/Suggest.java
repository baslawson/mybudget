package com.mybudget.app;

import android.content.Context;
import android.widget.*;
import java.util.*;
import java.util.function.Supplier;

/**
 * A text box that suggests values used before (payees, notes, groups). After one letter it lists the ones containing
 * what's typed anywhere, ignoring capitals; tapped while empty it lists them all. Anything new can still be typed.
 */
final class Suggest {
    private Suggest(){}
    /** Adds the box to [f]. [options] is asked again each time the box is focused, so it can follow the rest of the form. */
    static AutoCompleteTextView box(Context context,LinearLayout f,String hint,Supplier<List<String>> options){
        AutoCompleteTextView box=new AutoCompleteTextView(context);box.setHint(hint);box.setSingleLine(true);box.setThreshold(1);
        Matches adapter=new Matches(context,options.get());box.setAdapter(adapter);
        // Only on a tap (the window already has focus), not when a form opens with this box focused.
        Runnable showAll=()->{if(box.getText().length()==0&&box.isAttachedToWindow()&&box.hasWindowFocus()&&!adapter.all.isEmpty()){adapter.showAll();box.showDropDown();}};
        box.setOnFocusChangeListener((v,focused)->{if(!focused)return;adapter.all=new ArrayList<>(options.get());showAll.run();});
        box.setOnClickListener(v->showAll.run());
        f.addView(box,new LinearLayout.LayoutParams(-1,-2));return box;
    }
    // The default ArrayAdapter filter matches only word starts; this one matches anywhere, ignoring capitals.
    private static final class Matches extends ArrayAdapter<String>{
        volatile List<String> all;
        Matches(Context context,List<String> options){super(context,android.R.layout.simple_dropdown_item_1line,new ArrayList<>(options));all=new ArrayList<>(options);}
        void showAll(){setNotifyOnChange(false);clear();addAll(all);notifyDataSetChanged();}
        @Override public Filter getFilter(){return filter;}
        private final Filter filter=new Filter(){
            @Override protected FilterResults performFiltering(CharSequence typed){
                List<String> list=all,found=new ArrayList<>();String t=typed==null?"":typed.toString().trim().toLowerCase(Locale.ROOT);
                for(String s:list)if(t.isEmpty()||s.toLowerCase(Locale.ROOT).contains(t))found.add(s);
                FilterResults r=new FilterResults();r.values=found;r.count=found.size();return r;
            }
            @SuppressWarnings("unchecked") @Override protected void publishResults(CharSequence typed,FilterResults r){setNotifyOnChange(false);clear();addAll((List<String>)r.values);if(r.count>0)notifyDataSetChanged();else notifyDataSetInvalidated();}
        };
    }
}
