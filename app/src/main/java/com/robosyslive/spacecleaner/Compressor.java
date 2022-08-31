package com.robosyslive.spacecleaner;

/**
 * Created by xwiz on 12/08/2018.
 */


import android.app.IntentService;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.preference.PreferenceManager;

import com.robosyslive.spacecleaner.ImageUtil;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.Callable;

import io.reactivex.Flowable;

/**
 * Created on : June 18, 2016
 * Author     : zetbaitsu
 * Name       : Zetra
 * GitHub     : https://github.com/zetbaitsu
 */
public class Compressor extends id.zelory.compressor.Compressor {
    //max width and height values of the compressed image is taken as 612x816
    private int maxWidth = 1600;
    private int maxHeight = 1600;
    private Bitmap.CompressFormat compressFormat = Bitmap.CompressFormat.JPEG;
    private int quality = 85;
    private String destinationDirectoryPath;
    private String resized;

    public Compressor(Context context) {
        super(context);
        SharedPreferences SP = PreferenceManager.getDefaultSharedPreferences(context);
        this.resized = SP.getString("resized_name", "");
        this.quality = Integer.parseInt(SP.getString("picture_quality", "85"));
        this.maxHeight = Integer.parseInt(SP.getString("max_height_width", "1600"));
        this.maxWidth = this.maxHeight;
    }

    public Compressor(Context context, File f) {
        super(context);
        destinationDirectoryPath = f.getParent();
        SharedPreferences SP = PreferenceManager.getDefaultSharedPreferences(context);
        this.resized = SP.getString("resized_name", "");
        this.quality = Integer.parseInt(SP.getString("picture_quality", "85"));
        this.maxHeight = Integer.parseInt(SP.getString("max_height_width", "1600"));
        this.maxWidth = this.maxHeight;
    }

    @Override
    public File compressToFile(File imageFile, String compressedFileName) throws IOException {
        return ImageUtil.compressImage(imageFile, maxWidth, maxHeight, compressFormat, quality,
                destinationDirectoryPath + File.separator + compressedFileName, resized, 1);
    }

    public Flowable<File> compressToFileAsFlowable(final File imageFile) {
        return compressToFileAsFlowable(imageFile, imageFile.getName());
    }

    public Flowable<File> compressToFileAsFlowable(final File imageFile, final String compressedFileName) {
        return Flowable.defer(new Callable<Flowable<File>>() {
            @Override
            public Flowable<File> call() {
                try {
                    return Flowable.just(compressToFile(imageFile, compressedFileName));
                } catch (IOException e) {
                    return Flowable.error(e);
                }
            }
        });
    }
}
