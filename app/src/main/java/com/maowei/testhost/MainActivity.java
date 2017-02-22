package com.maowei.testhost;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.hardware.usb.UsbRequest;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private static final String ACTION_USB_PERMISSION =
            "com.android.example.USB_PERMISSION";

    private PendingIntent mPermissionIntent = null;
    private Button mButton = null;
    private Button mSendButton = null;
    private Button mRcvButton = null;
    private  HashMap<String, UsbDevice> deviceList = null;
    private UsbManager manager = null;
    private UsbDevice mDevice = null;
    private UsbInterface mUsbInterface = null;
    private UsbEndpoint mEndpointOut = null;
    private UsbEndpoint mEndpointIn = null;
    private UsbDeviceConnection mDeviceConnection = null;

    private final byte[] bytes = {0,1,2,3,4,5};
    private final int baurt = 9600;
    private static int TIMEOUT = 0;
    private int DEFAULT_TIMEOUT = 500;
    private boolean forceClaim = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mButton = (Button) findViewById(R.id.fresh_button);
        mButton.setOnClickListener(mOnClickListener);
        mSendButton = (Button)findViewById(R.id.send);
        mSendButton.setOnClickListener(mOnClickListener);
        mRcvButton = (Button)findViewById(R.id.receive);
        mRcvButton.setOnClickListener(mOnClickListener);

        manager = (UsbManager)getSystemService(Context.USB_SERVICE);
        mPermissionIntent = PendingIntent.getBroadcast(this, 0, new Intent(ACTION_USB_PERMISSION), 0);
        IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
        registerReceiver(mUsbReceiver, filter);

    }

    private View.OnClickListener mOnClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            switch(v.getId()) {
                case R.id.fresh_button :
                    getDevice();
                    break;
                case R.id.send :
                    sendByte();
                    break;
                case R.id.receive:
                    receiveByte();
                default:
                    break;
            }
        }
    };

    public void getDevice() {
        deviceList = manager.getDeviceList();
        if(! deviceList.isEmpty()) {
            for(UsbDevice device :  deviceList.values()) {
                if(device.getDeviceClass() == 255)
                mDevice = device;
            }
            if(mDevice!= null) {
                Log.d(TAG,"get device");
                manager.requestPermission(mDevice,mPermissionIntent);
            }
            else {
                Log.d(TAG,"can't get device");
            }
        }
        else {
            Log.e(TAG,"no device found");
        }
    }

    public void sendByte() {
        if(mDevice != null) {
            Thread t = new Thread(new Runnable() {
                @Override
                public void run() {
                    Log.d(TAG,"sendbyte right now");
                    mEndpointOut = mUsbInterface.getEndpoint(1);
                    mDeviceConnection = manager.openDevice(mDevice);
                    mDeviceConnection.claimInterface(mUsbInterface, forceClaim);
                    ByteBuffer buffer = ByteBuffer.allocate(24);
                    buffer.clear();
                    UsbRequest request = new UsbRequest();
                    request.initialize(mDeviceConnection, mEndpointOut);
                    UartInit();
                    SetConfig(baurt,(byte) 8, (byte) 1, (byte) 0, (byte) 0);
                    byte a = 1;
                    buffer.put(a);
                    boolean retval = request.queue(buffer, 1);
                   // System.out.println(retval);
                }
            });
            t.run();
        }
        else {
            Log.e(TAG,"can't find mDevice when sending byte");
        }
    }

    public void receiveByte() {
        if(mDevice != null) {
            mUsbInterface = mDevice.getInterface(0);
            mEndpointIn = mUsbInterface.getEndpoint(0);
            mDeviceConnection = manager.openDevice(mDevice);
            mDeviceConnection.claimInterface(mUsbInterface, forceClaim);
            Thread t = new Thread(new Runnable() {
                @Override
                public void run() {

                }
            });
            t.run();
        }
        else {
            Log.e(TAG,"can't find mDevice when sending byte");
        }
    }

    public boolean UartInit(){
        int ret;
        int size = 8;
        byte[] buffer = new byte[size];
        Uart_Control_Out(UartCmd.VENDOR_SERIAL_INIT, 0x0000, 0x0000);
        ret = Uart_Control_In(UartCmd.VENDOR_VERSION, 0x0000, 0x0000, buffer, 2);
        if(ret < 0)
            return false;
        Uart_Control_Out(UartCmd.VENDOR_WRITE, 0x1312, 0xD982);
        Uart_Control_Out(UartCmd.VENDOR_WRITE, 0x0f2c, 0x0004);
        ret = Uart_Control_In(UartCmd.VENDOR_READ, 0x2518, 0x0000, buffer, 2);
        if(ret < 0)
            return false;
        Uart_Control_Out(UartCmd.VENDOR_WRITE, 0x2727, 0x0000);
        Uart_Control_Out(UartCmd.VENDOR_MODEM_OUT, 0x00ff, 0x0000);
        return true;
    }

    public int Uart_Control_Out(int request, int value, int index)
    {
        int retval = 0;
        retval = mDeviceConnection.controlTransfer(UsbType.USB_TYPE_VENDOR | UsbType.USB_RECIP_DEVICE | UsbType.USB_DIR_OUT,
                request, value, index, null, 0, DEFAULT_TIMEOUT);

        return retval;
    }

    public int Uart_Control_In(int request, int value, int index, byte[] buffer, int length)
    {
        int retval = 0;
        retval = mDeviceConnection.controlTransfer(UsbType.USB_TYPE_VENDOR | UsbType.USB_RECIP_DEVICE | UsbType.USB_DIR_IN,
                request, value, index, buffer, length, DEFAULT_TIMEOUT);
        return retval;
    }

    public boolean SetConfig(int baudRate, byte dataBit, byte stopBit, byte parity, byte flowControl){
        int value = 0;
        int index = 0;
        char valueHigh = 0, valueLow = 0, indexHigh = 0, indexLow = 0;
        switch(parity) {
            case 0:	/*NONE*/
                valueHigh = 0x00;
                break;
            case 1:	/*ODD*/
                valueHigh |= 0x08;
                break;
            case 2:	/*Even*/
                valueHigh |= 0x18;
                break;
            case 3:	/*Mark*/
                valueHigh |= 0x28;
                break;
            case 4:	/*Space*/
                valueHigh |= 0x38;
                break;
            default:	/*None*/
                valueHigh = 0x00;
                break;
        }

        if(stopBit == 2) {
            valueHigh |= 0x04;
        }

        switch(dataBit) {
            case 5:
                valueHigh |= 0x00;
                break;
            case 6:
                valueHigh |= 0x01;
                break;
            case 7:
                valueHigh |= 0x02;
                break;
            case 8:
                valueHigh |= 0x03;
                break;
            default:
                valueHigh |= 0x03;
                break;
        }

        valueHigh |= 0xc0;
        valueLow = 0x9c;

        value |= valueLow;
        value |= (int)(valueHigh << 8);

        switch(baudRate) {
            case 50:
                indexLow = 0;
                indexHigh = 0x16;
                break;
            case 75:
                indexLow = 0;
                indexHigh = 0x64;
                break;
            case 110:
                indexLow = 0;
                indexHigh = 0x96;
                break;
            case 135:
                indexLow = 0;
                indexHigh = 0xa9;
                break;
            case 150:
                indexLow = 0;
                indexHigh = 0xb2;
                break;
            case 300:
                indexLow = 0;
                indexHigh = 0xd9;
                break;
            case 600:
                indexLow = 1;
                indexHigh = 0x64;
                break;
            case 1200:
                indexLow = 1;
                indexHigh = 0xb2;
                break;
            case 1800:
                indexLow = 1;
                indexHigh = 0xcc;
                break;
            case 2400:
                indexLow = 1;
                indexHigh = 0xd9;
                break;
            case 4800:
                indexLow = 2;
                indexHigh = 0x64;
                break;
            case 9600:
                indexLow = 2;
                indexHigh = 0xb2;
                break;
            case 19200:
                indexLow = 2;
                indexHigh = 0xd9;
                break;
            case 38400:
                indexLow = 3;
                indexHigh = 0x64;
                break;
            case 57600:
                indexLow = 3;
                indexHigh = 0x98;
                break;
            case 115200:
                indexLow = 3;
                indexHigh = 0xcc;
                break;
            case 230400:
                indexLow = 3;
                indexHigh = 0xe6;
                break;
            case 460800:
                indexLow = 3;
                indexHigh = 0xf3;
                break;
            case 500000:
                indexLow = 3;
                indexHigh = 0xf4;
                break;
            case 921600:
                indexLow = 7;
                indexHigh = 0xf3;
                break;
            case 1000000:
                indexLow = 3;
                indexHigh = 0xfa;
                break;
            case 2000000:
                indexLow = 3;
                indexHigh = 0xfd;
                break;
            case 3000000:
                indexLow = 3;
                indexHigh = 0xfe;
                break;
            default:	// default baudRate "9600"
                indexLow = 2;
                indexHigh = 0xb2;
                break;
        }

        index |= 0x88 |indexLow;
        index |= (int)(indexHigh << 8);

        Uart_Control_Out(UartCmd.VENDOR_SERIAL_INIT, value, index);
        if(flowControl == 1) {
            Uart_Tiocmset(UartModem.TIOCM_DTR | UartModem.TIOCM_RTS, 0x00);
        }
        return true;
    }

    public int Uart_Tiocmset(int set, int clear) {
        int control = 0;
        if ((set & UartModem.TIOCM_RTS) == UartModem.TIOCM_RTS)
            control |= UartIoBits.UART_BIT_RTS;
        if ((set & UartModem.TIOCM_DTR) == UartModem.TIOCM_DTR)
            control |= UartIoBits.UART_BIT_DTR;
        if ((clear & UartModem.TIOCM_RTS) == UartModem.TIOCM_RTS)
            control &= ~UartIoBits.UART_BIT_RTS;
        if ((clear & UartModem.TIOCM_DTR) == UartModem.TIOCM_DTR)
            control &= ~UartIoBits.UART_BIT_DTR;

        return Uart_Set_Handshake(control);
    }

    private int Uart_Set_Handshake(int control) {
        return Uart_Control_Out(UartCmd.VENDOR_MODEM_OUT, ~control, 0);
    }


    private final BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {

        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (ACTION_USB_PERMISSION.equals(action)) {
                synchronized (this) {
                    UsbDevice device = (UsbDevice)intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);

                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        if(device != null){
                            mUsbInterface = device.getInterface(0);
                            Log.d(TAG,"get permission for the device");
                        }
                    }
                    else {
                        Log.d(TAG, "permission denied for device " + device);
                    }
                }
            }
        }
    };

    public final class UartModem {
        public static final int TIOCM_LE = 0x001;
        public static final int TIOCM_DTR = 0x002;
        public static final int TIOCM_RTS = 0x004;
        public static final int TIOCM_ST = 0x008;
        public static final int TIOCM_SR = 0x010;
        public static final int TIOCM_CTS = 0x020;
        public static final int TIOCM_CAR = 0x040;
        public static final int TIOCM_RNG = 0x080;
        public static final int TIOCM_DSR = 0x100;
        public static final int TIOCM_CD = TIOCM_CAR;
        public static final int TIOCM_RI = TIOCM_RNG;
        public static final int TIOCM_OUT1 = 0x2000;
        public static final int TIOCM_OUT2 = 0x4000;
        public static final int TIOCM_LOOP = 0x8000;
    }

    public final class UsbType {
        public static final int USB_TYPE_VENDOR = (0x02 << 5);
        public static final int USB_RECIP_DEVICE = 0x00;
        public static final int USB_DIR_OUT = 0x00; /* to device */
        public static final int USB_DIR_IN = 0x80; /* to host */
    }

    public final class UartCmd {
        public static final int VENDOR_WRITE_TYPE = 0x40;
        public static final int VENDOR_READ_TYPE = 0xC0;
        public static final int VENDOR_READ = 0x95;
        public static final int VENDOR_WRITE = 0x9A;
        public static final int VENDOR_SERIAL_INIT = 0xA1;
        public static final int VENDOR_MODEM_OUT = 0xA4;
        public static final int VENDOR_VERSION = 0x5F;
    }

    public final class UartState {
        public static final int UART_STATE = 0x00;
        public static final int UART_OVERRUN_ERROR = 0x01;
        public static final int UART_PARITY_ERROR = 0x02;
        public static final int UART_FRAME_ERROR = 0x06;
        public static final int UART_RECV_ERROR = 0x02;
        public static final int UART_STATE_TRANSIENT_MASK = 0x07;
    }

    public final class UartIoBits {
        public static final int UART_BIT_RTS = (1 << 6);
        public static final int UART_BIT_DTR = (1 << 5);
    }
}
