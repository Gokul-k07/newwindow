import * as functions from "firebase-functions";
import * as admin from "firebase-admin";
import * as twilio from "twilio";

// Initialize Twilio
const TWILIO_SID = functions.config().twilio?.sid || "";
const TWILIO_TOKEN = functions.config().twilio?.token || "";
const TWILIO_PHONE = functions.config().twilio?.phone || "";

let twilioClient: twilio.Twilio | null = null;
if (TWILIO_SID && TWILIO_TOKEN) {
  twilioClient = twilio(TWILIO_SID, TWILIO_TOKEN);
}

interface AlertData {
  deviceId: string;
  userId: string;
  type: string;
  timestamp: number;
  location?: {
    lat: number;
    lng: number;
    address?: string;
  };
  sessionId?: string;
}

/**
 * Send SMS alert to trusted contact
 */
export const sendSecuritySMS = async (alertId: string, alertData: AlertData): Promise<void> => {
  try {
    if (!twilioClient) {
      console.error("Twilio not configured");
      return;
    }

    // Get user data to retrieve trusted number
    const userSnapshot = await admin.database()
      .ref(`/users/${alertData.userId}`)
      .once("value");

    const userData = userSnapshot.val();
    if (!userData || !userData.trustedNumber) {
      console.error("No trusted number configured for user:", alertData.userId);
      return;
    }

    // Check rate limiting (max 1 SMS per 5 minutes)
    if (userData.lastSMS && (Date.now() - userData.lastSMS) < 5 * 60 * 1000) {
      console.log("SMS rate limit: Skipping (sent within last 5 minutes)");
      await admin.database()
        .ref(`/alerts/${alertId}`)
        .update({
          smsSent: false,
          smsSkipped: true,
          smsSkipReason: "rate_limit",
        });
      return;
    }

    // Get device information
    const deviceSnapshot = await admin.database()
      .ref(`/devices/${alertData.deviceId}`)
      .once("value");

    const deviceData = deviceSnapshot.val();

    // Determine alert type
    const alertTypeShort: Record<string, string> = {
      "UNAUTHORIZED_POWEROFF": "Unauthorized power-off attempt",
      "SIM_CHANGED": "SIM card changed",
      "FAILED_AUTH_THRESHOLD": "3 failed login attempts",
      "APP_UNINSTALL_ATTEMPT": "App uninstall attempt",
      "DEVICE_ADMIN_REMOVED": "Device admin removed",
    };

    const alertMsg = alertTypeShort[alertData.type] || "Security alert";

    // Format location
    const locationText = alertData.location?.address ||
      (alertData.location ? `${alertData.location.lat.toFixed(4)}, ${alertData.location.lng.toFixed(4)}` : "Unknown");

    // Generate short tracking link (would use URL shortener in production)
    const trackingLink = alertData.sessionId ?
      `https://securepower.app/t/${alertData.sessionId.substring(0, 8)}` :
      "https://securepower.app";

    // Build SMS message (160 char limit for single SMS)
    const smsMessage = `SecurePower Alert: ${alertMsg} on ${deviceData?.deviceName || "your device"}. Location: ${locationText}. Track: ${trackingLink}`;

    // Send SMS
    const message = await twilioClient.messages.create({
      body: smsMessage,
      from: TWILIO_PHONE,
      to: userData.trustedNumber,
    });

    console.log(`SMS sent: ${message.sid}`);

    // Update alert record and user's last SMS timestamp
    await Promise.all([
      admin.database()
        .ref(`/alerts/${alertId}`)
        .update({
          smsSent: true,
          smsSentAt: Date.now(),
          smsRecipient: userData.trustedNumber,
          smsId: message.sid,
        }),
      admin.database()
        .ref(`/users/${alertData.userId}`)
        .update({
          lastSMS: Date.now(),
        }),
    ]);
  } catch (error) {
    console.error("Error sending SMS:", error);

    // Log failure to alert record
    await admin.database()
      .ref(`/alerts/${alertId}`)
      .update({
        smsSent: false,
        smsError: error instanceof Error ? error.message : String(error),
      });

    throw error;
  }
};
