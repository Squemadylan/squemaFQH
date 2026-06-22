package com.byterax.phoenix.read.service;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;

import com.byterax.phoenix.read.Constants;

public class ServiceProvider extends ContentProvider {

    public static final String METHOD_PULL = "pull";

    @Override
    public boolean onCreate() {
        return false;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
                        String[] selectionArgs, String sortOrder) {
        return null;
    }

    @Override
    public String getType(Uri uri) {
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        String caller = getCallingPackage();

        if (METHOD_PULL.equals(method) && Constants.MODULE_PACKAGE.equals(caller)) {
            boolean connected = ServiceClient.get().tryConnect();
            Bundle reply = new Bundle();
            reply.putBoolean("connected", connected);
            reply.putInt("version", ServiceClient.get().getServiceVersion());
            return reply;
        }

        if (!Constants.ANDROID_PACKAGE.equals(caller)) {
            return null;
        }
        if (extras == null) {
            return null;
        }
        ServiceClient.get().linkService(extras.getBinder("binder"));
        return new Bundle();
    }
}
