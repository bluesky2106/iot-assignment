package online.dantrisoft.gateway;

import androidx.appcompat.app.AppCompatActivity;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;
import com.hoho.android.usbserial.util.SerialInputOutputManager;

import org.eclipse.paho.android.service.BuildConfig;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;

import java.nio.charset.Charset;
import java.util.List;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity implements SerialInputOutputManager.Listener {
    MQTTService mqttService;

    private static final String ACTION_USB_PERMISSION = "com.android.recipes.USB_PERMISSION";
    private static final String INTENT_ACTION_GRANT_USB = BuildConfig.APPLICATION_ID + ".GRANT_USB";
    UsbManager manager;
    UsbSerialPort port;
    UsbSerialDriver driver;
    UsbDeviceConnection connection;

    TextView txtTest;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        txtTest = findViewById(R.id.txtTest);

        mqttService = new MQTTService(this);
        mqttService.setCallback(new MqttCallbackExtended() {
            @Override
            public void connectComplete(boolean reconnect, String serverURI) {

            }

            @Override
            public void connectionLost(Throwable cause) {

            }

            @Override
            public void messageArrived(String topic, MqttMessage message) throws Exception {
                if (topic.equals(mqttService.subscriptionTopic1)) {
                    String msg = message.toString() + "#";
                    txtTest.setText(msg);
                    port.write(msg.getBytes(), 1000);
                }
            }

            @Override
            public void deliveryComplete(IMqttDeliveryToken token) {

            }
        });
        mqttService.connect();

        openUART();
    }

    private void sendTemperatureMQTT(String data){
        MqttMessage msg = new MqttMessage();
        msg.setId(1234);
        msg.setQos(0);
        msg.setRetained(true);

        byte[] b = data.getBytes(Charset.forName("UTF-8"));
        msg.setPayload(b);

        try {
            mqttService.mqttAndroidClient.publish(mqttService.subscriptionTopic2, msg);

        } catch (MqttException e){
        }
    }

    private  void openUART() {
        manager = (UsbManager) getSystemService(Context.USB_SERVICE);
        List<UsbSerialDriver> availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(manager);

        if (!availableDrivers.isEmpty()) {
            driver = availableDrivers.get(0);
            connection = manager.openDevice(driver.getDevice());
            if (connection == null) {

                PendingIntent usbPermissionIntent = PendingIntent.getBroadcast(this, 0, new Intent(INTENT_ACTION_GRANT_USB), 0);
                manager.requestPermission(driver.getDevice(), usbPermissionIntent);

                manager.requestPermission(driver.getDevice(), PendingIntent.getBroadcast(this, 0, new Intent(ACTION_USB_PERMISSION), 0));

                return;
            } else {
                port = driver.getPorts().get(0);
                try {
                    port.open(connection);
                    port.setParameters(115200, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE);

//                    port.write("ABC#".getBytes(), 1000);

                    SerialInputOutputManager usbIoManager = new SerialInputOutputManager(port, this);
                    Executors.newSingleThreadExecutor().submit(usbIoManager);
                } catch (Exception e) {

                }
            }
        }
    }

    @Override
    public void onNewData(byte[] data) {
        String temperature = new String(data);
        sendTemperatureMQTT(temperature);
    }

    @Override
    public void onRunError(Exception e) {

    }
}