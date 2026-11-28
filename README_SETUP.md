# RoomMate Expenses

Android app built with Kotlin, Jetpack Compose, Firebase Email/Password Authentication and Firebase Realtime Database.

## Included in this build
- Purple and white Material 3 interface
- **Complete Email/Password authentication: Sign in, Create account, and Forgot password**
- Sign-up collects name, mobile number, email and password; the profile is stored under `users/{uid}/profile`
- Create room asks only for a room name; joining asks only for the 6-digit room code
- Email format validation, 8+ character password rule, confirm password, and show/hide password controls
- Friendly authentication error messages and disabled buttons while a request is running
- **Link an existing anonymous account to email/password so its UID, rooms and expenses are preserved**
- Sign out and profile/account section with soft email-verification status and resend/refresh actions
- Firebase Auth state handling; sign-out clears the locally saved room session
- **Rooms are optional**: create a room, join with a six-digit invite code, or continue in personal mode
- Personal mode stores expenses per user under `users/{uid}/personalExpenses`
- Switch between personal mode and room mode from Profile at any time
- WhatsApp number is optional; personal mode never shows a room member's phone number
- Active room or personal session is restored automatically after an app restart
- Room-wide monthly spending dashboard and personal monthly total
- Add, edit and delete expense entries with a date field: today is selected by default and earlier dates can be chosen manually
- Filter expenses by name and by a from/to date range
- Only the member who added an expense can edit or delete it; deletion always asks for confirmation
- Remove member asks for confirmation; removed members can no longer write expenses because database rules check room membership
- Saved rooms are remembered after joining, so switching back from personal mode does not require the code again; multiple joined rooms appear as buttons
- Notebook tab in both room and personal modes for short shared/private notes
- Room dashboard includes a category pie chart for the current month
- Categories: Food, Rent, Bills, Travel, Shopping and Other
- Live member and expense lists
- Admin badge and removal UI
- Launcher icon
- GitHub Actions workflow to build and upload a signed release APK

## Firebase setup
1. Keep your project's `app/google-services.json` in place.
2. In Firebase Console, enable **Authentication → Sign-in method → Email/Password**.
3. Under **Authentication → Settings → Authorized domains**, add your Firebase Hosting/domain if you deploy a web client; the Android app uses the default Firebase domains.
4. Optional: customize **Templates → Password reset** and **Verify email** so users recognize the messages.
5. Keep **Anonymous** enabled only if you still have existing guest accounts to migrate; the app now opens on a sign-in screen for new users, and the Profile tab lets an existing guest link an email/password to keep the same UID.
6. Create a Realtime Database and verify its URL/region in Firebase.
7. Publish database rules appropriate to your test and production needs. The included `firebase-database.rules.json` requires `auth != null` for room reads/writes, restricts room reads to members, lets a user write only their own member record, allows expense writes only by current room members (and updates/deletes only by the expense author), and allows a user to read/write only their own `profile`, `personalExpenses` and `personalNotes`. Room notes use the same membership and author-ownership checks. Publish the updated rules file before testing sign-up profiles, personal mode or expense ownership.

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
