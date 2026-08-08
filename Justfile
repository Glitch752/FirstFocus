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

install: debug
    ./gradlew installDebug