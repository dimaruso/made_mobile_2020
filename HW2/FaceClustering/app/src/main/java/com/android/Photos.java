package com.android;


import android.app.Fragment;
import android.app.FragmentManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.AsyncTask;
import android.os.Bundle;

import android.os.SystemClock;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.android.db.ImageAnalysisResults;
import com.android.db.TopCategoriesData;
import com.android.db.RectFloat;
import com.android.mtcnn.MTCNNModel;
import com.android.mtcnn.Box;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.Vector;


public class Photos extends Fragment {
    /** Tag for the {@link Log}. */
    private static final String TAG = "PhotosFragment";

    private List<List<String>> photos;
    private int[] currentPhotoIndexes=null;
    private PhotoProcessor photoProcessor=null;
    private MTCNNModel mtcnnFaceDetector=null;
    private static int minFaceSize=40;
    private ImageView photoView;
    private TextView recResultTextView;
    private Spinner photosSpinner;
    private static final boolean ENABLE_SERVER=false;
    private float x1,x2;

    public Photos(MTCNNModel detector) {
        mtcnnFaceDetector = detector;
    }
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        return inflater.inflate(R.layout.fragment_photos, container, false);
    }

    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        photoView = (ImageView) view.findViewById(R.id.photoView);
        recResultTextView = (TextView) view.findViewById(R.id.recResultTextView);
        recResultTextView.setMovementMethod(new ScrollingMovementMethod());

        photoProcessor=PhotoProcessor.getPhotoProcessor(getActivity());
        Map<String,Long> photo2date=photoProcessor.getCameraImages();
        photosSpinner = (Spinner) view.findViewById(R.id.photos_spinner);

        String[] arraySpinner=getArguments().getStringArray("photosTaken");
        photos=new ArrayList<>();
        for(int i=0;i<arraySpinner.length;++i) {
            ArrayList<String> filenames=getArguments().getStringArrayList(arraySpinner[i]);
            filenames.sort(new Comparator<String>() {
                @Override
                public int compare(String lhs, String rhs) {
                    return photo2date.get(rhs).compareTo(photo2date.get(lhs));
                }
            });
            photos.add(filenames);
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(getActivity(),
                R.layout.spinner_item, arraySpinner);
        photosSpinner.setAdapter(adapter);
        if(arraySpinner.length!=1) {
            photosSpinner.setEnabled(true);
            photosSpinner.setVisibility(View.VISIBLE);
        }
        else{
            photosSpinner.setEnabled(false);
            photosSpinner.setVisibility(Character.isDigit(arraySpinner[0].charAt(0))?View.GONE:View.VISIBLE);
        }
        if(currentPhotoIndexes==null)
            currentPhotoIndexes=new int[arraySpinner.length];

        processImage();

        view.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        x1 = event.getX();
                        return true;
                    case MotionEvent.ACTION_UP:
                        x2 = event.getX();
                        float deltaX = x2 - x1;
                        if (Math.abs(deltaX) > 100) {
                            if (deltaX < 0) {
                                //Right to Left swipe
                                nextPhoto();
                            } else if (deltaX > 0) {
                                //Left to Right swipe
                                prevPhoto();
                            }
                            return true;
                        }
                        break;
                }

                return false;
            }
        });
        final Button prevButton=(Button)view.findViewById(R.id.prev_button);
        prevButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                prevPhoto();
            }
        });


        final Button nextButton=(Button)view.findViewById(R.id.next_button);
        nextButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                nextPhoto();
            }
        });

        final Button backButton=(Button)view.findViewById(R.id.back_button);
        backButton.setVisibility((getFragmentManager().getBackStackEntryCount()>0)?View.VISIBLE:View.GONE);
        backButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FragmentManager fm = getFragmentManager();
                if(fm.getBackStackEntryCount() > 0) {
                    Log.d(TAG, "popping backstack");
                    fm.popBackStackImmediate();
                }
            }
        });
        photosSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> parent,
                                       View itemSelected, int selectedItemPosition, long selectedId) {
                processImage();
            }

            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
    }

    private void prevPhoto(){
        int pos=photosSpinner.getSelectedItemPosition();
        if(pos<0)
            pos=0;
        if(currentPhotoIndexes.length<=pos || photos.size()<=pos)
            return;
        if(!photos.get(pos).isEmpty()) {
            if (currentPhotoIndexes[pos] > 0) {
                --currentPhotoIndexes[pos];
            }
            else
                currentPhotoIndexes[pos]=photos.get(pos).size()-1;
            processImage();
        }
    }
    private void nextPhoto(){
        int pos=photosSpinner.getSelectedItemPosition();
        if(pos<0)
            pos=0;
        if(currentPhotoIndexes.length<=pos || photos.size()<=pos)
            return;
        if(!photos.get(pos).isEmpty()) {
            if (currentPhotoIndexes[pos]<photos.get(pos).size()-1){
                ++currentPhotoIndexes[pos];
            }
            else
                currentPhotoIndexes[pos]=0;
            processImage();
        }
    }

    private static int MAX_IMAGE_SIZE=2000;

    class ProcessImageTask extends AsyncTask<Void, Void, String> {

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
        }

        @Override
        protected String doInBackground(Void... params) {
            StringBuilder text=new StringBuilder();
            int pos=photosSpinner.getSelectedItemPosition();
            if(pos<0)
                pos=0;
            if(currentPhotoIndexes.length<=pos)
                return null;
            int currentPhotoIndex=currentPhotoIndexes[pos];
            List<String> photosTaken=photos.get(pos);

            text.append("photo ").append(currentPhotoIndex+1).append(" out of ").append(photosTaken.size()).append("\n");
            if (currentPhotoIndex<photosTaken.size()) {
                String filename=photosTaken.get(currentPhotoIndex);

                try{
                    Bitmap bmp=photoProcessor.loadBitmap(filename);
                    if(bmp==null)
                        return text.toString();
                    int w=bmp.getWidth();
                    int h=bmp.getHeight();
                    if(w>=MAX_IMAGE_SIZE && h>=MAX_IMAGE_SIZE) {
                        while (w >= MAX_IMAGE_SIZE && h >= MAX_IMAGE_SIZE) {
                            w /= 2;
                            h /= 2;
                        }
                        bmp = Bitmap.createScaledBitmap(bmp, w, h, true);
                    }
                    text.append(filename).append("\n");

                    Bitmap resizedBitmap=bmp;
                    double minSize=600.0;
                    double scale=Math.min(bmp.getWidth(),bmp.getHeight())/minSize;
                    if(scale > 1.0) {
                        resizedBitmap = Bitmap.createScaledBitmap(bmp, (int)(bmp.getWidth()/scale), (int)(bmp.getHeight()/scale), false);
                        bmp=resizedBitmap;
                    }
                    long startTime = SystemClock.uptimeMillis();

                    Vector<Box> bboxes = mtcnnFaceDetector.detectFaces(resizedBitmap, minFaceSize);
                    Log.i(TAG, "Timecost to run mtcnn: " + Long.toString(SystemClock.uptimeMillis() - startTime));


                    Bitmap tempBmp = Bitmap.createBitmap(bmp.getWidth(), bmp.getHeight(), Bitmap.Config.ARGB_8888);

                    Canvas c = new Canvas(tempBmp);
                    Paint p = new Paint();
                    p.setStyle(Paint.Style.STROKE);
                    p.setAntiAlias(true);
                    p.setFilterBitmap(true);
                    p.setDither(true);
                    p.setColor(Color.BLUE);
                    p.setStrokeWidth(10);
                    c.drawBitmap(bmp, 0, 0, null);
                    for (Box box : bboxes) {

                        p.setColor(Color.RED);
                        android.graphics.Rect bbox = new android.graphics.Rect(bmp.getWidth()*box.left() / resizedBitmap.getWidth(),
                                bmp.getHeight()* box.top() / resizedBitmap.getHeight(),
                                bmp.getWidth()* box.right() / resizedBitmap.getWidth(),
                                bmp.getHeight() * box.bottom() / resizedBitmap.getHeight()
                        );

                        c.drawRect(bbox, p);

//                if(classifier!=null) {
//                    Bitmap faceBitmap = Bitmap.createBitmap(bmp, bbox.left, bbox.top, bbox.width(), bbox.height());
//                    Bitmap resultBitmap = Bitmap.createScaledBitmap(faceBitmap, classifier.getImageSizeX(), classifier.getImageSizeY(), false);
//                    ClassifierResult res = classifier.classifyFrame(resultBitmap);
//                    c.drawText(res.toString(), bbox.left, Math.max(0, bbox.top - 20), p_text);
//                    Log.i(TAG, res.toString());
//                }
                    }
//                    ImageAnalysisResults res = photoProcessor.getImageAnalysisResults(filename, bmp, text, true);
//                    //res = photoProcessor.getImageAnalysisResultsFromServer(filename, bmp);
//                    text.append(res.locations.toString()).append("\n\n");
//
//                    text.append(res.scene).append("\n");
//
//                    if(getActivity()!=null)
//                        getActivity().runOnUiThread(() -> {
//                            if(photoView!=null) {
//                                photoView.setImageBitmap(tempBmp);
//                            }
//                        });
                }
                catch(Exception e){
                    e.printStackTrace();
                    Log.e(TAG, "While  processing image "+filename+" exception thrown: " + e, e);
                }
            }
            return text.toString();
        }

        @Override
        protected void onPostExecute(String result) {
            super.onPostExecute(result);
            if(recResultTextView!=null) {
                recResultTextView.setText(result);
            }
        }
    }
    private void processImage(){
        new ProcessImageTask().execute();
    }

}
