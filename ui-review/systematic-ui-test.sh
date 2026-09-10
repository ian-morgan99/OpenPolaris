#!/bin/bash
# Systematic UI testing script for OpenPolaris app

ADB="/home/ian/android-sdk/platform-tools/adb"
SCREENSHOT_DIR="/tmp/openpolaris/screenshots"
REPO_SCREENSHOT_DIR="/home/ian/Documents/VSCodeProjects/OpenPolaris/ui-review/screenshots"

mkdir -p "$SCREENSHOT_DIR"

# Function to take numbered screenshot
take_screenshot() {
    local num=$1
    local desc=$2
    $ADB shell screencap -p /sdcard/op-test-$num.png
    $ADB pull /sdcard/op-test-$num.png $SCREENSHOT_DIR/$num-$desc.png
    cp $SCREENSHOT_DIR/$num-$desc.png $REPO_SCREENSHOT_DIR/$num-$desc.png
}

# Function to tap at coordinates
tap() {
    local x=$1
    local y=$2
    echo "Tapping at ($x, $y)"
    $ADB shell input tap $x $y
    sleep 2
}

# Start with opening the app
echo "Opening OpenPolaris app..."
$ADB shell am start -n dev.openpolaris.app/dev.openpolaris.android.MainActivity
sleep 2
take_screenshot "01" "main-screen"

# Try to open Connection dialog by tapping Wi-Fi button
echo "Trying to open Connection dialog..."
tap 2000 300  # Wi-Fi button area
take_screenshot "02" "connection-dialog"

# If Connection dialog opened, try Demo mode button
echo "Trying Demo mode button..."
tap 1500 600  # Demo mode button area
take_screenshot "03" "demo-mode-enabled"

# Try other panes
echo "Trying Slew & Align..."
tap 2000 450  # Slew button area
take_screenshot "04" "slew-align-dialog"

$ADB shell input keyevent 4  # Back
sleep 1

echo "Trying Camera pane..."
tap 2000 600  # Camera button area
take_screenshot "05" "camera-pane"

$ADB shell input keyevent 4  # Back
sleep 1

echo "Trying Preview pane..."
tap 2000 750  # Preview button area
take_screenshot "06" "preview-pane"

$ADB shell input keyevent 4  # Back
sleep 1

echo "Trying More menu..."
tap 2000 900  # More button area
take_screenshot "07" "more-menu"

# Try Settings from More menu
echo "Trying Settings dialog..."
tap 1800 1000  # Settings option in More menu
take_screenshot "08" "settings-dialog"

echo "Systematic UI test complete!"