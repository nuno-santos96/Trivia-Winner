package pt.ulisboa.tecnico.cmov.triviawinner;

import android.graphics.Bitmap;
import android.util.SparseArray;

import com.google.android.gms.vision.Frame;
import com.google.android.gms.vision.text.TextBlock;
import com.google.android.gms.vision.text.TextRecognizer;

public class OCR {

    //Google Mobile Vision API
    public static String googleVision(TextRecognizer textRecognizer, Bitmap image) {
        Frame frame = new Frame.Builder()
                .setBitmap(image)
                .build();

        SparseArray<TextBlock> blocks = textRecognizer.detect(frame);

        String result = "";
        for (int i = 0; i < blocks.size(); i++) {
            result += blocks.get(blocks.keyAt(i)).getValue();
            if (i < blocks.size() - 1) result += "\n";
        }
        return result;
    }

    //Tesseract OCR
    /*public static String tesseractOCR(TessBaseAPI tessTwo, Bitmap image) {
        tessTwo.setImage(image);
        return tessTwo.getUTF8Text();
    }*/
}
