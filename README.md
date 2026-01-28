# SecurePower - Anti-Theft Mobile Security App

SecurePower is an anti-theft mobile security application that prevents unauthorized device power-off by requiring authentication. If authentication fails, the app triggers comprehensive security measures including location tracking, owner notifications, and alarm activation.

## Features

### Core Security Features
- **Power-Off Protection**: Intercepts power button press and requires PIN/password authentication
- **Fake Power-Off Screen**: Displays convincing black screen while security measures activate
- **Location Tracking**: Continuous GPS tracking during security alerts
- **SIM Change Detection**: Monitors SIM card changes and triggers alerts
- **Owner Notifications**: Sends email alerts to family members with live tracking links
- **SMS Alerts**: Sends text messages to trusted contacts on security events
- **Loud Alarm**: Plays maximum volume alarm that bypasses silent mode

### Authentication
- Dual-layer protection: PIN (4-6 digits) + Password (8+ characters)
- Biometric support (fingerprint/face recognition)
- Failed attempt tracking with automatic alert after 3 failures

## Technology Stack

### Android Application
- **Language**: Kotlin
- **UI Framework**: Jetpack Compose
- **Architecture**: MVVM
- **Min SDK**: API 26 (Android 8.0)
- **Target SDK**: API 34 (Android 14)

### Backend Services
- **Platform**: Firebase
- **Services**: Authentication, Realtime Database, Cloud Functions, Cloud Messaging
- **Email**: SendGrid API
- **SMS**: Twilio API

## Project Structure

```
newwindow/
├── android/                 # Android native app (Kotlin)
│   └── app/src/main/java/com/antitheft/securepower/
├── firebase/                # Firebase backend
│   ├── functions/          # Cloud Functions (Node.js/TypeScript)
│   └── rules/              # Database security rules
├── shared/                  # Shared resources
│   ├── docs/               # Documentation
│   └── assets/             # Images, icons, sounds
└── README.md
```

## Setup Instructions

### Prerequisites
- Node.js 18+ and npm
- Android Studio (for Android development)
- Firebase CLI: `npm install -g firebase-tools`
- Java 17+
- Kotlin 1.9+

### Firebase Setup

1. Create a Firebase project at https://console.firebase.google.com/
2. Enable the following services:
   - Authentication (Email/Password)
   - Realtime Database
   - Cloud Functions
   - Cloud Messaging (FCM)

3. Install dependencies and deploy:
```bash
cd firebase/functions
npm install
firebase login
firebase init
firebase deploy --only functions
firebase deploy --only database
```

4. Configure external services:
```bash
# Set SendGrid API key
firebase functions:config:set sendgrid.key="YOUR_SENDGRID_KEY"

# Set Twilio credentials
firebase functions:config:set twilio.sid="YOUR_TWILIO_SID"
firebase functions:config:set twilio.token="YOUR_TWILIO_TOKEN"
```

### Android Setup

1. Open `android/` folder in Android Studio
2. Download `google-services.json` from Firebase Console
3. Place it in `android/app/` directory
4. Sync Gradle and build project
5. Run on device or emulator

### Required Permissions (Android)

The app requires the following permissions for security features:
- **Location (Always)**: For device tracking during security alerts
- **Accessibility Service**: To detect power button press
- **Device Administrator**: To prevent app uninstall
- **System Alert Window**: To show authentication dialog over lockscreen
- **Notifications**: For security alerts
- **Phone State**: For SIM change detection

## Security Features

### Power Button Interception
Uses AccessibilityService to detect power button events and intercept shutdown attempts before they occur.

### Fake Power-Off Screen
Displays full-screen black activity while maintaining all security services in background, giving the appearance that the device is powered off.

### Location Tracking
Uses FusedLocationProviderClient for accurate location tracking. Updates every 10 seconds during security alerts and syncs to Firebase.

### Alert System
Triggers comprehensive response on unauthorized access:
1. Fake power-off screen activated
2. Email sent to all family members
3. SMS sent to trusted contact
4. Location tracking begins
5. Loud alarm plays
6. Internet and GPS automatically enabled

## Development Status

This is an MVP implementation focusing on Android platform with Firebase backend. iOS version is planned for future releases.

### Implemented Features
- ✅ Firebase Cloud Functions for alerts
- ✅ Email notification system
- ✅ SMS notification system
- ✅ Android authentication system
- ✅ Power button interception
- ✅ Location tracking service
- ✅ SIM change detection
- ✅ Alarm service
- ✅ Fake power-off screen
- ✅ User interface and onboarding

### Planned Features
- ⏳ iOS application
- ⏳ Web dashboard for remote device management
- ⏳ Photo capture on failed authentication
- ⏳ Geofencing capabilities
- ⏳ Multi-device support

## Usage

### Initial Setup
1. Install and launch the app
2. Create an account with email and password
3. Set up PIN and backup password
4. Add family member email addresses
5. Add trusted contact phone number
6. Grant all required permissions
7. Enable device administrator privileges
8. Toggle protection ON

### Normal Operation
- When you press the power button, you'll be prompted for your PIN
- Enter your PIN to power off the device normally
- If you forget your PIN, use the backup password

### Security Alert
If someone enters the wrong PIN/password 3 times:
- The screen will appear to power off (fake)
- You'll receive an email with the device location
- Your trusted contact will receive an SMS
- Location tracking will begin automatically
- An alarm will sound at maximum volume

### Stopping a False Alarm
- Enter correct PIN in the hidden input (double-tap center of screen)
- Use secret gesture: Volume Up 3x + Volume Down 2x within 5 seconds
- Use the web dashboard (future feature)

## Privacy & Security

### Data Collection
- Location data (only during security alerts)
- Failed authentication attempts
- SIM card information
- Device information (model, OS version)

### Data Storage
- All data encrypted in transit (HTTPS/TLS 1.3)
- PIN/passwords stored in Android Keystore (encrypted)
- Location data retained for 30 days, then deleted
- User can request data export or deletion

### Security Measures
- Code obfuscation (ProGuard)
- Root/jailbreak detection
- SSL certificate pinning
- Tamper detection
- No logging of sensitive information

## Testing

### Manual Testing
1. Enable protection and test normal power-off with correct PIN
2. Test failed authentication (wrong PIN 3 times)
3. Verify email and SMS delivery
4. Check location tracking accuracy
5. Test SIM card change detection
6. Verify alarm activation

### Automated Tests
```bash
cd android
./gradlew test                    # Unit tests
./gradlew connectedAndroidTest    # Integration tests
```

## Troubleshooting

### Power button interception not working
- Ensure Accessibility Service is enabled in Settings
- Grant "Display over other apps" permission
- Disable battery optimization for the app

### Location not updating
- Ensure GPS is enabled
- Grant "Location (Always)" permission
- Check internet connectivity
- Whitelist app from battery optimization

### Emails not received
- Check spam/junk folder
- Verify family email addresses in settings
- Check SendGrid configuration in Firebase

### App stops in background
- Disable battery optimization for the app
- Enable "Autostart" permission (Xiaomi devices)
- Check device manufacturer specific battery settings

## Contributing

This is a security-focused application. Contributions should maintain high security standards:
- Follow OWASP Mobile Top 10 guidelines
- Use proper encryption for sensitive data
- Implement comprehensive error handling
- Add unit tests for new features
- Document security implications

## License

MIT License - See LICENSE file for details

## Disclaimer

This application is designed for anti-theft protection of your own devices. Users are responsible for complying with local laws and regulations regarding device tracking, data collection, and privacy. Ensure you have consent from device users before enabling tracking features.

## Support

For issues, questions, or feature requests:
- GitHub Issues: [Repository Issues Page]
- Email: support@securepower.app

## Acknowledgments

- Firebase for backend infrastructure
- SendGrid for email delivery
- Twilio for SMS services
- Android Jetpack libraries
- Open source community
