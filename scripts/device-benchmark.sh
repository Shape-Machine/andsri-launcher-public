#!/bin/zsh
set -euo pipefail

adb_bin=${ADB:-$HOME/Library/Android/sdk/platform-tools/adb}
serial_args=()
if [[ -n ${SERIAL:-} ]]; then serial_args=(-s "$SERIAL"); fi
package=xyz.shapemachine.andsri
component=$package/.MainActivity
runs=${RUNS:-10}

"$adb_bin" $serial_args shell input keyevent KEYCODE_WAKEUP
"$adb_bin" $serial_args shell wm dismiss-keyguard
"$adb_bin" $serial_args shell cmd statusbar collapse

times=()
for _ in $(seq 1 "$runs"); do
  "$adb_bin" $serial_args shell am force-stop "$package"
  value=$("$adb_bin" $serial_args shell am start -W -S -n "$component" | awk -F': ' '/TotalTime/{gsub("\\r", "", $2); print $2}')
  if [[ "$value" == <-> ]]; then times+=("$value"); fi
done

sample_count=${#times[@]}
if (( sample_count == 0 )); then
  printf 'Cold start measurement failed: Android returned no TotalTime samples.\n' >&2
  exit 1
fi
sorted=$(printf '%s\n' $times | sort -n)
median=$(printf '%s\n' "$sorted" | awk -v n=$sample_count 'NR == int((n + 1) / 2) { print }')
maximum=$(printf '%s\n' "$sorted" | tail -1)
printf 'Cold start ms (%s/%s valid): %s\nMedian: %s ms\nMaximum: %s ms\n' "$sample_count" "$runs" "${times[*]}" "$median" "$maximum"

"$adb_bin" $serial_args shell input keyevent KEYCODE_WAKEUP
"$adb_bin" $serial_args shell wm dismiss-keyguard
"$adb_bin" $serial_args shell cmd statusbar collapse
"$adb_bin" $serial_args shell am start -W -n "$component" >/dev/null
"$adb_bin" $serial_args shell dumpsys gfxinfo "$package" reset >/dev/null
sleep 1
for _ in {1..8}; do
  "$adb_bin" $serial_args shell input swipe 540 1800 540 500 240
  sleep 0.25
done
gfxinfo=$("$adb_bin" $serial_args shell dumpsys gfxinfo "$package")
rendered=$(printf '%s\n' "$gfxinfo" | awk -F': ' '/Total frames rendered:/{gsub("\\r", "", $2); print $2; exit}')
if [[ -n "$rendered" && "$rendered" -gt 0 ]]; then
  printf '%s\n' "$gfxinfo" | awk '/Total frames rendered:|Janky frames:|90th percentile:|95th percentile:|99th percentile:/{print}' | head -5
else
  printf 'Frame sample unavailable: Android recorded no rendered frames during scripted swipes.\n'
fi
"$adb_bin" $serial_args shell dumpsys cpuinfo | awk -v package="$package" '$0 ~ package {print "CPU sample:", $0}'
