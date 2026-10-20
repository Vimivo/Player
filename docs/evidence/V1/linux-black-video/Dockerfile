FROM ubuntu:24.04
ENV DEBIAN_FRONTEND=noninteractive
RUN apt-get update -qq && apt-get install -y -qq --no-install-recommends openjdk-17-jdk libmpv2 libmpv-dev \
    xvfb xauth x11-utils xdotool imagemagick mesa-utils libgl1-mesa-dri libegl1 ffmpeg dbus dbus-x11 rsync python3 \
    x11-apps fontconfig fonts-dejavu-core ca-certificates git && rm -rf /var/lib/apt/lists/*
