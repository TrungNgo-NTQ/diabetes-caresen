package net.huray.caresens

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.text.method.ScrollingMovementMethod
import android.util.Log
import android.util.SparseArray
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.util.forEach
import net.huray.caresens.ble.Const
import net.huray.caresens.ble.ExtendedDevice
import net.huray.caresens.ble.GlucoseRecord
import net.huray.caresens.callbacks.BluetoothConnectionCallbacks
import net.huray.caresens.callbacks.BluetoothDataCallbacks
import net.huray.caresens.callbacks.BluetoothScanCallbacks
import net.huray.caresens.data.DeviceInfo
import net.huray.caresens.databinding.ActivityMainBinding
import net.huray.caresens.enums.ConnectState
import net.huray.caresens.enums.DataReadState
import net.huray.caresens.enums.GlucoseUnit
import net.huray.caresens.enums.ScanState
import java.lang.StringBuilder
import java.text.SimpleDateFormat

class MainActivity : AppCompatActivity() {
    private val TAG = this::class.java.simpleName
    private var _binding: ActivityMainBinding? = null
    private val binding get() = _binding
    private var mDeviceAdapter: DeviceAdapter? = null

    var caresensBluetoothService: CaresensBluetoothService? = null


    private var readData = false
    private var connected = false
    private var result = StringBuilder()

    private var serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val serviceBinder = service as CaresensBluetoothService.ServiceBinder
            caresensBluetoothService = serviceBinder.getService().apply {
                initialize(
                    this,
                    bluetoothScanCallbacks = object : BluetoothScanCallbacks {
                        override fun onScan(
                            state: ScanState,
                            errorMsg: String?,
                            devices: ArrayList<ExtendedDevice>?
                        ) {
                            when (state) {
                                ScanState.SCANNING -> {
                                    for (device in devices!!) {
                                        if (device.device.name.contains("CareSens") || device.device.name.contains("meter")) {

                                            Handler(Looper.getMainLooper()).postDelayed({
                                                if (!connected) {
                                                    Log.d("LinhBD", "Connect")
                                                    connect(device.device)
                                                    connected = true
                                                }
                                            }, 3000)
                                        }
                                    }
                                }

                                ScanState.STOPPED -> {

                                }

                                else -> {

                                }
                            }

                        }

                    },
                    bluetoothConnectionCallbacks = object : BluetoothConnectionCallbacks {
                        override fun onStateChanged(
                            state: ConnectState,
                            errorMsg: String?,
                            deviceInfo: DeviceInfo?
                        ) {
                            when (state) {
                                ConnectState.DISCONNECTED -> {
                                    connected = false
                                    readData = false
                                }
                                ConnectState.CONNECTED -> {
                                    Handler(Looper.getMainLooper()).postDelayed({
                                        if (!readData) {
                                            requestAllRecords()
                                            readData = true
                                        }
                                    }, 3000)
                                }
                                else -> {

                                }
                            }
                        }

                    },
                    bluetoothDataCallbacks = object : BluetoothDataCallbacks {
                        override fun onRead(
                            dataReadState: DataReadState,
                            errorMsg: String?,
                            deviceInfo: DeviceInfo?,
                            glucoseRecords: SparseArray<GlucoseRecord>?
                        ) {
                            when (dataReadState) {
                                DataReadState.GlUCOSE_RECORD_READ_COMPLETE -> {
                                    Log.d("LinhBD", "glucoseRecords size: ${glucoseRecords?.size()}")

                                    glucoseRecords?.forEach { key, value ->
                                        if (value.flag_ketone == 1) {
                                            result.append(
                                                value.glucoseData.toString()
                                            )
                                        } else {
                                            result.append(
                                                value.glucoseData.toString()
                                            )
                                        }
                                    }

                                    Log.d("LinhBD", result.toString())
                                }
                                DataReadState.DEVICE_INFO_READ_COMPLETE -> {
                                    /*_binding?.txtDeviceName?.text = deviceInfo?.name
                                    _binding?.txtSerialNum?.text = deviceInfo?.serialNumber
                                    _binding?.txtSoftwareVersion?.text =
                                        deviceInfo?.version.toString()
                                    _binding?.txtTotalCount?.text =
                                        deviceInfo?.totalCount.toString()*/

                                    Log.d("LinhBD", result.toString())
                                }
                                else -> {
                                    Log.d("LinhBD", "SomeThingWrong")
                                }
                            }

                        }

                    }

                )
                setCheckAutoConnect(false)
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {}
    }

    private fun serviceBind() {
        val intent = Intent(this, CaresensBluetoothService::class.java)
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        _binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding?.root)
        serviceBind()
        initView()
    }

    fun initView() {
        _binding?.btnStartScan?.setOnClickListener {
            mDeviceAdapter?.clearDevices()
            _binding?.listviewScannedDevices?.visibility = View.VISIBLE
            _binding?.layoutDeviceInfo?.visibility = View.GONE
            _binding?.txtResult?.visibility = View.GONE
            _binding?.layoutButton?.visibility = View.GONE
            _binding?.btnBack?.visibility = View.GONE
            caresensBluetoothService?.startScan()
        }

        _binding?.btnStopScan?.setOnClickListener {
            caresensBluetoothService?.stopScan()
        }

        mDeviceAdapter = DeviceAdapter(this)
        _binding?.listviewScannedDevices?.adapter = mDeviceAdapter
        _binding?.listviewScannedDevices?.setOnItemClickListener { parent, view, position, id ->
            caresensBluetoothService?.connect((mDeviceAdapter!!.getItem(position) as ExtendedDevice).device)
        }

        _binding?.btnDownloadAll?.setOnClickListener {
            caresensBluetoothService?.requestAllRecords()
        }

        _binding?.btnDownloadGreaterOrEqual?.setOnClickListener {
            var sequence = 1
            if (_binding?.edtSequenceDownloadFrom?.text.toString() != "") {
                sequence = _binding?.edtSequenceDownloadFrom?.text
                    .toString().trim { it <= ' ' }
                    .toInt()
            }
            caresensBluetoothService?.requestRecordsGreaterOrEqual(sequence)
        }

        _binding?.btnDisconnect?.setOnClickListener {
            caresensBluetoothService?.disConnect()
        }

        _binding?.btnBack?.setOnClickListener {
            _binding?.txtResult?.visibility = View.GONE
            _binding?.listviewScannedDevices?.visibility = View.GONE
            _binding?.layoutDeviceInfo?.visibility = View.VISIBLE
            _binding?.layoutButton?.visibility = View.VISIBLE
            it.visibility = View.GONE
        }

        _binding?.checkAutoConnect?.setOnCheckedChangeListener { buttonView, isChecked ->
            caresensBluetoothService?.setCheckAutoConnect(isChecked)
        }

    }

    fun getDate(t: Long): String? {
        val sdfNow = SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
        return sdfNow.format(t * 1000)
    }
}