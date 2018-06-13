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
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.SparseArray;
import android.view.Display;

import com.google.android.gms.vision.Frame;
import com.google.android.gms.vision.text.TextBlock;
import com.google.android.gms.vision.text.TextRecognizer;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;

public class ScreenshotActivity extends Activity {

    private int REQUEST_CODE = 100;
    private MediaProjection sMediaProjection;
    private MediaProjectionManager mProjectionManager;
    private ImageReader mImageReader;
    private Handler mHandler;
    private Display mDisplay;
    private VirtualDisplay mVirtualDisplay;
    private int mDensity;
    private int mWidth;
    private int mHeight;
    private Bitmap bitmap = null;
    public static String MY_ACTION = "MY_ACTION";

    private Double[] GENERAL_QUESTION_SIZES = new Double[] {0.0,0.17,1.0,0.28};
    private Double[] CS_QUESTION_SIZES = new Double[] {0.0,0.17,1.0,0.17};
    private Double[] HANGTIME_QUESTION_SIZES = new Double[] {0.0,0.3,0.45,0.7};
    private Double[] HYPSPORTS_QUESTION_SIZES = new Double[] {0.0,0.35,1.0,0.25};
    private Double[] THEQ_QUESTION_SIZES = new Double[] {0.0,0.42,1.0,0.22};

    private Double[] GENERAL_OPTS_SIZES = new Double[] {0.0,0.4,1.0,0.5};
    private Double[] HANGTIME_OPTS_SIZES = new Double[] {0.45,0.3,0.55,0.7};
    private Double[] HYPSPORTS_OPTS_SIZES = new Double[] {0.0,0.6,1.0,0.4};
    private Double[] THEQ_OPTS_SIZES = new Double[] {0.0,0.65,1.0,0.35};

    private String game = "";
    private HashMap<String,Double[]> question_sizes = new HashMap<>();
    private HashMap<String,Double[]> opts_sizes = new HashMap<>();

    private class ImageAvailableListener implements ImageReader.OnImageAvailableListener {
        @Override
        public void onImageAvailable(ImageReader reader) {
            Image image = null;
            if (bitmap == null) {
                try {
                    image = reader.acquireLatestImage();
                    if (image != null) {
                        Image.Plane[] planes = image.getPlanes();
                        ByteBuffer buffer = planes[0].getBuffer();
                        int pixelStride = planes[0].getPixelStride();
                        int rowStride = planes[0].getRowStride();
                        int rowPadding = rowStride - pixelStride * mWidth;

                        // create bitmap
                        bitmap = Bitmap.createBitmap(mWidth + rowPadding / pixelStride, mHeight, Bitmap.Config.ARGB_8888);
                        bitmap.copyPixelsFromBuffer(buffer);

                        if (!game.equals(Constants.DEFAULT_GAME)) {
                            int image_width = bitmap.getWidth();
                            int image_height = bitmap.getHeight();
                            Double[] q_sizes = question_sizes.get(game);
                            Double[] answers_sizes = opts_sizes.get(game);
                            Bitmap question_image = Bitmap.createBitmap(bitmap, (int) (q_sizes[0] * image_width),
                                                                                (int) (q_sizes[1] * image_height),
                                                                                (int) (q_sizes[2] * image_width),
                                                                                (int) (q_sizes[3] * image_height));

                            Bitmap opts_image = Bitmap.createBitmap(bitmap, (int) (answers_sizes[0] * image_width),
                                                                            (int) (answers_sizes[1] * image_height),
                                                                            (int) (answers_sizes[2] * image_width),
                                                                            (int) (answers_sizes[3] * image_height));

                            saveBitmap(question_image,"Question.jpg");
                            saveBitmap(opts_image,"Opts.jpg");
                            readQuestionAndOptions(question_image,opts_image);
                        } else {
                            readImage(bitmap);
                        }

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
                    if (mImageReader != null) mImageReader.setOnImageAvailableListener(null, null);
                    sMediaProjection.unregisterCallback(MediaProjectionStopCallback.this);
                }
            });
        }
    }

    /****************************************** Activity Lifecycle methods ************************/
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_screenshot);

        game = getIntent().getStringExtra(Constants.GAME_TITLE);
        question_sizes.put(Constants.HQ, GENERAL_QUESTION_SIZES);
        question_sizes.put(Constants.CASH_SHOW, CS_QUESTION_SIZES);
        question_sizes.put(Constants.HANGTIME, HANGTIME_QUESTION_SIZES);
        question_sizes.put(Constants.HYPSPORTS, HYPSPORTS_QUESTION_SIZES);
        question_sizes.put(Constants.THEQ, THEQ_QUESTION_SIZES);

        opts_sizes.put(Constants.HQ, GENERAL_OPTS_SIZES);
        opts_sizes.put(Constants.CASH_SHOW, GENERAL_OPTS_SIZES);
        opts_sizes.put(Constants.HANGTIME, HANGTIME_OPTS_SIZES);
        opts_sizes.put(Constants.HYPSPORTS, HYPSPORTS_OPTS_SIZES);
        opts_sizes.put(Constants.THEQ, THEQ_OPTS_SIZES);

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
        finish();
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

    public void readImage(Bitmap bitmap){
        TextRecognizer textRecognizer = new TextRecognizer.Builder(getApplicationContext()).build();
        Frame imageFrame = new Frame.Builder()
                .setBitmap(bitmap)
                .build();

        String question = "";
        String opts = "";
        boolean parsed = false;

        SparseArray<TextBlock> textBlocks = textRecognizer.detect(imageFrame);

        int n_of_opts = 0;
        for (int i = 0; i < textBlocks.size(); i++) {
            String text = textBlocks.get(textBlocks.keyAt(i)).getValue();
            if (parsed){
                if (n_of_opts < 3 && !text.contains("prize for") && !text.contains("$")) {
                    opts += text.toLowerCase() + Constants.DELIMITER;
                    n_of_opts++;
                }
            }
            else if (text.length() > 24){
                question = text;
                parsed = true;
            }
        }

        opts = opts.replaceAll("\n",Constants.DELIMITER);
        if (opts.length() > 0)
            opts = opts.substring(0, opts.length() - 1);
        question = question.replaceAll("\n"," ");

        Intent intent = new Intent();
        intent.setAction(MY_ACTION);
        intent.putExtra(Constants.QUESTION, question);
        intent.putExtra(Constants.OPTIONS, opts);
        sendBroadcast(intent);
    }

    public void readQuestionAndOptions(Bitmap questionBitmap, Bitmap optsBitmap){
        TextRecognizer textRecognizer = new TextRecognizer.Builder(getApplicationContext()).build();
        Frame questionFrame = new Frame.Builder()
                .setBitmap(questionBitmap)
                .build();

        Frame optsFrame = new Frame.Builder()
                .setBitmap(optsBitmap)
                .build();

        String question = "";
        String opts = "";

        SparseArray<TextBlock> questionBlocks = textRecognizer.detect(questionFrame);
        SparseArray<TextBlock> optsBlocks = textRecognizer.detect(optsFrame);

        for (int i = 0; i < questionBlocks.size(); i++) {
            String text = questionBlocks.get(questionBlocks.keyAt(i)).getValue();
            question += text;
        }
        for (int i = 0; i < optsBlocks.size(); i++) {
            String text = optsBlocks.get(optsBlocks.keyAt(i)).getValue().toLowerCase();
            opts += text + Constants.DELIMITER;
        }

        if (opts.length() > 0)
            opts = opts.substring(0, opts.length() - 1);
        question = question.replaceAll("\n"," ");
        opts = opts.replaceAll("\n",Constants.DELIMITER);


        Intent intent = new Intent();
        intent.setAction(MY_ACTION);
        intent.putExtra(Constants.QUESTION, question);
        intent.putExtra(Constants.OPTIONS, opts);
        sendBroadcast(intent);
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