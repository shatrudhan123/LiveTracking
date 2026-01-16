# 📍 Offline Supported Live Location Tracking App

## 📌 Overview
This Android application implements an **offline-supported live location tracking module**.  
The app continuously captures the user's location and guarantees **zero data loss** by saving all location data locally when the device is offline and automatically syncing it to the server (Firebase Firestore) once the network is restored.

This project is developed as part of the **Kredily – Android Developer Assignment**.

---

## 🚀 Features

### ✅ Live Location Tracking
- Uses **FusedLocationProviderClient**
- High accuracy GPS tracking
- Location captured every **10 seconds** (configurable)

### ✅ Offline Data Storage
- Uses **Room Database**
- Location data is stored locally when internet is unavailable
- Each record has an `isSynced` flag

### ✅ Automatic Sync
- Unsynced records are uploaded automatically when network becomes available
- FIFO order maintained
- Sync handled using **WorkManager**

### ✅ Background Tracking
- Uses **Foreground Service**
- Location tracking continues even if app is closed
- Persistent notification shown while tracking is active

### ✅ Network Awareness
- Detects online / offline state
- UI shows network status in real time

### ✅ Simple UI
- Start Tracking button
- Stop Tracking button
- Pending offline logs count
- Synced logs count
- Tracking status (Started / Stopped)
- Online / Offline indicator

---

## 🏗️ Architecture

**MVVM + Repository Pattern**

