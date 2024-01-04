#include <ArduinoUniqueID.h>

// defines for setting and clearing register bits
#ifndef cbi
#define cbi(sfr, bit) (_SFR_BYTE(sfr) &= ~_BV(bit))
#endif
#ifndef sbi
#define sbi(sfr, bit) (_SFR_BYTE(sfr) |= _BV(bit))
#endif

#define timeGap 3000UL  // millis
unsigned long prevTime = 0;
bool doLameDetection = false;
unsigned long lameDetectionDisableTime;
unsigned long pcounts[9];
int latestLameFreq = 0;
int freqDiff = 0;
int matchingTargetIntervals = 0;
int printcount = -1;

String bleDeviceAddress = "ns";
char *uniqueid;
#define a0pin A0
#define a1pin A1
#define a2pin A2
#define d2pin 2
short analogVal0 = 0;
short analogVal1 = 0;
int prevAnalogVal = -1;
short a0intervalmax = 0;
short lastintervala0max = 0;
short a0ind = 0;
int touchmode = 0;
unsigned long touchmillistart;
int loopssincecondition = 0;
#define MODE4SIZE 600
int otherSwordFreq = 0;
int swordFreq = 0;
#define MARGIN 400
#define LOOPSINCONDITION 10
long drhigh = 0;
//#define OLD_LAME 1
#ifdef OLD_LAME
#define NEEDED_PULSES 2
//#define MEASURE_TOUCH_LENGTH 1
#else
#define NEEDED_PULSES 3 // for debouncing
volatile bool contactDetected = false;
unsigned long periodMin;
unsigned long periodMax;
#endif

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
    otherSwordFreq = 3900;  // 3300 under 5V computer power
    swordFreq = 4800; // 4600 under 5V computer power
    periodMin = 232;
    periodMax = 286;
  } else {
    otherSwordFreq = 4800; // 4600 under 5V computer power
    swordFreq = 3900; // 3300 under 5V computer power
    periodMin = 192;
    periodMax = 228;
  }
  pinMode(d2pin, INPUT);
  for(int i = 0; i < 9; ++i) {
    pcounts[i] = 0;
  }
#ifndef OLD_LAME
  attachInterrupt(0, lameRead, RISING);
#endif
}

void lameRead() {
  static int contactCount = 0;
  static unsigned long lastLameRead = micros();
  unsigned long nowTime = micros();
  
  if (!contactDetected) {
    unsigned long period = nowTime - lastLameRead;
    if ((period > periodMin) && (period < periodMax)) {
      ++contactCount;
      if (contactCount >= NEEDED_PULSES) {
        contactCount = NEEDED_PULSES;
        contactDetected = true;
      } 
    }
    else {
      --contactCount;
      if (contactCount < 0) {
        contactCount = 0;
      }
    }
  }
  lastLameRead = nowTime;
}

void sendIsTouched() {
  Serial.print("1");
}

void sendTouched() {
  Serial.print("2");
}

void sendText(char *s) {
  char slen[100];
  sprintf(slen, "3%4d%s", strlen(s), s);
  Serial.print(slen);
}

void sendInt(int s) {
  char buf[20];
  sprintf(buf, "%d", s);
  sendText(buf);
}

void loop() {
  int i;

  unsigned long curMillis = millis();

  if (prevTime == 0) {
    prevTime = curMillis;
    doLameDetection = true;
  }

  if (!doLameDetection) {
    unsigned long timeDiff = curMillis - lameDetectionDisableTime;
    if (timeDiff > 2000UL) {
      doLameDetection = true;
      //sendText("RLD");
#ifndef OLD_LAME
      contactDetected = false;
#endif
    }
  }

  int phigh=13, plow=13;

//#define DOPCOUNTS 1
#if OLD_LAME
  if (doLameDetection) {
    drhigh++;
#ifdef DOPCOUNTS
    pcounts[0]++;
#endif
    for(int j = 0; j < NEEDED_PULSES; ++j) {
#ifdef DOPCOUNTS
      pcounts[1 + j * 4]++;
#endif
      phigh = pulseIn(d2pin, LOW, 1000);
      if (phigh != 0) {
#ifdef DOPCOUNTS
        pcounts[2 + j * 4]++;
#endif
        plow = pulseIn(d2pin, HIGH, 1000);
        if (plow != 0) {
#ifdef DOPCOUNTS
          pcounts[3 + j * 4]++;
#endif
          latestLameFreq = 1000000 / (phigh + plow);
          freqDiff = latestLameFreq - otherSwordFreq;

          //if (abs(latestLameFreq - otherSwordFreq) < MARGIN) {
          if (freqDiff > -MARGIN && freqDiff < MARGIN) {
#ifdef DOPCOUNTS
            pcounts[4 + j * 4]++;
#endif
            if (j == NEEDED_PULSES - 1) {
              sendIsTouched();
              sendInt(latestLameFreq);
              //disableInt0();
              doLameDetection = false;
              lameDetectionDisableTime = curMillis;
            }
          } else {
            break;
          }
        } else {
          break;
        }
      } else {
        break;
      }
    }
  }
#else
  if (doLameDetection) {
    if (contactDetected) {
      sendIsTouched();
      doLameDetection = false;
      lameDetectionDisableTime = curMillis;
    }
  }
#endif

  analogVal0 = analogRead(a0pin);

#ifdef OLD_LAME
  delayMicroseconds(100);
#endif

#if 0
  unsigned long realTimeGap = curMillis - prevTime;
  if ((prevTime != 0) && (realTimeGap >= timeGap)) {
    prevTime = curMillis;
    //sendInt(analogVal0);
    /*
    char tgbuf[100];
    sprintf(tgbuf, "(%d %lu %lu %lu %lu %lu %lu %lu %lu %lu)", latestLameFreq, 
        pcounts[0], 
        pcounts[1], 
        pcounts[2], 
        pcounts[3], 
        pcounts[4], 
        pcounts[5], 
        pcounts[6], 
        pcounts[7],
        pcounts[8]);
    sendText(tgbuf);
    */
  }
#endif
    
  if (analogVal0 > 590) {
    loopssincecondition = 0;
    if (touchmode == 0) {
      touchmillistart = curMillis;
      touchmode = 1;
    } else if (touchmode == 1) {
      unsigned long diff = curMillis - touchmillistart;
      if (diff >= 15UL) {
        sendTouched();
        /*
        sendInt(otherSwordFreq);
        sendInt(periodMin);
        sendInt(periodMax);
        */
        touchmode = 2;
      }
    }
  } else {
    loopssincecondition++;
    if (touchmode > 0) {
      if (loopssincecondition > 1000) {
        touchmode = 0;
      }
    }
  }
}