package com.android;

import android.content.Context;
import android.util.Log;

import com.android.db.ClassifierResult;

import java.io.IOException;
import java.util.ArrayList;

public class EmotionTfLiteClassifier extends TfLiteClassifier{

    /** Tag for the {@link Log}. */
    private static final String TAG = "EmotionTfLite";

    private static final String MODEL_FILE = "emotions_mobilenet_quant.tflite";

    public EmotionTfLiteClassifier(final Context context) throws IOException {
        super(context, MODEL_FILE);
    }

    protected void addPixelValue(int val) {
        imgData.putFloat((val & 0xFF) - 103.939f);
        imgData.putFloat(((val >> 8) & 0xFF) - 116.779f);
        imgData.putFloat(((val >> 16) & 0xFF) - 123.68f);
    }

    protected ClassifierResult getResults(float[][][] outputs) {
        final float[] emotions_scores = outputs[0][0];

        Log.i(TAG, "features.length: " + emotions_scores.length);

        FaceData res=new FaceData(0, 0, emotions_scores, emotions_scores);
        return res;
    }
}
