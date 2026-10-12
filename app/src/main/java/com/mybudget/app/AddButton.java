package com.mybudget.app;

import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Bundle;
import android.view.*;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.animation.DecelerateInterpolator;
import android.widget.Button;
import android.widget.FrameLayout;
import java.util.ArrayList;
import java.util.List;

/**
 * Add transaction: a round + over every main screen (not Settings), always in reach. A tap opens the form; a drag moves it
 * anywhere over the screen and, let go, it slides to the nearer side; a hold without moving opens its menu (left side, right
 * side, back in the corner). Its place is kept on this device (prefs "appearance": add_side, add_height), not in backups:
 * the side and how far up the screen it is (0 the bottom, 1 the top), so it keeps its place across screens, sizes and rotation.
 */
final class AddButton {
    private static final int SIZE=64,EDGE=4,BOTTOM=12; // dp: the button, its gap from the side and from the bottom
    private AddButton(){}

    /** Puts the button over [main.body], above the screen. */
    static void add(MainActivity main){Ui ui=main.ui;FrameLayout body=main.body;
        Button add=ui.button("+",()->main.forms.transaction(null));add.setTextSize(30);add.setTextColor(Color.WHITE);add.setPadding(0,0,0,ui.dp(3));
        GradientDrawable round=new GradientDrawable();round.setShape(GradientDrawable.OVAL);round.setColor(main.primary);add.setBackground(round);
        GradientDrawable mask=new GradientDrawable();mask.setShape(GradientDrawable.OVAL);mask.setColor(Color.WHITE);
        add.setForeground(new RippleDrawable(ColorStateList.valueOf(Ui.tint(Color.WHITE,70)),null,mask));
        add.setElevation(ui.dp(6));add.setContentDescription("Add transaction");
        // Hunt 23 M5: TalkBack reaches it before the screen (it is drawn over it, so it would come last).
        add.setId(View.generateViewId());body.getChildAt(0).setAccessibilityTraversalAfter(add.getId());
        FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(ui.dp(SIZE),ui.dp(SIZE),Gravity.BOTTOM|Gravity.RIGHT);p.setMargins(ui.dp(EDGE),0,ui.dp(EDGE),ui.dp(BOTTOM));
        body.addView(add,p);main.content.setPadding(0,0,0,ui.dp(SIZE+24)); // the last card can scroll clear of it
        // Its saved place, whenever the screen is laid out (first draw, rotation); not while it is being dragged.
        boolean[] dragging={false};
        body.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->{if(!dragging[0])place(main,add,false);});
        add.setOnTouchListener(new Drag(main,add,dragging));
        add.setOnLongClickListener(v->{menu(main,add);return true;});
        add.setAccessibilityDelegate(new View.AccessibilityDelegate(){
            @Override public void onInitializeAccessibilityNodeInfo(View v,AccessibilityNodeInfo info){super.onInitializeAccessibilityNodeInfo(v,info);
                for(Move m:moves(main))info.addAction(new AccessibilityNodeInfo.AccessibilityAction(m.id,m.text));}
            @Override public boolean performAccessibilityAction(View v,int action,Bundle args){
                for(Move m:moves(main))if(m.id==action){m.run(main,add);return true;}return super.performAccessibilityAction(v,action,args);}});
        // No pop-in: it is there from the first frame (0.0.17's grew from nothing and was cancelled by place(), so it stayed hidden).
    }

    private static SharedPreferences prefs(MainActivity main){return main.getSharedPreferences("appearance",0);}
    private static boolean left(MainActivity main){return prefs(main).getString("add_side","right").equals("left");}
    private static float height(MainActivity main){return Math.max(0f,Math.min(1f,prefs(main).getFloat("add_height",0f)));}
    private static boolean moved(MainActivity main){return left(main)||height(main)>0f;}
    /** How far it can go left and up from the bottom right corner (px). */
    private static float roomX(MainActivity main,View add){return Math.max(0,main.body.getWidth()-add.getWidth()-2*main.ui.dp(EDGE));}
    private static float roomY(MainActivity main,View add){return Math.max(0,main.body.getHeight()-add.getHeight()-main.ui.dp(BOTTOM)-main.ui.dp(EDGE));}
    /** Moves [add] to its saved place, sliding there when [slide] (and the phone's animations are on). */
    private static void place(MainActivity main,View add,boolean slide){float x=left(main)?-roomX(main,add):0,y=-height(main)*roomY(main,add);
        add.animate().cancel();add.setScaleX(1f);add.setScaleY(1f); // a stopped animation (the drag's lift) never leaves it smaller
        if(slide&&Ui.motion())add.animate().translationX(x).translationY(y).setStartDelay(0).setDuration(200).setInterpolator(new DecelerateInterpolator()).start();
        else{add.setTranslationX(x);add.setTranslationY(y);}}
    private static void save(MainActivity main,boolean left,float height){SharedPreferences.Editor e=prefs(main).edit();
        if(left||height>0.005f){e.putString("add_side",left?"left":"right");e.putFloat("add_height",height);}
        else{e.remove("add_side");e.remove("add_height");} // the corner is the default: nothing kept
        e.apply();}

    /** A drag: moves past the touch slop pick it up; a tap and a hold go on to the button's own click and long click. */
    private static final class Drag implements View.OnTouchListener {
        final MainActivity main;final View add;final boolean[] dragging;final int slop;float downX,downY,startX,startY;
        Drag(MainActivity main,View add,boolean[] dragging){this.main=main;this.add=add;this.dragging=dragging;slop=ViewConfiguration.get(main).getScaledTouchSlop();}
        @Override public boolean onTouch(View v,MotionEvent e){
            switch(e.getActionMasked()){
                case MotionEvent.ACTION_DOWN:downX=e.getRawX();downY=e.getRawY();startX=v.getTranslationX();startY=v.getTranslationY();dragging[0]=false;return false;
                case MotionEvent.ACTION_MOVE:{float dx=e.getRawX()-downX,dy=e.getRawY()-downY;
                    if(!dragging[0]){if(Math.hypot(dx,dy)<slop)return false;
                        dragging[0]=true;v.animate().cancel();MotionEvent cancel=MotionEvent.obtain(e);cancel.setAction(MotionEvent.ACTION_CANCEL);
                        v.onTouchEvent(cancel);cancel.recycle(); // the button lets go of the press: no tap, no hold
                        v.getParent().requestDisallowInterceptTouchEvent(true);Ui.tick(v);
                        v.animate().scaleX(1.08f).scaleY(1.08f).setDuration(Ui.motion()?120:0).start();}
                    v.setTranslationX(Math.max(-roomX(main,v),Math.min(0,startX+dx)));v.setTranslationY(Math.max(-roomY(main,v),Math.min(0,startY+dy)));return true;}
                case MotionEvent.ACTION_UP:case MotionEvent.ACTION_CANCEL:{if(!dragging[0])return false;dragging[0]=false;
                    float room=roomX(main,v),up=roomY(main,v);boolean left=room>0&&-v.getTranslationX()>room/2;
                    save(main,left,up>0?-v.getTranslationY()/up:0f);place(main,v,true);return true;}}
            return false;}
    }

    /** One way to move it: its TalkBack action id, words, icon and where it goes. */
    private static final class Move {
        final int id,icon;final String text;final Boolean left;final float height; // left null: keep the side
        Move(int id,int icon,String text,Boolean left,float height){this.id=id;this.icon=icon;this.text=text;this.left=left;this.height=height;}
        void run(MainActivity main,View add){save(main,left!=null?left:left(main),height<0?height(main):height);place(main,add,true);}
    }
    /** The moves that change something from where it is now. */
    private static List<Move> moves(MainActivity main){List<Move> m=new ArrayList<>();boolean l=left(main);
        if(l)m.add(new Move(R.id.add_right,MoveMenu.RIGHT,"Move to the right side",false,-1));
        else m.add(new Move(R.id.add_left,MoveMenu.LEFT,"Move to the left side",true,-1));
        if(moved(main))m.add(new Move(R.id.add_corner,MoveMenu.RESET,"Put back in the corner",false,0f));
        return m;}
    private static void menu(MainActivity main,View add){List<MoveMenu.Choice> c=new ArrayList<>(); // the hold's own buzz says it opened
        for(Move m:moves(main))c.add(new MoveMenu.Choice(m.icon,m.text,m.id==R.id.add_corner,()->m.run(main,add)));
        MoveMenu.show(main.ui,add,"Add transaction","drag it anywhere",c);}
}
