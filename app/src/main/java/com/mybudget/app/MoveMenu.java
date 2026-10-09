package com.mybudget.app;

import android.content.res.ColorStateList;
import android.graphics.*;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.*;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.*;
import java.util.*;

/**
 * The ⋮ at the top right of a card that can be moved (Home, Accounts, Reports) or of a group on Budget, and the menu it opens
 * right there: the card's name and place, then each move it can make (Move to top, Move above …, Move below …, Move to bottom)
 * and, set apart, the quieter choices (Default order). The bottom sheet a hold opens (Movable) uses the same rows and icons.
 */
final class MoveMenu {
    static final int TOP=0,UP=1,DOWN=2,BOTTOM=3,RESET=4,EDIT=5;
    /** One choice: its icon, words and action; [quiet] ones come last, under a line, in the muted colour. */
    static final class Choice {
        final int icon;final String text;final boolean quiet;final Runnable action;
        Choice(int icon,String text,boolean quiet,Runnable action){this.icon=icon;this.text=text;this.quiet=quiet;this.action=action;}
    }
    private MoveMenu(){}

    /** The ⋮ button (48dp to touch, drawn dots in [colour]); TalkBack reads [said]. */
    static View dots(Ui ui,int colour,String said,Runnable open){
        Paint dot=new Paint(Paint.ANTI_ALIAS_FLAG);dot.setColor(colour);
        View b=new View(ui.main){@Override protected void onDraw(Canvas c){float x=getWidth()/2f,y=getHeight()/2f,u=ui.dp(1);
            for(int i=-1;i<=1;i++)c.drawCircle(x,y+i*6*u,2.2f*u,dot);}};
        b.setBackground(new RippleDrawable(ColorStateList.valueOf(Ui.tint(ui.main.blue,ui.main.darkTheme?70:44)),null,null)); // round, past the edges
        b.setClickable(true);b.setFocusable(true);b.setContentDescription(said);
        b.setAccessibilityDelegate(new View.AccessibilityDelegate(){@Override public void onInitializeAccessibilityNodeInfo(View v,AccessibilityNodeInfo info){
            super.onInitializeAccessibilityNodeInfo(v,info);info.setClassName(Button.class.getName());}});
        b.setOnClickListener(v->{Ui.tick(v);open.run();});return b;}
    /** Lays [dots] out after [title]: at the end of its row when it has one, else in a new full-width row with it. */
    static void besides(Ui ui,TextView title,View dots){ViewGroup p=(ViewGroup)title.getParent();if(p==null)return;
        LinearLayout.LayoutParams own=new LinearLayout.LayoutParams(ui.dp(48),ui.dp(48));own.gravity=Gravity.CENTER_VERTICAL;own.setMarginEnd(-ui.dp(10)); // the dots line up with the card's text edge
        if(p instanceof LinearLayout&&((LinearLayout)p).getOrientation()==LinearLayout.HORIZONTAL){p.addView(dots,own);return;}
        int at=p.indexOfChild(title);ViewGroup.LayoutParams was=title.getLayoutParams();p.removeViewAt(at);
        LinearLayout row=new LinearLayout(ui.main);row.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(title,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));row.addView(dots,own);
        LinearLayout.LayoutParams lp=was instanceof ViewGroup.MarginLayoutParams?new LinearLayout.LayoutParams((ViewGroup.MarginLayoutParams)was):new LinearLayout.LayoutParams(was);
        lp.width=ViewGroup.LayoutParams.MATCH_PARENT;lp.height=ViewGroup.LayoutParams.WRAP_CONTENT;p.addView(row,at,lp);}

    /** The menu at [anchor] (its right edges lined up; above it when there's no room below). */
    static void show(Ui ui,View anchor,String title,String place,List<Choice> choices){MainActivity main=ui.main;
        LinearLayout box=new LinearLayout(main);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(ui.dp(6),ui.dp(10),ui.dp(6),ui.dp(8));
        TextView head=ui.label(title.toUpperCase(Locale.ROOT)+"  ·  "+place,11,main.muted,true);head.setLetterSpacing(0.08f);head.setSingleLine(true);
        head.setEllipsize(android.text.TextUtils.TruncateAt.END);head.setPadding(ui.dp(14),ui.dp(2),ui.dp(14),ui.dp(8));head.setContentDescription(title+", "+place);Ui.heading(head);box.addView(head);
        PopupWindow p=new PopupWindow(box,ViewGroup.LayoutParams.WRAP_CONTENT,ViewGroup.LayoutParams.WRAP_CONTENT,true);
        rows(ui,box,choices,ui.dp(48),15,ui.dp(32),p::dismiss);
        // Dark: a shade lighter than the cards (a shadow barely shows on dark), with a clearer edge.
        int raised=main.darkTheme?mix(main.surface,main.ink,0.07f):main.surface;
        GradientDrawable bg=new GradientDrawable();bg.setColor(raised);bg.setCornerRadius(ui.dp(20));bg.setStroke(ui.dp(1),Ui.tint(main.ink,main.darkTheme?44:16));
        p.setBackgroundDrawable(bg);p.setElevation(ui.dp(14));p.setOutsideTouchable(true);p.setAnimationStyle(0);
        int screen=main.getResources().getDisplayMetrics().widthPixels,most=Math.min(screen-ui.dp(32),ui.dp(360));
        box.setMinimumWidth(Math.min(ui.dp(240),most));box.measure(View.MeasureSpec.makeMeasureSpec(most,View.MeasureSpec.AT_MOST),View.MeasureSpec.UNSPECIFIED);
        int wide=Math.min(box.getMeasuredWidth(),most);p.setWidth(wide);
        box.measure(View.MeasureSpec.makeMeasureSpec(wide,View.MeasureSpec.EXACTLY),View.MeasureSpec.UNSPECIFIED);p.setHeight(box.getMeasuredHeight()); // a known height, so it opens above the ⋮ when there's no room below
        p.showAsDropDown(anchor,-ui.dp(4),-ui.dp(6),Gravity.END);
        if(Ui.motion()){box.setPivotX(p.getWidth());box.setPivotY(0);box.setAlpha(0f);box.setScaleX(0.92f);box.setScaleY(0.92f);
            box.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(160).setInterpolator(new android.view.animation.DecelerateInterpolator(2f)).start();}
    }
    /** [a] moved [part] of the way to [b] (opaque). */
    private static int mix(int a,int b,float part){return Color.rgb(Math.round(Color.red(a)+(Color.red(b)-Color.red(a))*part),
        Math.round(Color.green(a)+(Color.green(b)-Color.green(a))*part),Math.round(Color.blue(a)+(Color.blue(b)-Color.blue(a))*part));}
    /** Adds [choices] to [into]: [height] tall rows, [icon] round icons; a line before the first quiet one. Each runs [close] first. */
    static void rows(Ui ui,LinearLayout into,List<Choice> choices,int height,int size,int icon,Runnable close){MainActivity main=ui.main;boolean lined=false;
        for(Choice c:choices){
            if(c.quiet&&!lined&&into.getChildCount()>1){View line=new View(main);line.setBackgroundColor(Ui.tint(main.ink,main.darkTheme?26:18));
                LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,ui.dp(1));lp.setMargins(ui.dp(12),ui.dp(6),ui.dp(12),ui.dp(6));into.addView(line,lp);lined=true;}
            int colour=c.quiet?main.muted:main.ink;
            LinearLayout row=new LinearLayout(main);row.setGravity(Gravity.CENTER_VERTICAL);row.setMinimumHeight(height);row.setPadding(ui.dp(10),0,ui.dp(14),0);
            row.addView(new Icon(ui,c.icon,c.quiet?main.muted:main.blue),new LinearLayout.LayoutParams(icon,icon));
            TextView t=ui.label(c.text,size,colour,false);t.setMaxLines(2);t.setEllipsize(android.text.TextUtils.TruncateAt.END);t.setPadding(ui.dp(14),ui.dp(6),0,ui.dp(6));
            row.addView(t,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
            GradientDrawable mask=new GradientDrawable();mask.setColor(Color.WHITE);mask.setCornerRadius(ui.dp(14));
            row.setForeground(new RippleDrawable(ColorStateList.valueOf(Ui.tint(main.blue,main.darkTheme?56:36)),null,mask));
            row.setClickable(true);row.setFocusable(true);row.setOnClickListener(v->{Ui.tick(v);close.run();c.action.run();});
            into.addView(row,new LinearLayout.LayoutParams(-1,ViewGroup.LayoutParams.WRAP_CONTENT));}
    }

    /** The menus' icons in a tinted circle, drawn (the arrow-to-bar symbols are missing from some phones' fonts). */
    static final class Icon extends View {
        final int kind;final Ui ui;final Paint fill=new Paint(Paint.ANTI_ALIAS_FLAG),pen=new Paint(Paint.ANTI_ALIAS_FLAG);
        Icon(Ui ui,int kind,int color){super(ui.main);this.ui=ui;this.kind=kind;fill.setColor(Ui.tint(color,ui.main.darkTheme?52:30));pen.setColor(color);pen.setStyle(Paint.Style.STROKE);
            pen.setStrokeWidth(ui.dp(2));pen.setStrokeCap(Paint.Cap.ROUND);pen.setStrokeJoin(Paint.Join.ROUND);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
        @Override protected void onDraw(Canvas c){float x=getWidth()/2f,y=getHeight()/2f,u=ui.dp(1)*Math.min(getWidth(),getHeight())/(float)ui.dp(40);c.drawCircle(x,y,Math.min(x,y),fill);
            if(kind==RESET){RectF o=new RectF(x-7*u,y-7*u,x+7*u,y+7*u);c.drawArc(o,-90,290,false,pen); // ↺: from the top, round to the upper left
                Paint head=new Paint(Paint.ANTI_ALIAS_FLAG);head.setColor(pen.getColor());float ty=y-7*u; // a filled head at the top, pointing left
                Path p=new Path();p.moveTo(x-4*u,ty);p.lineTo(x+2*u,ty-4*u);p.lineTo(x+2*u,ty+4*u);p.close();c.drawPath(p,head);return;}
            if(kind==EDIT){c.drawLine(x-6*u,y+6*u,x+5*u,y-5*u,pen);c.drawLine(x+2*u,y-8*u,x+8*u,y-2*u,pen); // a pencil: its body, the end cap
                c.drawLine(x-6*u,y+6*u,x-8*u,y+8*u,pen);return;}
            float dir=kind==TOP||kind==UP?-1:1,shift=kind==TOP||kind==BOTTOM?2*u*-dir:0; // arrows point up (-1) or down (1); with a bar they move off it
            float tip=y+dir*6*u+shift,tail=y-dir*7*u+shift;c.drawLine(x,tail,x,tip,pen);
            Path head=new Path();head.moveTo(x-5*u,tip-dir*5*u);head.lineTo(x,tip);head.lineTo(x+5*u,tip-dir*5*u);c.drawPath(head,pen);
            if(kind==TOP||kind==BOTTOM){float bar=y+dir*10*u;c.drawLine(x-7*u,bar,x+7*u,bar,pen);}}
    }
}
