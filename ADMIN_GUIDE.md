# SNUG admin guide (Firebase Console)

Everything below is done in Firebase Console -> Firestore Database -> Data. The app cannot change these fields,
only you can (the security rules block it).

## Become an admin
Easiest: put the admin email in `firestore.rules`, in the `isAdmin()` function (replace `ADMIN_EMAIL_HERE`,
add more emails separated by commas), then publish the rules. The account must have a verified email
(Google sign-in always does). Alternative: add a document to the `admins` collection whose id is the user's UID.
Reopen the app and Profile shows **Admin: Verification review**.

## Review photo verifications (in the app)
1. Profile -> Admin: Verification review. The Pending tab lists every selfie waiting.
2. Each card shows the reference photo (the user's profile photo at the time) next to the live selfie, plus the pose they had to make.
3. Tap Approve (adds the blue check) or Reject (pick a reason; the user sees it and can try again).
4. The user gets an in-app notification. The selfie and reference image are erased from the request as soon as you decide.
5. The History tab is the audit trail: who decided, when, and the rejection reason.

Console fallback: you can still edit `verificationRequests` and `users/<id>.photoVerified` by hand.

## Suspend a user (ban)
In `users/<id>` add a boolean field `banned` = true. The person sees "Account suspended", disappears from
Discover and Radar for everyone, and the rules stop them from liking, matching or messaging.
Set `banned` back to false (or delete it) to lift the suspension.

## Read reports
Open the `reports` collection. Each document is `<reporterId>_<reportedId>` with the `reason`. Several reports
about the same `reportedId` are a strong sign to review and suspend that account.

## Email verification for messaging
Sending a message (text or photo) needs a verified email. New accounts get the verification email automatically
when they sign up. Existing accounts are asked to verify the first time they try to send a message.
The rule is `emailVerified()` in `firestore.rules`. To switch it off for testing, delete the line
`&& emailVerified()` from the chats rule and publish the rules again.
