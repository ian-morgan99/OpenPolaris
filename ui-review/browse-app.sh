#!/bin/bash
# Systematic UI browsing script for OpenPolaris app
# This script navigates through all screens and captures screenshots

ADB="/home/ian/android-sdk/platform-tools/adb"
SCREENSHOT_DIR="/tmp/openpolaris/screenshots"
REPO_SCREENSHOT_DIR="/home/ian/Documents/VSCodeProjects/OpenPolaris/ui-review/screenshots"

mkdir -p "$SCREENSHOT_DIR"

# Function to take numbered screenshot
take_screenshot() {
    local num=$1
    local desc=$2
    $ADB shell screencap -p /sdcard/op-screenshot-$num.png
    $ADB pull /sdcard/op-screenshot-$num.png $SCREENSHOT_DIR/$num-$desc.png
    cp $SCREENSHOT_DIR/$num-$desc.png $REPO_SCREENSHOT_DIR/$num-$desc.png
}

# Function to tap at coordinates
tap() {
    local x=$1
    local y=$2
    $ADB shell input tap $x $y
}

# Start with opening the app
echo "Opening OpenPolaris app..."
$ADB shell am start -n dev.openpolaris.app/dev.openpolaris.android.MainActivity
sleep 2
take_screenshot "00" "main-screen"

# For Compose apps, we need to interact with specific UI elements
# Let's try to access different panes by tapping on typical locations

echo "Navigating through app screens..."

# Main screen is already captured (00-main-screen)

# Try to open Connection dialog (typically top-right or bottom area)
echo "Opening Connection dialog..."
tap 1800 200  # Approximate location for connection button
sleep 2
take_screenshot "01" "connection-dialog"

# Go back
$ADB shell input keyevent 4
sleep 1

# Try Slew & Align
echo "Opening Slew & Align..."
tap 1800 400
sleep 2
take_screenshot "02" "slew-align-dialog"

$ADB shell input keyevent 4
sleep 1

# Try Camera
echo "Opening Camera pane..."
tap 1800 600
sleep 2
take_screenshot "03" "camera-pane"

$ADB shell input keyevent 4
sleep 1

# Try Preview
echo "Opening Preview pane..."
tap 1800 800
sleep 2
take_screenshot "04" "preview-pane"

$ADB shell input keyevent 4
sleep 1

# Try Helpers
echo "Opening Astro helpers..."
tap 1800 1000
sleep 2
take_screenshot "05" "helpers-pane"

$ADB shell input keyevent 4
sleep 1

# Try Firmware
echo "Opening Firmware update..."
tap 1800 1200
sleep 2
take_screenshot "06" "firmware-pane"

$ADB shell input keyevent 4
sleep 1

# Try Settings
echo "Opening Settings..."
tap 1800 1400
sleep 2
take_screenshot "07" "settings-dialog"

echo "Screenshot capture complete!"