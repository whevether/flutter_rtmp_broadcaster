## 0.0.1
1. Completely refactored the Android version. Upgraded Gradle and the RTMP streaming plugin `com.github.pedroSG94.RootEncoder` to the latest version.  
2. Added filters for the Android version.  
3. Improved deprecated methods and fixed crashes caused by camera switching on Android.  
4. Fixed the issue where live recording could not be captured on Android.  
5. Added a toggle for low-light environment settings on Android.  

## 0.0.2
1. Removed redundant Android packages to reduce build size.  

## 0.0.3
1. Upgraded iOS HaishinKit to version 1.9.9.  
2. Rewrote some deprecated methods.  

## 0.0.4
1. Fixed Android crash errors when switching cameras. Added camera switching and audio toggle features. Optimized the example project.  
2. Cleaned up unused methods.  

## 0.0.5
1. Optimized Android example project.  
2. Added pause/resume recording functionality for Android.  

## 0.0.6
1. Updated iOS HaishinKit to version 2.0.0 (preview version, not stable, may contain bugs).  

## 1.0.0
1. Updated iOS HaishinKit to version 2.0.0 (stable release).  
2. Completely refactored iOS code, now managing dependencies via Swift Package Manager.  
3. Added numerous new methods for iOS.  
4. Updated examples: revised `camera.dart`, removed redundant fields and duplicate methods.  
5. Upgraded Android Gradle to 9.0, updated RTMP package to the latest version, unified return values with iOS, and improved disposal methods.  
6. Added filter functionality for Android.  

## 1.0.1
1. update package
2. add - 📸 Take snapshot during streaming: `takePicture`  