ANDROID_HOME ?= $(HOME)/Android/Sdk

CMDLINE_TOOLS_ZIP := commandlinetools-linux-15859902_latest.zip
CMDLINE_TOOLS_SHA256 := 4e4c464f145a7512b57d088ac6c278c03c9eea610886b35a5e0804e74eedf583
SDK_PACKAGES := "platform-tools" "platforms;android-37.0" "build-tools;37.0.0"

SDKMANAGER := $(ANDROID_HOME)/cmdline-tools/latest/bin/sdkmanager
ADB := $(ANDROID_HOME)/platform-tools/adb
APP_ID := dev.ftassi.cockpit

export ANDROID_HOME

.PHONY: help deps system-deps sdk devices adb-pair adb-connect build test install run logs awake screenshot

help: ## Show this help
	@grep -E '^[a-z-]+:.*## ' $(MAKEFILE_LIST) | awk -F ':.*## ' '{printf "  %-14s %s\n", $$1, $$2}'

deps: system-deps sdk ## Install everything needed to build and debug

system-deps: ## Install JDK 21 and udev rules for Android devices (sudo)
	sudo apt-get install -y openjdk-21-jdk-headless android-sdk-platform-tools-common

sdk: $(SDKMANAGER) ## Install Android SDK packages and accept licenses
	yes | $(SDKMANAGER) --licenses > /dev/null
	$(SDKMANAGER) --install $(SDK_PACKAGES)

$(SDKMANAGER):
	mkdir -p $(ANDROID_HOME)/cmdline-tools
	curl -fL -o $(ANDROID_HOME)/$(CMDLINE_TOOLS_ZIP) https://dl.google.com/android/repository/$(CMDLINE_TOOLS_ZIP)
	echo "$(CMDLINE_TOOLS_SHA256)  $(ANDROID_HOME)/$(CMDLINE_TOOLS_ZIP)" | sha256sum -c -
	rm -rf $(ANDROID_HOME)/cmdline-tools/latest $(ANDROID_HOME)/cmdline-tools/cmdline-tools
	unzip -q $(ANDROID_HOME)/$(CMDLINE_TOOLS_ZIP) -d $(ANDROID_HOME)/cmdline-tools
	mv $(ANDROID_HOME)/cmdline-tools/cmdline-tools $(ANDROID_HOME)/cmdline-tools/latest
	rm $(ANDROID_HOME)/$(CMDLINE_TOOLS_ZIP)

devices: ## List connected devices
	$(ADB) devices -l

adb-pair: ## Pair over Wi-Fi: make adb-pair ADDR=ip:port CODE=123456 (both from the pairing dialog)
	$(ADB) pair $(ADDR) $(CODE)

adb-connect: ## Connect over Wi-Fi: make adb-connect ADDR=ip:port
	$(ADB) connect $(ADDR)

build: ## Build the debug APK
	./gradlew assembleDebug

test: ## Run unit tests
	./gradlew testDebugUnitTest

install: ## Build and install the debug APK on the connected phone
	./gradlew installDebug

run: install ## Install and launch the app
	$(ADB) shell am start -n $(APP_ID)/.MainActivity

logs: ## Show the app's own log lines (tag Cockpit) plus crashes
	$(ADB) logcat -s Cockpit:V AndroidRuntime:E

awake: ## Keep the phone screen on while plugged into USB, wake it and dismiss the lock screen
	$(ADB) shell svc power stayon usb
	$(ADB) shell input keyevent KEYCODE_WAKEUP
	$(ADB) shell wm dismiss-keyguard

screenshot: ## Save a screenshot of the phone to screenshots/latest.png
	mkdir -p screenshots
	$(ADB) exec-out screencap -p > screenshots/latest.png
