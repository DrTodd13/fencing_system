//#include <ArduinoBLE.h>
#include <ArduinoUniqueID.h>

int incomingByte = 0;
const unsigned long timeGap = 5000;
unsigned long prevTime = 0;

//String bleDeviceAddress = "ns";
char *uniqueid;
int a0pin = A0;
int analogVal = 0;
int prevAnalogVal = -1;

void setup() {
  Serial.begin(115200);               //initial the Serial
  //bleDeviceAddress = BLE.address();
  uniqueid = (char*) malloc(2 * UniqueIDsize + 1);
  for (int i = 0; i < UniqueIDsize; i++) {
    sprintf(uniqueid + 2*i, "%02x", UniqueID[i]);
  }
  uniqueid[2 * UniqueIDsize] = '\0';
}

void loop()
{
  unsigned long curMillis = millis();

  if (Serial.available()) {
    incomingByte = Serial.read();
    Serial.write(incomingByte);
    if (prevTime == 0) {
      prevTime = curMillis;
    }
  }

  analogVal = analogRead(a0pin);
  if (abs(analogVal - prevAnalogVal) > 50) {
    Serial.print(analogVal);
    Serial.print(" ");
    prevAnalogVal = analogVal;
  }

  if ((prevTime != 0) && (curMillis - prevTime >= timeGap)) {
    //Serial.print(bleDeviceAddress);
    //Serial.print(UniqueIDsize);
    //Serial.print(" ");
    //Serial.print(uniqueid);
    //Serial.print(" ");
    prevTime = curMillis;
  }
}


