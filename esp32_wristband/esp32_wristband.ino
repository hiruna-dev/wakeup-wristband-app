#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLEUtils.h>
#include <BLE2902.h>

// Connect your LED to GPIO 2 (D2)
#define LED_PIN 2

BLEServer *pServer = NULL;
BLECharacteristic *pRxCharacteristic;
bool deviceConnected = false;
bool oldDeviceConnected = false;

// See the following for generating UUIDs:
// https://www.uuidgenerator.net/
#define SERVICE_UUID           "6E400001-B5A3-F393-E0A9-E50E24DCCA9E"
#define CHARACTERISTIC_UUID_RX "6E400002-B5A3-F393-E0A9-E50E24DCCA9E"

class MyServerCallbacks: public BLEServerCallbacks {
    void onConnect(BLEServer* pServer) {
      deviceConnected = true;
      Serial.println("Device connected");
    };

    void onDisconnect(BLEServer* pServer) {
      deviceConnected = false;
      Serial.println("Device disconnected");
      digitalWrite(LED_PIN, LOW); // Turn off LED when app disconnects
    }
};

class MyCallbacks: public BLECharacteristicCallbacks {
    void onWrite(BLECharacteristic *pCharacteristic) {
      String rxValue = pCharacteristic->getValue().c_str();

      if (rxValue.length() > 0) {
        Serial.println("*********");
        Serial.print("Received Value: ");
        for (int i = 0; i < rxValue.length(); i++)
          Serial.print(rxValue[i]);
        Serial.println();
        Serial.println("*********");

        if (rxValue == "ON") {
          Serial.println("Turning LED ON");
          digitalWrite(LED_PIN, HIGH);
        } else if (rxValue == "OFF") {
          Serial.println("Turning LED OFF");
          digitalWrite(LED_PIN, LOW);
        }
      }
    }
};

void setup() {
  Serial.begin(115200);
  delay(2000); // Give the serial port some time to open
  Serial.println("Starting ESP32 Wristband Firmware...");

  pinMode(LED_PIN, OUTPUT);
  digitalWrite(LED_PIN, LOW);

  Serial.println("Initializing BLE Device...");
  // Initialize the Bluetooth LE environment
  BLEDevice::init("WRISTBAND_WAKEUP"); // The exact name the app will scan for

  Serial.println("Creating BLE Server...");
  // Create the BLE Server
  pServer = BLEDevice::createServer();
  pServer->setCallbacks(new MyServerCallbacks());

  Serial.println("Creating BLE Service...");
  // Create the BLE Service
  BLEService *pService = pServer->createService(SERVICE_UUID);

  Serial.println("Creating BLE Characteristic...");
  // Create a BLE Characteristic
  pRxCharacteristic = pService->createCharacteristic(
                       CHARACTERISTIC_UUID_RX,
                       BLECharacteristic::PROPERTY_WRITE
                     );

  pRxCharacteristic->setCallbacks(new MyCallbacks());

  Serial.println("Starting BLE Service...");
  // Start the service
  pService->start();

  Serial.println("Configuring BLE Advertising...");
  // Start advertising
  BLEAdvertising *pAdvertising = BLEDevice::getAdvertising();
  pAdvertising->addServiceUUID(SERVICE_UUID);
  pAdvertising->setScanResponse(true);
  pAdvertising->setMinPreferred(0x06);  // functions that help with iPhone connections issue
  pAdvertising->setMinInterval(0x20); // 20msec intervals (faster discovery)
  pAdvertising->setMaxInterval(0x40); 
  
  Serial.println("Executing startAdvertising()...");
  BLEDevice::startAdvertising();
  
  Serial.println("SUCCESS! Advertising started. Waiting a client connection to notify...");
}

void loop() {
    // disconnecting
    if (!deviceConnected && oldDeviceConnected) {
        delay(500); // give the bluetooth stack the chance to get things ready
        pServer->startAdvertising(); // restart advertising
        Serial.println("start advertising");
        oldDeviceConnected = deviceConnected;
    }
    // connecting
    if (deviceConnected && !oldDeviceConnected) {
        // do stuff here on connecting
        oldDeviceConnected = deviceConnected;
    }
}
