# RoomMate Expenses

Android app built with Kotlin, Jetpack Compose, Firebase Anonymous Authentication and Firebase Realtime Database.

## Included in this build
- Purple and white Material 3 interface
- Create a room and join using a six-digit invite code
- Room-wide monthly spending dashboard and personal monthly total
- Add, edit and delete expense entries
- Categories: Food, Rent, Bills, Travel, Shopping and Other
- Live member and expense lists
- Admin badge and basic member removal UI
- Launcher icon
- GitHub Actions workflow to build and upload a debug APK

## Firebase setup
1. Keep your project's `app/google-services.json` in place.
2. Enable **Authentication → Anonymous**.
3. Create a Realtime Database and verify its URL/region in Firebase.
4. Publish database rules appropriate to your test and production needs.

The included rules file is a starting point, not a security audit. In particular, the invite-code lookup and write permissions need a trusted backend design before real users rely on the app. Do not use public read/write rules for a deployed app. WhatsApp numbers are user-entered and are not verified by this app.

## Build APK with GitHub Actions
Push the repository to GitHub. Open **Actions → Build RoomMate Expenses APK** and download the `RoomMate-Expenses-debug-apk` artifact after the run succeeds. The workflow also runs on pushes affecting the app source.

## Notes
This is an improved feature build, not yet a release-certified production app. Test room creation/joining, concurrent expense updates, member removal, and database rules with separate test accounts before distributing it. The app currently uses anonymous Firebase identity and does not include verified phone login, automated bill splitting, or paid Cloud Functions.
