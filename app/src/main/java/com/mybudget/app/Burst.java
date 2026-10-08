package com.mybudget.app;

import android.animation.ValueAnimator;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;
import java.util.Random;

/**
 * A short burst of coloured confetti over a view, drawn in its window's overlay (nothing is added to the layout, so nothing
 * moves and TalkBack sees nothing). Shown when To budget reaches zero. Only when animations are on (Ui.motion).
 */
final class Burst extends Drawable {
    private static final int PIECES=46;private static final long MILLIS=1300;
    private final float[] x=new float[PIECES],y=new float[PIECES],vx=new float[PIECES],vy=new float[PIECES],spin=new float[PIECES],size=new float[PIECES];
    private final int[] color=new int[PIECES];private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);private float t,gravity;

    private Burst(Rect area,float density,boolean dark){setBounds(area);Random r=new Random();gravity=900*density;
        // Light pastels show on the dark background; the light background needs the deeper flag colours.
        int[] colors=dark?new int[]{Color.rgb(255,209,102),Color.rgb(117,219,177),Color.rgb(179,191,255),Color.rgb(255,142,150),Color.rgb(247,107,21),Color.WHITE}
            :new int[]{Color.rgb(229,72,77),Color.rgb(247,107,21),Color.rgb(226,178,3),Color.rgb(48,164,108),Color.rgb(62,99,221),Color.rgb(142,78,198)};
        for(int i=0;i<PIECES;i++){x[i]=area.exactCenterX();y[i]=area.exactCenterY();double a=r.nextDouble()*Math.PI*2;float speed=(140+r.nextFloat()*320)*density;
            vx[i]=(float)Math.cos(a)*speed;vy[i]=(float)Math.sin(a)*speed-260*density;spin[i]=r.nextFloat()*720-360;size[i]=(3+r.nextFloat()*4)*density;
            color[i]=colors[r.nextInt(colors.length)];}}

    /** Bursts over [over], inside the window's content view; does nothing with animations off or before [over] is laid out. */
    static void over(View over,boolean dark){if(!Ui.motion())return;over.post(()->{View root=over.getRootView();if(!(root instanceof ViewGroup)||over.getWidth()==0)return;
        int[] at=new int[2],base=new int[2];over.getLocationInWindow(at);root.getLocationInWindow(base);
        Rect area=new Rect(at[0]-base[0],at[1]-base[1],at[0]-base[0]+over.getWidth(),at[1]-base[1]+over.getHeight());
        Burst burst=new Burst(area,over.getResources().getDisplayMetrics().density,dark);((ViewGroup)root).getOverlay().add(burst);
        ValueAnimator run=ValueAnimator.ofFloat(0f,MILLIS/1000f);run.setDuration(MILLIS);run.addUpdateListener(a->{burst.t=(float)a.getAnimatedValue();burst.invalidateSelf();});
        run.addListener(new android.animation.AnimatorListenerAdapter(){@Override public void onAnimationEnd(android.animation.Animator a){((ViewGroup)root).getOverlay().remove(burst);}});
        run.start();});}

    @Override public void draw(Canvas canvas){float fade=Math.max(0f,1f-t/(MILLIS/1000f));fade=fade*fade;
        for(int i=0;i<PIECES;i++){float px=x[i]+vx[i]*t,py=y[i]+vy[i]*t+gravity*t*t/2;paint.setColor(color[i]);paint.setAlpha((int)(255*Math.min(1f,fade*1.6f)));
            canvas.save();canvas.rotate(spin[i]*t,px,py);canvas.drawRect(px-size[i],py-size[i]/2,px+size[i],py+size[i]/2,paint);canvas.restore();}}
    @Override public void setAlpha(int alpha){}
    @Override public void setColorFilter(ColorFilter filter){}
    @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
}
