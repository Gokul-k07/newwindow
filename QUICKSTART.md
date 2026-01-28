# SecurePower - Quick Start Guide

This guide will help you get the SecurePower anti-theft app up and running quickly.

## Prerequisites

Before you begin, ensure you have the following installed:

### For Firebase Backend:
- **Node.js** 18+ and npm
- **Firebase CLI**: `npm install -g firebase-tools`

### For Android Development:
- **Android Studio** Hedgehog (2023.1.1) or later
- **Java Development Kit (JDK)** 17
- **Android SDK** with API level 34
- **Kotlin** plugin (comes with Android Studio)

## Step 1: Firebase Setup (15 minutes)

### 1.1 Create Firebase Project

1. Go to https://console.firebase.google.com/
2. Click "Add project"
3. Name your project (e.g., "securepower-antitheft")
4. Disable Google Analytics (optional)
5. Click "Create project"

### 1.2 Enable Firebase Services

In your Firebase Console, enable these services:

1. **Authentication**
   - Go to Authentication → Sign-in method
   - Enable "Email/Password"

2. **Realtime Database**
   - Go to Realtime Database
   - Click "Create Database"
   - Start in "Test mode" (we'll deploy rules later)
   - Choose a location close to your users

3. **Cloud Functions**
   - Already enabled by default

4. **Cloud Messaging (FCM)**
   - Go to Project Settings → Cloud Messaging
   - Note your Server Key (for later)

### 1.3 Get Firebase Configuration

1. **For Android:**
   - In Firebase Console, click the Android icon
   - Package name: `com.antitheft.securepower`
   - Download `google-services.json`
   - Save it to `android/app/google-services.json`

### 1.4 Deploy Firebase Backend

```bash
# Navigate to firebase directory
cd firebase/functions

# Install dependencies
npm install

# Login to Firebase
firebase login

# Initialize project (if needed)
firebase use --add
# Select your project and give it an alias (e.g., "default")

# Deploy database rules
cd ..
firebase deploy --only database

# Deploy Cloud Functions
firebase deploy --only functions
```

### 1.5 Configure External Services

#### SendGrid (Email Service)
1. Sign up at https://sendgrid.com/ (free tier: 100 emails/day)
2. Create an API key:
   - Settings → API Keys → Create API Key
   - Give it full access
3. Configure Firebase Functions:
```bash
firebase functions:config:set sendgrid.key="YOUR_SENDGRID_API_KEY"
```

#### Twilio (SMS Service)
1. Sign up at https://twilio.com/ (free trial: $15 credit)
2. Get your credentials from Dashboard:
   - Account SID
   - Auth Token
   - Phone Number
3. Configure Firebase Functions:
```bash
firebase functions:config:set twilio.sid="YOUR_ACCOUNT_SID"
firebase functions:config:set twilio.token="YOUR_AUTH_TOKEN"
firebase functions:config:set twilio.phone="+15551234567"
```

4. Redeploy functions with new config:
```bash
firebase deploy --only functions
```

## Step 2: Android App Setup (10 minutes)

### 2.1 Open Project in Android Studio

1. Launch Android Studio
2. Select "Open an Existing Project"
3. Navigate to `newwindow/android/` directory
4. Click "OK"
5. Wait for Gradle sync to complete

### 2.2 Verify Configuration

1. Check that `google-services.json` is in `android/app/` directory
2. If Gradle sync fails, check:
   - Java 17 is being used (File → Project Structure → SDK Location)
   - Internet connection is active (for downloading dependencies)

### 2.3 Build the Project

```bash
# From android/ directory
./gradlew build

# Or use Android Studio:
# Build → Make Project (Ctrl+F9 / Cmd+F9)
```

### 2.4 Run on Device/Emulator

1. Connect an Android device via USB (with USB debugging enabled)
   - OR -
   Start an Android emulator (Tools → Device Manager)

2. Click the "Run" button (green triangle) in Android Studio
3. Select your device/emulator
4. Wait for installation and launch

## Step 3: Initial App Configuration (5 minutes)

### 3.1 First Launch

1. Grant required permissions when prompted:
   - Location (Always)
   - Phone State (for SIM detection)
   - Notifications

2. Set up authentication:
   - Create a PIN (4-6 digits)
   - Create a backup password (8+ characters)
   - Optionally enable biometric authentication

3. Configure alerts:
   - Add family member email addresses
   - Add trusted contact phone number

4. Enable accessibility service:
   - Settings → Accessibility → SecurePower
   - Toggle ON
   - Confirm

5. Grant additional permissions:
   - Display over other apps
   - Battery optimization exemption
   - Device Administrator (prevents uninstall)

### 3.2 Enable Protection

1. On the home screen, toggle "Power-Off Protection" to ON
2. You're now protected!

## Step 4: Testing (5 minutes)

### Test 1: Normal Power-Off
1. Press the power button
2. Authentication dialog should appear
3. Enter your correct PIN
4. Device should power off normally

### Test 2: Failed Authentication
1. Press the power button
2. Enter wrong PIN 3 times
3. Should trigger security response:
   - Fake power-off screen
   - Email sent to family members
   - SMS sent to trusted contact
   - Location tracking starts

### Test 3: SIM Change Detection
1. Remove SIM card (or insert different SIM)
2. Should trigger alert within 5 minutes
3. Check your email for alert notification

## Troubleshooting

### Firebase Functions Not Deploying

**Error**: "Cannot find module"
```bash
cd firebase/functions
rm -rf node_modules
npm install
firebase deploy --only functions
```

**Error**: "Billing account required"
- Firebase Functions require Blaze (pay-as-you-go) plan
- But it has generous free tier
- Go to Firebase Console → Upgrade to Blaze plan

### Android Build Fails

**Error**: "SDK location not found"
- Create `local.properties` in `android/` directory:
```properties
sdk.dir=/path/to/your/Android/Sdk
```

**Error**: "google-services.json missing"
- Download from Firebase Console
- Place in `android/app/` directory

### Power Button Interception Not Working

1. **Check Accessibility Service**:
   - Settings → Accessibility → SecurePower → Ensure it's ON

2. **Check Battery Optimization**:
   - Settings → Apps → SecurePower → Battery → Unrestricted

3. **Check Permissions**:
   - Settings → Apps → SecurePower → Permissions → All granted

### Emails Not Received

1. **Check SendGrid Configuration**:
```bash
firebase functions:config:get
# Should show sendgrid.key
```

2. **Check Spam Folder**:
- SendGrid emails often land in spam initially

3. **Check SendGrid Dashboard**:
- https://app.sendgrid.com/
- Check Activity Feed for delivery status

### Location Not Updating

1. **Enable GPS**: Settings → Location → ON
2. **Grant Permission**: Always allow location (not "While using")
3. **Disable Battery Saver**: Can restrict background location
4. **Check Firebase**: Database → tracking → Verify data is being written

## Next Steps

### Customize the App

1. **Change App Name/Icon**:
   - Edit `android/app/src/main/res/values/strings.xml`
   - Replace launcher icons in `res/mipmap-*` directories

2. **Adjust Security Settings**:
   - Edit lockout duration in `AuthManager.kt`
   - Change failed attempt threshold
   - Customize alert email templates in `firebase/functions/src/email.ts`

3. **Add More Features**:
   - Implement photo capture on failed auth
   - Add geofencing
   - Build web dashboard

### Deploy to Production

1. **Android Release Build**:
```bash
cd android
./gradlew assembleRelease
# APK will be in app/build/outputs/apk/release/
```

2. **Generate Signing Key**:
```bash
keytool -genkey -v -keystore securepower.keystore -alias securepower -keyalg RSA -keysize 2048 -validity 10000
```

3. **Sign APK** (in Android Studio):
- Build → Generate Signed Bundle/APK
- Follow wizard

4. **Upload to Google Play**:
- Create developer account ($25 one-time fee)
- Create app listing
- Upload signed APK
- Submit for review

### Monitor Your App

1. **Firebase Console**:
   - Monitor real-time database usage
   - Check function execution logs
   - View authentication statistics

2. **Crashlytics** (optional):
   - Add Firebase Crashlytics to track crashes
   - Follow: https://firebase.google.com/docs/crashlytics/get-started

3. **Analytics** (optional):
   - Already integrated (Firebase Analytics)
   - View in Firebase Console → Analytics

## Cost Estimation

### Free Tier Usage (for personal use):
- **Firebase Realtime Database**: 1 GB storage, 10 GB/month download - FREE
- **Cloud Functions**: 2 million invocations/month - FREE
- **SendGrid**: 100 emails/day - FREE
- **Twilio**: $15 trial credit (then pay-per-SMS)

### Expected Costs (1 device, 1 security alert/month):
- Firebase: $0 (within free tier)
- SendGrid: $0 (within free tier)
- Twilio: ~$0.0075 per SMS (negligible)

**Total: ~$0-1/month** for personal use

### Production Scale (1000 devices):
- Estimate $20-50/month depending on alert frequency

## Security Checklist

Before deploying to production:

- [ ] Change Firebase security rules from test mode
- [ ] Enable ProGuard/R8 obfuscation for release builds
- [ ] Implement SSL pinning for Firebase
- [ ] Add root/jailbreak detection
- [ ] Rotate API keys regularly
- [ ] Review privacy policy and terms of service
- [ ] Implement data retention policies
- [ ] Test on multiple device manufacturers (Samsung, Xiaomi, etc.)
- [ ] Audit permissions and remove unnecessary ones
- [ ] Implement rate limiting on all endpoints

## Getting Help

- **Documentation**: See `/shared/docs/` for detailed docs
- **Implementation Status**: Check `IMPLEMENTATION_STATUS.md`
- **GitHub Issues**: Report bugs and request features
- **Firebase Docs**: https://firebase.google.com/docs
- **Android Docs**: https://developer.android.com/

## Legal Disclaimer

This application is for personal device security only. Ensure compliance with local laws regarding:
- Device tracking
- Data collection and storage
- Privacy regulations (GDPR, CCPA, etc.)
- User consent requirements

Always inform users about data collection and obtain proper consent before enabling tracking features.

---

**Congratulations!** You now have a fully functional anti-theft mobile security app. Stay secure! 🔐
