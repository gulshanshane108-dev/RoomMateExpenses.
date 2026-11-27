# RoomMate Expenses

Android app built with Kotlin, Jetpack Compose, Firebase Email/Password Authentication and Firebase Realtime Database.

## Included in this build
- Purple and white Material 3 interface
- **Permanent Email/Password sign-in and account creation**
- **Link an existing anonymous account to email/password so its UID, rooms and expenses are preserved**
- Sign out and profile/account section
- Create a room and join using a six-digit invite code
- Room-wide monthly spending dashboard and personal monthly total
- Add, edit and delete expense entries
- Categories: Food, Rent, Bills, Travel, Shopping and Other
- Live member and expense lists
- Admin badge and basic member removal UI
- Launcher icon
- GitHub Actions workflow to build and upload a signed release APK

## Firebase setup
1. Keep your project's `app/google-services.json` in place.
2. In Firebase Console, enable **Authentication → Sign-in method → Email/Password**.
3. Keep **Anonymous** enabled only if you still have existing guest accounts to migrate; the app now opens on a sign-in screen for new users, and the Profile tab lets an existing guest link an email/password to keep the same UID.
4. Create a Realtime Database and verify its URL/region in Firebase.
5. Publish database rules appropriate to your test and production needs.

The included rules file is a starting point, not a security audit. In particular, the invite-code lookup and write permissions need a trusted backend design before real users rely on the app. Do not use public read/write rules for a deployed app. WhatsApp numbers are user-entered and are not verified by this app.

## Build APK with GitHub Actions
Push the repository to GitHub. Open **Actions → Build RoomMate Expenses Release APK** and download the `RoomMateExpenses-release-apk` artifact after the run succeeds. The workflow also runs on pushes to `main`.

### Required repository secrets
The release build is signed with one permanent keystore so later APKs can be installed as direct updates. Add these secrets under **Settings → Secrets and variables → Actions**:

- `RELEASE_KEYSTORE_B64` — base64 text of the release `.jks` keystore
- `RELEASE_STORE_PASSWORD` — keystore password
- `RELEASE_KEY_ALIAS` — key alias, for example `roommate`
- `RELEASE_KEY_PASSWORD` — key password

Never commit the keystore or passwords to the repository. If this is the first signed release, users must remove any older debug build once; after that, every release using this same keystore updates in place and keeps app data. Always increase `versionCode` for each release.

## Notes
This is an improved feature build, not yet a release-certified production app. Test sign-up, sign-in, account linking, room creation/joining, concurrent expense updates, member removal, and database rules with separate test accounts before distributing it. The app does not include verified phone login, automated bill splitting, or paid Cloud Functions. Keep using the same signing key for updates so app data is not lost.
