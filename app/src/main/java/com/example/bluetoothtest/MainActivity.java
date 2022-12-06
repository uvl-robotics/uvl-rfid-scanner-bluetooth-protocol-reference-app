package com.example.bluetoothtest;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.DialogInterface;
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
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {

    static Handler mainLooperHandler = new Handler(Looper.getMainLooper());

    BluetoothAdapter bluetoothAdapter;
    BluetoothDevice bluetoothDevice = null;
    BluetoothSocket bluetoothSocket = null;

    ArrayList<View> controls = new ArrayList<>();

    LinearLayout rootLayout;
    TextView textView;
    Button scanBtn;
    Button scanExtendedBtn;
    Button handshakeBtn;
    Button versionBtn;
    Button scanOnBtn;
    Button scanOffBtn;
    Button interruptBtn;
    Button scanCountBtn;
    EditText scanCountInput;
    Button scanDurationBtn;
    EditText scanDurationInput;
    Button findBtn;
    Switch persistentSw;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        setTitle("UVL Scan 1.4.2");

        rootLayout = findViewById(R.id.rootLayout);
        textView = findViewById(R.id.textView);
        handshakeBtn = findViewById(R.id.handshakeBtn);
        versionBtn = findViewById(R.id.versionBtn);
        scanBtn = findViewById(R.id.scanBtn);
        scanExtendedBtn = findViewById(R.id.scanExtendedBtn);
        scanOnBtn = findViewById(R.id.scanOnBtn);
        scanOffBtn = findViewById(R.id.scanOffBtn);
        interruptBtn = findViewById(R.id.interruptBtn);
        scanCountBtn = findViewById(R.id.scanCountBtn);
        scanCountInput = findViewById(R.id.scanCountInput);
        scanDurationBtn = findViewById(R.id.scanDurationBtn);
        scanDurationInput = findViewById(R.id.scanDurationInput);
        findBtn = findViewById(R.id.findBtn);
        persistentSw = findViewById(R.id.persistentSw);
        controls.add(handshakeBtn);
        controls.add(versionBtn);
        controls.add(scanBtn);
        controls.add(scanExtendedBtn);
        controls.add(scanOnBtn);
        controls.add(scanOffBtn);
        controls.add(interruptBtn);
        controls.add(scanCountBtn);
        controls.add(scanCountInput);
        controls.add(scanDurationBtn);
        controls.add(scanDurationInput);
        controls.add(findBtn);
        controls.add(persistentSw);


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

        versionBtn.setOnClickListener(view -> {
            new Thread(() -> {
                String ver = version(bluetoothSocket);
                log(ver != null ? "Version: " + ver : "Version request failed");
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

        scanOffBtn.setOnClickListener(view -> {
            waitingForResponse = false;
            new Thread(() -> {
                boolean ok = scanOff(bluetoothSocket);
                log(ok ? "ScanOff ok" : "ScanOff failed");
            }).start();
        });

        interruptBtn.setOnClickListener(view -> {
            waitingForResponse = false;
            new Thread(() -> {
                boolean ok = interrupt(bluetoothSocket);
                log(ok ? "Interrupt ok" : "Interrupt failed");
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

        scanDurationBtn.setOnClickListener(view -> {
            log("Scanning...");
            new Thread(() -> {
                String res = scanDuration(bluetoothSocket);
                if (res == null) {
                    log("Scanned nothing");
                    return;
                }
            }).start();
        });

        findBtn.setOnClickListener(view -> {
            log("Searching...");
            new Thread(() -> {
                String res = find(bluetoothSocket);
                if (res == null)
                    log("Found nothing");
            }).start();
        });

        scanExtendedBtn.setOnClickListener(view -> {
            log("Scanning...");
            new Thread(() -> {
                String res = scanExtended(bluetoothSocket);
                if (res == null)
                    log("Scanned nothing");
            }).start();
        });

        scanExtendedBtn.setOnLongClickListener(view -> {
            scanBtn.callOnClick();
            return true;
        });

        scanCountInput.setOnLongClickListener(view -> {
            scanCountInput.setText("inf");
            return false;
        });

        scanDurationInput.setOnLongClickListener(view -> {
            scanDurationInput.setText("inf");
            return false;
        });

        textView.setOnLongClickListener(view -> {
            new AlertDialog.Builder(this)
                    .setMessage("Clear console?")
                    .setPositiveButton("YES",(dialogInterface, i) ->
                        textView.setText("Cleared\r\n"))
                    .setNegativeButton("CANCEL", null)
                    .show();
            return true;
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

        log("Created RFCOMM");

        try {
            bluetoothSocket.connect();
            log("Connected");
        } catch (IOException e) {
            log("Cant connect to socket");
            return null;
        }

        return bluetoothSocket;
    }

    void disconnectDevice(BluetoothSocket bluetoothSocket) {
        try {
            bluetoothSocket.close();
            log("Closed");
        } catch (IOException e) {
            log("Cant close the socket");
        }
    }

    boolean handshake(BluetoothSocket socket) {
        return commandBasic(socket, "AT", 1000);
    }

    String version(BluetoothSocket socket) {
        return command(socket, "AT+VERSION", "OK", 1000);
    }

    boolean scanOn(BluetoothSocket socket) {
        command(socket, "AT+SCAN=1", "OK", Integer.MAX_VALUE);
        return true;
    }

    boolean scanOff(BluetoothSocket socket) {
        return commandBasic(socket, "AT+SCAN=0", 1000);
    }

    boolean interrupt(BluetoothSocket socket) {
        return commandBasic(socket, "AT+INTERRUPT", 1000);
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

    String scanDuration(BluetoothSocket socket) {
        String text = scanDurationInput.getText().toString();
        Integer number;
        try {
            number = Integer.valueOf(text);
        } catch (NumberFormatException err) {
            log("\"" + text + "\"", "is not a number");
            return null;
        }
        return command(socket, "AT+SCAN?DURATION=" + number, "\r\n\r\nOK", number + 1000);
    }

    String find(BluetoothSocket socket) {
        StringBuilder sb = new StringBuilder("AT+FIND");
        if (persistentSw.isChecked())
            sb.append("?PERSISTENT");
        else
            sb.append("?BEST");
        appendParam(sb, "COUNT", scanCountInput.getText().toString());
        Integer duration = appendParam(sb, "DURATION", scanDurationInput.getText().toString());
        int waitForMs = waitMsFromParam(duration);
        return command(socket, sb.toString(), "\r\nOK", waitForMs);
    }

    String scanExtended(BluetoothSocket socket) {
        StringBuilder sb = new StringBuilder("AT+SCAN");
        appendParam(sb, "COUNT", scanCountInput.getText().toString());
        Integer duration = appendParam(sb, "DURATION", scanDurationInput.getText().toString());
        int waitForMs = waitMsFromParam(duration);
        return command(socket, sb.toString(), "\r\nOK", waitForMs);
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
        //runOnUiThread(r);
        mainLooperHandler.post(r);
        //new Handler(Looper.getMainLooper()).post(r);
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

    Integer appendParam(StringBuilder sb, String name, String value) {
        boolean first = sb.indexOf("?") == -1;
        if (value.equals("inf")) {
            sb.append(first ? '?' : '&')
              .append(name)
              .append("=inf");
            return Integer.MAX_VALUE;
        } else try {
            int number = Integer.valueOf(value);
            sb.append(first ? '?' : '&')
              .append(name)
              .append("=")
              .append(number);
            return number;
        } catch (NumberFormatException err) {
            return null;
        }
    }

    int waitMsFromParam(Integer param) {
        if (param == null)
            return 1000;
        else if (param == Integer.MAX_VALUE)
            return param;
        else
            return param + 1000;
    }
}