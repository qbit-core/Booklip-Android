#!/bin/zsh
# Helpers for capturing raw screenshots from the emulator.
#   source tools.sh; demo_on; shot phone_library; tapd "[Select]"; demo_off
ADB=~/Library/Android/sdk/platform-tools/adb
HERE=${0:A:h}

# A clean status bar: 9:41, full battery and signal, no notification icons.
demo_on() {
  $ADB shell settings put global sysui_demo_allowed 1
  b() { $ADB shell am broadcast -a com.android.systemui.demo "$@" >/dev/null }
  b -e command enter
  b -e command clock -e hhmm 0941
  b -e command battery -e level 100 -e plugged false
  b -e command network -e wifi show -e level 4 -e fully true
  b -e command network -e mobile hide
  b -e command notifications -e visible false
}
demo_off() { $ADB shell am broadcast -a com.android.systemui.demo -e command exit >/dev/null }

# Tablet captures reuse the phone emulator with a 10-inch-class display.
tablet_on()  { $ADB shell wm size 1600x2560; $ADB shell wm density 320 }
tablet_off() { $ADB shell wm size reset; $ADB shell wm density reset }

shot() { $ADB exec-out screencap -p > "$HERE/raw/$1.png"; echo "raw/$1.png" }

# Visible text / [content-desc] with the centre of each node.
ui() {
  $ADB shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
  $ADB exec-out cat /sdcard/ui.xml | python3 -c "
import sys,re
for m in re.finditer(r'<node[^>]*?text=\"([^\"]*)\"[^>]*?content-desc=\"([^\"]*)\"[^>]*?bounds=\"\[(\d+),(\d+)\]\[(\d+),(\d+)\]\"', sys.stdin.read()):
    t,d,x1,y1,x2,y2=m.groups()
    if t or d: print(f'{(int(x1)+int(x2))//2},{(int(y1)+int(y2))//2}\t{t or \"[\"+d+\"]\"}')
"
}

# Tap the first node whose text / content-desc contains $1.
tapd() {
  local line=$(ui | grep -F -m1 -- "$1")
  [[ -z "$line" ]] && { echo "not found: $1"; return 1 }
  local xy=${line%%$'\t'*}
  $ADB shell input tap ${xy%,*} ${xy#*,}; echo "tapped $line"
}
