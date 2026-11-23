# Fast X VPN — WireGuard VPS Agent

Deploy this lightweight agent on each Ubuntu/Debian VPS node (Bangladesh, USA, Singapore, UK, Canada, India) to enable automatic peer key registration from the Fast X VPN Android app.

## 1. Setup WireGuard Kernel Interface
```bash
chmod +x setup-wg-vps.sh
sudo ./setup-wg-vps.sh "Bangladesh-Dhaka-01"
```

## 2. Configure & Run Provisioning API
Place your Firebase Admin service account JSON at `/etc/fastxvpn/service-account.json` (or set `GOOGLE_APPLICATION_CREDENTIALS`), then:

```bash
npm install
export GOOGLE_APPLICATION_CREDENTIALS=/etc/fastxvpn/service-account.json
export WG_SERVER_PUBLIC_KEY="$(cat /etc/wireguard/server_public.key)"
export WG_ENDPOINT_HOST="$(curl -s4 ifconfig.me)"
export WG_ENDPOINT_PORT=51820
export REGION_NAME="Bangladesh"
sudo -E npm start
```

Put the agent behind Caddy or Nginx with HTTPS (Let's Encrypt) and paste the HTTPS URL (e.g., `https://bd1.yourdomain.com`) into the **VPS Provisioning API URL** field in the Fast X VPN **Admin Panel**.
