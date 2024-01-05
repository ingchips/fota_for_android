package com.ingchips.fota

import android.Manifest
import android.app.Activity
import android.bluetooth.*
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.channels.Channel
import java.util.*

/**
 * a wrapper of Android BLE APIs featuring synchronized operations
 *
 * Functions:
 *
 * 1. Android BLE permissions (@see permissionsOK, @see requestBlePermissions)
 * 1. Scan (@see startScan)
 * 1. Connection (@see connect, @see disconnect)
 * 1. Profile discover (@see discover)
 * 1. GATT operations (@see readCharacteristics, @see writeCharacteristics, etc)
 *
 */
class BLEUtil private constructor(private val context: Context) {

    data class ConnectionStateChangeEvent (var gatt: BluetoothGatt, var status: Int, var newState: Int)
    data class ServiceDiscoveredEvent (var gatt: BluetoothGatt, var status: Int)
    data class CharacteristicEvent (var gatt: BluetoothGatt, var characteristic: BluetoothGattCharacteristic, var status: Int)

    data class MtuChangeEvent (var gatt: BluetoothGatt, var mtu: Int, var status: Int)

    interface DevDisconnected {
        fun disconnected(gatt: BluetoothGatt)
    }

    var chConnStateEvents = Channel<ConnectionStateChangeEvent>(10)
    var chServiceDiscoveredEvents = Channel<ServiceDiscoveredEvent>(10)
    var chCharacteristicReadEvents = Channel<CharacteristicEvent>(10)
    var chCharacteristicWriteEvents = Channel<CharacteristicEvent>(10)
    var chMtuChangeEvents = Channel<MtuChangeEvent> (10)

    inner class SimpleGattCallback : BluetoothGattCallback() {
        override fun onPhyUpdate(gatt: BluetoothGatt, txPhy: Int, rxPhy: Int, status: Int) {}
        override fun onPhyRead(gatt: BluetoothGatt, txPhy: Int, rxPhy: Int, status: Int) {}
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            chConnStateEvents.trySend(ConnectionStateChangeEvent(gatt, status, newState))
            if ((onDisconnected != null) && (newState == BluetoothProfile.STATE_DISCONNECTED))
                onDisconnected!!.disconnected(gatt)
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            chServiceDiscoveredEvents.trySend(ServiceDiscoveredEvent(gatt, status))
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            chCharacteristicReadEvents.trySend(CharacteristicEvent(gatt, characteristic, status))
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            Log.d("BLE", String.format("write done: %s: %d", characteristic.uuid, status))
            chCharacteristicWriteEvents.trySend(CharacteristicEvent(gatt, characteristic, status))
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
        }

        override fun onDescriptorRead(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int
        ) {
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int
        ) {
        }

        override fun onReliableWriteCompleted(gatt: BluetoothGatt, status: Int) {}
        override fun onReadRemoteRssi(gatt: BluetoothGatt, rssi: Int, status: Int) {}
        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            chMtuChangeEvents.trySend(MtuChangeEvent(gatt, mtu, status))
        }
        override fun onServiceChanged(gatt: BluetoothGatt) {}
    }

    var gattCallback = SimpleGattCallback()

    private fun checkPermission(): Boolean {
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        ) {

        }
        return true
    }

    interface OnScanResult {
        fun onScanResult(result: ScanResult?)
    }

    private var scanCallback: OnScanResult? = null
    private val scanHandler = Handler(Looper.myLooper()!!)
    private val leScanCallback: ScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            super.onScanResult(callbackType, result)
            if (scanCallback != null) scanCallback!!.onScanResult(result)
        }

        override fun onBatchScanResults(results: List<ScanResult>) {
            super.onBatchScanResults(results)
            for (r in results) onScanResult(0, r)
        }

        override fun onScanFailed(errorCode: Int) {}
    }

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            bluetoothManager = context.getSystemService(
                BluetoothManager::class.java
            )
        } else {
            bluetoothManager =
                context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        }
        bluetoothAdapter = bluetoothManager!!.adapter
        bluetoothLeScanner = bluetoothAdapter!!.bluetoothLeScanner
        if (!bluetoothAdapter!!.isEnabled) {
            Toast.makeText(context, "Please enable Bluetooth and retry.", Toast.LENGTH_LONG)
                .show()
        }
        if (bluetoothLeScanner == null) {
            Toast.makeText(context, "NO BLE DEVICE!", Toast.LENGTH_LONG)
                .show()
        }
    }

    private fun stopScan0() {
        if (scanCallback == null) return
        if (!checkPermission()) return
        bluetoothLeScanner!!.stopScan(leScanCallback)
        scanCallback = null
    }

    private fun startScan0(callback: OnScanResult, durationMilli: Long) {
        if (!checkPermission()) return
        if (scanCallback != null) {
            stopScan()
        }
        scanCallback = callback
        scanHandler.postDelayed({ stopScan() }, durationMilli)
        bluetoothLeScanner!!.startScan(null,
            ScanSettings.Builder().setLegacy(false).build(),
            leScanCallback)
    }

    companion object {
        private var bluetoothManager: BluetoothManager? = null
        private var bluetoothAdapter: BluetoothAdapter? = null
        private var bluetoothLeScanner: BluetoothLeScanner? = null
        private var instance: BLEUtil? = null

        private const val DEFAULT_MTU = 23

        @JvmStatic
        var onDisconnected: DevDisconnected? = null

        @JvmStatic
        fun init(ctx: Context) {
            if (instance != null) return
            val i = BLEUtil(ctx)
            if (bluetoothLeScanner != null) instance = i
        }

        private val BLE_PERMISSIONS = arrayOf(
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION
        )

        @RequiresApi(api = Build.VERSION_CODES.S)
        private val ANDROID_12_BLE_PERMISSIONS = arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.ACCESS_FINE_LOCATION
        )

        @JvmStatic
        fun requestBlePermissions(activity: Activity?, requestCode: Int) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) ActivityCompat.requestPermissions(
                activity!!, ANDROID_12_BLE_PERMISSIONS, requestCode
            ) else ActivityCompat.requestPermissions(
                activity!!, BLE_PERMISSIONS, requestCode
            )
        }

        @JvmStatic
        fun permissionsOK(ctx: Context?): Boolean {
            val items =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) ANDROID_12_BLE_PERMISSIONS else BLE_PERMISSIONS
            for (item in items) if (ContextCompat.checkSelfPermission(
                    ctx!!, Manifest.permission.ACCESS_FINE_LOCATION
                ) !=
                PackageManager.PERMISSION_GRANTED
            ) return false
            return true
        }

        @JvmStatic
        val isReady: Boolean
            get() = instance != null

        @JvmStatic
        fun stopScan() {
            if (!isReady) return
            instance!!.stopScan0()
        }

        @JvmStatic
        fun startScan(callback: OnScanResult, durationMilli: Long) {
            if (!isReady) return
            instance!!.startScan0(callback, durationMilli)
        }

        @JvmStatic
        val isScanning: Boolean
            get() = if (instance != null) instance!!.scanCallback != null else false

        suspend fun connect(device: BluetoothDevice): BluetoothGatt? {
            if (!isReady) return null
            if (!instance!!.checkPermission()) return null

            device.connectGatt(
                instance!!.context, false, instance!!.gattCallback)

            val r = instance!!.chConnStateEvents.receive()
            return if (r.newState == BluetoothProfile.STATE_CONNECTED) r.gatt else null
        }

        suspend fun disconnect(device: BluetoothDevice): Boolean {
            if (!isReady) return true
            if (!instance!!.checkPermission()) return true

            device.connectGatt(
                instance!!.context, false, instance!!.gattCallback)

            val r = instance!!.chConnStateEvents.receive()
            return r.newState == BluetoothProfile.STATE_DISCONNECTED
        }

        suspend fun discover(gatt: BluetoothGatt): Boolean {
            if (!isReady) return true
            if (!instance!!.checkPermission()) return true

            gatt.discoverServices()

            val r = instance!!.chServiceDiscoveredEvents.receive()
            return r.status == 0
        }

        suspend fun requestMtu(gatt: BluetoothGatt, mtu: Int): Int {
            if (!isReady) return DEFAULT_MTU
            if (!instance!!.checkPermission()) return DEFAULT_MTU

            gatt.requestMtu(mtu)

            val r = instance!!.chMtuChangeEvents.receive()
            return if (r.status == 0) r.mtu else DEFAULT_MTU
        }

        fun getCharacteristics(
            gatt: BluetoothGatt,
            service: String?,
            chars: Array<String>
        ): Hashtable<String, BluetoothGattCharacteristic> {
            val r = Hashtable<String, BluetoothGattCharacteristic>()
            val s = gatt.getService(UUID.fromString(service)) ?: return r
            val gattCharacteristics = s.characteristics
            for (uuid in chars) {
                for (c in gattCharacteristics) if (c.uuid == UUID.fromString(uuid)) r[uuid] = c
            }
            return r
        }

        suspend fun readCharacteristics(
            gatt: BluetoothGatt,
            c: BluetoothGattCharacteristic?
        ): Boolean {
            if (!isReady) return false
            if (!instance!!.checkPermission()) return false

            gatt.readCharacteristic(c)

            val r = instance!!.chCharacteristicReadEvents.receive()
            return r.status == 0
        }

        suspend fun writeCharacteristics(
            gatt: BluetoothGatt,
            c: BluetoothGattCharacteristic?,
            d: ByteArray
        ): Boolean {
            if (!isReady) return false
            if (!instance!!.checkPermission()) return false

            Log.d("BLE", String.format("write %s ", c!!.uuid))

            c.value = d
            gatt.writeCharacteristic(c)

            val r = instance!!.chCharacteristicWriteEvents.receive()
            Log.d("BLE", String.format("chCharacteristicWriteEvents %d ", r.status))
            return r.status == 0
        }
    }
}