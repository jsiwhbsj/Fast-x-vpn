#!/usr/bin/env bash
set -euo pipefail

# Fast X VPN - Ubuntu/Debian WireGuard VPS Provisioning Script
# Run as root on each region VPS (Bangladesh, USA, Singapore, UK, Canada, India)

REGION_NAME="${1:-FastX-Node}"
WG_PORT="${WG_PORT:-51820}"
API_PORT="${API_PORT:-8443}"
SUBNET_CIDR="${SUBNET_CIDR:-10.66.0.1/24}"
WAN_IFACE="$(ip route show default | awk '/default/ {print $5; exit}')"

apt-get update && apt-get install -y wireguard iptables curl ufw nodejs npm

mkdir -p /etc/wireguard
chmod 700 /etc/wireguard

if [ ! -f /etc/wireguard/server_private.key ]; then
  wg genkey | tee /etc/wireguard/server_private.key | wg pubkey > /etc/wireguard/server_public.key
  chmod 600 /etc/wireguard/server_private.key
fi

SERVER_PRIV="$(cat /etc/wireguard/server_private.key)"
SERVER_PUB="$(cat /etc/wireguard/server_public.key)"
PUBLIC_IP="$(curl -s4 ifconfig.me || hostname -I | awk '{print $1}')"

cat > /etc/wireguard/wg0.conf <<EOF
[Interface]
PrivateKey = ${SERVER_PRIV}
Address = ${SUBNET_CIDR}
ListenPort = ${WG_PORT}
SaveConfig = true
PostUp = sysctl -w net.ipv4.ip_forward=1 && iptables -A FORWARD -i wg0 -j ACCEPT && iptables -t nat -A POSTROUTING -o ${WAN_IFACE} -j MASQUERADE
PostDown = iptables -D FORWARD -i wg0 -j ACCEPT && iptables -t nat -D POSTROUTING -o ${WAN_IFACE} -j MASQUERADE
EOF

systemctl enable wg-quick@wg0
systemctl restart wg-quick@wg0

ufw allow "${WG_PORT}/udp"
ufw allow "${API_PORT}/tcp"

echo "=================================================="
echo "Fast X VPN WireGuard Node Ready: ${REGION_NAME}"
echo "Endpoint Host : ${PUBLIC_IP}"
echo "Endpoint Port : ${WG_PORT}"
echo "Server PubKey : ${SERVER_PUB}"
echo "Agent Port    : ${API_PORT}"
echo "=================================================="
