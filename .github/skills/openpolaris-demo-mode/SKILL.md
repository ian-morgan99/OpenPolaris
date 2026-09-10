# OpenPolaris Demo Mode Enablement

## Overview
This skill documents the process of enabling Demo mode in the OpenPolaris Android app when the Demo mode button is not visible in the UI. Demo mode provides an in-process simulator for testing wireless bridging functionality without requiring actual hardware.

## Prerequisites
- Android device or emulator with OpenPolaris app installed
- ADB (Android Debug Bridge) set up and connected to device
- Basic understanding of Android UI hierarchy and UI Automator

## Methodology

### 1. Initial State Assessment
Begin by assessing the current state of the app and device:

```bash
# Ensure app is in foreground
adb shell am start -n dev.openpolaris.app/dev.openpolaris.android.MainActivity

# Check basic connectivity
adb shell ping -c 3 8.8.8.8

# Verify WiFi and Bluetooth are enabled
adb shell svc wifi enable
adb shell svc bluetooth enable
```

### 2. Systematic UI Exploration
When the Demo mode button is not visible, systematically explore the UI hierarchy:

#### A. Connection Pane Inspection
1. Open Connection pane (Wi-Fi button)
2. Capture UI dump:
   ```bash
   adb shell uiautomator dump /sdcard/conn.xml && adb pull /sdcard/conn.xml /tmp/conn.xml
   ```
3. Analyze UI elements:
   ```bash
   python3 -c "
   import re
   import xml.etree.ElementTree as ET
   tree = ET.parse('/tmp/conn.xml')
   root = tree.getroot()
   for node in root.iter('node'):
       bnds = node.get('bounds', '')
       if bnds:
           coords = re.findall(r'\d+', bnds)
           if len(coords) == 4:
               x1, y1, x2, y2 = map(int, coords)
               center_x = (x1 + x2) // 2
               center_y = (y1 + y2) // 2
               txt = node.get('text', '')
               click = node.get('clickable', 'false')
               cls = node.get('class', '')
               pkg = node.get('package', '')
               if 'dev.openpolaris' in pkg and (txt or click == 'true'):
                   print(f'{center_y:4} {center_x:4} {pkg:20} {cls:25} text=\"{txt}\" click={click} bounds={bnds}')
   " | sort -n
   ```

#### B. Settings Dialog Exploration
1. Navigate to Settings via overflow menu:
   - Tap More button (bottom-right)
   - Tap Settings option
2. Inspect Settings dialog for feature flags:
   ```bash
   adb shell uiautomator dump /sdcard/settings.xml && adb pull /sdcard/settings.xml /tmp/settings.xml
   ```
3. Look for feature toggles:
   ```bash
   python3 -c "
   import re
   import xml.etree.ElementTree as ET
   tree = ET.parse('/tmp/settings.xml')
   root = tree.getroot()
   # Find main content ViewGroup
   for node in root.iter('node'):
       cls = node.get('class', '')
       bnds = node.get('bounds', '')
       if 'ViewGroup' in cls and bnds:
           coords = re.findall(r'\d+', bnds)
           if len(coords) == 4:
               x1, y1, x2, y2 = map(int, coords)
               if y1 < 100 and y2 > 900:  # Full height ViewGroup
                   # Analyze children
                   for child in root.iter('node'):
                       child_bnds = child.get('bounds', '')
                       if child_bnds:
                           child_coords = re.findall(r'\d+', child_bnds)
                           if len(child_coords) == 4:
                               cx1, cy1, cx2, cy2 = map(int, child_coords)
                               if cx1 >= x1 and cx2 <= x2 and cy1 >= y1 and cy2 <= y2:
                                   txt = child.get('text', '')
                                   click = child.get('clickable', 'false')
                                   ccls = child.get('class', '')
                                   if txt or click == 'true':
                                       center_x = (cx1 + cx2) // 2
                                       center_y = (cy1 + cy2) // 2
                                       print(f'  {center_y:4} {center_x:4} {ccls:25} text=\"{txt}\" click={click} bounds={child_bnds}')
   " | sort -n
   ```

### 3. Feature Flag Toggle Identification
When inspecting the Settings dialog, look for:
- Section headers like "Feature flags (runtime overrides)"
- Descriptions indicating toggle availability
- Clickable View elements (not Button or Switch) that represent toggles
- Typical toggle layout: [Label] [Space] [Toggle View]

### 4. Toggle Interaction
Once toggle positions are identified:
1. Tap suspected toggle positions:
   ```bash
   adb shell input tap X Y
   ```
2. Wait briefly between taps (1-2 seconds)
3. Check for UI changes or log indications

### 5. Verification
After attempting to enable demo mode:
1. Return to main screen
2. Look for simulator indicators:
   - Connection status changing from "Disconnected"
   - Position readings showing simulated values
   - New UI elements indicating simulated connection
3. Check logs for demo mode activation:
   ```bash
   adb logcat -d | grep -i -E "demo|simulated.*mount|connectdemo" | tail -10
   ```

## Alternative Approaches

### Direct ViewModel Manipulation
If UI exploration fails, consider:
1. Using Android's debug bridge to broadcast intents
2. Checking if the app exposes debug interfaces
3. Looking for hidden activities or services

### Log Analysis
Monitor logs for clues:
```bash
adb logcat | grep -i openpolaris
adb logcat | grep -i -E "feature.*flag|demo.*mode"
```

## Troubleshooting

### Common Issues
1. **UI Elements Not Visible**: May be due to ViewModel state or conditional rendering
2. **Tap Not Registering**: Ensure correct coordinates and sufficient tap duration
3. **Settings Not Persisting**: May require app restart to take effect
4. **Demo Mode Not Activating**: May require specific ViewModel state transitions

### Debug Tips
1. Use `adb shell uiautomator dump` frequently to track state changes
2. Take screenshots before and after interactions for comparison
3. Monitor logcat in real-time during experiments:
   ```bash
   adb logcat | grep openpolaris
   ```
4. Try different tap durations and positions around suspected elements

## Recording for Future Use

To create a reusable record of this process:
1. Document all successful tap coordinates
2. Save UI dumps at key decision points
3. Record log outputs showing success indicators
4. Note any device-specific variations
5. Create a step-by-step playbook for consistent reproduction

## Example Success Indicators
When demo mode is successfully enabled, look for:
- Connection status changing to show simulated connection
- Position readings updating with simulated values
- New UI elements appearing in panes (Camera, Preview, etc.)
- Log messages indicating simulator initialization
- Ability to interact with simulated mount controls

## References
- OpenPolaris Source Code: ConnectionPane.kt, FeatureFlagsPane.kt, AppViewModel.kt
- Android UI Automator Documentation
- ADB Shell Command Reference