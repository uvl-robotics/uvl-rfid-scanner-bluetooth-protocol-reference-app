package com.example.bluetoothtest;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelUuid;
import android.text.method.ScrollingMovementMethod;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {

    BluetoothAdapter bluetoothAdapter;
    BluetoothDevice bluetoothDevice = null;
    BluetoothSocket bluetoothSocket = null;

    ArrayList<View> controls = new ArrayList<>();

    LinearLayout rootLayout;
    TextView textView;
    Button scanBtn;
    Button handshakeBtn;
    Button scanOnBtn;
    Button scanOffBtn;
    Button scanCountBtn;
    EditText scanCountInput;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        rootLayout = (LinearLayout) findViewById(R.id.rootLayout);
        textView = (TextView) findViewById(R.id.textView);
        handshakeBtn = (Button) findViewById(R.id.handshakeBtn);
        scanBtn = (Button) findViewById(R.id.scanBtn);
        scanOnBtn = (Button) findViewById(R.id.scanOnBtn);
        scanOffBtn = (Button) findViewById(R.id.scanOffBtn);
        scanCountBtn = (Button) findViewById(R.id.scanCountBtn);
        scanCountInput = (EditText) findViewById(R.id.scanCountInput);
        controls.add(handshakeBtn);
        controls.add(scanBtn);
        controls.add(scanOnBtn);
        controls.add(scanOffBtn);
        controls.add(scanCountBtn);
        controls.add(scanCountInput);


        textView.setMovementMethod(new ScrollingMovementMethod());


        if(ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH) != PackageManager.PERMISSION_GRANTED)
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.BLUETOOTH}, 1);

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        if(ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED)
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.BLUETOOTH_CONNECT}, 1);

        if (bluetoothAdapter == null) {
            log("Bluetooth no work");
        }
        else {
            log(bluetoothAdapter.toString(), String.valueOf(bluetoothAdapter.isEnabled()));

            if(!bluetoothAdapter.isEnabled()) {
                log("Bluetooth not on");
            }
            else {
                log("Bound devices:");
                Set<BluetoothDevice> boundDevices = bluetoothAdapter.getBondedDevices();
                for (BluetoothDevice device : boundDevices) {
                    log(device.getName() + "\n" + device.getAddress() + "\n");
                    spawnDeviceButton(device);
                }
            }
        }

        handshakeBtn.setOnClickListener(view -> {
            new Thread(() -> {
                boolean ok = handshake(bluetoothSocket);
                log(ok ? "Handshake ok" : "Handshake failed");
            }).start();
        });

        scanOnBtn.setOnClickListener(view -> {
            new Thread(() -> {
                boolean ok = scanOn(bluetoothSocket);
                log(ok ? "ScanOn ok" : "ScanOn failed");
            }).start();
        });

        scanOffBtn.setOnClickListener(view -> {
            waitingForResponse = false;
            new Thread(() -> {
                boolean ok = scanOff(bluetoothSocket);
                log(ok ? "ScanOff ok" : "ScanOff failed");
            }).start();
        });

        scanCountBtn.setOnClickListener(view -> {
            log("Scanning...");
            new Thread(() -> {
                String res = scanCount(bluetoothSocket);
                if (res == null) {
                    log("Scanned nothing");
                    return;
                }
            }).start();
        });

        scanBtn.setOnClickListener(view -> {
            log("Scanning...");
            new Thread(() -> {
                String res = scan(bluetoothSocket);
                if (res == null) {
                    log("Scanned nothing");
                    return;
                }
                String[] parts = res.split(",");
                if (parts.length != 7) {
                    log("Wrong format:", res);
                    return;
                }
                log("#   ", parts[0]); // Scan number in result sequence
                log("EPC ", parts[1]); // Electronic Product Code
                log("Ant ", parts[2]); // Antenna number
                log("RSSI", parts[3]); // Received signal strength indication
                log("Lon ", parts[4]); // GPS longitude of scanner
                log("Lat ", parts[5]); // GPS latitude of scanner
                log("TagT", parts[6]); // Tag type
            }).start();
        });
    }

    @SuppressLint("MissingPermission")
    void spawnDeviceButton(BluetoothDevice device) {
        Button button = new Button(this);
        button.setText(device.getName() + "\n" + device.getAddress());
        button.setTextAlignment(View.TEXT_ALIGNMENT_TEXT_START);

        button.setOnClickListener(view -> {
            if (bluetoothSocket != null) {
                lockControlButtons();
                new Thread(() -> {
                    disconnectDevice(bluetoothSocket);
                    bluetoothSocket = null;
                    bluetoothDevice = null;
                    runUI(this::unlockDeviceButtons);
                }).start();
            } else if (bluetoothDevice == null) {
                lockDeviceButtons(device.getAddress());
                new Thread(() -> {
                    bluetoothDevice = device;
                    bluetoothSocket = connectDevice(device);
                    if (bluetoothSocket == null) {
                        runUI(this::unlockDeviceButtons);
                        bluetoothDevice = null;
                    } else {
                        runUI(this::unlockControlButtons);
                    }
                }).start();
            }
        });

        rootLayout.addView(button);
    }

    @SuppressLint("MissingPermission")
    BluetoothSocket connectDevice (BluetoothDevice device) {
        UUID rfcommUuid = null;

        log("============CONNECTING================");
        log(device.getName());

        try {
            device.fetchUuidsWithSdp();
            List<ParcelUuid> uuids = Arrays.asList(device.getUuids());
            for (ParcelUuid uuid : uuids) {
                /*
                 * Look for the RFCOMM service
                 * According to official Bluetooth documentation, the RFCOMM service
                 * has the first 16 bits of its UUID be 0x0003, or 00001101 in binary
                 * https://www.bluetooth.com/specifications/assigned-numbers/
                 * https://btprodspecificationrefs.blob.core.windows.net/assigned-values/16-bit%20UUID%20Numbers%20Document.pdf
                 */
                if (uuid.toString().startsWith("00001101")) {
                    log("Found RFCOMM service", uuid.toString());
                    rfcommUuid = uuid.getUuid();
                }
            }
            if (rfcommUuid == null) {
                log("Could not find RFCOMM service");
                return null;
            }
        } catch (Exception e) {
            log("Cant get services list, trying default");
            /*
             * Try to use the default Bluetooth service UUID,
             * with RFCOMM service's first 16 bits added at the beginning.
             */
            rfcommUuid = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb");
        }

        BluetoothSocket bluetoothSocket;
        try {
            bluetoothSocket = device.createRfcommSocketToServiceRecord(rfcommUuid);
        } catch (IOException e) {
            try {
                bluetoothSocket = device.createInsecureRfcommSocketToServiceRecord(rfcommUuid);
                log("Switched to insecure connection ");
            } catch (IOException e2) {
                log("Can not create socket:");
                return null;
            }
        }

        log("created RFCOMM");

        try {
            bluetoothSocket.connect();
            log("connected");
        } catch (IOException e) {
            log("Cant connect to socket");
            return null;
        }

        return bluetoothSocket;
    }

    void disconnectDevice(BluetoothSocket bluetoothSocket) {
        try {
            bluetoothSocket.close();
            log("closed");
        } catch (IOException e) {
            log("Cant close the socket");
        }
    }

    boolean handshake(BluetoothSocket socket) {
        return commandBasic(socket, "AT", 1000);
    }

    boolean scanOn(BluetoothSocket socket) {
        command(socket, "AT+SCAN=1", "\r\n\r\nOK", Integer.MAX_VALUE);
        return true;
    }

    boolean scanOff(BluetoothSocket socket) {
        return commandBasic(socket, "AT+SCAN=0", 1000);
    }

    String scan(BluetoothSocket socket) {
        return command(socket, "AT+SCAN?", "\r\n\r\nOK", 10000);
    }

    String scanCount(BluetoothSocket socket) {
        String text = scanCountInput.getText().toString();
        Integer number;
        try {
            number = Integer.valueOf(text);
        } catch (NumberFormatException err) {
            log("\"" + text + "\"", "is not a number");
            return null;
        }
        return command(socket, "AT+SCAN?COUNT=" + number, "\r\n\r\nOK", 10000);
    }

    boolean commandBasic(BluetoothSocket socket, String command, int waitForMs) {
        String response = command(socket, command, "OK", waitForMs);
        return response != null;
    }

    @SuppressLint("MissingPermission")
    boolean waitingForResponse = false;
    String command(BluetoothSocket socket, String command, String expect, int waitForMs) {
        if (waitingForResponse) {
            log("another command running!");
            return null;
        } else
            waitingForResponse = true;

        try {
            OutputStream os = socket.getOutputStream();
            InputStream is = socket.getInputStream();

            int waited = 0;
            byte[] buff = new byte[4096];
            int len = 0;

            os.write((command + "\r\n").getBytes());
            os.flush();
            log(">", command);
            logRaw("< ");

            while (waitingForResponse && waited < waitForMs) {
                if (is.available() > 0) {
                    int read = is.read(buff, len, buff.length - len);
                    if (read == -1) {
                        buff = new byte[4096];
                        len = 0;
                        break;
                    } else if (read > 0) {
                        String responseRawPart = new String(buff, len,read);
                        logRaw(responseRawPart);
                        String responseRaw = new String(buff);
                        int expectIndex = responseRaw.indexOf(expect + "\r\n");
                        if (expectIndex >= 0) {
                            //log("<", responseRaw.substring(0, expectIndex + expect.length()));
                            String response = responseRaw.substring(0, expectIndex);
                            waitingForResponse = false;
                            return response;
                        }
                    }
                    len += read;
                    if (read >= buff.length) {
                        buff = new byte[4096];
                        len = 0;
                    }
                }
                Thread.sleep(1);
                waited += 1;
            }
        } catch (IOException e) {
            log("Cant use the socket");
            waitingForResponse = false;
            return null;
        } catch (InterruptedException e) {
            log("Reading interrupted");
            waitingForResponse = false;
            return null;
        }
        if (!waitingForResponse) {
            log("Command interrupted");
            return null;
        }
        log("Command timed out");
        waitingForResponse = false;
        return null;
    }

    void log(String arg, String ...args) {
        runUI(() -> {
            textView.append(arg);
            for (String more: args) {
                textView.append(" ");
                textView.append(more == null ? "null" : more);
            }
            textView.append("\n");
        });
    }

    void logRaw(String arg, String ...args) {
        runUI(() -> {
            textView.append(arg);
            for (String more: args)
                textView.append(more == null ? "null" : more);
        });
    }

    void runUI(Runnable r) {
        new Handler(Looper.getMainLooper()).post(r);
    }

    void lockDeviceButtons(String except) {
        int n = rootLayout.getChildCount();
        for (int i = 0; i < n; i++) {
            Button btn = (Button) rootLayout.getChildAt(i);
            if (!btn.getText().toString().contains(except))
                btn.setEnabled(false);
        }
    }

    void unlockDeviceButtons() {
        int n = rootLayout.getChildCount();
        for (int i = 0; i < n; i++) {
            Button btn = (Button) rootLayout.getChildAt(i);
            btn.setEnabled(true);
        }
    }

    void lockControlButtons() {
        for (View view : controls)
            view.setEnabled(false);
    }

    void unlockControlButtons() {
        for (View view : controls)
            view.setEnabled(true);
    }
}