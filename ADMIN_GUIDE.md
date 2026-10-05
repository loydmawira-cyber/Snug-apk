# SNUG admin guide (Firebase Console)

Everything below is done in Firebase Console -> Firestore Database -> Data. The app cannot change these fields,
only you can (the security rules block it).

## Approve or reject a photo verification
1. Open the `verificationRequests` collection. Each document id is the user's id and has `status: "pending"`.
2. Look at the selfie: copy the long `selfie` value (starts with `data:image/jpeg;base64,`), paste it into the
   address bar of Chrome and press Enter. The `pose` field tells you which pose the person had to make.
3. Open `users/<same id>` and compare with their `profilePhoto` / `photos` the same way.
4. Match -> in `users/<id>` add a boolean field `photoVerified` = true, then set the request `status` to `approved`.
   No match -> set the request `status` to `rejected` (the person can then try again).
5. Delete the request document when you are done so the selfie is not kept longer than needed.

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
