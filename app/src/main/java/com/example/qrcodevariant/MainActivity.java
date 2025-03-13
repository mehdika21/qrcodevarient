package com.example.qrcodevariant;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Bundle;
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

public class MainActivity extends AppCompatActivity {

    private static final int CAMERA_REQUEST_CODE = 100;
    private static final int CAMERA_PERMISSION_CODE = 101;

    private EditText editTextBinary;
    private Spinner spinnerGridSizeEncode, spinnerGridSizeDecode;
    private Button buttonGenerate, buttonCapture;
    private ImageView imageViewCode, imageViewCaptured;
    private TextView textViewDecoded;

    private int blockSize = 100; // Define blocksize

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
        imageViewCode = findViewById(R.id.imageViewCode);
        imageViewCaptured = findViewById(R.id.imageViewCaptured);
        textViewDecoded = findViewById(R.id.textViewDecoded);

        // Setup spinner with grid sizes (values from res/values/strings.xml)
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(
                this,
                R.array.grid_sizes,
                android.R.layout.simple_spinner_item
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

        // Assign the same adapter to both spinners
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

                Bitmap generatedBitmap = generateQRCodeVariant(binaryInput, gridSize);
                imageViewCode.setImageBitmap(generatedBitmap);
            }
        });

        // Handle Capture Code button click (Decoding)
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
    }

    // Opens the camera using an intent
    // In your openCamera method
    private void openCamera() {
        Intent cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        // Request high resolution image
        if (cameraIntent.resolveActivity(getPackageManager()) != null) {
            startActivityForResult(cameraIntent, CAMERA_REQUEST_CODE);
        }
    }

    // Handle permission result for the camera
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                openCamera();
            } else {
                Toast.makeText(
                        this,
                        "Camera permission is required to capture image",
                        Toast.LENGTH_SHORT
                ).show();
            }
        }
    }

    // Process the captured image from the camera
    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == CAMERA_REQUEST_CODE && resultCode == RESULT_OK) {
            Bundle extras = data.getExtras();
            Bitmap capturedImage = (Bitmap) extras.get("data");
            imageViewCaptured.setImageBitmap(capturedImage);

            // Use the user-selected grid size for DECODING
            int gridSize = Integer.parseInt(spinnerGridSizeDecode.getSelectedItem().toString());
            String decodedBinary = decodeQRCodeVariant(capturedImage, gridSize);
            textViewDecoded.setText("Decoded Binary: " + decodedBinary);
        }
    }

    // Generates the QR code variant bitmap from the binary string and grid size
    private Bitmap generateQRCodeVariant(String binary, int gridSize) {
        int width = gridSize * blockSize;
        int height = gridSize * blockSize;

        // Create a blank bitmap and canvas
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
                    canvas.drawRect(
                            col * blockSize,
                            row * blockSize,
                            (col + 1) * blockSize,
                            (row + 1) * blockSize,
                            paint
                    );
                    index++;
                }
            }
        }

        // 4) Draw the grid lines on top to make boundaries clear
        paint.setColor(Color.BLACK);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(3); // Adjust thickness if needed

        // Horizontal lines
        for (int i = 0; i <= gridSize; i++) {
            int y = i * blockSize;
            canvas.drawLine(0, y, width, y, paint);
        }
        // Vertical lines
        for (int i = 0; i <= gridSize; i++) {
            int x = i * blockSize;
            canvas.drawLine(x, 0, x, height, paint);
        }

        // Reset style (optional if you do more drawing later)
        paint.setStyle(Paint.Style.FILL);

        return bitmap;
    }



    // Decodes the QR code variant using the provided grid size by reading the central region
    private String decodeQRCodeVariant(Bitmap bitmap, int gridSize) {
        if (bitmap == null || bitmap.getWidth() == 0 || bitmap.getHeight() == 0) {
            return "No image to decode.";
        }

        // Compute the block dimensions based on the captured bitmap
        int blockWidth = bitmap.getWidth() / gridSize;
        int blockHeight = bitmap.getHeight() / gridSize;
        StringBuilder binaryResult = new StringBuilder();

        // Process only the central area (excluding the border row & column)
        for (int row = 1; row < gridSize - 1; row++) {
            for (int col = 1; col < gridSize - 1; col++) {
                int startX = col * blockWidth;
                int startY = row * blockHeight;

                // We'll sample the center of each block to avoid the grid lines
                binaryResult.append(
                        getBlockValue(bitmap, startX, startY, blockWidth, blockHeight)
                );
            }
        }
        return binaryResult.toString();
    }

    /**
     * Samples the center region of a block to decide if it's mostly black (1) or white (0).
     */
    private char getBlockValue(Bitmap bitmap, int startX, int startY, int blockWidth, int blockHeight) {
        int blackCount = 0;
        int whiteCount = 0;

        // 20% padding helps skip the grid lines drawn at the edges
        int innerPadding = (int) (Math.min(blockWidth, blockHeight) * 0.2);
        int sampleStartX = startX + innerPadding;
        int sampleStartY = startY + innerPadding;
        int sampleEndX = startX + blockWidth - innerPadding;
        int sampleEndY = startY + blockHeight - innerPadding;

        int samplePoints = 0;

        // Sample a few points within the padded area
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

        // If we somehow didn't sample anything, default to '0'
        if (samplePoints == 0) {
            return '0';
        }

        // Whichever color we have more of determines the block value
        return (blackCount > whiteCount) ? '1' : '0';
    }

}