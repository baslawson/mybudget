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
 * actions): a sheet with Move to top, Move up, Move down, Move to bottom and Default order. The order is saved on this device
 * under [pref] (CardOrder), not in the backup.
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
            main.content.addView(s,at,new LinearLayout.LayoutParams(-1,-2));shown.add(s);actions(s);}
    }
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
    private void menu(Section s){
        int i=shown.indexOf(s),last=shown.size()-1;
        LinearLayout sheet=new LinearLayout(main);sheet.setOrientation(LinearLayout.VERTICAL);int side=ui.dp(20);sheet.setPadding(side,ui.dp(10),side,ui.dp(16));
        GradientDrawable bg=new GradientDrawable();bg.setColor(main.surface);float r=ui.dp(24);bg.setCornerRadii(new float[]{r,r,r,r,0,0,0,0});sheet.setBackground(bg);
        View handle=new View(main);GradientDrawable h=new GradientDrawable();h.setColor(Ui.tint(main.muted,110));h.setCornerRadius(ui.dp(2));handle.setBackground(h);
        handle.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(ui.dp(36),ui.dp(4));
        hp.gravity=Gravity.CENTER_HORIZONTAL;hp.bottomMargin=ui.dp(14);sheet.addView(handle,hp);
        TextView title=ui.label(name(s),20,main.ink,true);Ui.heading(title);sheet.addView(title);
        sheet.addView(ui.label((i+1)+" of "+shown.size()+" on "+where,13,main.muted,false));
        View space=new View(main);sheet.addView(space,new LinearLayout.LayoutParams(1,ui.dp(8)));
        AlertDialog d=new AlertDialog.Builder(main).create();
        if(i>0){row(sheet,d,Icon.TOP,"Move to top",main.ink,()->moveTo(s,0,"Moved to the top"));row(sheet,d,Icon.UP,"Move up",main.ink,()->moveTo(s,i-1,"Moved up"));}
        if(i<last){row(sheet,d,Icon.DOWN,"Move down",main.ink,()->moveTo(s,i+1,"Moved down"));row(sheet,d,Icon.BOTTOM,"Move to bottom",main.ink,()->moveTo(s,last,"Moved to the bottom"));}
        if(saved()!=null){View line=new View(main);line.setBackgroundColor(Ui.tint(main.ink,main.darkTheme?26:18));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,ui.dp(1));
            lp.setMargins(0,ui.dp(6),0,ui.dp(6));sheet.addView(line,lp);row(sheet,d,Icon.RESET,"Default order",main.muted,this::defaultOrder);}
        TextView tip=ui.label("Tip: hold and drag to move it anywhere.",12,main.muted,false);tip.setPadding(0,ui.dp(10),0,0);sheet.addView(tip);
        WindowInsets screen=main.getWindow().getDecorView().getRootWindowInsets();int bar=android.os.Build.VERSION.SDK_INT>=30&&screen!=null?screen.getInsets(WindowInsets.Type.navigationBars()).bottom:0;
        sheet.setPadding(side,ui.dp(10),side,ui.dp(16)+bar); // the rows stay above the gesture bar the sheet runs under
        ScrollView holder=new ScrollView(main);holder.addView(sheet);d.setView(holder,0,0,0,0); // scrolls when it doesn't fit (landscape, large text)d.setCanceledOnTouchOutside(true);main.editors.add(d);d.setOnDismissListener(x->main.editors.remove(d));d.show();
        Window w=d.getWindow();if(w!=null){w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));w.setGravity(Gravity.BOTTOM);
            int wide=Math.min(main.getResources().getDisplayMetrics().widthPixels,ui.dp(560));w.setLayout(wide,ViewGroup.LayoutParams.WRAP_CONTENT);
            w.setWindowAnimations(0);w.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);w.setNavigationBarColor(main.surface);if(android.os.Build.VERSION.SDK_INT>=29)w.setNavigationBarContrastEnforced(false);
            if(android.os.Build.VERSION.SDK_INT>=30){w.setDecorFitsSystemWindows(false);WindowManager.LayoutParams lp=w.getAttributes();lp.setFitInsetsTypes(0);w.setAttributes(lp);}} // the sheet runs under the gesture bar (its padding keeps the rows above it)
        if(Ui.motion()){holder.setTranslationY(ui.dp(360));holder.animate().translationY(0).setDuration(240).setInterpolator(new android.view.animation.DecelerateInterpolator(2f)).start();}
    }
    /** One choice in the sheet: a round tinted icon and its label, 56dp tall; it closes the sheet, then acts. */
    private void row(LinearLayout sheet,AlertDialog d,int icon,String text,int color,Runnable action){
        LinearLayout row=new LinearLayout(main);row.setGravity(Gravity.CENTER_VERTICAL);row.setMinimumHeight(ui.dp(56));row.setPadding(ui.dp(4),0,ui.dp(4),0);
        row.addView(new Icon(icon,color==main.muted?main.muted:main.blue),new LinearLayout.LayoutParams(ui.dp(40),ui.dp(40)));
        TextView t=ui.label(text,16,color,false);t.setPadding(ui.dp(16),0,0,0);row.addView(t,new LinearLayout.LayoutParams(0,-2,1));
        GradientDrawable mask=new GradientDrawable();mask.setColor(Color.WHITE);mask.setCornerRadius(ui.dp(14));
        row.setForeground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(Ui.tint(main.blue,main.darkTheme?56:36)),null,mask));
        row.setClickable(true);row.setFocusable(true);row.setOnClickListener(v->{Ui.tick(v);d.dismiss();action.run();});
        sheet.addView(row,new LinearLayout.LayoutParams(-1,-2));}
    /** The sheet's icons, drawn (the arrow-to-bar symbols are missing from some phones' fonts). */
    private final class Icon extends View {
        static final int TOP=0,UP=1,DOWN=2,BOTTOM=3,RESET=4;
        final int kind;final Paint fill=new Paint(Paint.ANTI_ALIAS_FLAG),pen=new Paint(Paint.ANTI_ALIAS_FLAG);
        Icon(int kind,int color){super(main);this.kind=kind;fill.setColor(Ui.tint(color,main.darkTheme?52:30));pen.setColor(color);pen.setStyle(Paint.Style.STROKE);
            pen.setStrokeWidth(ui.dp(2));pen.setStrokeCap(Paint.Cap.ROUND);pen.setStrokeJoin(Paint.Join.ROUND);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
        @Override protected void onDraw(Canvas c){float x=getWidth()/2f,y=getHeight()/2f,u=ui.dp(1);c.drawCircle(x,y,Math.min(x,y),fill);
            if(kind==RESET){RectF o=new RectF(x-7*u,y-7*u,x+7*u,y+7*u);c.drawArc(o,-90,290,false,pen); // ↺: from the top, round to the upper left
                Paint head=new Paint(Paint.ANTI_ALIAS_FLAG);head.setColor(pen.getColor());float ty=y-7*u; // a filled head at the top, pointing left
                Path p=new Path();p.moveTo(x-4*u,ty);p.lineTo(x+2*u,ty-4*u);p.lineTo(x+2*u,ty+4*u);p.close();c.drawPath(p,head);return;}
            float dir=kind==TOP||kind==UP?-1:1,shift=kind==TOP||kind==BOTTOM?2*u*-dir:0; // arrows point up (-1) or down (1); with a bar they move off it
            float tip=y+dir*6*u+shift,tail=y-dir*7*u+shift;c.drawLine(x,tail,x,tip,pen);
            Path head=new Path();head.moveTo(x-5*u,tip-dir*5*u);head.lineTo(x,tip);head.lineTo(x+5*u,tip-dir*5*u);c.drawPath(head,pen);
            if(kind==TOP||kind==BOTTOM){float bar=y+dir*10*u;c.drawLine(x-7*u,bar,x+7*u,bar,pen);}}
    }
    /** The section's name for the sheet: its title, in sentence case if shown in capitals ("TO BUDGET" → "To budget"). */
    private static String name(Section s){TextView h=heading(s);if(h==null)return "Move";String t=h.getText().toString();
        return t.equals(t.toUpperCase(Locale.ROOT))&&!t.equals(t.toLowerCase(Locale.ROOT))?t.charAt(0)+t.substring(1).toLowerCase(Locale.ROOT):t;}
    /** The section's title (its first heading, else its first text), which carries the TalkBack actions. */
    private static TextView heading(View v){TextView first=null;ArrayDeque<View> todo=new ArrayDeque<>();todo.add(v);
        while(!todo.isEmpty()){View x=todo.poll();
            if(x instanceof TextView&&!(x instanceof Button)&&x.getImportantForAccessibility()!=View.IMPORTANT_FOR_ACCESSIBILITY_NO){
                if(android.os.Build.VERSION.SDK_INT>=28&&x.isAccessibilityHeading())return (TextView)x;if(first==null)first=(TextView)x;}
            if(x instanceof ViewGroup)for(int i=0;i<((ViewGroup)x).getChildCount();i++)todo.add(((ViewGroup)x).getChildAt(i));}
        return first;}
    private void actions(Section s){TextView title=heading(s);if(title==null)return;
        View h=title;for(View up=title;up!=s&&up!=null;up=(View)up.getParent())if(up.isClickable())h=up; // inside a clickable card (To budget) TalkBack focuses the card, so the actions go there
        h.setAccessibilityDelegate(new View.AccessibilityDelegate(){
            @Override public void onInitializeAccessibilityNodeInfo(View v,AccessibilityNodeInfo info){super.onInitializeAccessibilityNodeInfo(v,info);int i=shown.indexOf(s);
                if(i>0){info.addAction(new AccessibilityNodeInfo.AccessibilityAction(R.id.move_top,"Move to top"));info.addAction(new AccessibilityNodeInfo.AccessibilityAction(R.id.move_up,"Move up"));}
                if(i<shown.size()-1){info.addAction(new AccessibilityNodeInfo.AccessibilityAction(R.id.move_down,"Move down"));info.addAction(new AccessibilityNodeInfo.AccessibilityAction(R.id.move_bottom,"Move to bottom"));}
                if(saved()!=null)info.addAction(new AccessibilityNodeInfo.AccessibilityAction(R.id.move_default,"Default order"));}
            @Override public boolean performAccessibilityAction(View v,int action,android.os.Bundle args){int i=shown.indexOf(s);
                if(action==R.id.move_top)return moveTo(s,0,"Moved to the top");if(action==R.id.move_up)return moveTo(s,i-1,"Moved up");
                if(action==R.id.move_down)return moveTo(s,i+1,"Moved down");if(action==R.id.move_bottom)return moveTo(s,shown.size()-1,"Moved to the bottom");
                if(action==R.id.move_default){defaultOrder();return true;}return super.performAccessibilityAction(v,action,args);}});}

    /** A movable section: a hold anywhere in it (the finger kept still) picks it up, even over a button or a chart. */
    private final class Section extends LinearLayout {
        final String key;float downX,downY;boolean held;
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
