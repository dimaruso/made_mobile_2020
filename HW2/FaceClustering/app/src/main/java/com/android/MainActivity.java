package com.android;

import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.os.SystemClock;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;

import android.view.View;
import android.util.Log;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.android.mtcnn.Box;
import com.android.mtcnn.MTCNNModel;

import java.io.File;
import java.util.*;

public class MainActivity extends FragmentActivity {

    /** Tag for the {@link Log}. */
    private static final String TAG = "MainActivity";
    private final int REQUEST_CODE_ASK_MULTIPLE_PERMISSIONS = 124;

    private HighLevelVisualPreferences preferencesFragment;
    private Photos photosFragment;

    private ProgressBar progressBar;
    private TextView progressBarinsideText;

    public static MTCNNModel mtcnnFaceDetector=null;
    public static AgeGenderEthnicityTfLiteClassifier facialAttributeClassifier=null;
    public static final float FACES_CATEGORY_THRESHOLD = 0.09f;
    private static int MAX_IMAGE_SIZE=2000;
    private static int minFaceSize=40;

    private Thread photoProcessingThread=null;
    private Map<String,Long> photosTaken;
    private ArrayList<String> photosFilenames;
    private int currentPhotoIndex=0;
    private PhotoProcessor photoProcessor = null;

    private String[] categoryList;

    private List<Map<String,Map<String, Set<String>>>> categoriesHistograms=new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main_activity);

        if (!allPermissionsGranted()) {
            ActivityCompat.requestPermissions(this, getRequiredPermissions(), REQUEST_CODE_ASK_MULTIPLE_PERMISSIONS);
        }
        else
            init();
    }
    private void init() {
        try {
            mtcnnFaceDetector = MTCNNModel.Companion.create(getAssets());
        } catch (final Exception e) {
            Log.e(TAG, "Exception initializing MTCNNModel!"+e);
        }
        try {
            facialAttributeClassifier=new AgeGenderEthnicityTfLiteClassifier(getApplicationContext());
        } catch (final Exception e) {
            Log.e(TAG, "Exception initializing MTCNNModel!"+e);
        }

        categoryList = getResources().getStringArray(R.array.category_list);

        for(int i=0;i<categoryList.length-1;++i){
            categoriesHistograms.add(new HashMap<>());
        }

        photoProcessor = PhotoProcessor.getPhotoProcessor(this);
        photosTaken = photoProcessor.getCameraImages();
        photosFilenames=new ArrayList<String>(photosTaken.keySet());
        currentPhotoIndex=0;

        progressBar=(ProgressBar) findViewById(R.id.progress);
        progressBar.setMax(photosFilenames.size());
        progressBarinsideText=(TextView)findViewById(R.id.progressBarinsideText);
        progressBarinsideText.setText("");


        photoProcessingThread = new Thread(() -> {
         processAllPhotos();
        }, "photo-processing-thread");
        progressBar.setVisibility(View.VISIBLE);

        preferencesFragment = new HighLevelVisualPreferences();
        Bundle prefArgs = new Bundle();
        prefArgs.putInt("color", Color.GREEN);
        prefArgs.putString("title", "High-Level topCategories");
        preferencesFragment.setArguments(prefArgs);

        photosFragment=new Photos();
        Bundle args = new Bundle();
        args.putStringArray("photosTaken", new String[]{"0"});
        args.putStringArrayList("0",new ArrayList<String>(photoProcessor.getCameraImages().keySet()));
        photosFragment.setArguments(args);
        PreferencesClick(null);

        photoProcessingThread.setPriority(Thread.MIN_PRIORITY);
        photoProcessingThread.start();
    }

    class FaceFeatures {
        public FaceFeatures(float[] feat, float x, float y) {
            features=feat;
            centerX=x;
            centerY=y;
            id=0;
        }
        public void setId(int id) {
            id = id;
        }
        public String getId() {
            return "Person " + id;
        }
        public int getFaceNum(List<FaceFeatures> facesInfo){
            int bestId=-1;
            int maxId=0;
            float minErr=10000;
            float curErr=0;

            if (facesInfo!=null){
                for(int i = 0; i < facesInfo.size(); ++i) {
                    if (facesInfo.get(i).id > 0) {
                        if (maxId<facesInfo.get(i).id)
                            maxId=facesInfo.get(i).id;

                        curErr=0;

                        for(int j = 0; j < facesInfo.get(i).features.length; ++j) {
                            curErr += (features[j] - facesInfo.get(i).features[j]) * (features[j] - facesInfo.get(i).features[j]);
                        }
                        Log.i(TAG, "Cur err: " + curErr);
                        if(minErr > curErr) {
                            minErr = curErr;
                            bestId = facesInfo.get(i).id;
                        }
                    }
                }
            }
            Log.i(TAG, "Min err: " + minErr + " faceId: " + bestId);
            if ((minErr < FACES_CATEGORY_THRESHOLD) && (bestId != -1))
                id = bestId;
            else
                id = maxId + 1;
            return id;
        }
        public float[] features;
        public float centerX, centerY;
        public int id;
    }

    public synchronized List<Map<String,Map<String, Set<String>>>> getCategoriesHistograms(boolean allLogs){
        return categoriesHistograms;
    }

    private List<FaceFeatures> mtcnnDetectionAndAttributesRecognition(String filename, TfLiteClassifier classifier) {
        Log.i(TAG, "image: " + filename);
        List<FaceFeatures> facesInfo=new ArrayList<>();
        Bitmap bmp = photoProcessor.loadBitmap(filename);
        if(bmp==null)
            return facesInfo;
        int w=bmp.getWidth();
        int h=bmp.getHeight();
        if(w>=MAX_IMAGE_SIZE && h>=MAX_IMAGE_SIZE) {
            while (w >= MAX_IMAGE_SIZE && h >= MAX_IMAGE_SIZE) {
                w /= 2;
                h /= 2;
            }
            bmp = Bitmap.createScaledBitmap(bmp, w, h, true);
        }

        Bitmap resizedBitmap=bmp;
        double minSize=600.0;
        double scale=Math.min(bmp.getWidth(),bmp.getHeight())/minSize;
        if(scale>1.0) {
            resizedBitmap = Bitmap.createScaledBitmap(bmp, (int)(bmp.getWidth()/scale), (int)(bmp.getHeight()/scale), false);
            bmp=resizedBitmap;
        }
        long startTime = SystemClock.uptimeMillis();
        Vector<Box> bboxes = new Vector<Box>();
        if (mtcnnFaceDetector != null)
            bboxes = mtcnnFaceDetector.detectFaces(resizedBitmap, minFaceSize);
        Log.i(TAG, "Timecost to run mtcnn: " + Long.toString(SystemClock.uptimeMillis() - startTime));

        Bitmap tempBmp = Bitmap.createBitmap(bmp.getWidth(), bmp.getHeight(), Bitmap.Config.ARGB_8888);

        for (Box box : bboxes) {

            android.graphics.Rect bbox = new android.graphics.Rect(
                    bmp.getWidth()*box.left() / resizedBitmap.getWidth(),
                    bmp.getHeight()* box.top() / resizedBitmap.getHeight(),
                    bmp.getWidth()* box.right() / resizedBitmap.getWidth(),
                    bmp.getHeight() * box.bottom() / resizedBitmap.getHeight()
            );

            if (bbox.left < 0)
                bbox.left = 0;
            if (bbox.top < 0)
                bbox.top = 0;
            if (bbox.right < 0)
                bbox.right = 0;
            if (bbox.bottom < 0)
                bbox.bottom = 0;

            if(classifier!=null) {
                Bitmap faceBitmap = Bitmap.createBitmap(bmp, bbox.left, bbox.top, bbox.width(), bbox.height());
                Bitmap resultBitmap = Bitmap.createScaledBitmap(faceBitmap, classifier.getImageSizeX(), classifier.getImageSizeY(), false);
                FaceData res = (FaceData) classifier.classifyFrame(resultBitmap);
                facesInfo.add(new FaceFeatures(res.features, 0.5f * (box.left() + box.right()) / resizedBitmap.getWidth(), 0.5f * (box.top() + box.bottom()) / resizedBitmap.getHeight()));
            }
        }
        return facesInfo;
    }

    private void processAllPhotos() {
        List<FaceFeatures> allFacesInfo=new ArrayList<>();

        for(;currentPhotoIndex<photosTaken.size();++currentPhotoIndex){
            String filename=photosFilenames.get(currentPhotoIndex);
            try {
                File file = new File(filename);

                if (file.exists()) {
                    long startTime = SystemClock.uptimeMillis();
                    List<FaceFeatures> curFacesInfo=new ArrayList<>();
                    curFacesInfo = mtcnnDetectionAndAttributesRecognition(filename, facialAttributeClassifier);
                    for (FaceFeatures faceInfo: curFacesInfo) {
                        Log.d(TAG, "FaceId old: "+ faceInfo.id);
                    }
                    for (FaceFeatures faceInfo: curFacesInfo) {
                        faceInfo.getFaceNum(allFacesInfo);
                    }
                    for (FaceFeatures faceInfo: curFacesInfo) {
                        Log.d(TAG, "FaceId new: "+ faceInfo.id);
                    }
                    allFacesInfo.addAll(curFacesInfo);
                    long endTime = SystemClock.uptimeMillis();
                    Log.d(TAG, "!!Processed: "+ filename+" in background thread:" + Long.toString(endTime - startTime));

                    processRecognitionResults(filename, curFacesInfo);
                    final int progress=currentPhotoIndex+1;
                    runOnUiThread(() -> {
                        if(progressBar!=null) {
                            progressBar.setProgress(progress);
                            progressBarinsideText.setText(""+100*progress/photosTaken.size()+"%");
                        }
                    });
                }
            } catch (Exception e) {
                e.printStackTrace();
                Log.e(TAG, "While processing image" + filename + " exception thrown: " + e);
            }
        }
    }

    private void updateCategory(List<Map<String, Map<String, Set<String>>>> histos, int highLevelCategory, String category, String filename){
        if(highLevelCategory >= 0) {
            if (histos.size() <= highLevelCategory) {
                histos.add(new TreeMap<>());
            }
            Map<String, Map<String, Set<String>>> histo = histos.get(highLevelCategory);
            if (!histo.containsKey(category)) {
                histo.put(category, new TreeMap<>());
                histo.get(category).put("0", new TreeSet<>());
            }
            histo.get(category).get("0").add(filename);
        }
    }

    private List<Map<String,Map<String, Set<String>>>> deepCopyCategories(List<Map<String,Map<String, Set<String>>>> categories){
        ArrayList<Map<String,Map<String, Set<String>>>> result=new ArrayList<>(categories.size());
        for(Map<String,Map<String, Set<String>>> m:categories){
            Map<String,Map<String, Set<String>>> m1=new HashMap<>(m.size());
            result.add(m1);
            for(Map.Entry<String,Map<String, Set<String>>> me:m.entrySet()){
                Map<String, Set<String>> m2=new TreeMap<>(Collections.reverseOrder());
                m1.put(me.getKey(),m2);
                for(Map.Entry<String, Set<String>> map_files:me.getValue().entrySet()){
                    m2.put(map_files.getKey(),new TreeSet<>(map_files.getValue()));
                }
            }
        }
        return result;
    }
    private synchronized void processRecognitionResults(String filename, List<FaceFeatures> curFacesInfo) {
        if (curFacesInfo.isEmpty()) {
            List<Map<String, Map<String, Set<String>>>> newCategoriesHistograms = deepCopyCategories(categoriesHistograms);
            updateCategory(newCategoriesHistograms, photoProcessor.getHighLevelCategory("Without people"), "Without people", filename);
            categoriesHistograms = newCategoriesHistograms;

            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    preferencesFragment.updateChart();
                }
            });
        }
        for(FaceFeatures faceInfo: curFacesInfo) {
            String person = faceInfo.getId();
            List<Map<String, Map<String, Set<String>>>> newCategoriesHistograms = deepCopyCategories(categoriesHistograms);
            updateCategory(newCategoriesHistograms, photoProcessor.getHighLevelCategory(person), person, filename);
            categoriesHistograms = newCategoriesHistograms;

            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    preferencesFragment.updateChart();
                }
            });
        }
    }

    public void PreferencesClick(View view) {
        FragmentManager fm = getFragmentManager();
        FragmentTransaction fragmentTransaction = fm.beginTransaction();
        fragmentTransaction.replace(R.id.fragment_switch, preferencesFragment);
        fragmentTransaction.commit();
    }

    public void PhotosClick(View view) {
        FragmentManager fm = getFragmentManager();
        if(fm.getBackStackEntryCount()==0) {
            FragmentTransaction fragmentTransaction = fm.beginTransaction();
            fragmentTransaction.replace(R.id.fragment_switch, photosFragment);
            fragmentTransaction.addToBackStack(null);
            fragmentTransaction.commit();
        }
    }

    private String[] getRequiredPermissions() {
        try {
            PackageInfo info =
                   getPackageManager()
                            .getPackageInfo(getPackageName(), PackageManager.GET_PERMISSIONS);
            String[] ps = info.requestedPermissions;
            if (ps != null && ps.length > 0) {
                return ps;
            } else {
                return new String[0];
            }
        } catch (Exception e) {
            return new String[0];
        }
    }
    private boolean allPermissionsGranted() {
        for (String permission : getRequiredPermissions()) {
            int status=ContextCompat.checkSelfPermission(this,permission);
            if (ContextCompat.checkSelfPermission(this,permission)
                    != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        switch (requestCode) {
            case REQUEST_CODE_ASK_MULTIPLE_PERMISSIONS:
                Map<String, Integer> perms = new HashMap<String, Integer>();
                boolean allGranted = true;
                for (int i = 0; i < permissions.length; i++) {
                    perms.put(permissions[i], grantResults[i]);
                    if (grantResults[i] != PackageManager.PERMISSION_GRANTED)
                        allGranted = false;
                }
                // Check for ACCESS_FINE_LOCATION
                if (allGranted) {
                    // All Permissions Granted
                    init();
                } else {
                    // Permission Denied
                    Toast.makeText(MainActivity.this, "Some Permission is Denied", Toast.LENGTH_SHORT)
                            .show();
                    finish();
                }
                break;
            default:
                super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }
}
