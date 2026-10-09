package com.mybudget.app;

import android.animation.ValueAnimator;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipDescription;
import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.SystemClock;
import android.view.*;
import android.view.accessibility.AccessibilityManager;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.*;
import java.util.*;
import java.util.function.Consumer;

/**
 * Sections of a screen (the Reports cards, Home's parts, accounts) that can be held and dragged up or down: the others make way
 * and the screen scrolls at its top and bottom edges. Held and let go without moving (with TalkBack, held, or from a heading's
 * actions): a sheet with Move to top, Move above/below its neighbours, Move to bottom and Default order; the ⋮ by each title
 * opens the same choices as a menu there (MoveMenu). The order is saved on this device under [pref] (CardOrder), not in the backup.
 */
final class Movable {
    private static final String MIME="application/x-mybudget-section";
    private final Ui ui;private final MainActivity main;private final String pref,where;private final List<String> all;
    private final List<Section> shown=new ArrayList<>(); // in screen order, next to each other

    /** [where]: the screen (or group) named in the sheet, "3 of 8 on Reports". */
    Movable(Ui ui,String pref,String where,List<String> all){this.ui=ui;main=ui.main;this.pref=pref;this.where=where;this.all=all;}

    /** Builds each section with [build] (it may add nothing) in the saved order, each wrapped so it can be moved. */
    void build(Consumer<String> build){
        for(String key:CardOrder.order(saved(),all)){int at=main.content.getChildCount();build.accept(key);int n=main.content.getChildCount()-at;if(n<=0)continue;
            Section s=new Section(key);for(int i=0;i<n;i++){View v=main.content.getChildAt(at);main.content.removeViewAt(at);s.addView(v);} // each keeps its layout
            main.content.addView(s,at,new LinearLayout.LayoutParams(-1,-2));shown.add(s);s.title=heading(s);actions(s);dots(s);}
        if(shown.size()<2&&saved()==null)for(Section s:shown)if(s.dots!=null)s.dots.setVisibility(View.GONE); // one card alone: nothing to move it past
    }
    /** The ⋮ beside the section's title, opening its menu right there. */
    private void dots(Section s){TextView title=s.title;if(title==null)return;View[] at=new View[1];
        at[0]=MoveMenu.dots(ui,title.getCurrentTextColor(),"Move options for "+name(s),()->{int i=shown.indexOf(s);if(i<0)return;
            MoveMenu.show(ui,at[0],name(s),(i+1)+" of "+shown.size(),choices(s));});
        MoveMenu.besides(ui,title,at[0]);s.dots=at[0];}
    /** What [s] can do, by its neighbours' names: only moves that change something, each once ("Move to top" from 3rd down). */
    private List<MoveMenu.Choice> choices(Section s){List<MoveMenu.Choice> c=new ArrayList<>();int i=shown.indexOf(s),last=shown.size()-1;
        if(i>1)c.add(new MoveMenu.Choice(MoveMenu.TOP,"Move to top",false,()->moveTo(s,0,"Moved to the top")));
        if(i>0)c.add(new MoveMenu.Choice(MoveMenu.UP,"Move above "+name(shown.get(i-1)),false,()->moveTo(s,i-1,"Moved up")));
        if(i>=0&&i<last)c.add(new MoveMenu.Choice(MoveMenu.DOWN,"Move below "+name(shown.get(i+1)),false,()->moveTo(s,i+1,"Moved down")));
        if(i>=0&&i<last-1)c.add(new MoveMenu.Choice(MoveMenu.BOTTOM,"Move to bottom",false,()->moveTo(s,last,"Moved to the bottom")));
        if(saved()!=null)c.add(new MoveMenu.Choice(MoveMenu.RESET,"Default order",true,this::defaultOrder));
        return c;}
    private String saved(){return main.prefs().getString(pref,null);}
    private void save(){save(keys());}
    private void save(List<String> keys){
        String order=CardOrder.save(CardOrder.moved(CardOrder.order(saved(),all),keys),all);
        if(order==null)main.prefs().edit().remove(pref).apply();else main.prefs().edit().putString(pref,order).apply();}
    private List<String> keys(){List<String> keys=new ArrayList<>();for(Section s:shown)keys.add(s.key);return keys;}
    /** Puts [s] at [to] among the sections shown (CardOrder.place). */
    private void move(Section s,int to){List<String> order=CardOrder.place(keys(),s.key,to);if(order.equals(keys()))return;
        int base=main.content.indexOfChild(shown.get(0));shown.remove(s);shown.add(order.indexOf(s.key),s);
        main.content.removeView(s);main.content.addView(s,base+shown.indexOf(s));}
    /** From the sheet or TalkBack: moves [s], saves, brings it into view and flashes it so it's clear where it went. */
    private boolean moveTo(Section s,int to,String said){
        if(s.getParent()!=main.content){List<String> order=CardOrder.place(keys(),s.key,to);if(order.equals(keys()))return false; // the screen was redrawn under the sheet (rotation, a reload): its sections are gone, so move by the saved order
            save(order);main.render();return true;}
        int from=shown.indexOf(s);move(s,to);if(shown.indexOf(s)==from)return false;save();
        ScrollView scroll=(ScrollView)main.content.getParent();
        ViewTreeObserver tree=main.content.getViewTreeObserver(); // once laid out in its new place (a post can run before that)
        tree.addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener(){@Override public boolean onPreDraw(){
            if(main.content.getViewTreeObserver().isAlive())main.content.getViewTreeObserver().removeOnPreDrawListener(this);
            scroll.smoothScrollTo(0,Math.max(0,s.getTop()-ui.dp(12)));flash(s);return true;}});
        s.announceForAccessibility(said);return true;}
    /** A brief glow over the moved section (light over dark cards, blue over light ones), rounded like its cards. */
    private void flash(Section s){if(!Ui.motion())return;int glowColour=main.darkTheme?main.ink:main.blue,peak=main.darkTheme?60:80;
        GradientDrawable glow=new GradientDrawable();glow.setCornerRadius(ui.dp(16));glow.setColor(Ui.tint(glowColour,0));glow.setBounds(0,0,s.getWidth(),s.getHeight());
        s.getOverlay().add(glow);ValueAnimator a=ValueAnimator.ofInt(0,peak,0);a.setDuration(1000);a.setStartDelay(200);
        a.addUpdateListener(x->glow.setColor(Ui.tint(glowColour,(int)x.getAnimatedValue())));
        a.addListener(new android.animation.AnimatorListenerAdapter(){@Override public void onAnimationEnd(android.animation.Animator x){s.getOverlay().remove(glow);}});a.start();}
    private void defaultOrder(){main.prefs().edit().remove(pref).apply();main.render();}

    /** Held: drag it; with TalkBack (no dragging), or if the drag can't start, the sheet. */
    private void hold(Section s){
        AccessibilityManager a=(AccessibilityManager)main.getSystemService(Context.ACCESSIBILITY_SERVICE);
        if(a!=null&&a.isTouchExplorationEnabled()||s.getWidth()<=0||s.getHeight()<=0){menu(s);return;}
        Drag d=new Drag(s);main.content.setOnDragListener(d);
        ClipData clip=new ClipData(new ClipDescription(s.key,new String[]{MIME}),new ClipData.Item(s.key));
        if(!s.startDragAndDrop(clip,new Shadow(s),null,0)){main.content.setOnDragListener(null);menu(s);return;}
        s.setAlpha(0.35f);} // its place, while the shadow follows the finger

    /** The sheet from the bottom of the screen: the section's name and place, then each move it can make. */
    private void menu(Section s){int i=shown.indexOf(s);List<MoveMenu.Choice> c=choices(s);if(c.isEmpty())return; // hunt 27 H3: a card alone has no moves
        MoveMenu.sheet(ui,name(s),(i+1)+" of "+shown.size()+" on "+where,Collections.singletonList(c),"Tip: hold and drag to move it anywhere.");}
    /** The section's name for the sheet: its title, in sentence case if shown in capitals ("TO BUDGET" → "To budget"). */
    private static String name(Section s){TextView h=s.title;if(h==null)return "Move";String t=h.getText().toString();
        return t.equals(t.toUpperCase(Locale.ROOT))&&!t.equals(t.toLowerCase(Locale.ROOT))?t.charAt(0)+t.substring(1).toLowerCase(Locale.ROOT):t;}
    /** The section's title (its first heading, else its first text), which carries the TalkBack actions. */
    private static TextView heading(View v){TextView first=null;ArrayDeque<View> todo=new ArrayDeque<>();todo.add(v);
        while(!todo.isEmpty()){View x=todo.poll();
            if(x instanceof TextView&&!(x instanceof Button)&&x.getImportantForAccessibility()!=View.IMPORTANT_FOR_ACCESSIBILITY_NO){
                if(android.os.Build.VERSION.SDK_INT>=28&&x.isAccessibilityHeading())return (TextView)x;if(first==null)first=(TextView)x;}
            if(x instanceof ViewGroup)for(int i=0;i<((ViewGroup)x).getChildCount();i++)todo.add(((ViewGroup)x).getChildAt(i));}
        return first;}
    private void actions(Section s){TextView title=s.title;if(title==null)return;
        View h=title;for(View up=title;up!=s&&up!=null;up=(View)up.getParent())if(up.isClickable())h=up; // inside a clickable card (To budget) TalkBack focuses the card, so the actions go there
        h.setAccessibilityDelegate(new View.AccessibilityDelegate(){
            @Override public void onInitializeAccessibilityNodeInfo(View v,AccessibilityNodeInfo info){super.onInitializeAccessibilityNodeInfo(v,info);int i=shown.indexOf(s);
                if(i>0){info.addAction(new AccessibilityNodeInfo.AccessibilityAction(R.id.move_top,"Move to top"));info.addAction(new AccessibilityNodeInfo.AccessibilityAction(R.id.move_up,"Move above "+name(shown.get(i-1))));}
                if(i>=0&&i<shown.size()-1){info.addAction(new AccessibilityNodeInfo.AccessibilityAction(R.id.move_down,"Move below "+name(shown.get(i+1))));info.addAction(new AccessibilityNodeInfo.AccessibilityAction(R.id.move_bottom,"Move to bottom"));}
                if(saved()!=null)info.addAction(new AccessibilityNodeInfo.AccessibilityAction(R.id.move_default,"Default order"));}
            @Override public boolean performAccessibilityAction(View v,int action,android.os.Bundle args){int i=shown.indexOf(s);
                if(action==R.id.move_top)return moveTo(s,0,"Moved to the top");if(action==R.id.move_up)return moveTo(s,i-1,"Moved up");
                if(action==R.id.move_down)return moveTo(s,i+1,"Moved down");if(action==R.id.move_bottom)return moveTo(s,shown.size()-1,"Moved to the bottom");
                if(action==R.id.move_default){defaultOrder();return true;}return super.performAccessibilityAction(v,action,args);}});}

    /** A movable section: a hold anywhere in it (the finger kept still) picks it up, even over a button or a chart. */
    private final class Section extends LinearLayout {
        final String key;float downX,downY;boolean held;TextView title;View dots; // title: found before the ⋮ goes in beside it (that moves it a level deeper)
        private final Runnable longPress=()->{held=true;performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);cancelChildren();hold(this);};
        Section(String key){super(main);this.key=key;setOrientation(VERTICAL);}
        private void cancelChildren(){long now=SystemClock.uptimeMillis();MotionEvent c=MotionEvent.obtain(now,now,MotionEvent.ACTION_CANCEL,downX,downY,0);
            super.dispatchTouchEvent(c);c.recycle();}
        @Override public boolean dispatchTouchEvent(MotionEvent e){
            switch(e.getActionMasked()){
                case MotionEvent.ACTION_DOWN:downX=e.getX();downY=e.getY();held=false;removeCallbacks(longPress);postDelayed(longPress,ViewConfiguration.getLongPressTimeout());
                    super.dispatchTouchEvent(e);return true; // the rest of the touch comes here, so a lifted finger stops the hold
                case MotionEvent.ACTION_MOVE:if(Math.hypot(e.getX()-downX,e.getY()-downY)>ViewConfiguration.get(main).getScaledTouchSlop())removeCallbacks(longPress);break;
                case MotionEvent.ACTION_UP:case MotionEvent.ACTION_CANCEL:case MotionEvent.ACTION_POINTER_DOWN:removeCallbacks(longPress);break;}
            if(held)return true; // the rest of this touch belongs to the drag
            return super.dispatchTouchEvent(e);}
        @Override protected void onDetachedFromWindow(){removeCallbacks(longPress);super.onDetachedFromWindow();}
    }

    /** The part of the section around the finger, at most 160dp tall (a long card would cover the screen). */
    private final class Shadow extends View.DragShadowBuilder {
        final int top,height;final int x,y;
        Shadow(Section s){super(s);height=Math.min(s.getHeight(),ui.dp(160));top=(int)Math.max(0,Math.min(s.downY-height/2f,s.getHeight()-height));
            x=(int)s.downX;y=(int)s.downY-top;}
        @Override public void onProvideShadowMetrics(Point size,Point touch){size.set(getView().getWidth(),height);touch.set(x,y);}
        @Override public void onDrawShadow(Canvas c){c.translate(0,-top);getView().draw(c);}
    }

    /** Follows the finger over the screen's content: the held section takes the place it is over; near an edge, the screen scrolls. */
    private final class Drag implements View.OnDragListener {
        final Section s;final ScrollView scroll=(ScrollView)main.content.getParent();final List<Section> before=new ArrayList<>(shown);
        float y=Float.NaN,startY=Float.NaN;int atScroll,edge;boolean moved;
        final Runnable keepScrolling=new Runnable(){@Override public void run(){if(edge==0)return;scroll.scrollBy(0,edge*ui.dp(10));
            if(!Float.isNaN(y))place(y+scroll.getScrollY()-atScroll);scroll.postOnAnimation(this);}};
        Drag(Section s){this.s=s;}
        void place(float at){int to=0,was=shown.indexOf(s);for(Section o:shown)if(o!=s&&at>o.getTop()+o.getHeight()/2f)to++;move(s,to);
            if(shown.indexOf(s)!=was)save();} // saved as it goes: a redraw mid-drag (rotation) never sends this listener the drag's end
        @Override public boolean onDrag(View v,DragEvent e){
            switch(e.getAction()){
                case DragEvent.ACTION_DRAG_STARTED:return e.getClipDescription()!=null&&e.getClipDescription().hasMimeType(MIME);
                case DragEvent.ACTION_DRAG_LOCATION:y=e.getY();atScroll=scroll.getScrollY();if(Float.isNaN(startY))startY=y;if(Math.abs(y-startY)>ui.dp(16))moved=true;
                    place(y);if(!moved)return true;
                    {float onScreen=y-atScroll;int zone=ui.dp(72),was=edge;edge=onScreen<zone?-1:onScreen>scroll.getHeight()-zone?1:0;
                    if(edge!=0&&was==0)scroll.postOnAnimation(keepScrolling);}return true;
                case DragEvent.ACTION_DRAG_ENDED:edge=0;s.setAlpha(1f);main.content.setOnDragListener(null);
                    if(!shown.equals(before)){save();s.announceForAccessibility("Moved");}else if(!moved)menu(s);return true;
                default:return true;}
        }
    }
}
