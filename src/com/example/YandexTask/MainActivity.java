package com.example.YandexTask;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v7.app.ActionBarActivity;
import android.view.*;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import java.io.File;

public class MainActivity extends ActionBarActivity {

    public static final String TOKEN = "slideshower.token";

    private static final String TAG = "MainActivity";

    private TokenManager tokenManager;
    private ListView lv;

    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        lv = (ListView) findViewById(R.id.listView);
        tokenManager = new TokenManager(this);

        if(tryAuthorize()) {
            if(!DirectoryManager.IsInited()) {
                deleteOldFiles();
            }

            DirectoryManager.getInst().OnConfigurationChange(this, getToken());
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.action_bar_menu, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle presses on the action bar items
        switch (item.getItemId()) {
            case R.id.action_refresh:
                String token = getToken();
                if(token != null) {
                    DirectoryManager.getInst().RefreshDiskContent(token);
                } else {
                    tryAuthorize();
                }
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public boolean onKeyDown ( int keyCode, KeyEvent event )
    {
        if (keyCode == KeyEvent.KEYCODE_BACK)
        {
            String token = getToken();
            if (token != null && DirectoryManager.getInst().TryDirectoryFallback(token)) {
                return false;
            }
        }

        return super.onKeyDown(keyCode, event);  // This is original event operations
    }

    public void UpdateList(ArrayAdapter<String> adapter, AdapterView.OnItemClickListener listener, String currentDir) {
        lv.setAdapter(adapter);
        lv.setOnItemClickListener(listener);

        getSupportActionBar().setSubtitle(currentDir);
        getSupportActionBar().setDisplayHomeAsUpEnabled(!DirectoryManager.getInst().IsRoot());
    }

    public Intent getSupportParentActivityIntent () {
        DirectoryManager.getInst().TryDirectoryFallback(getToken());
        return super.getSupportParentActivityIntent();
    }

    public void ShowDiskContents(String token, String path) {
        DirectoryManager.getInst().ShowDiskContent(token, path);
    }

    private boolean tryAuthorize() {
        boolean res = true;

        if (getIntent() != null && getIntent().getData() != null) {
            tokenManager.OnLogin();
        }

        if (getToken() == null) {
            tokenManager.GetToken();
            res = false;
        }

        return res;
    }

    private void deleteOldFiles() {
        File dir = getExternalFilesDir(null);
        deleteFilesInDir(dir);

        dir = getFilesDir();
        deleteFilesInDir(dir);
    }

    private void deleteFilesInDir(File dir) {
        if(dir != null) {
            File[] files = dir.listFiles();
            if(files != null) {
                for (File file : files) {
                    file.delete();
                }
            }
        }
    }

    private String getToken() {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        return preferences.getString(TOKEN, null);
    }
}
