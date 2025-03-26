package com.example.qrcodevariant;

import android.Manifest;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class MainActivity extends AppCompatActivity {

    private static final int CAMERA_REQUEST_CODE = 100;
    private static final int CAMERA_PERMISSION_CODE = 101;
    private static final int GALLERY_REQUEST_CODE = 200;

    private EditText editTextBinary;
    private Spinner spinnerGridSizeEncode, spinnerGridSizeDecode;
    private Button buttonGenerate, buttonCapture, buttonSelectFromGallery;
    private ImageView imageViewCode, imageViewCaptured;
    private TextView textViewDecoded;

    private int blockSize = 100; // Define block size (adjust as needed)

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize UI elements
        editTextBinary = findViewById(R.id.editTextBinary);
        spinnerGridSizeEncode = findViewById(R.id.spinnerGridSizeEncode);
        spinnerGridSizeDecode = findViewById(R.id.spinnerGridSizeDecode);
        buttonGenerate = findViewById(R.id.buttonGenerate);
        buttonCapture = findViewById(R.id.buttonCapture);
        buttonSelectFromGallery = findViewById(R.id.buttonSelectFromGallery);
        imageViewCode = findViewById(R.id.imageViewCode);
        imageViewCaptured = findViewById(R.id.imageViewCaptured);
        textViewDecoded = findViewById(R.id.textViewDecoded);

        // Setup spinner with grid sizes from res/values/strings.xml
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(
                this,
                R.array.grid_sizes,
                android.R.layout.simple_spinner_item
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerGridSizeEncode.setAdapter(adapter);
        spinnerGridSizeDecode.setAdapter(adapter);

        // Handle Generate Code button click (Encoding)
        buttonGenerate.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String binaryInput = editTextBinary.getText().toString().trim();
                int gridSize = Integer.parseInt(spinnerGridSizeEncode.getSelectedItem().toString());
                int requiredLength = (gridSize - 2) * (gridSize - 2);

                if (binaryInput.length() != requiredLength) {
                    Toast.makeText(
                            MainActivity.this,
                            "Binary input must be exactly " + requiredLength + " bits.",
                            Toast.LENGTH_SHORT
                    ).show();
                    return;
                }

                // Generate the QR code bitmap
                Bitmap generatedBitmap = generateQRCodeVariant(binaryInput, gridSize);

                // Display in ImageView
                imageViewCode.setImageBitmap(generatedBitmap);

                // Save to gallery
                String fileName = "QRCodeVariant_" + System.currentTimeMillis();
                saveBitmapToGallery(generatedBitmap, fileName);
            }
        });


        // Handle Capture Code button click (Decoding from Camera)
        buttonCapture.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // Check for camera permission
                if (ContextCompat.checkSelfPermission(
                        MainActivity.this,
                        Manifest.permission.CAMERA
                ) != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(
                            MainActivity.this,
                            new String[]{Manifest.permission.CAMERA},
                            CAMERA_PERMISSION_CODE
                    );
                } else {
                    openCamera();
                }
            }
        });

        // Handle Select from Gallery button click (Decoding from Gallery)
        buttonSelectFromGallery.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                openGallery();
            }
        });
    }

    // Opens the camera using an intent (returns a thumbnail)
    private void openCamera() {
        Intent cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (cameraIntent.resolveActivity(getPackageManager()) != null) {
            startActivityForResult(cameraIntent, CAMERA_REQUEST_CODE);
        }
    }

    // Opens the gallery for the user to pick an image
    private void openGallery() {
        Intent galleryIntent = new Intent(Intent.ACTION_PICK,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        startActivityForResult(galleryIntent, GALLERY_REQUEST_CODE);
    }

    // Handle camera permission result
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                openCamera();
            } else {
                Toast.makeText(this, "Camera permission is required to capture image", Toast.LENGTH_SHORT).show();
            }
        }
    }

    // Process the captured or selected image
    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        // If the user captured a photo with the camera
        if (requestCode == CAMERA_REQUEST_CODE && resultCode == RESULT_OK && data != null) {
            Bundle extras = data.getExtras();
            Bitmap capturedImage = (Bitmap) extras.get("data");
            if (capturedImage != null) {
                imageViewCaptured.setImageBitmap(capturedImage);

                int gridSize = Integer.parseInt(spinnerGridSizeDecode.getSelectedItem().toString());
                String decodedBinary = decodeQRCodeVariant(capturedImage, gridSize);
                textViewDecoded.setText("Decoded Binary: " + decodedBinary);
            }
        }
        // If the user selected an image from the gallery
        else if (requestCode == GALLERY_REQUEST_CODE && resultCode == RESULT_OK && data != null) {
            Uri selectedImageUri = data.getData();
            if (selectedImageUri != null) {
                try {
                    // Load and rotate the image if needed
                    Bitmap selectedImage = handleSamplingAndRotationBitmap(selectedImageUri);
                    imageViewCaptured.setImageBitmap(selectedImage);

                    int gridSize = Integer.parseInt(spinnerGridSizeDecode.getSelectedItem().toString());
                    String decodedBinary = decodeQRCodeVariant(selectedImage, gridSize);
                    textViewDecoded.setText("Decoded Binary: " + decodedBinary);
                } catch (IOException e) {
                    e.printStackTrace();
                    Toast.makeText(this, "Failed to load image.", Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    /**
     * This method handles the image rotation and sampling to avoid out of memory issues
     * @param selectedImage The URI of the selected image
     * @return A rotated bitmap based on EXIF data
     */
    public Bitmap handleSamplingAndRotationBitmap(Uri selectedImage) throws IOException {
        int MAX_HEIGHT = 1024;
        int MAX_WIDTH = 1024;

        // First decode with inJustDecodeBounds=true to check dimensions
        final BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        InputStream imageStream = getContentResolver().openInputStream(selectedImage);
        BitmapFactory.decodeStream(imageStream, null, options);
        imageStream.close();

        // Calculate inSampleSize
        options.inSampleSize = calculateInSampleSize(options, MAX_WIDTH, MAX_HEIGHT);

        // Decode bitmap with inSampleSize set
        options.inJustDecodeBounds = false;
        imageStream = getContentResolver().openInputStream(selectedImage);
        Bitmap img = BitmapFactory.decodeStream(imageStream, null, options);
        imageStream.close();

        // Rotate the image if needed
        return rotateImageIfRequired(img, selectedImage);
    }

    /**
     * Calculate an inSampleSize for use in a BitmapFactory.Options object when decoding
     * bitmaps using the decode* methods from BitmapFactory. This implementation calculates
     * the closest inSampleSize that will result in the final decoded bitmap having a width and
     * height equal to or larger than the requested width and height.
     */
    public static int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
        // Raw height and width of image
        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;

        if (height > reqHeight || width > reqWidth) {
            // Calculate ratios of height and width to requested height and width
            final int heightRatio = Math.round((float) height / (float) reqHeight);
            final int widthRatio = Math.round((float) width / (float) reqWidth);

            // Choose the smallest ratio as inSampleSize value, this will guarantee a final image
            // with both dimensions larger than or equal to the requested height and width.
            inSampleSize = heightRatio < widthRatio ? heightRatio : widthRatio;

            // This offers some additional logic in case the image has a strange
            // aspect ratio. For example, a panorama may have a much larger
            // width than height. In these cases the total pixels might still
            // end up being too large to fit comfortably in memory, so we should
            // be more aggressive with sample down the image (=larger inSampleSize).
            final float totalPixels = width * height;

            // Anything more than 2x the requested pixels we'll sample down further
            final float totalReqPixelsCap = reqWidth * reqHeight * 2;

            while (totalPixels / (inSampleSize * inSampleSize) > totalReqPixelsCap) {
                inSampleSize++;
            }
        }
        return inSampleSize;
    }

    /**
     * Rotate an image if required.
     * @param img The image bitmap
     * @param selectedImage Image URI
     * @return The resulted Bitmap after handling rotation
     */
    private Bitmap rotateImageIfRequired(Bitmap img, Uri selectedImage) throws IOException {
        String[] projection = {MediaStore.Images.Media.DATA};
        Cursor cursor = getContentResolver().query(selectedImage, projection, null, null, null);

        if (cursor == null) {
            ExifInterface ei = new ExifInterface(getContentResolver().openInputStream(selectedImage));
            int orientation = ei.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED);
            return rotateImage(img, getRotationFromOrientation(orientation));
        } else {
            int column_index = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
            cursor.moveToFirst();
            String path = cursor.getString(column_index);
            cursor.close();

            ExifInterface ei = new ExifInterface(path);
            int orientation = ei.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED);
            return rotateImage(img, getRotationFromOrientation(orientation));
        }
    }

    /**
     * Get rotation in degrees from EXIF orientation constant
     */
    private static int getRotationFromOrientation(int orientation) {
        switch (orientation) {
            case ExifInterface.ORIENTATION_ROTATE_90:
                return 90;
            case ExifInterface.ORIENTATION_ROTATE_180:
                return 180;
            case ExifInterface.ORIENTATION_ROTATE_270:
                return 270;
            default:
                return 0;
        }
    }

    /**
     * Rotate the given bitmap with specified degrees
     */
    private static Bitmap rotateImage(Bitmap img, int degree) {
        if (degree == 0) return img;

        Matrix matrix = new Matrix();
        matrix.postRotate(degree);
        Bitmap rotatedImg = Bitmap.createBitmap(img, 0, 0, img.getWidth(), img.getHeight(), matrix, true);
        img.recycle();
        return rotatedImg;
    }

    private void saveBitmapToGallery(Bitmap bitmap, String fileName) {
        OutputStream fos;
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                ContentValues values = new ContentValues();
                values.put(MediaStore.Images.Media.DISPLAY_NAME, fileName);
                values.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
                values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/QRCodeVariants");

                Uri uri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
                if (uri != null) {
                    fos = getContentResolver().openOutputStream(uri);
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
                    if (fos != null) fos.close();
                    Toast.makeText(this, "Saved to gallery!", Toast.LENGTH_SHORT).show();
                }
            } else {
                // Legacy method for API < 29
                String imagesDir = Environment.getExternalStoragePublicDirectory(
                        Environment.DIRECTORY_PICTURES
                ).toString() + "/QRCodeVariants";
                File file = new File(imagesDir);
                if (!file.exists()) file.mkdirs();

                File image = new File(file, fileName + ".png");
                fos = new FileOutputStream(image);
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
                fos.flush();
                fos.close();

                // Make visible in gallery
                Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
                Uri contentUri = Uri.fromFile(image);
                mediaScanIntent.setData(contentUri);
                this.sendBroadcast(mediaScanIntent);

                Toast.makeText(this, "Saved to gallery!", Toast.LENGTH_SHORT).show();
            }
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "Failed to save image.", Toast.LENGTH_SHORT).show();
        }
    }


    // Generates the QR code variant bitmap with visible grid lines from the binary string and grid size
    private Bitmap generateQRCodeVariant(String binary, int gridSize) {
        int width = gridSize * blockSize;
        int height = gridSize * blockSize;

        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint();

        // 1) Fill the background with white
        canvas.drawColor(Color.WHITE);

        // 2) Draw the alternating border (first row and first column)
        for (int col = 0; col < gridSize; col++) {
            paint.setColor((col % 2 == 0) ? Color.BLACK : Color.WHITE);
            canvas.drawRect(col * blockSize, 0, (col + 1) * blockSize, blockSize, paint);
        }
        for (int row = 0; row < gridSize; row++) {
            paint.setColor((row % 2 == 0) ? Color.BLACK : Color.WHITE);
            canvas.drawRect(0, row * blockSize, blockSize, (row + 1) * blockSize, paint);
        }

        // 3) Fill the central region with the binary data
        int index = 0;
        for (int row = 1; row < gridSize - 1; row++) {
            for (int col = 1; col < gridSize - 1; col++) {
                if (index < binary.length()) {
                    char bit = binary.charAt(index);
                    paint.setColor(bit == '1' ? Color.BLACK : Color.WHITE);
                    canvas.drawRect(col * blockSize, row * blockSize,
                            (col + 1) * blockSize, (row + 1) * blockSize, paint);
                    index++;
                }
            }
        }

        // 4) Draw grid lines for clear block boundaries
        paint.setColor(Color.BLACK);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(3);
        for (int i = 0; i <= gridSize; i++) {
            int y = i * blockSize;
            canvas.drawLine(0, y, width, y, paint);
        }
        for (int i = 0; i <= gridSize; i++) {
            int x = i * blockSize;
            canvas.drawLine(x, 0, x, height, paint);
        }
        paint.setStyle(Paint.Style.FILL);

        return bitmap;
    }

    // Decodes the QR code variant by reading the central region of the bitmap using the provided grid size
    private String decodeQRCodeVariant(Bitmap bitmap, int gridSize) {
        if (bitmap == null || bitmap.getWidth() == 0 || bitmap.getHeight() == 0) {
            return "No image to decode.";
        }

        int blockWidth = bitmap.getWidth() / gridSize;
        int blockHeight = bitmap.getHeight() / gridSize;
        StringBuilder binaryResult = new StringBuilder();

        // Process only the central area (excluding the border row/column)
        for (int row = 1; row < gridSize - 1; row++) {
            for (int col = 1; col < gridSize - 1; col++) {
                int startX = col * blockWidth;
                int startY = row * blockHeight;
                binaryResult.append(getBlockValue(bitmap, startX, startY, blockWidth, blockHeight));
            }
        }
        return binaryResult.toString();
    }

    /**
     * Samples the center region of a block to determine whether it's mostly black (returns '1')
     * or white (returns '0'), skipping the outer edges where grid lines may be drawn.
     */
    private char getBlockValue(Bitmap bitmap, int startX, int startY, int blockWidth, int blockHeight) {
        int blackCount = 0;
        int whiteCount = 0;

        int innerPadding = (int) (Math.min(blockWidth, blockHeight) * 0.2);
        int sampleStartX = startX + innerPadding;
        int sampleStartY = startY + innerPadding;
        int sampleEndX = startX + blockWidth - innerPadding;
        int sampleEndY = startY + blockHeight - innerPadding;

        int samplePoints = 0;

        for (int y = sampleStartY; y < sampleEndY; y += 5) {
            for (int x = sampleStartX; x < sampleEndX; x += 5) {
                if (x >= 0 && x < bitmap.getWidth() && y >= 0 && y < bitmap.getHeight()) {
                    int pixel = bitmap.getPixel(x, y);
                    int gray = (Color.red(pixel) + Color.green(pixel) + Color.blue(pixel)) / 3;
                    if (gray < 128) {
                        blackCount++;
                    } else {
                        whiteCount++;
                    }
                    samplePoints++;
                }
            }
        }
        if (samplePoints == 0) {
            return '0';
        }
        return (blackCount > whiteCount) ? '1' : '0';
    }
}
