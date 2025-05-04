## 📂 Dataset Structure

/sdcard/apk\_dataset/

├── safe/               # APK files verified as safe

│   ├── app1.apk

│   ├── app2.apk

│   └── ... (clean applications)

├── malware/            # APK files identified as malicious

│   ├── malware1.apk

│   ├── malware2.apk

│   └── ... (malicious applications)

└── suspicious/         # APK files with suspicious behavior (optional)

    ├── suspicious1.apk

    └── ... (potentially harmful applications)

## 🔍 Dataset Description

This dataset consists of Android APK files organized into categories based on their security classification:

*   **Safe**: Applications that have been verified as safe and contain no malicious code
*   **Malware**: Applications that contain malicious code, trojans, spyware, or other harmful functionality
*   **Suspicious**: Applications that exhibit some potentially harmful behavior but are not conclusively malicious