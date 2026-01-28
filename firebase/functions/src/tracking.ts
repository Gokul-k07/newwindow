import * as admin from "firebase-admin";
import {DataSnapshot} from "firebase-functions/v1/database";

interface LocationData {
  lat: number;
  lng: number;
  accuracy: number;
  speed?: number;
  bearing?: number;
  altitude?: number;
  timestamp: number;
  batteryLevel?: number;
  connectionType?: string;
}

/**
 * Process location update and maintain tracking session
 */
export const processLocationUpdate = async (snapshot: DataSnapshot): Promise<void> => {
  const locationData = snapshot.val() as LocationData;
  const sessionId = snapshot.ref.parent?.parent?.key;
  const timestamp = snapshot.key;

  if (!sessionId || !timestamp) {
    console.error("Invalid location update reference");
    return;
  }

  try {
    console.log(`Processing location update for session ${sessionId}`);

    // Get session data
    const sessionSnapshot = await admin.database()
      .ref(`/tracking/${sessionId}`)
      .once("value");

    const sessionData = sessionSnapshot.val();
    if (!sessionData) {
      console.error("Session not found:", sessionId);
      return;
    }

    // Perform reverse geocoding to get address
    // Note: In production, use Google Maps Geocoding API
    // For now, we'll store coordinates only
    const address = await reverseGeocode(locationData.lat, locationData.lng);

    if (address) {
      await snapshot.ref.update({address});
    }

    // Update session metadata
    await admin.database()
      .ref(`/tracking/${sessionId}`)
      .update({
        lastUpdate: Date.now(),
        lastLocation: {
          lat: locationData.lat,
          lng: locationData.lng,
          accuracy: locationData.accuracy,
          address,
          timestamp: locationData.timestamp,
        },
      });

    // Update device's last known location
    if (sessionData.deviceId) {
      await admin.database()
        .ref(`/devices/${sessionData.deviceId}/lastLocation`)
        .set({
          lat: locationData.lat,
          lng: locationData.lng,
          accuracy: locationData.accuracy,
          address,
          timestamp: locationData.timestamp,
        });
    }

    // Check if session should be auto-closed (24 hours)
    const sessionAge = Date.now() - sessionData.startTime;
    const TWENTY_FOUR_HOURS = 24 * 60 * 60 * 1000;

    if (sessionAge > TWENTY_FOUR_HOURS && sessionData.active) {
      await closeTrackingSession(sessionId, sessionData);
    }

    // Manage location history size (keep last 500 points)
    await pruneOldLocations(sessionId);
  } catch (error) {
    console.error("Error processing location update:", error);
  }
};

/**
 * Reverse geocode coordinates to address
 * Note: This is a placeholder. In production, use Google Maps Geocoding API
 */
async function reverseGeocode(lat: number, lng: number): Promise<string | null> {
  try {
    // Placeholder for actual geocoding API call
    // In production, use:
    // const response = await fetch(
    //   `https://maps.googleapis.com/maps/api/geocode/json?latlng=${lat},${lng}&key=${GOOGLE_API_KEY}`
    // );
    // const data = await response.json();
    // return data.results[0]?.formatted_address || null;

    return `${lat.toFixed(6)}, ${lng.toFixed(6)}`;
  } catch (error) {
    console.error("Reverse geocoding error:", error);
    return null;
  }
}

/**
 * Close tracking session and archive it
 */
async function closeTrackingSession(sessionId: string, sessionData: any): Promise<void> {
  try {
    console.log(`Auto-closing tracking session ${sessionId} (24 hours elapsed)`);

    // Mark session as inactive
    await admin.database()
      .ref(`/tracking/${sessionId}`)
      .update({
        active: false,
        endTime: Date.now(),
        autoCloseReason: "24_hour_limit",
      });

    // Send summary email to owner
    const userSnapshot = await admin.database()
      .ref(`/users/${sessionData.userId}`)
      .once("value");

    const userData = userSnapshot.val();
    if (userData?.familyEmails) {
      // Would send summary email here
      console.log(`Would send summary email to ${userData.familyEmails.join(", ")}`);
    }

    // Update device status
    if (sessionData.deviceId) {
      await admin.database()
        .ref(`/devices/${sessionData.deviceId}`)
        .update({
          status: "active",
        });
    }
  } catch (error) {
    console.error("Error closing tracking session:", error);
  }
}

/**
 * Keep only the last 500 location points to manage database size
 */
async function pruneOldLocations(sessionId: string): Promise<void> {
  try {
    const locationsSnapshot = await admin.database()
      .ref(`/tracking/${sessionId}/locations`)
      .orderByKey()
      .once("value");

    const locations = locationsSnapshot.val();
    if (!locations) return;

    const locationKeys = Object.keys(locations);
    const MAX_LOCATIONS = 500;

    if (locationKeys.length > MAX_LOCATIONS) {
      // Sort by timestamp (keys are timestamps)
      locationKeys.sort();

      // Delete oldest locations
      const toDelete = locationKeys.slice(0, locationKeys.length - MAX_LOCATIONS);
      const deletePromises = toDelete.map((key) =>
        admin.database().ref(`/tracking/${sessionId}/locations/${key}`).remove()
      );

      await Promise.all(deletePromises);
      console.log(`Pruned ${toDelete.length} old locations from session ${sessionId}`);
    }
  } catch (error) {
    console.error("Error pruning old locations:", error);
  }
}
