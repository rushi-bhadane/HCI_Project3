package com.example.audioapp;

import android.content.Context;
import org.tensorflow.lite.Interpreter;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import android.content.res.AssetFileDescriptor;

public class TFLiteHelper {
    private Interpreter tflite;

    // Constructor
    public TFLiteHelper(Context context) {
        try {
            tflite = new Interpreter(loadModelFile(context, "model.tflite"));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Load the TFLite model from assets
    private MappedByteBuffer loadModelFile(Context context, String modelName) throws IOException {
        AssetFileDescriptor fileDescriptor = context.getAssets().openFd(modelName);
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }

    // Placeholder method for preprocessing the input. Adapt it to your needs.
    public ByteBuffer preprocess(byte[] frameData) {
        // Preprocess the frame data here (resize, normalize, etc.)
        // Return a ByteBuffer ready for inference
        return ByteBuffer.wrap(frameData); // Simple placeholder
    }

    // Run inference and return the recognized digit
    public String runInference(ByteBuffer inputBuffer) {
        // Assuming your model's output is a float array of probabilities for each class (digit)
        float[][] output = new float[1][10]; // 10 classes for digits 0-9

        tflite.run(inputBuffer, output);

        // Post-processing: Convert output probabilities to actual digit prediction
        int maxIndex = -1;
        float maxProbability = 0.0f;
        for (int i = 0; i < output[0].length; i++) {
            if (output[0][i] > maxProbability) {
                maxProbability = output[0][i];
                maxIndex = i;
            }
        }

        return Integer.toString(maxIndex); // Return the recognized digit as a string
    }
}
