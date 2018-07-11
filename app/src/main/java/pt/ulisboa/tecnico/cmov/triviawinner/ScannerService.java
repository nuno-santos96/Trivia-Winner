package pt.ulisboa.tecnico.cmov.triviawinner;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.Toast;

import com.android.volley.DefaultRetryPolicy;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.crashlytics.android.Crashlytics;
import com.google.android.gms.vision.text.TextRecognizer;

import java.io.FileInputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.fabric.sdk.android.Fabric;

public class ScannerService extends Service {

    private WindowManager mWindowManager;
    private View mChatHeadView;
    private String game = "";
    private double[] question_sizes;
    private double[] opts_sizes;
    //private TessBaseAPI tessTwo;
    private TextRecognizer textRecognizer;
    private BroadcastReceiver myReceiver;
    private Toast resultToast;

    public ScannerService() {
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        game = intent.getStringExtra(Constants.GAME_TITLE);
        find_sizes(game);
        String lang = intent.getStringExtra(Constants.GAME_LANG);
        //tessTwo = new TessBaseAPI();
        //tessTwo.init(Environment.getExternalStorageDirectory().toString(), lang);
        textRecognizer = new TextRecognizer.Builder(getApplicationContext()).build();
        return START_NOT_STICKY;
    }

    private void showNotification() {
        Notification notification = new NotificationCompat.Builder(this, "M_CH_ID")
                .setContentTitle("Trivia Winner")
                .setTicker("Trivia Winner")
                .setContentText("Running")
                .setSmallIcon(R.drawable.icon)
                .setOngoing(true).build();
        startForeground(101, notification);
    }

    @Override
    public void onCreate() {
        super.onCreate();

        showNotification();

        Fabric.with(this, new Crashlytics());

        //Inflate the chat head layout we created
        mChatHeadView = LayoutInflater.from(this).inflate(R.layout.layout_scanner, null);

        myReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, final Intent intent) {
                Thread thread = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            FileInputStream is = openFileInput(Constants.QUESTION);
                            FileInputStream is2 = openFileInput(Constants.OPTIONS);
                            Bitmap question = BitmapFactory.decodeStream(is);
                            Bitmap opts = BitmapFactory.decodeStream(is2);
                            is.close();
                            is2.close();
                            readQuestionAndOpts(question, opts);
                            question.recycle();
                            opts.recycle();
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                });
                thread.start();
            }
        };
        LocalBroadcastManager.getInstance(this).registerReceiver(myReceiver, new IntentFilter(ScreenshotActivity.MY_ACTION));

        final WindowManager.LayoutParams params;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            //Add the view to the window.
            params = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                    PixelFormat.TRANSLUCENT);
        } else {
            //Add the view to the window.
            params = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.TYPE_PHONE,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                    PixelFormat.TRANSLUCENT);
        }

        //Specify the chat head position
        params.gravity = Gravity.TOP | Gravity.LEFT;

        //Add the view to the window
        mWindowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        mWindowManager.addView(mChatHeadView, params);

        ImageView closeButton = mChatHeadView.findViewById(R.id.close_btn);
        closeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopForeground(true);
                stopSelf();
            }
        });

        //Drag and move chat head using user's touch action.
        final ImageView chatHeadImage = mChatHeadView.findViewById(R.id.chat_head);
        chatHeadImage.setOnTouchListener(new View.OnTouchListener() {
            private int lastAction;
            private int initialX;
            private int initialY;
            private float initialTouchX;
            private float initialTouchY;
            private int longClickDetect = 0;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = params.x;
                        initialY = params.y;

                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();

                        lastAction = event.getAction();
                        return true;
                    case MotionEvent.ACTION_UP:
                        if (lastAction == MotionEvent.ACTION_DOWN) {
                            Intent intent = new Intent(getBaseContext(), ScreenshotActivity.class);
                            intent.putExtra(Constants.GAME_TITLE,game);
                            intent.putExtra(Constants.QUESTION_SIZES, question_sizes);
                            intent.putExtra(Constants.OPTIONS_SIZES, opts_sizes);
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            startActivity(intent);
                        }
                        longClickDetect = 0;
                        lastAction = event.getAction();
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        params.x = initialX + (int) (event.getRawX() - initialTouchX);
                        params.y = initialY + (int) (event.getRawY() - initialTouchY);

                        mWindowManager.updateViewLayout(mChatHeadView, params);

                        longClickDetect++;
                        if (longClickDetect == 10) {
                            lastAction = event.getAction();
                            longClickDetect = 0;
                        }

                        return true;
                }
                return false;
            }
        });
    }

    @Override
    public void onDestroy() {
        if (myReceiver != null) LocalBroadcastManager.getInstance(this).unregisterReceiver(myReceiver);
        if (mChatHeadView != null) mWindowManager.removeView(mChatHeadView);
        //tessTwo.end();
        super.onDestroy();
    }

    public void readQuestionAndOpts(Bitmap questionBitmap, Bitmap optsBitmap) {
        String question = OCR.googleVision(textRecognizer, questionBitmap);
        String opts = OCR.googleVision(textRecognizer, optsBitmap);

        question = question.replaceAll("\n"," ");
        opts = opts.replaceAll("\n",Constants.DELIMITER);
        opts = opts.replaceAll("&",Constants.DELIMITER);
        opts = opts.toLowerCase();
        if (question.startsWith("Which of these"))
            question = question.substring(15);

        Log.e("Question", question);
        Log.e("Opts", opts);

        search(question,opts);
    }

    public void search(String question, String opts) {
        final LinkedHashMap<String,Integer> answers = new LinkedHashMap<>();
        for (String option : opts.split(Constants.DELIMITER))
            if (!option.equals(""))
                answers.put(option.trim(),0);

        //search google page
        String page = Search.google(question).toLowerCase();
        String toToast = "";
        for (String opt : answers.keySet()){
            int count = matchCount(page, opt);
            answers.put(opt, count);
            toToast += opt + " -> " + count + "\n";
        }
        sendToast(toToast);

        //search each result of google page
        List<String> links = Search.parseLinks(page);
        RequestQueue queue = Volley.newRequestQueue(getBaseContext());
        for (String link : links) {
            StringRequest stringRequest = new StringRequest(Request.Method.GET, link,
                new Response.Listener<String>() {
                    @Override
                    public void onResponse(String response) {
                        response = response.toLowerCase();
                        String toToast = "";
                        for (String opt : answers.keySet()){
                            int count = matchCount(response, opt);
                            answers.put(opt,answers.get(opt) + count);
                            toToast += opt + " -> " + answers.get(opt) + "\n";
                        }
                        sendToast(toToast);
                    }
                }, new Response.ErrorListener() {
                @Override
                public void onErrorResponse(VolleyError error) {
                    error.printStackTrace();
                }
            });
            stringRequest.setRetryPolicy(new DefaultRetryPolicy(
                    5000,
                    DefaultRetryPolicy.DEFAULT_MAX_RETRIES,
                    DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));
            // Add the request to the RequestQueue.
            queue.add(stringRequest);
        }
    }

    public int matchCount(String text, String word) {
        String patternString = "\\b" + word + "\\b";
        Pattern pattern = Pattern.compile(patternString);
        Matcher matcher = pattern.matcher(text);
        int count = 0;
        while (matcher.find())
            count++;
        return count;
    }

    public void sendToast(String text) {
        Message toast = Message.obtain();
        toast.obj = text;
        toast.setTarget(toastHandler);
        toast.sendToTarget();
    }

    Handler toastHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message toastMessage) {
            String toast = (String) toastMessage.obj;
            if(resultToast == null)
                resultToast = Toast.makeText(getBaseContext(), toast, Toast.LENGTH_LONG);
            resultToast.setText(toast);
            resultToast.setDuration(Toast.LENGTH_LONG);
            resultToast.show();
        }
    };

    public void find_sizes(String game) {
        switch (game) {
            case Constants.HQ:
                question_sizes = Constants.HQ_QUESTION_SIZES;
                opts_sizes = Constants.HQ_OPTS_SIZES;
                break;
            case Constants.CASH_SHOW:
                question_sizes = Constants.CS_QUESTION_SIZES;
                opts_sizes = Constants.CS_OPTS_SIZES;
                break;
            case Constants.HANGTIME:
                question_sizes = Constants.HANGTIME_QUESTION_SIZES;
                opts_sizes = Constants.HANGTIME_OPTS_SIZES;
                break;
            case Constants.HYPSPORTS:
                question_sizes = Constants.HYPSPORTS_QUESTION_SIZES;
                opts_sizes = Constants.HYPSPORTS_OPTS_SIZES;
                break;
            case Constants.THEQ:
                question_sizes = Constants.THEQ_QUESTION_SIZES;
                opts_sizes = Constants.THEQ_OPTS_SIZES;
                break;
            case Constants.QTWELVE:
                question_sizes = Constants.QTWELVE_QUESTION_SIZES;
                opts_sizes = Constants.QTWELVE_OPTS_SIZES;
                break;
        }
    }
}