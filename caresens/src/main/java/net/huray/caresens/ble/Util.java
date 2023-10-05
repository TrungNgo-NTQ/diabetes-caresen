package net.huray.caresens.ble;

import static android.content.Context.MODE_PRIVATE;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.widget.Toast;

/**
 * Created by user on 2017-12-18.
 */

public class Util {

    //Instance
    private static Util instance;
    private Context mContext;

    //private construct
    private Util(Context context) {
        mContext = context;
    }

    public static synchronized Util getInstance(Context context) {
        if (instance == null) { instance = new Util(context);}
        return instance;
    }

    public int getPreference(String key) {
        SharedPreferences pref = mContext.getSharedPreferences("pref_ble_example", MODE_PRIVATE);
        return pref.getInt(key, 0);
    }

    public void setPreference(String key, int value) {
        SharedPreferences pref = mContext.getSharedPreferences("pref_ble_example", MODE_PRIVATE);
        SharedPreferences.Editor editor = pref.edit();
        editor.putInt(key, value);
        editor.commit();
    }

    public boolean getPreferenceBool(String key) {
        SharedPreferences pref = mContext.getSharedPreferences("pref_ble_example", MODE_PRIVATE);
        return pref.getBoolean(key, false);
    }

    public void setPreference(String key, boolean value) {
        SharedPreferences pref = mContext.getSharedPreferences("pref_ble_example", MODE_PRIVATE);
        SharedPreferences.Editor editor = pref.edit();
        editor.putBoolean(key, value);
        editor.commit();
    }

    public void showToast(String text) {
        Toast.makeText(mContext, text, Toast.LENGTH_SHORT).show();
    }

    public boolean runningOnKitkatOrHigher() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT;
    }

}
