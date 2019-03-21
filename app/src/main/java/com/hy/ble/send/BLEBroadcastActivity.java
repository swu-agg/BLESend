package com.hy.ble.send;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattServerCallback;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.ParcelUuid;
import android.support.annotation.Nullable;
import android.util.Log;
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.Toast;

import com.tbruyelle.rxpermissions2.RxPermissions;
import com.trello.rxlifecycle2.android.ActivityEvent;
import com.trello.rxlifecycle2.components.support.RxAppCompatActivity;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import io.reactivex.android.schedulers.AndroidSchedulers;

/**
 * <pre>
 *     author    : Agg
 *     blog      : https://blog.csdn.net/Agg_bin
 *     time      : 2019/03/15
 *     desc      : BLE模拟设备，周边
 *     reference :
 * </pre>
 */
public class BLEBroadcastActivity extends RxAppCompatActivity {

    private static final String TAG = BLEBroadcastActivity.class.getSimpleName();
    private static final ParcelUuid PARCEL_UUID_1 = ParcelUuid.fromString("0000ccc0-0000-1000-8000-00805f9b34fb");
    private static final ParcelUuid PARCEL_UUID_2 = ParcelUuid.fromString("0000bbb0-0000-1000-8000-00805f9b34fb");
    private static final UUID SERVICE_UUID_1 = UUID.fromString("0000ccc0-0000-1000-8000-00805f9b34fb");
    private static final UUID SERVICE_UUID_2 = UUID.fromString("0000bbb0-0000-1000-8000-00805f9b34fb");
    private static final UUID CHARACTERISTIC_UUID_1 = UUID.fromString("0000ccc1-0000-1000-8000-00805f9b34fb");
    private static final UUID CHARACTERISTIC_UUID_2 = UUID.fromString("0000ccc2-0000-1000-8000-00805f9b34fb");
    private static final UUID CHARACTERISTIC_UUID_3 = UUID.fromString("0000bbb1-0000-1000-8000-00805f9b34fb");
    private static final UUID DESCRIPTOR_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
    private static final byte[] BROADCAST_DATA = {0x12, 0x34, 0x56, 0x78};
    private static final int MANUFACTURER_ID = 0xACAC;

    private BluetoothManager bluetoothManager;
    private BluetoothGattServer bluetoothGattServer;
    private BluetoothLeAdvertiser bluetoothLeAdvertiser;
    private List<BluetoothDevice> bluetoothDeviceList = new ArrayList<>(); // 建立通知关系的device队列，当发送通知时，通知所有设备。

    @BindView(R.id.et_info)
    EditText etInfo;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_blebroadcast);
        ButterKnife.bind(this);
        etInfo.setImeOptions(EditorInfo.IME_ACTION_SEND);
        etInfo.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND || (event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                sendInfo(etInfo.getText().toString().trim());
                hideSoftInput();
                etInfo.setText("");
                return true;
            }
            return false;
        });
        askPermission();
    }

    @SuppressLint("CheckResult")
    private void askPermission() {
        if (Build.VERSION.SDK_INT >= 23) {
            new RxPermissions(this).request(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION})
                    .compose(bindUntilEvent(ActivityEvent.DESTROY))
                    .take(1)
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(aBoolean -> {
                        if (aBoolean) {
                            isSupportBluetooth4();
                        } else {
                            Toast.makeText(BLEBroadcastActivity.this, "未授予模糊定位权限", Toast.LENGTH_SHORT).show();
                            finish();
                        }
                    });
        } else {
            isSupportBluetooth4();
        }
    }

    private void isSupportBluetooth4() {
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(BLEBroadcastActivity.this, "蓝牙不支持BLE", Toast.LENGTH_SHORT).show();
            finish();
        } else if (!isOpenBluetooth()) {
            Toast.makeText(BLEBroadcastActivity.this, "此硬件平台不支持蓝牙", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private boolean isOpenBluetooth() {
        bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null) {
            return false;
        }
        boolean enable = bluetoothAdapter.enable();// 自动打开蓝牙
        if (!enable) {
            Toast.makeText(this, "请打开蓝牙", Toast.LENGTH_SHORT).show();
            finish();
        } else {
            etInfo.postDelayed(this::setService, 1500); // 等待蓝牙开启后再使用(预计1.5秒以上就可以)
        }
        bluetoothLeAdvertiser = bluetoothAdapter.getBluetoothLeAdvertiser();
        return true;
    }

    private void setService() {
        bluetoothGattServer = bluetoothManager.openGattServer(this, bluetoothGattServerCallback);
        // 可写ccc1
        BluetoothGattCharacteristic bluetoothGattCharacteristic1 = new BluetoothGattCharacteristic(CHARACTERISTIC_UUID_1, BluetoothGattCharacteristic.PROPERTY_WRITE, BluetoothGattCharacteristic.PERMISSION_WRITE);
        // 可读ccc2
        BluetoothGattCharacteristic bluetoothGattCharacteristic2 = new BluetoothGattCharacteristic(CHARACTERISTIC_UUID_2, BluetoothGattCharacteristic.PROPERTY_READ, BluetoothGattCharacteristic.PERMISSION_READ);
        // service1ccc0
        BluetoothGattService service1 = new BluetoothGattService(SERVICE_UUID_1, BluetoothGattService.SERVICE_TYPE_PRIMARY);
        service1.addCharacteristic(bluetoothGattCharacteristic1);
        service1.addCharacteristic(bluetoothGattCharacteristic2);
        bluetoothGattServer.addService(service1);
        // 可读可写可通知bbb1
        BluetoothGattCharacteristic characteristic3 = new BluetoothGattCharacteristic(CHARACTERISTIC_UUID_3,
                BluetoothGattCharacteristic.PROPERTY_NOTIFY | BluetoothGattCharacteristic.PROPERTY_WRITE | BluetoothGattCharacteristic.PROPERTY_READ,
                BluetoothGattCharacteristic.PERMISSION_WRITE | BluetoothGattCharacteristic.PERMISSION_READ);
        characteristic3.addDescriptor(new BluetoothGattDescriptor(DESCRIPTOR_UUID, BluetoothGattDescriptor.PERMISSION_WRITE));
        // service2bbb0
        final BluetoothGattService service2 = new BluetoothGattService(SERVICE_UUID_2, BluetoothGattService.SERVICE_TYPE_PRIMARY);
        service2.addCharacteristic(characteristic3);
        new Thread(() -> {
            try {
                Thread.sleep(1000);
                bluetoothGattServer.addService(service2);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }).start();
    }

    private final BluetoothGattServerCallback bluetoothGattServerCallback = new BluetoothGattServerCallback() {

        @Override
        public void onConnectionStateChange(BluetoothDevice device, int status, int newState) {
            super.onConnectionStateChange(device, status, newState);
            // 这个device是中央设备， mac地址会 因为 中央（手机）蓝牙重启而变化
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.i(TAG, "连接成功");
                Log.i(TAG, "onConnectionStateChange: " + status + " newState:" + newState + " deviceName:" + device.getName() + " mac:" + device.getAddress());
            }
        }

        @Override
        public void onServiceAdded(int status, BluetoothGattService service) {
            super.onServiceAdded(status, service);
            Log.i(TAG, " onServiceAdded status:" + status + " service:" + service.getUuid().toString());
        }

        @Override
        public void onCharacteristicReadRequest(BluetoothDevice device, int requestId, int offset, BluetoothGattCharacteristic characteristic) {
            super.onCharacteristicReadRequest(device, requestId, offset, characteristic);
            Log.i(TAG, " onCharacteristicReadRequest requestId:" + requestId + " offset:" + offset + " characteristic:" + characteristic.getUuid().toString());
            bluetoothGattServer.sendResponse(device, requestId, 0, offset, "agg coming".getBytes());
        }

        @Override
        public void onCharacteristicWriteRequest(BluetoothDevice device, int requestId, BluetoothGattCharacteristic characteristic, boolean preparedWrite, boolean responseNeeded, int offset, byte[] value) {
            super.onCharacteristicWriteRequest(device, requestId, characteristic, preparedWrite, responseNeeded, offset, value);
            Log.e(TAG, " onCharacteristicWriteRequest requestId:" + requestId + " preparedWrite:" + preparedWrite + " responseNeeded:" + responseNeeded + " offset:" + offset + " value:" + new String(value) + " characteristic:" + characteristic.getUuid().toString());
            runOnUiThread(() -> Toast.makeText(BLEBroadcastActivity.this, "收到请求：" + new String(value), Toast.LENGTH_SHORT).show());
            bluetoothGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value);
        }

        @Override
        public void onDescriptorReadRequest(BluetoothDevice device, int requestId, int offset, BluetoothGattDescriptor descriptor) {
            super.onDescriptorReadRequest(device, requestId, offset, descriptor);
            Log.i(TAG, " onCharacteristicReadRequest requestId:" + requestId + " offset:" + offset + " descriptor:" + descriptor.getUuid().toString());
        }

        int i = 0;

        @Override
        public void onDescriptorWriteRequest(final BluetoothDevice device, int requestId, BluetoothGattDescriptor descriptor, boolean preparedWrite, boolean responseNeeded, int offset, byte[] value) {
            super.onDescriptorWriteRequest(device, requestId, descriptor, preparedWrite, responseNeeded, offset, value);
            Log.i(TAG, " onDescriptorWriteRequest requestId:" + requestId + " preparedWrite:" + preparedWrite + " responseNeeded:" + responseNeeded + " offset:" + offset + " value:" + toHexString(value) + " characteristic:" + descriptor.getUuid().toString());
            bluetoothGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value);

            // 添加到通知列表队列：添加前先移除之前添加的！
            for (BluetoothDevice bluetoothDevice : bluetoothDeviceList) {
                if (bluetoothDevice.getAddress().equals(device.getAddress())) {
                    bluetoothDeviceList.remove(bluetoothDevice);
                    break;
                }
            }
            bluetoothDeviceList.add(device);

            // 循环通知3个数据
            new Thread(() -> {
                while (i < 3) {
                    try {
                        Thread.sleep(1000);
                        notifyData(device, ("通知数据" + i++).getBytes(), false);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }).start();
        }

        @Override
        public void onExecuteWrite(BluetoothDevice device, int requestId, boolean execute) {
            super.onExecuteWrite(device, requestId, execute);
            Log.i(TAG, " onExecuteWrite requestId:" + requestId + " execute:" + execute);
        }

        @Override
        public void onNotificationSent(BluetoothDevice device, int status) {
            super.onNotificationSent(device, status);
            Log.i(TAG, " onNotificationSent status:" + status);
        }

    };

    private AdvertiseCallback advertiseCallback = new AdvertiseCallback() {
        @Override
        public void onStartSuccess(AdvertiseSettings settingsInEffect) {
            super.onStartSuccess(settingsInEffect);
            Toast.makeText(BLEBroadcastActivity.this, "开启BLE广播成功", Toast.LENGTH_SHORT).show();
        }

        @Override
        public void onStartFailure(int errorCode) {
            super.onStartFailure(errorCode);
            Toast.makeText(BLEBroadcastActivity.this, "开启BLE广播失败，errorCode：" + errorCode, Toast.LENGTH_SHORT).show();
        }
    };

    private void sendInfo(String data) {
        Log.e(TAG, "sendInfo: " + data + "，bluetoothDeviceList.size()：" + bluetoothDeviceList.size());
        try {
            for (BluetoothDevice bluetoothDevice : bluetoothDeviceList) {
                boolean notifyData = notifyData(bluetoothDevice, data.getBytes(), false);
                Toast.makeText(this, "通知数据\"" + data + "\"给" + bluetoothDevice.getAddress() + "--------" + notifyData, Toast.LENGTH_LONG).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, "请打开广播连接通信", Toast.LENGTH_SHORT).show();
        }
    }

    private boolean notifyData(final BluetoothDevice device, byte[] value, final boolean confirm) {
        BluetoothGattCharacteristic characteristic = null;
        for (BluetoothGattService service : bluetoothGattServer.getServices()) {
            for (BluetoothGattCharacteristic mCharacteristic : service.getCharacteristics()) {
                if (mCharacteristic.getUuid().equals(CHARACTERISTIC_UUID_3)) {
                    characteristic = mCharacteristic;
                    break;
                }
            }
        }
        if (characteristic != null) {
            characteristic.setValue(value);
            return bluetoothGattServer.notifyCharacteristicChanged(device, characteristic, confirm);
        }
        return false;
    }

    private void hideSoftInput() {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        assert imm != null;
        imm.hideSoftInputFromWindow(etInfo.getWindowToken(), 0); // 强制隐藏键盘
    }

    private AdvertiseSettings createAdvertiseSettings(boolean connectable, int timeoutMillis) {
        // 设置广播的模式，低功耗，平衡和低延迟三种模式：对应  AdvertiseSettings.ADVERTISE_MODE_LOW_POWER  ,ADVERTISE_MODE_BALANCED ,ADVERTISE_MODE_LOW_LATENCY
        // 从左右到右，广播的间隔会越来越短
        return new AdvertiseSettings.Builder().setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                // 设置是否可以连接。广播分为可连接广播和不可连接广播，一般不可连接广播应用在iBeacon设备上，这样APP无法连接上iBeacon设备
                .setConnectable(connectable)
                // 设置广播的信号强度，从左到右分别表示强度越来越强.。
                // 常量有AdvertiseSettings.ADVERTISE_TX_POWER_ULTRA_LOW,ADVERTISE_TX_POWER_LOW,ADVERTISE_TX_POWER_MEDIUM,ADVERTISE_TX_POWER_HIGH
                // 举例：当设置为ADVERTISE_TX_POWER_ULTRA_LOW时，手机1和手机2放在一起，手机2扫描到的rssi信号强度为-56左右；
                // 当设置为ADVERTISE_TX_POWER_HIGH  时， 扫描到的信号强度为-33左右，信号强度越大，表示手机和设备靠的越近。
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                // 设置广播的最长时间，最大值为常量AdvertiseSettings.LIMITED_ADVERTISING_MAX_MILLIS = 180 * 1000;  180秒
                // 设为0时，代表无时间限制会一直广播
                .setTimeout(timeoutMillis)
                .build();
    }

    private AdvertiseData createAdvertiseData(byte[] broadcastData) {
        return new AdvertiseData.Builder()
                .addServiceUuid(PARCEL_UUID_1)
                .addServiceUuid(PARCEL_UUID_2)
                .addServiceData(PARCEL_UUID_1, new byte[]{0x33, 0x33, 0x33, 0x33})
                .addManufacturerData(MANUFACTURER_ID, broadcastData)
                .build();
    }

    public static String toHexString(byte[] byteArray) {
        if (byteArray == null || byteArray.length < 1) return "";
        final StringBuilder hexString = new StringBuilder();
        for (byte aByteArray : byteArray) {
            if ((aByteArray & 0xff) < 0x10)//0~F前面不零
                hexString.append("0");
            hexString.append(Integer.toHexString(0xFF & aByteArray));
        }
        return hexString.toString().toLowerCase();
    }

    @OnClick(R.id.bt_open_broadcast)
    public void openBroadcast() {
        if (bluetoothLeAdvertiser != null) {
            bluetoothLeAdvertiser.stopAdvertising(advertiseCallback);
            bluetoothLeAdvertiser.startAdvertising(createAdvertiseSettings(true, 0), createAdvertiseData(BROADCAST_DATA), advertiseCallback);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (bluetoothLeAdvertiser != null) {
            bluetoothLeAdvertiser.stopAdvertising(advertiseCallback);
            bluetoothLeAdvertiser = null;
        }
    }

}
