//#include <ArduinoBLE.h>
#include <ArduinoUniqueID.h>

int incomingByte = 0;
const unsigned long timeGap = 5000;
unsigned long prevTime = 0;
int printcount = -1;

//String bleDeviceAddress = "ns";
char *uniqueid;
int a0pin = A0;
int a1pin = A1;
int analogVal0 = 0;
int analogVal1 = 0;
int prevAnalogVal = -1;
short a0intervalmax = 0;
short lastintervala0max = 0;
//short a0samples[1024];
short a0ind = 0;
unsigned long sum = 0;
int touchmode = 0;
int touchmillistart;
int posttouchloops;
const int minPostTouchLowValLoops = 4;
int postTouchLowValLoops = 0;

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
  }
  if (prevTime == 0) {
    prevTime = curMillis;
  }

  analogVal0 = analogRead(a0pin);
  analogVal1 = analogRead(a1pin);
  
  int adiff = abs(analogVal0 - analogVal1);
      //if (adiff > 5) {
      //    Serial.print(analogVal0 - analogVal1);
      //    Serial.print(" ");
      //}
  if (touchmode <= 3) {
    if (analogVal0 - analogVal1 > 900) {
      touchmode++;
      //Serial.print("TM");
      //Serial.print(touchmode);
      //Serial.print(" ");
      if (touchmode == 4) {
        touchmillistart = curMillis;
      }
    } else {
      touchmode = 0;
    }
  } else if (touchmode == 4) {
    if (analogVal0 < 980) {
      touchmode = 0;
      //Serial.print("TMcancel ");
    } else {
      if (curMillis - touchmillistart >= 15) {
        Serial.print("TOUCH! ");
        touchmode = 5;
        posttouchloops = 0;
        postTouchLowValLoops = 0;
      }
    }
  } else {
    posttouchloops++;
    if (analogVal0 < 900) {
      postTouchLowValLoops++;
    } else {
      postTouchLowValLoops = 0;
    }

    if (postTouchLowValLoops >= minPostTouchLowValLoops) {
      /*
      Serial.print(analogVal0);
      Serial.print(" ");
      Serial.print(posttouchloops);
      Serial.print(" ");
      */
      touchmode = 0;
      // Serial.print("TMreset ");
    }
  }

  if (printcount >= 0) {
     sum += analogVal0;
        //Serial.print(analogVal0);
        //Serial.print(" ");
      printcount++;
      /*
      Serial.print("0-");
      Serial.print(analogVal0);
      Serial.print(" 1-");
      Serial.print(analogVal1);
      Serial.print(" ");
      */
 
      if (printcount >= 100) {
          printcount = -1;
          Serial.print("s");
          Serial.print((double)sum / 100);
          Serial.print(" ");
          //Serial.print(curMillis - prevTime);
          //Serial.print(" ");
          sum = 0;
      }
  }
  

/*
  if (analogVal0 > a0intervalmax) {
    a0intervalmax = analogVal0;
  }
  a0samples[a0ind] = analogVal0;
  a0ind++;
  if (a0ind >= 1024) {
    a0ind = 0;

    //if (abs(lastintervala0max - a0intervalmax) > 35) {
        //Serial.print(a0intervalmax);
        //Serial.print(" ");
    //}
    lastintervala0max = a0intervalmax;
    a0intervalmax = 0;
  }
  */
  //if (abs(analogVal0 - prevAnalogVal) > 50) {
  //  Serial.print(analogVal0);
  //  Serial.print(" ");
  //  prevAnalogVal = analogVal0;
  //}


  if ((prevTime != 0) && (curMillis - prevTime >= timeGap)) {
    //Serial.print(bleDeviceAddress);
    //Serial.print(UniqueIDsize);
    //Serial.print(" ");
    //Serial.print(uniqueid);
    //Serial.print(" ");
    prevTime = curMillis;
    //printcount = 0;
  } 
}