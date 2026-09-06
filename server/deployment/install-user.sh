#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
JAR_URL="${JAR_URL:-https://github.com/mikun12138/MikunPic/releases/download/v0.2.1/mikunpic-0.2.1.jar}"
JAR_NAME="${JAR_NAME:-mikunpic.jar}"
SERVICE_NAME="${SERVICE_NAME:-mikunpic}"
WORKING_DIR="${MIKUNPIC_HOME:-${XDG_DATA_HOME:-$HOME/.local/share}/mikunpic}"
SERVICE_DIR="${XDG_CONFIG_HOME:-$HOME/.config}/systemd/user"
SERVICE_FILE="$SERVICE_DIR/$SERVICE_NAME.service"
SERVICE_TEMPLATE="$SCRIPT_DIR/mikunpic-user.service"
SOURCE_JAR="${SOURCE_JAR:-}"

if [[ "$(id -u)" -eq 0 ]]; then
    echo "User install should not be run as root. Use $SCRIPT_DIR/install.sh" >&2
    exit 1
fi

write_service_file() {
    if [[ -f "$SERVICE_TEMPLATE" ]]; then
        sed -e "s|{{WORKING_DIR}}|$WORKING_DIR|g" \
            -e "s|{{JAR_NAME}}|$JAR_NAME|g" \
            -e "s|{{SERVICE_NAME}}|$SERVICE_NAME|g" \
            "$SERVICE_TEMPLATE" > "$SERVICE_FILE"
        return
    fi

    cat > "$SERVICE_FILE" <<EOF
[Unit]
Description=Mikun Pic Service
After=network.target

[Service]
WorkingDirectory=$WORKING_DIR
ExecStart=/usr/bin/java -jar $WORKING_DIR/$JAR_NAME --deploy-mode=user
Restart=always

[Install]
WantedBy=default.target
EOF
}

mkdir -p "$WORKING_DIR" "$SERVICE_DIR"

if [[ -n "$SOURCE_JAR" ]]; then
    if [[ -f "$SOURCE_JAR" ]]; then
        cp "$SOURCE_JAR" "$WORKING_DIR/$JAR_NAME"
    else
        curl -fL "$SOURCE_JAR" -o "$WORKING_DIR/$JAR_NAME"
    fi
else
    curl -fL "$JAR_URL" -o "$WORKING_DIR/$JAR_NAME"
fi

write_service_file
systemctl --user daemon-reload
systemctl --user enable --now "$SERVICE_NAME.service"

echo "Installed $SERVICE_NAME user service"
echo "Working directory: $WORKING_DIR"
echo "Service file: $SERVICE_FILE"
