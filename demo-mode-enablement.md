# OpenPolaris Demo Mode Enablement Guide

## Problem
The Demo mode button in the Connection pane is not visible or accessible, preventing testing of wireless bridging functionality without actual hardware.

## Solution
Enable demo mode through the Settings dialog's feature flags toggle.

## Step-by-Step Instructions

### 1. Return to Main Screen
- Press the back button repeatedly until you reach the main OpenPolaris screen
- You should see: "Disconnected" status, position readouts, and the bottom navigation bar (Wi-Fi, Slew, Camera, Preview, More)

### 2. Open Settings Dialog
- Tap the "More" button (⋮) in the bottom-right corner of the screen
- From the overflow menu, tap "Settings"

### 3. Locate Feature Toggles
In the Settings dialog, you should see:
- Title: "Settings"
- Section: "Feature flags (runtime overrides)"
- Description: "Settings a regular user can safely flip. Each row is a plain toggle."
- Feature category: "Slew / track / jog (513-516, 524-536)"

### 4. Enable Demo Mode Toggle
Based on UI analysis, the demo mode toggle is likely the FIRST clickable toggle in the settings list:

**Tap Position:** Approximately X=1238, Y=516
- This is in the toggle area just below the description text
- Try tapping this position 2-3 times with 1-second intervals between taps

### 5. Verify Demo Mode Activation
After tapping, return to the main screen and look for:
- Connection status changing from "Disconnected" to show simulated connection
- Position readings updating with simulated values (not just "—°")
- Ability to interact with Camera and Preview panes (they should show simulated content)
- New UI elements or indicators showing simulator is active

### 6. Alternative Toggle Positions to Try
If the first position doesn't work, try these alternative Y positions at X≈1238:
- Y=496 (slightly above the description)
- Y=536 (slightly below the description)
- Y=674 (in the Slew/track/jog toggle area)
- Y=694 (the second toggle we identified)

### 7. Verification via Logs
Check for demo mode activation in logs:
```bash
adb logcat | grep -i -E "demo|simulated.*mount|connectdemo"
```

### 8. If Still Not Working
1. Try restarting the app:
   - Swipe up from recent apps to close OpenPolaris
   - Reopen the app
   - Repeat steps 2-5
2. Try toggling multiple times in quick succession
3. Check if any UI elements change appearance after tapping (color, size, etc.)

## Expected Behavior in Demo Mode
When demo mode is successfully enabled:
- The app runs an in-process simulator that mimics a connected mount
- Wireless bridging UI elements should become interactive
- Camera pane should show simulated video feed
- Preview pane should show simulated controls
- Slew & Align dialog should show simulated mount controls
- Connection pane may show "Connected" status with simulated data

## Notes
- The exact toggle position may vary slightly based on screen density and font scaling
- Demo mode is intended for development/testing and may not reflect all real-world behaviors
- To disable demo mode, repeat the toggle process or clear app data

## Troubleshooting
If taps don't seem to register:
1. Try longer press durations (press and hold for 500ms)
2. Try tapping slightly above/below the target position
3. Make sure you're tapping within the bounds of the toggle view (not just the text label)
4. Verify you're in the correct Settings dialog (not a system settings screen)

## Reference Coordinates
Based on analysis of a typical emulator screen:
- Screen width: ~1080px
- Screen height: ~2340px
- Demo mode toggle approximate position: (1238, 516)
- This is roughly: 65% of screen width, 22% of screen height from top