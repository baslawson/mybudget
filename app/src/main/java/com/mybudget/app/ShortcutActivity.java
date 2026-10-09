package com.mybudget.app;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

/**
 * Where the app shortcuts land first (hunt 26 C5). Android starts a static shortcut with NEW_TASK and CLEAR_TASK, which would
 * close a running MainActivity with its half-typed form or Undo bar. This activity has a task of its own (taskAffinity ""), so
 * only that is cleared, and it hands the screen to MainActivity the way the widget does (onNewIntent: the form stays).
 */
public final class ShortcutActivity extends Activity {
    @Override protected void onCreate(Bundle state){super.onCreate(state);
        String what=getIntent()==null?null:getIntent().getStringExtra(MainActivity.OPEN);
        Intent open=new Intent(this,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);
        if("add".equals(what)||"budget".equals(what)||"transactions".equals(what))open.putExtra(MainActivity.OPEN,what);
        startActivity(open);finish();}
}
