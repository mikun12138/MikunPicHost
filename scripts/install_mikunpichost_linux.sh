#!/bin/bash

JAR_URL="https://github.com/mikun12138/MikunPicHost/releases/download/v0.0.1/MikunPicHost-0.0.1-all.jar"
WORKING_DIR="/opt/mikun-pic-host"
JAR_NAME="MikunPicHost.jar"

SERVICE_FILE_URL="https://github.com/mikun12138/MikunPicHost/scripts/mikun-pic-host.service"

SERVICE_FILE="mikun-pic-host.service"

SERVICE_NAME="mikun-pic-host"
REAL_USER=${SUDO_USER:-$USER}
REAL_GROUP=$(id -gn $REAL_USER)
JAVA_PATH=$(which java)

curl -L "$JAR_URL" -o "$WORKING_DIR/$JAR_NAME"
curl -L "$SERVICE_FILE_URL" -o "$(pwd)/$SERVICE_FILE"

sed -e "s|{{USER}}|$REAL_USER|g" \
    -e "s|{{GROUP}}|$REAL_GROUP|g" \
    -e "s|{{WORKING_DIR}}|$WORKING_DIR|g" \
    -e "s|{{JAVA_PATH}}|$JAVA_PATH|g" \
    -e "s|{{JAR_NAME}}|$JAR_NAME|g" \
    -e "s|{{SERVICE_NAME}}|$SERVICE_NAME|g" \
    "$SERVICE_FILE" > "/etc/systemd/system/$SERVICE_NAME.service"

systemctl daemon-reload
systemctl enable "$SERVICE_NAME"
systemctl restart "$SERVICE_NAME"

echo "OK"