package com.mybudget.app;

import android.content.*;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.*;
import java.util.List;

/**
 * Take a photo: the camera app writes the new photo into one file in the cache (cache/camera/<uuid>.jpg), through a
 * content uri it's granted for that capture only, like a FileProvider (framework only). Not exported; nothing else is served.
 */
public final class PhotoProvider extends ContentProvider {
    static Uri uri(Context c,String name){return new Uri.Builder().scheme("content").authority(c.getPackageName()+".photos").appendPath(name).build();}
    static File dir(Context c){return new File(c.getCacheDir(),"camera");}
    private File file(Uri uri)throws FileNotFoundException{List<String> p=uri.getPathSegments();
        if(p.size()!=1||!p.get(0).matches("[0-9a-f-]{36}[.]jpg"))throw new FileNotFoundException();return new File(dir(getContext()),p.get(0));} // a file name only: never a path
    @Override public boolean onCreate(){return true;}
    @Override public ParcelFileDescriptor openFile(Uri uri,String mode)throws FileNotFoundException{return ParcelFileDescriptor.open(file(uri),ParcelFileDescriptor.parseMode(mode));}
    @Override public String getType(Uri uri){return "image/jpeg";}
    @Override public Cursor query(Uri uri,String[] projection,String selection,String[] args,String sort){File f;try{f=file(uri);}catch(FileNotFoundException e){return null;}
        MatrixCursor c=new MatrixCursor(new String[]{OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE});c.addRow(new Object[]{f.getName(),f.length()});return c;}
    @Override public Uri insert(Uri uri,ContentValues values){return null;}
    @Override public int delete(Uri uri,String selection,String[] args){return 0;}
    @Override public int update(Uri uri,ContentValues values,String selection,String[] args){return 0;}
}
