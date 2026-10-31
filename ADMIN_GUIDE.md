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

## Reports, suspensions and appeals (in the app)
Profile -> Admin dashboard has two tabs for this: **Reports** and **Appeals**.

**Reports tab.** Every person with open reports, most reported first. Tap one to see each report (reason and
time), the reported messages (text and photos) and their account status. Then decide:
- **Suspend** for 1, 3, 7 or 30 days, or permanently. Write the reason: the person sees it. The reports are closed,
  the person and the reporters get an in-app notification, and the decision is saved in the History tab.
- **Send a warning only**: the person gets your message as a notification, the reports are closed.
- **No violation**: closes the reports. Reporters are thanked. (A reporter can report the same person again later.)
- **Reinstate now**: lifts a suspension early.

A suspended person disappears from Discover and Radar and cannot like, match or message. A timed suspension
switches off by itself when it ends: the person's own app lifts it the next time it is opened (the rules allow
only that one change). Until they open the app they stay hidden.

**Appeals tab.** A suspended person sees the reason and end date, and can write an appeal. It shows up here.
- **Accept and reinstate** lifts the suspension straight away.
- **Deny** keeps it; add a note, the person sees it. They may send a new appeal afterwards.

Publish `firestore.rules` after updating the app: these features need the new rules (reports can be closed,
the `appeals` collection, `suspendedUntil` and `suspensionReason` on users).

Console fallback: in `users/<id>` set `banned` = true (and optionally `suspendedUntil` as a timestamp and
`suspensionReason` as text). Set `banned` to false to lift it.

## Read reports in the Console
Open the `reports` collection. Each document is `<reporterId>_<reportedId>` with the `reason`, a `status`
(missing or `open`, `actioned`, `dismissed`) and, once closed, `resolution`, `resolvedBy` and `resolvedAt`.

## Read message reports
When someone long-presses a message and taps **Report message**, a document is added to `messageReports`
(`<reporterId>_<messageId>`) with the message text (and photo, if it was a picture), the reason, `reportedId`
and `matchId`. It also adds a normal person-level report, so the sender shows up in the dashboard's most-reported list.

## Email verification for messaging
Sending a message (text or photo) needs a verified email. New accounts get the verification email automatically
when they sign up. Existing accounts are asked to verify the first time they try to send a message.
The rule is `emailVerified()` in `firestore.rules`. To switch it off for testing, delete the line
`&& emailVerified()` from the chats rule and publish the rules again.
