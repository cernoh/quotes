let
  pkgs = import (builtins.getFlake "nixpkgs") {
    system = builtins.currentSystem;
    config = {
      android_sdk.accept_license = true;
      allowUnfree = true;
    };
  };

  android = pkgs.androidenv.composeAndroidPackages {
    cmdLineToolsVersion = "latest";
    platformToolsVersion = "latest";
    # AGP resolves more than the compileSdk it was given, so keep both.
    buildToolsVersions = [ "36.0.0" "37.0.0" ];
    platformVersions = [ "36" "37" ];
    includeEmulator = true;
    includeSystemImages = true;
    systemImageTypes = [ "default" ];
    abiVersions = [ "x86_64" ];
    includeSources = false;
    includeNDK = false;
    includeCmake = false;
  };

  sdk = android.androidsdk;
in
pkgs.mkShell {
  packages = [
    pkgs.gradle_9
    pkgs.jdk21
    sdk
  ];

  shellHook = ''
    export ANDROID_HOME=${sdk}/libexec/android-sdk
    export ANDROID_SDK_ROOT=$ANDROID_HOME
    # The store is read-only, so AVDs and Gradle caches live in the checkout.
    export ANDROID_AVD_HOME=$PWD/.android/avd
    export ANDROID_USER_HOME=$PWD/.android
    export ANDROID_EMULATOR_HOME=$PWD/.android

    # The composition puts nothing on PATH, and the cmdline-tools directory is
    # named after the archive version.
    for dir in "$ANDROID_HOME"/cmdline-tools/*/bin \
               "$ANDROID_HOME"/platform-tools \
               "$ANDROID_HOME"/emulator; do
      if [ -d "$dir" ]; then PATH="$dir:$PATH"; fi
    done
    export PATH

    echo "android shell ready"
    echo "  sdk    $ANDROID_HOME"
    echo "  gradle $(gradle --version 2>/dev/null | sed -n 's/^Gradle //p' | head -1)"
    echo "  java   $(java -version 2>&1 | head -1)"
    echo "build:  gradle assembleDebug -Pandroid.aapt2FromMavenOverride=$ANDROID_HOME/build-tools/36.0.0/aapt2"
  '';
}
