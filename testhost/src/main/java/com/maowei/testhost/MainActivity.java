package com.maowei.testhost;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;

import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.util.HashMap;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private static final String ACTION_USB_PERMISSION =
            "com.android.example.USB_PERMISSION";
    private static final int MSG_SHOW_DATA = 1;

    private Button mButton;
    private Button mSendButton;
    private Button mRcvButton;
    private TextView mRcvTextView;
    private EditText mSendEditView;

    private Handler mHandler;
    private PendingIntent mPermissionIntent;
    private UsbManager mUsbManager;
    private UsbDevice mDevice;
    private UsbInterface mUsbInterface;
    private UsbEndpoint mEndpointOut;
    private UsbEndpoint mEndpointIn;
    private UsbDeviceConnection mDeviceConnection;

    private final int baurt = 9600;
    private static int TIMEOUT = 0;
    private int DEFAULT_TIMEOUT = 500;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mButton = (Button) findViewById(R.id.fresh_button);
        mSendButton = (Button)findViewById(R.id.send);
        mRcvButton = (Button)findViewById(R.id.receive);
        mRcvTextView = (TextView)findViewById(R.id.receive_text);
        mSendEditView = (EditText)findViewById(R.id.send_text);

        mButton.setOnClickListener(mOnClickListener);
        mSendButton.setOnClickListener(mOnClickListener);
        mRcvButton.setOnClickListener(mOnClickListener);

        mUsbManager = (UsbManager)getSystemService(Context.USB_SERVICE);
        mPermissionIntent = PendingIntent.getBroadcast(this, 0, new Intent(ACTION_USB_PERMISSION), 0);
        IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
        registerReceiver(mUsbReceiver, filter);

        mHandler =  new Handler() {
            @Override
            public void handleMessage(Message msg) {
                super.handleMessage(msg);
                switch(msg.what) {
                    case 0:
                        mRcvTextView.setText((String)msg.obj);
                        break;
                    case 1:
                        mRcvTextView.setText("didn't read anything!");
                }
            }
        };
    }

    @Override
    public void onDestroy() {
        unregisterReceiver(mUsbReceiver);
        releaseUsbSrc();
    }

    private View.OnClickListener mOnClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            switch(v.getId()) {
                case R.id.fresh_button :
                    connectDevice();
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

    public void connectDevice() {
        if(findUartDevice()) {
            requestPermission();
        }
    }

    public boolean findUartDevice() {
        HashMap<String, UsbDevice> deviceList = mUsbManager.getDeviceList();
        if(! deviceList.isEmpty()) {
            for(UsbDevice device :  deviceList.values()) {
                if(device.getDeviceClass() == 255) {
                    mDevice = device;
                    break;
                }
            }
        } else {
            Log.e(TAG,"no device found");
            return false;
        }
        return true;
    }

    public void requestPermission() {
        if(mDevice!= null) {
            mUsbManager.requestPermission(mDevice, mPermissionIntent);
        } else {
            Log.d(TAG, "can't get device");
        }
    }

    public void sendByte() {
        if(mDevice != null) {
            Thread t = new Thread(new Runnable() {
                @Override
                public void run() {
                    byte[] data = getByteArrayFromEditText();
                    int length = data.length;
                    int  result = mDeviceConnection.bulkTransfer(mEndpointOut, data, length, 1000);
                    if(result > 0) {
                        Log.d(TAG,"successful send byte");
                    } else {
                        Log.d(TAG,"send byte failed");
                    }
                }
            });
            t.run();
        } else {
            Log.e(TAG,"can't find mDevice when sending byte");
        }
    }

    public void receiveByte() {
        if(mDevice != null) {
            Thread t = new Thread(new Runnable() {
                @Override
                public void run() {
                    byte[] rcv = new byte[8];
                    int result = mDeviceConnection.bulkTransfer(mEndpointIn, rcv, 8, 500);
                    if(result > 0) {
                        showByteArrayInTextView(rcv);
                    } else {
                        Message msg = new Message();
                        msg.what = 1;
                        mHandler.sendMessage(msg);
                    }
                }
            });
            t.run();
        } else {
            Log.e(TAG,"can't find mDevice when sending byte");
        }
    }

    public byte[] getByteArrayFromEditText() {
        String dataStr = mSendEditView.getText().toString();
        byte[] result = hexStr2Bytes(dataStr);
        return result;
    }

    public byte[] hexStr2Bytes(String src)
    {
        int m=0,n=0;
        int l=src.length()/2;
        byte[] ret = new byte[l];
        for (int i = 0; i < l; i++)
        {
            m=i*2+1;
            n=m+1;
            ret[i] = Byte.decode("0x" + src.substring(i*2, m) + src.substring(m,n));
        }
        return ret;
    }

    public void showByteArrayInTextView(byte[] data) {
        String hexString = byteArrayToHexStr(data);
        mHandler.sendMessage(mHandler.obtainMessage(0,hexString));
    }

    public static String byteArrayToHexStr(byte[] byteArray) {
        if (byteArray == null){
            return null;
        }
        char[] hexArray = "0123456789ABCDEF".toCharArray();
        char[] hexChars = new char[byteArray.length * 2];
        for (int j = 0; j < byteArray.length; j++) {
            int v = byteArray[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars);
    }

    public void  configUartMode() {
        if(mDeviceConnection != null) {
            uartInit();
            setUartConfig(baurt, (byte) 8, (byte) 1, (byte) 0, (byte) 0);
            Log.d(TAG,"config UartMode is ok");
        } else {
            Log.e(TAG,"failed to config Uart mode");
        }
    }

    public boolean uartInit(){
        int ret;
        int size = 8;
        byte[] buffer = new byte[size];
        uartControlOut(UartCmd.VENDOR_SERIAL_INIT, 0x0000, 0x0000);
        ret = uartControlIn(UartCmd.VENDOR_VERSION, 0x0000, 0x0000, buffer, 2);
        if(ret < 0)
            return false;
        uartControlOut(UartCmd.VENDOR_WRITE, 0x1312, 0xD982);
        uartControlOut(UartCmd.VENDOR_WRITE, 0x0f2c, 0x0004);
        ret = uartControlIn(UartCmd.VENDOR_READ, 0x2518, 0x0000, buffer, 2);
        if(ret < 0)
            return false;
        uartControlOut(UartCmd.VENDOR_WRITE, 0x2727, 0x0000);
        uartControlOut(UartCmd.VENDOR_MODEM_OUT, 0x00ff, 0x0000);
        return true;
    }

    public int uartControlOut(int request, int value, int index)
    {
        int retval = 0;
        retval = mDeviceConnection.controlTransfer(UsbType.USB_TYPE_VENDOR | UsbType.USB_RECIP_DEVICE | UsbType.USB_DIR_OUT,
                request, value, index, null, 0, DEFAULT_TIMEOUT);

        return retval;
    }

    public int uartControlIn(int request, int value, int index, byte[] buffer, int length)
    {
        int retval = 0;
        retval = mDeviceConnection.controlTransfer(UsbType.USB_TYPE_VENDOR | UsbType.USB_RECIP_DEVICE | UsbType.USB_DIR_IN,
                request, value, index, buffer, length, DEFAULT_TIMEOUT);
        return retval;
    }

    public boolean setUartConfig(int baudRate, byte dataBit, byte stopBit, byte parity, byte flowControl){
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

        uartControlOut(UartCmd.VENDOR_SERIAL_INIT, value, index);
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
        return uartControlOut(UartCmd.VENDOR_MODEM_OUT, ~control, 0);
    }

    private void setInterface(UsbDevice device) {
        if(device != null) {
            mUsbInterface = device.getInterface(0);
        } else {
            Log.e(TAG,"Can't get interface for device" + device.getDeviceName());
        }
    }

    private void setEndpoint() {
        UsbEndpoint epOut = null;
        UsbEndpoint epIn = null;
        if(mUsbInterface != null) {
            for(int i = 0; i < mUsbInterface.getEndpointCount(); i++) {
                UsbEndpoint ep = mUsbInterface.getEndpoint(i);
                if(ep.getType() == UsbConstants.USB_ENDPOINT_XFER_BULK) {
                    if (ep.getDirection() == UsbConstants.USB_DIR_OUT) {
                        epOut = ep;
                    } else {
                        epIn = ep;
                    }
                }
            }
            if(epOut == null || epIn == null) {
                throw new IllegalArgumentException("not all endpoints found");
            }
            mEndpointOut = epOut;
            mEndpointIn = epIn;
        }
    }

    private void setDeviceConnection() {
        if(mDevice != null && mUsbInterface != null) {
            mDeviceConnection = mUsbManager.openDevice(mDevice);
            if(mDeviceConnection.claimInterface(mUsbInterface, true)) {
                Log.d(TAG,"claim Interface succeeded");
            } else {
                Log.e(TAG,"failed to claim interface");
            }
        } else {
            Log.e(TAG,"failed to open device.");
        }
    }

    private void releaseUsbSrc() {
        if(mDeviceConnection != null) {
            if(mUsbInterface != null) {
                mDeviceConnection.releaseInterface(mUsbInterface);
                mUsbInterface = null;
            }
            mDeviceConnection.close();
            mDevice = null;
            mDeviceConnection = null;
        }
    }


    private final BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {

        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (ACTION_USB_PERMISSION.equals(action)) {
                synchronized (this) {
                    UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);

                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        Toast.makeText(
                                MainActivity.this,
                                "ACTION_USB_DEVICE_ATTACHED: \n"
                                        + device.toString(), Toast.LENGTH_LONG)
                                .show();
                        setInterface(device);
                        setEndpoint();
                        setDeviceConnection();
                        configUartMode();
                    } else {
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
