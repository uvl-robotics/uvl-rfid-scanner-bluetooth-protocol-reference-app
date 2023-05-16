package ai.uvl.uvl_rfid_scanner_bluetooth_protocol_reference_app;

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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
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
    TextView heartbeatBtn;

    Thread parserThread = null;
    Handler  heartbeatHandler = new Handler();
    boolean heartbeatLogEnabled = false;


    void setHeartbeatStatusGray() { heartbeatBtn.setText("⚫"); }
    void setHeartbeatStatusGreen() { heartbeatBtn.setText("\uD83D\uDFE2"); }
    void setHeartbeatStatusRed() { heartbeatBtn.setText("\uD83D\uDD34"); }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        setTitle("UVL Scan 1.5.0");

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
        heartbeatBtn = findViewById(R.id.heartbeatBtn);
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
        controls.add(heartbeatBtn);

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

        handshakeBtn.setOnClickListener(view -> handshake());
        versionBtn.setOnClickListener(view -> version());
        scanOnBtn.setOnClickListener(view -> scanOn());
        scanOffBtn.setOnClickListener(view -> scanOff());
        scanOffBtn.setOnClickListener(view -> scanOff());
        interruptBtn.setOnClickListener(view -> interrupt());
        scanCountBtn.setOnClickListener(view -> scanCount());
        scanDurationBtn.setOnClickListener(view -> scanDuration());
        findBtn.setOnClickListener(view -> find());
        scanExtendedBtn.setOnClickListener(view -> scanExtended());
        scanExtendedBtn.setOnLongClickListener(view -> {scan(); return true; });
        scanBtn.setOnClickListener(view -> scan());

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

        heartbeatBtn.setOnClickListener(view -> {
            heartbeatLogEnabled = !heartbeatLogEnabled;
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
                    stop_parser();
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
                        run_parser();
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

    void handshake() { sendCommand("AT"); }

    void version() {
        sendCommand("AT+VERSION");
    }

    void scanOn() {
        sendCommand("AT+SCAN=1");
    }

    void scanOff() {
        sendCommand("AT+SCAN=0");
    }

    void interrupt() {
        sendCommand("AT+INTERRUPT");
    }

    void scan() {
        sendCommand("AT+SCAN?");
    }

    void scanCount() {
        String text = scanCountInput.getText().toString();
        Integer number;
        try {
            number = Integer.valueOf(text);
            sendCommand("AT+SCAN?COUNT=" + number);
        } catch (NumberFormatException err) {
            log("\"" + text + "\"", "is not a number");
        }
    }

    void scanDuration() {
        String text = scanDurationInput.getText().toString();
        Integer number;
        try {
            number = Integer.valueOf(text);
            sendCommand("AT+SCAN?DURATION=" + number);
        } catch (NumberFormatException err) {
            log("\"" + text + "\"", "is not a number");
        }
    }

    void find() {
        StringBuilder sb = new StringBuilder("AT+FIND");
        if (persistentSw.isChecked())
            sb.append("?PERSISTENT");
        else
            sb.append("?BEST");
        appendParam(sb, "COUNT", scanCountInput.getText().toString());
        Integer duration = appendParam(sb, "DURATION", scanDurationInput.getText().toString());
        sendCommand(sb.toString());
    }

    void scanExtended() {
        StringBuilder sb = new StringBuilder("AT+SCAN");
        appendParam(sb, "COUNT", scanCountInput.getText().toString());
        Integer duration = appendParam(sb, "DURATION", scanDurationInput.getText().toString());
        sendCommand(sb.toString());
    }

    void sendCommand(String command) {
        try {
            OutputStream os = bluetoothSocket.getOutputStream();
            os.write((command + "\r\n").getBytes());
            os.flush();
            log(">", command);
        } catch (IOException e) {
            log("Cant use the socket");
        }
    }


    boolean parserOnline = false;
    @SuppressLint("MissingPermission")
    void run_parser() {
        log("Running parser...");

        heartbeatHandler.removeCallbacksAndMessages(null);
        parserThread = new Thread(() -> {
            log("Started thread");
            heartbeatHandler.removeCallbacksAndMessages(null);
            runUI(this::setHeartbeatStatusRed);
            parserOnline = true;
            try {
                InputStream is = bluetoothSocket.getInputStream();
                InputStreamReader isr = new InputStreamReader(is, "UTF-8");
                BufferedReader br = new BufferedReader(isr);
                while (parserOnline) {
                    if (br.ready()) {
                        String line = br.readLine();
                        if (line.equals("+HB")) {
                            heartbeatHandler.removeCallbacksAndMessages(null);
                            runUI(this::setHeartbeatStatusGreen);
                            heartbeatHandler.postDelayed(() -> {
                                runUI(this::setHeartbeatStatusRed);
                            }, 1000);
                            if (heartbeatLogEnabled)
                                log("<", line);
                        } else {
                            log("<", line);
                        }
                    }
                }
                log("Quit loop");
            } catch (IOException e) {
                log("Cant use the socket");
            }
        });
        parserThread.start();
    }
    void stop_parser() {
        log("Stopping parser");
        heartbeatHandler.removeCallbacksAndMessages(null);
        runUI(this::setHeartbeatStatusGray);
        parserOnline = false;
        try {
            parserThread.join();
            log("Parser stopped");
        } catch (InterruptedException exc) {
            log("Error stopping parser thread", exc.toString());
        }
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
            for (String more: args) {
                textView.append(more == null ? "null" : more);
            }
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