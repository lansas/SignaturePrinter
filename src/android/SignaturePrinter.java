package com.iigservices.cordova.plugin;

import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CallbackContext;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import java.io.*;
import java.util.*;

/**
 * This class echoes a string called from JavaScript.
 */
public class SignaturePrinter extends CordovaPlugin {

   private final static char ESC_CHAR = 0x1B;
    private final static byte[] LINE_FEED = new byte[]{0x0A};
    private final static byte[] INIT_PRINTER = new byte[]{ESC_CHAR, 0x40};
    private static byte[] SELECT_BIT_IMAGE_MODE = {0x1B, 0x2A, 33};
    private final static byte[] SET_LINE_SPACE_0 = new byte[]{ESC_CHAR, 0x33, 24};
    private final static byte[] SET_LINE_SPACE_30 = new byte[]{ESC_CHAR, 0x33, 30};
    // android built in classes for bluetooth operations
    BluetoothAdapter mBluetoothAdapter;
    BluetoothSocket mmSocket;
    BluetoothDevice mmDevice;
    // needed for communication to bluetooth device / network
    OutputStream mmOutputStream;
    InputStream mmInputStream;
    Thread workerThread;

    byte[] readBuffer;
    int readBufferPosition;
    volatile boolean stopWorker;
    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
        if(action.equals("initPrinter"))
        {
            try{
                String printerMAC = args.getString(0);
                this.initPrinter(printerMAC,callbackContext);
                return true;
            }
            catch(Exception e)
            {
                callbackContext.error("Error in execute().init():Message "+e.getMessage());
            }
        }
        if(action.equals("printText"))
        {
            try{
                String toPrint = args.getString(0);
                this.printText(toPrint,callbackContext);
                return true;
            }
            catch(Exception e)
            {
                callbackContext.error("Error in execute().print():Message "+e.getMessage());
            }
        }
        if (action.equals("printSignature")) {
            try{
                JSONArray array = args.getJSONArray(0);
                // Create an int array to accomodate the numbers.
                int[] numbers = new int[array.length()];
                // Extract numbers from JSON array.
                for (int i = 0; i < array.length(); ++i) {
                    numbers[i] = array.getInt(i);
                }
                int width = args.getInt(1);
                int height = args.getInt(2);
                this.printSignature(numbers,width,height, callbackContext);
                return true;
            }
            catch(Exception e)
            {
                callbackContext.error("Error in execute().printSignature():Message "+e.getMessage());
            }
        }
        return false;
    }
    
    private void initPrinter(String deviceMAC,CallbackContext callbackContext){
        if (deviceMAC != null && deviceMAC.length() > 0) {
            try {
                mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
                final Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();
                if(pairedDevices.size() > 0) {
                    for ( int i = 0 ;i< pairedDevices.size() ;i++){
                        BluetoothDevice device = (BluetoothDevice)pairedDevices.toArray()[i];
                        device.getAddress();
                        if (device.getAddress().equals(deviceMAC)) {
                            mmDevice = device;
							openBT(callbackContext);
                            break;
                        }
                    }
                }
                callbackContext.success("Initialization successfully.");
            }
            catch(Exception e){
                callbackContext.error("Error in initPrinter():Message "+e.getMessage());
            }
        }
        else {
            callbackContext.error("Expected BluetoothPrinter non-empty printerMAC argument.");
        }
    }
    private void printText(String message,CallbackContext callbackContext)
    {
        if(message != null && message.length() > 0){
            try{
                mmOutputStream.write(message.getBytes());
                callbackContext.success("Message printed successfully");
            }
            catch(Exception e){
                callbackContext.error("Error in printText():Message "+e.getMessage());
            }
        }
            else {
            callbackContext.error("Expected BluetoothPrinter non-empty string argument.");
        }
    }
    private void printSignature(int[] image,int width,int height, CallbackContext callbackContext)
    {
        if (image != null) {
                Bitmap newImage = Bitmap.createBitmap(image,width,height,Bitmap.Config.ARGB_8888);
                newImage = scaleDown(newImage,360,false);
                printImage(getPixelsSlow(newImage),mmOutputStream,callbackContext);
                callbackContext.success("Signature printed successfully");
        } else {
            callbackContext.error("Expected BluetoothPrinter not null Array argument.");
        }
    }
    private int[][] getPixelsSlow(Bitmap image) {
        int width = image.getWidth();
        int height = image.getHeight();
        int[][] result = new int[height][width];
        for (int row = 0; row < height; row++) {
            for (int col = 0; col < width; col++) {
                result[row][col] = image.getPixel(col,row);
            }
        }
        return result;
    }
    private void printImage(int[][] pixels,OutputStream printPort,CallbackContext callbackContext) {
    // Set the line spacing at 24 (we'll print 24 dots high)
        try {

            printPort.write(SET_LINE_SPACE_0);
            for (int y = 0; y < pixels.length; y += 24) {
                // Like I said before, when done sending data,
                // the printer will resume to normal text printing
                printPort.write(SELECT_BIT_IMAGE_MODE);
                // Set nL and nH based on the width of the image
                printPort.write(new byte[]{(byte) (0x00ff & pixels[y].length)
                , (byte) ((0xff00 & pixels[y].length) >> 8)});
                for (int x = 0; x < pixels[y].length; x++) {
                // for each stripe, recollect 3 bytes (3 bytes = 24 bits)
                    printPort.write(recollectSlice(y, x, pixels));
                }
                // Do a line feed, if not the printing will resume on the same line
                printPort.write(LINE_FEED);
            }
            printPort.write(SET_LINE_SPACE_30);
            printPort.write(LINE_FEED);
            printPort.write(LINE_FEED);
            printPort.write(LINE_FEED);
        } catch (Exception e) {
            callbackContext.error("Error in printImage():Message "+e.getMessage());
        }
    }
    private byte[] recollectSlice(int y, int x, int[][] img) {
        byte[] slices = new byte[] {0, 0, 0};
        for (int yy = y, i = 0; yy < y + 24 && i < 3; yy += 8, i++) {
        byte slice = 0;
        for (int b = 0; b < 8; b++) {
            int yyy = yy + b;
            if (yyy >= img.length) {
                continue;
            }
            int col = img[yyy][x];
            boolean v = shouldPrintColor(col);
            slice |= (byte) ((v ? 1 : 0) << (7 - b));
        }
            slices[i] = slice;
        }
        return slices;
    }
    private boolean shouldPrintColor(int col) {
        final int threshold = 127;
        int a, r, g, b, luminance;
        a = (col >> 24) & 0xff;
        if (a != 0xff) {// Ignore transparencies
            return false;
        }
        r = (col >> 16) & 0xff;
        g = (col >> 8) & 0xff;
        b = col & 0xff;
        luminance = (int) (0.299 * r + 0.587 * g + 0.114 * b);
        return luminance < threshold;
    }
    private Bitmap scaleDown(Bitmap realImage, float maxImageSize,boolean filter) {
        float ratio = Math.min(
        maxImageSize / realImage.getWidth(),
        maxImageSize / realImage.getHeight());
        int width = Math.round(ratio * realImage.getWidth());
        int height = Math.round(ratio * realImage.getHeight());
        int[] colors = new int[1000];
        Bitmap newBitmap = Bitmap.createScaledBitmap(realImage, width,
        height, filter);
        return newBitmap;
    }
    void openBT(CallbackContext callbackContext ){
        int stat =0;
        if(mmDevice!=null){
            try {
                // Standard SerialPortService ID
                UUID uuid = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb");
                mmSocket = mmDevice.createRfcommSocketToServiceRecord(uuid);
                mBluetoothAdapter.cancelDiscovery();
                mmSocket.connect();
                mmOutputStream = mmSocket.getOutputStream();
                mmInputStream = mmSocket.getInputStream();
                beginListenForData(callbackContext);
            } catch (Exception e) {
                callbackContext.error("Error in openBT():Message "+e.getMessage()+" Status:"+stat);
            }
        }
    }
    /*
     * after opening a connection to bluetooth printer device,
     * we have to listen and check if a data were sent to be printed.
     */
    void beginListenForData(CallbackContext callbackContext) {
        try {
            final Handler handler = new Handler();
            // this is the ASCII code for a newline character
            final byte delimiter = 10;
            stopWorker = false;
            readBufferPosition = 0;
            readBuffer = new byte[1024];
            workerThread = new Thread(new Runnable() {
                public void run() {
                    while (!Thread.currentThread().isInterrupted() && !stopWorker) {
                        try {
                            int bytesAvailable = mmInputStream.available();
                            if (bytesAvailable > 0) {
                                byte[] packetBytes = new byte[bytesAvailable];
                                mmInputStream.read(packetBytes);
                                for (int i = 0; i < bytesAvailable; i++) {
                                    byte b = packetBytes[i];
                                    if (b == delimiter) {
                                        byte[] encodedBytes = new byte[readBufferPosition];
                                        System.arraycopy(
                                        readBuffer, 0,
                                        encodedBytes, 0,
                                        encodedBytes.length
                                        );
                                        // specify US-ASCII encoding
                                        final String data = new String(encodedBytes, "US-ASCII");
                                        readBufferPosition = 0;
                                        // tell the user data were sent to bluetooth printer device
                                    } else {
                                        readBuffer[readBufferPosition++] = b;
                                    }
                                }
                            }
                        } catch (IOException ex) {
                            stopWorker = true;
                        }
                    }
                }
            });
            workerThread.start();
        } catch (Exception e) {
            callbackContext.error("Error in beginListenForData():Message "+e.getMessage());
        }
    }

    // close the connection to bluetooth printer.
    void closeBT(CallbackContext callbackContext) throws IOException {
        try {
            stopWorker = true;
            mmOutputStream.close();
            mmInputStream.close();
            mmSocket.close();
        } catch (Exception e) {
             callbackContext.error("Error in closeBT():Message "+e.getMessage());
        }
    }
}
