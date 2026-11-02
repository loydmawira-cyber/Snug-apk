// SNUG Cloud Functions: verify Google Play subscriptions and keep `isVip` in sync.
// Only these functions (Admin SDK) can set isVip / vipExpiresAt; the Firestore rules block the app.
const { onCall, HttpsError } = require("firebase-functions/v2/https");
const { onMessagePublished } = require("firebase-functions/v2/pubsub");
const { onSchedule } = require("firebase-functions/v2/scheduler");
const { onUserCreated } = require("firebase-functions/v2/identity");
const admin = require("firebase-admin");
const { google } = require("googleapis");

admin.initializeApp();
const db = admin.firestore();

const PACKAGE_NAME = "com.aistudio.snug.rdxwzq";
const PRODUCT_ID = "snug_vip";
const ACTIVE_STATES = [
  "SUBSCRIPTION_STATE_ACTIVE",
  "SUBSCRIPTION_STATE_IN_GRACE_PERIOD",
  "SUBSCRIPTION_STATE_CANCELED", // cancelled but still paid until expiry
];

// Create the Firestore profile as soon as Firebase Authentication creates an account.
// The admin Users tab reads the `users` collection, so this keeps Auth and Firestore
// in sync even when a person has not completed onboarding yet.
exports.createUserProfile = onUserCreated(async (event) => {
  const user = event.data;
  const ref = db.collection("users").doc(user.uid);
  const existing = await ref.get();

  // Never overwrite a profile that onboarding or an older version already created.
  if (existing.exists) return;

  await ref.set({
    displayName: user.displayName || "",
    profilePhoto: user.photoURL || "",
    coverPhoto: "",
    bio: "",
    gender: "",
    interestedIn: [],
    interests: [],
    photos: [],
    city: "",
    country: "",
    lookingFor: "",
    photoVerified: false,
    banned: false,
    isVip: false,
    isPaused: false,
    onboarded: false,
    online: false,
    createdAt: admin.firestore.FieldValue.serverTimestamp(),
    updatedAt: admin.firestore.FieldValue.serverTimestamp(),
  });
});

async function publisher() {
  const auth = new google.auth.GoogleAuth({
    scopes: ["https://www.googleapis.com/auth/androidpublisher"],
  });
  return google.androidpublisher({ version: "v3", auth });
}

async function fetchSubscription(token) {
  const pub = await publisher();
  const res = await pub.purchases.subscriptionsv2.get({ packageName: PACKAGE_NAME, token });
  return res.data;
}

function evaluate(sub) {
  const item = (sub.lineItems || []).find((i) => i.productId === PRODUCT_ID);
  const expiry = item && item.expiryTime ? new Date(item.expiryTime) : null;
  const active = !!expiry && expiry > new Date() && ACTIVE_STATES.includes(sub.subscriptionState);
  const uid = sub.externalAccountIdentifiers && sub.externalAccountIdentifiers.obfuscatedExternalAccountId;
  return { active, expiry, uid, state: sub.subscriptionState };
}

async function applyEntitlement({ uid, active, expiry, state }) {
  const ref = db.collection("users").doc(uid);
  const snap = await ref.get();
  if (!snap.exists) return;
  if (active) {
    await ref.update({
      isVip: true,
      vipExpiresAt: admin.firestore.Timestamp.fromDate(expiry),
      vipProductId: PRODUCT_ID,
      vipState: state,
    });
    return;
  }
  // Inactive: only downgrade people whose VIP came from Play (admin-granted VIP has no vipExpiresAt)
  const stored = snap.get("vipExpiresAt");
  if (!stored) return;
  // Ignore a late notification about an older token when a newer subscription runs longer
  if (expiry && stored.toDate() > expiry) return;
  await ref.update({ isVip: false, vipState: state || "EXPIRED" });
}

// Called by the app right after a purchase (and when restoring purchases).
exports.verifyPurchase = onCall(async (request) => {
  if (!request.auth) throw new HttpsError("unauthenticated", "Sign in first.");
  const { purchaseToken, productId } = request.data || {};
  if (typeof purchaseToken !== "string" || purchaseToken.length < 10) {
    throw new HttpsError("invalid-argument", "Missing purchase token.");
  }
  if (productId !== PRODUCT_ID) throw new HttpsError("invalid-argument", "Unknown product.");

  let sub;
  try {
    sub = await fetchSubscription(purchaseToken);
  } catch (e) {
    console.error("Play API error", e.message);
    throw new HttpsError("unavailable", "Could not reach Google Play. Try again.");
  }

  const result = evaluate(sub);
  // The purchase must belong to the signed-in account (set at purchase time by the app)
  if (result.uid !== request.auth.uid) {
    throw new HttpsError("permission-denied", "This purchase belongs to a different account.");
  }

  // Acknowledge on the server so Google does not refund after 3 days
  if (sub.acknowledgementState === "ACKNOWLEDGEMENT_STATE_PENDING" && result.active) {
    try {
      const pub = await publisher();
      await pub.purchases.subscriptions.acknowledge({
        packageName: PACKAGE_NAME,
        subscriptionId: PRODUCT_ID,
        token: purchaseToken,
        requestBody: {},
      });
    } catch (e) {
      console.error("Acknowledge failed", e.message);
      throw new HttpsError("unavailable", "Could not finish the purchase. Try again.");
    }
  }

  await applyEntitlement({ uid: request.auth.uid, ...result });
  return { active: result.active, expiresAt: result.expiry ? result.expiry.toISOString() : null };
});

// Google Play real-time developer notifications (renewals, cancellations, refunds, expiry).
// Play Console -> Monetization setup -> Real-time notifications -> topic: projects/<id>/topics/play-rtdn
exports.playNotifications = onMessagePublished("play-rtdn", async (event) => {
  const msg = event.data.message.json;
  if (!msg || msg.packageName !== PACKAGE_NAME) return;

  let token = null;
  if (msg.subscriptionNotification) token = msg.subscriptionNotification.purchaseToken;
  else if (msg.voidedPurchaseNotification && msg.voidedPurchaseNotification.productType === 1) {
    token = msg.voidedPurchaseNotification.purchaseToken;
  }
  if (!token) return; // test notifications and other types

  const sub = await fetchSubscription(token);
  const result = evaluate(sub);
  if (!result.uid) return;
  await applyEntitlement(result);
});

// Safety net: switch off Play-granted VIP whose expiry has passed (missed notification, etc.)
exports.expireVips = onSchedule("every 24 hours", async () => {
  const now = admin.firestore.Timestamp.now();
  const snap = await db.collection("users").where("vipExpiresAt", "<", now).get();
  const batch = db.batch();
  let n = 0;
  snap.forEach((doc) => {
    if (doc.get("isVip") === true) {
      batch.update(doc.ref, { isVip: false, vipState: "EXPIRED" });
      n++;
    }
  });
  if (n > 0) await batch.commit();
  console.log(`Expired ${n} VIP accounts`);
});
