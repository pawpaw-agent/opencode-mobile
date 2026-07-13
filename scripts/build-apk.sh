#!/bin/sh
cd "$(dirname "$0")/android" && exec gradle assembleDebug --no-daemon
