package pt.ulisboa.tecnico.cmov.triviawinner;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Environment;
import android.os.Handler;
import android.os.Bundle;
import android.os.Looper;
import android.support.v4.content.LocalBroadcastManager;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;

import com.crashlytics.android.Crashlytics;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;

import io.fabric.sdk.android.Fabric;

public class ScreenshotActivity extends Activity {

    private int REQUEST_CODE = 100;
    private int IMAGES_PRODUCED = 0;
    private MediaProjection sMediaProjection;
    private MediaProjectionManager mProjectionManager;
    private ImageReader mImageReader;
    private Handler mHandler;
    private Display mDisplay;
    private VirtualDisplay mVirtualDisplay;
    private int mDensity;
    private int mWidth;
    private int mHeight;
    public static String MY_ACTION = "MY_ACTION";

    private String game;
    private double[] question_sizes;
    private double[] opts_sizes;

    private class ImageAvailableListener implements ImageReader.OnImageAvailableListener {
        @Override
        public void onImageAvailable(ImageReader reader) {
            Image image = null;
            if (IMAGES_PRODUCED == 0) {
                try {
                    image = reader.acquireLatestImage();
                    if (image != null) {
                        Image.Plane[] planes = image.getPlanes();
                        ByteBuffer buffer = planes[0].getBuffer();
                        int pixelStride = planes[0].getPixelStride();
                        int rowStride = planes[0].getRowStride();
                        int rowPadding = rowStride - pixelStride * mWidth;

                        // create bitmap
                        Bitmap bitmap = Bitmap.createBitmap(mWidth + rowPadding / pixelStride, mHeight, Bitmap.Config.ARGB_8888);
                        bitmap.copyPixelsFromBuffer(buffer);

                        int image_width = bitmap.getWidth();
                        int image_height = bitmap.getHeight();
                        if (game.equals(Constants.HQ)){
                            Bitmap hq_image = Bitmap.createBitmap(bitmap, (int) (Constants.HQ_SIZES[0] * image_width),
                                    (int) (Constants.HQ_SIZES[1] * image_height),
                                    (int) (Constants.HQ_SIZES[2] * image_width),
                                    (int) (Constants.HQ_SIZES[3] * image_height));

                            saveBitmap(hq_image, "HQ.jpg");

                            try {
                                FileOutputStream stream = getBaseContext().openFileOutput(Constants.HQ, Context.MODE_PRIVATE);
                                hq_image.compress(Bitmap.CompressFormat.PNG, 100, stream);
                                stream.close();
                            } catch (Exception e) {
                                e.printStackTrace();
                            }

                            hq_image.recycle();
                        } else {
                            Bitmap question_image = Bitmap.createBitmap(bitmap, (int) (question_sizes[0] * image_width),
                                    (int) (question_sizes[1] * image_height),
                                    (int) (question_sizes[2] * image_width),
                                    (int) (question_sizes[3] * image_height));

                            Bitmap opts_image = Bitmap.createBitmap(bitmap, (int) (opts_sizes[0] * image_width),
                                    (int) (opts_sizes[1] * image_height),
                                    (int) (opts_sizes[2] * image_width),
                                    (int) (opts_sizes[3] * image_height));

                            saveBitmap(question_image, "Question.jpg");
                            saveBitmap(opts_image, "Opts.jpg");

                            try {
                                FileOutputStream stream = getBaseContext().openFileOutput(Constants.QUESTION, Context.MODE_PRIVATE);
                                question_image.compress(Bitmap.CompressFormat.PNG, 100, stream);
                                FileOutputStream stream2 = getBaseContext().openFileOutput(Constants.OPTIONS, Context.MODE_PRIVATE);
                                opts_image.compress(Bitmap.CompressFormat.PNG, 100, stream2);

                                stream.close();
                                stream2.close();
                            } catch (Exception e) {
                                e.printStackTrace();
                            }

                            question_image.recycle();
                            opts_image.recycle();
                        }

                        bitmap.recycle();

                        Intent intent = new Intent(MY_ACTION);
                        LocalBroadcastManager.getInstance(getBaseContext()).sendBroadcast(intent);

                        IMAGES_PRODUCED++;
                        stopProjection();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    if (image != null)
                        image.close();
                }
            }
        }
    }


    private class MediaProjectionStopCallback extends MediaProjection.Callback {
        @Override
        public void onStop() {
            Log.e("ScreenCapture", "stopping projection.");
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    if (mVirtualDisplay != null) mVirtualDisplay.release();
                    if (mImageReader != null){
                        mImageReader.setOnImageAvailableListener(null, null);
                        mImageReader.close();
                    }
                    sMediaProjection.unregisterCallback(MediaProjectionStopCallback.this);
                }
            });
            finish();
        }
    }

    /****************************************** Activity Lifecycle methods ************************/
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Fabric.with(this, new Crashlytics());
        setContentView(R.layout.activity_screenshot);

        game = getIntent().getStringExtra(Constants.GAME_TITLE);
        question_sizes = getIntent().getDoubleArrayExtra(Constants.QUESTION_SIZES);
        opts_sizes = getIntent().getDoubleArrayExtra(Constants.OPTIONS_SIZES);

        // call for the projection manager
        mProjectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);

        startProjection();

        // start capture handling thread
        new Thread() {
            @Override
            public void run() {
                Looper.prepare();
                mHandler = new Handler();
                Looper.loop();
            }
        }.start();
    }



    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {

        if (requestCode == REQUEST_CODE) {
            sMediaProjection = mProjectionManager.getMediaProjection(resultCode, data);

            if (sMediaProjection != null) {
                // display metrics
                DisplayMetrics metrics = getResources().getDisplayMetrics();
                mDensity = metrics.densityDpi;
                mDisplay = getWindowManager().getDefaultDisplay();

                // create virtual display depending on device width / height
                createVirtualDisplay();

                // register media projection stop callback
                sMediaProjection.registerCallback(new MediaProjectionStopCallback(), mHandler);
            }
        }
    }

    /****************************************** UI Widget Callbacks *******************************/
    private void startProjection() {
        startActivityForResult(mProjectionManager.createScreenCaptureIntent(), REQUEST_CODE);
    }

    private void stopProjection() {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                if (sMediaProjection != null) {
                    sMediaProjection.stop();
                }
            }
        });
    }

    /****************************************** Factoring Virtual Display creation ****************/
    private void createVirtualDisplay() {
        String screencap_name = "screencap";
        int VIRTUAL_DISPLAY_FLAGS = DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY | DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC;
        // get width and height
        Point size = new Point();
        mDisplay.getSize(size);
        mWidth = size.x;
        mHeight = size.y;

        // start capture reader
        mImageReader = ImageReader.newInstance(mWidth, mHeight, PixelFormat.RGBA_8888, 1);
        mVirtualDisplay = sMediaProjection.createVirtualDisplay(screencap_name, mWidth, mHeight, mDensity, VIRTUAL_DISPLAY_FLAGS, mImageReader.getSurface(), null, mHandler);
        mImageReader.setOnImageAvailableListener(new ImageAvailableListener(), mHandler);
    }

    public void saveBitmap(Bitmap b, String filename) {
        String root = Environment.getExternalStorageDirectory().toString();
        File myDir = new File(root + "/Captures");
        myDir.mkdirs();
        File file = new File(myDir, filename);
        if (file.exists())
            file.delete();
        try {
            FileOutputStream out = new FileOutputStream(file);
            b.compress(Bitmap.CompressFormat.PNG, 100, out);
            out.flush();
            out.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}