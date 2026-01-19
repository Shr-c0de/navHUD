#include <Arduino.h>
#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLEUtils.h>
#include <Wire.h>
#include <Adafruit_GFX.h>
#include <Adafruit_SSD1306.h>

#define SCREEN_WIDTH 128
#define SCREEN_HEIGHT 64
#define OLED_RESET -1
Adafruit_SSD1306 display(SCREEN_WIDTH, SCREEN_HEIGHT, &Wire);

#define LEDPIN 2
#define SERVICE_UUID "4fafc201-1fb5-459e-8fcc-c2d9c331914b"
#define CHARACTERISTIC_UUID "beb5483e-abe1-4688-b7f5-ea07361b26a8"

BLECharacteristic *rxCharacteristic;
std::string rxBuffer;

static constexpr size_t IMAGE_BYTES = 288;
bool isconnected;

const uint8_t logo[] PROGMEM = {
    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x01, 0x80,
    0x00, 0x00, 0x00, 0x00, 0x03, 0xc0, 0x00, 0x00, 0x00, 0x00, 0x03, 0xc0, 0x00, 0x00, 0x00, 0x00,
    0x07, 0xe0, 0x00, 0x00, 0x00, 0x00, 0x07, 0xe0, 0x00, 0x00, 0x00, 0x00, 0x07, 0xe0, 0x00, 0x00,
    0x00, 0x00, 0x0f, 0xf0, 0x00, 0x00, 0x00, 0x00, 0x0f, 0xf0, 0x00, 0x00, 0x00, 0x00, 0x1e, 0x78,
    0x00, 0x00, 0x00, 0x00, 0x1e, 0x78, 0x00, 0x00, 0x00, 0x00, 0x3e, 0x7c, 0x00, 0x00, 0x00, 0x00,
    0x3c, 0x3c, 0x00, 0x00, 0x00, 0x00, 0x3c, 0x3c, 0x00, 0x00, 0x00, 0x00, 0x78, 0x1e, 0x00, 0x00,
    0x00, 0x00, 0x78, 0x1e, 0x00, 0x00, 0x00, 0x00, 0xf8, 0x1f, 0x00, 0x00, 0x00, 0x00, 0xf0, 0x0f,
    0x00, 0x00, 0x00, 0x01, 0xf0, 0x0f, 0x80, 0x00, 0x00, 0x01, 0xe0, 0x07, 0x80, 0x00, 0x00, 0x01,
    0xe0, 0x07, 0x80, 0x00, 0x00, 0x03, 0xc0, 0x03, 0xc0, 0x00, 0x00, 0x03, 0xc0, 0x03, 0xc0, 0x00,
    0x00, 0x07, 0xc0, 0x03, 0xe0, 0x00, 0x00, 0x07, 0x80, 0x01, 0xe0, 0x00, 0x00, 0x0f, 0x80, 0x01,
    0xf0, 0x00, 0x00, 0x0f, 0x00, 0x00, 0xf0, 0x00, 0x00, 0x0f, 0x00, 0x00, 0xf0, 0x00, 0x00, 0x1e,
    0x00, 0x00, 0x78, 0x00, 0x00, 0x1e, 0x00, 0x00, 0x78, 0x00, 0x00, 0x3c, 0x00, 0x00, 0x3c, 0x00,
    0x00, 0x3c, 0x00, 0x00, 0x3c, 0x00, 0x00, 0x3c, 0x03, 0xc0, 0x3c, 0x00, 0x00, 0x78, 0x0f, 0xf0,
    0x1e, 0x00, 0x00, 0x78, 0x3f, 0xfc, 0x1e, 0x00, 0x00, 0xf0, 0xff, 0xff, 0x0f, 0x00, 0x00, 0xf1,
    0xfe, 0x7f, 0x8f, 0x00, 0x01, 0xf7, 0xf8, 0x1f, 0xef, 0x80, 0x01, 0xff, 0xe0, 0x07, 0xff, 0x80,
    0x03, 0xff, 0x80, 0x01, 0xff, 0xc0, 0x03, 0xfe, 0x00, 0x00, 0x7f, 0xc0, 0x03, 0xf8, 0x00, 0x00,
    0x1f, 0xc0, 0x07, 0xe0, 0x00, 0x00, 0x07, 0xe0, 0x03, 0xc0, 0x00, 0x00, 0x03, 0xc0, 0x01, 0x00,
    0x00, 0x00, 0x00, 0x80, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00};

const uint8_t bleConnected[] = {
    0x00, 0x00, 0x00, 0x00, 0xf0, 0x00, 0x03, 0xfc, 0x00, 0x0f, 0xff, 0x00, 0x1f, 0x9f, 0x80, 0x1f,
    0x8f, 0x80, 0x3f, 0x87, 0xc0, 0x3e, 0x07, 0xc0, 0x7f, 0x0f, 0xe0, 0x7f, 0x9f, 0xe0, 0x7f, 0x9f,
    0xe0, 0x7f, 0x0f, 0xe0, 0x3e, 0x07, 0xc0, 0x3e, 0x87, 0xc0, 0x1f, 0x8f, 0x80, 0x1f, 0x9f, 0x80,
    0x0f, 0xff, 0x00, 0x03, 0xfc, 0x00, 0x00, 0xf0, 0x00, 0x00, 0x00, 0x00};
/*
Display design:
0,0 to 128,16 is for text (ETA, KM remaining, etc)
0,16 to 48,64 is image
0,48 to 128,64 is directions
*/
void Display(
    const std::vector<uint8_t> &rxBuffer,
    int width,
    int height)
{
    if (rxBuffer.size() < IMAGE_BYTES)
    {

        Serial.println("Not enough data for image");
        display.clearDisplay();

        display.setCursor(30, 0);
        display.setTextColor(0);
        display.setTextSize(2);
        display.setTextWrap(1);
        display.fillRect(0, 0, 128, 16, 1);
        display.print("navHUD");
        display.drawBitmap(40, 16, logo, 48, 48, 1);
        display.fillRect(108, 44, 20, 20, 0);
        if (isconnected)
            display.drawBitmap(108, 44, bleConnected, 20, 20, 1);
        display.display();
        return;
    }
    size_t textSize = rxBuffer.size() - IMAGE_BYTES;

    // Extract text BEFORE the image
    std::string s(
        reinterpret_cast<const char *>(rxBuffer.data()),
        textSize);

    std::string next, eta, direction, data;

    int index = 0, sel = 0;
    for (; index < s.size(); index++)
    {
        if (s[index] == '|' || s[index] == '-')
        {
            sel++;
            if (s[index] == '|')
                continue;
        }
        switch (sel)
        {
        case 0:
            next = next + s[index];
            break;
        case 1:
            direction = direction + s[index];
            break;
        case 2:
            data = data + s[index];
            break;
        case 3:
            data = data + s[index];
            break;
        case 4:
            if ((s[index] == ' ' || s[index] == '-') && eta.size() <= 1)
                continue;
            eta = eta + s[index];
            break;
        }
    }
    Serial.println(next.c_str());
    Serial.println(direction.c_str());
    Serial.println(data.c_str());
    Serial.println(eta.c_str());

    display.setCursor(0, 0);
    display.setTextColor(1);
    display.setTextSize(1);
    display.setTextWrap(1);

    display.fillRect(0, 0, 128, 16, 0);
    display.print(data.c_str());

    display.setCursor(0, 8);
    display.print(eta.c_str());

    display.setCursor(48, 16);
    display.fillRect(48, 16, 128, 44, 0);
    display.setTextSize(2);
    display.print(next.c_str());

    display.setCursor(48, 32);
    display.fillRect(48, 32, 128, 44, 0);
    display.setTextSize(1);
    display.print(direction.c_str());

    // Pointer to last 288 bytes
    const uint8_t *image =
        rxBuffer.data() + (rxBuffer.size() - IMAGE_BYTES) - 1;

    // Sanity check
    if (width * height != IMAGE_BYTES * 8)
    {
        Serial.println("Bitmap dimension mismatch");
        return;
    }
    display.fillRect(0, 16, 48, 48, 0);
    display.drawBitmap(0, 16, image, 48, 48, 1);
    display.fillRect(108, 44, 20, 20, 0);
    if (isconnected)
        display.drawBitmap(108, 44, bleConnected, 20, 20, 1);
    display.display();
}

class ServerCallbacks : public BLEServerCallbacks
{
    void onConnect(BLEServer *server) override
    {
        isconnected = 1;
        Serial.println("Client connected");
        display.fillRect(108, 44, 20, 20, 0);
        display.drawBitmap(108, 44, bleConnected, 20, 20, 1);
        display.display();
        analogWrite(LEDPIN, 0);
    }

    void onDisconnect(BLEServer *server) override
    {
        isconnected = 0;
        analogWrite(LEDPIN, 5);
        Serial.println("Client disconnected");
        display.fillRect(108, 44, 20, 20, 0);
        display.display();
        BLEDevice::startAdvertising();
    }
};
class RxCallbacks : public BLECharacteristicCallbacks
{
    static constexpr size_t MAX_IMAGE_SIZE = 288;
    static constexpr size_t MAX_CHUNKS = 255;

    std::vector<uint8_t> rxBuffer;
    std::vector<bool> received;
    uint8_t expectedChunks = 0;
    bool receiving = false;

    void reset()
    {
        rxBuffer.clear();
        received.clear();
        expectedChunks = 0;
        receiving = false;
    }

    void onWrite(BLECharacteristic *characteristic) override
    {
        std::string value = characteristic->getValue();
        if (value.size() < 2)
            return; // invalid packet

        const uint8_t chunkIndex = value[0];
        const uint8_t totalChunks = value[1];
        const uint8_t *payload = (const uint8_t *)&value[2];
        const size_t payloadLen = value.size() - 2;

        // First packet initializes state
        if (!receiving)
        {
            expectedChunks = totalChunks;
            received.assign(totalChunks, false);
            rxBuffer.resize(totalChunks * 18); // max possible size
            receiving = true;

            Serial.printf("Starting transfer: %d chunks\n", totalChunks);
        }

        // Sanity checks
        if (totalChunks != expectedChunks || chunkIndex >= expectedChunks)
        {
            Serial.println("Protocol error, resetting");
            reset();
            return;
        }

        if (received[chunkIndex])
        {
            return;
        }

        // Copy payload to correct offset
        const size_t offset = chunkIndex * 18;
        memcpy(rxBuffer.data() + offset, payload, payloadLen);
        received[chunkIndex] = true;

        Serial.printf(
            "Received chunk %d/%d (%d bytes)\n",
            chunkIndex + 1, expectedChunks, payloadLen);

        // Check if all chunks arrived
        for (bool ok : received)
            if (!ok)
                return;

        // Trim to actual size
        rxBuffer.resize(
            (expectedChunks - 1) * 18 + payloadLen);

        Serial.printf("Full image received (%d bytes)\n", rxBuffer.size());

        // ---- process image here ----
        Display(rxBuffer, 48, 48);
        Serial.println();
        // --------------------------------

        reset();
    }
};

void BLEinit()
{
    pinMode(LEDPIN, OUTPUT);
    analogWrite(LEDPIN, 5);
    BLEDevice::init("navHUD");

    BLEServer *server = BLEDevice::createServer();
    server->setCallbacks(new ServerCallbacks);
    BLEService *service = server->createService(SERVICE_UUID);

    rxCharacteristic = service->createCharacteristic(
        CHARACTERISTIC_UUID,
        BLECharacteristic::PROPERTY_WRITE |
            BLECharacteristic::PROPERTY_WRITE_NR);

    rxCharacteristic->setCallbacks(new RxCallbacks());

    service->start();

    BLEAdvertising *advertising = BLEDevice::getAdvertising();
    advertising->addServiceUUID(SERVICE_UUID);
    advertising->setScanResponse(true);

    BLEDevice::startAdvertising();
    Serial.println("BLE advertising started");
}

void func()
{
    Serial.println("Scanning I2C bus...");
    bool found = false;

    for (uint8_t addr = 1; addr < 127; addr++)
    {
        Wire.beginTransmission(addr);
        if (Wire.endTransmission() == 0)
        {
            Serial.print("Found device at 0x");
            Serial.println(addr, HEX);
            found = true;
        }
    }

    if (!found)
    {
        Serial.println("No I2C devices found");
    }
}

void setup()
{
    Serial.begin(115200);
    while (!Serial)
        delay(10);
    Serial.println("USB serial OK");

    Wire.begin(8, 9);


    if (!display.begin(SSD1306_SWITCHCAPVCC, 0x3C))
    {
        Serial.println("SSD1306 allocation failed");
        for (;;)
            Serial.println("SSD1306 allocation failed");
    }
    display.clearDisplay();
    display.setCursor(30, 0);
    display.setTextColor(0);
    display.setTextSize(2);
    display.setTextWrap(1);
    display.fillRect(0, 0, 128, 16, 1);
    display.print("navHUD");
    display.drawBitmap(40, 16, logo, 48, 48, 1);
    display.display();
    delay(2000);
    BLEinit();
}

void loop()
{
    delay(1);
}
