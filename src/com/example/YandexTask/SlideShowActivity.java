package com.example.YandexTask;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;
import android.util.DisplayMetrics;
import android.view.*;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;

import java.util.Timer;
import java.util.TimerTask;

/**
 * Created by MAX on 29.06.2014.
 */
public class SlideShowActivity extends ActionBarActivity {
    private static final int DURATION = 10000;
    private static final String TAG = "SlideShowActivity";

    private static int progressValue, progressMax;
    private static ImageDownloadManager downloadManager;
    private static int currentIndex;
    private static int imagesCount;

    private ProgressDialog pd;
    private ImageView imageView;
    private Timer timer;
    private Bitmap currentBitmap;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.image_slide);

        imageView = (ImageView)findViewById(R.id.imageView);

        getSupportActionBar().hide();

        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (getIntent() != null && getIntent().getStringArrayExtra(DirectoryManager.PATHS_NAME) != null) {
            showProgressDialog();

            if(getIntent().getBooleanExtra(DirectoryManager.ROTATE_FLAG, false)) {
                downloadManager.OnConfigurationChange(this);

                if(progressValue != -1) {
                    pd.setIndeterminate(false);
                    pd.setMax(progressMax);
                    pd.setProgress(0);
                    pd.incrementProgressBy(progressValue);
                } else {
                    SetupBitmapShow(currentIndex);
                }
            } else {
                prepareImages();
            }

            getIntent().putExtra(DirectoryManager.ROTATE_FLAG, true);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if(pd.isShowing()) {
            progressValue = pd.getProgress();
            progressMax = pd.getMax();
            pd.dismiss();
        } else {
            progressValue = -1;
        }

        resetBitmapShow();
    }

    @Override
    public boolean onKeyDown ( int keyCode, KeyEvent event )
    {
        if (keyCode == KeyEvent.KEYCODE_BACK)
        {
            stopThis();
        }

        return super.onKeyDown(keyCode, event);  // This is original event operations
    }

    public void ResetProgress() {
        int value = pd.getProgress();
        pd.incrementProgressBy(-value);
    }

    public void UpdateProgress(int total, int diff) {
        if(pd.isShowing()) {
            pd.setIndeterminate(false);
            pd.setMax(total);
            pd.incrementProgressBy(diff);
        }
    }

    public void SetupBitmapShow(int index) {
        progressValue = -1;
        currentIndex = index;
        currentBitmap = downloadManager.getBitmap(currentIndex);
        imageView.setImageBitmap(currentBitmap);

        if (pd.isShowing()) {
            pd.dismiss();
        }

        timer = new Timer();
        final Context appContext = getApplicationContext();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                int nextIndex = getNextToShow();
                if(nextIndex != -1) {
                    currentIndex = nextIndex;
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            switchImage();
                        }

                        private void switchImage() {
                            Animation fadeOutAnim = AnimationUtils.loadAnimation(appContext, R.anim.abc_fade_out);
                            fadeOutAnim.setAnimationListener(new Animation.AnimationListener() {
                                @Override
                                public void onAnimationStart(Animation animation) {
                                }

                                @Override
                                public void onAnimationEnd(Animation animation) {
                                    imageView.setImageBitmap(null);
                                    currentBitmap.recycle();
                                    currentBitmap = downloadManager.getBitmap(currentIndex);
                                    imageView.setImageBitmap(currentBitmap);
                                    Animation fadeInAnim = AnimationUtils.loadAnimation(appContext, R.anim.abc_fade_in);
                                    imageView.startAnimation(fadeInAnim);
                                }

                                @Override
                                public void onAnimationRepeat(Animation animation) {
                                }
                            });
                            imageView.startAnimation(fadeOutAnim);
                        }
                    });
                }
            }

            private int getNextToShow() {
                for(int i = currentIndex + 1; i < imagesCount; ++i) {
                    if(downloadManager.isSaved(i)) {
                        return i;
                    }
                }

                for(int i = 0; i < currentIndex; ++i) {
                    if(downloadManager.isSaved(i)) {
                        return i;
                    }
                }

                return -1;
            }
        }, DURATION, DURATION);
    }

    public void CancelSlideShow() {
        stopThis();
        finish();
    }

    private void stopThis() {
        if(pd != null && pd.isShowing()) {
            pd.dismiss();
        }

        if(downloadManager != null) {
            downloadManager.StopDownload();
            downloadManager.BitmapsRecycle();
            downloadManager = null;
        }

        resetBitmapShow();
    }

    private void resetBitmapShow() {
        if(timer != null) {
            timer.cancel();
            timer.purge();
        }

        if(currentBitmap != null) {
            imageView.setImageBitmap(null);
            currentBitmap.recycle();
            currentBitmap = null;
        }
    }

    private void prepareImages() {
        DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);

        String[] paths = getIntent().getStringArrayExtra(DirectoryManager.PATHS_NAME);
        String token = getIntent().getStringExtra(DirectoryManager.TOKEN);
        currentIndex = getIntent().getIntExtra(DirectoryManager.SELECTED_IMAGE, 0);
        imagesCount = paths.length;

        if(downloadManager != null) {
            downloadManager.StopDownload();
            downloadManager.BitmapsRecycle();
        }

        downloadManager = new ImageDownloadManager(paths, this, token, metrics);
        downloadManager.LoadFromIndex(currentIndex);
    }

    private void showProgressDialog() {
        pd = new ProgressDialog(this);
        pd.setTitle("Processing");
        pd.setMessage("Loading...");
        pd.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        pd.setIndeterminate(true);
        pd.setOnCancelListener(new DialogInterface.OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialogInterface) {
                CancelSlideShow();
            }
        });
        pd.show();
    }
}