package com.robosyslive.spacecleaner;

/**
 * Created by xwiz on 12/08/2018.
 */

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Created on : June 18, 2016
 * Author     : zetbaitsu
 * Name       : Zetra
 * GitHub     : https://github.com/zetbaitsu
 */
public class ImageUtil {

    public ImageUtil() {

    }

    static File compressImage(File imageFile, int reqWidth, int reqHeight, Bitmap.CompressFormat compressFormat, int quality, String destinationPath, String resized, int retries) throws IOException {
        //skip already resized images

        //if(imageFile.getName().contains(resized)) return imageFile;
        FileOutputStream fileOutputStream = null;
        File file = new File(destinationPath).getParentFile();
        if (!file.exists()) {
            file.mkdirs();
        }
        try {
            String newFile = file.getAbsolutePath();
            if(!resized.equals("")){
                newFile = GetNewFilename(destinationPath, resized);
            }
            fileOutputStream = new FileOutputStream(newFile);
            // write the compressed bitmap at the destination specified by destinationPath.
            Bitmap b = decodeSampledBitmapFromFile(imageFile, reqWidth, reqHeight);
            if(b != null) {
                b.compress(compressFormat, quality, fileOutputStream);
            }
            else{
                if(retries > 0){
                    return compressImage(imageFile, reqWidth, reqHeight, compressFormat, quality, destinationPath, resized, (retries - 1));
                }
                else {
                    fileOutputStream.flush();
                    fileOutputStream.close();
                    fileOutputStream = null;
                    if(!resized.equals("")){
                        File f = new File(newFile);
                        if(f.exists()) f.delete();
                    }
                }
            }
        } finally {
            if (fileOutputStream != null) {
                fileOutputStream.flush();
                fileOutputStream.close();
                if(!resized.equals("")){
                    imageFile.delete();
                }
            }
        }

        return new File(destinationPath);
    }

    static Bitmap decodeSampledBitmapFromFile(File imageFile, int reqWidth, int reqHeight) throws IOException {
        // First decode with inJustDecodeBounds=true to check dimensions
        if(!imageFile.exists() || imageFile.length() == 0){
            return null;
        }
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(imageFile.getAbsolutePath(), options);

        // Calculate inSampleSize
        options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight);

        // Decode bitmap with inSampleSize set
        options.inJustDecodeBounds = false;
        // decode full image pre-resized

        FileInputStream in = new FileInputStream(imageFile.getAbsolutePath());
        options = new BitmapFactory.Options();
        // decode full image
        Bitmap scaledBitmap = BitmapFactory.decodeStream(in, null, options);

        //check the rotation of the image and display it properly
        ExifInterface exif;
        exif = new ExifInterface(imageFile.getAbsolutePath());
        int orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, 0);
        Matrix matrix = new Matrix();
        if (orientation == 6) {
            matrix.postRotate(90);
        } else if (orientation == 3) {
            matrix.postRotate(180);
        } else if (orientation == 8) {
            matrix.postRotate(270);
        }

        if(scaledBitmap == null) {
            Log.e("SC_ERR","Failed to decode resource - " + imageFile.getAbsolutePath());
            return null;
        }

        int finalWidth = reqWidth;
        int finalHeight = reqHeight;

        if (reqHeight > 0 && reqWidth > 0) {
            int width = scaledBitmap.getWidth();
            int height = scaledBitmap.getHeight();
            if(height < reqHeight && width < reqWidth){
                //we do not need to resize.
                Log.e("SC_ERR","Picture too small - " + width + ": " + height);
                return scaledBitmap;
            }
            float ratioBitmap = (float) width / (float) height;
            float ratioMax = (float) reqWidth / (float) reqHeight;

            if (ratioMax > ratioBitmap) {
                finalWidth = (int) ((float) reqHeight * ratioBitmap);
            } else {
                finalHeight = (int) ((float) reqWidth / ratioBitmap);
            }
        }

        in.close();

        scaledBitmap = Bitmap.createBitmap(scaledBitmap, 0, 0, finalWidth, finalHeight, matrix, true);
        return scaledBitmap;
    }

    private static int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
        // Raw height and width of image
        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;

        if (height > reqHeight || width > reqWidth) {

            final int halfHeight = height / 2;
            final int halfWidth = width / 2;

            // Calculate the largest inSampleSize value that is a power of 2 and keeps both
            // height and width larger than the requested height and width.
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2;
            }
        }

        return inSampleSize;
    }

    public static String GetNewFilename(String path, String resize) {
        String destinationPath = path;
        String ext = path.substring(path.lastIndexOf("."));
        destinationPath = destinationPath.replace(destinationPath.substring(destinationPath.lastIndexOf(".")), resize + ext);
        return  destinationPath;
    }

    public static String GetNewFilename(File file, String resize) {
        String destinationPath = file.getAbsolutePath();
        return  GetNewFilename(destinationPath, resize);
    }
}
