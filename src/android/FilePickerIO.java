package com.dbaq.cordova.filepickerio;

import java.util.ArrayList;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import com.filestack.Config;
import com.filestack.android.FsActivity;
import com.filestack.android.FsConstants;
import com.filestack.android.Selection;


public class FilePickerIO extends CordovaPlugin {

    private CallbackContext callbackContext;

    private JSONArray executeArgs;

    private String action;

    private String apiKey;

    public static final int REQUEST_FILESTACK = 1000;

    public static final String ACTION_SET_KEY = "setKey";

    public static final String ACTION_SET_NAME = "setName";

    public static final String ACTION_PICK = "pick";

    public static final String ACTION_PICK_AND_STORE = "pickAndStore";

    public static final String ACTION_HAS_PERMISSION = "hasPermission";

    private static final String LOG_TAG = "FilePickerIO";

    public FilePickerIO() {}

    /**
     * Executes the request and returns PluginResult.
     *
     * @param action            The action to execute.
     * @param args              JSONArray of arguments for the plugin.
     * @param callbackContext   The callback context used when calling back into JavaScript.
     * @return                  True if the action was valid, false otherwise.
     */
    public boolean execute(final String action, final JSONArray args, final CallbackContext callbackContext) throws JSONException {
        this.callbackContext = callbackContext;
        this.executeArgs = args;
        this.action = action;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || action.equals(ACTION_HAS_PERMISSION)) {
            callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.OK, hasPermission()));
            return true;
        }
        else {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || action.equals(ACTION_SET_KEY) || action.equals(ACTION_SET_NAME)) {
                execute();
                return true;
            }
            else {
                if (hasPermission()) {
                    execute();
                } else {
                    requestPermission();
                }
                return true;
            }
        }
    }

    private boolean hasPermission() {
        return cordova.hasPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE);
    }

    private void requestPermission() {
        cordova.requestPermission(this, 0, android.Manifest.permission.WRITE_EXTERNAL_STORAGE);
    }

    public void onRequestPermissionResult(int requestCode, String[] permissions, int[] grantResults) throws JSONException {
        for (int r : grantResults) {
            if (r == PackageManager.PERMISSION_DENIED) {
                callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.ERROR, "User has denied permission"));
                return;
            }
        }
        execute();
    }

    public void execute() {
        final FilePickerIO cdvPlugin = this;
        this.cordova.getThreadPool().execute(() -> {
            try {
                if (ACTION_SET_KEY.equals(cdvPlugin.getAction())) {
                    this.apiKey = cdvPlugin.getArgs().getString(0);
                    return;
                }

                Context context = cordova.getActivity().getApplicationContext();
                Intent intent = new Intent(context, FsActivity.class);
                Config config = new Config(this.apiKey);
                intent.putExtra(FsConstants.EXTRA_CONFIG, config);
                intent.putExtra(FsConstants.EXTRA_AUTO_UPLOAD, true);
                if (ACTION_PICK.equals(cdvPlugin.getAction()) || ACTION_PICK_AND_STORE.equals(cdvPlugin.getAction())) {
                    parseGlobalArgs(intent, cdvPlugin.getArgs());
                    if (ACTION_PICK_AND_STORE.equals(cdvPlugin.getAction())) {
                        parseStoreArgs(intent, cdvPlugin.getArgs());
                    }
                    cordova.startActivityForResult(cdvPlugin, intent, REQUEST_FILESTACK);
                }
            }
            catch(JSONException exception) {
                cdvPlugin.getCallbackContext().error("cannot parse json");
            }
        });
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_FILESTACK) {
            if (resultCode == Activity.RESULT_OK) {
                ArrayList<Selection> selections = data.getParcelableArrayListExtra(FsConstants.EXTRA_SELECTION_LIST);
                try{
                    callbackContext.success(toJSON(selections));
                }
                catch(JSONException exception) {
                    callbackContext.error("json exception");
                }
            } else {
                callbackContext.error("nok");
            }
        }
        else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    public void parseGlobalArgs(Intent intent, JSONArray args) throws JSONException {
        if (!args.isNull(0)) {
            intent.putExtra("mimetype", parseJSONStringArray(args.getJSONArray(0)));
        }
        if (!args.isNull(1)) {
            intent.putExtra("services", parseJSONStringArray(args.getJSONArray(1)));
        }
        if (!args.isNull(2)) {
            intent.putExtra("multiple", args.getBoolean(2));
        }
        if (!args.isNull(3)) {
            intent.putExtra("maxFiles", args.getInt(3));
        }
        if (!args.isNull(4)) {
            intent.putExtra("maxSize", args.getInt(4));
        }
    }

    public void parseStoreArgs(Intent intent, JSONArray args) throws JSONException {
        if (!args.isNull(5)) {
            intent.putExtra("location", args.getString(5));
        }
        if (!args.isNull(6)) {
            intent.putExtra("path", args.getString(6));
        }
        if (!args.isNull(7)) {
            intent.putExtra("container", args.getString(7));
        }
        if (!args.isNull(8)) {
            intent.putExtra("access", args.getString(8));
        }
    }

    public String[] parseJSONStringArray(JSONArray jSONArray) throws JSONException {
        String[] a = new String[jSONArray.length()];
        for(int i = 0; i < jSONArray.length(); i++){
            a[i] = jSONArray.getString(i);
        }
        return a;
    }

    public JSONArray toJSON(ArrayList<Selection> selections) throws JSONException {
        JSONArray res = new JSONArray();
        for (Selection selection : selections) {
            JSONObject f = new JSONObject();
            f.put("provider", selection.getProvider());
            f.put("url", selection.getUri());
            f.put("filename", selection.getName());
            f.put("mimetype", selection.getMimeType());
            f.put("localPath", selection.getPath());
            f.put("size", selection.getSize());

            res.put(f);
        }
        return res;
    }

    public String getAction() {
        return this.action;
    }

    public JSONArray getArgs() {
        return this.executeArgs;
    }

    public CallbackContext getCallbackContext() {
        return this.callbackContext;
    }
}
