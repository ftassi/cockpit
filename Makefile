ANDROID_HOME ?= $(HOME)/Android/Sdk

CMDLINE_TOOLS_ZIP := commandlinetools-linux-15859902_latest.zip
CMDLINE_TOOLS_SHA256 := 4e4c464f145a7512b57d088ac6c278c03c9eea610886b35a5e0804e74eedf583
SDK_PACKAGES := "platform-tools" "platforms;android-37.0" "build-tools;37.0.0"

SDKMANAGER := $(ANDROID_HOME)/cmdline-tools/latest/bin/sdkmanager
ADB := $(ANDROID_HOME)/platform-tools/adb
APP_ID := dev.ftassi.cockpit
KEYSTORE_DIR := $(HOME)/.config/cockpit

# Phosphor Icons (MIT) and Barlow Semi Condensed (OFL), committed under res/.
PHOSPHOR_VERSION := 2.1.1
ICONS := radio spotify-logo play pause phone navigation-arrow microphone magnifying-glass squares-four caret-down arrow-left arrows-clockwise
FONTS_URL := https://raw.githubusercontent.com/google/fonts/main/ofl/barlowsemicondensed
FONT_WEIGHTS := Medium SemiBold

export ANDROID_HOME

.PHONY: help deps system-deps sdk devices adb-pair adb-connect keystore build test install install-release run logs awake screenshot usb-devices icons fonts

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

keystore: ## Create the signing key in ~/.config/cockpit (never overwrites; back it up!)
	@test ! -e $(KEYSTORE_DIR)/release.jks || { echo "$(KEYSTORE_DIR)/release.jks already exists"; exit 1; }
	@mkdir -p -m 700 $(KEYSTORE_DIR)
	@umask 077; pass=$$(openssl rand -hex 24); \
	keytool -genkeypair -keystore $(KEYSTORE_DIR)/release.jks -storetype PKCS12 \
		-alias cockpit -keyalg RSA -keysize 4096 -validity 36500 \
		-dname "CN=Cockpit" -storepass "$$pass" -keypass "$$pass" >/dev/null 2>&1 && \
	printf 'storeFile=%s\nstorePassword=%s\nkeyAlias=cockpit\nkeyPassword=%s\n' \
		"$(KEYSTORE_DIR)/release.jks" "$$pass" "$$pass" > $(KEYSTORE_DIR)/keystore.properties
	@echo "Created $(KEYSTORE_DIR)/release.jks and keystore.properties"

build: ## Build the debug APK
	./gradlew assembleDebug

test: ## Run unit tests
	./gradlew testDebugUnitTest

install: ## Build and install the debug APK on the connected phone
	./gradlew installDebug

install-release: ## Build and install the release APK (the one for the car)
	./gradlew installRelease

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

usb-devices: ## List USB devices the phone has hosted (vendor/product ids for the USB filter)
	$(ADB) shell dumpsys usb | sed -n '/host_manager={/,$$p' | grep -E 'manufacturer=[0-9]+$$|product=[0-9]+$$' | paste - - | sort -u

icons: ## Regenerate the Phosphor vector drawables listed in ICONS, plus their license
	python3 scripts/phosphor_icons.py app/src/main/res/drawable $(PHOSPHOR_VERSION) $(ICONS)
	curl -fsSL -o licenses/phosphor-MIT.txt https://unpkg.com/@phosphor-icons/core@$(PHOSPHOR_VERSION)/LICENSE

fonts: ## Download the Barlow Semi Condensed weights the UI uses, plus its license
	mkdir -p app/src/main/res/font licenses
	for w in $(FONT_WEIGHTS); do \
		curl -fsSL -o app/src/main/res/font/barlow_semi_condensed_$$(echo $$w | tr A-Z a-z).ttf $(FONTS_URL)/BarlowSemiCondensed-$$w.ttf || exit 1; \
	done
	curl -fsSL -o licenses/barlow-semi-condensed-OFL.txt $(FONTS_URL)/OFL.txt
