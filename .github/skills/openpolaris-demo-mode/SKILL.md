---
name: openpolaris-demo-mode
description: Run and verify the OpenPolaris in-process simulator on Android or desktop for UI, workflow and regression checks.
---

# OpenPolaris Demo mode

Open the Connection pane and select **Demo mode**. The common UI calls `AppViewModel.connectDemo` and connects to `SimulatedMount` in process. This procedure works from the normal app on Android or desktop. No Wi-Fi, Bluetooth or Internet setup is needed for the simulator.

Expect **Demo mode (simulated mount)**, simulated mount readings and responsive controls. Run the requested UI path and retain the build identity, platform, steps, result and screenshots or logs when needed. Label the evidence simulated. Use the physical Polaris and package test procedures for device claims.

If the action is missing, check the exact installed build and the Demo mode feature flag for that build. Inspect the [Connection pane](../../../composeApp/src/commonMain/kotlin/dev/openpolaris/ui/Panes.kt) and [ViewModel](../../../composeApp/src/commonMain/kotlin/dev/openpolaris/ui/AppViewModel.kt). On Android, optionally inspect the UI hierarchy on a selected test device using `adb -s SERIAL shell uiautomator dump`. Use the visible action or a repository UI test. Leave the radios and the operator's device settings alone.

For source regression checks run the same shared and desktop targets used by CI:

~~~sh
./gradlew :shared:jvmTest :composeApp:jvmTest
~~~
