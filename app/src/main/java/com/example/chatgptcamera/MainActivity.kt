package com.example.chatgptcamera

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.hardware.usb.*
import android.hardware.usb.UsbManager.ACTION_USB_DEVICE_ATTACHED
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.ContextCompat.getSystemService
import androidx.core.content.getSystemService
import com.example.chatgptcamera.databinding.ActivityMainBinding

private const val TAG = "ActivityMain"


class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val CAMERA_PERMISSION_REQUEST_CODE = 100
    private lateinit var myUsbDevice: UsbDevice
    private lateinit var myUsbManager: UsbManager
    private val forceClaim = true
    private lateinit var bytes: ByteArray
    private val TIMEOUT = 0

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == ACTION_USB_DEVICE_ATTACHED) {
                // A USB device is attached, check if it is a camera and request permission
                val device = intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)
                if (device?.isCamera() == true) {
                    myUsbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
                    Log.d(TAG, "isCamera has registered as true, usbmanager established")
                    val permissionIntent = PendingIntent.getBroadcast(context, 0, Intent(
                        CAMERA_SERVICE), 0)
                    myUsbManager.requestPermission(device, permissionIntent)
                    Log.d(TAG, "request permission should have fired")
                    myUsbDevice = device

                } else {
                    Log.d(TAG, "isCamera has registered as false")
                }
            }
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), CAMERA_PERMISSION_REQUEST_CODE)
        }

        val filter = IntentFilter(UsbManager.ACTION_USB_DEVICE_ATTACHED)
        registerReceiver(usbReceiver, filter)
        Log.d(TAG, "receiver registered after onCreate")



        binding.takePicture.setOnClickListener {
            val interfacecount = myUsbDevice.interfaceCount
            Log.d(TAG, "Interface count established $interfacecount")

            var usbInterfaceTemp: UsbInterface?
            var usbInterface: UsbInterface? = null
            var endpointIN: UsbEndpoint?
            var endpointOUT: UsbEndpoint? = null
            for (i in 0 until interfacecount + 1) {
                Log.d(TAG, "Interface count is $interfacecount")
                usbInterfaceTemp = myUsbDevice.getInterface(i)
                Log.d(TAG, "device.getinterface($i) loop started")
                Log.d(TAG, "endpoint count ${usbInterfaceTemp.endpointCount}")
                if (usbInterfaceTemp.endpointCount == 1) {
                    //for (j in 0 until usbInterfaceTemp.endpointCount+1) {
                    Log.d(TAG, "device.getinterfacetemp.endpointcount loop started")
                    val usbEndpointTemp = usbInterfaceTemp.getEndpoint(0)
                    Log.d(TAG, "endpoint type ${usbEndpointTemp.type}")
                    if (usbEndpointTemp.type == UsbConstants.USB_ENDPOINT_XFERTYPE_MASK) {
                        Log.d(TAG, "endpoint type SUB_ENDPOINT_XFERTYPE_MASK loop started")
                        Log.d(TAG, "endpoint direction is ${usbEndpointTemp.direction}")
                        if (usbEndpointTemp.direction == UsbConstants.USB_DIR_IN) {
                            Log.d(
                                TAG,
                                "yes indeed the usbendpointtemp direction is USB_DIR_IN number 128 $usbEndpointTemp"
                            )
                            endpointIN = usbEndpointTemp
                            usbInterface = usbInterfaceTemp
                            Log.d(TAG, "usbinterface set as usbinterfaceTemp")

                            val usbDeviceConnection = myUsbManager.openDevice(myUsbDevice)
                            //currently says there is a null object reference for device, maybe because permission not granted?
                            Log.d(TAG, "manager has opened the device line")
                            usbDeviceConnection.claimInterface(usbInterface, forceClaim)
                            Log.d(TAG, "manager has claimed the interface")
                            Log.d(TAG, "at this point the endpointin is $endpointIN")
                            val dataReceived = usbDeviceConnection.bulkTransfer(
                                endpointIN,
                                bytes,
                                bytes.size,
                                TIMEOUT
                            )
                            Log.d(TAG, "usbDeviceConnection.bulkTransfer called")
                            Log.d(TAG, "Data received is $dataReceived")
                        }
                    }
                }
            }
        }
        }




    override fun onResume() {
        super.onResume()
        val filter = IntentFilter(ACTION_USB_DEVICE_ATTACHED)
        registerReceiver(usbReceiver, filter)
        Log.d(TAG, "receiver registered after onResume")
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(usbReceiver)
        Log.d(TAG, "receiver unregistered after onPause")
    }

    override fun onDestroy() {
        super.onDestroy()
        // Unregister the USB receiver
        unregisterReceiver(usbReceiver)
        Log.d(TAG, "receiver unregistered after onDestroy")
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_USB_PERMISSION) {
            Log.d(TAG, "checking requestCode in onRequestPermissionResult fun")
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted, you can now access the USB camera
                Toast.makeText(
                    this,
                    "USB Permissions granted whoop!",
                    Toast.LENGTH_LONG
                ).show()
            } else {
                // Permission denied, show an error message or request again
                Toast.makeText(
                    this,
                    "USB Permissions denied boohoo!",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    fun UsbDevice.isCamera(): Boolean {
        // Check the USB interface class to see if it's a still image or video device
        if (this.interfaceCount <= 0) {
            return false
        }

        for (i in 0 until this.interfaceCount) {
            val usbInterface = this.getInterface(i)

            if (usbInterface.interfaceClass == UsbConstants.USB_CLASS_STILL_IMAGE ||
                usbInterface.interfaceClass == UsbConstants.USB_CLASS_VIDEO) {
                // Check the vendor and product IDs to see if it's a camera
                return this.vendorId == 20809 && this.productId == 5075
            }
        }

        return false
    }




    companion object {
        private const val ACTION_USB_DEVICE_ATTACHED = "android.hardware.usb.action.USB_DEVICE_ATTACHED"
        private const val CAMERA_PERMISSION_REQUEST_CODE = "android.hardware.CAMERA"
        private const val ACTION_USB_PERMISSION = "com.example.chatgptcamera.USB_PERMISSION"
        private const val REQUEST_USB_PERMISSION = 1
    }


}



// Note: Make sure to handle the camera permission request and other necessary checks before accessing the external camera.
