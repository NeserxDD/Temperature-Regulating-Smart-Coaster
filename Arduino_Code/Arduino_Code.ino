#include <Wire.h>
#include <Adafruit_MLX90614.h>
#include <LiquidCrystal_I2C.h>
#include "BluetoothSerial.h"

// Create an instance of the MLX90614 temperature sensor
Adafruit_MLX90614 mlx = Adafruit_MLX90614();
// Create an instance of the LCD (address 0x27, 16 characters, 2 lines)
LiquidCrystal_I2C lcd(0x27, 16, 2);

#define TRIG_PIN 5   // Trig pin connected to GPIO 5 (Ultrasonic sensor)
#define ECHO_PIN 18  // Echo pin connected to GPIO 18 (Ultrasonic sensor)
#define relay1 14    // Relay connected to GPIO 14

BluetoothSerial SerialBT;  // Create a Bluetooth serial object
int prefertemp = 40;       // Preferred temperature (default 70°C)
int tresholdHigh = 0;
int tresholdLow = 0;
float averageDifference = 0;
float realTemp = 0;

bool isConnected = false;  // Bluetooth connection status
bool deviceState = true;   //  (ON/OFF) of smart coaster


void setup() {
  Serial.begin(115200);  // Initialize serial communication at 115200 baud rate

  // Initialize I2C communication on GPIO 21 (SDA) and GPIO 22 (SCL)
  Wire.begin(21, 22);

  // Initialize the MLX90614 sensor
  if (!mlx.begin()) {
    Serial.println("Error connecting to MLX90614 sensor. Check your wiring!");
    while (1)
      ;
  }

  // Set up the relay pin
  pinMode(relay1, OUTPUT);
  digitalWrite(relay1, LOW);  // Start with the relay OFF

  // Initialize Bluetooth with the name "Smart_Coaster"
  SerialBT.begin("Smart_Coaster");
  Serial.println("Bluetooth Started");

  // Initialize the LCD
  lcd.begin(16, 2);
  lcd.backlight();      // Turn on the LCD backlight
  lcd.setCursor(1, 0);  // Set cursor to column 1, row 0
  lcd.print("Starting Smart");

  // Print "Coaster" on the second row of the LCD
  lcd.setCursor(5, 1);  // Set cursor to column 5, row 1
  lcd.print("Coaster");
  delay(5000);  // Wait 5 seconds
  lcd.clear();

  // Initialize the Ultrasonic sensor (HC-SR04) pins
  pinMode(TRIG_PIN, OUTPUT);  // Set trigger pin as output
  pinMode(ECHO_PIN, INPUT);   // Set echo pin as input
}



void loop() {
  // Measure the distance using the Ultrasonic sensor
  long duration, distance;
  digitalWrite(TRIG_PIN, LOW);  // Set the trigger pin low for 2 microseconds
  delayMicroseconds(2);
  digitalWrite(TRIG_PIN, HIGH);  // Set the trigger pin high for 10 microseconds
  delayMicroseconds(10);
  digitalWrite(TRIG_PIN, LOW);         // Set the trigger pin low again
  duration = pulseIn(ECHO_PIN, HIGH);  // Measure the duration of the pulse
  distance = long(duration) / 29 / 2;  // Convert duration to distance in cm

  // Print the measured distance to the Serial Monitor
  Serial.print("Distance: ");
  Serial.print(distance);
  Serial.println(" cm");

  // Check if the Bluetooth device is connected
  if (SerialBT.connected()) {
    isConnected = true;

    // Read any available Bluetooth data
    if (SerialBT.available()) {
      String received = SerialBT.readStringUntil('\n');  // Read incoming string
      received.trim();                                   // Remove any extra whitespace or newline characters

      // Turn the coaster on
      if (received.equalsIgnoreCase("on")) {
        deviceState = true;
        Serial.println("PTC Heater ON");
      } else if (received.equalsIgnoreCase("off")) {
        deviceState = false;
        Serial.println("PTC Heater OFF");
      }

      // Set preferred temperature if "N1" command is received
      if (received.startsWith("N1")) {
        prefertemp = received.substring(2).toInt();  // Extract the number after "N1"
        Serial.print("Received prefertemp: ");
        Serial.println(prefertemp);
      }
      // Set tresholdHigh if "N2" command is received
      else if (received.startsWith("N2")) {
        tresholdHigh = received.substring(2).toInt();  // Extract the number after "N2"
        Serial.print("Received tresholdHigh: ");
        Serial.println(tresholdHigh);
      }
      // Set tresholdLow if "N3" command is received
      else if (received.startsWith("N3")) {
        tresholdLow = received.substring(2).toInt();  // Extract the number after "N3"
        Serial.print("Received tresholdLow: ");
        Serial.println(tresholdLow);
      }

      else if (received.startsWith("N4")) {
        averageDifference = received.substring(2).toInt();  // Extract the number after "N3"
        Serial.print("Received tresholdLow: ");
        Serial.println(tresholdLow);
      }
    }
  } else {
    isConnected = false;
  }


  if (deviceState == true) {
    if (distance < 9) {  // Cup detected within 30 cm

      // Read object temperature from the MLX90614 sensor
      float objectTemp = mlx.readObjectTempC();

      realTemp = objectTemp + averageDifference;  // adds the mlx temperature and the difference of mlx temperature and real temperature

      String realtempDecimal = String(realTemp, 1);

      String stringRealTemp = String(realTemp) + "\n";

      SerialBT.print(stringRealTemp);
      // Send object temperature to Bluetooth app





      // Turn on the PTC heater if the temperature is below the preferred setting
      if (realTemp <= prefertemp + tresholdHigh) {
        digitalWrite(relay1, HIGH);  // Turn on the relay
        Serial.println("PTC Heater ON");
      }
      // Turn off the PTC heater if the temperature exceeds the preferred setting
      else if (realTemp >= prefertemp - tresholdLow) {
        digitalWrite(relay1, LOW);  // Turn off the relay
        Serial.println("PTC Heater OFF");
      } else {
        digitalWrite(relay1, LOW);  // Ensure PTC is off in case of errors
        Serial.println("Error PTC OFF");
      }

      // // Print object temperature to the Serial Monitor
      // Serial.print("Object Temperature: ");
      // Serial.print(objectTemp);
      // Serial.println(" °C");

      // Display the current and preferred temperatures on the LCD
      lcd.clear();
      lcd.setCursor(0, 0);  // Set cursor to row 0
      lcd.print("CT:");
      lcd.print(realtempDecimal);  // Print current temperature
      lcd.print("C");

      lcd.setCursor(10, 0);  // Set cursor to row 0, column 9
      lcd.print("PT:");
      lcd.print(int(prefertemp));  // Print preferred temperature
      lcd.print("C");

      // Display Bluetooth connection status on the second row of the LCD
      lcd.setCursor(0, 1);  // Set cursor to row 1
      lcd.print(isConnected ? "   Connnected" : "  Disconnected");

      // Delay before the next reading
      delay(1000);
    } else {
      digitalWrite(relay1, LOW);  // Ensure PTC is off in case of errors


      // If no cup is detected, display "No Cup Detected" on the LCD
      lcd.clear();
      lcd.setCursor(0, 0);  // Set cursor to first line
      lcd.print("No Cup Detected");
      delay(1000);  // Delay for 1 second
    }
  } else {

    digitalWrite(relay1, LOW);  // Ensure PTC is off in case of errors

    delay(1000);
  }
}
