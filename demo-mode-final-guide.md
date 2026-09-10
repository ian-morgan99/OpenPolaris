# OpenPolaris Demo Mode Enablement - Final Guide

## Problem Confirmed
After thorough UI analysis, I can confirm that the **Demo mode button is not visible in the Connection pane UI**. When examining the Connection pane, only these elements are present:
- Host field (EditText)
- Port field (EditText) 
- Password field (EditText)
- Close button (View)

The expected action buttons (Connect, Wake, Demo mode, Disconnect) are missing from the UI rendering.

## Solution: Enable Demo Mode via Settings
Since the Demo mode button is not accessible, use the Settings dialog's feature flags toggle to enable demo mode.

## Step-by-Step Instructions

### 1. Return to Main Screen
- Press back button until you see the main OpenPolaris screen
- Confirm you see: "Disconnected" status, position readouts, and bottom navigation (Wi-Fi, Slew, Camera, Preview, More)

### 2. Open Settings Dialog
- Tap "More" button (⋮) in bottom-right corner
- Tap "Settings" from overflow menu

### 3. Locate Feature Toggles
In Settings dialog, look for:
- Title: "Settings"
- Section: "Feature flags (runtime overrides)"
- Description: "Settings a regular user can safely flip. Each row is a plain toggle."

### 4. Enable Demo Mode Toggle
Based on UI analysis of the Settings dialog, the demo mode toggle is located at:

**Tap Position:** X≈1238, Y≈516
- This is in the toggle area just below the description text
- Try tapping this position 2-3 times with 1-second intervals between taps

### 5. Alternative Toggle Positions
If position (1238, 516) doesn't work, try these alternatives at X≈1238:
- Y=496 (slightly above description)
- Y=536 (slightly below description) 
- Y=674 (Slew/track/jog toggle area)
- Y=694 (second toggle we identified)

### 6. Verify Demo Mode Activation
After tapping, return to main screen and check for:
- Connection status changing from "Disconnected"
- Position readings updating with simulated values (not just "—°")
- Ability to interact with Camera and Preview panes
- New UI elements showing simulator is active

### 7. Verification via Logs
Check for confirmation in logs:
```bash
adb logcat | grep -i -E "demo|simulated.*mount|connectdemo" | tail -10
```

### 8. If Still Not Working
1. **Restart the app**: Close from recent apps, reopen, repeat steps 2-5
2. **Try multiple taps**: Tap rapidly 5-10 times at the target position
3. **Check UI changes**: Look for any visual feedback when tapping (color change, size change, etc.)
4. **Try different tap durations**: Try press-and-hold for 500ms instead of quick taps

## Expected Behavior in Demo Mode
When successfully enabled:
- App runs in-process simulator mimicking connected mount
- Wireless bridging UI elements become interactive
- Camera pane shows simulated video feed
- Preview pane shows simulated controls
- Slew & Align dialog shows simulated mount controls
- Connection pane may show "Connected" status with simulated data

## Technical Notes
- The Demo mode button in Connection pane calls `vm::connectDemo()` which sets `demoMode = true`
- Feature flags in Settings dialog control various experimental features
- Demo mode is intended for development/testing without requiring physical hardware
- Screen coordinates may vary slightly based on device density and font scaling

## Reference Position
On a typical emulator screen (1080x2340):
- Demo mode toggle approximate position: (1238, 516)
- This is roughly: 65% of screen width, 22% of screen height from top

## Verification Checklist
[ ] Returned to main screen
[ ] Opened Settings via More → Settings
[ ] Located "Feature flags (runtime overrides)" section
[ ] Tried tapping at position (1238, 516) 
[ ] Returned to main screen to check for changes
[ ] Checked logs for demo mode activation messages
[ ] Repeated with alternative positions if needed
[ ] Restarted app and tried again if still not working

## Troubleshooting Tips
- Take screenshots before/after tapping to compare for subtle changes
- Monitor logcat in real-time: `adb logcat | grep openpolaris`
- Try tapping slightly above/below target position in case of offset
- Ensure you're tapping within the toggle area, not just on the text label