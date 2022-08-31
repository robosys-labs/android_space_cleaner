package com.robosyslive.spacecleaner;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.design.widget.BottomNavigationView;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.util.LongSparseArray;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.reactivestreams.Publisher;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import io.reactivex.Flowable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.functions.Consumer;
import io.reactivex.functions.Function;
import io.reactivex.schedulers.Schedulers;

import static android.Manifest.permission.READ_EXTERNAL_STORAGE;

public class MainActivity extends AppCompatActivity {

    private List<StorageHelper.StorageVolume> storages;
    private ArrayList<String> errorList = new ArrayList<String>();
    private ArrayList<File> pictures = new ArrayList<File>();
    private ArrayList<File> videos = new ArrayList<File>();
    private ArrayList<File> music = new ArrayList<File>();
    private ArrayList<File> documents = new ArrayList<File>();
    private File currentDirectory;
    private Button btnPictures;
    private Button btnVideos;
    private Button btnMusic;
    private Button btnDocuments;
    private Button btnExit;
    private TextView progressText;
    private RelativeLayout progressLayout;
    public int defaultIgnoreSize = 200000;//200kb duplicates ignore
    private SharedPreferences SP;
    private boolean inProgress;
    private FileCheckAsyncTask fcheck;
    //private ViewFlipper flipper;

    private BottomNavigationView.OnNavigationItemSelectedListener mOnNavigationItemSelectedListener
            = new BottomNavigationView.OnNavigationItemSelectedListener() {

        @Override
        public boolean onNavigationItemSelected(@NonNull MenuItem item) {
            switch (item.getItemId()) {
                case R.id.navigation_internal:
                    if(!currentDirectory.getAbsolutePath().equals(storages.get(0).file.getAbsolutePath())) {
                        currentDirectory = storages.get(0).file;
                        fcheck = new FileCheckAsyncTask(getApplicationContext());
                        fcheck.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
                    }
                    return true;
                case R.id.navigation_external:
                    if(storages.size() == 1){
                        Toast.makeText(MainActivity.this, "No external memory card found", Toast.LENGTH_SHORT).show();
                    } else if(storages.size() > 1) {
                        if(!currentDirectory.getAbsolutePath().equals(storages.get(1).file.getAbsolutePath())) {
                            currentDirectory = storages.get(1).file;
                            fcheck = new FileCheckAsyncTask(getApplicationContext());
                            fcheck.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
                        }
                    }
                    return true;
                case R.id.navigation_settings:
                    Intent i = new Intent(MainActivity.this, SettingsActivity.class);
                    startActivity(i);
                    return true;
            }
            return false;
        }

    };

    public final String[] EXTERNAL_PERMS = {Manifest.permission.WRITE_EXTERNAL_STORAGE, READ_EXTERNAL_STORAGE
    };

    public final int EXTERNAL_REQUEST = 138;

    @Override
    protected void onResume()
    {
        super.onResume();

        requestForPermission();
    }
    @TargetApi(Build.VERSION_CODES.M)
    public boolean requestForPermission() {

        boolean isPermissionOn = true;
        final int version = Build.VERSION.SDK_INT;
        if (version >= 23) {
            if (!canAccessExternalSd()) {
                isPermissionOn = false;
                ActivityCompat.requestPermissions(MainActivity.this, EXTERNAL_PERMS, EXTERNAL_REQUEST);
            }
        }

        return isPermissionOn;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case EXTERNAL_REQUEST:
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // permission was granted, proceed to the normal flow.
                    fcheck = new FileCheckAsyncTask(getApplicationContext());
                    fcheck.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
                } else {
                    Toast.makeText(getApplicationContext(), "Space cleaner does not have access to files. Kindly grant access and try again.", Toast.LENGTH_LONG).show();
                }
        }
    }

    public boolean canAccessExternalSd() {
        return (hasPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE));
    }

    private boolean hasPermission(String perm) {
        return (PackageManager.PERMISSION_GRANTED == ContextCompat.checkSelfPermission(this, perm));

    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main_activity);
        requestForPermission();
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayShowHomeEnabled(true);
            actionBar.setIcon(R.drawable.ic_stat_memoryknife);
            actionBar.setTitle(" " + getResources().getString(R.string.app_name));
        }

        SharedPreferences SP = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());

        String intStore = System.getenv("EXTERNAL_STORAGE");
        storages = StorageHelper.getStorages(false);
        currentDirectory = storages.get(0).file;
        defaultIgnoreSize = Integer.parseInt(SP.getString("deault_ignore_size", "200000"));

        btnDocuments = (Button) findViewById(R.id.btn_documents);
        btnPictures = (Button) findViewById(R.id.btn_pictures);
        btnVideos = (Button) findViewById(R.id.btn_videos);
        btnMusic = (Button) findViewById(R.id.btn_music);
        btnExit = (Button) findViewById(R.id.btn_exit);
        progressLayout = (RelativeLayout) findViewById(R.id.progress_layout);
        progressText = (TextView) findViewById(R.id.progress_text);


        btnVideos.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(TaskInProgress()) return;
                CheckDuplicateFiles(videos, SELECT_MODE_RECENT, defaultIgnoreSize, true);
//                progressLayout.setVisibility(View.VISIBLE);
//                totalProcessed = 0;
//                newLength = 0L;
//                previousLength = 0L;
//                for(File f: videos)
//                {
//                    //compress and output new video specs
//                    VideoAsyncTask task = new VideoAsyncTask(MainActivity.this);
//                    startMyTask(task, f.getAbsolutePath());
//                }
            }
        });

        btnPictures.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(TaskInProgress()) return;
                CheckDuplicateFiles(pictures, SELECT_MODE_RECENT, defaultIgnoreSize, false);
                CompressPictures();
            }
        });

        btnMusic.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(TaskInProgress()) return;
                CheckDuplicateFiles(music, SELECT_MODE_RECENT, defaultIgnoreSize, true);
            }
        });

        btnDocuments.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(TaskInProgress()) return;
                CheckDuplicateFiles(documents, SELECT_MODE_RECENT, defaultIgnoreSize, true);
            }
        });

        btnExit.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                moveTaskToBack(true);
                android.os.Process.killProcess(android.os.Process.myPid());
                System.exit(1);
            }
        });

        BottomNavigationView navigation = (BottomNavigationView) findViewById(R.id.navigation);
        navigation.setOnNavigationItemSelectedListener(mOnNavigationItemSelectedListener);

        fcheck = new FileCheckAsyncTask(getApplicationContext());
        fcheck.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    private boolean TaskInProgress() {
        if(inProgress){
            Toast.makeText(getApplicationContext(), "Task in progress. Please wait...", Toast.LENGTH_LONG).show();
        }
        return inProgress;
    }

    private SharedPreferences GetPreferences()
    {
        if(SP == null){
            SP = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        }
        return SP;
    }

    public void DoRating()
    {
        Rate rate = new Rate.Builder(MainActivity.this)
                // Trigger dialog after this many events (optional, defaults to 6)
                .setTriggerCount(10)
                // After dismissal, trigger again after this many events (optional, defaults to 30)
                .setRepeatCount(10)
                .setMinimumInstallTime((int) TimeUnit.DAYS.toMillis(6))   // Optional, defaults to 7 days
                .setFeedbackAction(new OnFeedbackListener() {       // Optional
                    @Override
                    public void onFeedbackTapped() {
                        Intent Email = new Intent(Intent.ACTION_SEND);
                        Email.setType("text/email");
                        String body = "Hi," + "\n";
                        try {
                            body += getApplicationContext().getPackageManager().getPackageInfo(getApplicationContext().getPackageName(), 0).versionName;
                            body += "\n\n-----------------------------\nPlease don't remove this information\n Device OS: Android \n Device OS version: " +
                                    Build.VERSION.RELEASE + "\n App Version: " + body + "\n Device Brand: " + Build.BRAND +
                                    "\n Device Model: " + Build.MODEL + "\n Device Manufacturer: " + Build.MANUFACTURER;
                        } catch (PackageManager.NameNotFoundException e){

                        }
                        Email.putExtra(Intent.EXTRA_EMAIL, new String[] { "info@robosyslive.com" });
                        Email.putExtra(Intent.EXTRA_SUBJECT, "Space Cleaner Feedback");
                        Email.putExtra(Intent.EXTRA_TEXT, body);
                        startActivity(Intent.createChooser(Email, "Send Feedback:"));
                        Toast.makeText(MainActivity.this, "Thank you for your feedback", Toast.LENGTH_LONG).show();
                    }

                    @Override
                    public void onRateTapped() {

                    }

                    @Override
                    public void onRequestDismissed(boolean dontAskAgain) {
                        // User has dismissed the request
                    }
                })
                .setMessage(R.string.please_rate)                // Optional
                .setPositiveButton("Sure!")                         // Optional
                .setCancelButton("Maybe later")                     // Optional
                .setNegativeButton("Nope!")                         // Optional
                .build();
        rate.showRequest();
    }

    private Long newLength = 0L;
    private int totalProcessed = 0;
    private Long previousLength = 0L;

    @TargetApi(Build.VERSION_CODES.HONEYCOMB) // API 11
    void startMyTask(AsyncTask asyncTask, String... params) {
        totalProcessed += 1;
        File oldFile = new File(params[0]);
        progressText.setText("Compressing " + oldFile.getName() + "(" + formatSize(oldFile.length()) + ")...");

        asyncTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, params);
    }

    public void UpdateVideoProgress(String originalFile, String compressedFile)
    {
        File newFile = new File(compressedFile);
        File oldFile = new File(originalFile);
        previousLength += oldFile.length();
        newLength += newFile.length();
        progressText.setText("Compressed " + oldFile.getName() + "(" + formatSize(newFile.length()) + ")...");
        CheckFilesComplete(videos);
    }

    public void CompressPictures() {
        DoRating();
        inProgress = true;

        boolean compress = GetPreferences().getBoolean("compress_pictures", true);
        if(!compress){
            CleanupFinished();
            Toast.makeText(getApplicationContext(), R.string.optimization_turned_off, Toast.LENGTH_LONG).show();
            return;
        }

        progressLayout.setVisibility(View.VISIBLE);
        final String resize = GetPreferences().getString("resized_name", "_resized");

        Flowable.fromIterable(pictures)
                .flatMap(new Function<File, Publisher<File>>() {
                    @Override
                    public Publisher<File> apply(@NonNull File file) throws Exception {
                        previousLength += file.length();
                        return new Compressor(getApplicationContext(), file).compressToFileAsFlowable(file);
                    }
                }).subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Consumer<File>() {
                    @Override
                    public void accept(File file) {
                        totalProcessed += 1;
                        File newFile = new File(ImageUtil.GetNewFilename(file, resize));
                        if(newFile.length() == 0){
                            //no resize was performed
                            newFile = file;
                        }
                        newLength += newFile.length();
                        progressText.setText("Compressed " + file.getName() + "(" + formatSize(newFile.length()) + ")...");
                        CheckFilesComplete(pictures);
                    }
                }, new Consumer<Throwable>() {
                    @Override
                    public void accept(Throwable throwable) {
                        totalProcessed = pictures.size();
                        CheckFilesComplete(pictures);
                        Toast.makeText(getApplicationContext(), R.string.optimization_error, Toast.LENGTH_LONG).show();
                        //throwable.printStackTrace();
                    }
                });
    }

    private void CheckFilesComplete(ArrayList<File> files)
    {
        if(totalProcessed == files.size())
        {
            //we are done

            CleanupFinished();

            progressLayout.setVisibility(View.GONE);
            Long totalSaved = previousLength - newLength;
            if(totalSaved < 0){
                totalSaved = 0L;
            }
            if(totalSaved == 0)
            {
                Toast.makeText(getApplicationContext(), R.string.memory_utilized_efficiently, Toast.LENGTH_LONG).show();
            } else{
                Toast.makeText(getApplicationContext(), "Optimization complete. You saved " + formatSize(totalSaved), Toast.LENGTH_LONG).show();
            }

            //reset variables
            totalProcessed = 0;
            newLength = 0L;
            previousLength = 0L;
        }
    }

    private String formatSize(Long size)
    {
        if(size > (1024 * 1024 * 1024))
        {
            return size / (1024 * 1024 * 1024) + "GB";
        }
        else if(size > (1024 * 1024))
        {
            return  size / (1024 * 1024) + "MB";
        }
        else if(size > 1024)
        {
            return  size / 1024 + "KB";
        }
        return size + "B";
    }

    private void CleanupFinished() {
        inProgress = false;
        boolean vibrate = GetPreferences().getBoolean("vibrate", true);
        if(!vibrate) return;

        if (Build.VERSION.SDK_INT >= 26) {
            ((Vibrator) getSystemService(VIBRATOR_SERVICE)).vibrate(VibrationEffect.createOneShot(150, VibrationEffect.DEFAULT_AMPLITUDE));
        } else {
            ((Vibrator) getSystemService(VIBRATOR_SERVICE)).vibrate(150);
        }
    }

    protected Long calculateFilesSize(ArrayList<File> files)
    {
        long total = 0;
        for(File f: files){
            total += f.length();
        }
        return  total;
    }

    protected Long calculateFolderSize(File d)
    {
        ArrayList<File> array = new ArrayList<File>();
        recursiveScan(array, d);
        return calculateFilesSize(array);
    }

    protected Long calculateFolderSize(File d, String ext)
    {
        ArrayList<File> array = new ArrayList<File>();
        recursiveScan(array, d, ext);
        return calculateFilesSize(array);
    }


    protected Long calculateFolderSize(File d, String[] exts)
    {
        ArrayList<File> array = new ArrayList<File>();
        recursiveScan(array, d, exts);
        return  calculateFilesSize(array);
    }
    protected ArrayList<File> getFiles(File dir)
    {
        ArrayList<File> array = new ArrayList<File>();
        recursiveScan(array, dir);
        return  array;
    }

    public void recursiveScan(ArrayList<File> arr, File d) {
        File[] files = d.listFiles();
        if(files == null) return;
        for (File f : files){
            if (f.isDirectory()) recursiveScan(arr, f);
            if (f.isFile()) {
                //Add to list
                arr.add(f);
            }
        }
    }

    public void recursiveScan(ArrayList<File> arr, File d, String ext) {
        File[] file = d.listFiles();
        if(file == null) return;
        for (File f : file) {
            if (f.isDirectory()) recursiveScan(arr, f, ext);
            if (f.isFile() && f.getAbsolutePath().toLowerCase().endsWith(ext.toLowerCase())) {
                //Add to list
                arr.add(f);
            }
        }
    }

    public void recursiveScan(ArrayList<File> arr, File d, String[] exts) {
        File[] file = d.listFiles();
        if(file == null) return;
        for (File f : file) {
            if (f.isDirectory()) recursiveScan(arr, f, exts);
            for(String e: exts) {
                if (f.isFile() && f.getAbsolutePath().toLowerCase().endsWith(e.toLowerCase())) {
                    //Add to list
                    arr.add(f);
                    continue;
                }
            }
        }
    }

    public static final int SELECT_MODE_RECENT = 1;
    public static final int SELECT_MODE_FOLDER = 2;

    public ArrayList<File> CheckDuplicateFiles(ArrayList<File> files, int selectMode, int ignoreSize, boolean complete)
    {
        inProgress = true;

        boolean dupdelete = GetPreferences().getBoolean("delete_duplicates", true);
        if(!dupdelete && complete){
            CleanupFinished();
            Toast.makeText(getApplicationContext(), R.string.duplicates_turned_off, Toast.LENGTH_LONG).show();
            return null;
        }

        progressLayout.setVisibility(View.VISIBLE);
        progressText.setText(R.string.checking_for_duplicates);

        ArrayList<File> duplicates = new ArrayList<File>();
        LongSparseArray<File> sizes = new LongSparseArray<>();
        for(File f: files){
            if(f.length() < ignoreSize) continue;
            if(sizes.indexOfKey(f.length()) > -1) {
                duplicates.add(f);
            }
            else {
                sizes.put(f.length(), f);
            }
        }

        progressText.setText("Found " + duplicates.size() + " potential duplicates... Double-checking...");
        long dupSize = 0L;

        ArrayList<File> result = new ArrayList<>(duplicates.size());
        for(File f : duplicates){
            File mainFile = sizes.get(f.length());

            if(GetMd5OfFile(mainFile.getAbsolutePath()).equals(
                    GetMd5OfFile(f.getAbsolutePath()))){
                //todo: consider selectMode before select of the final file to add
//                switch (selectMode){
//                    case SELECT_MODE_RECENT:
//                        break;
//                    case SELECT_MODE_FOLDER:
//                        break;
//                }
                result.add(f);
                dupSize += f.length();
                progressText.setText("Deleting duplicate " + f.getName() + "(" +formatSize(f.length()) + ")");
                f.delete();
            }
        }

        progressLayout.setVisibility(View.GONE);

        if(dupSize == 0L){
            if(complete){
                CleanupFinished();
                Toast.makeText(getApplicationContext(), R.string.memory_utilized_efficiently, Toast.LENGTH_LONG).show();
            }
            else{
                Toast.makeText(getApplicationContext(), "Duplicates cleanup complete.", Toast.LENGTH_LONG).show();
            }
        }
        else {
            Toast.makeText(getApplicationContext(), "Duplicates cleanup complete. You saved " + formatSize(dupSize), Toast.LENGTH_LONG).show();
        }
        return result;
    }

    public static String GetMd5OfFile(String filePath) {
        String returnVal = "";
        try {
            InputStream input = new FileInputStream(filePath);
            byte[] buffer = new byte[1024];
            MessageDigest md5Hash = MessageDigest.getInstance("MD5");
            int numRead = 0;
            while (numRead != -1) {
                numRead = input.read(buffer);
                if (numRead > 0) {
                    md5Hash.update(buffer, 0, numRead);
                }
            }
            input.close();

            byte[] md5Bytes = md5Hash.digest();
            for (int i = 0; i < md5Bytes.length; i++) {
                returnVal += Integer.toString((md5Bytes[i] & 0xff) + 0x100, 16).substring(1);
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }
        return returnVal.toUpperCase();
    }

    class DuplicateAsyncTask extends AsyncTask<String, String, String>{
        Context mContext;

        public DuplicateAsyncTask(Context context){ mContext = context; }


        @Override
        protected String doInBackground(String... params) {
            return null;
        }
    }

    class FileCheckAsyncTask extends AsyncTask<String, Integer, String>{
        Context mContext;

        public FileCheckAsyncTask(Context context){ mContext = context; }

        private void checkFiles()
        {
            if(!canAccessExternalSd()) return;
            pictures.clear();
            recursiveScan(pictures, currentDirectory, ".jpg");
            publishProgress(R.string.pictures);

            videos.clear();
            recursiveScan(videos, currentDirectory, new String[]{".mp4", ".3gp", ".avi", ".mov", ".mkv"});
            publishProgress(R.string.videos);

            music.clear();
            recursiveScan(music, currentDirectory, new String[]{".mp3", ".wma", ".aac"});
            publishProgress(R.string.music);

            documents.clear();
            recursiveScan(documents, currentDirectory, new String[]{".docx", ".doc", ".pdf", ".xls", ".xlsx", ".zip"});
            publishProgress(R.string.documents);
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();

            inProgress = true;
            progressLayout.setVisibility(View.VISIBLE);
            progressText.setText(R.string.scanning_all_files);
        }

        @Override
        protected void onProgressUpdate(Integer... resource)
        {
            super.onProgressUpdate(resource);
            switch(resource[0]){
                case R.string.pictures:
                    btnPictures.setText(getResources().getString(R.string.pictures) + "\n(" + formatSize(calculateFilesSize(pictures)) + ")");
                    break;
                case R.string.music:
                    btnMusic.setText(getResources().getString(R.string.music) + "\n(" + formatSize(calculateFilesSize(music)) + ")");
                    break;
                case R.string.videos:
                    btnVideos.setText(getResources().getString(R.string.videos) + "\n(" + formatSize(calculateFilesSize(videos)) + ")");
                    break;
                case R.string.documents:
                    btnDocuments.setText(getResources().getString(R.string.documents) + "\n(" + formatSize(calculateFilesSize(documents)) + ")");                    break;
            }
        }

        @Override
        protected String doInBackground(String... params) {
            checkFiles();
            return null;
        }

        @Override
        protected void onPostExecute(String compressedFilePath) {
            super.onPostExecute(compressedFilePath);

            progressText.setText(R.string.scanning_complete);
            progressLayout.setVisibility(View.GONE);
            inProgress = false;
        }
    }

    class VideoAsyncTask extends AsyncTask<String, String, String> {

        Context mContext;
        String originaPath;

        public VideoAsyncTask(Context context){
            mContext = context;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
        }

        @Override
        protected String doInBackground(String... paths) {
            originaPath = paths[0];
            File destinationPath = new File(originaPath).getParentFile();
            MediaController.getInstance().convertVideo(paths[0], destinationPath, 0, 0, 0);
            File cached = MediaController.cachedFile;
            if(cached != null) {
                return cached.getPath();
            } else{
                return originaPath;
            }
        }

        @Override
        protected void onPostExecute(String compressedFilePath) {
            super.onPostExecute(compressedFilePath);
            String ext = originaPath.substring(originaPath.lastIndexOf("."));
            String destinationPath = originaPath.replace(ext, "_converted"+ext);
            new File(compressedFilePath).renameTo(new File(destinationPath));
            MainActivity.this.UpdateVideoProgress(originaPath, destinationPath);
        }
    }

}
