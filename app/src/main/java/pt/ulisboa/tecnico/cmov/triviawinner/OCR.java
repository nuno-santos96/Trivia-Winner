package pt.ulisboa.tecnico.cmov.triviawinner;

import android.graphics.Bitmap;
import android.util.SparseArray;

import com.google.android.gms.vision.Frame;
import com.google.android.gms.vision.text.TextBlock;
import com.google.android.gms.vision.text.TextRecognizer;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class OCR {

    //Google Mobile Vision API
    public static String googleVision(TextRecognizer textRecognizer, Bitmap image) {
        Frame frame = new Frame.Builder()
                .setBitmap(image)
                .build();

        SparseArray<TextBlock> blocks = textRecognizer.detect(frame);

        Map<TextBlock,Integer> unsortedMap = new HashMap<>();
        for (int i = 0; i < blocks.size(); i++) {
            TextBlock tb = blocks.get(blocks.keyAt(i));
            unsortedMap.put(tb, tb.getCornerPoints()[0].y);
        }

        List<Map.Entry<TextBlock, Integer>> list = new LinkedList<>(unsortedMap.entrySet());

        Collections.sort(list, new Comparator<Map.Entry<TextBlock, Integer>>() {
            public int compare(Map.Entry<TextBlock, Integer> o1,
                               Map.Entry<TextBlock, Integer> o2) {
                return o1.getValue().compareTo(o2.getValue());
            }
        });

        String result = "";
        for (Map.Entry<TextBlock, Integer> entry : list)
            result += entry.getKey().getValue() + "\n";
        if (result.length() > 0) result = result.substring(0, result.length() - 1);

        return result;
    }
}
