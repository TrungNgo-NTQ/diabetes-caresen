package net.huray.caresens

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
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
import java.text.SimpleDateFormat

class MainActivity : AppCompatActivity() {
    private val TAG = this::class.java.simpleName
    private var _binding: ActivityMainBinding? = null
    private val binding get() = _binding
    private var mDeviceAdapter: DeviceAdapter? = null

    var caresensBluetoothService: CaresensBluetoothService? = null
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
                                    for(device in devices!!){
                                        Log.d(TAG, "device name:" + device?.device?.name)
                                        Log.d(
                                            TAG,
                                            "device address:" + device?.device?.address
                                        )
                                        mDeviceAdapter?.addDevice(device)
                                    }
                                }
                                ScanState.STOPPED -> {
                                    Log.d(TAG, "onStop called")
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
                            Log.d(TAG, "Connect Callback called: $state")
                            when (state) {
                                ConnectState.DISCONNECTED -> {
                                    Log.d(
                                        TAG,
                                        "DisConnect deviceInfo name: ${deviceInfo?.name}"
                                    )
                                    Log.d(
                                        TAG,
                                        "DisConnect deviceInfo SN: ${deviceInfo?.serialNumber}"
                                    )
                                    Log.d(
                                        TAG,
                                        "DisConnect deviceInfo Ver: ${deviceInfo?.version}"
                                    )
                                    Log.d(
                                        TAG,
                                        "DisConnect deviceInfo count: ${deviceInfo?.totalCount}"
                                    )
                                }
                                ConnectState.CONNECTED -> {
                                    _binding?.listviewScannedDevices?.visibility = View.GONE
                                    _binding?.layoutDeviceInfo?.visibility = View.VISIBLE
                                    _binding?.layoutButton?.visibility = View.VISIBLE
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
                                    _binding?.txtResult?.movementMethod = ScrollingMovementMethod()
                                    _binding?.txtResult?.scrollTo(0, 0)
                                    _binding?.txtResult?.text = ""
                                    _binding?.txtResult?.visibility = View.VISIBLE
                                    if (glucoseRecords == null || glucoseRecords.size() <= 0) {
                                        _binding?.txtResult?.append("No data downloaded.")
                                        return
                                    }
                                    _binding?.listviewScannedDevices?.visibility = View.GONE
                                    _binding?.btnBack?.visibility = View.VISIBLE
                                    _binding?.layoutButton?.visibility = View.GONE

                                    glucoseRecords.forEach { key, value ->
                                        if (value.flag_ketone == 1) {
                                            _binding?.txtResult?.append(
                                                """### sequence: ${value.sequenceNumber}, ketone: ${value.glucoseData / Const.KetoneMultiplier}mmol/L, date: ${
                                                    getDate(
                                                        value.time
                                                    )
                                                }, timeoffset: ${value.timeoffset}""".trimIndent()
                                            )
                                        } else {
                                            _binding?.txtResult?.append(
                                                """### sequence: ${value.sequenceNumber}, glucose: ${value.glucoseData}${
                                                    GlucoseUnit.getValue(
                                                        caresensBluetoothService?.getGlucoseUnit()
                                                    )
                                                }, date: ${
                                                    getDate(
                                                        value.time
                                                    )
                                                }, timeoffset: ${value.timeoffset}""".trimIndent()
                                            )
                                        }
                                    }

                                    Log.d(TAG, "glucose Record: $glucoseRecords")
                                }
                                DataReadState.DEVICE_INFO_READ_COMPLETE -> {
                                    _binding?.txtDeviceName?.text = deviceInfo?.name
                                    _binding?.txtSerialNum?.text = deviceInfo?.serialNumber
                                    _binding?.txtSoftwareVersion?.text =
                                        deviceInfo?.version.toString()
                                    _binding?.txtTotalCount?.text =
                                        deviceInfo?.totalCount.toString()
                                }
                                else -> {
                                }
                            }

                        }

                    }

                )
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