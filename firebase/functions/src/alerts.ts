import * as admin from "firebase-admin";
import {DataSnapshot} from "firebase-functions/v1/database";
import {sendSecurityEmail} from "./email";
import {sendSecuritySMS} from "./sms";

interface AlertData {
  deviceId: string;
  userId: string;
  type: string;
  timestamp: number;
  location?: {
    lat: number;
    lng: number;
    accuracy: number;
    address?: string;
  };
  details?: {
    failedAttempts?: number;
    simChange?: {
      oldICCID?: string;
      newICCID?: string;
    };
  };
  sessionId?: string;
}

/**
 * Process security alert when created
 * Coordinates email, SMS, and tracking session
 */
export const processSecurityAlert = async (snapshot: DataSnapshot): Promise<void> => {
  const alertId = snapshot.key;
  if (!alertId) {
    console.error("Alert ID is null");
    return;
  }

  const alertData = snapshot.val() as AlertData;

  try {
    console.log(`Processing security alert ${alertId}:`, alertData.type);

    // Validate alert data
    if (!alertData.userId || !alertData.deviceId || !alertData.type) {
      console.error("Invalid alert data:", alertData);
      return;
    }

    // Get user preferences
    const userSnapshot = await admin.database()
      .ref(`/users/${alertData.userId}`)
      .once("value");

    const userData = userSnapshot.val();
    if (!userData) {
      console.error("User not found:", alertData.userId);
      return;
    }

    // Create tracking session if not exists
    let sessionId = alertData.sessionId;
    if (!sessionId) {
      sessionId = `${alertData.deviceId}_${Date.now()}`;

      await admin.database()
        .ref(`/tracking/${sessionId}`)
        .set({
          deviceId: alertData.deviceId,
          userId: alertData.userId,
          alertType: alertData.type,
          active: true,
          startTime: alertData.timestamp,
          endTime: null,
          locations: {},
        });

      // Update alert with session ID
      await snapshot.ref.update({sessionId});
    }

    // Send email notification
    try {
      if (userData.settings?.emailNotifications !== false) {
        await sendSecurityEmail(alertId, {...alertData, sessionId});
      }
    } catch (emailError) {
      console.error("Failed to send email:", emailError);
      // Continue processing even if email fails
    }

    // Send SMS notification (based on alert type and user settings)
    const shouldSendSMS = alertData.type === "UNAUTHORIZED_POWEROFF" ||
                         alertData.type === "SIM_CHANGED" ||
                         alertData.type === "FAILED_AUTH_THRESHOLD";

    if (shouldSendSMS && userData.settings?.smsNotifications !== false) {
      try {
        await sendSecuritySMS(alertId, {...alertData, sessionId});
      } catch (smsError) {
        console.error("Failed to send SMS:", smsError);
        // Continue processing even if SMS fails
      }
    }

    // Send FCM push notification to user's other devices (if any)
    try {
      if (userData.fcmTokens && Array.isArray(userData.fcmTokens)) {
        const notificationPromises = userData.fcmTokens.map((token: string) => {
          return admin.messaging().send({
            token,
            notification: {
              title: "🚨 Security Alert",
              body: `${getAlertTypeMessage(alertData.type)} on ${alertData.deviceId}`,
            },
            data: {
              alertId,
              alertType: alertData.type,
              deviceId: alertData.deviceId,
              sessionId: sessionId || "",
            },
          });
        });

        await Promise.allSettled(notificationPromises);
      }
    } catch (fcmError) {
      console.error("Failed to send FCM notifications:", fcmError);
      // Continue processing even if FCM fails
    }

    // Update device status
    await admin.database()
      .ref(`/devices/${alertData.deviceId}`)
      .update({
        status: "security_alert",
        lastAlert: alertData.timestamp,
        lastAlertType: alertData.type,
      });

    // Mark alert as processed
    await snapshot.ref.update({
      processed: true,
      processedAt: Date.now(),
    });

    console.log(`Alert ${alertId} processed successfully`);
  } catch (error) {
    console.error(`Error processing alert ${alertId}:`, error);

    // Mark alert with error
    await snapshot.ref.update({
      processed: false,
      processingError: error instanceof Error ? error.message : String(error),
    });

    throw error;
  }
};

/**
 * Get human-readable alert type message
 */
function getAlertTypeMessage(type: string): string {
  const messages: Record<string, string> = {
    "UNAUTHORIZED_POWEROFF": "Unauthorized power-off attempt",
    "SIM_CHANGED": "SIM card changed",
    "FAILED_AUTH_THRESHOLD": "Multiple failed login attempts",
    "APP_UNINSTALL_ATTEMPT": "App uninstall attempt",
    "DEVICE_ADMIN_REMOVED": "Device admin removed",
  };

  return messages[type] || "Security event detected";
}
