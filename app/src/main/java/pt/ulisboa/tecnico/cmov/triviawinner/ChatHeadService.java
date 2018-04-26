package pt.ulisboa.tecnico.cmov.triviawinner;

import android.app.Service;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.PixelFormat;
import android.os.IBinder;
import android.util.Log;
import android.util.SparseArray;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.google.android.gms.vision.Frame;
import com.google.android.gms.vision.text.TextBlock;
import com.google.android.gms.vision.text.TextRecognizer;

import org.json.JSONObject;

import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;

public class ChatHeadService extends Service {

    private WindowManager mWindowManager;
    private View mChatHeadView;

    public ChatHeadService() {
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        //Inflate the chat head layout we created
        mChatHeadView = LayoutInflater.from(this).inflate(R.layout.layout_chat_head, null);

        //Add the view to the window.
        final WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);

        //Specify the chat head position
        params.gravity = Gravity.TOP | Gravity.RIGHT;

        //Add the view to the window
        mWindowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        mWindowManager.addView(mChatHeadView, params);

        //Set the close button.
        ImageView closeButton = (ImageView) mChatHeadView.findViewById(R.id.close_btn);
        closeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //close the service and remove the chat head from the window
                stopSelf();
            }
        });

        //Drag and move chat head using user's touch action.
        final ImageView chatHeadImage = (ImageView) mChatHeadView.findViewById(R.id.chat_head_profile_iv);
        chatHeadImage.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                takeScreenshot();
                readImage();
            }
        });
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mChatHeadView != null) mWindowManager.removeView(mChatHeadView);
    }

    public void takeScreenshot(){
        Intent intent = new Intent(this, ScreenshotActivity.class);
        startActivity(intent);
    }

    public void readImage(){
        new Thread(new Runnable() {
            public void run() {
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inPreferredConfig = Bitmap.Config.ARGB_8888;
                Bitmap bitmap = BitmapFactory.decodeFile(getExternalFilesDir(null).getAbsolutePath() + "/screenshots/myscreen.png", options);

                TextRecognizer textRecognizer = new TextRecognizer.Builder(getApplicationContext()).build();
                Frame imageFrame = new Frame.Builder()
                        .setBitmap(bitmap)  // your image bitmap
                        .build();

                String imageText = "";
                String question = "";
                ArrayList<String> opts = new ArrayList<>();
                boolean parsed = false;

                SparseArray<TextBlock> textBlocks = textRecognizer.detect(imageFrame);

                for (int i = 0; i < textBlocks.size(); i++) {
                    TextBlock textBlock = textBlocks.get(textBlocks.keyAt(i));
                    if (parsed){
                        opts.add(textBlock.getValue());
                    }
                    else if (textBlock.getValue().length() > 15){
                        question = textBlock.getValue();
                        parsed = true;
                    }
                    imageText += textBlock.getValue();
                }

                RequestQueue queue = Volley.newRequestQueue(getBaseContext());
                String url ="http://www.google.com/search?q=" + question;

                JsonObjectRequest jsonObjectRequest = new JsonObjectRequest
                        (Request.Method.GET, url, null, new Response.Listener<JSONObject>() {

                            @Override
                            public void onResponse(JSONObject response) {
                                Log.e("r",response.toString());
                            }
                        }, new Response.ErrorListener() {

                            @Override
                            public void onErrorResponse(VolleyError error) {
                                error.printStackTrace();

                            }
                        });
                queue.add(jsonObjectRequest);

                /*String google = "http://ajax.googleapis.com/ajax/services/search/web?v=1.0&q=";
                String search = "stackoverflow";
                String charset = "UTF-8";

                URL url = new URL(google + URLEncoder.encode(search, charset));
                Reader reader = new InputStreamReader(url.openStream(), charset);
                GoogleResults results = new Gson().fromJson(reader, GoogleResults.class);*/


                /*StringRequest stringRequest = new StringRequest(Request.Method.GET, url,
                        new Response.Listener<String>() {
                            @Override
                            public void onResponse(String response) {
                                Log.e("r",response.toString());
                                String string = response.toString().substring(15);
                                JSONObject jsonObject = null;
                                try {
                                    jsonObject = new JSONObject(string);
                                    JSONObject jsonObject_responseData = jsonObject.getJSONObject("responseData");
                                    JSONArray jsonArray_results = jsonObject_responseData.getJSONArray("results");
                                    for(int i = 0; i < jsonArray_results.length(); i++){

                                        JSONObject jsonObject_i = jsonArray_results.getJSONObject(i);

                                        String iTitle = jsonObject_i.getString("title");
                                        String iContent = jsonObject_i.getString("content");
                                        String iUrl = jsonObject_i.getString("url");

                                        Log.e("url",iUrl);
                                    }
                                } catch (JSONException e) {
                                    e.printStackTrace();
                                }

                                JSONParser jsonParser = new JSONParser();
                                final JSONObject json = jsonParser.makeHttpRequest(url_kbj + "/" + idkbj + "/", "GET", params1);
                                final JSONObject data = json.getJSONObject("data");

                            }
                        }, new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        error.printStackTrace();
                    }
                });
                queue.add(stringRequest);
                */




                /*
                Log.e("question",question);
                for (String s : opts)
                    Log.e("opt",s);
                    */

            }
        }).start();
    }
}
