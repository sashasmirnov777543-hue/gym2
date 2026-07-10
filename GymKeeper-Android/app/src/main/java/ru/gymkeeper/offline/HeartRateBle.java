package ru.gymkeeper.offline;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelUuid;

import java.util.Collections;
import java.util.UUID;

/** Standard BLE Heart Rate Service client for Huawei HR Data Broadcasts. */
final class HeartRateBle {
    interface Listener {
        void onState(String status, Integer bpm, String deviceName, String error);
    }

    private static final UUID HEART_RATE_SERVICE = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb");
    private static final UUID HEART_RATE_MEASUREMENT = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb");
    private static final UUID CLIENT_CONFIG = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    private final Activity activity;
    private final Listener listener;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private BluetoothLeScanner scanner;
    private BluetoothGatt gatt;
    private boolean scanning;
    private String deviceName;

    HeartRateBle(Activity activity, Listener listener) {
        this.activity = activity;
        this.listener = listener;
    }

    String[] requiredPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return new String[]{Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT};
        }
        return new String[]{Manifest.permission.ACCESS_FINE_LOCATION};
    }

    boolean hasPermissions() {
        for (String permission : requiredPermissions()) {
            if (activity.checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) return false;
        }
        return true;
    }

    void start() {
        if (!hasPermissions()) {
            emit("error", null, null, "Разрешите поиск Bluetooth-устройств");
            return;
        }
        BluetoothManager manager = (BluetoothManager) activity.getSystemService(Context.BLUETOOTH_SERVICE);
        BluetoothAdapter adapter = manager == null ? null : manager.getAdapter();
        if (adapter == null) {
            emit("error", null, null, "Bluetooth LE не поддерживается");
            return;
        }
        if (!adapter.isEnabled()) {
            emit("error", null, null, "Включите Bluetooth на телефоне");
            return;
        }
        stop();
        scanner = adapter.getBluetoothLeScanner();
        if (scanner == null) {
            emit("error", null, null, "Не удалось запустить поиск Bluetooth");
            return;
        }
        emit("connecting", null, null, null);
        ScanFilter filter = new ScanFilter.Builder().setServiceUuid(new ParcelUuid(HEART_RATE_SERVICE)).build();
        ScanSettings settings = new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build();
        scanning = true;
        scanner.startScan(Collections.singletonList(filter), settings, scanCallback);
        handler.postDelayed(scanTimeout, 20000L);
    }

    void stop() {
        handler.removeCallbacks(scanTimeout);
        if (scanning && scanner != null && hasPermissions()) {
            try { scanner.stopScan(scanCallback); } catch (Exception ignored) {}
        }
        scanning = false;
        scanner = null;
        if (gatt != null) {
            try { gatt.disconnect(); } catch (Exception ignored) {}
            try { gatt.close(); } catch (Exception ignored) {}
            gatt = null;
        }
        deviceName = null;
    }

    private final Runnable scanTimeout = () -> {
        if (!scanning) return;
        stopScanSafely();
        scanning = false;
        emit("error", null, null, "Часы не найдены. Включите на них «Трансляцию пульса»");
    };

    private void stopScanSafely() {
        if (scanner != null && hasPermissions()) {
            try { scanner.stopScan(scanCallback); } catch (Exception ignored) {}
        }
    }

    private final ScanCallback scanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            connect(result.getDevice());
        }

        @Override
        public void onScanFailed(int errorCode) {
            scanning = false;
            emit("error", null, null, "Ошибка поиска Bluetooth: " + errorCode);
        }
    };

    private void connect(BluetoothDevice device) {
        if (!scanning || !hasPermissions()) return;
        scanning = false;
        handler.removeCallbacks(scanTimeout);
        try { scanner.stopScan(scanCallback); } catch (Exception ignored) {}
        try { deviceName = device.getName(); } catch (SecurityException ignored) { deviceName = null; }
        if (deviceName == null || deviceName.isEmpty()) deviceName = "Huawei Watch GT 5 Pro";
        gatt = device.connectGatt(activity, false, gattCallback, BluetoothDevice.TRANSPORT_LE);
    }

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt current, int status, int newState) {
            if (status == BluetoothGatt.GATT_SUCCESS && newState == BluetoothProfile.STATE_CONNECTED) {
                current.discoverServices();
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                emit("idle", null, deviceName, "Пульс отключён");
            } else if (status != BluetoothGatt.GATT_SUCCESS) {
                emit("error", null, deviceName, "Ошибка подключения к часам: " + status);
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt current, int status) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                emit("error", null, deviceName, "Не удалось прочитать датчик пульса");
                return;
            }
            BluetoothGattService service = current.getService(HEART_RATE_SERVICE);
            BluetoothGattCharacteristic characteristic = service == null ? null : service.getCharacteristic(HEART_RATE_MEASUREMENT);
            if (characteristic == null) {
                emit("error", null, deviceName, "Часы не транслируют стандартный профиль пульса");
                return;
            }
            current.setCharacteristicNotification(characteristic, true);
            BluetoothGattDescriptor descriptor = characteristic.getDescriptor(CLIENT_CONFIG);
            if (descriptor == null) {
                emit("error", null, deviceName, "Уведомления пульса недоступны");
                return;
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                current.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
            } else {
                descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                current.writeDescriptor(descriptor);
            }
            emit("connected", null, deviceName, null);
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt current, BluetoothGattCharacteristic characteristic) {
            parseAndEmit(characteristic.getValue());
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt current, BluetoothGattCharacteristic characteristic, byte[] value) {
            parseAndEmit(value);
        }
    };

    private void parseAndEmit(byte[] value) {
        if (value == null || value.length < 2) return;
        int flags = value[0] & 0xff;
        int bpm;
        if ((flags & 0x01) != 0 && value.length >= 3) bpm = (value[1] & 0xff) | ((value[2] & 0xff) << 8);
        else bpm = value[1] & 0xff;
        if (bpm >= 25 && bpm <= 250) emit("connected", bpm, deviceName, null);
    }

    private void emit(String status, Integer bpm, String name, String error) {
        handler.post(() -> listener.onState(status, bpm, name, error));
    }
}
