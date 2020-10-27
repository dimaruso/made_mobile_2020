package com.processimage;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import androidx.exifinterface.media.ExifInterface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Parcelable;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.Toast;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.*;
import org.opencv.features2d.*;
import org.opencv.imgproc.Imgproc;
import org.opencv.utils.Converters;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.opencv.core.Core.DFT_SCALE;
import static org.opencv.core.CvType.CV_8U;

public class MainActivity extends AppCompatActivity {
    /** Tag for the {@link Log}. */
    private static final String TAG = "MainActivity";
    private final int REQUEST_CODE_ASK_MULTIPLE_PERMISSIONS = 124;
    private ImageView imageView;
    private Mat sampledImage=null;
    private Mat curImage=null;
    private ArrayList<org.opencv.core.Point> corners=new ArrayList<org.opencv.core.Point>();
    private Uri outputFileUri;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.my_toolbar);
        setSupportActionBar(toolbar);
        imageView=(ImageView)findViewById(R.id.inputImageView);
        imageView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent event) {
                Log.i(TAG, "event.getX(), event.getY(): " + event.getX() +" "+ event.getY());
                if(sampledImage!=null) {
                    Log.i(TAG, "sampledImage.width(), sampledImage.height(): " + sampledImage.width() +" "+ sampledImage.height());
                    Log.i(TAG, "view.getWidth(), view.getHeight(): " + view.getWidth() +" "+ view.getHeight());
                    int left=(view.getWidth()-sampledImage.width())/2;
                    int top=(view.getHeight()-sampledImage.height())/2;
                    int right=(view.getWidth()+sampledImage.width())/2;
                    int bottom=(view.getHeight()+sampledImage.height())/2;
                    Log.i(TAG, "left: " + left +" right: "+ right +" top: "+ top +" bottom:"+ bottom);
                    if(event.getX()>=left && event.getX()<=right && event.getY()>=top && event.getY()<=bottom) {
                        int projectedX = (int)event.getX()-left;
                        int projectedY = (int)event.getY()-top;
                        org.opencv.core.Point corner = new org.opencv.core.Point(projectedX, projectedY);
                        corners.add(corner);
                        if(corners.size()>4)
                            corners.remove(0);
                        Mat sampleImageCopy=sampledImage.clone();
                        for(org.opencv.core.Point c : corners)
                            Imgproc.circle(sampleImageCopy, c, (int) 5, new Scalar(0, 0, 255), 2);
                        displayImage(sampleImageCopy);
                    }
                }
                return false;
            }
        });

        if (!allPermissionsGranted()) {
            ActivityCompat.requestPermissions(this, getRequiredPermissions(), REQUEST_CODE_ASK_MULTIPLE_PERMISSIONS);
        }
        else
            init();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.toolbar_menu, menu);
        return true;
    }
    private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS:
                {
                    Log.i(TAG, "OpenCV loaded successfully");
                    System.loadLibrary("ImageProcessLib");
                    Log.i(TAG, "After loading all libraries" );
                    Toast.makeText(getApplicationContext(),
                            "Mobile Scanner loaded successfully",
                            Toast.LENGTH_SHORT).show();
                } break;
                default:
                {
                    super.onManagerConnected(status);
                    Toast.makeText(getApplicationContext(),
                            "Load error",
                            Toast.LENGTH_SHORT).show();
                } break;
            }
        }
    };
    private void init(){
    }
    @Override
    public void onResume() {
        super.onResume();
        if (!OpenCVLoader.initDebug()) {
            Log.d(TAG, "Internal OpenCV library not found. Using OpenCV Manager for initialization");
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION, this, mLoaderCallback);
        } else {
            Log.d(TAG, "OpenCV library found inside package. Using it!");
            mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
        }
    }
    @Override
    protected void onPause() {
        super.onPause();
    }
    @Override
    protected void onDestroy() {
        super.onDestroy();
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
            int status= ContextCompat.checkSelfPermission(this,permission);
            if (ContextCompat.checkSelfPermission(this,permission)
                    != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
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

    private static final int SELECT_PICTURE = 1;
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_OpenImage:
                corners.clear();
                openImageIntent();
                return true;
            case R.id.action_Refresh:
                if(isImageLoaded()) {
                    corners.clear();
                    displayImage(curImage);
                }
                return true;
            case R.id.action_transformer:
                if(isImageLoaded()) {
                    perspectiveTransform();
                }
                return true;
            case R.id.action_Rotate90:
                if(isImageLoaded()) {
                    rotate_90();
                }
                return true;
            case R.id.action_Rotate180:
                if(isImageLoaded()) {
                    rotate_180();
                }
                return true;
            case R.id.action_Rotate270:
                if(isImageLoaded()) {
                    rotate_270();
                }
                return true;
            case R.id.action_Opening:
                if(isImageLoaded()) {
                    opening();
                }
                return true;
            case R.id.action_Closing:
                if(isImageLoaded()) {
                    closing();
                }
                return true;
            case R.id.action_edgedetector:
                if(isImageLoaded()) {
                    edgedetector();
                }
                return true;
            case R.id.action_canny:
                if(isImageLoaded()) {
                    canny();
                }
                return true;
            case R.id.action_Binary:
                if(isImageLoaded()) {
                    binary();
                }
                return true;
            case R.id.action_grayscale:
                if(isImageLoaded()) {
                    grayscale();
                }
                return true;
            case R.id.action_median:
                if(isImageLoaded()) {
                    median();
                }
                return true;
            case R.id.action_bilateral:
                if(isImageLoaded()) {
                    bilateral();
                }
                return true;
            case R.id.action_Contrast:
                if(isImageLoaded()) {
                    contrast();
                }
                return true;
            case R.id.action_Gamma:
                if(isImageLoaded()) {
                    gammaCorrection();
                }
                return true;
            case R.id.action_Equalize:
                if(isImageLoaded()) {
                    equalizeHisto();
                }
                return true;
            case R.id.action_Gaussian:
                if(isImageLoaded()) {
                    blur();
                }
                return true;
            case R.id.action_FFT:
                if(isImageLoaded()) {
                    fftFilter();
                }
                return true;
            default:
                // If we got here, the user's action was not recognized.
                // Invoke the superclass to handle it.
                return super.onOptionsItemSelected(item);
        }
    }
    private boolean isImageLoaded(){
        if(sampledImage==null)
            Toast.makeText(getApplicationContext(),
                    "It is necessary to open image firstly",
                    Toast.LENGTH_SHORT).show();
        return sampledImage!=null;
    }
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK) {
            if (requestCode == SELECT_PICTURE) {
                final boolean isCamera;
                if (data == null)
                    isCamera = true;
                else {
                    final String action = data.getAction();
                    if (action == null)
                        isCamera = false;
                    else
                        isCamera = action.equals(android.provider.MediaStore.ACTION_IMAGE_CAPTURE);
                }
                Uri selectedImageUri;
                if (isCamera)
                    selectedImageUri = outputFileUri;
                else
                    selectedImageUri = data == null ? null : data.getData();
                Log.d(TAG,"selected_uri = " + selectedImageUri);
                Log.d(TAG,"camera_uri = " + outputFileUri);
                Log.d(TAG,"isCamera = " + isCamera);
                if (selectedImageUri == null && outputFileUri != null)
                    convertToMat(outputFileUri);
                else
                    convertToMat(selectedImageUri);
            }
        }
    }
    private void openImageIntent()
    {
        // Determine Uri of camera image to save.
        final File root = new File(Environment.getExternalStorageDirectory() + File.separator + "MobileScanner" + File.separator);
        root.mkdirs();
        final String fname = "tmp";
        final File sdImageMainDirectory = new File(root, fname);
        outputFileUri = Uri.fromFile(sdImageMainDirectory);
        // Camera
        final List<Intent> cameraIntents = new ArrayList<Intent>();
        final Intent captureIntent = new Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE);
        final PackageManager packageManager = getPackageManager();
        final List<ResolveInfo> listCam = packageManager.queryIntentActivities(captureIntent, 0);
        for(ResolveInfo res : listCam) {
            final String packageName = res.activityInfo.packageName;
            final Intent intent = new Intent(captureIntent);
            intent.setComponent(new ComponentName(packageName, res.activityInfo.name));
            intent.setPackage(packageName);
            //intent.putExtra("return-data", true);
            intent.putExtra(MediaStore.EXTRA_OUTPUT, outputFileUri);
            cameraIntents.add(intent);
        }
        // Filesystem
        final Intent galleryIntent = new Intent();
        galleryIntent.setType("image/*");
        galleryIntent.setAction(Intent.ACTION_GET_CONTENT); // Chooser of filesystem options.
        final Intent chooserIntent = Intent.createChooser(galleryIntent, "Choose an image from the gallery or make a new one");
        chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, cameraIntents.toArray(new Parcelable[cameraIntents.size()]));

        startActivityForResult(chooserIntent, SELECT_PICTURE);
    }
    private void convertToMat(Uri selectedImageUri) {
        try {
            InputStream ims = getContentResolver().openInputStream(selectedImageUri);
            Bitmap bmp=BitmapFactory.decodeStream(ims);
            Mat rgbImage=new Mat();
            Utils.bitmapToMat(bmp, rgbImage);
            ims.close();
            ims = getContentResolver().openInputStream(selectedImageUri);
            ExifInterface exif = new ExifInterface(ims);//selectedImageUri.getPath());
            int orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION,
                    1);
            switch (orientation)
            {
                case ExifInterface.ORIENTATION_ROTATE_90:
                    //get the mirrored image
                    rgbImage=rgbImage.t();
                    //flip on the y-axis
                    Core.flip(rgbImage, rgbImage, 1);
                    break;
                case ExifInterface.ORIENTATION_ROTATE_270:
                    //get up side down image
                    rgbImage=rgbImage.t();
                    //Flip on the x-axis
                    Core.flip(rgbImage, rgbImage, 0);
                    break;
            }

            Display display = getWindowManager().getDefaultDisplay();
            android.graphics.Point size = new android.graphics.Point();
            display.getSize(size);
            int width = size.x;
            int height = size.y;
            double downSampleRatio= calculateSubSampleSize(rgbImage,width,height);
            curImage=new Mat();
            Imgproc.resize(rgbImage, curImage, new
                    Size(),downSampleRatio,downSampleRatio,Imgproc.INTER_AREA);
            displayImage(curImage);
        } catch (Exception e) {
            Log.e(TAG, "Exception thrown: " + e+" "+Log.getStackTraceString(e));
            sampledImage=null;
        }
    }
    private static double calculateSubSampleSize(Mat srcImage, int reqWidth,
                                                 int reqHeight) {
        final int height = srcImage.height();
        final int width = srcImage.width();
        double inSampleSize = 1;
        if (height > reqHeight || width > reqWidth) {
            final double heightRatio = (double) reqHeight / (double) height;
            final double widthRatio = (double) reqWidth / (double) width;
            inSampleSize = heightRatio<widthRatio ? heightRatio :widthRatio;
        }
        return inSampleSize;
    }

    // The parameters were chosen so that the contrast change was noticeable enough.
    private void contrast() {
        Mat grayImage=new Mat();
        Mat out=new Mat();
        Mat HSV=new Mat();
        if (sampledImage.channels() != 1) {
            Imgproc.cvtColor(sampledImage, grayImage, Imgproc.COLOR_RGB2GRAY);
            Imgproc.cvtColor(sampledImage, HSV, Imgproc.COLOR_RGB2HSV);
        } else {
            Imgproc.cvtColor(sampledImage, HSV, Imgproc.COLOR_GRAY2RGB);
            Imgproc.cvtColor(HSV, grayImage, Imgproc.COLOR_RGB2GRAY);
            Imgproc.cvtColor(HSV, HSV, Imgproc.COLOR_RGB2HSV);
        }
        ArrayList<Mat> hsv_list = new ArrayList(3);
        Core.split(HSV,hsv_list);

        for(int channel=1; channel<=2; ++channel) {
            Core.MinMaxLocResult minMaxLocRes = Core.minMaxLoc(hsv_list.get(channel));
            double minVal = minMaxLocRes.minVal + 20;
            double maxVal = minMaxLocRes.maxVal - 50;
            Mat corrected = new Mat();
            hsv_list.get(channel).convertTo(corrected, CV_8U, 255.0 / (maxVal - minVal), -minVal * 255.0 / (maxVal - minVal));
            hsv_list.set(channel, corrected);
        }
        Core.merge(hsv_list,HSV);
        Imgproc.cvtColor(HSV, out, Imgproc.COLOR_HSV2RGB);


        displayImage(out);
    }
    // The parameters were chosen so that the gamma change was noticeable enough.
    private void gammaCorrection() {
        double gammaValue = 1.3;
        Mat lookUpTable = new Mat(1, 256, CV_8U);
        byte[] lookUpTableData = new byte[(int) (lookUpTable.total() * lookUpTable.channels())];
        for (int i = 0; i < lookUpTable.cols(); i++) {
            lookUpTableData[i] = saturate(Math.pow(i / 255.0, gammaValue) * 255.0);
        }
        lookUpTable.put(0, 0, lookUpTableData);

        Mat out=new Mat();

        Mat HSV=new Mat();
        if (sampledImage.channels() != 1) {
            Imgproc.cvtColor(sampledImage, HSV, Imgproc.COLOR_RGB2HSV);
        } else {
            Imgproc.cvtColor(sampledImage, HSV, Imgproc.COLOR_GRAY2RGB);
            Imgproc.cvtColor(HSV, HSV, Imgproc.COLOR_RGB2HSV);
        }
        ArrayList<Mat> hsv_list = new ArrayList(3);
        Core.split(HSV,hsv_list);

        for(int channel=1; channel<=2; ++channel) {
            Mat corrected = new Mat();
            Core.LUT(hsv_list.get(channel), lookUpTable, corrected);
            hsv_list.set(channel, corrected);
        }
        Core.merge(hsv_list,HSV);
        Imgproc.cvtColor(HSV, out, Imgproc.COLOR_HSV2RGB);

        displayImage(out);
    }
    private byte saturate(double val) {
        int iVal = (int) Math.round(val);
        iVal = iVal > 255 ? 255 : (iVal < 0 ? 0 : iVal);
        return (byte) iVal;
    }
    private void equalizeHisto() {
        Mat out=new Mat();
        Mat HSV=new Mat();
        if (sampledImage.channels() != 1) {
            Imgproc.cvtColor(sampledImage, HSV, Imgproc.COLOR_RGB2HSV);
        } else {
            Imgproc.cvtColor(sampledImage, HSV, Imgproc.COLOR_GRAY2RGB);
            Imgproc.cvtColor(HSV, HSV, Imgproc.COLOR_RGB2HSV);
        }
        ArrayList<Mat> hsv_list = new ArrayList(3);
        Core.split(HSV,hsv_list);
        for(int channel=1;channel<=2;++channel) {
            Mat equalizedValue = new Mat();
            Imgproc.equalizeHist(hsv_list.get(channel), equalizedValue);
            hsv_list.set(channel, equalizedValue);
        }
        Core.merge(hsv_list,HSV);
        Imgproc.cvtColor(HSV, out, Imgproc.COLOR_HSV2RGB);

        displayImage(out);
    }
    // Filter size selected based on the resolution of the processed photos
    private void blur() {
        Mat out=new Mat();
        Imgproc.GaussianBlur(sampledImage,out,new Size(8,8),0,0);
        displayImage(out);
    }
    private void fftFilter() {
        Mat grayImage = new Mat();
        if (sampledImage.channels() != 1) {
            Imgproc.cvtColor(sampledImage, grayImage, Imgproc.COLOR_RGB2GRAY);
        } else {
            grayImage = sampledImage.clone();
        }
        grayImage.convertTo(grayImage, CvType.CV_64FC1);

        int m = Core.getOptimalDFTSize(grayImage.rows());
        int n = Core.getOptimalDFTSize(grayImage.cols()); // on the border

        Mat padded = new Mat(new Size(n, m), CvType.CV_64FC1); // expand input

        Core.copyMakeBorder(grayImage, padded, 0, m - grayImage.rows(), 0,
                n - grayImage.cols(), Core.BORDER_CONSTANT);

        List<Mat> planes = new ArrayList<Mat>();
        planes.add(padded);
        planes.add(Mat.zeros(padded.rows(), padded.cols(), CvType.CV_64FC1));
        Mat complexI = new Mat();
        Core.merge(planes, complexI); // Add to the expanded another plane with zeros
        Mat complexI2=new Mat();
        Core.dft(complexI, complexI2); // this way the result may fit in the source matrix

        int cropSizeX=8;
        int cropSizeY=8;
        Mat mask=Mat.zeros(complexI2.size(),CV_8U);
        Mat crop = new Mat(mask, new Rect(cropSizeX, cropSizeY, complexI2.cols() - 2 * cropSizeX, complexI2.rows() - 2 * cropSizeY));
        crop.setTo(new Scalar(1));
        Mat tmp=new Mat();
        complexI2.copyTo(tmp, mask);
        complexI2=tmp;

        Mat complexII=new Mat();
        Core.idft(complexI2, complexII,DFT_SCALE);
        Core.split(complexII, planes);
        Mat out = planes.get(0);

        Core.normalize(out, out, 0, 255, Core.NORM_MINMAX);
        out.convertTo(out, CvType.CV_8UC1);
        displayImage(out);
    }
    // Filter size selected based on the resolution of the processed photos
    private void median() {
        Mat noisyImage=getNoisyImage(true);
        Mat blurredImage=new Mat();
        Imgproc.medianBlur(noisyImage,blurredImage, 8);
        displayImage(blurredImage);
    }
    // Filter size selected based on the resolution of the processed photos
    private void bilateral() {
        if (sampledImage.channels() != 1) {
            Mat noisyImage=getNoisyImage(false);
            Mat outImage=new Mat();
            Mat rgb=new Mat();
            Imgproc.cvtColor(noisyImage, rgb, Imgproc.COLOR_RGBA2RGB);
            Imgproc.bilateralFilter(rgb,outImage,8,75,75);
            displayImage(outImage);
        }
    }
    private void grayscale() {
        if (sampledImage.channels() != 1) {
            Mat grayImage=new Mat();
            Imgproc.cvtColor(sampledImage, grayImage, Imgproc.COLOR_RGB2GRAY);
            displayImage(grayImage);
        }
    }

    // Filter size selected based on the resolution of the processed photos
    private void binary() {
        Mat binImage = new Mat();
        Mat grayImage = new Mat();
        if (sampledImage.channels() != 1) {
            Imgproc.cvtColor(sampledImage, grayImage, Imgproc.COLOR_RGB2GRAY);
        } else {
            grayImage = sampledImage.clone();
        }
        Imgproc.GaussianBlur(grayImage,grayImage,new Size(8,8),0,0);
        Imgproc.threshold(grayImage,binImage,0,255,Imgproc.THRESH_BINARY+Imgproc.THRESH_OTSU);

        displayImage(binImage);
    }
    // The settings were chosen so as to highlight black text well on a white A4 page that completely fills the photo.
    private void closing() {
        Mat grayImage = new Mat();
        if (sampledImage.channels() != 1) {
            Imgproc.cvtColor(sampledImage, grayImage, Imgproc.COLOR_RGB2GRAY);
        } else {
            grayImage = sampledImage.clone();
        }
        Mat binImage=new Mat();
        Imgproc.threshold(grayImage,binImage,0,255,Imgproc.THRESH_BINARY+Imgproc.THRESH_OTSU);
        final int kernel_size=1;
        Mat kernel=Imgproc.getStructuringElement(Imgproc.CV_SHAPE_RECT,new Size(kernel_size,kernel_size));
        Mat outImage=new Mat();
        final int num_iterations=1;
        Imgproc.morphologyEx(binImage,outImage,Imgproc.MORPH_CLOSE,kernel,new Point(-1,-1),num_iterations);
        displayImage(outImage);
    }
    // The settings were chosen so as to highlight white object well on black background.
    private void opening() {
        Mat grayImage = new Mat();
        if (sampledImage.channels() != 1) {
            Imgproc.cvtColor(sampledImage, grayImage, Imgproc.COLOR_RGB2GRAY);
        } else {
            grayImage = sampledImage.clone();
        }
        Mat binImage=new Mat();
        Imgproc.threshold(grayImage,binImage,0,255,Imgproc.THRESH_BINARY+Imgproc.THRESH_OTSU);
        final int kernel_size=4;
        Mat kernel=Imgproc.getStructuringElement(Imgproc.CV_SHAPE_RECT,new Size(kernel_size,kernel_size));
        Mat outImage=new Mat();
        final int num_iterations=1;
        Imgproc.morphologyEx(binImage,outImage,Imgproc.MORPH_OPEN,kernel,new Point(-1,-1),num_iterations);
        displayImage(outImage);
    }
    // The parameters were chosen so that the number of borders was noticeable enough.
    private void edgedetector() {
        Mat grayImage=new Mat();
        if (sampledImage.channels() != 1) {
            Imgproc.cvtColor(sampledImage, grayImage, Imgproc.COLOR_RGB2GRAY);
        } else {
            grayImage = sampledImage.clone();
        }
        Mat xFirstDervative =new Mat(),yFirstDervative =new Mat();
        Imgproc.Scharr(grayImage, xFirstDervative,-1 , 1,0);
        Imgproc.Scharr(grayImage, yFirstDervative,-1 , 0,1);
        Mat absXD=new Mat(),absYD=new Mat();
        Core.convertScaleAbs(xFirstDervative, absXD);
        Core.convertScaleAbs(yFirstDervative, absYD);
        Mat edgeImage=new Mat();
        double alpha=0.5;
        Core.addWeighted(absXD, alpha, absYD, 1-alpha, 0, edgeImage);
        displayImage(edgeImage);
    }
    // The parameters were chosen so that the number of borders was noticeable enough.
    private void canny() {
        Mat grayImage=new Mat();
        if (sampledImage.channels() != 1) {
            Imgproc.cvtColor(sampledImage, grayImage, Imgproc.COLOR_RGB2GRAY);
        } else {
            grayImage = sampledImage.clone();
        }
        Mat edgeImage=new Mat();
        Imgproc.Canny(grayImage, edgeImage, 100, 200);
        displayImage(edgeImage);
    }

    private void perspectiveTransform() {
        if(corners.size()<4){
            Toast.makeText(getApplicationContext(),
                    "It is necessary to choose 4 corners",
                    Toast.LENGTH_SHORT).show();
            return;
        }
        org.opencv.core.Point centroid=new org.opencv.core.Point(0,0);
        for(org.opencv.core.Point point:corners)
        {
            centroid.x+=point.x;
            centroid.y+=point.y;
        }
        centroid.x/=corners.size();
        centroid.y/=corners.size();

        sortCorners(corners,centroid);
        Mat correctedImage=new Mat(sampledImage.rows(),sampledImage.cols(),sampledImage.type());
        Mat srcPoints= Converters.vector_Point2f_to_Mat(corners);

        Mat destPoints=Converters.vector_Point2f_to_Mat(Arrays.asList(new org.opencv.core.Point[]{
                new org.opencv.core.Point(0, 0),
                new org.opencv.core.Point(correctedImage.cols(), 0),
                new org.opencv.core.Point(correctedImage.cols(),correctedImage.rows()),
                new org.opencv.core.Point(0,correctedImage.rows())}));

        Mat transformation=Imgproc.getPerspectiveTransform(srcPoints, destPoints);
        Imgproc.warpPerspective(sampledImage, correctedImage, transformation, correctedImage.size());

        corners.clear();
        displayImage(correctedImage);
    }
    private void rotate_90() {
        Mat rotImage = new Mat();
        rotImage=sampledImage.t();
        double k = (double) sampledImage.cols() / sampledImage.rows();
        Core.flip(rotImage, rotImage, 1);
        Imgproc.resize(rotImage, rotImage,new Size(), k, k);
        displayImage(rotImage);
    }
    private void rotate_180() {
        Mat rotImage = new Mat();
        rotImage=sampledImage;
        Core.flip(rotImage, rotImage, 0);
        Core.flip(rotImage, rotImage, 1);
        displayImage(rotImage);
    }
    private void rotate_270() {
        Mat rotImage = new Mat();
        rotImage=sampledImage.t();
        double k = (double) sampledImage.cols() / sampledImage.rows();
        Core.flip(rotImage, rotImage, 0);
        Imgproc.resize(rotImage, rotImage,new Size(), k, k);
        displayImage(rotImage);
    }
    private Mat getNoisyImage(boolean add_noise) {
        Mat noisyImage;
        if(add_noise) {
            Mat noise = new Mat(sampledImage.size(), sampledImage.type());
            MatOfDouble mean = new MatOfDouble ();
            MatOfDouble dev = new MatOfDouble ();
            Core.meanStdDev(sampledImage,mean,dev);
            Core.randn(noise,0, 1*dev.get(0,0)[0]);
            noisyImage = new Mat();
            Core.add(sampledImage, noise, noisyImage);
        }
        else{
            noisyImage=sampledImage;
        }
        return noisyImage;
    }
    private void displayImage(Mat image) {
        sampledImage = image.clone();
        Bitmap bitmap = Bitmap.createBitmap(image.cols(),
                image.rows(),Bitmap.Config.RGB_565);
        Utils.matToBitmap(image, bitmap);
        displayImage(bitmap);
    }
    private void displayImage(Bitmap bitmap) {
        imageView.setImageBitmap(bitmap);
    }
    void sortCorners(ArrayList<Point> corners, org.opencv.core.Point center) {
        ArrayList<org.opencv.core.Point> top=new ArrayList<org.opencv.core.Point>();
        ArrayList<org.opencv.core.Point> bottom=new ArrayList<org.opencv.core.Point>();

        for (int i = 0; i < corners.size(); i++)
        {
            if (corners.get(i).y < center.y)
                top.add(corners.get(i));
            else
                bottom.add(corners.get(i));
        }

        double topLeft=top.get(0).x;
        int topLeftIndex=0;
        for(int i=1;i<top.size();i++)
        {
            if(top.get(i).x<topLeft)
            {
                topLeft=top.get(i).x;
                topLeftIndex=i;
            }
        }

        double topRight=0;
        int topRightIndex=0;
        for(int i=0;i<top.size();i++)
        {
            if(top.get(i).x>topRight)
            {
                topRight=top.get(i).x;
                topRightIndex=i;
            }
        }

        double bottomLeft=bottom.get(0).x;
        int bottomLeftIndex=0;
        for(int i=1;i<bottom.size();i++)
        {
            if(bottom.get(i).x<bottomLeft)
            {
                bottomLeft=bottom.get(i).x;
                bottomLeftIndex=i;
            }
        }

        double bottomRight=bottom.get(0).x;
        int bottomRightIndex=0;
        for(int i=1;i<bottom.size();i++)
        {
            if(bottom.get(i).x>bottomRight)
            {
                bottomRight=bottom.get(i).x;
                bottomRightIndex=i;
            }
        }

        org.opencv.core.Point topLeftPoint = top.get(topLeftIndex);
        org.opencv.core.Point topRightPoint = top.get(topRightIndex);
        org.opencv.core.Point bottomLeftPoint = bottom.get(bottomLeftIndex);
        org.opencv.core.Point bottomRightPoint = bottom.get(bottomRightIndex);

        corners.clear();
        corners.add(topLeftPoint);
        corners.add(topRightPoint);
        corners.add(bottomRightPoint);
        corners.add(bottomLeftPoint);
    }
}
