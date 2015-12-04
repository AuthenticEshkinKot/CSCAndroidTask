package com.example.YandexTask;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import com.yandex.disk.client.*;

import java.util.ArrayList;

/**
 * Created by MAX on 29.06.2014.
 */
public class DirectoryManager {
    public static final String PATHS_NAME = "com.example.YandexTask.Paths";
    public static final String SIZES_NAME = "com.example.YandexTask.Sizes";
    public static final String SELECTED_IMAGE = "com.example.YandexTask.SelectedImage";
    public static final String TOKEN = "com.example.YandexTask.Token";
    public static final String ROTATE_FLAG = "com.example.YandexTask.ROTATE_FLAG";

    private static final String TAG = "DirectoryManager";

    private static DirectoryManager instance;

    private UpdateListTask task;
    private ProgressDialog pd;
    private MainActivity parentActivity;
    private String currentDir = "/";
    private boolean showErrorItem;
    private boolean listLoaded;

    private String[] names;
    private String[] paths;
    private long[] sizes;
    private String token;
    private int dirsNumber;
    private int listLength;

    private DirectoryManager() {
    }

    public static boolean IsInited() { return (instance != null); }

    public static DirectoryManager getInst() {
        if(instance == null) {
            instance = new DirectoryManager();
        }

        return instance;
    }

    public void ShowDiskContent(final String token, String path) {
        startLoading(token, path);
    }

    public void RefreshDiskContent(final String token) {
        if(listLoaded) {
            startLoading(token, currentDir);
        }
    }

    public void OnConfigurationChange(MainActivity parentActivity, String token) {
        this.parentActivity = parentActivity;
        if(listLoaded) {
            showProgressDialog();
            updateList();
        } else {
            if(task != null) {
                task.cancel(false);
            }

            startLoading(token, currentDir);
        }
    }

    public boolean TryDirectoryFallback(String token) {
        if(!currentDir.contentEquals("/")) {
            String newDir;
            if(currentDir.lastIndexOf("/") != 0) {
                newDir = currentDir.substring(0, currentDir.lastIndexOf("/"));
            } else {
                newDir = "/";
            }

            ShowDiskContent(token, newDir);
            return true;
        }

        return false;
    }

    private void startLoading(final String token, String path) {
        showProgressDialog();
        loadNewDirectory(token, path);
    }

    private void loadNewDirectory(final String token, String path) {
        task = new UpdateListTask();
        task.execute(token, path);
    }

    private void showProgressDialog() {
        if(pd != null) {
            pd.dismiss();
        }

        pd = new ProgressDialog(parentActivity);
        pd.setTitle("Processing");
        pd.setMessage("Wait a moment...");
        pd.setProgressStyle(ProgressDialog.STYLE_SPINNER);
        pd.show();
    }

    private class UpdateListTask extends AsyncTask<String, Void, Void> {

        protected Void doInBackground(String... params) {
            listLoaded = false;
            showErrorItem = true;

            names = new String[] { "No data received" };
            paths = new String[0];
            sizes = new long[0];
            dirsNumber = 0;
            listLength = 0;

            token = params[0];
            currentDir = params[1];

            final ArrayList<ListItem> dirs = new ArrayList<ListItem>();
            final ArrayList<ListItem> imgs = new ArrayList<ListItem>();

            TransportClient client = null;
            try {
                client = TransportClient.getInstance(parentActivity,
                        new Credentials("", token));

                client.getList(currentDir, 20, new ListParsingHandler() {
                    boolean ignoreFirstItem = true;

                    @Override
                    public boolean handleItem(ListItem item) {
                        if(isCancelled()) {
                            return false;
                        }

                        if(ignoreFirstItem) {
                            ignoreFirstItem = false;
                            return false;
                        } else {
                            //Log.d(TAG, "MIME-type = " + item.getContentType());
                            if(item.isCollection()) {
                                dirs.add(item);
                            } else if(item.getContentType() != null &&
                                    item.getContentType().length() != 0 &&
                                    item.getContentType().startsWith("image/") &&
                                    isSuitableType(item.getContentType())) {
                                imgs.add(item);
                            }
                            return false;
                        }
                    }

                    @Override
                    public void onPageFinished(int itemsOnPage) {
                        if(isCancelled()) {
                            return;
                        }

                        dirsNumber = dirs.size();
                        listLength = dirsNumber + imgs.size();

                        if(listLength != 0) {
                            showErrorItem = false;
                            names = new String[listLength];
                            paths = new String[listLength];
                            sizes = new long[listLength - dirsNumber];

                            for(int i = 0; i < dirsNumber; ++i) {
                                names[i] = dirs.get(i).getDisplayName();
                                paths[i] = dirs.get(i).getFullPath();
                            }

                            for(int i = dirsNumber, k = 0; i < listLength; ++i, ++k) {
                                names[i] = imgs.get(k).getDisplayName();
                                paths[i] = imgs.get(k).getFullPath();
                                sizes[k] = imgs.get(k).getContentLength();
                            }
                        } else {
                            names = new String[] { "No suitable data" };
                        }
                    }
                    private boolean isSuitableType(String mimeType) {
                        return (mimeType.endsWith("/png") ||
                                mimeType.endsWith("/jpeg") ||
                                mimeType.endsWith("/gif") ||
                                mimeType.endsWith("/bmp"));
                    }
                });
            } catch (Exception e) {
                Log.d(TAG, "GetAuthTokenCallback", e);
                e.printStackTrace();
            } finally {
                TransportClient.shutdown(client);

                if(isCancelled()) {
                    dirs.clear();
                    imgs.clear();
                }
            }

            return null;
        }

        protected void onPostExecute(Void result) {
            updateList();
        }
    }

    private void updateList() {
        SomeAdapter adapter =
                new SomeAdapter(parentActivity, android.R.layout.simple_list_item_1, names, dirsNumber);

        parentActivity.UpdateList(adapter, new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                if (showErrorItem) {
                    return;
                }

                if (i < dirsNumber) {
                    ShowDiskContent(token, paths[i]);
                } else {
                    Intent intent = new Intent(parentActivity, SlideShowActivity.class);

                    int imgPathsLength = listLength - dirsNumber;
                    String[] imgPaths = new String[imgPathsLength];
                    System.arraycopy(paths, dirsNumber, imgPaths, 0, imgPathsLength);

                    intent.putExtra(PATHS_NAME, imgPaths);
                    intent.putExtra(SIZES_NAME, sizes);
                    intent.putExtra(SELECTED_IMAGE, i - dirsNumber);
                    intent.putExtra(TOKEN, token);
                    intent.putExtra(ROTATE_FLAG, false);

                    parentActivity.startActivity(intent);
                }
            }
        }, currentDir);

        pd.dismiss();
        listLoaded = true;
    }

    private class SomeAdapter extends ArrayAdapter<String> {
        private int dirsNumber;

        public SomeAdapter(Context context, int resource, String[] objects, int dirsNumber) {
            super(context, resource, objects);
            this.dirsNumber = dirsNumber;
        }

        public View getView(int position, View convertView, ViewGroup parent) {
            TextView tv = (TextView)super.getView(position, convertView, parent);

            if(!showErrorItem) {
                if(position < dirsNumber) {
                    tv.setCompoundDrawablesWithIntrinsicBounds(R.drawable.directory, 0, 0, 0);
                } else {
                    tv.setCompoundDrawablesWithIntrinsicBounds(R.drawable.image, 0, 0, 0);
                }
            } else {
                tv.setCompoundDrawablesWithIntrinsicBounds(R.drawable.error, 0, 0, 0);
            }

            return tv;
        }
    }

    public boolean IsRoot() {
        return (currentDir.contentEquals("/"));
    }
}
