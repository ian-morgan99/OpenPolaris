#!/usr/bin/env bash
# Source this to set the env vars the OpenPolaris build needs.
#   . scripts/env.sh
# It is intentionally a no-op on environments that already have the
# variables configured.
: "${ANDROID_HOME:=/home/ian/android-sdk}"
: "${ANDROID_SDK_ROOT:=$ANDROID_HOME}"
export ANDROID_HOME ANDROID_SDK_ROOT
