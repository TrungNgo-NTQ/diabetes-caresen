package net.huray.caresens

import android.Manifest
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.*
import android.content.pm.PackageManager
import android.os.Binder
import android.os.Handler
import android.os.IBinder
import android.util.Log
import androidx.core.util.forEach
import com.gun0912.tedpermission.PermissionListener
import com.gun0912.tedpermission.TedPermission
import net.huray.caresens.ble.Const
import net.huray.caresens.ble.ExtendedDevice
import net.huray.caresens.ble.GlucoseBleService
import net.huray.caresens.ble.ScannerServiceParser
import net.huray.caresens.ble.Util
import net.huray.caresens.callbacks.BluetoothConnectionCallbacks
import net.huray.caresens.callbacks.BluetoothDataCallbacks
import net.huray.caresens.callbacks.BluetoothInitializeCallbacks
import net.huray.caresens.callbacks.BluetoothScanCallbacks
import net.huray.caresens.const.Consts
import net.huray.caresens.data.DeviceInfo
import net.huray.caresens.enums.ConnectState
import net.huray.caresens.enums.DataReadState
import net.huray.caresens.enums.GlucoseUnit
import net.huray.caresens.enums.ScanState
import java.util.*
import kotlin.collections.ArrayList


open class CaresensBluetoothService : Service() {
    private val TAG = this::class.java.simpleName
    private lateinit var context: Context
    private var mAutoConnect = true

    // Interface
    private var bluetoothInitializeCallbacks: BluetoothInitializeCallbacks? = null
    private var bluetoothConnectionCallbacks: BluetoothConnectionCallbacks? = null
    private var bluetoothDataCallbacks: BluetoothDataCallbacks? = null
    private var bluetoothScanCallbacks: BluetoothScanCallbacks? = null

    private val binder = ServiceBinder()
    private var mGlucoseBleService: GlucoseBleService? = null
    private var mIsBindGlucoseBleService = false
    private var mBluetoothAdapter: BluetoothAdapter? = null
    private var mBluetoothManager: BluetoothManager? = null
    private var mIsScanning = false
    private var mScanCallback: ScanCallback? = null
    private var mDeviceSoftwareVer: Array<String>? = null

    private var glucoseUnit = GlucoseUnit.MG

    private var extendedDevices: ArrayList<ExtendedDevice> = ArrayList()
    private val connectedDeviceInfo by lazy { DeviceInfo() }

    private val mBleUpdateReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action
            val extraData = intent.getStringExtra(Const.INTENT_BLE_EXTRA_DATA)
            when (action) {
                Const.INTENT_BLE_TOTAL_COUNT -> {
                    Log.d(TAG,"INTENT_BLE_TOTAL_COUNT")
                    connectedDeviceInfo.totalCount = extraData?.toInt()
                    bluetoothDataCallbacks?.onRead(DataReadState.DEVICE_INFO_READ_COMPLETE,"",connectedDeviceInfo,null)
                }
                Const.INTENT_BLE_CHAR_GLUCOSE_CONTEXT -> {
                    //##GET TOTAL STORED DATA COUNT
                    Log.d(TAG,"INTENT_BLE_CHAR_GLUCOSE_CONTEXT")
                    requestTotalDataCnt()
                }
                Const.INTENT_BLE_SOFTWARE_VERSION -> {
                    Log.d(TAG,"INTENT_BLE_SOFTWARE_VERSION")
                    connectedDeviceInfo.version = extraData
                }
                Const.INTENT_BLE_SERIAL_NUMBER -> {
                    Log.d(TAG,"INTENT_BLE_SERIAL_NUMBER")
                    connectedDeviceInfo.serialNumber = extraData
                }
                Const.INTENT_BLE_BOND_NONE -> {
                    Log.d(TAG,"INTENT_BLE_BOND_NONE")
                    bluetoothConnectionCallbacks?.onStateChanged(
                        ConnectState.ERROR,
                        "블루투스 연결에 실패했습니다. 블루투스 연결 재시도해주세요",
                        null
                    )
                }
                Const.INTENT_BLE_DEVICE_CONNECTED -> {
                    Log.d(TAG,"INTENT_BLE_DEVICE_CONNECTED")
                    stopScan()
                    connectedDeviceInfo.name = extraData
                    bluetoothConnectionCallbacks?.onStateChanged(
                        ConnectState.CONNECTED, "Device Connected",
                        connectedDeviceInfo
                    )
                }
                Const.INTENT_BLE_DEVICE_DISCONNECTED -> {
                    Log.d(TAG,"INTENT_BLE_DEVICE_DISCONNECTED")
                    bluetoothConnectionCallbacks?.onStateChanged(
                        ConnectState.DISCONNECTED, "Device DisConnected",
                        null
                    )
                }
                Const.INTENT_BLE_ERROR -> {
                    Log.d(TAG,"INTENT_BLE_ERROR")
                    bluetoothDataCallbacks?.onRead(
                        dataReadState = DataReadState.UNKNOWN_ERROR,
                        "", null, null
                    )
                }
                Const.INTENT_BLE_DEVICE_NOT_SUPPORTED -> {
                    Log.d(TAG,"INTENT_BLE_DEVICE_NOT_SUPPORTED")
                    bluetoothDataCallbacks?.onRead(
                        dataReadState = DataReadState.BLE_DEVICE_NOT_SUPPORTED,
                        "", null, null
                    )
                }
                Const.INTENT_BLE_OPERATE_FAILED -> {
                    Log.d(TAG,"INTENT_BLE_OPERATE_FAILED")
                    bluetoothDataCallbacks?.onRead(
                        dataReadState = DataReadState.BLE_OPERATE_FAILED,
                        "", null, null
                    )
                }
                Const.INTENT_BLE_OPERATE_NOT_SUPPORTED -> {
                    Log.d(TAG,"INTENT_BLE_OPERATE_NOT_SUPPORTED")
                    bluetoothDataCallbacks?.onRead(
                        dataReadState = DataReadState.BLE_OPERATE_NOT_SUPPORTED,
                        "", null, null
                    )
                }
                Const.INTENT_BLE_READ_MANUFACTURER -> {
                    Log.d(TAG,"INTENT_BLE_READ_MANUFACTURER")
                }
                Const.INTENT_BLE_READ_SOFTWARE_REV -> {
                    Log.d(TAG,"INTENT_BLE_READ_SOFTWARE_REV")
                }
                Const.INTENT_BLE_TIMESYNC_SUCCESS -> {
                    Log.d(TAG,"INTENT_BLE_TIMESYNC_SUCCESS")
                }
                Const.INTENT_BLE_READ_COMPLETED -> {
                    Log.d(TAG,"INTENT_BLE_READ_COMPLETED")
                    val mRecords = GlucoseBleService.getRecords()

                    mRecords.forEach { key, value ->
                        try {
                            // device version 1.4~ (ketone low added)
                            value.flag_hilow =
                                if (value.flag_ketone == 1
                                    && value.flag_hilow == -1
                                    && mDeviceSoftwareVer!![0].toInt() >= 1
                                    && mDeviceSoftwareVer!![1].toInt() >= 4
                                ) -2
                                else value.flag_hilow
                            if (value.flag_ketone == 1) {
                                value.keton = value.glucoseData / Const.KetoneMultiplier
                            } else {
                                when (glucoseUnit) {
                                    GlucoseUnit.MG -> {
                                    }
                                    GlucoseUnit.MMOL -> {
                                        value.glucoseData =
                                            (Math.round(10 * value.glucoseData / Const.GlucoseUnitConversionMultiplier) / 10.0).toString()
                                                .toDouble()
                                    }
                                }
                            }
                        } catch (e: java.lang.Exception) {
                        }
                    }
                    bluetoothDataCallbacks?.onRead(
                        dataReadState = DataReadState.GlUCOSE_RECORD_READ_COMPLETE,
                        "", connectedDeviceInfo, mRecords
                    )
                }
                Const.INTENT_STOP_SCAN -> {
                }
            }
        }
    }

    private fun addScannedDevice(device: BluetoothDevice, rssi: Int, isBonded: Boolean) {
        val extendedDevice = ExtendedDevice(device,rssi, isBonded)
        val indexInNotBonded = extendedDevices.indexOf(extendedDevice)
        if(indexInNotBonded >= 0) {
            extendedDevices[indexInNotBonded] = extendedDevice
        } else {
            extendedDevices.add(extendedDevice)
        }

        try {
            bluetoothScanCallbacks?.onScan(
                ScanState.SCANNING, "", extendedDevices
            )
        } catch (e: NullPointerException) {
            bluetoothScanCallbacks?.onScan(
                ScanState.FAIL,
                "Scan Start Failed: ${e.message}",
                null
            )
        } catch (e: Exception) {
            bluetoothScanCallbacks?.onScan(
                ScanState.FAIL,
                "Scan Start Failed: ${e.message}",
                null
            )
        }
    }

    fun initialize(
        context: Context,
        bluetoothInitializeCallbacks: BluetoothInitializeCallbacks? = null,
        bluetoothScanCallbacks: BluetoothScanCallbacks? = null,
        bluetoothConnectionCallbacks: BluetoothConnectionCallbacks? = null,
        bluetoothDataCallbacks: BluetoothDataCallbacks? = null,
    ) {
        this.context = context
        this.bluetoothInitializeCallbacks = bluetoothInitializeCallbacks
        this.bluetoothScanCallbacks = bluetoothScanCallbacks
        this.bluetoothConnectionCallbacks = bluetoothConnectionCallbacks
        this.bluetoothDataCallbacks = bluetoothDataCallbacks
    }

    // Manage life cycle of BLE Service
    private val mServiceConnection: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(componentName: ComponentName, service: IBinder) {
            mGlucoseBleService = (service as GlucoseBleService.LocalBinder).service
            bluetoothInitializeCallbacks?.onSuccess()
        }

        override fun onServiceDisconnected(componentName: ComponentName) {
            mGlucoseBleService = null
        }
    }

    private fun makeBleUpdateIntentFilter(): IntentFilter? {
        val intentFilter = IntentFilter()
        intentFilter.addAction(Const.INTENT_BLE_EXTRA_DATA)
        intentFilter.addAction(Const.INTENT_BLE_CONNECT_DEVICE)
        intentFilter.addAction(Const.INTENT_BLE_BOND_NONE)
        intentFilter.addAction(Const.INTENT_BLE_DEVICE_CONNECTED)
        intentFilter.addAction(Const.INTENT_BLE_DEVICE_DISCONNECTED)
        intentFilter.addAction(Const.INTENT_BLE_SERVICE_DISCOVERED)
        intentFilter.addAction(Const.INTENT_BLE_ERROR)
        intentFilter.addAction(Const.INTENT_BLE_DEVICE_NOT_SUPPORTED)
        intentFilter.addAction(Const.INTENT_BLE_OPERATE_FAILED)
        intentFilter.addAction(Const.INTENT_BLE_OPERATE_NOT_SUPPORTED)
        intentFilter.addAction(Const.INTENT_BLE_TIMESYNC_SUCCESS)
        intentFilter.addAction(Const.INTENT_BLE_READ_MANUFACTURER)
        intentFilter.addAction(Const.INTENT_BLE_READ_SOFTWARE_REV)
        intentFilter.addAction(Const.INTENT_BLE_READ_COMPLETED)
        intentFilter.addAction(Const.INTENT_BLE_READ_GREATER_OR_EQUAL)
        intentFilter.addAction(Const.INTENT_BLE_SOFTWARE_VERSION)
        intentFilter.addAction(Const.INTENT_BLE_CHAR_GLUCOSE_CONTEXT)
        intentFilter.addAction(Const.INTENT_BLE_TOTAL_COUNT)
        intentFilter.addAction(Const.INTENT_BLE_SERIAL_NUMBER)
        intentFilter.addAction(Const.INTENT_STOP_SCAN)
        return intentFilter
    }

    override fun onBind(intent: Intent?): IBinder? {
        val isBleAvailable =
            packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)
        if (isBleAvailable && Util.getInstance(applicationContext).runningOnKitkatOrHigher()) {
            mBluetoothManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
            mBluetoothAdapter = mBluetoothManager!!.adapter
            if (mBluetoothAdapter == null) {
                bluetoothInitializeCallbacks?.onError(getString(R.string.ble_not_supported))
            }
        } else {
            bluetoothInitializeCallbacks?.onError("BLE off. Turn on ble mode")
        }

        stopService(Intent(this, GlucoseBleService::class.java))
        mIsBindGlucoseBleService = applicationContext.bindService(
            Intent(applicationContext, GlucoseBleService::class.java),
            mServiceConnection,
            BIND_AUTO_CREATE
        )
        registerReceiver(mBleUpdateReceiver, makeBleUpdateIntentFilter())
        return binder
    }

    inner class ServiceBinder : Binder() {
        fun getService(): CaresensBluetoothService {
            return this@CaresensBluetoothService
        }
    }

    //##START SCAN
    fun startScan() {
        if (isBLEEnabled() && hasPermissions()) {
            try {
                if (mBluetoothAdapter!!.state == BluetoothAdapter.STATE_ON) {
                    if (mScanCallback == null) initCallback()
                    val filters: List<ScanFilter> = ArrayList()
                    val settings = ScanSettings.Builder()
                        .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                        .setReportDelay(0)
                        .build()
                    if (hasPermissions()
                    ) {
                        mBluetoothAdapter!!.bluetoothLeScanner.flushPendingScanResults(
                            mScanCallback
                        )
                        mBluetoothAdapter!!.bluetoothLeScanner.stopScan(mScanCallback)
                        mBluetoothAdapter!!.bluetoothLeScanner.startScan(
                            filters,
                            settings,
                            mScanCallback
                        )
                    }
                }
            } catch (e: java.lang.Exception) {
                e.message
                mIsScanning = false
            }
            mIsScanning = true
        } else if (!isBLEEnabled()) {
            bluetoothScanCallbacks?.onScan(
                ScanState.FAIL,
                "startScan() failed. Set your bluetooth enabled",
                null
            )
            showBLEDialog()
        } else if (!hasPermissions()) {
            requestPermissions()
        }
    }

    //##STOP SCAN
    fun stopScan() {
        try {
            if (mIsScanning) {
                // Stop scan and flush pending scan
                mBluetoothAdapter!!.bluetoothLeScanner.flushPendingScanResults(mScanCallback)
                mBluetoothAdapter!!.bluetoothLeScanner.stopScan(mScanCallback)
                bluetoothScanCallbacks?.onScan(ScanState.STOPPED, "", null)
                mIsScanning = false
            }
        } catch (e: java.lang.Exception) {
            bluetoothScanCallbacks?.onScan(
                ScanState.FAIL,
                "stopScan() failed. error message: ${e.message}",
                null
            )
        }
    }

    fun connect(device: BluetoothDevice?) {
        try {
            connectedDeviceInfo.name = device?.name
            bluetoothConnectionCallbacks?.onStateChanged(
                ConnectState.CONNECTING, "",
                connectedDeviceInfo
            )
            broadcastUpdate(
                Const.INTENT_BLE_CONNECT_DEVICE,
                device.toString()
            )
        } catch (e: java.lang.Exception) {
            bluetoothConnectionCallbacks?.onStateChanged(
                ConnectState.ERROR,
                "Start Connect Failed: ${e.message}",
                null
            )
        }

    }

    fun disConnect() {
        if (mIsScanning) {
            stopScan()
        }
        mGlucoseBleService?.disconnect()
    }

    private fun isBLEEnabled(): Boolean {
        val adapter = mBluetoothManager!!.adapter
        return adapter != null && adapter.isEnabled
    }

    private fun showBLEDialog() {
        val enableIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
        enableIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(enableIntent)
    }

    private fun hasPermissions(): Boolean = TedPermission.isGranted(
        context,
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )

    private fun requestPermissions() {
        TedPermission.with(context)
            .setPermissions(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
            .setDeniedMessage(context.getString(R.string.error_denied_permission))
            .setPermissionListener(object : PermissionListener {
                override fun onPermissionGranted() {
                    if (!mIsScanning) {
                        //mBluetoothAdapter!!.startLeScan(mLEScanCallback)
                        startScan()
                        mIsScanning = true
                    }
                }

                override fun onPermissionDenied(deniedPermissions: MutableList<String>?) {
                    bluetoothScanCallbacks?.onScan(
                        ScanState.FAIL,
                        context.getString(R.string.error_denied_permission),
                        null
                    )
                }
            })
            .check()
    }

    private fun broadcastUpdate(action: String, data: String) {
        val intent = Intent(action)
        if (data !== "") intent.putExtra(Const.INTENT_BLE_EXTRA_DATA, data)
        sendBroadcast(intent)
    }

    fun setCheckAutoConnect(boolean: Boolean) {
        mAutoConnect = boolean
    }

    fun requestRecordsGreaterOrEqual(sequence: Int) {
        if (!mGlucoseBleService!!.getRecordsGreaterOrEqual(sequence)) {
            try {
                Thread.sleep(300)
                mGlucoseBleService!!.getRecordsGreaterOrEqual(sequence)
            } catch (e: InterruptedException) {
                e.printStackTrace()
            }
        }
    }

    fun requestAllRecords() {
        Handler().post {
            if (!mGlucoseBleService!!.allRecords) {
                try {
                    Thread.sleep(300)
                    mGlucoseBleService!!.allRecords
                } catch (e: InterruptedException) {
                    e.printStackTrace()
                }
            }
        }
    }

    private fun initCallback() {
        if (mScanCallback != null) return
        if (hasPermissions()) {
            mScanCallback = object : ScanCallback() {
                override fun onScanResult(callbackType: Int, result: ScanResult?) {
                    super.onScanResult(callbackType, result)
                    if (result != null) {
                        try {
                            if (ScannerServiceParser.decodeDeviceAdvData(result.scanRecord!!.bytes)) {
                                if (result.device.bondState == BluetoothDevice.BOND_BONDED) {
                                    if (mAutoConnect) {
                                        // CONNECT DEVICE
                                        broadcastUpdate(
                                            Const.INTENT_BLE_CONNECT_DEVICE,
                                            result.device.toString()
                                        )
                                    } else addScannedDevice(
                                        result.device,
                                        result.rssi,
                                        Consts.DEVICE_IS_BONDED
                                    )
                                } else {
                                    addScannedDevice(
                                        result.device,
                                        result.rssi,
                                        Consts.DEVICE_NOT_BONDED
                                    )
                                }
                            }
                        } catch (e: java.lang.Exception) {
                            bluetoothScanCallbacks?.onScan(ScanState.FAIL, "", null)
                        }
                    }
                }

                override fun onBatchScanResults(results: List<ScanResult>) {
                    super.onBatchScanResults(results)
                }

                override fun onScanFailed(errorCode: Int) {
                    super.onScanFailed(errorCode)
                    bluetoothScanCallbacks?.onScan(
                        ScanState.FAIL,
                        "errorCode : $errorCode",
                        null
                    )
                }
            }
        }
    }

    private fun requestTotalDataCnt() {
        Handler().post {
            if (!mGlucoseBleService!!.totalDataCnt) {
                try {
                    Thread.sleep(300)
                    mGlucoseBleService!!.totalDataCnt
                } catch (e: InterruptedException) {
                    e.printStackTrace()
                }
            }
        }
    }

    fun requestTimeSync() {
        if (!mGlucoseBleService!!.timeSync()) {
            try {
                Thread.sleep(300)
                mGlucoseBleService!!.timeSync()
            } catch (e: InterruptedException) {
                e.printStackTrace()
            }
        }
    }

    fun setGlucoseUnit(glucoseUnit: GlucoseUnit) {
        this.glucoseUnit = glucoseUnit
    }

    fun setBluetoothInitializeCallbacks(bluetoothInitializeCallbacks: BluetoothInitializeCallbacks?) {
        this.bluetoothInitializeCallbacks = bluetoothInitializeCallbacks
    }

    fun setBluetoothScanCallbacks(bluetoothScanCallbacks: BluetoothScanCallbacks?) {
        this.bluetoothScanCallbacks = bluetoothScanCallbacks
    }

    fun setBluetoothConnectionCallbacks(bluetoothConnectionCallbacks: BluetoothConnectionCallbacks?) {
        this.bluetoothConnectionCallbacks = bluetoothConnectionCallbacks
    }

    fun setBluetoothDataCallbacks(bluetoothDataCallbacks: BluetoothDataCallbacks?) {
        this.bluetoothDataCallbacks = bluetoothDataCallbacks
    }

    fun getGlucoseUnit() = glucoseUnit
}