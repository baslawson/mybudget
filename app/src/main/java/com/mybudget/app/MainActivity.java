package com.mybudget.app;

import android.app.*;
import android.os.Bundle;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.content.Intent;
import android.net.Uri;
import android.graphics.BitmapFactory;
import java.io.*;
import java.nio.charset.StandardCharsets;
import android.text.*;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import java.text.NumberFormat;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class MainActivity extends Activity {
    // Each screen's views and forms live in its own class; they work on this activity's state below.
    final Ui ui=new Ui(this);
    final HomeScreen homeScreen=new HomeScreen(this);
    final BudgetScreen budgetScreen=new BudgetScreen(this);
    final TransactionsScreen transactionsScreen=new TransactionsScreen(this);
    final AccountsScreen accountsScreen=new AccountsScreen(this);
    final ReportsScreen reportsScreen=new ReportsScreen(this);
    final SettingsScreen settingsScreen=new SettingsScreen(this);
    final TransactionForms forms=new TransactionForms(this);
    Budget budget=new Budget();
    int ink,blue,muted,red,amber,green,canvas,surface,buttonSurface,primary;
    String themeMode;
    LinearLayout root,content;FrameLayout body;
    String tab="Home",search="",accountFilter="",categoryFilter="",fromFilter="",toFilter=""; // Transactions filters (see Budget.Filter)
    int flagFilter=-1,clearedFilter=-1;
    private String previousTab="Home";
    YearMonth month=YearMonth.now(),seenNow=YearMonth.now(); // seenNow: the real month when last shown (hunt 26 C4)
    boolean storageReadable=true,showHidden=false;
    // Money in the budget's currency (Settings), in the device's number style; made again when the currency changes.
    private NumberFormat moneyFormat;private String moneyCode;
    NumberFormat money(){if(moneyFormat==null||!budget.currency.equals(moneyCode)){moneyCode=budget.currency;moneyFormat=Budget.moneyFormat(moneyCode,Locale.getDefault());}return moneyFormat;}
    @Override public void onCreate(Bundle state){
        themeMode=getSharedPreferences("appearance",0).getString("theme","Dark");
        hideAmounts=getSharedPreferences("appearance",0).getBoolean("hideAmounts",false);
        boolean dark=themeMode.equals("Dark")||(themeMode.equals("Auto")&&(getResources().getConfiguration().uiMode&Configuration.UI_MODE_NIGHT_MASK)==Configuration.UI_MODE_NIGHT_YES);
        setTheme(dark?R.style.AppTheme:R.style.AppTheme_Light);darkTheme=dark;
        super.onCreate(state);
        if(dark){ink=Color.rgb(237,241,250);blue=Color.rgb(179,191,255);muted=Color.rgb(171,181,201);red=Color.rgb(255,142,150);
            amber=Color.rgb(245,198,110);green=Color.rgb(117,219,177);canvas=Color.rgb(17,21,31);surface=Color.rgb(32,38,53);
            buttonSurface=Color.rgb(43,52,78);primary=Color.rgb(65,80,159);}
        else{ink=Color.rgb(27,39,62);blue=Color.rgb(57,77,165);muted=Color.rgb(111,121,140);red=Color.rgb(178,51,55);amber=Color.rgb(159,104,12);
            green=Color.rgb(32,115,85);canvas=Color.rgb(243,245,250);surface=Color.WHITE;buttonSurface=Color.rgb(231,235,249);primary=blue;}
        getWindow().getDecorView().setSystemUiVisibility(dark?0:View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        if(state!=null){tab=state.getString("tab","Home");previousTab=state.getString("previousTab","Home");search=state.getString("search","");
            accountFilter=state.getString("accountFilter","");categoryFilter=state.getString("categoryFilter","");
            fromFilter=state.getString("fromFilter","");toFilter=state.getString("toFilter","");flagFilter=state.getInt("flagFilter",-1);
            clearedFilter=state.getInt("clearedFilter",-1);month=YearMonth.parse(state.getString("month",YearMonth.now().toString()));
            period=Math.max(0,Math.min(ReportsScreen.PERIODS.length-1,state.getInt("period",0)));byGroup=state.getBoolean("byGroup",false);
            trendKey=state.getString("trendKey","");trendMonths=state.getInt("trendMonths",6)==12?12:6;reportYear=state.getInt("reportYear",0);
            String shot=state.getString("cameraFile",null);if(shot!=null)cameraFile=new File(PhotoProvider.dir(this),shot);} // so it's still deleted when the camera returns
        else{File[] left=PhotoProvider.dir(this).listFiles();if(left!=null)for(File f:left)f.delete();} // a fresh start: no capture is in progress
        load();if(storageReadable){render();cleanupPhotos();if(state==null)openFrom(getIntent());} // a recreated screen has opened it already
    }
    // The saved data as this screen last read or wrote it. AddExpenseActivity may save an expense from another app
    // meanwhile: when the saved data differs, it's read in (open forms close, as they show the old budget), so the next save keeps it.
    String loaded;
    private boolean reloadIfChanged(){String raw=prefs().getString("data",null);if(raw==null||raw.equals(loaded)||!storageReadable)return false;
        Budget fresh;try{fresh=BudgetStore.decode(raw);}catch(Exception e){throw new IllegalStateException("Could not reload your budget.");}
        fresh.fromPlanner.addAll(budget.fromPlanner);budget=fresh;loaded=raw;return true;}
    boolean pickingPhoto;java.util.function.Consumer<Uri> photoTarget;AlertDialog photoForm;
    /**
     * Back from the photo picker: data saved meanwhile is read in, but the form the photo is for stays open (with what was
     * typed and the photo). Its save looks records up by id, so it saves on the latest data; any other open form closes.
     */
    private void photoReturned(){AlertDialog form=photoForm;photoForm=null;
        try{if(reloadIfChanged()){for(AlertDialog editor:new ArrayList<>(editors))if(editor!=form)editor.dismiss();
                render();}}catch(IllegalStateException e){ui.toast(e.getMessage());}}
    // Automatic backup: today's, if it hasn't run yet (the daily job may not have had a chance).
    // Not reloaded on return from the photo picker: photoReturned did that, keeping the form the photo is for open.
    @Override protected void onResume(){super.onResume();if(!storageReadable)return;
        if(pickingPhoto)pickingPhoto=false;else try{if(reloadIfChanged()){for(AlertDialog editor:new ArrayList<>(editors))editor.dismiss();
                render();}}catch(IllegalStateException e){ui.toast(e.getMessage());}
        // Hunt 26 C4: left open across midnight on the 1st, the month that was current moves on (one picked on purpose stays).
        YearMonth now=YearMonth.now();if(!now.equals(seenNow)){if(month.equals(seenNow)){month=now;if(editors.isEmpty())render();}seenNow=now;}
        AutoBackup.schedule(this);BudgetWidget.refresh(this); // the widget may show last month after midnight on the 1st
        // Reminders, as Planner on each open: a ringing alarm whose notification is gone is stopped; then missed reminders are
        // shown and every alarm is set again (at most once a minute: coming back from a picker or Settings is no new open).
        AlarmService.stopIfUnseen(this);long at=android.os.SystemClock.elapsedRealtime();if(remindersChecked==0||at-remindersChecked>60_000L){remindersChecked=at;BootReceiver.onAppOpen(this);}
        if(prefs().getString("auto_backup_tree",null)!=null){boolean asked=backupDue(); // Home's backup reminder goes once this succeeds
            new Thread(()->{AutoBackup.run(getApplicationContext(),false);runOnUiThread(()->{if(asked&&!backupDue()&&tab.equals("Home")&&!isFinishing())render();});}).start();}}
    // Hunt 23: side by side with Planner (split screen) this screen stays resumed while an expense from Planner is saved, so
    // onResume doesn't read it in. Coming back to this window does, unless a form is open (its save reads it in: commit).
    @Override public void onTopResumedActivityChanged(boolean top){super.onTopResumedActivityChanged(top);
        if(!top||!storageReadable||pickingPhoto||!editors.isEmpty())return;
        try{if(reloadIfChanged())render();}catch(IllegalStateException e){ui.toast(e.getMessage());}}
    // Hunt 25 C1: rotated or resized (the manifest's configChanges): the screen is drawn again for the new size; open forms stay.
    // A theme, font size or language change still starts the screen again (its colours and text come from those).
    @Override public void onConfigurationChanged(Configuration c){super.onConfigurationChanged(c);if(storageReadable)render();}
    @Override protected void onSaveInstanceState(Bundle state){state.putString("tab",tab);state.putString("previousTab",previousTab);
        state.putString("search",search);state.putString("accountFilter",accountFilter);state.putString("categoryFilter",categoryFilter);
        state.putString("fromFilter",fromFilter);state.putString("toFilter",toFilter);state.putInt("flagFilter",flagFilter);
        state.putInt("clearedFilter",clearedFilter);state.putString("month",month.toString());state.putInt("period",period);
        state.putBoolean("byGroup",byGroup);state.putString("trendKey",trendKey);state.putInt("trendMonths",trendMonths);state.putInt("reportYear",reportYear);
        if(cameraFile!=null)state.putString("cameraFile",cameraFile.getName());
        super.onSaveInstanceState(state);}
    private void options(View anchor){
        List<MoveMenu.Choice> c=new ArrayList<>();
        c.add(new MoveMenu.Choice(MoveMenu.GEAR,"Settings",false,this::openSettings));
        c.add(new MoveMenu.Choice(hideAmounts?MoveMenu.SHOW:MoveMenu.HIDE,hideAmounts?"Show amounts":"Hide amounts",false,()->{
            if(!getSharedPreferences("appearance",0).edit().putBoolean("hideAmounts",!hideAmounts).commit()){ui.toast("Could not save that setting.");return;}
            hideAmounts=!hideAmounts;render();BudgetWidget.refresh(this);}));
        c.add(new MoveMenu.Choice(MoveMenu.RESET,"Budget reset",true,budgetScreen::planReset));
        MoveMenu.show(ui,anchor,"MyBudget",month.format(java.time.format.DateTimeFormatter.ofPattern("MMMM yyyy")),c);
    }
    void openSettings(){if(!tab.equals("Settings"))previousTab=tab;tab="Settings";render();}
    // The widget and the app shortcuts open a screen with the OPEN extra: the Add transaction form, Budget, Transactions or
    // Home, this month. Without an account yet it's Home, where the start card is. Any app can send it (this is the launcher's
    // activity), but it only opens a screen or an empty form: nothing is saved without the user's tap.
    static final String OPEN="com.mybudget.app.open",OPEN_ADD="add",OPEN_HOME="home";
    @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);openFrom(intent);}
    void openFrom(Intent intent){
        // A tap on a ringing reminder stops it (its own token: AlarmService), and opens Transactions.
        String stop=intent==null?null:intent.getStringExtra(AlarmService.EXTRA_STOP_ALARM);if(stop!=null){intent.removeExtra(AlarmService.EXTRA_STOP_ALARM);AlarmService.stopFromTap(this,stop);}
        String what=intent==null?null:intent.getStringExtra(OPEN);if(what==null||!storageReadable)return;intent.removeExtra(OPEN);
        try{if(reloadIfChanged())for(AlertDialog editor:new ArrayList<>(editors))editor.dismiss();}catch(IllegalStateException e){ui.toast(e.getMessage());} // before onResume would, so the form stays
        boolean ready=!budget.accounts.isEmpty();month=YearMonth.now();
        tab=!ready?"Home":what.equals("budget")?"Plan":what.equals("transactions")?"Spending":"Home";if(tab.equals("Spending"))clearFilters();
        render();if(ready&&what.equals(OPEN_ADD))forms.transaction(null);}
    private void closeSettings(){tab=previousTab;render();}
    @Override public void onBackPressed(){if(tab.equals("Settings"))closeSettings();else super.onBackPressed();}
    // Backup, restore and export go through Android's file picker, so MyBudget needs no storage permission.
    static final int BACKUP=1,RESTORE=2,EXPORT=3,IMPORT=4,AUTO=5,PHOTO=6,CAMERA=7,NOTIFY=8;
    private static long remindersChecked; // when this process last looked for missed reminders (elapsed time)
    private Runnable notificationsAsked;
    /**
     * Reminders' "Turn on": Android's question (Android 13 and later), or, once it was answered no for good, the app's
     * notification settings. [after] runs when the question is answered.
     */
    void askNotifications(Runnable after){
        if(android.os.Build.VERSION.SDK_INT>=33&&checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED
            &&(!prefs().getBoolean("notifications_asked",false)||shouldShowRequestPermissionRationale(android.Manifest.permission.POST_NOTIFICATIONS))){
            prefs().edit().putBoolean("notifications_asked",true).apply();notificationsAsked=after;requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS},NOTIFY);return;}
        try{ReminderNotifications.openNotificationSettings(this);}catch(Exception e){ui.toast("Open Android's settings to turn on MyBudget's notifications.");}}
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] results){super.onRequestPermissionsResult(request,permissions,results);
        if(request==NOTIFY&&notificationsAsked!=null){Runnable after=notificationsAsked;notificationsAsked=null;after.run();}}
    android.content.SharedPreferences prefs(){return getSharedPreferences("budget",0);}
    void pick(Intent intent,int request){intent.addCategory(Intent.CATEGORY_OPENABLE);
        try{startActivityForResult(intent,request);}catch(android.content.ActivityNotFoundException e){ui.toast("No app on this device can save or open files.");}}
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);Uri uri=data==null?null:data.getData();
        if(request==PHOTO){java.util.function.Consumer<Uri> target=photoTarget;photoTarget=null;
            if(result==RESULT_OK&&uri!=null&&target!=null)target.accept(uri);photoReturned();return;}
        if(request==CAMERA){java.util.function.Consumer<Uri> target=photoTarget;photoTarget=null;File shot=cameraFile;cameraFile=null;
            if(result==RESULT_OK&&shot!=null&&shot.length()>0&&target!=null)target.accept(Uri.fromFile(shot)); // copied into photos like a picked one
            if(shot!=null)shot.delete();photoReturned();return;} // the capture itself never stays, taken or cancelled
        if(result!=RESULT_OK||uri==null||!storageReadable)return;
        if(request==AUTO){try{getContentResolver().takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION);}catch(Exception e){ui.toast("MyBudget couldn't keep access to that folder. Choose another.");
                return;}prefs().edit().putString("auto_backup_tree",uri.toString()).remove("auto_backup_last").remove("auto_backup_error").apply();
            AutoBackup.schedule(this);String tree=uri.toString();new Thread(()->{String e=AutoBackup.run(this,true);
                runOnUiThread(()->{ui.toast(e==null?"Automatic backup is on. First backup saved.":e);render();});}).start();return;}
        if(request==IMPORT){List<List<String>> rows;
            try{rows=CsvImport.statement(read(uri));}catch(Exception e){ui.toast((e instanceof IOException||e instanceof IllegalArgumentException)&&e.getMessage()!=null?e.getMessage():"Could not read that file.");
                return;}if(rows.size()<=1&&(rows.isEmpty()||rows.get(0).equals(Arrays.asList("Date","Payee","Amount")))){ui.toast("That file has no transactions MyBudget can read.");return;}settingsScreen.importDialog(rows);return;} // an OFX or QIF file without transactions gives just its header row
        if(request==RESTORE){BudgetStore.Backup backup;try{backup=BudgetStore.readBackup(read(uri));}catch(Exception e){String m=e.getMessage();
                ui.toast((e instanceof org.json.JSONException||e instanceof IOException)&&m!=null?m:"Could not read that file.");return;}
            settingsScreen.confirmRestore(backup);return;}
        try{write(uri,request==BACKUP?BudgetStore.backup(budget,LocalDateTime.now()):"﻿"+budget.csv());if(request==BACKUP){AutoBackup.backedUp(this);render();} // Home's reminder, Settings' Last backup
            ui.toast(request==BACKUP?(photoCount()>0?"Budget backed up. Photos stay on this phone.":"Budget backed up."):"Transactions exported.");}
        catch(Exception e){try{android.provider.DocumentsContract.deleteDocument(getContentResolver(),uri);}catch(Exception ignored){}
            ui.toast(request==BACKUP?"Could not save the backup.":"Could not save the export.");}
    }
    // Photos: JPEGs in files/photos, at most 1600 px on the long side, turned upright from the camera's EXIF.
    File photoDir(){File d=new File(getFilesDir(),"photos");d.mkdirs();return d;}
    /** Choose from gallery: Android's picker. False when no app can pick one. */
    boolean choosePhoto(){try{startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE),PHOTO);return true;}
        catch(Exception e){ui.toast("No app on this device can pick a photo.");return false;}}
    File cameraFile; // a capture in progress (cache/camera): deleted once it's copied into photos, or cancelled
    /**
     * Take a photo: the camera app writes into a new cache file (PhotoProvider grants it that one file), then it's copied into
     * photos like a picked one. No permission is needed while the manifest doesn't declare CAMERA (if it did, the capture would need CAMERA granted first).
     */
    boolean takePhoto(){File dir=PhotoProvider.dir(this);dir.mkdirs();File shot=new File(dir,UUID.randomUUID()+".jpg");Uri out=PhotoProvider.uri(this,shot.getName());
        Intent i=new Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE).putExtra(android.provider.MediaStore.EXTRA_OUTPUT,out)
            .addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION|Intent.FLAG_GRANT_READ_URI_PERMISSION);i.setClipData(android.content.ClipData.newRawUri("",out));
        try{startActivityForResult(i,CAMERA);cameraFile=shot;return true;}catch(Exception e){shot.delete();ui.toast("No camera app on this device can take a photo.");return false;}}
    String copyPhoto(Uri uri)throws IOException{
        BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;
        try(InputStream in=getContentResolver().openInputStream(uri)){BitmapFactory.decodeStream(in,null,bounds);}
        if(bounds.outWidth<=0)throw new IOException("Not an image.");int sample=1;
        while(Math.max(bounds.outWidth,bounds.outHeight)/(sample*2)>=1600)sample*=2;
        BitmapFactory.Options o=new BitmapFactory.Options();o.inSampleSize=sample;android.graphics.Bitmap bm;
        try(InputStream in=getContentResolver().openInputStream(uri)){bm=BitmapFactory.decodeStream(in,null,o);}
        if(bm==null)throw new IOException("Not an image.");
        int rotate=0;
        try(InputStream in=getContentResolver().openInputStream(uri)){if(in!=null){int t=new android.media.ExifInterface(in).getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION,1);
                rotate=t==6?90:t==3?180:t==8?270:0;}}catch(Exception ignored){}
        float scale=Math.min(1f,1600f/Math.max(bm.getWidth(),bm.getHeight()));android.graphics.Matrix m=new android.graphics.Matrix();
        m.postScale(scale,scale);m.postRotate(rotate);
        android.graphics.Bitmap out=android.graphics.Bitmap.createBitmap(bm,0,0,bm.getWidth(),bm.getHeight(),m,true);
        String name=UUID.randomUUID()+".jpg";
        try(OutputStream os=new FileOutputStream(new File(photoDir(),name))){if(!out.compress(android.graphics.Bitmap.CompressFormat.JPEG,85,os))throw new IOException("Could not save the photo.");}return name;
    }
    android.graphics.Bitmap photoBitmap(String name,int max){File file=new File(photoDir(),name);if(name.isEmpty()||!file.isFile())return null;
        BitmapFactory.Options b=new BitmapFactory.Options();b.inJustDecodeBounds=true;BitmapFactory.decodeFile(file.getPath(),b);int s=1;
        while(Math.max(b.outWidth,b.outHeight)/(s*2)>=max)s*=2;BitmapFactory.Options o=new BitmapFactory.Options();o.inSampleSize=s;
        return BitmapFactory.decodeFile(file.getPath(),o);}
    int photoCount(){int n=0;for(Budget.Entry e:budget.entries)if(!e.photo.isEmpty())n++;return n;}
    /** Deletes photo files no transaction uses (a form cancelled after adding one, a deleted transaction); keeps those Undo restore / Undo budget reset could bring back. */
    private void cleanupPhotos(){Set<String> used=new HashSet<>();for(Budget.Entry e:budget.entries)used.add(e.photo);
        for(String key:new String[]{"before_restore","before_reset"}){java.util.regex.Matcher m=java.util.regex.Pattern.compile("\"photo\":\"([^\"]+)\"").matcher(prefs().getString(key,""));
            while(m.find())used.add(m.group(1));}
        File[] files=new File(getDataDir(),"files/photos").listFiles(); /* listing only: getFilesDir() would create the folder */
        if(files!=null)for(File file:files)if(!used.contains(file.getName()))file.delete();}
    private void write(Uri uri,String text)throws IOException{
        OutputStream out;
        try{out=getContentResolver().openOutputStream(uri,"wt");}catch(FileNotFoundException|IllegalArgumentException|UnsupportedOperationException e){out=getContentResolver().openOutputStream(uri,"w");}
        if(out==null)throw new IOException();try(OutputStream o=out){o.write(text.getBytes(StandardCharsets.UTF_8));}
    }
    // Hunt 23 M6: well above what MyBudget writes (a busy year is about 0.75 MB; it was 10 MB, which years of data could pass).
    static final int MAX_BACKUP=50_000_000;
    private String read(Uri uri)throws IOException{
        try(InputStream in=getContentResolver().openInputStream(uri)){if(in==null)throw new IOException();
            ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buffer=new byte[8192];int n;
            while((n=in.read(buffer))>0){out.write(buffer,0,n);
                if(out.size()>MAX_BACKUP)throw new IOException("This file is too large to be a MyBudget backup.");}
            String text;try{text=CsvImport.decode(out.toByteArray());}catch(OutOfMemoryError e){throw new IOException("This backup is too large to open on this phone.");}return text.startsWith("﻿")?text.substring(1):text;}
    }
    // Hide amounts (⋮ menu) shows dots instead of money everywhere on screen, for showing the plan to someone.
    boolean hideAmounts,darkTheme;
    // What the screen showed last time, for motion only: the tab and month (to animate a change of either), To budget (to count from the
    // old figure to the new one), and which targets were short (so a target that just became funded gets its check mark pop).
    String shownTab;YearMonth shownMonth;Long shownReady;YearMonth shownReadyMonth;private ScrollView screenScroll;final Set<String> shownUnfunded=new HashSet<>();
    /** What a tab is called on screen (MyBudget's own names; the keys stay as saved in older sessions). */
    static String tabTitle(String tab){switch(tab){case"Plan":return "Budget";case"Spending":return "Transactions";case"Reflect":return "Reports";default:return tab;}}
    void render(){
        if(popup!=null){android.widget.PopupWindow open=popup;popup=null;open.dismiss();}
        budget.fromPlanner.clear();budget.fromPlanner.addAll(PlannerBills.read(this,budget)); // Planner may have sent a new list meanwhile
        if(!budget.cached())budget.cache(true); // month maths are kept from screen to screen until a change (commit), which works them out afresh
        int keepY=screenScroll!=null&&tab.equals(shownTab)&&month.equals(shownMonth)?screenScroll.getScrollY():0; // the same screen again (after a save): stay where you were
        root=ui.column();root.setBackgroundColor(canvas);root.setPadding(ui.dp(16),ui.dp(12),ui.dp(16),ui.dp(8));setContentView(root);
        root.setOnApplyWindowInsetsListener((v,i)->{root.setPadding(ui.dp(16),i.getSystemWindowInsetTop()+ui.dp(8),ui.dp(16),i.getSystemWindowInsetBottom()+ui.dp(4));return i;});
        LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);
        ImageView logo=new ImageView(this);logo.setImageResource(R.drawable.brand_mark);
        logo.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        LinearLayout.LayoutParams logoSize=new LinearLayout.LayoutParams(ui.dp(40),ui.dp(40));logoSize.setMargins(0,0,ui.dp(12),0);
        header.addView(logo,logoSize);
        LinearLayout brand=ui.column();TextView wordmark=ui.label("MyBudget",21,ink,true);
        wordmark.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));wordmark.setLetterSpacing(0.01f);wordmark.setPadding(0,0,0,0);
        SpannableString brandName=new SpannableString("MyBudget");
        brandName.setSpan(new android.text.style.ForegroundColorSpan(blue),0,2,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        wordmark.setText(brandName);brand.addView(wordmark);
        TextView tagline=ui.label("Your money. Your plan.",11,muted,false);tagline.setPadding(0,ui.dp(2),0,0);brand.addView(tagline);
        header.addView(brand,new LinearLayout.LayoutParams(0,-2,1));
        Button overflow=ui.button("\u22ee",()->{});overflow.setContentDescription("More options");overflow.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP,26);overflow.setMinWidth(0);
        overflow.setMinimumWidth(0);overflow.setPadding(0,0,0,0);overflow.setBackground(ui.bg(Color.TRANSPARENT));
        overflow.setOnClickListener(v->options(v));header.addView(overflow,new LinearLayout.LayoutParams(ui.dp(48),ui.dp(48)));LinearLayout top=ui.column();top.addView(header);
        top.addView(ui.heading(tabTitle(tab),28,ink));
        LinearLayout months=new LinearLayout(this);months.setGravity(Gravity.CENTER_VERTICAL);
        Button previous=ui.button("\u2039",()->{Ui.tick(root);month=month.minusMonths(1);render();});previous.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP,26);
        previous.setContentDescription("Previous month");previous.setBackground(ui.bg(Color.TRANSPARENT));
        months.addView(previous,new LinearLayout.LayoutParams(ui.dp(48),ui.dp(48)));
        TextView title=ui.label(month.format(DateTimeFormatter.ofPattern("MMMM yyyy")),16,ink,true);title.setGravity(Gravity.CENTER);
        months.addView(title,new LinearLayout.LayoutParams(0,-2,1));Button next=ui.button("\u203a",()->{Ui.tick(root);month=month.plusMonths(1);render();});
        next.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP,26);next.setContentDescription("Next month");next.setBackground(ui.bg(Color.TRANSPARENT));
        months.addView(next,new LinearLayout.LayoutParams(ui.dp(48),ui.dp(48)));if(!tab.equals("Settings"))top.addView(months);
        ScrollView scroll=new ScrollView(this);content=ui.column();scroll.addView(content);
        if(getResources().getConfiguration().fontScale>=1.3f)content.addView(top);else root.addView(top); // large text: the header scrolls away with the screen, leaving room for it
        body=new FrameLayout(this);body.addView(scroll,new FrameLayout.LayoutParams(-1,-1));root.addView(body,new LinearLayout.LayoutParams(-1,0,1)); // body: the screen, with room for a floating button over it
        switch(tab){case"Settings":settingsScreen.settings();break;case"Home":homeScreen.home();break;case"Plan":budgetScreen.plan();break;case"Spending":transactionsScreen.spending();break;case"Accounts":accountsScreen.accounts();break;default:reportsScreen.reflect();}
        // A new tab fades up into place; another month slides in from the side it came from. A plain re-render (after a save) doesn't move.
        boolean newTab=!tab.equals(shownTab),newMonth=!newTab&&shownMonth!=null&&!month.equals(shownMonth);
        if(Ui.motion()&&shownTab!=null&&(newTab||newMonth)){scroll.setAlpha(0f);if(newTab)scroll.setTranslationY(ui.dp(14));
            else scroll.setTranslationX(ui.dp(month.isAfter(shownMonth)?28:-28));
            scroll.animate().alpha(1f).translationX(0f).translationY(0f).setDuration(220).setInterpolator(new android.view.animation.DecelerateInterpolator()).start();}
        boolean tabChanged=shownTab!=null&&newTab;shownTab=tab;shownMonth=month;screenScroll=scroll;
        if(keepY>0)scroll.post(()->scroll.scrollTo(0,keepY));
        addUndoBar(); // a delete's Undo, above the tabs (gone on another tab)
        if(tab.equals("Settings")){root.addView(ui.button("Back",this::closeSettings));return;}
        LinearLayout nav=new LinearLayout(this);nav.setPadding(ui.dp(4),ui.dp(4),ui.dp(4),ui.dp(4));nav.setBackground(ui.surface(surface));
        String[] names={"Home","Plan","Spending","Accounts","Reflect"};
        int[] icons={R.drawable.nav_home,R.drawable.nav_plan,R.drawable.nav_spending,R.drawable.nav_accounts,R.drawable.nav_reflect};
        for(int i=0;i<names.length;i++){String name=names[i];boolean selected=tab.equals(name);
            Button b=ui.button(tabTitle(name),()->{if(!tab.equals(name))Ui.tick(root);tab=name;accountFilter="";render();});b.setTextSize(10);b.setMaxLines(1);
            b.setAutoSizeTextTypeUniformWithConfiguration(ui.dp(7),Math.max(ui.dp(8),Math.round(android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_SP,10,getResources().getDisplayMetrics()))),1,android.util.TypedValue.COMPLEX_UNIT_PX); // large text shrinks to fit the tab
            b.setPadding(ui.dp(2),ui.dp(7),ui.dp(2),ui.dp(5));b.setBackground(ui.bg(selected?primary:Color.TRANSPARENT));
            b.setTextColor(selected?Color.WHITE:muted);b.setSelected(selected);b.setContentDescription(tabTitle(name)+(selected?", selected":""));
            if(selected)b.setTypeface(null,Typeface.BOLD);Drawable icon=getDrawable(icons[i]).mutate();icon.setTint(selected?Color.WHITE:muted);
            icon.setBounds(0,0,ui.dp(20),ui.dp(20));b.setCompoundDrawables(null,icon,null,null);b.setCompoundDrawablePadding(ui.dp(4));
            LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,ui.dp(60),1);p.setMargins(ui.dp(1),0,ui.dp(1),0);
            nav.addView(b,p);if(selected&&tabChanged)Ui.pop(b);}
        LinearLayout.LayoutParams np=new LinearLayout.LayoutParams(-1,-2);np.setMargins(0,ui.dp(6),0,0);root.addView(nav,np);
    }
    // Changes from a menu: the category is looked up again by id, in case the budget was reloaded meanwhile.
    boolean change(Runnable action){try{commit(action);render();return true;}catch(Exception e){ui.toast(e.getMessage());return false;}}
    Budget.Category categoryById(String id){Budget.Category c=budget.category(id);
        if(c==null)throw new IllegalArgumentException("That category no longer exists.");return c;}
    void clearFilters(){search="";accountFilter="";categoryFilter="";fromFilter="";toFilter="";flagFilter=-1;clearedFilter=-1;}
    // Reports: spending breakdown (period, by category or group), spending trends, the income and expense table. Periods count back from the month on screen.
    int period,trendMonths=6;boolean byGroup;String trendKey="";
    int reportYear; // the yearly report's year; 0: this year
    void commit(Runnable action){
        if(!storageReadable)throw new IllegalStateException("Saved data could not be read.");
        // Saved meanwhile (split screen: an expense from Planner): open forms hold the old budget, so they close and nothing
        // is saved over the new data; the change is made again on what is there now.
        if(reloadIfChanged()){for(AlertDialog editor:new ArrayList<>(editors))editor.dismiss();render();
            throw new IllegalArgumentException("MyBudget changed meanwhile (an expense from Planner, or a reminder's button). Make your change again.");}
        // What a failed change puts back: the saved data, which the budget matches between changes (read again only then: reading
        // years of transactions takes a while), or a copy while nothing is saved yet (the starter categories).
        String saved=loaded;Budget copy=null;
        if(saved==null)try{copy=BudgetStore.decode(BudgetStore.encode(budget));copy.fromPlanner.addAll(budget.fromPlanner);}catch(Exception e){throw new IllegalStateException("Could not prepare save.");}
        // Month maths stay cached during [action]: Budget's own changes (assign, move, enter, deleting...) clear them; an action that
        // changes fields or lists straight and then reads month maths must call budget.changed() in between. Fresh again after.
        budget.cache(true);
        try{action.run();budget.changed();String raw=BudgetStore.encode(budget);
            if(!getSharedPreferences("budget",0).edit().putString("data",raw).commit())throw new IllegalStateException("Could not save to device storage.");loaded=raw;BudgetWidget.refresh(this);}
        catch(Exception e){Budget back=copy;
            if(back==null)try{back=BudgetStore.decode(saved);back.fromPlanner.addAll(budget.fromPlanner);}catch(Exception x){storageReadable=false;throw new IllegalStateException("Your budget couldn't be saved or put back. Close MyBudget and open it again.");}
            budget=back;throw new IllegalArgumentException(e.getMessage()==null?"Check your entry.":e.getMessage());}
        undoBefore=null; // another change: a delete's Undo is gone
    }
    /** Home's backup reminder: an account, and no backup for 14 days (or never), unless snoozed for the week (DataSafety). */
    boolean backupDue(){return DataSafety.backupReminderDue(!budget.accounts.isEmpty(),AutoBackup.lastBackup(this),prefs().getString("backup_reminder_until",null),LocalDate.now());}
    // Undo for deletes: the saved data from just before the delete and what the delete saved. A bar above the tabs offers
    // Undo for 8 seconds, until the tab changes or another change is saved; it puts [undoBefore] back through commit(),
    // only while the saved data is still [undoAfter] (DataSafety.undoAllowed), so nothing saved since is overwritten.
    static final long UNDO_MILLIS=8000;String undoBefore,undoAfter,undoText,undoTab;private long undoUntil;private int undoShown;private View undoBar;
    /** A delete, as change(), then the Undo bar ([done]: "Transaction deleted"). */
    boolean deleteWithUndo(String done,Runnable action){String[] before={null};
        try{commit(()->{try{before[0]=loaded!=null?loaded:BudgetStore.encode(budget);}catch(Exception e){throw new IllegalStateException("Could not prepare save.");}action.run();});} // the budget matches the saved data until the change
        catch(Exception e){ui.toast(e.getMessage());return false;}
        undoBefore=before[0];undoAfter=loaded;undoText=done;undoTab=tab;undoUntil=android.os.SystemClock.uptimeMillis()+UNDO_MILLIS;render();
        root.announceForAccessibility(done+". Undo is available for a few seconds.");return true;}
    private void hideUndo(){undoBefore=null;if(undoBar!=null&&undoBar.getParent()instanceof LinearLayout)((LinearLayout)undoBar.getParent()).removeView(undoBar);undoBar=null;}
    private void addUndoBar(){
        long left=undoUntil-android.os.SystemClock.uptimeMillis();if(undoBefore==null||!tab.equals(undoTab)||left<=0){undoBefore=null;undoBar=null;return;}
        LinearLayout bar=new LinearLayout(this);bar.setGravity(Gravity.CENTER_VERTICAL);bar.setPadding(ui.dp(16),0,ui.dp(4),0);bar.setBackground(ui.bg(primary));
        TextView text=ui.label(undoText,14,Color.WHITE,false);bar.addView(text,new LinearLayout.LayoutParams(0,-2,1));
        Button undo=ui.button("Undo",this::undo);undo.setTextColor(Color.WHITE);undo.setTypeface(null,Typeface.BOLD);undo.setBackground(ui.bg(Color.TRANSPARENT));
        undo.setContentDescription("Undo: "+undoText);bar.addView(undo,new LinearLayout.LayoutParams(-2,-2));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,ui.dp(6),0,0);root.addView(bar,p);undoBar=bar;
        if(Ui.motion()&&left>UNDO_MILLIS-400){bar.setAlpha(0f);bar.setTranslationY(ui.dp(16));bar.animate().alpha(1f).translationY(0f).setDuration(220).start();} // slides up when it first shows
        int shown=++undoShown;getWindow().getDecorView().postDelayed(()->{if(shown==undoShown)hideUndo();},left);}
    private void undo(){String before=undoBefore,after=undoAfter,done=undoText;hideUndo();if(before==null)return;
        if(!DataSafety.undoAllowed(prefs().getString("data",null),after,loaded)){
            try{if(reloadIfChanged())for(AlertDialog editor:new ArrayList<>(editors))editor.dismiss();}catch(IllegalStateException e){ui.toast(e.getMessage());}
            render();ui.toast("This can't be undone now: your budget changed since (an expense from Planner came in, or a backup was restored).");return;}
        try{commit(()->{Budget previous;try{previous=BudgetStore.decode(before);}catch(Exception e){throw new IllegalStateException("The budget from before can't be read. Nothing was changed.");}
                previous.fromPlanner.addAll(budget.fromPlanner);budget=previous;});}
        catch(Exception e){ui.toast(e.getMessage());render();return;}
        for(AlertDialog editor:new ArrayList<>(editors))editor.dismiss();render();String back=done.replace(" deleted"," restored")+".";ui.toast(back);}
    final List<AlertDialog> editors=new ArrayList<>();
    /** The ⋮ menu open now (MoveMenu.show): closed when the screen is drawn again, so it never acts on old figures (hunt 27 H2). */
    android.widget.PopupWindow popup;
    Budget.Account accountById(String id){Budget.Account a=budget.account(id);
        if(a==null)throw new IllegalArgumentException("That account no longer exists.");return a;}
    /** Open accounts, plus [keep] (an old transaction's accounts) even when closed. */
    List<Budget.Account> openAccounts(Budget.Account... keep){List<Budget.Account> list=new ArrayList<>();
        for(Budget.Account a:budget.accounts)if(!a.closed||Arrays.asList(keep).contains(a))list.add(a);return list;}
    /** Categories to spend from (not hidden, not a card payment), plus [keep] (an old transaction's category) even when hidden. */
    List<Budget.Category> visibleCategories(Budget.Category keep){List<Budget.Category> list=new ArrayList<>();
        for(Budget.Category c:budget.categories)if((!c.hidden&&!c.payment())||c==keep)list.add(c);return list;}
    void load(){
        String raw=getSharedPreferences("budget",0).getString("data",null);loaded=raw;
        if(raw==null){budget.addStarter(Budget.starterGroups());return;} // the starter categories (Budget.STARTER), saved with the first change
        try{budget=BudgetStore.decode(raw);if(!raw.contains("\"version\"")){String updated=BudgetStore.encode(budget);
                if(!getSharedPreferences("budget",0).edit().putString("legacy_backup",raw).putString("data",updated).commit())throw new IllegalStateException("Migration could not be saved.");
                loaded=updated;BudgetWidget.refresh(this);ui.toast("Budget upgraded. Existing balances preserved; monthly assignments begin this month.");}}
        catch(Exception e){storageReadable=false;new AlertDialog.Builder(this).setTitle("Unable to load budget")
                .setMessage("Your saved data has been preserved. Close the app to avoid changes.").setPositiveButton("Close",(d,w)->finish())
                .setCancelable(false).show();}
    }
}
