package com.mybudget.app;

import android.app.AlertDialog;
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
    static final int TOP=0,UP=1,DOWN=2,BOTTOM=3,RESET=4,EDIT=5,MONEY=6,SWAP=7,LIST=8,PIN=9,SNOOZE=10,HIDE=11,SHOW=12,DELETE=13,
        COVER=14,ENTER=15,SKIP=16,CAMERA=17,GALLERY=18,MERGE=19,TAG=20,CHECKED=21,GEAR=22,SUN=23,MOON=24,AUTO=25,NONE=-1;
    /** One choice: its icon, words and action; [quiet] ones come last, under a line, in the muted colour; [danger] ones in red. */
    static final class Choice {
        final int icon;final String text;final boolean quiet,danger;final Runnable action;
        String sub,badge;CharSequence detail;int detailColour;boolean selected; // pickers: a second line, a letter instead of the icon, a figure at the end, the current one
        Choice(int icon,String text,boolean quiet,Runnable action){this(icon,text,quiet,false,action);}
        Choice(int icon,String text,boolean quiet,boolean danger,Runnable action){this.icon=icon;this.text=text;this.quiet=quiet;this.danger=danger;this.action=action;}
        /** A choice that removes or throws something away, in red. */
        static Choice danger(int icon,String text,Runnable action){return new Choice(icon,text,false,true,action);}
        /** One of a list to pick from (a category, a payee, a currency), shown with its first letter in the circle. */
        static Choice pick(String text,Runnable action){Choice c=new Choice(NONE,text,false,action);String t=text.trim();
            c.badge=t.isEmpty()?"·":new String(Character.toChars(t.codePointAt(0))).toUpperCase(Locale.ROOT);return c;}
        Choice sub(String s){sub=s;return this;}
        Choice detail(CharSequence d,int colour){detail=d;detailColour=colour;return this;}
        Choice selected(boolean on){selected=on;return this;}
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
        if(main.popup!=null)main.popup.dismiss();main.popup=p;p.setOnDismissListener(()->{if(main.popup==p)main.popup=null;});
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
    /**
     * The menu card in the middle of the screen (a tap on a category, an upcoming transaction or a payee, a list to pick from,
     * a card held still): [title] and [subtitle], then each list in [sections] with a line between them, and [tip] (or none) at the end. It
     * closes with the forms when data is read in meanwhile (main.editors), and leaves that list before a choice acts, so a
     * choice that opens a form (a photo) sees the form under it as the newest one.
     */
    static AlertDialog sheet(Ui ui,String title,String subtitle,List<List<Choice>> sections,String tip){MainActivity main=ui.main;
        LinearLayout sheet=new LinearLayout(main);sheet.setOrientation(LinearLayout.VERTICAL);int side=ui.dp(20);
        TextView name=ui.label(title,20,main.ink,true);Ui.heading(name);sheet.addView(name);
        if(subtitle!=null&&!subtitle.isEmpty())sheet.addView(ui.label(subtitle,13,main.muted,false));
        View space=new View(main);sheet.addView(space,new LinearLayout.LayoutParams(1,ui.dp(8)));
        AlertDialog d=new AlertDialog.Builder(main).create();Runnable close=()->{main.editors.remove(d);d.dismiss();};boolean first=true;
        for(List<Choice> part:sections){if(part.isEmpty())continue;
            if(!first){View line=new View(main);line.setBackgroundColor(Ui.tint(main.ink,main.darkTheme?26:18));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,ui.dp(1));
                lp.setMargins(ui.dp(12),ui.dp(6),ui.dp(12),ui.dp(6));sheet.addView(line,lp);}
            int before=sheet.getChildCount();rows(ui,sheet,part,ui.dp(52),16,ui.dp(40),close);first=false;
            if(sheet.getChildCount()==before)first=true;}
        if(tip!=null){TextView t=ui.label(tip,12,main.muted,false);t.setPadding(ui.dp(12),ui.dp(10),0,0);sheet.addView(t);}
        sheet.setPadding(side,ui.dp(20),side,ui.dp(14));
        // A rounded card in the middle of the screen, at most 85% of its height; longer lists scroll inside it.
        int tallest=(int)(main.getResources().getDisplayMetrics().heightPixels*0.85f);
        ScrollView holder=new ScrollView(main){@Override protected void onMeasure(int w,int h){
            super.onMeasure(w,MeasureSpec.makeMeasureSpec(Math.min(tallest,MeasureSpec.getSize(h)>0?MeasureSpec.getSize(h):tallest),MeasureSpec.AT_MOST));}};
        GradientDrawable bg=new GradientDrawable();bg.setColor(main.darkTheme?mix(main.surface,main.ink,0.05f):main.surface);bg.setCornerRadius(ui.dp(28));
        bg.setStroke(ui.dp(1),Ui.tint(main.ink,main.darkTheme?46:20));holder.setBackground(bg);holder.setClipToOutline(true);holder.setElevation(ui.dp(12));
        holder.addView(sheet);d.setView(holder,0,0,0,0);
        d.setCanceledOnTouchOutside(true);main.editors.add(d);d.setOnDismissListener(x->main.editors.remove(d));d.show();
        Window w=d.getWindow();if(w!=null){w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));w.setGravity(Gravity.CENTER);
            int wide=Math.min(main.getResources().getDisplayMetrics().widthPixels-ui.dp(40),ui.dp(480));w.setLayout(wide,ViewGroup.LayoutParams.WRAP_CONTENT);
            w.setWindowAnimations(0);}
        if(Ui.motion()){holder.setAlpha(0f);holder.setScaleX(0.94f);holder.setScaleY(0.94f);
            holder.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(180).setInterpolator(new android.view.animation.DecelerateInterpolator(2f)).start();}
        return d;}
    /** [a] moved [part] of the way to [b] (opaque). */
    private static int mix(int a,int b,float part){return Color.rgb(Math.round(Color.red(a)+(Color.red(b)-Color.red(a))*part),
        Math.round(Color.green(a)+(Color.green(b)-Color.green(a))*part),Math.round(Color.blue(a)+(Color.blue(b)-Color.blue(a))*part));}
    /** Adds [choices] to [into]: [height] tall rows, [icon] round icons; a line before the first quiet one. Each runs [close] first. */
    static void rows(Ui ui,LinearLayout into,List<Choice> choices,int height,int size,int icon,Runnable close){MainActivity main=ui.main;boolean lined=false,loud=false; // a line before the first quiet one, only after normal ones here
        for(Choice c:choices){
            if(c.quiet&&!lined&&loud){View line=new View(main);line.setBackgroundColor(Ui.tint(main.ink,main.darkTheme?26:18));
                LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,ui.dp(1));lp.setMargins(ui.dp(12),ui.dp(6),ui.dp(12),ui.dp(6));into.addView(line,lp);lined=true;}
            int colour=c.danger?main.red:c.quiet?main.muted:main.ink;if(!c.quiet)loud=true;
            LinearLayout row=new LinearLayout(main);row.setGravity(Gravity.CENTER_VERTICAL);row.setMinimumHeight(height);row.setPadding(ui.dp(10),0,ui.dp(14),0);
            if(c.badge!=null){TextView b=ui.label(c.badge,15,c.selected?(main.darkTheme?main.canvas:Color.WHITE):main.blue,true);b.setGravity(Gravity.CENTER);
                GradientDrawable round=new GradientDrawable();round.setShape(GradientDrawable.OVAL);round.setColor(c.selected?main.blue:Ui.tint(main.blue,main.darkTheme?52:30));
                b.setBackground(round);b.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);row.addView(b,new LinearLayout.LayoutParams(icon,icon));}
            else if(c.icon!=NONE)row.addView(new Icon(ui,c.icon,c.danger?main.red:c.quiet?main.muted:main.blue),new LinearLayout.LayoutParams(icon,icon));
            LinearLayout words=new LinearLayout(main);words.setOrientation(LinearLayout.VERTICAL);words.setPadding(ui.dp(c.icon==NONE&&c.badge==null?4:14),ui.dp(6),0,ui.dp(6));
            TextView t=ui.label(c.text,size,colour,c.selected);t.setMaxLines(2);t.setEllipsize(android.text.TextUtils.TruncateAt.END);words.addView(t);
            if(c.sub!=null&&!c.sub.isEmpty()){TextView s=ui.label(c.sub,13,main.muted,false);s.setMaxLines(3);s.setEllipsize(android.text.TextUtils.TruncateAt.END);words.addView(s);}
            row.addView(words,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
            if(c.detail!=null){TextView d=ui.label("",15,c.detailColour,true);d.setText(c.detail);d.setPadding(ui.dp(10),0,0,0);d.setFontFeatureSettings("tnum");row.addView(d);}
            if(c.selected){Icon tick=new Icon(ui,CHECKED,main.blue);LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(ui.dp(28),ui.dp(28));tp.setMarginStart(ui.dp(10));row.addView(tick,tp);
                row.setContentDescription(c.text+(c.sub==null?"":", "+c.sub)+(c.detail==null?"":", "+c.detail)+", selected");}
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
            if(kind!=TOP&&kind!=UP&&kind!=DOWN&&kind!=BOTTOM){other(c,x,y,u);return;}
            float dir=kind==TOP||kind==UP?-1:1,shift=kind==TOP||kind==BOTTOM?2*u*-dir:0; // arrows point up (-1) or down (1); with a bar they move off it
            float tip=y+dir*6*u+shift,tail=y-dir*7*u+shift;c.drawLine(x,tail,x,tip,pen);
            Path head=new Path();head.moveTo(x-5*u,tip-dir*5*u);head.lineTo(x,tip);head.lineTo(x+5*u,tip-dir*5*u);c.drawPath(head,pen);
            if(kind==TOP||kind==BOTTOM){float bar=y+dir*10*u;c.drawLine(x-7*u,bar,x+7*u,bar,pen);}}
        /** The icons of the action sheets, each about 16dp across in the middle of the circle. */
        private void other(Canvas c,float x,float y,float u){Paint solid=new Paint(Paint.ANTI_ALIAS_FLAG);solid.setColor(pen.getColor());Path p=new Path();
            switch(kind){
                case MONEY:solid.setTextSize(17*u);solid.setFakeBoldText(true);solid.setTextAlign(Paint.Align.CENTER);c.drawText("$",x,y-(solid.descent()+solid.ascent())/2,solid);break;
                case SWAP:c.drawLine(x-7*u,y-3*u,x+7*u,y-3*u,pen);p.moveTo(x+4*u,y-6*u);p.lineTo(x+7*u,y-3*u);p.lineTo(x+4*u,y);
                    c.drawLine(x+7*u,y+4*u,x-7*u,y+4*u,pen);p.moveTo(x-4*u,y+u);p.lineTo(x-7*u,y+4*u);p.lineTo(x-4*u,y+7*u);c.drawPath(p,pen);break;
                case LIST:for(int i=-1;i<=1;i++){c.drawCircle(x-6*u,y+i*5*u,1.3f*u,solid);c.drawLine(x-2*u,y+i*5*u,x+7*u,y+i*5*u,pen);}break;
                case PIN:c.drawCircle(x,y-3*u,4*u,pen);c.drawLine(x,y+u,x,y+9*u,pen);break;
                case SNOOZE:solid.setTextSize(11*u);solid.setFakeBoldText(true);c.drawText("z",x-6*u,y+5*u,solid);solid.setTextSize(15*u);c.drawText("Z",x-u,y+u,solid);break;
                case HIDE:case SHOW:c.drawOval(new RectF(x-8*u,y-5*u,x+8*u,y+5*u),pen);c.drawCircle(x,y,2.5f*u,solid);
                    if(kind==HIDE)c.drawLine(x-8*u,y+7*u,x+8*u,y-7*u,pen);break;
                case DELETE:c.drawLine(x-7*u,y-5*u,x+7*u,y-5*u,pen);c.drawLine(x-2*u,y-7*u,x+2*u,y-7*u,pen);
                    p.moveTo(x-5*u,y-5*u);p.lineTo(x-4*u,y+8*u);p.lineTo(x+4*u,y+8*u);p.lineTo(x+5*u,y-5*u);c.drawPath(p,pen);c.drawLine(x,y-2*u,x,y+5*u,pen);break;
                case COVER:c.drawLine(x-6*u,y,x+6*u,y,pen);c.drawLine(x,y-6*u,x,y+6*u,pen);break;
                case ENTER:p.moveTo(x-6*u,y);p.lineTo(x-2*u,y+5*u);p.lineTo(x+7*u,y-5*u);c.drawPath(p,pen);break;
                case SKIP:p.moveTo(x-6*u,y-6*u);p.lineTo(x+3*u,y);p.lineTo(x-6*u,y+6*u);p.close();c.drawPath(p,pen);c.drawLine(x+6*u,y-6*u,x+6*u,y+6*u,pen);break;
                case CAMERA:c.drawRoundRect(new RectF(x-8*u,y-4*u,x+8*u,y+7*u),2*u,2*u,pen);c.drawCircle(x,y+1.5f*u,3.5f*u,pen);
                    p.moveTo(x-4*u,y-4*u);p.lineTo(x-2.5f*u,y-7*u);p.lineTo(x+2.5f*u,y-7*u);p.lineTo(x+4*u,y-4*u);c.drawPath(p,pen);break;
                case GALLERY:c.drawRoundRect(new RectF(x-8*u,y-7*u,x+8*u,y+7*u),2*u,2*u,pen);c.drawCircle(x+3*u,y-3*u,1.6f*u,solid);
                    p.moveTo(x-8*u,y+5*u);p.lineTo(x-3*u,y-u);p.lineTo(x+u,y+3*u);p.lineTo(x+4*u,y);p.lineTo(x+8*u,y+4*u);c.drawPath(p,pen);break;
                case MERGE:p.moveTo(x-6*u,y-7*u);p.lineTo(x,y);p.lineTo(x+6*u,y-7*u);p.moveTo(x,y);p.lineTo(x,y+8*u);
                    p.moveTo(x-3*u,y+5*u);p.lineTo(x,y+8*u);p.lineTo(x+3*u,y+5*u);c.drawPath(p,pen);break;
                case TAG:p.moveTo(x-7*u,y-7*u);p.lineTo(x+u,y-7*u);p.lineTo(x+8*u,y);p.lineTo(x+u,y+7*u);p.lineTo(x-7*u,y+7*u);p.close();c.drawPath(p,pen);
                    c.drawCircle(x-3*u,y-3*u,1.5f*u,solid);break;
                case CHECKED:c.drawCircle(x,y,Math.min(x,y),solid);Paint white=new Paint(pen);white.setColor(ui.main.darkTheme?ui.main.canvas:Color.WHITE);white.setStrokeWidth(2.6f*u); // dark: the pale blue needs a dark tick
                    p.moveTo(x-5*u,y);p.lineTo(x-1.5f*u,y+4*u);p.lineTo(x+5.5f*u,y-4*u);c.drawPath(p,white);break;
                case GEAR:{float[] knob={3,-4,1};for(int i=-1;i<=1;i++){c.drawLine(x-8*u,y+i*5.5f*u,x+8*u,y+i*5.5f*u,pen);c.drawCircle(x+knob[i+1]*u,y+i*5.5f*u,2.4f*u,solid);}}break; // settings: three sliders
                case SUN:c.drawCircle(x,y,3.5f*u,pen);for(int i=0;i<8;i++){double a=i*Math.PI/4;float cs=(float)Math.cos(a),sn=(float)Math.sin(a);
                    c.drawLine(x+cs*6.5f*u,y+sn*6.5f*u,x+cs*8.5f*u,y+sn*8.5f*u,pen);}break;
                case MOON:{Path disc=new Path();disc.addCircle(x,y,7*u,Path.Direction.CW);Path bite=new Path();bite.addCircle(x+4.5f*u,y-3.5f*u,6*u,Path.Direction.CW);
                    disc.op(bite,Path.Op.DIFFERENCE);c.drawPath(disc,solid);}break;
                case AUTO:c.drawCircle(x,y,7*u,pen);p.addArc(new RectF(x-7*u,y-7*u,x+7*u,y+7*u),90,180);p.close();c.drawPath(p,solid);break; // half light, half dark
                default:break;}}
    }
}
