const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";
const ADMIN_UID = "admin_789";
const ADMIN_EMAIL = "ltlamiya146@gmail.com";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function validServerPayload(active = true) {
  const now = new Date(Date.now() - 2000);
  return {
    name: "Dhaka Edge 01",
    country: "Bangladesh",
    countryCode: "BD",
    city: "Dhaka",
    flagEmoji: "BD",
    endpointHost: "103.112.44.10",
    endpointPort: 51820,
    serverPublicKey: "A".repeat(43) + "=",
    dns: "1.1.1.1, 1.0.0.1",
    allowedIps: "0.0.0.0/0, ::/0",
    clientAddressCidr: "10.66.66.2/32",
    mtu: 1280,
    persistentKeepalive: 25,
    provisionApiUrl: "https://bd1.fastxvpn.net:8443",
    active,
    latencyMs: 28,
    loadPercent: 18,
    bandwidthMbps: 1000,
    createdAt: now,
    updatedAt: now,
  };
}

function validProfilePayload(uid) {
  const now = new Date(Date.now() - 2000);
  return {
    userId: uid,
    displayName: "Alice",
    email: "alice@example.com",
    clientPublicKey: "B".repeat(43) + "=",
    totalBytesDown: 0,
    totalBytesUp: 0,
    totalSessions: 0,
    totalConnectedSeconds: 0,
    autoReconnect: true,
    plan: "FREE",
    createdAt: now,
    updatedAt: now,
  };
}

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) await testEnv.cleanup();
});

beforeEach(async () => {
  if (testEnv) await testEnv.clearFirestore();
});

test("Unauthenticated user cannot read or write vpn_servers or users", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("vpn_servers").where("active", "==", true).get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).set(validProfilePayload(ALICE_UID)));
});

test("Regular user can create and read own profile, but cannot list all users or escalate plan", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID, { email: "alice@example.com", email_verified: true }).firestore();
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).set(validProfilePayload(ALICE_UID)));
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).get());
  await assertFails(aliceDb.collection("users").get());
  await assertFails(aliceDb.collection("users").doc(ALICE_UID).update({ plan: "PRO", updatedAt: new Date(Date.now() - 1000) }));
  await assertFails(aliceDb.collection("users").doc(BOB_UID).get());
});

test("Server query alignment: regular user can query active==true servers, unfiltered query fails; admin can list all and create", async () => {
  const adminDb = testEnv.authenticatedContext(ADMIN_UID, { email: ADMIN_EMAIL, email_verified: true }).firestore();
  await assertSucceeds(adminDb.collection("vpn_servers").doc("bd_dhaka_01").set(validServerPayload(true)));
  await assertSucceeds(adminDb.collection("vpn_servers").doc("us_nyc_01").set(validServerPayload(false)));

  const aliceDb = testEnv.authenticatedContext(ALICE_UID, { email: "alice@example.com", email_verified: true }).firestore();
  await assertSucceeds(aliceDb.collection("vpn_servers").where("active", "==", true).get());
  await assertFails(aliceDb.collection("vpn_servers").get());
  await assertFails(aliceDb.collection("vpn_servers").doc("rogue").set(validServerPayload(true)));

  // Spoofed unverified admin email must fail
  const spoofDb = testEnv.authenticatedContext("spoof", { email: ADMIN_EMAIL, email_verified: false }).firestore();
  await assertFails(spoofDb.collection("vpn_servers").get());
  await assertSucceeds(adminDb.collection("vpn_servers").get());
});

test("User sessions and custom configs enforce owner isolation and schema validation", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID, { email: "alice@example.com", email_verified: true }).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID, { email: "bob@example.com", email_verified: true }).firestore();
  const now = new Date(Date.now() - 1000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("sessions").doc("sess_1").set({
      userId: ALICE_UID,
      serverId: "bd_dhaka_01",
      serverName: "Dhaka Edge 01",
      country: "Bangladesh",
      protocol: "WireGuard",
      bytesDown: 102400,
      bytesUp: 24576,
      durationSeconds: 120,
      createdAt: now,
    })
  );

  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("sessions").get());
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("sessions").doc("sess_bad").set({
      id: "sess_bad",
      userId: ALICE_UID,
      serverId: "bd_dhaka_01",
      serverName: "Dhaka Edge 01",
      country: "Bangladesh",
      protocol: "WireGuard",
      bytesDown: 100,
      bytesUp: 50,
      durationSeconds: 10,
      createdAt: now,
    })
  );
});
