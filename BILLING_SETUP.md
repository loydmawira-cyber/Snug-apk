# SNUG VIP: Google Play Billing setup

Code is in `data/billing/BillingManager.kt` (app) and `functions/` (server). The app never sets `isVip`;
the Cloud Function does after checking the purchase with Google.

## 1. Firebase
1. Upgrade the project to the **Blaze** plan (Functions need it). Set a budget alert ($5-$10).
2. `npm i -g firebase-tools && firebase login`
3. From the repo root: `cd functions && npm install && cd ..`
4. `firebase deploy --only functions,firestore:rules`

## 2. Play Console (app must be uploaded to Internal testing first)
1. Monetize > Products > **Subscriptions** > Create: product ID **`snug_vip`**.
2. Add base plans (auto-renewing): `monthly` (1 month), `six-months` (6 months), `yearly` (1 year). Set prices.
3. On each base plan add an **offer** (e.g. `free-trial`) with a 7-day free trial phase. Activate everything.
4. Setup > API access: link your Google Cloud project. Take the Cloud Function's service account
   (Cloud Console > Cloud Run > `verifypurchase` > Security, usually `<project-number>-compute@developer.gserviceaccount.com`),
   invite that email under Users and permissions with **View financial data** and **Manage orders and subscriptions**.
5. Real-time notifications: Monetize > Monetization setup. In Google Cloud create Pub/Sub topic `play-rtdn`,
   give `google-play-developer-notifications@system.gserviceaccount.com` the **Pub/Sub Publisher** role on it,
   then enter `projects/<project-id>/topics/play-rtdn` in Play Console and send a test notification.
6. Setup > License testing: add your Gmail so test purchases are free.

## 3. Test
Install from the Internal testing track (billing does not work on a sideloaded debug APK).
Buy a plan with the license-tester account, then check `users/<uid>` in Firestore: `isVip: true`, `vipExpiresAt` set.
Cancel in Google Play and wait; RTDN or the nightly `expireVips` job turns it off after expiry.
