package pt.ulisboa.tecnico.cmov.triviawinner;

import android.annotation.SuppressLint;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.Toast;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ScannerService extends Service {

    private WindowManager mWindowManager;
    private View mChatHeadView;
    private String game = "";
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
        return START_STICKY;
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public void onCreate() {
        super.onCreate();

        //Inflate the chat head layout we created
        mChatHeadView = LayoutInflater.from(this).inflate(R.layout.layout_scanner, null);

        myReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, final Intent intent) {
                Thread thread = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try  {
                            String question = intent.getStringExtra(Constants.QUESTION);
                            String opts = intent.getStringExtra(Constants.OPTIONS);
                            googleSearch(question,opts);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                });
                thread.start();
            }
        };
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(ScreenshotActivity.MY_ACTION);
        registerReceiver(myReceiver, intentFilter);

        //Add the view to the window.
        final WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);

        //Specify the chat head position
        params.gravity = Gravity.TOP | Gravity.END;

        //Add the view to the window
        mWindowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        mWindowManager.addView(mChatHeadView, params);

        //Set the close button.
        ImageView closeButton = mChatHeadView.findViewById(R.id.close_btn);
        closeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //close the service and remove the chat head from the window
                stopSelf();
            }
        });

        //Drag and move chat head using user's touch action.
        final ImageView chatHeadImage = mChatHeadView.findViewById(R.id.chat_head_profile_iv);
        chatHeadImage.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(getBaseContext(), ScreenshotActivity.class);
                intent.putExtra(Constants.GAME_TITLE,game);
                startActivity(intent);
            }
        });


        /*chatHeadImage.setOnTouchListener(new View.OnTouchListener() {
            private int lastAction;
            private int initialX;
            private int initialY;
            private float initialTouchX;
            private float initialTouchY;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:

                        //remember the initial position.
                        initialX = params.x;
                        initialY = params.y;

                        //get the touch location
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();

                        lastAction = event.getAction();
                        return true;
                    case MotionEvent.ACTION_UP:
                        //As we implemented on touch listener with ACTION_MOVE,
                        //we have to check if the previous action was ACTION_DOWN
                        //to identify if the user clicked the view or not.
                        if (lastAction == MotionEvent.ACTION_DOWN) {
                            //Open the chat conversation click.
                            Intent intent = new Intent(getBaseContext(), ScreenshotActivity.class);
                            intent.putExtra("game",game);
                            startActivity(intent);

                            //close the service and remove the chat heads
                            //stopSelf();
                        }
                        lastAction = event.getAction();
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        //Calculate the X and Y coordinates of the view.
                        params.x = initialX + (int) (event.getRawX() - initialTouchX);
                        params.y = initialY + (int) (event.getRawY() - initialTouchY);

                        //Update the layout with new X & Y coordinate
                        mWindowManager.updateViewLayout(mChatHeadView, params);
                        lastAction = event.getAction();
                        return true;
                }
                return false;
            }
        });*/
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mChatHeadView != null) mWindowManager.removeView(mChatHeadView);
        if (myReceiver != null) unregisterReceiver(myReceiver);
    }

    public void googleSearch(String question, String opts) {
        final LinkedHashMap<String,Integer> answers = new LinkedHashMap<>();
        for (String key : opts.split(Constants.DELIMITER))
            answers.put(key, 0);
        String query = "https://www.google.com/search?q=" + question + "&num=10";
        String page = getSearchContent(query).toLowerCase();
        String toToast = "";
        for (String opt : answers.keySet()){
            String patternString = "\\W" + opt + "\\W";
            Pattern pattern = Pattern.compile(patternString);
            Matcher matcher = pattern.matcher(page);
            int count = 0;
            while (matcher.find())
                count++;
            answers.put(opt,answers.get(opt) + count);
            toToast += opt + " -> " + answers.get(opt) + "\n";
        }
        Message toast = Message.obtain();
        toast.obj = toToast;
        toast.setTarget(toastHandler);
        toast.sendToTarget();

        List<String> links = parseLinks(page);
        RequestQueue queue = Volley.newRequestQueue(getBaseContext());
        for (String link : links) {
            StringRequest stringRequest = new StringRequest(Request.Method.GET, link,
                    new Response.Listener<String>() {
                        @Override
                        public void onResponse(String response) {
                            response = response.toLowerCase();
                            String toToast = "";
                            for (String opt : answers.keySet()){
                                String patternString = "\\W" + opt + "\\W";
                                Pattern pattern = Pattern.compile(patternString);
                                Matcher matcher = pattern.matcher(response);
                                int count = 0;
                                while (matcher.find())
                                    count++;
                                answers.put(opt,answers.get(opt) + count);
                                toToast += opt + " -> " + answers.get(opt) + "\n";
                            }
                            Message toast = Message.obtain();
                            toast.obj = toToast;
                            toast.setTarget(toastHandler);
                            toast.sendToTarget();
                        }
                    }, new Response.ErrorListener() {
                @Override
                public void onErrorResponse(VolleyError error) {
                    //error.printStackTrace();
                }
            });
            // Add the request to the RequestQueue.
            queue.add(stringRequest);
        }
    }

    public String getSearchContent(String path) {
        final String agent = "Mozilla/5.0 (compatible; Googlebot/2.1; +http://www.google.com/bot.html)";
        try {
            URL url = new URL(path);
            final URLConnection connection = url.openConnection();
            connection.setRequestProperty("User-Agent", agent);
            final InputStream stream = connection.getInputStream();
            return getString(stream);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return "error";
    }

    public List<String> parseLinks(final String html) {
        List<String> result = new ArrayList<>();
        String pattern1 = "<h3 class=\"r\"><a href=\"/url?q=";
        String pattern2 = "\">";
        Pattern p = Pattern.compile(Pattern.quote(pattern1) + "(.*?)" + Pattern.quote(pattern2));
        Matcher m = p.matcher(html);

        while (m.find()) {
            String domainName = m.group(0).trim();

            /* remove the unwanted text */
            domainName = domainName.substring(domainName.indexOf("/url?q=") + 7);
            domainName = domainName.substring(0, domainName.indexOf("&amp;"));

            result.add(domainName);
        }
        return result;
    }

    public String getString(InputStream is) {
        StringBuilder sb = new StringBuilder();

        BufferedReader br = new BufferedReader(new InputStreamReader(is));
        String line;
        try {
            while ((line = br.readLine()) != null) {
                sb.append(line);
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                br.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return sb.toString();
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
}