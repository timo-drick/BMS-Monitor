#!/bin/bash
echo "Turning on bluetooth hci log"
adb shell settings put secure bluetooth_hci_log 1
echo "Waiting. Press any key to stop logging"
read -s -n 1
echo "Turning off bluetooth hci log"
adb shell settings put secure bluetooth_hci_log 1
echo "Extracting log from device"
adb shell dumpsys bluetooth_manager > tmp.snoop
./btsnooz.py tmp.snoop > bluetooth_hci.log
echo "Ready: bluetooth_hci.log"
