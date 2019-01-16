package com.example.tensorflowliterecognition;

import android.Manifest;
import android.annotation.TargetApi;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.FileProvider;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.example.tensorflowliterecognition.network.Urls;
import com.lzy.okgo.OkGo;
import com.lzy.okgo.callback.StringCallback;
import com.lzy.okgo.model.Response;
import com.yalantis.ucrop.UCrop;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity implements View.OnClickListener,
        NormalTextDialogFragment.OnClickCallBack {

    private static final String HANDLE_THREAD_NAME = "ClassifyBackGround";
    /**
     * An additional thread for running tasks that shouldn't block the UI.
     */
    private HandlerThread backgroundThread;

    /**
     * A {@link Handler} for running tasks in the background.
     */
    private Handler backgroundHandler;
    private final Object lock = new Object();
    private boolean runClassifier = false;

    private Button modelBtn;
    private Button albumBtn;
    private Button takePhotoBtn;
    private Button recognitionBtn;
    private TextView predictionTV;
    private TextView uploadTV;
    private ImageView imageView;
    private ImageClassifier classifier;
    private Bitmap originalBitmap;
    private static final int REQUEST_IMAGE_CAPTURE = 1;
    private static final int REQUEST_LIST_CODE = 0;
    private static final int REQUEST_CAMERA_CODE = 1;
    private Uri photoURI;
    private Uri cropURI;
    private File mCropFile;
    private static String FileProviderName = "com.example.tensorflowliterecognition.fileprovider";
    private NormalTextDialogFragment normalTextDialogFragment;
    public static final int CHOOSE_PHOTO = 3;
    public boolean startClassify = false;

    //tensorflow mobile 相关参数
    private Classifier mobileClassifier;
    private Executor executor = Executors.newSingleThreadExecutor();


    private Boolean isModelLoaded = false;
    //是否使用tensorflow mobile
    boolean isUseTensorflowLite = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initView();
        initValue();
        initEvent();
        prepareBackgroundThread();
        requestPermission();
    }

    private void requestPermission() {
//        if (!Camera2BasicFragment.allPermissionsGranted(this)) {
//            requestPermissions(Camera2BasicFragment.getRequiredPermissions
//                            (this),
//                    Camera2BasicFragment.PERMISSIONS_REQUEST_CODE);
//        }
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    private Runnable periodicClassify =
            new Runnable() {
                @Override
                public void run() {
                    synchronized (lock) {
                        if (runClassifier) {
                            classifyFrame();
                        }
                    }
//                    if (startClassify) {
//                        backgroundHandler.post(periodicClassify);
//                    }
                }
            };

    private void stopBackgroundThread() {
        backgroundThread.quitSafely();
        try {
            backgroundThread.join();
            backgroundThread = null;
            backgroundHandler = null;
            synchronized (lock) {
                runClassifier = false;
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private Bitmap scaleBitmap(Bitmap origin, int newWidth, int newHeight) {
        if (origin == null) {
            return null;
        }
        int height = origin.getHeight();
        int width = origin.getWidth();
        float scaleWidth = ((float) newWidth) / width;
        float scaleHeight = ((float) newHeight) / height;
        Matrix matrix = new Matrix();
        matrix.postScale(scaleWidth, scaleHeight);// 使用后乘
        Bitmap newBM = Bitmap.createBitmap(origin, 0, 0, width, height, matrix, false);
        return newBM;
    }

    private void classifyFrame() {
        if (classifier == null) {
            showToast("Uninitialized Classifier or invalid context.");
            return;
        }


        Bitmap bitmap = scaleBitmap(originalBitmap, classifier.getImageSizeX(), classifier
                .getImageSizeY());
//        Bitmap bitmap = scaleBitmap(originalBitmap, ImageClassifier2.DIM_IMG_SIZE_X,
//                ImageClassifier2.DIM_IMG_SIZE_Y);
        SpannableStringBuilder textToShow = new SpannableStringBuilder();
        classifier.classifyFrame(bitmap, textToShow);
        String text = predictionTV.getText().toString();
        showToast(textToShow.toString());
        //当识别结果相同时，停止继续识别
//        String newText = textToShow.toString();
//        if (text.contains("\n")) {
//            text = text.split("\n")[1];
//        }
//        if (newText.contains("\n")) {
//            newText = newText.split("\n")[1];
//        }
//        if (text.equals(newText)) {
//            startClassify=false;
//        }
    }

    private void prepareBackgroundThread() {
        backgroundThread = new HandlerThread(HANDLE_THREAD_NAME);
        backgroundThread.start();
        backgroundHandler = new Handler(backgroundThread.getLooper());
        synchronized (lock) {
            runClassifier = true;
        }
    }

    private void initEvent() {
        takePhotoBtn.setOnClickListener(this);
        recognitionBtn.setOnClickListener(this);
        uploadTV.setOnClickListener(this);
        normalTextDialogFragment.setOnClickCallBack(this);
        albumBtn.setOnClickListener(this);
        modelBtn.setOnClickListener(this);
    }

    private void initValue() {
        normalTextDialogFragment = new NormalTextDialogFragment();
        originalBitmap =
                ((BitmapDrawable) imageView.getDrawable()).getBitmap();

//        if (isUseTensorflowLite) {
        //tflite
        try {
            classifier = new ImageClassifierFloatMobilenetRetrained(this);
        } catch (IOException e) {
            e.printStackTrace();
        }
//        } else {

        //tfmobile
        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    mobileClassifier = TensorFlowImageQuantizedClassifier.create(
                            getAssets()
                    );
                    setModelLoadedFlag();
                } catch (final Exception e) {
                    throw new RuntimeException("Error initializing TensorFlow!", e);
                }
            }
        });
//        }
    }

    private void setModelLoadedFlag() {
        isModelLoaded = true;
    }

    private void initView() {
        takePhotoBtn = (Button) findViewById(R.id.bt_take_photo);
        recognitionBtn = (Button) findViewById(R.id.bt_recognition);
        predictionTV = (TextView) findViewById(R.id.tv_prediction);
        albumBtn = (Button) findViewById(R.id.btn_album);
        uploadTV = (TextView) findViewById(R.id.bt_upload);
        imageView = (ImageView) findViewById(R.id.iv);
        modelBtn = (Button) findViewById(R.id.btn_model);
    }

    @Override
    public void onClick(View v) {
        startClassify = false;
        switch (v.getId()) {

            case R.id.bt_take_photo:
                //调用拍照或者从相册选取
                dispatchTakePictureIntent();

                break;
            case R.id.bt_recognition:
                if (isUseTensorflowLite) {
                    classifyBitmap();
                } else {
                    classifyImage();
                }
                break;
            case R.id.bt_upload:
                uploadPic();
                break;
            case R.id.btn_album:
                if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission
                        .WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest
                            .permission.WRITE_EXTERNAL_STORAGE}, 1);
                } else {
                    openAlbum();
                }
                break;
            case R.id.btn_model:
                if (isUseTensorflowLite) {
                    modelBtn.setText(getString(R.string.useTensorflowMobile));
                    isUseTensorflowLite = false;
                } else {

                    modelBtn.setText(getString(R.string.useTensorflowLite));
                    isUseTensorflowLite = true;
                }
                break;
            default:
                break;
        }
    }

    private void openAlbum() {
        Intent intent = new Intent("android.intent.action.GET_CONTENT");
        intent.setType("image/*");
        startActivityForResult(intent, CHOOSE_PHOTO); // 打开相册
    }

    private void uploadPic() {

        String dir = Environment.getExternalStorageDirectory().getAbsolutePath() +
                "/tensorflowlitedemo/";
        Calendar now = new GregorianCalendar();
        SimpleDateFormat simpleDate = new SimpleDateFormat("yyyyMMddHHmmss", Locale.getDefault());
        String fileName = simpleDate.format(now.getTime());
        try {
            File dict = new File(dir);
            if (!dict.exists()) {
                dict.mkdir();
            }
            File file = new File(dir + fileName + ".jpg");
            FileOutputStream out = new FileOutputStream(file);
            originalBitmap.compress(Bitmap.CompressFormat.JPEG, 20, out);
            out.flush();
            out.close();

            OkGo.<String>post(Urls.TEST_URL).tag(this).params("pic", file)
                    .execute(new StringCallback() {


                        @Override
                        public void onSuccess(Response<String> response) {
                            showToast(response.message());
                            showToast(response.body());
                        }

                        @Override
                        public void onError(Response<String> response) {
                            if (response != null && response.message() != null) {
                            }
                            super.onError(response);
                        }
                    });
        } catch (Exception e) {
            e.printStackTrace();
        }


    }

    static final int REQUEST_TAKE_PHOTO = 1;
    static final int REQUEST_CUT_PHOTO = 2;
    File photoFile;

    private void dispatchTakePictureIntent() {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        // Ensure that there's a camera activity to handle the intent
        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
            // Create the File where the photo should go
            photoFile = null;
            try {
                photoFile = createImageFile();
            } catch (IOException ex) {
                // Error occurred while creating the File
            }
            // Continue only if the File was successfully created
            if (photoFile != null) {
                Uri photoURI = FileProvider.getUriForFile(this,
                        FileProviderName,
                        photoFile);
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI);
                startActivityForResult(takePictureIntent, REQUEST_TAKE_PHOTO);
            }
        }
    }

    private void classifyBitmap() {
        startClassify = true;
        backgroundHandler.post(periodicClassify);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopBackgroundThread();
        if (classifier != null) {
            classifier.close();
        }
    }

    private void showToast(final String text) {
        runOnUiThread(
                new Runnable() {
                    @Override
                    public void run() {
                        predictionTV.setText(text);
                    }
                });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_TAKE_PHOTO && resultCode == RESULT_OK) {

            normalTextDialogFragment.show(getFragmentManager(), "tip");


        } else if (requestCode == REQUEST_CUT_PHOTO && resultCode == RESULT_OK) {


        } else if (resultCode == RESULT_OK && requestCode == UCrop.REQUEST_CROP) {
            final Uri resultUri = UCrop.getOutput(data);
            setPic(resultUri);
        }

        switch (requestCode) {
            case CHOOSE_PHOTO:
                if (resultCode == RESULT_OK) {
                    // 判断手机系统版本号
                    if (Build.VERSION.SDK_INT >= 19) {
                        // 4.4及以上系统使用这个方法处理图片
                        handleImageOnKitKat(data);
                    } else {
                        // 4.4以下系统使用这个方法处理图片
                        handleImageBeforeKitKat(data);
                    }
                }
                break;
            default:
                break;
        }
    }

    @TargetApi(19)
    private void handleImageOnKitKat(Intent data) {
        String imagePath = null;
        Uri uri = data.getData();
        Log.d("TAG", "handleImageOnKitKat: uri is " + uri);
        if (DocumentsContract.isDocumentUri(this, uri)) {
            // 如果是document类型的Uri，则通过document id处理
            String docId = DocumentsContract.getDocumentId(uri);
            if ("com.android.providers.media.documents".equals(uri.getAuthority())) {
                String id = docId.split(":")[1]; // 解析出数字格式的id
                String selection = MediaStore.Images.Media._ID + "=" + id;
                imagePath = getImagePath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, selection);
            } else if ("com.android.providers.downloads.documents".equals(uri.getAuthority())) {
                Uri contentUri = ContentUris.withAppendedId(Uri.parse
                        ("content://downloads/public_downloads"), Long.valueOf(docId));
                imagePath = getImagePath(contentUri, null);
            }
        } else if ("content".equalsIgnoreCase(uri.getScheme())) {
            // 如果是content类型的Uri，则使用普通方式处理
            imagePath = getImagePath(uri, null);
        } else if ("file".equalsIgnoreCase(uri.getScheme())) {
            // 如果是file类型的Uri，直接获取图片路径即可
            imagePath = uri.getPath();
        }
//        displayImage(imagePath); // 根据图片路径显示图片
        setPic(uri);
    }

    private String getImagePath(Uri uri, String selection) {
        String path = null;
        // 通过Uri和selection来获取真实的图片路径
        Cursor cursor = getContentResolver().query(uri, null, selection, null, null);
        if (cursor != null) {
            if (cursor.moveToFirst()) {
                path = cursor.getString(cursor.getColumnIndex(MediaStore.Images.Media.DATA));
            }
            cursor.close();
        }
        return path;
    }

    private void handleImageBeforeKitKat(Intent data) {
        Uri uri = data.getData();
        setPic(uri);
//        String imagePath = getImagePath(uri, null);
//        displayImage(imagePath);
    }


    private void crop(Uri uri) {
        try {
            mCropFile = createImageFile();
            mCropPhotoPath = mCropFile.getAbsolutePath();
        } catch (IOException e) {
            e.printStackTrace();
        }
        String destinationFileName = "SAMPLE_CROPPED_IMAGE_NAME.jpg";
        UCrop.of(uri, Uri.fromFile(new File(getCacheDir(), destinationFileName)))
                .start(this);
    }


    String mCurrentPhotoPath;
    String mCropPhotoPath;

    private File createImageFile() throws IOException {

        // Create an image file name
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String imageFileName = "JPEG_" + timeStamp + "_";
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        File image = File.createTempFile(
                imageFileName,  /* prefix */
                ".jpg",         /* suffix */
                storageDir      /* directory */
        );

        // Save a file: path for use with ACTION_VIEW intents
        mCurrentPhotoPath = image.getAbsolutePath();
        return image;
    }

    private void setPic(Uri uri) {
        Bitmap bitmap = null;
        try {
            bitmap = MediaStore.Images.Media.getBitmap(this.getContentResolver(), uri);
            originalBitmap = bitmap;
        } catch (IOException e) {
            e.printStackTrace();
        }

        imageView.setImageURI(uri);
    }

    public Uri getImageContentUri(File imageFile) {
        String filePath = imageFile.getAbsolutePath();
        Cursor cursor = getContentResolver().query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                new String[]{MediaStore.Images.Media._ID},
                MediaStore.Images.Media.DATA + "=? ",
                new String[]{filePath}, null);

        if (cursor != null && cursor.moveToFirst()) {
            int id = cursor.getInt(cursor
                    .getColumnIndex(MediaStore.MediaColumns._ID));
            Uri baseUri = Uri.parse("content://media/external/images/media");
            return Uri.withAppendedPath(baseUri, "" + id);
        } else {
            if (imageFile.exists()) {
                ContentValues values = new ContentValues();
                values.put(MediaStore.Images.Media.DATA, filePath);
                return getContentResolver().insert(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
            } else {
                return null;
            }
        }
    }

    @Override
    public void onPositiveButton() {
        crop(FileProvider.getUriForFile(this, FileProviderName,
                photoFile));
    }

    @Override
    public void onNegativeButton() {
        setPic(FileProvider.getUriForFile(this, FileProviderName,
                photoFile));
    }

    private void classifyImage() {
        Bitmap bitmap = scaleBitmap(originalBitmap, classifier.getImageSizeX(), classifier
                .getImageSizeY());
        if (isModelLoaded == false) {
            Toast.makeText(this, "分类模型加载中...", Toast.LENGTH_SHORT).show();
            return;
        }
//        Toast.makeText(this, "处理中........", Toast.LENGTH_SHORT).show();
        long starttime = System.currentTimeMillis();
        final List<Classifier.Recognition> results = mobileClassifier.recognizeImage(bitmap);
        long endTime = System.currentTimeMillis();
        predictionTV.setText(results.toString() + "\n" + (endTime - starttime) + "ms" + "    pb");


    }
}
