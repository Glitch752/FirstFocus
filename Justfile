default: check

check:
    ./gradlew check

build:
    ./gradlew assemble

debug:
    ./gradlew assembleDebug

release:
    ./gradlew assembleRelease

test:
    ./gradlew test

clean:
    ./gradlew clean

install:
    ./gradlew installDebug
    adb shell am start -n "com.example.focus/com.example.focus.MainActivity"