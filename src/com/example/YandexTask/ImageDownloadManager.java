package com.example.YandexTask;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.util.DisplayMetrics;
import android.util.Log;
import android.widget.Toast;
import com.yandex.disk.client.Credentials;
import com.yandex.disk.client.ProgressListener;
import com.yandex.disk.client.TransportClient;

import java.io.*;

/**
 * Created by MAX on 30.06.2014.
 */
public class ImageDownloadManager {

    private static final String IMAGE_FILENAME = "image";
    private static final String TAG = "ImageDownloadManager";

    private static int instCode = 0;

    private int imagesCount;
    private File[] files;
    private String[] paths;
    private Context appContext;
    private String token;
    private DisplayMetrics metrics;

    private SlideShowActivity parentActivity;

    private boolean firstImage;
    private boolean isWorking;
    private boolean hasWorkingTask;
    private boolean isFinishPlanned;

    private int firstUnloaded;

    public ImageDownloadManager(String[] filePaths,
                                SlideShowActivity parentActivity,
                                String token,
                                DisplayMetrics metrics) {

        imagesCount = filePaths.length;
        files = new File[imagesCount];
        this.paths = filePaths;

        this.parentActivity = parentActivity;
        this.token = token;
        this.metrics = metrics;

        ++instCode;

        appContext = parentActivity.getApplicationContext();
    }

    public void LoadFromIndex(int fileIndex) {
        firstImage = true;
        isWorking = true;
        isFinishPlanned = false;
        hasWorkingTask = true;
        firstUnloaded = -1;
        new ImageLoaderTask().execute(paths[fileIndex], String.valueOf(fileIndex));
    }

    public void BitmapsRecycle() {
        isFinishPlanned = true;

        if(!hasWorkingTask) {
            for(File file : files) {
                if(file != null) {
                    file.delete();
                }
            }
        }
    }

    public void OnConfigurationChange(SlideShowActivity parentActivity) {
        this.parentActivity = parentActivity;
    }

    public void StopDownload() {
        isWorking = false;
    }

    private class ImageLoaderTask extends AsyncTask<String, Void, Void> {
        int indexToLoad;

        protected Void doInBackground(String... params) {

            indexToLoad = Integer.decode(params[1]);

            Bitmap imageBmp = tryDownloadImage(params[0]);
            if(imageBmp != null) {
                saveImageToFile(params[1], imageBmp);
                imageBmp.recycle();
            }

            return null;
        }

        private Bitmap tryDownloadImage(String path) {
            TransportClient client = null;
            File file = null;
            Bitmap resultBmp = null;
            Bitmap srcBmp = null;

            try {
                client = TransportClient.getInstance(appContext, new Credentials("", token));

                file = File.createTempFile(IMAGE_FILENAME + String.valueOf(instCode), null, appContext.getCacheDir());
                client.downloadFile(path, file, new ProgressListener() {
                    long prev = 0;

                    @Override
                    public void updateProgress(long loaded, long total) {
                        long diff = loaded - prev;
                        prev = loaded;

                        parentActivity.UpdateProgress((int)total, (int)diff);
                    }

                    @Override
                    public boolean hasCancelled() {
                        return false;
                    }
                });

                BitmapFactory.Options options = new BitmapFactory.Options();
                srcBmp = BitmapFactory.decodeFile(file.getPath(), options);
                srcBmp.recycle();
                srcBmp = null;
                System.gc();

                options.inSampleSize = computeSampleSize(options.outWidth, options.outHeight, metrics);
                resultBmp = BitmapFactory.decodeFile(file.getPath(), options);
            } catch(OutOfMemoryError err) {
                Log.d(TAG, "OutOfMemory", err);
            } catch(IOException err) {
                Log.d(TAG, "IOException - file is too big", err);
            } catch(Exception e) {
                Log.d(TAG, "Exception in ImageLoader", e);
            } finally {
                TransportClient.shutdown(client);

                if(file != null) {
                    file.delete();
                }

                if(srcBmp != null && !srcBmp.isRecycled()) {
                    srcBmp.recycle();
                }

                System.gc();
            }

            return resultBmp;
        }

        private void saveImageToFile(String imageIndex, Bitmap resultBmp) {

            String filename = IMAGE_FILENAME + String.valueOf(instCode) + String.valueOf(imageIndex);

            boolean savedOnExternal = false;
            if(appContext.getExternalFilesDir(null) != null) {
                File file = new File(appContext.getExternalFilesDir(null), filename);
                savedOnExternal = trySaveBitmapToFile(file, resultBmp);
                if(!savedOnExternal) {
                    file.delete();
                }
            }

            if(!savedOnExternal) {
                File file = new File(appContext.getFilesDir(), filename);
                if(!trySaveBitmapToFile(file, resultBmp)) {
                    file.delete();
                }
            }
        }

        private boolean trySaveBitmapToFile(File file, Bitmap resultBmp) {
            boolean res = false;

            try {
                FileOutputStream fOut = new FileOutputStream(file);
                resultBmp.compress(Bitmap.CompressFormat.PNG, 0, fOut);
                fOut.flush();
                fOut.close();

                files[indexToLoad] = file;
                res = true;
            } catch(OutOfMemoryError err) {
                Log.d(TAG, "OutOfMemory", err);
            } catch(Exception e) {
                Log.d(TAG, "Exception on file writing", e);
            }

            return res;
        }

        protected void onPostExecute(Void result) {
            if(isFinishPlanned) {
                hasWorkingTask = false;
                BitmapsRecycle();
                return;
            }

            if(firstImage) {
                if(files[indexToLoad] != null) {
                    parentActivity.SetupBitmapShow(indexToLoad);
                    firstImage = false;
                } else {
                    parentActivity.ResetProgress();
                    if(paths[indexToLoad] != null) {
                        String imageName = paths[indexToLoad].substring(paths[indexToLoad].lastIndexOf('/') + 1,
                                paths[indexToLoad].length());
                        Toast.makeText(parentActivity, "Can't load " + imageName, Toast.LENGTH_LONG).show();
                    }
                    else {
                        Toast.makeText(parentActivity, "Image pathToCurrentDir is null", Toast.LENGTH_LONG).show();
                    }
                }
            }

            int nextToLoad = -1;
            for(int i = indexToLoad + 1; i < imagesCount && nextToLoad == -1; ++i) {
                if(files[i] == null) {
                    nextToLoad = i;
                }
            }

            if(nextToLoad == -1) {
                for(int i = 0; i < indexToLoad && nextToLoad == -1; ++i) {
                    if(files[i] == null) {
                        nextToLoad = i;
                    }
                }
            }

            if(files[indexToLoad] == null) {
                if(firstUnloaded == -1) {
                    firstUnloaded = indexToLoad;
                } else if(firstUnloaded == indexToLoad) {
                    isWorking = false;
                }
            } else if(firstUnloaded != -1) {
                firstUnloaded = -1;
            }

            if((nextToLoad == -1 || !isWorking) && firstImage) {
                parentActivity.CancelSlideShow();
            }

            if(nextToLoad != -1 && isWorking) {
                new ImageLoaderTask().execute(paths[nextToLoad], String.valueOf(nextToLoad));
            } else {
                hasWorkingTask = false;
            }
        }

        private int computeSampleSize(int outWidth, int outHeight, DisplayMetrics metrics) {
            int biggestImgSize = outWidth > outHeight ?
                    outWidth : outHeight;
            int biggestScreenSize = metrics.widthPixels > metrics.heightPixels ?
                    metrics.widthPixels : metrics.heightPixels;

            return biggestImgSize / biggestScreenSize;
        }
    }

    public Bitmap getBitmap(int index) {
        if(files[index] != null) {
            Bitmap ret = null;

            try {
                ret = BitmapFactory.decodeFile(files[index].getPath());
            } catch (Exception e) {
                Log.d(TAG, "Exception in bitmap output", e);
            }

            return ret;
        } else {
            return null;
        }
    }

    public boolean isSaved(int index) {
        return files[index] != null;
    }
}
