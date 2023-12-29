#include <ArduinoUniqueID.h>

// defines for setting and clearing register bits
#ifndef cbi
#define cbi(sfr, bit) (_SFR_BYTE(sfr) &= ~_BV(bit))
#endif
#ifndef sbi
#define sbi(sfr, bit) (_SFR_BYTE(sfr) |= _BV(bit))
#endif

#define timeGap 1500  // millis
#define lameDetectionPeriod 5000  // micros
unsigned long prevTime = 0;
unsigned long startLameDetection0 = 0;
unsigned long startLameDetection1 = 0;
volatile unsigned long freqcount0 = 0;
volatile unsigned long freqcount1 = 0;
volatile unsigned long *freqcountVars[2] = {&freqcount0, &freqcount1};
unsigned long *startLameDetectionVars[2] = {&startLameDetection0, &startLameDetection1};
float latestLameFreq = 0.0;
int matchingTargetIntervals = 0;
int printcount = -1;

String bleDeviceAddress = "ns";
char *uniqueid;
#define a0pin A0
#define a1pin A1
#define a2pin A2
short analogVal0 = 0;
short analogVal1 = 0;
//short analogVal2 = 0;
int prevAnalogVal = -1;
short a0intervalmax = 0;
short lastintervala0max = 0;
//short a0samples[1024];
short a0ind = 0;
unsigned long sum0 = 0;
unsigned long sum1 = 0;
unsigned long sum2 = 0;
unsigned long sum2squared = 0;
int a2max = 0;
int touchmode = 0;
int touchmillistart;
//int posttouchloops;
//const int minPostTouchLowValLoops = 4;
//int postTouchLowValLoops = 0;
int loopssincecondition = 0;
//int loopsmode4 = 0;
#define MODE4SIZE 600
//short a2mode4[MODE4SIZE], i; 
unsigned long otherSwordFreq = 4600;
#define MARGIN 499
#define LOOPSINCONDITION 5
//bool sawoppfreq = false;
//float freqsmode4[100];
unsigned long loopcount = 0;
unsigned long startmode4loopcount = 0;
//unsigned long startprintloopcount = 0;

void disableInt0() {
  EIMSK &= 0b00000010;

  startLameDetection0 = 0;
  startLameDetection1 = 0;
}

void enableInt0(unsigned long curMicros) {
  EIMSK |= 0b00000001;
  EIFR = 0b00000001;

  startLameDetection0 = curMicros;
  startLameDetection1 = curMicros - lameDetectionPeriod/2;
  freqcount0 = 0;
  freqcount1 = 0;
  matchingTargetIntervals = 0;
}

void setup() {
  // set prescale to 16
  /*
  sbi(ADCSRA,ADPS2) ;
  cbi(ADCSRA,ADPS1) ;
  cbi(ADCSRA,ADPS0) ;
  */

  Serial.begin(115200);               //initial the Serial
  //bleDeviceAddress = BLE.address();
  uniqueid = (char*) malloc(2 * UniqueIDsize + 1);
  for (int i = 0; i < UniqueIDsize; i++) {
    sprintf(uniqueid + 2*i, "%02x", UniqueID[i]);
  }
  uniqueid[2 * UniqueIDsize] = '\0';
  if (UniqueID[UniqueIDsize-1] == 0x77) {
    otherSwordFreq = 3600;
  } else {
    otherSwordFreq = 4600;
  }
  //prevTime = millis();
  //startLameDetection = millis();
  //prevTime = 1;
  //startLameDetection = 1;
  attachInterrupt(0, countFrequency, RISING);
  disableInt0();
}

void countFrequency() {
  freqcount0++;
  freqcount1++;
}

void sendIsTouched() {
  Serial.print("1");
}

void sendTouched() {
  Serial.print("2");
}

void sendText(char *s) {
  Serial.print("3");
  char slen[5];
  sprintf(slen, "%4d", strlen(s));
  Serial.print(slen);
  Serial.print(s);
}

void sendInt(int s) {
  char buf[20];
  sprintf(buf, "%d", s);
  sendText(buf);
}

void loop() {
  loopcount++;

  unsigned long curMicros = micros();
  unsigned long curMillis = millis();

  if (Serial.available()) {
    //sendText("ReceivedByte");
    int incomingByte = Serial.read();
    if (incomingByte == '1') {
      if (startLameDetection0 != 0 || startLameDetection1 != 0) {
        // what to do here?
        sendText("Bad startLameDetection");
      } else {
        enableInt0(curMicros);
        //sendText("Restarted Lame Detection! ");
      }
    } else {
      sendText("UnknownCommand");
      sendInt(incomingByte);
    }
    //Serial.write(incomingByte);
  }

  if (prevTime == 0) {
    prevTime = curMillis;
    enableInt0(curMicros);
  }

  analogVal0 = analogRead(a0pin);
  //analogVal1 = analogRead(a1pin);
  //analogVal2 = analogRead(a2pin);
  
  if (startLameDetection0 != 0 && startLameDetection1 != 0) {
    for (int i = 0; i < 2; i++) {
      unsigned long actualLameDetectionPeriod = curMicros - *startLameDetectionVars[i];
      if (actualLameDetectionPeriod >= lameDetectionPeriod) {
        latestLameFreq = (float)*freqcountVars[i] * 1000000.0 / (float)actualLameDetectionPeriod;
        if (abs(latestLameFreq - otherSwordFreq) < MARGIN) {
          matchingTargetIntervals++;
          if (matchingTargetIntervals >= 3) {
            sendIsTouched();
            sendInt(latestLameFreq);
            disableInt0();
          }
        } else {
          matchingTargetIntervals = 0;
        }
        *startLameDetectionVars[i] = curMicros;
        *freqcountVars[i] = 0;
      }
    }
  }

  if (analogVal0 > 40) {
    loopssincecondition = 0;
    if (touchmode <= 3) {
      touchmode++;
      //Serial.print("TM");
      //Serial.print(touchmode);
      //Serial.print(" ");
      //Serial.print(analogVal0);
      //Serial.print(" ");
      if (touchmode == 4) {
        touchmillistart = curMillis;
        //sawoppfreq = false;
        //loopsmode4 = 0;
        startmode4loopcount = loopcount;

        /*
        freqcount = 0;
        long startmicro = micros();
        enableInt0();
        delay(5);
        long stopmicro = micros();
        disableInt0();
        long actualLameDetectionPeriod = stopmicro - startmicro;
        if (actualLameDetectionPeriod < 0) {
          freqcount = 0;
          startmicro = micros();
          enableInt0();
          delay(5);
          stopmicro = micros();
          disableInt0();
          actualLameDetectionPeriod = stopmicro - startmicro;
        }
  
        latestLameFreq = (float)freqcount * 1000 / ((float)actualLameDetectionPeriod / 1000);
        if (abs(latestLameFreq - otherSwordFreq) < MARGIN) {
          sawoppfreq = true;
        }
        */
        //Serial.print(latestLameFreq);
        //Serial.print(" ");
      }
    } else if (touchmode == 4) {
      /*
      if (loopsmode4 < 100) {
        freqsmode4[loopsmode4] = latestLameFreq;
      }
      */
      //Serial.print(latestLameFreq);
      //Serial.print(" ");
      //if (abs(latestLameFreq - otherSwordFreq) < MARGIN) {
      //  sawoppfreq = true;
      //}
      /*
      if (analogVal2 > a2max) {
        a2max = analogVal2;
      }
      sum2 += analogVal2;
      sum2squared += analogVal2 * analogVal2;
      */
      //loopsmode4++;
      //Serial.print("Millis 1: ");
      //Serial.print(curMillis);
      if (curMillis - touchmillistart >= 15) {
        sendTouched();
        sendInt(otherSwordFreq);
        /*
        if (sawoppfreq) {
          Serial.print(F("On Target Touch! "));
        } else {
          Serial.print(F("Off Target Touch! "));
        }
        */
        //Serial.print(loopsmode4);
        //Serial.print(" ");
        //Serial.print(startmode4loopcount);
        //Serial.print(" ");
        //Serial.print(loopcount);
        //Serial.print(" ");
        /*
        for(int i = 0; i < 100 && i < loopsmode4; ++i) {
          Serial.print(freqsmode4[i]);
          Serial.print(" ");
        }
        */
        //Serial.print("TOUCH! ");
        
        //loopsmode4 = 0;
        touchmode = 5;
      }
    } else {
      // Intentionally do nothing.
    }
  } else {
    loopssincecondition++;
    if (touchmode <= 4) {
      if (loopssincecondition >= LOOPSINCONDITION) {
        touchmode = 0;
      }
    } else {
      if (loopssincecondition >= 3 * LOOPSINCONDITION) {
        touchmode = 0;
      }
    }
  }

/*
  if ((prevTime != 0) && (curMillis - prevTime >= timeGap)) {
    //Serial.print(bleDeviceAddress);
    //Serial.print(UniqueIDsize);
    //Serial.print(" ");
    //Serial.print(uniqueid);
    //Serial.print(" ");
    prevTime = curMillis;
    //sendText("0-");
    //sendInt(analogVal0);
    //Serial.print(" ");
    //Serial.print("1-");
    //Serial.print(analogVal1);
    //Serial.print(" ");
    //Serial.print("2-");
    //Serial.print(analogVal2);
    //Serial.print(" ");
    sendInt((int)latestLameFreq);
    //Serial.print(" ");
    //printcount = 0;
    //Serial.print("lc");
    //Serial.print((loopcount - startprintloopcount) / (float)timeGap);
    //Serial.print(" ");
    //startprintloopcount = loopcount;
  }
*/

  /*
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
      //Serial.print(analogVal0);
      //Serial.print(" ");
      //Serial.print(posttouchloops);
      //Serial.print(" ");
      touchmode = 0;
      // Serial.print("TMreset ");
    }
    }
  */
  /*
  if (touchmode <= 3) {
    if (analogVal0 > 30) {
      touchmode++;
      Serial.print("TM");
      Serial.print(touchmode);
      Serial.print(" ");
      loopssincecondition = 0;
      if (touchmode == 4) {
        touchmillistart = curMillis;
        loopssincecondition = 0;
      }
    } else {
      loopssincecondition++;
      if (loopssincecondition >= 5) {
        touchmode = 0;
      }
    }
  } else if (touchmode == 4) {
    if (analogVal0 > 30) {
      loopssincecondition = 0;
      if (curMillis - touchmillistart >= 15) {
        Serial.print("TOUCH! ");
        touchmode = 5;
        loopssincecondition = 0;
      }
    } else {
      loopssincecondition++;
      if (loopssincecondition >= 5) {
        touchmode = 0;
      }
    }
  } else {
    if (analogVal0 > 30) {
      loopssincecondition = 0;
    } else {
      loopssincecondition++;
      if (loopssincecondition >= 5) {
        touchmode = 0;
      }
    }
  }
  */

/*
  if (printcount >= 0) {
      sum0 += analogVal0;
      sum1 += analogVal1;
      sum2 += analogVal2;

      if (analogVal2 > a2max) {
        a2max = analogVal2;
      }
      //Serial.print(analogVal0);
      //Serial.print(" ");
      printcount++;
      
      Serial.print("0-");
      Serial.print(analogVal0);
      Serial.print(" 1-");
      Serial.print(analogVal1);
      Serial.print(" ");
       
      if (printcount >= 100) {
          printcount = -1;
          //Serial.print("s0-");
          //Serial.print((double)sum0 / 100);
          //Serial.print(" ");
          //Serial.print("s1-");
          //Serial.print((double)sum1 / 100);
          //Serial.print(" ");
          if (touchmode >= 4) {
            Serial.print("s2-");
            Serial.print((double)sum2 / 100);
            Serial.print(" ");
            Serial.print("m-");
            Serial.print(a2max);
            Serial.print(" ");
          }
          //Serial.print(curMillis - prevTime);
          //Serial.print(" ");
          sum0 = 0;
          sum1 = 0;
          sum2 = 0;
      }
  }
  */

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
          /*
        originally from start of touchmode 4
        sum2 = 0;
        sum2squared = 0;
        a2max = 0;
        unsigned long beforeread = millis();
        for (i = 0; i < MODE4SIZE; ++i) {
          a2mode4[i] = analogRead(a2pin);
        }
        unsigned long afterread = millis();
        for (i = 0; i < MODE4SIZE; ++i) {
          if (a2mode4[i] > a2max) {
            a2max = a2mode4[i];
          }
          sum2 += a2mode4[i];
          sum2squared += a2mode4[i] * a2mode4[i];
          if (a2mode4[i] > 40) {
            loopssincecondition = 0;
          } else {
            loopssincecondition++;
            if (loopssincecondition >= 10) {
              touchmode = 0;
              Serial.print(F("break "));
              break;
            }
          }
        }
        if (touchmode == 4) {
          Serial.print(a2max);
          Serial.print(F(" "));
          Serial.print((float)sum2 / MODE4SIZE);
          Serial.print(F(" "));
          Serial.print((float)sum2squared / MODE4SIZE);
          Serial.print(F(" "));
          Serial.print(afterread - beforeread);
          Serial.print(F(" "));
        }
        */
        /*
        Serial.print(a2max);
        Serial.print(F(" "));
        Serial.print((float)sum2 / MODE4SIZE);
        Serial.print(F(" "));
        Serial.print((float)sum2squared / MODE4SIZE);
        Serial.print(F(" "));
        
        a2max = 0;
        sum2 = 0;
        sum2squared = 0;
        */

}