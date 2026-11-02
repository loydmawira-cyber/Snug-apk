// One-time repair for Firebase Authentication users that do not yet have
// a Firestore users/{uid} profile document.
//
// Run from this directory with Application Default Credentials, for example:
//   gcloud auth application-default login
//   node backfill-users.js
//
// The script is safe to run repeatedly: existing profiles are never overwritten.
const admin = require("firebase-admin");

admin.initializeApp();
const db = admin.firestore();

async function backfill() {
  let nextPageToken;
  let checked = 0;
  let created = 0;

  do {
    const page = await admin.auth().listUsers(1000, nextPageToken);
    const refs = page.users.map((user) => db.collection("users").doc(user.uid));

    // Read only the current Auth page before writing missing profiles.
    const snapshots = refs.length ? await db.getAll(...refs) : [];
    const batch = db.batch();
    let writes = 0;

    for (let i = 0; i < page.users.length; i++) {
      const user = page.users[i];
      if (snapshots[i].exists) continue;

      batch.set(refs[i], {
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
        createdAt: admin.firestore.Timestamp.fromDate(
          user.metadata.creationTime
            ? new Date(user.metadata.creationTime)
            : new Date()
        ),
        updatedAt: admin.firestore.FieldValue.serverTimestamp(),
      });
      created++;
      writes++;
    }

    if (writes) await batch.commit();
    checked += page.users.length;
    console.log(`Checked ${checked} Auth users; created ${created} profiles.`);
    nextPageToken = page.pageToken;
  } while (nextPageToken);

  console.log(`Done. Created ${created} missing users/{uid} profiles.`);
}

backfill().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
