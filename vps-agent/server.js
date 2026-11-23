const express = require("express");
const { execFile } = require("node:child_process");
const { promisify } = require("node:util");
const fs = require("node:fs");
const path = require("node:path");
const admin = require("firebase-admin");

const execFileAsync = promisify(execFile);

const PORT = parseInt(process.env.PORT || "8443", 10);
const WG_INTERFACE = process.env.WG_INTERFACE || "wg0";
const WG_SERVER_PUBLIC_KEY = process.env.WG_SERVER_PUBLIC_KEY || "";
const WG_ENDPOINT_HOST = process.env.WG_ENDPOINT_HOST || "";
const WG_ENDPOINT_PORT = parseInt(process.env.WG_ENDPOINT_PORT || "51820", 10);
const SUBNET_PREFIX = process.env.WG_SUBNET_PREFIX || "10.66.0";
const REGION_NAME = process.env.REGION_NAME || "FastX-Node";
const PEERS_DB_PATH = process.env.PEERS_DB_PATH || path.join(__dirname, "peers.json");

if (!admin.apps.length) {
  admin.initializeApp({
    credential: admin.credential.applicationDefault(),
  });
}

function loadPeersMap() {
  try {
    if (fs.existsSync(PEERS_DB_PATH)) {
      return JSON.parse(fs.readFileSync(PEERS_DB_PATH, "utf8"));
    }
  } catch (err) {
    console.error("Failed reading peers db:", err);
  }
  return { nextOctet: 2, peersByPubKey: {} };
}

function savePeersMap(data) {
  fs.writeFileSync(PEERS_DB_PATH, JSON.stringify(data, null, 2));
}

function isValidWireGuardKey(key) {
  return typeof key === "string" && /^[A-Za-z0-9+/]{42}[AEIMQUYcgkosw048]=$/.test(key.trim());
}

async function authenticateFirebase(req, res, next) {
  const authHeader = req.headers.authorization || "";
  if (!authHeader.startsWith("Bearer ")) {
    return res.status(401).json({ error: "Missing Bearer token" });
  }
  const idToken = authHeader.slice(7).trim();
  try {
    const decoded = await admin.auth().verifyIdToken(idToken);
    req.firebaseUser = decoded;
    return next();
  } catch (err) {
    return res.status(401).json({ error: "Invalid Firebase ID token", detail: err.message });
  }
}

const app = express();
app.use(express.json());

app.get("/health", async (_req, res) => {
  try {
    const { stdout } = await execFileAsync("wg", ["show", WG_INTERFACE, "transfer"]);
    const lines = stdout.trim().split("\n").filter(Boolean);
    let rxBytes = 0;
    let txBytes = 0;
    for (const line of lines) {
      const parts = line.split(/\s+/);
      if (parts.length >= 3) {
        rxBytes += parseInt(parts[1], 10) || 0;
        txBytes += parseInt(parts[2], 10) || 0;
      }
    }
    return res.json({
      status: "ok",
      region: REGION_NAME,
      activePeers: lines.length,
      rxBytes,
      txBytes,
      loadPercent: Math.min(100, Math.round((lines.length / 250) * 100)),
      uptimeSeconds: Math.round(process.uptime()),
      interfaceName: WG_INTERFACE,
    });
  } catch (err) {
    return res.status(500).json({
      status: "degraded",
      region: REGION_NAME,
      error: err.message,
    });
  }
});

app.post("/api/v1/peers/register", authenticateFirebase, async (req, res) => {
  const { clientPublicKey } = req.body || {};
  if (!isValidWireGuardKey(clientPublicKey)) {
    return res.status(400).json({ error: "Invalid WireGuard Curve25519 public key" });
  }
  if (!isValidWireGuardKey(WG_SERVER_PUBLIC_KEY)) {
    return res.status(500).json({ error: "WG_SERVER_PUBLIC_KEY not configured on VPS agent" });
  }

  const db = loadPeersMap();
  let assignedIp = db.peersByPubKey[clientPublicKey]?.ip;
  if (!assignedIp) {
    if (db.nextOctet > 254) {
      return res.status(507).json({ error: "Subnet /24 peer pool exhausted" });
    }
    assignedIp = `${SUBNET_PREFIX}.${db.nextOctet}/32`;
    db.nextOctet += 1;
    db.peersByPubKey[clientPublicKey] = {
      ip: assignedIp,
      uid: req.firebaseUser.uid,
      updatedAt: new Date().toISOString(),
    };
    savePeersMap(db);
  }

  try {
    await execFileAsync("wg", [
      "set",
      WG_INTERFACE,
      "peer",
      clientPublicKey.trim(),
      "allowed-ips",
      assignedIp,
    ]);

    return res.json({
      clientAddressCidr: assignedIp,
      serverPublicKey: WG_SERVER_PUBLIC_KEY,
      endpointHost: WG_ENDPOINT_HOST || undefined,
      endpointPort: WG_ENDPOINT_PORT,
      dns: "1.1.1.1, 1.0.0.1",
      allowedIps: "0.0.0.0/0, ::/0",
      mtu: 1280,
      persistentKeepalive: 25,
    });
  } catch (err) {
    return res.status(500).json({ error: "Failed to register WireGuard peer", detail: err.message });
  }
});

app.listen(PORT, "0.0.0.0", () => {
  console.log(`Fast X VPN VPS agent listening on port ${PORT} for ${WG_INTERFACE}`);
});
