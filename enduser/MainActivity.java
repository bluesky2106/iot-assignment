package one.demo.iot.fsb.iot_demo;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;
import com.hoho.android.usbserial.util.SerialInputOutputManager;
import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;
import com.squareup.okhttp.Callback;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.json.JSONArray;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity implements SerialInputOutputManager.Listener {

    private GraphView temperatureGraph;
    private UsbSerialPort port;
    private MQTTHelper mqttHelperTrafficLight;
    private String buffer = "";


    private static final String ACTION_USB_PERMISSION = "com.android.recipes.USB_PERMISSION";
    private static final String INTENT_ACTION_GRANT_USB = BuildConfig.APPLICATION_ID + ".GRANT_USB";
    //    private static final List<String> SIGNAL_LIST = Arrays.asList("0", "1", "2", "3", "tắt", "đỏ", "xanh", "vàng");
    private static final String LOG_TAG = "IOT_DEMO";
    private static final String[] SUBSCRIPTION_TOPICS = new String[]{
            "akagi/f/dengiaothon",
            "akagi/f/nhietdo"
    };
    private static final int TIMEOUT = 1000;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        temperatureGraph = findViewById(R.id.temperatureGraph);

        createTimer();
        openUART();
        mqttHelperTrafficLight = new MQTTHelper(this, SUBSCRIPTION_TOPICS[0]);
        mqttHelperTrafficLight.setCallback(new MqttCallbackExtended() {
            @Override
            public void connectComplete(boolean reconnect, String serverURI) {
                Log.i(LOG_TAG, "Reconnect?: " + reconnect);
                Log.i(LOG_TAG, "Server URI: " + serverURI);
            }

            @Override
            public void connectionLost(Throwable cause) {
                Log.i(LOG_TAG, "ERROR: " + cause.getMessage());
            }

            @Override
            public void messageArrived(String topic, MqttMessage message) throws Exception {
                Log.i(LOG_TAG, "Received: " + message.toString());
            }

            @Override
            public void deliveryComplete(IMqttDeliveryToken token) {

            }
        });

    }

    private void sendDataMQTT(String data) {

        MqttMessage msg = new MqttMessage();
        msg.setId(1234);
        msg.setQos(0);
        msg.setRetained(true);

        byte[] b = data.getBytes(Charset.forName("UTF-8"));
        msg.setPayload(b);

        Log.i(LOG_TAG, "Publish :" + msg);
        try {
            mqttHelperTrafficLight.mqttAndroidClient.publish(SUBSCRIPTION_TOPICS[0], msg);

        } catch (MqttException e) {
            Log.i(LOG_TAG, "ERROR :" + e.getMessage());
        }
    }

    private void openUART() {
        UsbManager manager = (UsbManager) getSystemService(Context.USB_SERVICE);
        List<UsbSerialDriver> availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(manager);
        Log.i(LOG_TAG, "Driver List's size: " + availableDrivers.size());
        if (availableDrivers.isEmpty()) {
            Log.i(LOG_TAG, "UART is not available");
        } else {
            Log.i(LOG_TAG, "UART is available");

            UsbSerialDriver driver = availableDrivers.get(0);
            UsbDeviceConnection connection = manager.openDevice(driver.getDevice());
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
//                    port.close();
                } catch (Exception e) {
                    Log.i(LOG_TAG, e.getMessage());
                }
            }
        }
    }

    public void clickToTurnOffAllLight(View v) {
        try {
            port.write("0#".getBytes(), TIMEOUT);
            sendDataMQTT("0");
        } catch (Exception e) {
            Log.e(LOG_TAG, e.getMessage());
        }
    }

    public void clickToRedLight(View v) {
        try {
            port.write("1#".getBytes(), TIMEOUT);
            sendDataMQTT("1");
        } catch (Exception e) {
            Log.e("TEST_DEMO", e.getMessage());
        }
    }

    public void clickToYellowLight(View v) {
        try {
            port.write("2#".getBytes(), TIMEOUT);
            sendDataMQTT("2");
        } catch (Exception e) {
            Log.e("TEST_DEMO", e.getMessage());
        }
    }

    public void clickToGreenLight(View v) {
        try {
            port.write("3#".getBytes(), TIMEOUT);
            sendDataMQTT("3");
        } catch (Exception e) {
            Log.e("TEST_DEMO", e.getMessage());
        }
    }


    @Override
    public void onNewData(byte[] data) {
        buffer += new String(data);
        if (buffer.length() == 6) {
            buffer = "";
        }
    }

    @Override
    public void onRunError(Exception e) {
        buffer = "Error";
        Log.i(LOG_TAG, buffer);
        buffer = "";
    }

    private void showDataOnGraph(LineGraphSeries<DataPoint> series, GraphView graph) {
        if (graph.getSeries().size() > 0) {
            graph.getSeries().remove(0);
        }
        graph.addSeries(series);
        series.setDrawDataPoints(true);
        series.setDataPointsRadius(10);
        graph.onDataChanged(true, true);
    }

    private void getDataFromAdafruit() {
        OkHttpClient client = new OkHttpClient();
        Request.Builder builder = new Request.Builder();
        String url = "https://io.adafruit.com/api/v2/akagi/feeds/nhietdo/data";
        Request request = builder.url(url).build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Request request, IOException e) {
                Log.i(LOG_TAG, "ERROR" + e.getMessage());
            }

            @Override
            public void onResponse(Response response) throws IOException {
                try {
                    String jsonString = response.body().string();
                    JSONArray array = new JSONArray(jsonString);
                    DataPoint[] dataPointTemperature = new DataPoint[array.length()];
                    for(int i =0; i<array.length(); i++){
                        try{
                            int value = array.getJSONObject(i).getInt("value");
                            dataPointTemperature[i] = new DataPoint(i, value);
                        }catch(Exception e){
                            Log.i(LOG_TAG, "ERROR" + e.getMessage());
                        }
                    }
                    LineGraphSeries<DataPoint> seriesTemperature = new LineGraphSeries<>(dataPointTemperature);
                    showDataOnGraph(seriesTemperature, temperatureGraph);
                }catch (Exception e){
                    Log.i(LOG_TAG, "ERROR" + e.getMessage());
                }
            }
        });

    }

    private void createTimer(){
        Timer timer = new Timer();
        TimerTask task = new TimerTask() {
            @Override
            public void run() {
                getDataFromAdafruit();
            }
        };
        timer.schedule(task, 5000, 10000);
    }
}
