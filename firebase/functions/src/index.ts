import * as functions from "firebase-functions";
import * as admin from "firebase-admin";

admin.initializeApp();

// Import function modules
import {sendSecurityEmail} from "./email";
import {sendSecuritySMS} from "./sms";
import {processSecurityAlert} from "./alerts";
import {processLocationUpdate} from "./tracking";

// Export Cloud Functions
export const onAlertCreated = functions.database
  .ref("/alerts/{alertId}")
  .onCreate(processSecurityAlert);

export const onLocationUpdate = functions.database
  .ref("/tracking/{sessionId}/locations/{timestamp}")
  .onCreate(processLocationUpdate);

export const emailAlert = sendSecurityEmail;
export const smsAlert = sendSecuritySMS;
