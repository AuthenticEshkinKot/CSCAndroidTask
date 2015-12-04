package com.example.YandexTask;

import android.accounts.*;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by MAX on 04.07.2014.
 */
public class TokenManager {
    public static final String CLIENT_ID = "165fe1f0a4a649448ec00e243c47a184";
    public static final String CLIENT_SECRET = "6b36152755a6441fa137ee3a505a4237";
    public static final String ACCOUNT_TYPE = "com.yandex";
    public static final String AUTH_URL = "https://oauth.yandex.ru/authorize?response_type=token&client_id="+CLIENT_ID;
    public static final String USERNAME = "slideshower.username";
    public static final String TOKEN = "slideshower.token";

    private static final String ACTION_ADD_ACCOUNT = "com.yandex.intent.ADD_ACCOUNT";
    private static final String TAG = "TokenManager";
    private static final String KEY_CLIENT_SECRET = "clientSecret";
    private static final int GET_ACCOUNT_CREDS_INTENT = 100;

    private MainActivity parentActivity;

    public TokenManager(MainActivity parentActivity) {
        this.parentActivity = parentActivity;
    }

    public void OnLogin () {
        Uri data = parentActivity.getIntent().getData();
        parentActivity.setIntent(null);
        Pattern pattern = Pattern.compile("access_token=(.*?)(&|$)");
        Matcher matcher = pattern.matcher(data.toString());
        if (matcher.find()) {
            final String token = matcher.group(1);
            if (!TextUtils.isEmpty(token)) {
                Log.d(TAG, "onLogin: token: " + token);
                saveToken(token);
            } else {
                Log.w(TAG, "onRegistrationSuccess: empty token");
            }
        } else {
            Log.w(TAG, "onRegistrationSuccess: token not found in return url");
        }
    }

    public void GetToken() {
        AccountManager accountManager = AccountManager.get(parentActivity.getApplicationContext());
        Account[] accounts = accountManager.getAccountsByType(ACCOUNT_TYPE);

        Log.d(TAG, "accounts: " + (accounts != null ? accounts.length : null));

        if (accounts != null && accounts.length > 0) {
            // get the first account, for example (you must show the list and allow user to choose)
            Account account = accounts[0];
            Log.d(TAG, "account: "+account);
            getAuthToken(account);
            return;
        }

        Log.d(TAG, "No such accounts: "+ACCOUNT_TYPE);
        for (AuthenticatorDescription authDesc : accountManager.getAuthenticatorTypes()) {
            if (ACCOUNT_TYPE.equals(authDesc.type)) {
                Log.d(TAG, "Starting "+ACTION_ADD_ACCOUNT);
                Intent intent = new Intent(ACTION_ADD_ACCOUNT);
                parentActivity.startActivityForResult(intent, GET_ACCOUNT_CREDS_INTENT);
                return;
            }
        }

        // no account manager for com.yandex
        loginUser();
    }

    private void loginUser() {
        Log.d(TAG, "Need to login user: "+ACCOUNT_TYPE);
        new AlertDialog.Builder(parentActivity)
                .setTitle(R.string.auth_title)
                .setMessage(R.string.auth_message)
                .setPositiveButton(R.string.auth_positive_button, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick (DialogInterface dialog, int which) {
                        dialog.dismiss();
                        parentActivity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(AUTH_URL)));
                    }
                })
                .setNegativeButton(R.string.auth_negative_button, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick (DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                })
                .create().show();
    }

    private void getAuthToken(Account account) {
        AccountManager systemAccountManager = AccountManager.get(parentActivity.getApplicationContext());
        Bundle options = new Bundle();
        options.putString(KEY_CLIENT_SECRET, CLIENT_SECRET);
        systemAccountManager.getAuthToken(account, CLIENT_ID, options, parentActivity, new GetAuthTokenCallback(), null);
    }

    private class GetAuthTokenCallback implements AccountManagerCallback<Bundle> {
        public void run(AccountManagerFuture<Bundle> result) {
            try {
                Bundle bundle = result.getResult();
                Log.d(TAG, "bundle: "+bundle);

                String message = (String) bundle.get(AccountManager.KEY_ERROR_MESSAGE);
                if (message != null) {
                    Toast.makeText(parentActivity, message, Toast.LENGTH_LONG).show();
                }

                Intent intent = (Intent) bundle.get(AccountManager.KEY_INTENT);
                Log.d(TAG, "intent: "+intent);
                if (intent != null) {
                    // User input required
                    parentActivity.startActivityForResult(intent, GET_ACCOUNT_CREDS_INTENT);
                } else {
                    String token = bundle.getString(AccountManager.KEY_AUTHTOKEN);
                    Log.d(TAG, "GetAuthTokenCallback: token="+token);
                    saveToken(token);
                    parentActivity.ShowDiskContents(token, "/");
                }
            } catch (Exception e) {
                Log.d(TAG, "GetAuthTokenCallback", e);
                Toast.makeText(parentActivity, e.getMessage(), Toast.LENGTH_LONG).show();
            }
        }
    }

    private void saveToken(String token) {
        SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(parentActivity).edit();
        editor.putString(USERNAME, "");
        editor.putString(TOKEN, token);
        editor.commit();
    }
}
