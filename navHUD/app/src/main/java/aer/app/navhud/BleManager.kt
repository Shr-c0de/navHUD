package aer.app.navhud

import android.Manifest
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelUuid
import android.util.Log
import androidx.core.app.ActivityCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.LinkedList
import java.util.Queue
import java.util.UUID

class BleManager(private val context: Context) {

    private val _isConnected = MutableStateFlow(false)
    val isConnected = _isConnected.asStateFlow()

    private val writeQueue: Queue<ByteArray> = LinkedList()
    private var isWriting = false
    private var connectionJob: Job? = null

    private val bluetoothAdapter: BluetoothAdapter by lazy {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothManager.adapter
    }

    private val bleScanner by lazy {
        bluetoothAdapter.bluetoothLeScanner
    }

    private var gatt: BluetoothGatt? = null
    private var navHudCharacteristic: BluetoothGattCharacteristic? = null

    fun startConnectionLoop() {
        if (connectionJob?.isActive == true) return // Already running
        connectionJob = CoroutineScope(Dispatchers.Default).launch {
            while (true) {
                if (!_isConnected.value) {
                    startScan()
                }
                delay(5000) // Wait 5 seconds between connection attempts
            }
        }
        Log.d("BleManager", "Connection loop started.")
    }

    fun stopConnectionLoop() {
        connectionJob?.cancel()
        connectionJob = null
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED) {
            bleScanner.stopScan(scanCallback)
        }
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
            gatt?.disconnect()
        }
        Log.d("BleManager", "Connection loop stopped.")
    }

    private fun startScan() {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
            Log.e("BleManager", "Bluetooth scan permission not granted")
            return
        }
        
        if (!bluetoothAdapter.isEnabled) {
            Log.e("BleManager", "Bluetooth is not enabled.")
            return
        }

        // Stop any previous scan before starting a new one to prevent resource leaks
        bleScanner.stopScan(scanCallback)
        Log.d("BleManager", "Stopping previous scan (if any) and starting a new one.")

        val scanFilter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(SERVICE_UUID))
            .build()

        val scanSettings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()

        bleScanner.startScan(listOf(scanFilter), scanSettings, scanCallback)
        Log.d("BleManager", "New scan started with filter for UUID: $SERVICE_UUID")
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED) {
                bleScanner.stopScan(this)
            }

            val device = result.device
            Log.d("BleManager", "Found device matching filter: ${device.name ?: "Unknown"}")

            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) return

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
            } else {
                device.connectGatt(context, false, gattCallback)
            }
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                _isConnected.value = true
                this@BleManager.gatt = gatt
                Log.d("BLEconnection", "Connection Successful. Discovering services...")
                if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                    gatt.discoverServices()
                }
            } else {
                _isConnected.value = false
                writeQueue.clear()
                isWriting = false
                Log.d("BLEconnection", "Disconnected with status: $status")
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val service = gatt.getService(SERVICE_UUID)
                navHudCharacteristic = service?.getCharacteristic(CHARACTERISTIC_UUID)
                if (navHudCharacteristic != null) {
                    Log.d("BleManager", "Service and characteristic found.")
                } else {
                    Log.e("BleManager", "Characteristic or Service not found!")
                }
            }
        }

        override fun onCharacteristicWrite(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                isWriting = false
                if (writeQueue.isEmpty()) {
                    Log.d("BleManager", "--- Full message sent successfully ---")
                } else {
                    writeNextFromQueue()
                }
            } else {
                Log.e("BleManager", "Write failed with status: $status")
                writeQueue.clear()
                isWriting = false
            }
        }
    }

    fun sendData(data: ByteArray) {
        if (!_isConnected.value) return

        val PAYLOAD_SIZE = 18
        val totalChunks = Math.ceil(data.size.toDouble() / PAYLOAD_SIZE).toInt()

        if (totalChunks > 255) {
            Log.e("BleManager", "Data is too large to be sent with this packet format.")
            return
        }

        val dataChunks = data.asSequence().chunked(PAYLOAD_SIZE).toList()

        for ((index, chunk) in dataChunks.withIndex()) {
            val packet = ByteArray(2 + chunk.size)
            packet[0] = index.toByte()
            packet[1] = totalChunks.toByte()
            System.arraycopy(chunk.toByteArray(), 0, packet, 2, chunk.size)
            writeQueue.add(packet)
        }

        if (!isWriting) {
            writeNextFromQueue()
        }
    }

    private fun writeNextFromQueue() {
        if (writeQueue.isNotEmpty() && !isWriting) {
            val characteristic = navHudCharacteristic ?: return

            isWriting = true
            val packet = writeQueue.poll()!!
            characteristic.value = packet
            characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT

            val chunkNum = packet[0].toInt() and 0xFF
            val totalChunks = packet[1].toInt() and 0xFF
            Log.d("BLEsender", "Sending packet ${chunkNum + 1}/$totalChunks. Size: ${packet.size} bytes.")

            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                gatt?.writeCharacteristic(characteristic)
            }
        }
    }

    companion object {
        val SERVICE_UUID = UUID.fromString("4fafc201-1fb5-459e-8fcc-c2d9c331914b")
        val CHARACTERISTIC_UUID = UUID.fromString("beb5483e-abe1-4688-b7f5-ea07361b26a8")
    }
}
