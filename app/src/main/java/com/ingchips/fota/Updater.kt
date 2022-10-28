package com.ingchips.fota

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.util.Log
import kotlinx.coroutines.*
import java.lang.System.arraycopy
import java.util.*

/**
 * This class is the OTA executor, which takes in an `PlanBuilder.Plan` to `doUpdate`.
 *
 * Usage:
 *
 * 1. Create an instance of this class in a new `Thread`;
 * 1. Call `doUpdate` to update the device.
 */
class Updater
/**
 * Create an instance
 * @param device            device to be connected and updated
 * @param showProgress      callback to show message and progress
 * @param onPrepared        callback when everything is ready
 * @param runUiFunc         tool function for executing code on UI thread
 */(
    private var device: BluetoothDevice,
    showProgress: ProgressMsg,
    onPrepared: OnStepListener,
    updateTopPromptSecure: UpdateTopPromptSecure,
    runUiFunc: GeneralFuncRunner
) {
    private val SERVICE_NAME = "INGChips FOTA Service"
    private val GUID_SERVICE = "3345c2f0-6f36-45c5-8541-92f56728d5f3"
    private val GUID_CHAR_OTA_VER = "3345c2f1-6f36-45c5-8541-92f56728d5f3"
    private val GUID_CHAR_OTA_CTRL = "3345c2f2-6f36-45c5-8541-92f56728d5f3"
    private val GUID_CHAR_OTA_DATA = "3345c2f3-6f36-45c5-8541-92f56728d5f3"
    private val GUID_CHAR_OTA_PUBKEY = "3345c2f4-6f36-45c5-8541-92f56728d5f3"

    private val  OTA_CTRL_STATUS_DISABLED: Byte = 0
    private val  OTA_CTRL_STATUS_OK: Byte = 1
    private val  OTA_CTRL_STATUS_ERROR: Byte = 2
    private val  OTA_CTRL_STATUS_WAIT_DATA: Byte = 3

    private val  OTA_CTRL_START: Byte = (0xAA).toByte() // param: no
    private val  OTA_CTRL_PAGE_BEGIN: Byte = (0xB0).toByte() // param: page address, following DATA contains the data
    private val  OTA_CTRL_PAGE_END: Byte = (0xB1).toByte() // param: no
    private val  OTA_CTRL_READ_PAGE: Byte = (0xC0).toByte() // param: page address
    private val  OTA_CTRL_SWITCH_APP: Byte = (0xD0).toByte() // param: no
    private val  OTA_CTRL_METADATA: Byte = (0xE0).toByte() // param: ota_meta_t
    private val  OTA_CTRL_REBOOT: Byte = (0xFF).toByte() // param: no

    private val  MAX_RETRY = 3
    private val  WAIT_BETWEEN_MTU: Long = 10
    private val  WAIT_BETWEEN_PAGE: Long = 80

    class Version {
        private val major: Int
        private val minor: Int
        private val patch: Int

        constructor(major: Int, minor: Int, patch: Int) {
            this.major = major
            this.minor = minor
            this.patch = patch
        }

        private fun unsignedbyte(b: Byte): Int {
            return if (b < 0) 256 + b else b.toInt()
        }

        constructor(bytes: ByteArray, offset: Int) {
            major = unsignedbyte(bytes[offset + 0]) + (unsignedbyte(bytes[offset + 1]) shl 8)
            minor = unsignedbyte(bytes[offset + 2])
            patch = unsignedbyte(bytes[offset + 3])
        }

        fun compare(b: Version): Int {
            var r = major - b.major
            if (r == 0) r = minor - b.minor
            if (r == 0) r = patch - b.patch
            return r
        }

        override fun toString(): String {
            return if (major >= 0) String.format("%d.%d.%d", major, minor, patch) else "∞"
        }
    }

    class ProductVersion (var platform: Version, var app: Version)

    class BLEDriver (
        private val gatt: BluetoothGatt, private val chCtrl: BluetoothGattCharacteristic,
        private val chData: BluetoothGattCharacteristic
    ) {

        suspend fun WriteCtrl(bytes: ByteArray): Boolean {
            return BLEUtil.writeCharacteristics(gatt, chCtrl, bytes)
        }

        suspend fun ReadCtrl(): ByteArray? {
            val r = BLEUtil.readCharacteristics(gatt, chCtrl)
            return if (r) chCtrl.value!! else null
        }

        suspend fun WriteData(bytes: ByteArray): Boolean {
            return BLEUtil.writeCharacteristics(gatt, chData, bytes)
        }
    }

    interface ProgressMsg {
        fun onProgressMsg(progress: Int, msg: String?)
    }

    interface GeneralFuncRunner {
        fun run(k: java.lang.Runnable)
    }

    interface UpdateTopPromptSecure {
        fun update(b: Boolean)
    }

    private var keyUtils: KeyUtils =
        KeyUtils()
    private var showProgress: ProgressMsg? = showProgress
    private var ready = false
    private var gatt: BluetoothGatt? = null
    var devVer: ProductVersion? = null
    private var onPrepared: OnStepListener? = onPrepared
    private var updateTopPromptSecure: UpdateTopPromptSecure? = updateTopPromptSecure
    private var runUi: GeneralFuncRunner? = runUiFunc
    private var driver: BLEDriver? = null
    private var mtu: Int = 0
    private var totalBytes: Int = 0
    private var currentBytes: Int = 0
    private var isSecureOTA: Boolean = false
    private fun setCurrentBytes(value: Int) {
        currentBytes = value
        updateProgress()
    }

    private fun showMsg(s: String) {
        if ((runUi != null) && (showProgress != null)) {
            runUi!!.run(Runnable {
                    showProgress!!.onProgressMsg(-1, s)
            })
        }
    }

    private fun updateSecurePrompt(b: Boolean) {
        if ((runUi != null) && (updateTopPromptSecure != null)) {
            runUi!!.run(Runnable {
                updateTopPromptSecure!!.update(b);
            })
        }
    }

    private fun updateProgress() {
        if ((runUi != null) && (showProgress != null)) {
            runUi!!.run(Runnable {
                val prog = 100 * currentBytes  / totalBytes
                showProgress!!.onProgressMsg(prog, null)
            })
        }
    }

    interface OnStepListener {
        fun onSetp(updater: Updater?)
    }

    init {
        prepare()
    }

    private fun callOnPrepared() {
        showMsg("version confirmed")

        if ((runUi != null) && (onPrepared != null)) {
            runUi!!.run(java.lang.Runnable {
                onPrepared!!.onSetp(this)
            })
        }
    }

    private fun prepare() = runBlocking {
        // let UI update
        delay(50)
        prepare0()
    }

    /**
     * Disconnect device
     */
    fun abort() {
        if (gatt != null) {
            gatt!!.disconnect()
            gatt!!.close()
            gatt = null
        }
    }

    /**
     * Exchange the session key
     */
    private suspend fun exchangeKey(chPubKey: BluetoothGattCharacteristic): Boolean {
        if (!BLEUtil.readCharacteristics(gatt!!, chPubKey)) {
            return false;
        }
        val pk = chPubKey!!.value
        keyUtils.peer_pk = pk

        val sig = keyUtils.signData(keyUtils.root_sk, keyUtils.session_pk)
        if (!BLEUtil.writeCharacteristics(gatt!!, chPubKey, keyUtils.session_pk + sig))
            return false
        val r = ReadStatus() != OTA_CTRL_STATUS_ERROR
        if (r)
        {
            keyUtils.shared_secret = KeyUtils.getSharedSecret(keyUtils.session_sk, keyUtils.peer_pk);
            keyUtils.xor_key = KeyUtils.SHA256(keyUtils.shared_secret);
            keyUtils.is_secure_fota = true;
        }
        return r;
    }

    private suspend fun prepare0() {
        showMsg("connecting to " + device.address + " ...")
        gatt = BLEUtil.connect(device)
        if (gatt == null) {
            showMsg("connection failed")
            return
        }
        mtu = BLEUtil.requestMtu(gatt!!,512) - 3

        BLEUtil.discover(gatt!!)

        val chars = BLEUtil.getCharacteristics(
            gatt!!,
            GUID_SERVICE, arrayOf(
                GUID_CHAR_OTA_VER,
                GUID_CHAR_OTA_CTRL,
                GUID_CHAR_OTA_DATA,
                GUID_CHAR_OTA_PUBKEY
            )
        )

        if (!chars.containsKey(GUID_CHAR_OTA_VER)
            || !chars.containsKey(GUID_CHAR_OTA_CTRL)
            || !chars.containsKey(GUID_CHAR_OTA_DATA)
        ) {
            showMsg("$SERVICE_NAME is not available")
            return
        }
        if (chars.containsKey(GUID_CHAR_OTA_PUBKEY)) {
            updateSecurePrompt(true);
            showMsg("Secure FOTA")
            isSecureOTA = true;
        } else {
            updateSecurePrompt(false);
            showMsg("Unsecure FOTA")
            isSecureOTA = false;
        }

        driver = BLEDriver(gatt!!, chars[GUID_CHAR_OTA_CTRL]!!, chars[GUID_CHAR_OTA_DATA]!!)

        showMsg("$SERVICE_NAME discovered.")

        if (isSecureOTA) {
            showMsg("exchange session key ...")
            if (!exchangeKey(chars[GUID_CHAR_OTA_PUBKEY]!!)) {
                showMsg("failed to exchange session key")
                return
            }
        }

        val ver = chars[GUID_CHAR_OTA_VER]
        showMsg("query current version ...")

        if (!BLEUtil.readCharacteristics(gatt!!, ver)) {
            showMsg("failed to query version")
            return
        }

        val b = ver!!.value

        devVer = ProductVersion(Version(b, 0), Version(b, 4))

        callOnPrepared()

        ready = true
    }

    private suspend fun ReadStatus(): Byte {
        val r = driver!!.ReadCtrl()
        if ((r == null) || (r.isEmpty()))
            return OTA_CTRL_STATUS_ERROR
        return r[0]
    }

    private suspend fun CheckDevStatus(): Boolean {
        return ReadStatus() == OTA_CTRL_STATUS_OK
    }

    private suspend fun BurnPage(page: ByteArray, address: Long): Boolean {
        if (isSecureOTA) {
            return BurnPageSecure(page, address)
        } else {
            return BurnPageUnsrcure(page, address)
        }
    }

    private suspend fun BurnPageUnsrcure(page: ByteArray, address: Long): Boolean {
        var cmd = byteArrayOf(OTA_CTRL_PAGE_BEGIN, 0, 0, 0, 0)
        Utils.writeU32LE(cmd, 1, address)
        driver!!.WriteCtrl(cmd)
        if (!CheckDevStatus()) return false

        for (i in page.indices step mtu) {
            var block = mtu
            if (i + mtu > page.size) block = page.size - i
            Log.d("BLE", "-->");
            if (!driver!!.WriteData(page.copyOfRange(i, i + block))) {
                Log.e("BLE", "FALSE")
                return false
            }
            Log.d("BLE", "done")
            setCurrentBytes(currentBytes + block)
            delay(WAIT_BETWEEN_MTU)
        }

        cmd = byteArrayOf(OTA_CTRL_PAGE_END, 0, 0, 0, 0)
        Utils.writeU16LE(cmd, 1, page.size.toLong())
        Utils.writeU16LE(cmd, 3, Utils.crc(page).toLong())
        driver!!.WriteCtrl(cmd)
        delay(WAIT_BETWEEN_PAGE)

        while (true) {
            when (ReadStatus()) {
                OTA_CTRL_STATUS_OK -> return true
                OTA_CTRL_STATUS_ERROR -> return false
                else -> {}
            }
        }
    }

    private suspend fun BurnPageSecure(page: ByteArray, address: Long): Boolean {
        val sig = keyUtils.signData(keyUtils.session_sk, page)

        keyUtils.encrypt(page)

        var cmd = byteArrayOf(OTA_CTRL_PAGE_BEGIN, 0, 0, 0, 0)
        Utils.writeU32LE(cmd, 1, address)
        driver!!.WriteCtrl(cmd)
        if (!CheckDevStatus()) return false
        Log.i("Updater", "page start")

        for (i in page.indices step mtu) {
            var block = mtu
            if (i + mtu > page.size) block = page.size - i
            Log.d("BLE", "-->");
            if (!driver!!.WriteData(page.copyOfRange(i, i + block))) {
                Log.e("BLE", "FALSE")
                return false
            }
            Log.d("BLE", "done")
            setCurrentBytes(currentBytes + block)
            delay(WAIT_BETWEEN_MTU)
        }

        cmd = ByteArray(5 + sig.size)
        cmd[0] = OTA_CTRL_PAGE_END
        Utils.writeU16LE(cmd, 1, page.size.toLong())
        Utils.writeU16LE(cmd, 3, Utils.crc(page).toLong())
        arraycopy(sig, 0, cmd, 5, sig.size)
        driver!!.WriteCtrl(cmd)
        delay(WAIT_BETWEEN_PAGE)

        Log.i("Updater", "page end")

        while (true) {
            when (ReadStatus()) {
                OTA_CTRL_STATUS_OK -> return true
                OTA_CTRL_STATUS_ERROR -> return false
                else -> {}
            }
        }
    }


    private suspend fun BurnFile(item: UpdateItem, pageSize: Int): Boolean {
        for (i in 0 until item.data.size step pageSize) {
            var err = 0
            var block = pageSize
            if (i + pageSize > item.data.size) block = item.data.size - i
            val page = Arrays.copyOfRange(item.data, i, i + block)
            val backup = currentBytes

            while (err < MAX_RETRY) {
                showMsg(if (err == 0) "burn ${item.name} ..." else "burn ${item.name} (retry #${err} ...")

                if (BurnPage(page, item.writeAddr + i)) {
                    err = 0
                    break
                }
                setCurrentBytes(backup)
                err++
            }

            if (err > 0)
                return false

            updateProgress()
        }

        return true
    }

    private suspend fun BurnFiles(plan: PlanBuilder.Plan): Boolean {
        for (f in plan.items)
            if (!BurnFile(f, plan.pageSize)) return false
        return true
    }

    private suspend fun BurnMetaData(item: UpdateItem, manualReboot: Boolean): Boolean {
        if (isSecureOTA) {
            return BurnMetaDataSecure(item, manualReboot);
        } else {
            return BurnMetaDataUnsecure(item, manualReboot);
        }
    }

    private suspend fun BurnMetaDataUnsecure(item: UpdateItem, manualReboot: Boolean): Boolean {
        showMsg("burn ${item.name}")

        val cmd = ByteArray(1 + item.data.size)
        cmd[0] = OTA_CTRL_METADATA
        arraycopy(item.data, 0, cmd, 1, item.data.size)

        if (!driver!!.WriteCtrl(cmd)) return false
        return if (manualReboot) CheckDevStatus() else true
    }

    private suspend fun BurnMetaDataSecure(item: UpdateItem, manualReboot: Boolean): Boolean {
        showMsg("burn ${item.name}")

        val data = ByteArray(item.data.size - 2);
        arraycopy(item.data, 2, data, 0, data.size)

        var sig = keyUtils.signData(keyUtils.session_sk, data)

        keyUtils.encrypt(data);

        val cmd = ByteArray(1 + sig.size + 2 + data.size)
        cmd[0] = OTA_CTRL_METADATA
        arraycopy(sig, 0, cmd, 1, sig.size)
        Utils.writeU16LE(cmd, 1 + sig.size,  Utils.crc(data).toLong())
        arraycopy(data, 0, cmd, 1 + sig.size + 2, data.size)

        if (!driver!!.WriteCtrl(cmd)) return false
        return if (manualReboot) CheckDevStatus() else true
    }


    private suspend fun doUpdate2(plan: PlanBuilder.Plan) {
        showMsg("enabling FOTA")
        if (!driver!!.WriteCtrl(byteArrayOf(OTA_CTRL_START, 0, 0, 0, 0)) || !CheckDevStatus())
            throw Exception("failed to enable FOTA")
        showMsg("FOTA successfully enabled")
        if (!BurnFiles(plan))
            throw Exception("burn failed")
        if (!BurnMetaData(plan.metaData, plan.manualReboot))
            throw Exception("metadata failed")
        showMsg("FOTA burn complete, reboot...")
        if (plan.manualReboot)
            driver!!.WriteCtrl(byteArrayOf( OTA_CTRL_REBOOT ))
        BLEUtil.disconnect(gatt!!.device)
    }

    private fun doUpdate1(plan: PlanBuilder.Plan) = runBlocking {
        try {
            totalBytes = 0
            currentBytes = 0
            for (f in plan.items) totalBytes += f.data.size

            doUpdate2(plan)
        } catch (e: java.lang.Exception) {
            showMsg(e.message!!)
        }
    }

    /**
     * Do the update
     * @param plan          Update plan
     */
    fun doUpdate(plan: PlanBuilder.Plan) {
        Thread {
            doUpdate1(plan)
        }.start()
    }
}