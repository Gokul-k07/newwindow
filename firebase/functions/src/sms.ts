import * as functions from "firebase-functions";
import * as admin from "firebase-admin";
import {Twilio} from "twilio";

// Initialize Twilio
const TWILIO_SID = functions.config().twilio?.sid || "";
const TWILIO_TOKEN = functions.config().twilio?.token || "";

let twilioClient: Twilio | null = null;
if (TWILIO_SID && TWILIO_TOKEN) {
  twilioClient = new Twilio(TWILIO_SID, TWILIO_TOKEN);
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
 * Send WhatsApp alert to trusted contact
 */
export const sendWhatsAppAlert = async (alertId: string, alertData: AlertData): Promise<void> => {
  try {
    if (!twilioClient) {
      console.error("Twilio not configured");
      return;
    }

    // Get user data to retrieve trusted WhatsApp number
    const userSnapshot = await admin.database()
      .ref(`/users/${alertData.userId}`)
      .once("value");

    const userData = userSnapshot.val();
    if (!userData || !userData.trustedWhatsAppNumber) {
      console.error("No trusted WhatsApp number configured for user:", alertData.userId);
      return;
    }

    // Check rate limiting (max 1 WhatsApp message per 5 minutes)
    if (userData.lastWhatsApp && (Date.now() - userData.lastWhatsApp) < 5 * 60 * 1000) {
      console.log("WhatsApp rate limit: Skipping (sent within last 5 minutes)");
      await admin.database()
        .ref(`/alerts/${alertId}`)
        .update({
          whatsappSent: false,
          whatsappSkipped: true,
          whatsappSkipReason: "rate_limit",
        });
      return;
    }

    // Get device information
    const deviceSnapshot = await admin.database()
      .ref(`/devices/${alertData.deviceId}`)
      .once("value");

    const deviceData = deviceSnapshot.val();

    // Determine alert type
    const alertTypeMessages: Record<string, string> = {
      "UNAUTHORIZED_POWEROFF": "UNAUTHORIZED POWER-OFF ATTEMPT",
      "SIM_CHANGED": "SIM CARD CHANGED",
      "FAILED_AUTH_THRESHOLD": "MULTIPLE FAILED LOGIN ATTEMPTS",
      "APP_UNINSTALL_ATTEMPT": "APP UNINSTALL ATTEMPT",
      "DEVICE_ADMIN_REMOVED": "DEVICE ADMIN REMOVED",
    };

    const alertType = alertTypeMessages[alertData.type] || "SECURITY ALERT";

    // Format location
    const locationText = alertData.location?.address ||
      (alertData.location ? `${alertData.location.lat.toFixed(6)}, ${alertData.location.lng.toFixed(6)}` : "Location unavailable");

    // Generate Google Maps link
    const mapsLink = alertData.location ?
      `https://www.google.com/maps?q=${alertData.location.lat},${alertData.location.lng}` :
      null;

    // Generate tracking link
    const trackingLink = alertData.sessionId ?
      `https://securepower-antitheft.web.app/track/${alertData.sessionId}` :
      "https://securepower-antitheft.web.app";

    // Format timestamp
    const date = new Date(alertData.timestamp);
    const timeString = date.toLocaleString("en-US", {
      month: "short",
      day: "numeric",
      year: "numeric",
      hour: "numeric",
      minute: "2-digit",
      timeZoneName: "short",
    });

    // Build WhatsApp message with formatting
    const whatsappMessage = `🚨 *SECURITY ALERT*

*Type:* ${alertType}
*Device:* ${deviceData?.deviceName || "Unknown Device"}
*Time:* ${timeString}

📍 *Location:*
${locationText}
${mapsLink ? `\n🗺️ View on map: ${mapsLink}` : ""}

🔗 Track device: ${trackingLink}

---
SecurePower Anti-Theft Alert`;

    // Send WhatsApp message (using Twilio Sandbox for testing)
    const message = await twilioClient.messages.create({
      body: whatsappMessage,
      from: "whatsapp:+14155238886", // Twilio WhatsApp Sandbox number
      to: `whatsapp:${userData.trustedWhatsAppNumber}`,
    });

    console.log(`WhatsApp sent: ${message.sid}`);

    // Update alert record and user's last WhatsApp timestamp
    await Promise.all([
      admin.database()
        .ref(`/alerts/${alertId}`)
        .update({
          whatsappSent: true,
          whatsappSentAt: Date.now(),
          whatsappRecipient: userData.trustedWhatsAppNumber,
          whatsappId: message.sid,
        }),
      admin.database()
        .ref(`/users/${alertData.userId}`)
        .update({
          lastWhatsApp: Date.now(),
        }),
    ]);
  } catch (error) {
    console.error("Error sending WhatsApp message:", error);

    // Log failure to alert record
    await admin.database()
      .ref(`/alerts/${alertId}`)
      .update({
        whatsappSent: false,
        whatsappFailed: true,
        whatsappError: error instanceof Error ? error.message : String(error),
      });

    throw error;
  }
};

// Backward compatibility export
export const sendSecuritySMS = sendWhatsAppAlert;
