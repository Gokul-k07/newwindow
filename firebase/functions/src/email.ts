import * as functions from "firebase-functions";
import * as admin from "firebase-admin";
import * as sgMail from "@sendgrid/mail";

// Initialize SendGrid
const SENDGRID_API_KEY = functions.config().sendgrid?.key || "";
if (SENDGRID_API_KEY) {
  sgMail.setApiKey(SENDGRID_API_KEY);
}

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
 * Send security alert email to family members
 */
export const sendSecurityEmail = async (alertId: string, alertData: AlertData): Promise<void> => {
  try {
    // Get user data to retrieve family emails
    const userSnapshot = await admin.database()
      .ref(`/users/${alertData.userId}`)
      .once("value");

    const userData = userSnapshot.val();
    if (!userData || !userData.familyEmails) {
      console.error("No family emails configured for user:", alertData.userId);
      return;
    }

    // Get device information
    const deviceSnapshot = await admin.database()
      .ref(`/devices/${alertData.deviceId}`)
      .once("value");

    const deviceData = deviceSnapshot.val();

    // Determine alert type message
    const alertTypeMessages: Record<string, string> = {
      "UNAUTHORIZED_POWEROFF": "Unauthorized Power-Off Attempt",
      "SIM_CHANGED": "SIM Card Changed",
      "FAILED_AUTH_THRESHOLD": "Multiple Failed Authentication Attempts",
      "APP_UNINSTALL_ATTEMPT": "App Uninstall Attempt Detected",
      "DEVICE_ADMIN_REMOVED": "Device Admin Privileges Removed",
    };

    const alertTypeMessage = alertTypeMessages[alertData.type] || "Security Alert";

    // Format location
    const locationText = alertData.location ?
      `${alertData.location.address || `${alertData.location.lat.toFixed(6)}, ${alertData.location.lng.toFixed(6)}`}` :
      "Location unavailable";

    // Generate tracking link
    const trackingLink = alertData.sessionId ?
      `https://securepower.app/track/${alertData.sessionId}` :
      "N/A";

    // Format timestamp
    const date = new Date(alertData.timestamp);
    const timeString = date.toLocaleString("en-US", {
      month: "long",
      day: "numeric",
      year: "numeric",
      hour: "numeric",
      minute: "2-digit",
      timeZoneName: "short",
    });

    // Build email HTML
    const emailHtml = `
<!DOCTYPE html>
<html>
<head>
  <style>
    body { font-family: Arial, sans-serif; line-height: 1.6; color: #333; }
    .container { max-width: 600px; margin: 0 auto; padding: 20px; }
    .header { background: #dc3545; color: white; padding: 20px; text-align: center; border-radius: 5px 5px 0 0; }
    .content { background: #f8f9fa; padding: 30px; border: 1px solid #dee2e6; }
    .alert-box { background: white; padding: 20px; margin: 20px 0; border-left: 4px solid #dc3545; }
    .info-row { margin: 10px 0; }
    .label { font-weight: bold; color: #495057; }
    .value { color: #212529; }
    .button { display: inline-block; background: #007bff; color: white; padding: 12px 30px;
              text-decoration: none; border-radius: 5px; margin: 20px 0; }
    .footer { text-align: center; margin-top: 30px; font-size: 12px; color: #6c757d; }
    .warning { color: #dc3545; font-weight: bold; }
  </style>
</head>
<body>
  <div class="container">
    <div class="header">
      <h1>🚨 Security Alert</h1>
      <h2>${alertTypeMessage}</h2>
    </div>
    <div class="content">
      <p>Hi ${userData.name || "there"},</p>
      <p class="warning">Your protected device has triggered a security alert.</p>

      <div class="alert-box">
        <div class="info-row">
          <span class="label">Alert Type:</span>
          <span class="value">${alertTypeMessage}</span>
        </div>
        <div class="info-row">
          <span class="label">Device:</span>
          <span class="value">${deviceData?.deviceName || "Unknown"} (${deviceData?.model || "Unknown Model"})</span>
        </div>
        <div class="info-row">
          <span class="label">Time:</span>
          <span class="value">${timeString}</span>
        </div>
        <div class="info-row">
          <span class="label">Location:</span>
          <span class="value">${locationText}</span>
        </div>
        ${alertData.details?.failedAttempts ? `
        <div class="info-row">
          <span class="label">Failed Attempts:</span>
          <span class="value">${alertData.details.failedAttempts}</span>
        </div>
        ` : ""}
        ${alertData.details?.simChange ? `
        <div class="info-row">
          <span class="label">SIM Change:</span>
          <span class="value">Old: ${alertData.details.simChange.oldICCID || "N/A"}<br>
          New: ${alertData.details.simChange.newICCID || "N/A"}</span>
        </div>
        ` : ""}
      </div>

      ${alertData.sessionId ? `
      <p><strong>Your device is now in security mode.</strong> Location tracking is active and security measures have been engaged.</p>
      <div style="text-align: center;">
        <a href="${trackingLink}" class="button">View Live Location Tracking</a>
      </div>
      ` : `
      <p><strong>Action Required:</strong> Check your device immediately.</p>
      `}

      <p><strong>What's Happening:</strong></p>
      <ul>
        <li>The device screen may appear powered off (security mode)</li>
        <li>Location tracking is active and updating every 10 seconds</li>
        <li>An alarm is playing at maximum volume</li>
        <li>Internet and GPS have been automatically enabled</li>
      </ul>

      <p><strong>If this is you:</strong> Enter your correct PIN to stop the security response.</p>
      <p><strong>If this is NOT you:</strong> Click the tracking link above to monitor your device's location.</p>

      <div class="footer">
        <p>SecurePower Anti-Theft Protection System</p>
        <p>This is an automated security alert. Do not reply to this email.</p>
        <p>If you need assistance, visit <a href="https://securepower.app/support">securepower.app/support</a></p>
      </div>
    </div>
  </div>
</body>
</html>
    `;

    const emailText = `
SecurePower Security Alert

Alert Type: ${alertTypeMessage}
Device: ${deviceData?.deviceName || "Unknown"}
Time: ${timeString}
Location: ${locationText}
${alertData.details?.failedAttempts ? `Failed Attempts: ${alertData.details.failedAttempts}\n` : ""}

${alertData.sessionId ? `View Live Tracking: ${trackingLink}` : "Check your device immediately."}

Your device is in security mode. Location tracking is active.

- SecurePower Team
    `;

    // Send email to all family members
    const familyEmails = userData.familyEmails as string[];
    const emailPromises = familyEmails.map((email) => {
      const msg = {
        to: email,
        from: {
          email: "alerts@securepower.app",
          name: "SecurePower Security",
        },
        subject: `🚨 Security Alert: ${alertTypeMessage}`,
        text: emailText,
        html: emailHtml,
      };

      return sgMail.send(msg);
    });

    await Promise.all(emailPromises);

    // Update alert record with email status
    await admin.database()
      .ref(`/alerts/${alertId}`)
      .update({
        emailSent: true,
        emailSentAt: Date.now(),
        emailRecipients: familyEmails,
      });

    console.log(`Security email sent for alert ${alertId} to ${familyEmails.length} recipients`);
  } catch (error) {
    console.error("Error sending security email:", error);

    // Log failure to alert record
    await admin.database()
      .ref(`/alerts/${alertId}`)
      .update({
        emailSent: false,
        emailError: error instanceof Error ? error.message : String(error),
      });

    throw error;
  }
};
